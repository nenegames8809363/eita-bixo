package com.caos.minecraft2;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {
    private GLSurfaceView view;
    private float ang = 0f;
    private FloatBuffer buf;

    static final float[] CUBO = {
        -1,-1, 1,  1,-1, 1,  1, 1, 1,  -1,-1, 1,  1, 1, 1,  -1, 1, 1,
         1,-1,-1, -1,-1,-1, -1, 1,-1,   1,-1,-1, -1, 1,-1,   1, 1,-1,
        -1,-1,-1, -1,-1, 1, -1, 1, 1,  -1,-1,-1, -1, 1, 1,  -1, 1,-1,
         1,-1, 1,  1,-1,-1,  1, 1,-1,   1,-1, 1,  1, 1,-1,   1, 1, 1,
        -1, 1, 1,  1, 1, 1,  1, 1,-1,  -1, 1, 1,  1, 1,-1,  -1, 1,-1,
        -1,-1,-1,  1,-1,-1,  1,-1, 1,  -1,-1,-1,  1,-1, 1,  -1,-1, 1
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buf = ByteBuffer.allocateDirect(CUBO.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buf.put(CUBO);
        buf.position(0);

        view = new GLSurfaceView(this);
        view.setRenderer(new GLSurfaceView.Renderer() {
            public void onSurfaceCreated(GL10 gl, EGLConfig c) {
                gl.glClearColor(0.53f, 0.81f, 0.92f, 1f);
                gl.glEnable(GL10.GL_DEPTH_TEST);
            }
            public void onSurfaceChanged(GL10 gl, int w, int h) {
                gl.glViewport(0, 0, w, h);
                gl.glMatrixMode(GL10.GL_PROJECTION);
                gl.glLoadIdentity();
                float r = (float) w / h;
                gl.glFrustumf(-r, r, -1, 1, 1, 100);
            }
            public void onDrawFrame(GL10 gl) {
                gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
                gl.glMatrixMode(GL10.GL_MODELVIEW);
                gl.glLoadIdentity();
                gl.glTranslatef(0, 0, -5);
                gl.glRotatef(ang, 1, 1, 0);
                gl.glColor4f(0.3f, 0.7f, 0.3f, 1f);
                gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
                gl.glVertexPointer(3, GL10.GL_FLOAT, 0, buf);
                gl.glDrawArrays(GL10.GL_TRIANGLES, 0, 36);
                ang += 0.8f;
            }
        });
        setContentView(view);
    }

    @Override
    protected void onPause() { super.onPause(); view.onPause(); }
    @Override
    protected void onResume() { super.onResume(); view.onResume(); }
}
