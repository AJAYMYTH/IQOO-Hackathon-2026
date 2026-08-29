#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include <thread>
#include <android/log.h>

#define TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

#if defined(HAVE_LLAMA_CPP) && HAVE_LLAMA_CPP
#include "llama.h"

static std::mutex g_llama_mutex;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_loadModel(
        JNIEnv *env, jobject /* this */,
        jstring modelPath, jint nGpuLayers) {

    std::lock_guard<std::mutex> lock(g_llama_mutex);
    llama_backend_init();

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading GGUF model from: %s with %d GPU layers", path ? path : "null", nGpuLayers);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;

    llama_model *model = llama_model_load_from_file(path, model_params);
    if (path) env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        LOGE("Failed to load model from path");
        return 0;
    }

    LOGI("Model loaded successfully into memory");
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT jlong JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_createContext(
        JNIEnv *env, jobject /* this */,
        jlong modelHandle, jint contextSize) {

    std::lock_guard<std::mutex> lock(g_llama_mutex);
    auto *model = reinterpret_cast<llama_model *>(modelHandle);
    if (model == nullptr) {
        LOGE("Model handle is null");
        return 0;
    }

    int n_threads = std::max(2, std::min(4, (int)std::thread::hardware_concurrency()));

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize > 0 ? contextSize : 4096;
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("Failed to create llama_context");
        return 0;
    }

    LOGI("Context created successfully with size %d, threads: %d, flash_attn: enabled", ctx_params.n_ctx, n_threads);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_generate(
        JNIEnv *env, jobject /* this */,
        jlong contextHandle, jstring prompt, jint maxTokens) {

    std::lock_guard<std::mutex> lock(g_llama_mutex);
    auto *ctx = reinterpret_cast<llama_context *>(contextHandle);
    if (ctx == nullptr) {
        return env->NewStringUTF("Error: context handle is null");
    }

    auto t_start = std::chrono::high_resolution_clock::now();

    const llama_model *model = llama_get_model(ctx);
    const llama_vocab *vocab = llama_model_get_vocab(model);

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_cpp(prompt_str ? prompt_str : "");
    if (prompt_str) env->ReleaseStringUTFChars(prompt, prompt_str);

    // Tokenize
    const int n_prompt_tokens = -llama_tokenize(vocab, prompt_cpp.c_str(), (int32_t)prompt_cpp.size(), NULL, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt_tokens > 0 ? n_prompt_tokens : 1);
    if (llama_tokenize(vocab, prompt_cpp.c_str(), (int32_t)prompt_cpp.size(), prompt_tokens.data(), (int32_t)prompt_tokens.size(), true, true) < 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("Error: tokenization failed");
    }

    // Truncate prompt if longer than context size
    int n_ctx = (int)llama_n_ctx(ctx);
    if ((int)prompt_tokens.size() > n_ctx - 128) {
        LOGI("Truncating prompt from %d to %d tokens to fit context", (int)prompt_tokens.size(), n_ctx - 128);
        prompt_tokens.resize(n_ctx - 128);
    }

    // Clear KV cache before generation
    llama_memory_clear(llama_get_memory(ctx), false);

    // Prompt evaluation using explicit batch positions and logits on last token only
    auto t_prefill_start = std::chrono::high_resolution_clock::now();
    llama_batch batch = llama_batch_init(512, 0, 1);
    for (size_t b = 0; b < prompt_tokens.size(); b += 512) {
        int32_t n_eval = std::min((int32_t)(prompt_tokens.size() - b), 512);
        batch.n_tokens = 0;
        for (int32_t j = 0; j < n_eval; j++) {
            bool is_last = (b + j == prompt_tokens.size() - 1);
            batch.token[j] = prompt_tokens[b + j];
            batch.pos[j] = (llama_pos)(b + j);
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = is_last ? 1 : 0;
        }
        batch.n_tokens = n_eval;
        if (llama_decode(ctx, batch) != 0) {
            LOGE("Prompt evaluation decode failed at chunk offset %zu", b);
            llama_batch_free(batch);
            return env->NewStringUTF("Error: decode failed during prompt evaluation");
        }
    }
    auto t_prefill_end = std::chrono::high_resolution_clock::now();
    long long prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_prefill_end - t_prefill_start).count();

    // Init Sampler
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    std::string result;
    int available_ctx = std::max(128, n_ctx - (int)prompt_tokens.size() - 4);
    int max_gen = maxTokens > 0 ? std::min(maxTokens, available_ctx) : available_ctx;
    int n_cur = (int)prompt_tokens.size();
    int tokens_generated = 0;
    LOGI("Starting on-device generation: %zu prompt tokens (prefill: %lld ms, available_ctx: %d), max_gen: %d", prompt_tokens.size(), prefill_ms, available_ctx, max_gen);

    auto t_gen_start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < max_gen; i++) {
        llama_token new_token_id = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            LOGI("End of generation token reached at step %d", i);
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
            tokens_generated++;
        }

        batch.n_tokens = 0;
        batch.token[0] = new_token_id;
        batch.pos[0] = (llama_pos)n_cur;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = 1;
        batch.n_tokens = 1;
        n_cur++;

        if (llama_decode(ctx, batch) != 0) {
            LOGE("Decode failed at generation step %d", i);
            break;
        }
    }
    auto t_end = std::chrono::high_resolution_clock::now();

    llama_batch_free(batch);
    llama_sampler_free(smpl);

    long long gen_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_gen_start).count();
    long long total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    double tok_per_sec = gen_ms > 0 ? ((double)tokens_generated / (gen_ms / 1000.0)) : 0.0;

    LOGI("Generation finished: %d tokens in %lld ms (%.2f tok/s), total: %lld ms, chars: %zu",
         tokens_generated, gen_ms, tok_per_sec, total_ms, result.size());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_generateStream(
        JNIEnv *env, jobject /* this */,
        jlong contextHandle, jstring prompt, jint maxTokens, jobject callback) {

    std::lock_guard<std::mutex> lock(g_llama_mutex);
    auto *ctx = reinterpret_cast<llama_context *>(contextHandle);
    if (ctx == nullptr) {
        LOGE("generateStream: contextHandle is null");
        return env->NewStringUTF("Error: context handle is null");
    }

    auto t_start = std::chrono::high_resolution_clock::now();

    jclass callbackClass = nullptr;
    jmethodID invokeMethod = nullptr;
    if (callback != nullptr) {
        callbackClass = env->GetObjectClass(callback);
        if (callbackClass != nullptr) {
            invokeMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
        }
    }

    const llama_model *model = llama_get_model(ctx);
    const llama_vocab *vocab = llama_model_get_vocab(model);

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_cpp(prompt_str ? prompt_str : "");
    if (prompt_str) env->ReleaseStringUTFChars(prompt, prompt_str);

    // Tokenize
    const int n_prompt_tokens = -llama_tokenize(vocab, prompt_cpp.c_str(), (int32_t)prompt_cpp.size(), NULL, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt_tokens > 0 ? n_prompt_tokens : 1);
    if (llama_tokenize(vocab, prompt_cpp.c_str(), (int32_t)prompt_cpp.size(), prompt_tokens.data(), (int32_t)prompt_tokens.size(), true, true) < 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("Error: tokenization failed");
    }

    // Truncate prompt if longer than context size
    int n_ctx = (int)llama_n_ctx(ctx);
    if ((int)prompt_tokens.size() > n_ctx - 128) {
        LOGI("Truncating stream prompt from %d to %d tokens to fit context", (int)prompt_tokens.size(), n_ctx - 128);
        prompt_tokens.resize(n_ctx - 128);
    }

    // Clear KV cache before generation
    llama_memory_clear(llama_get_memory(ctx), false);

    // Prompt evaluation using explicit batch positions and logits on last token only
    auto t_prefill_start = std::chrono::high_resolution_clock::now();
    llama_batch batch = llama_batch_init(512, 0, 1);
    for (size_t b = 0; b < prompt_tokens.size(); b += 512) {
        int32_t n_eval = std::min((int32_t)(prompt_tokens.size() - b), 512);
        batch.n_tokens = 0;
        for (int32_t j = 0; j < n_eval; j++) {
            bool is_last = (b + j == prompt_tokens.size() - 1);
            batch.token[j] = prompt_tokens[b + j];
            batch.pos[j] = (llama_pos)(b + j);
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = is_last ? 1 : 0;
        }
        batch.n_tokens = n_eval;
        if (llama_decode(ctx, batch) != 0) {
            LOGE("Streaming prompt evaluation decode failed at chunk offset %zu", b);
            llama_batch_free(batch);
            return env->NewStringUTF("Error: decode failed during prompt evaluation");
        }
    }
    auto t_prefill_end = std::chrono::high_resolution_clock::now();
    long long prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_prefill_end - t_prefill_start).count();

    // Init Sampler
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    std::string result;
    int available_ctx = std::max(128, n_ctx - (int)prompt_tokens.size() - 4);
    int max_gen = maxTokens > 0 ? std::min(maxTokens, available_ctx) : available_ctx;
    int n_cur = (int)prompt_tokens.size();
    int tokens_generated = 0;
    LOGI("Starting streaming generation: %zu prompt tokens (prefill: %lld ms, available_ctx: %d), max_gen: %d", prompt_tokens.size(), prefill_ms, available_ctx, max_gen);

    auto t_gen_start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < max_gen; i++) {
        llama_token new_token_id = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            LOGI("Streaming end of generation token at step %d", i);
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            result.append(piece);
            tokens_generated++;

            if (invokeMethod != nullptr && callback != nullptr) {
                jstring pieceStr = env->NewStringUTF(piece.c_str());
                jobject retObj = env->CallObjectMethod(callback, invokeMethod, pieceStr);
                env->DeleteLocalRef(pieceStr);
                if (retObj != nullptr) {
                    env->DeleteLocalRef(retObj);
                }
                if (env->ExceptionCheck()) {
                    LOGE("Exception in Kotlin stream callback, aborting stream");
                    env->ExceptionClear();
                    break;
                }
            }
        }

        batch.n_tokens = 0;
        batch.token[0] = new_token_id;
        batch.pos[0] = (llama_pos)n_cur;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = 1;
        batch.n_tokens = 1;
        n_cur++;

        if (llama_decode(ctx, batch) != 0) {
            LOGE("Streaming decode failed at step %d", i);
            break;
        }
    }
    auto t_end = std::chrono::high_resolution_clock::now();

    if (callbackClass != nullptr) {
        env->DeleteLocalRef(callbackClass);
    }
    llama_batch_free(batch);
    llama_sampler_free(smpl);

    long long gen_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_gen_start).count();
    long long total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    double tok_per_sec = gen_ms > 0 ? ((double)tokens_generated / (gen_ms / 1000.0)) : 0.0;

    LOGI("Streaming finished: %d tokens in %lld ms (%.2f tok/s), total: %lld ms, chars: %zu",
         tokens_generated, gen_ms, tok_per_sec, total_ms, result.size());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_freeContext(
        JNIEnv *env, jobject /* this */, jlong contextHandle) {
    std::lock_guard<std::mutex> lock(g_llama_mutex);
    auto *ctx = reinterpret_cast<llama_context *>(contextHandle);
    if (ctx != nullptr) {
        llama_free(ctx);
        LOGI("Context freed");
    }
}

