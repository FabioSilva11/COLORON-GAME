#include <jni.h>
#include <chrono>
#include <memory>
#include <mutex>
#include "GameEngine.h"
#include "Renderer.h"

namespace {
std::unique_ptr<GameEngine> gGame;
std::unique_ptr<Renderer> gRenderer;
std::mutex gMutex;
}

#define JNI_FN(name) Java_com_coloron3d_game_engine_NativeBridge_##name

extern "C" {

JNIEXPORT void JNICALL JNI_FN(nativeInit)(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gGame) gGame = std::make_unique<GameEngine>();
    gRenderer = std::make_unique<Renderer>(); // novo contexto GL => recria recursos
    gRenderer->init();
}

JNIEXPORT void JNICALL JNI_FN(nativeResize)(JNIEnv*, jobject, jint w, jint h) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gRenderer) gRenderer->resize(w, h);
}

JNIEXPORT void JNICALL JNI_FN(nativeFrame)(JNIEnv*, jobject, jfloat dt) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gGame || !gRenderer) return;
    gGame->update(dt);
    gRenderer->draw(*gGame);
}

JNIEXPORT void JNICALL JNI_FN(nativeStart)(JNIEnv*, jobject, jint skin) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gGame) gGame->start(skin);
}

JNIEXPORT void JNICALL JNI_FN(nativeSelectColor)(JNIEnv*, jobject, jint c) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gGame) gGame->selectColor(c);
}

// [estado, pontos, vidas, combo, melhorCombo, gemas, corDaBola]
JNIEXPORT jintArray JNICALL JNI_FN(nativeState)(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    jint v[7] = {0};
    if (gGame) {
        v[0] = (jint)gGame->state; v[1] = gGame->score; v[2] = gGame->lives;
        v[3] = gGame->combo; v[4] = gGame->bestCombo; v[5] = gGame->gems; v[6] = gGame->ballColor;
    }
    jintArray arr = env->NewIntArray(7);
    env->SetIntArrayRegion(arr, 0, 7, v);
    return arr;
}

}
