/*
 * Local Route JNI boundary based on ByeDPIAndroid v1.2.0.
 * GPL-3.0-or-later; see assets/licenses/NOTICE.md.
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>

#include "byedpi/error.h"
#include "byedpi/params.h"
#include "byedpi/proxy.h"
#include "main.h"
#include "utils.h"

JNIEXPORT jint JNI_OnLoad(
        __attribute__((unused)) JavaVM *vm,
        __attribute__((unused)) void *reserved) {
    default_params = params;
    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniCreateSocketWithCommandLine(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jobjectArray args) {
    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc((size_t) argc, sizeof(char *));
    if (!argv) return -1;

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *value = (*env)->GetStringUTFChars(env, arg, NULL);
        argv[i] = value ? strdup(value) : NULL;
        if (value) (*env)->ReleaseStringUTFChars(env, arg, value);
        (*env)->DeleteLocalRef(env, arg);
        if (!argv[i]) {
            for (int j = 0; j <= i; j++) free(argv[j]);
            free(argv);
            return -1;
        }
    }

    /*
     * ByeDPI's argument parser keeps pointers into argv for the lifetime of
     * the event loop.  Do not free them here.  The reference Android client
     * deliberately keeps this argv storage alive as well; freeing it caused
     * accepted SOCKS connections to close before sending a SOCKS response.
     */
    int parsed = parse_args(argc, argv);
    if (parsed < 0) {
        uniperror("parse_args");
        reset_params();
        return -1;
    }

    int fd = listen_socket((struct sockaddr_ina *)&params.laddr);
    if (fd < 0) {
        uniperror("listen_socket");
        reset_params();
    }
    return fd;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniStartProxy(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jint fd) {
    NOT_EXIT = 1;
    if (event_loop(fd) < 0) {
        uniperror("event_loop");
        return get_e();
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniStopProxy(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jint fd) {
    int result = fd >= 0 ? shutdown(fd, SHUT_RDWR) : 0;
    reset_params();
    return result;
}