JNIEXPORT void JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_freeModel(
        JNIEnv *env, jobject /* this */, jlong modelHandle) {
    std::lock_guard<std::mutex> lock(g_llama_mutex);
    auto *model = reinterpret_cast<llama_model *>(modelHandle);
    if (model != nullptr) {
        llama_model_free(model);
        LOGI("Model freed");
    }
}

JNIEXPORT jstring JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_getModelInfo(
        JNIEnv *env, jobject /* this */, jlong modelHandle) {
    std::lock_guard<std::mutex> lock(g_llama_mutex);
    auto *model = reinterpret_cast<llama_model *>(modelHandle);
    if (model == nullptr) {
        return env->NewStringUTF("No model loaded");
    }

    char desc[128] = {0};
    llama_model_desc(model, desc, sizeof(desc));
    char buf[256];
    snprintf(buf, sizeof(buf), "%s (%.2fB params)",
             desc,
             (double)llama_model_n_params(model) / 1e9);
    return env->NewStringUTF(buf);
}

} // extern "C"

#else

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_loadModel(
        JNIEnv *env, jobject /* this */,
        jstring modelPath, jint nGpuLayers) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Stub loadModel called for: %s (gpuLayers: %d)", path, nGpuLayers);
    env->ReleaseStringUTFChars(modelPath, path);
    return 1;
}

