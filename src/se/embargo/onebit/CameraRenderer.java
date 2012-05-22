package se.embargo.onebit;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import se.embargo.core.graphics.ShaderProgram;
import se.embargo.onebit.shader.AtkinsonShader;
import se.embargo.onebit.shader.IRenderStage;
import se.embargo.onebit.shader.PreviewShader;
import android.content.Context;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

public class CameraRenderer implements GLSurfaceView.Renderer {
    private Context _context;
	private Camera _camera;
    private ShaderProgram _program;
    
    private IRenderStage _shader;
    private PreviewShader _preview;
    
    public CameraRenderer(Context context) {
    	_context = context;
    }
    
    public void setCamera(Camera camera) {
    	_camera = camera;
    }
    
    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
    	// Turn off unneeded features 
    	GLES20.glDisable(GLES20.GL_BLEND);
    	GLES20.glDisable(GLES20.GL_CULL_FACE);
    	GLES20.glDisable(GLES20.GL_DITHER);
    	GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    	GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    	GLES20.glDisable(GLES20.GL_STENCIL_TEST);
    	GLES20.glDepthMask(false);

    	// Background color
    	GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    	
    	_program = new ShaderProgram(_context, PreviewShader.SHADER_SOURCE_ID, AtkinsonShader.SHADER_SOURCE_ID);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        // Start the preview
    	if (_camera != null) {
            Camera.Size previewSize = _camera.getParameters().getPreviewSize();        
        	_preview = new PreviewShader(_program, previewSize, width, height);
        	_shader = new AtkinsonShader(_program, previewSize);

        	try {
				_camera.setPreviewTexture(_preview.getPreviewTexture());
				_camera.startPreview();
			}
			catch (IOException e) {}
    	}
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
    	_program.draw();
    	_shader.draw();
        _preview.draw();
    }
}
