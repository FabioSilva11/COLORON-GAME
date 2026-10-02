package com.coloron3d.game.engine

/** Ponte JNI para o motor C++ (libcoloron3d.so). */
object NativeBridge {
    init { System.loadLibrary("coloron3d") }

    external fun nativeInit()
    external fun nativeResize(width: Int, height: Int)
    external fun nativeFrame(dt: Float)
    external fun nativeStart(skin: Int)
    external fun nativeSelectColor(color: Int)
    external fun nativeState(): IntArray
}

data class GameSnapshot(
    val state: Int = 0,
    val score: Int = 0,
    val lives: Int = 3,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val gems: Int = 0,
    val ballColor: Int = 0,
) {
    val playing get() = state == 1
    val over get() = state == 2

    companion object {
        fun from(a: IntArray) = GameSnapshot(a[0], a[1], a[2], a[3], a[4], a[5], a[6])
    }
}
