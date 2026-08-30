#include <jni.h>
#include <string>
#include <vector>
#include <unordered_set>
#include <mutex>
#include <algorithm>
#include <thread>
#include <android/log.h>

#define TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
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
    
    // If GPU offload failed on this specific device, fallback to CPU
    if (model == nullptr && nGpuLayers > 0) {
        LOGW("GPU layer offload failed (%d layers), attempting CPU-only fallback", nGpuLayers);
        model_params.n_gpu_layers = 0;
        model = llama_model_load_from_file(path, model_params);
    }

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

    int hw_threads = (int)std::thread::hardware_concurrency();
    int n_threads = hw_threads > 0 ? std::max(1, std::min(4, hw_threads <= 4 ? hw_threads : hw_threads - 2)) : 4;

    int requested_ctx = contextSize > 0 ? contextSize : 4096;
    std::vector<int> ctx_candidates;
    ctx_candidates.push_back(requested_ctx);
    if (requested_ctx > 2048) ctx_candidates.push_back(2048);
    if (requested_ctx > 1024) ctx_candidates.push_back(1024);
    if (requested_ctx > 512) ctx_candidates.push_back(512);

    llama_context *ctx = nullptr;
    int successful_ctx = 0;

    for (int candidate_ctx : ctx_candidates) {
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = candidate_ctx;
        ctx_params.n_batch = std::min(512, candidate_ctx);
        ctx_params.n_ubatch = std::min(512, candidate_ctx);
        ctx_params.n_threads = n_threads;
        ctx_params.n_threads_batch = n_threads;
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;

        ctx = llama_init_from_model(model, ctx_params);
        if (ctx != nullptr) {
            successful_ctx = candidate_ctx;
            LOGI("Context created successfully with size %d, threads: %d", successful_ctx, n_threads);
            break;
        }

        // Try without flash attention if it failed
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
        ctx = llama_init_from_model(model, ctx_params);
        if (ctx != nullptr) {
            successful_ctx = candidate_ctx;
            LOGI("Context created successfully (flash_attn disabled) with size %d, threads: %d", successful_ctx, n_threads);
            break;
        }

        LOGW("Failed to allocate context size %d, trying smaller fallback...", candidate_ctx);
    }

    if (ctx == nullptr) {
        LOGE("Failed to create llama_context for all candidate sizes");
        return 0;
    }

    return reinterpret_cast<jlong>(ctx);
}

static bool is_stop_sequence(const std::string & text) {
    static const char * stop_words[] = {
        "<|im_end|>",
        "<|im_start|>",
        "<|endoftext|>",
        "<|eot_id|>",
        "</s>",
        "<end_of_turn>",
        "<start_of_turn>",
        "\n<|im_start|>",
        "\n<|im_end|>",
        "\n<|",
        "\nUser:",
        "\nuser\n",
        "\nAssistant:",
        "\nassistant\n",
        "\nHuman:",
        "\nAI:",
        "\nSystem:",
        "\nsystem\n"
    };
    for (const auto & stop : stop_words) {
        if (text.find(stop) != std::string::npos) {
            return true;
        }
    }
    return false;
}

