package se.embargo.onebit.filter;

import se.embargo.onebit.R;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;

public class BayerShader implements IPreviewShader {
    private PreviewShader _preview;
    
    private int _thresholdTextureHandle, _thresholdTextureLocation;
    private byte[] _thresholdTexture = new byte[] {
    	0, 32, 8, 40, 2, 34, 10, 42, 
    	48, 16, 56, 24, 50, 18, 58, 26, 
    	12, 44, 4, 36, 14, 46, 6, 38, 
    	60, 28, 52, 20, 62, 30, 54, 22, 
    	3, 35, 11, 43, 1, 33, 9, 41, 
    	51, 19, 59, 27, 49, 17, 57, 25, 
    	15, 47, 7, 39, 13, 45, 5, 37, 
    	63, 31, 55, 23, 61, 29, 53, 21
    	};
	
	public BayerShader(Context context) {
		_preview = new PreviewShader(context, R.raw.image_bayer_shader);
		
		_thresholdTextureLocation = _preview.getUniformLocation("sThreshold");
	    _targetSizeLocation = _preview.getUniformLocation("uTargetSize");
	    
        // Bind the Bayer threshold matrix as a texture
        _thresholdTextureHandle = textures[1];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _thresholdTextureHandle);
        checkGlError("glBindTexture");

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        
        GLES20.glTexImage2D(
        	GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 
        	_targetSize[0], _targetSize[1], 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, 
        	createScaledTexture(_thresholdTexture, 8, 8, _targetSize[0], _targetSize[1]));

	}

	@Override
	public void setPreviewSize(int width, int height) {
		_preview.setPreviewSize(width, height);
	}
	
	@Override
	public SurfaceTexture getPreviewTexture() {
		return _preview.getPreviewTexture();
	}
	
	@Override
	public void draw() {
        // Bind the textures
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _thresholdTextureHandle);
        GLES20.glUniform1i(_thresholdTextureLocation, 1);
        checkGlError("glBindTexture");
        
        _preview.draw();
	}
}
