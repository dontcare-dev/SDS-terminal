package com.silverdev.sdsterminal

object PtyNative {
    init { System.loadLibrary("ptyjni") }
    external fun openPty(shellPath: String, args: Array<String>, envVars: Array<String>): Int
    external fun read(fd: Int, buffer: ByteArray): Int
    external fun write(fd: Int, data: ByteArray, len: Int): Int
    external fun setWindowSize(fd: Int, rows: Int, cols: Int): Int
    external fun close(fd: Int)
}