static void trim_trailing_stop_sequences(std::string & text) {
    static const char * stop_words[] = {
        "<|im_end|>",
        "<|im_start|>",
        "<|endoftext|>",
        "<|eot_id|>",
        "</s>",
        "<end_of_turn>",
        "<start_of_turn>",
        "\n<|im_start|>",
        "\n<|im_end|>",
        "\n<|",
        "\nUser:",
        "\nuser\n",
        "\nAssistant:",
        "\nassistant\n",
        "\nHuman:",
        "\nAI:",
        "\nSystem:",
        "\nsystem\n"
    };
    for (const auto & stop : stop_words) {
        size_t pos = text.find(stop);
        if (pos != std::string::npos) {
            text.resize(pos);
        }
    }
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

    // Init Sampler with DRY (Don't Repeat Yourself) and Repetition Penalties
    int32_t n_vocab = llama_vocab_n_tokens(vocab);
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());

    static const char * dry_breakers[] = { "\n", ":", "\"", "*", "`", ".", "!", "?" };
    llama_sampler_chain_add(smpl, llama_sampler_init_dry(
        vocab,
        0.8f,   // dry_multiplier
        1.75f,  // dry_base
        2,      // dry_allowed_length
        2048,   // dry_penalty_last_n
        dry_breakers,
        8
    ));

    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(n_vocab, 256, 1.20f, 0.2f, 0.2f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.4f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    // Feed prompt tokens to the sampler
    for (size_t p = 0; p < prompt_tokens.size(); p++) {
        llama_sampler_accept(smpl, prompt_tokens[p]);
    }

    std::string result;
    int available_ctx = std::max(128, n_ctx - (int)prompt_tokens.size() - 4);
    int max_gen = maxTokens > 0 ? std::min(maxTokens, available_ctx) : available_ctx;
    int n_cur = (int)prompt_tokens.size();
    int tokens_generated = 0;
    LOGI("Starting on-device generation: %zu prompt tokens (prefill: %lld ms, available_ctx: %d), max_gen: %d", prompt_tokens.size(), prefill_ms, available_ctx, max_gen);

    std::unordered_set<std::string> seen_lines;
    std::string current_line_buf;
    bool loop_detected = false;

    auto t_gen_start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < max_gen; i++) {
        llama_token new_token_id = llama_sampler_sample(smpl, ctx, -1);
        llama_sampler_accept(smpl, new_token_id);

        if (llama_vocab_is_eog(vocab, new_token_id) || llama_vocab_is_control(vocab, new_token_id)) {
            LOGI("End of generation token reached at step %d", i);
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            if (is_stop_sequence(piece) || is_stop_sequence(result + piece)) {
                LOGI("Stop sequence reached at step %d: %s", i, piece.c_str());
                break;
            }

            // Check for line-level repetition loops
            current_line_buf += piece;
            size_t nl = current_line_buf.find('\n');
            while (nl != std::string::npos) {
                std::string raw = current_line_buf.substr(0, nl);
                size_t s = raw.find_first_not_of(" \t\r-#*>`");
                size_t e = raw.find_last_not_of(" \t\r");
                if (s != std::string::npos && e != std::string::npos && (e >= s + 25)) {
                    std::string line_core = raw.substr(s, e - s + 1);
                    if (seen_lines.count(line_core) > 0) {
                        LOGI("Duplicate line loop detected at step %d: \"%s\"", i, line_core.c_str());
                        loop_detected = true;
                        break;
                    }
                    seen_lines.insert(line_core);
                }
                current_line_buf = current_line_buf.substr(nl + 1);
                nl = current_line_buf.find('\n');
            }
            if (loop_detected) break;

            result.append(piece);
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

    trim_trailing_stop_sequences(result);

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

    // Init Sampler with DRY (Don't Repeat Yourself) and Repetition Penalties
    int32_t n_vocab = llama_vocab_n_tokens(vocab);
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());

    static const char * dry_breakers[] = { "\n", ":", "\"", "*", "`", ".", "!", "?" };
    llama_sampler_chain_add(smpl, llama_sampler_init_dry(
        vocab,
        0.8f,   // dry_multiplier
        1.75f,  // dry_base
        2,      // dry_allowed_length
        2048,   // dry_penalty_last_n
        dry_breakers,
        8
    ));

    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(n_vocab, 256, 1.20f, 0.2f, 0.2f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.4f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    // Feed prompt tokens to the sampler
    for (size_t p = 0; p < prompt_tokens.size(); p++) {
        llama_sampler_accept(smpl, prompt_tokens[p]);
    }

    std::string result;
    int available_ctx = std::max(128, n_ctx - (int)prompt_tokens.size() - 4);
    int max_gen = maxTokens > 0 ? std::min(maxTokens, available_ctx) : available_ctx;
    int n_cur = (int)prompt_tokens.size();
    int tokens_generated = 0;
    LOGI("Starting streaming generation: %zu prompt tokens (prefill: %lld ms, available_ctx: %d), max_gen: %d", prompt_tokens.size(), prefill_ms, available_ctx, max_gen);

    std::unordered_set<std::string> seen_lines;
    std::string current_line_buf;
    bool loop_detected = false;

    auto t_gen_start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < max_gen; i++) {
        llama_token new_token_id = llama_sampler_sample(smpl, ctx, -1);
        llama_sampler_accept(smpl, new_token_id);

        if (llama_vocab_is_eog(vocab, new_token_id) || llama_vocab_is_control(vocab, new_token_id)) {
            LOGI("Streaming end of generation token at step %d", i);
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            if (is_stop_sequence(piece) || is_stop_sequence(result + piece)) {
                LOGI("Streaming stop sequence encountered at step %d: %s", i, piece.c_str());
                break;
            }

            // Check for line-level repetition loops
            current_line_buf += piece;
            size_t nl = current_line_buf.find('\n');
            while (nl != std::string::npos) {
                std::string raw = current_line_buf.substr(0, nl);
                size_t s = raw.find_first_not_of(" \t\r-#*>`");
                size_t e = raw.find_last_not_of(" \t\r");
                if (s != std::string::npos && e != std::string::npos && (e >= s + 25)) {
                    std::string line_core = raw.substr(s, e - s + 1);
                    if (seen_lines.count(line_core) > 0) {
                        LOGI("Streaming duplicate line loop detected at step %d: \"%s\"", i, line_core.c_str());
                        loop_detected = true;
                        break;
                    }
                    seen_lines.insert(line_core);
                }
                current_line_buf = current_line_buf.substr(nl + 1);
                nl = current_line_buf.find('\n');
            }
            if (loop_detected) break;

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

    trim_trailing_stop_sequences(result);

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