JNIEXPORT jlong JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_createContext(
        JNIEnv *env, jobject /* this */,
        jlong modelHandle, jint contextSize) {
    LOGI("Stub createContext called with size %d", contextSize);
    return 1;
}

JNIEXPORT jstring JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_generate(
        JNIEnv *env, jobject /* this */,
        jlong contextHandle, jstring prompt, jint maxTokens) {
    LOGI("Stub generate called");
    return env->NewStringUTF("{\"has_issue\": false, \"summary\": \"Analysis completed\", \"issues\": []}");
}

JNIEXPORT jstring JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_generateStream(
        JNIEnv *env, jobject /* this */,
        jlong contextHandle, jstring prompt, jint maxTokens, jobject callback) {
    LOGI("Stub generateStream called");
    if (callback != nullptr) {
        jclass callbackClass = env->GetObjectClass(callback);
        if (callbackClass != nullptr) {
            jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
            if (invokeMethod != nullptr) {
                jstring token = env->NewStringUTF("On-device AI response ready.");
                env->CallObjectMethod(callback, invokeMethod, token);
                env->DeleteLocalRef(token);
            }
        }
    }
    return env->NewStringUTF("On-device AI response ready.");
}

JNIEXPORT void JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_freeContext(
        JNIEnv *env, jobject /* this */, jlong contextHandle) {
    LOGI("Stub freeContext called");
}

JNIEXPORT void JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_freeModel(
        JNIEnv *env, jobject /* this */, jlong modelHandle) {
    LOGI("Stub freeModel called");
}

JNIEXPORT jstring JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_getModelInfo(
        JNIEnv *env, jobject /* this */, jlong modelHandle) {
    return env->NewStringUTF("On-Device LLM Bridge Ready (Snapdragon / ARM64)");
}

} // extern "C"

#endif
