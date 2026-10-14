/*
 * Local Route Android JNI lifecycle for ByeDPI.
 * Adapted from the GPL-3.0 ByeDPIAndroid and ByeByeDPI JNI integrations;
 * Local Route modifications are licensed GPL-3.0-or-later.
 * See app/src/main/assets/licenses/NOTICE.md and GPL-3.0.txt.
 */
#include <errno.h>
#include <getopt.h>
#include <jni.h>
#include <netinet/in.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>

#include "byedpi/error.h"
#include "byedpi/params.h"

int main(int argc, char **argv);

extern int server_fd;
static pthread_mutex_t proxy_mutex = PTHREAD_MUTEX_INITIALIZER;
static int proxy_started;
static int proxy_running;

/* Android services restart strategies inside one process. Upstream's command
 * entry point normally runs once per process, so restore its scalar defaults
 * before every run after clear_params() has freed the dynamic fields. */
static void reset_proxy_defaults(void) {
    memset(&params, 0, sizeof(params));
    params.await_int = 10;
    params.ipv6 = 1;
    params.resolve = 1;
    params.udp = 1;
    params.max_open = 512;
    params.bfsize = 16384;
    params.baddr.in6.sin6_family = AF_INET6;
    params.laddr.in.sin_family = AF_INET;
    params.debug = 0;
    optind = 1;
}

int __wrap_daemon(__attribute__((unused)) int nochdir, __attribute__((unused)) int noclose) {
    LOG(LOG_S, "daemon mode is not supported in the Android worker");
    errno = EPERM;
    return -1;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniStartProxy(
        JNIEnv *env, __attribute__((unused)) jobject thiz, jobjectArray args) {
    pthread_mutex_lock(&proxy_mutex);
    if (proxy_started) {
        pthread_mutex_unlock(&proxy_mutex);
        return -1;
    }
    proxy_started = 1;
    reset_proxy_defaults();
    pthread_mutex_unlock(&proxy_mutex);

    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc((size_t) argc + 1, sizeof(char *));
    int result = -1;
    if (!argv) goto finished;

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        if (!arg) goto cleanup;
        const char *value = (*env)->GetStringUTFChars(env, arg, NULL);
        if (value) {
            argv[i] = strdup(value);
            (*env)->ReleaseStringUTFChars(env, arg, value);
        }
        (*env)->DeleteLocalRef(env, arg);
        if (!argv[i]) goto cleanup;
    }

    pthread_mutex_lock(&proxy_mutex);
    server_fd = -1;
    proxy_running = 1;
    pthread_mutex_unlock(&proxy_mutex);
    result = main(argc, argv);

cleanup:
    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);
finished:
    pthread_mutex_lock(&proxy_mutex);
    proxy_running = 0;
    proxy_started = 0;
    server_fd = -1;
    pthread_mutex_unlock(&proxy_mutex);
    return result;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniStopProxy(
        __attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    pthread_mutex_lock(&proxy_mutex);
    int result = proxy_running && server_fd >= 0 ? shutdown(server_fd, SHUT_RDWR) : 0;
    pthread_mutex_unlock(&proxy_mutex);
    return result;
}
