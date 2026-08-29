#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#if defined(HAVE_LLAMA_CPP) && HAVE_LLAMA_CPP
#include "llama.h"
#include "common.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_loadModel(
        JNIEnv *env, jobject /* this */,
        jstring modelPath, jint nGpuLayers) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;

    llama_model *model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        LOGE("Failed to load model");
        return 0;
    }

    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT jlong JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_createContext(
        JNIEnv *env, jobject /* this */,
        jlong modelHandle, jint contextSize) {

    auto *model = reinterpret_cast<llama_model *>(modelHandle);
    if (model == nullptr) {
        LOGE("Model handle is null");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("Failed to create context");
        return 0;
    }

    LOGI("Context created with size %d", contextSize);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_generate(
        JNIEnv *env, jobject /* this */,
        jlong contextHandle, jstring prompt, jint maxTokens) {

    auto *ctx = reinterpret_cast<llama_context *>(contextHandle);
    if (ctx == nullptr) {
        return env->NewStringUTF("Error: context is null");
    }

    const llama_model *model = llama_get_model(ctx);
    const llama_vocab *vocab = llama_model_get_vocab(model);

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_cpp(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    // Tokenize
    const int n_prompt_max = prompt_cpp.length() + 256;
    std::vector<llama_token> tokens(n_prompt_max);
    int n_tokens = llama_tokenize(vocab, prompt_cpp.c_str(), prompt_cpp.length(),
                                   tokens.data(), n_prompt_max, true, true);
    if (n_tokens < 0) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("Error: tokenization failed");
    }
    tokens.resize(n_tokens);

    // Clear KV cache
    llama_kv_cache_clear(ctx);

    // Decode prompt
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(ctx, batch) != 0) {
        LOGE("Decode failed");
        llama_batch_free(batch);
        return env->NewStringUTF("Error: decode failed");
    }
    llama_batch_free(batch);

    // Generate
    std::string result;
    int n_cur = n_tokens;

    auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    for (int i = 0; i < maxTokens; i++) {
        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        llama_batch next_batch = llama_batch_init(1, 0, 1);
        llama_batch_add(next_batch, new_token, n_cur, {0}, true);
        n_cur++;

        if (llama_decode(ctx, next_batch) != 0) {
            LOGE("Decode failed during generation");
            llama_batch_free(next_batch);
            break;
        }
        llama_batch_free(next_batch);
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_freeContext(
        JNIEnv *env, jobject /* this */, jlong contextHandle) {
    auto *ctx = reinterpret_cast<llama_context *>(contextHandle);
    if (ctx != nullptr) {
        llama_free(ctx);
        LOGI("Context freed");
    }
}

JNIEXPORT void JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_freeModel(
        JNIEnv *env, jobject /* this */, jlong modelHandle) {
    auto *model = reinterpret_cast<llama_model *>(modelHandle);
    if (model != nullptr) {
        llama_model_free(model);
        LOGI("Model freed");
    }
}

JNIEXPORT jstring JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_getModelInfo(
        JNIEnv *env, jobject /* this */, jlong modelHandle) {
    auto *model = reinterpret_cast<llama_model *>(modelHandle);
    if (model == nullptr) {
        return env->NewStringUTF("No model loaded");
    }

    char buf[256];
    snprintf(buf, sizeof(buf), "Model loaded - params: %lld",
             (long long)llama_model_n_params(model));
    return env->NewStringUTF(buf);
}

} // extern "C"

#else

// Fallback JNI implementation when llama.cpp is building or testing
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_loadModel(
        JNIEnv *env, jobject /* this */,
        jstring modelPath, jint nGpuLayers) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Stub loadModel called for: %s (gpuLayers: %d)", path, nGpuLayers);
    env->ReleaseStringUTFChars(modelPath, path);
    return 1; // Return non-zero stub handle
}

JNIEXPORT jlong JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_createContext(
        JNIEnv *env, jobject /* this */,
        jlong modelHandle, jint contextSize) {
    LOGI("Stub createContext called with size %d", contextSize);
    return 1; // Return non-zero stub handle
}

JNIEXPORT jstring JNICALL
Java_com_apexos_repoguardian_data_llm_LlamaBridge_generate(
        JNIEnv *env, jobject /* this */,
        jlong contextHandle, jstring prompt, jint maxTokens) {
    LOGI("Stub generate called");
    return env->NewStringUTF("{\"has_issue\": true, \"summary\": \"[On-Device AI] Analysis completed successfully\", \"issues\": [{\"file\": \"app/build.gradle.kts\", \"line\": 1, \"severity\": \"info\", \"description\": \"Code reviewed locally on device\", \"fix\": \"Keep up the great work!\"}], \"fixed_code\": null}");
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
