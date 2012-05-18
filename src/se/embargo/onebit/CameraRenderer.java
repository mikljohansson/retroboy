package se.embargo.onebit;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import se.embargo.onebit.filter.IPreviewShader;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

public class CameraRenderer implements GLSurfaceView.Renderer {
    private Context _context;
    private IPreviewShader _shader;
    
    private Camera _camera = null;
    private Camera.Size _previewSize;
    
    public CameraRenderer(Context context) {
        _context = context;
    }
    
    public SurfaceTexture getPreviewTexture() {
    	return _shader.getPreviewTexture();
    }

    public void setCamera(Camera camera) {
        _camera = camera;
        
        Camera.Size size = camera.getParameters().getPreviewSize();        
        _shader.setPreviewSize(size.width, size.height);
    }
    
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
    	GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
    	
        // Start the preview
		try {
			_camera.setPreviewTexture(_shader.getPreviewTexture());
			_camera.startPreview();
		}
		catch (IOException e) {}
    }

    public void onDrawFrame(GL10 glUnused) {
    	GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        _shader.draw();
    }

    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        
        float ratio = (float)width / height;
        Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 1f, 10);
    }


	private Buffer createScaledTexture(byte[] texture, int sourcex, int sourcey, int targetx, int targety) {
		byte[] result = new byte[targetx * targety];
		for (int y = 0; y < targety; y++) {
			for (int x = 0; x < targetx; x++) {
				result[x + y * targetx] = texture[x % sourcex + (y % sourcey) * sourcex];
			}
		}
		
		return ByteBuffer.wrap(result);
	}
}
