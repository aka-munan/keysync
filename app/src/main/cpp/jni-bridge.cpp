//
// Created by munan on 01/02/26.
//

//#include <jni.h>
////#include "thread_loop.h"
//
//extern "C"
//JNIEXPORT void JNICALL
//Java_com_keysync_NativeBridge_start(
//        JNIEnv* env,
//        jobject /*thiz*/,
//        jstring path
//) {
//    const char* p = env->GetStringUTFChars(path, nullptr);
////    start_loop(p);
//    env->ReleaseStringUTFChars(path, p);
//}
//
//extern "C"
//JNIEXPORT void JNICALL
//Java_com_keysync_NativeBridge_stop(
//        JNIEnv* env,
//        jobject /*thiz*/
//) {
////    stop_loop();
//}

#include <jni.h>
//#include <cstring>
//
//
//#include <linux/uinput.h>
#include <cstring>
#include <android/log.h>
#include "InputReader.h"
#include "KeyboardDetector.h"
#include <vector>

jobject g_callback = nullptr;
jmethodID g_key_down_id = nullptr;
jmethodID g_key_up_id = nullptr;
JNIEnv *g_env = nullptr;

void onEventDown(int code);

void onEventUp(int code);


InputReader inputReader;


extern "C"
JNIEXPORT jint JNICALL
Java_com_devoid_keysync_service_InputReaderService_readInput(JNIEnv *env, jobject thiz, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    int fd = inputReader.open_input_device(p);
    env->ReleaseStringUTFChars(path, p);
    inputReader.run(fd, onEventDown, onEventUp);
    return fd;
}


void onEventDown(int code) {
    if (g_env == nullptr) return;
    g_env->CallVoidMethod(g_callback, g_key_down_id, code);
}

void onEventUp(int code) {
    if (g_env == nullptr) return;
    g_env->CallVoidMethod(g_callback, g_key_up_id, code);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_devoid_keysync_service_InputReaderService_attachCallbackToNative(JNIEnv *env, jobject thiz, jobject callback) {
    g_callback = env->NewGlobalRef(callback);
    jclass callbackClass = env->GetObjectClass(callback);
    if (callbackClass == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "native", "Error: Could not find Callback class");
        return 1;
    }
    g_env = env;
    g_key_down_id = env->GetMethodID(callbackClass, "onKeyDown", "(I)V");
    g_key_up_id = env->GetMethodID(callbackClass, "onKeyUp", "(I)V");
    env->DeleteLocalRef(callbackClass);

    __android_log_print(ANDROID_LOG_INFO, "native", "callback attached: %p", g_callback);
    return 0;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_devoid_keysync_service_InputReaderService_getKeyboardDevicePath(JNIEnv *env, jobject thiz) {
    KeyboardDetector keyboardDetector;
    std::vector<KeyboardDeviceInfo> keyboards = keyboardDetector.scanInputDevices();
    if (keyboards.empty()) {
        return nullptr;
    }
    std::string pathCtr = keyboards[0].path;
    return env->NewStringUTF(pathCtr.c_str());
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_devoid_keysync_service_InputReaderService_nativeGrabActiveDevice(JNIEnv *env, jobject thiz) {
    return inputReader.grabActiveDevice();
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_devoid_keysync_service_InputReaderService_nativeReleaseActiveDevice(JNIEnv *env, jobject thiz) {
    return inputReader.releaseActiveDevice();
}