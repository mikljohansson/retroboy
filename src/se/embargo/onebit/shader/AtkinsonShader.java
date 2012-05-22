package se.embargo.onebit.shader;

import se.embargo.core.graphics.ShaderProgram;
import se.embargo.onebit.R;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.util.Log;

public class AtkinsonShader implements IRenderStage {
    public static final int SHADER_SOURCE_ID = R.raw.image_atkinson_shader;
	
	private static final String TAG = "AtkinsonShader";
    private static final int TARGET_WIDTH = 480;
    
    private ShaderProgram _program;
    private int _targetSizeLocation;
    private int[] _targetSize;

	public AtkinsonShader(ShaderProgram program, Camera.Size previewSize) {
		_program = program;
		
        // Find handles to shader parameters
	    _targetSizeLocation = _program.getUniformLocation("uTargetSize");
	    
        // Scale the preview frames to match TARGET_WIDTH
        _targetSize = new int[] {TARGET_WIDTH, (int)((float)TARGET_WIDTH * ((float)previewSize.height / previewSize.width))};
	}
	
	@Override
	public void draw() {
        // Transfer the output image size
        GLES20.glUniform2f(_targetSizeLocation, _targetSize[0], _targetSize[1]);
        checkGlError("glUniform2f sTargetSize");
	}

	private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }
}
