package com.coloron3d.game.engine

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** GLSurfaceView que delega render e lógica ao C++ e publica o estado a cada frame. */
@SuppressLint("ViewConstructor")
class GameSurfaceView(
    context: Context,
    private val onState: (GameSnapshot) -> Unit,
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        preserveEGLContextOnPause = true
        setRenderer(object : Renderer {
            private var last = System.nanoTime()
            private var lastSnapshot = GameSnapshot()

            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                NativeBridge.nativeInit()
                last = System.nanoTime()
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) =
                NativeBridge.nativeResize(width, height)

            override fun onDrawFrame(gl: GL10?) {
                val now = System.nanoTime()
                NativeBridge.nativeFrame((now - last) / 1e9f)
                last = now
                val snap = GameSnapshot.from(NativeBridge.nativeState())
                if (snap != lastSnapshot) {
                    lastSnapshot = snap
                    onState(snap)
                }
            }
        })
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun startGame(skin: Int) = queueEvent { NativeBridge.nativeStart(skin) }
    fun selectColor(c: Int) = queueEvent { NativeBridge.nativeSelectColor(c) }
}
