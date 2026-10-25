#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <termios.h>

static int g_master_fd = -1;
static pid_t g_child_pid = -1;

JNIEXPORT jint JNICALL
Java_com_silverdev_sdsterminal_PtyNative_openPty(
    JNIEnv *env, jobject thiz, jstring shellPath,
    jobjectArray args, jobjectArray envVars) {
    int slave_fd = -1;
    if (openpty(&g_master_fd, &slave_fd, NULL, NULL, NULL) < 0) return -1;
    g_child_pid = fork();
    if (g_child_pid == 0) {
        setsid();
        ioctl(slave_fd, TIOCSCTTY, 0);
        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);
        close(slave_fd); close(g_master_fd);
        const char *path = (*env)->GetStringUTFChars(env, shellPath, NULL);
        int argc = (*env)->GetArrayLength(env, args);
        char **argv = malloc((argc + 1) * sizeof(char *));
        for (int i = 0; i < argc; i++) {
            jstring js = (jstring)(*env)->GetObjectArrayElement(env, args, i);
            argv[i] = (char *)(*env)->GetStringUTFChars(env, js, NULL);
        }
        argv[argc] = NULL;
        int envc = (*env)->GetArrayLength(env, envVars);
        for (int i = 0; i < envc; i++) {
            jstring js = (jstring)(*env)->GetObjectArrayElement(env, envVars, i);
            const char *ev = (*env)->GetStringUTFChars(env, js, NULL);
            putenv((char *)ev);
        }
        execvp(path, argv);
        _exit(127);
    }
    close(slave_fd);
    fcntl(g_master_fd, F_SETFL, O_NONBLOCK);
    return g_master_fd;
}

JNIEXPORT jint JNICALL
Java_com_silverdev_sdsterminal_PtyNative_read(JNIEnv *env, jobject thiz, jint fd, jbyteArray buffer) {
    jsize len = (*env)->GetArrayLength(env, buffer);
    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, NULL);
    ssize_t n = read(fd, buf, len);
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) return 0;
    return (jint)n;
}

JNIEXPORT jint JNICALL
Java_com_silverdev_sdsterminal_PtyNative_write(JNIEnv *env, jobject thiz, jint fd, jbyteArray data, jint len) {
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    ssize_t n = write(fd, buf, len);
    (*env)->ReleaseByteArrayElements(env, data, buf, 0);
    return (jint)n;
}

JNIEXPORT jint JNICALL
Java_com_silverdev_sdsterminal_PtyNative_setWindowSize(JNIEnv *env, jobject thiz, jint fd, jint rows, jint cols) {
    struct winsize ws;
    ws.ws_row = (unsigned short)rows; ws.ws_col = (unsigned short)cols;
    ws.ws_xpixel = 0; ws.ws_ypixel = 0;
    return ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT void JNICALL
Java_com_silverdev_sdsterminal_PtyNative_close(JNIEnv *env, jobject thiz, jint fd) {
    if (fd >= 0) close(fd);
    if (g_child_pid > 0) { kill(g_child_pid, SIGHUP); waitpid(g_child_pid, NULL, WNOHANG); g_child_pid = -1; }
    g_master_fd = -1;
}