package se.embargo.onebit.shader;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import se.embargo.core.graphics.ShaderProgram;
import se.embargo.onebit.R;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.util.Log;

public class BayerShader implements IRenderStage {
    public static final int SHADER_SOURCE_ID = R.raw.image_bayer_shader;
	
	private static final String TAG = "BayerShader";
    private static final int TARGET_WIDTH = 480;
    
    private static final byte[] _bayerThresholdMatrix = new byte[] {
    	0, 32, 8, 40, 2, 34, 10, 42, 
    	48, 16, 56, 24, 50, 18, 58, 26, 
    	12, 44, 4, 36, 14, 46, 6, 38, 
    	60, 28, 52, 20, 62, 30, 54, 22, 
    	3, 35, 11, 43, 1, 33, 9, 41, 
    	51, 19, 59, 27, 49, 17, 57, 25, 
    	15, 47, 7, 39, 13, 45, 5, 37, 
    	63, 31, 55, 23, 61, 29, 53, 21
    	};
	
    private ShaderProgram _program;
    private int _thresholdTextureHandle, _thresholdTextureLocation, _targetSizeLocation;
    private int[] _targetSize;

	public BayerShader(ShaderProgram program, Camera.Size previewSize) {
		_program = program;
		
        // Find handles to shader parameters
		_thresholdTextureLocation = _program.getUniformLocation("sThreshold");
	    _targetSizeLocation = _program.getUniformLocation("uTargetSize");
	    
        // Scale the preview frames to match TARGET_WIDTH
        _targetSize = new int[] {TARGET_WIDTH, (int)((float)TARGET_WIDTH * ((float)previewSize.height / previewSize.width))};
        
        // Bind the Bayer threshold matrix as a texture
        int[] textures = new int[1];
        GLES20.glGenTextures(textures.length, textures, 0);

        _thresholdTextureHandle = textures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _thresholdTextureHandle);
        checkGlError("glBindTexture");

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        
        GLES20.glTexImage2D(
        	GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 
        	_targetSize[0], _targetSize[1], 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, 
        	createScaledTexture(_bayerThresholdMatrix, 8, 8, _targetSize[0], _targetSize[1]));
        checkGlError("glTexImage2D");
	}
	
	@Override
	public void draw() {
        // Bind the textures
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _thresholdTextureHandle);
        GLES20.glUniform1i(_thresholdTextureLocation, 1);
        checkGlError("glBindTexture");

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
