package se.embargo.onebit.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import se.embargo.core.graphics.ShaderProgram;
import se.embargo.onebit.R;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

public class PreviewShader implements IRenderStage, SurfaceTexture.OnFrameAvailableListener {
	public static final int SHADER_SOURCE_ID = R.raw.image_vertex_shader;    
	
	private static final String TAG = "PreviewShader";
    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

    private ShaderProgram _program;

    private final float[] _vertices = {
        // X, Y, Z, U, V
    	-1.0f,  1.0f, 0.0f, 0.0f, 1.0f,		// Top left
    	 1.0f,  1.0f, 0.0f, 1.0f, 1.0f,		// Top right
        -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,		// Bottom left
         1.0f, -1.0f, 0.0f, 1.0f, 0.0f		// Bottom right
    };
    private FloatBuffer _vertexbuf;

    private int _previewTextureHandle, _previewTextureLocation;
    private SurfaceTexture _previewTexture;
    private boolean _updateSurface = false;

    private int muMVPMatrixHandle;
    private int maPositionHandle;
    private int maTextureCoordHandle;
    private int muSTMatrixHandle;
    private int muCRatioHandle;

    private float[] mMVPMatrix = new float[16];
    private float[] mVMatrix = new float[16];
    private float[] mSTMatrix = new float[16];
    private float[] mProjMatrix = new float[16];

    private float _cRatio;
    
    /**
     * @param program	Shader program built from SHADER_SOURCE_ID
     * @param size		Size of camera preview frames
     */
    public PreviewShader(ShaderProgram program, Camera.Size previewSize, int surfaceWidth, int surfaceHeight) {
    	_program = program;
    	_cRatio = (float)previewSize.width / previewSize.height;
        
        // Allocate buffer to hold vertices
    	_vertexbuf = ByteBuffer.allocateDirect(_vertices.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        _vertexbuf.put(_vertices).position(0);

        // Find handles to shader parameters
        maPositionHandle = _program.getAttributeLocation("aPosition");
        maTextureCoordHandle = _program.getAttributeLocation("aTextureCoord");
        muMVPMatrixHandle = _program.getUniformLocation("uMVPMatrix");
        muSTMatrixHandle = _program.getUniformLocation("uSTMatrix");
        muCRatioHandle = _program.getUniformLocation("uCRatio");
        _previewTextureLocation = _program.getUniformLocation("sTexture");
        
        // Create the external texture which the camera preview is written to
        int[] textures = new int[1];
        GLES20.glGenTextures(textures.length, textures, 0);

        _previewTextureHandle = textures[0];
        _previewTexture = new SurfaceTexture(_previewTextureHandle);
        _previewTexture.setOnFrameAvailableListener(this);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, _previewTextureHandle);
        checkGlError("glBindTexture");

        // No mip-mapping with camera source
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        
        // Clamp to edge is only option
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        /*
        Bitmap bm = BitmapFactory.decodeResource(_context.getResources(), R.raw.david);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bm, 0);
        */
        
        // Set the viewpoint
        Matrix.setLookAtM(mVMatrix, 0, 0, 0, 1.45f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
        Matrix.setIdentityM(mSTMatrix, 0);

        // Set the screen ratio projection 
        float ratio = (float)surfaceWidth / surfaceHeight;
        Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 1f, 10);

        // Apply the screen ratio projection
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);
    }
    
    public SurfaceTexture getPreviewTexture() {
    	return _previewTexture;
    }
    
    @Override
    public void draw() {
    	// Check if a new frame is available
    	synchronized (this) {
	    	if (_updateSurface) {
		    	_previewTexture.updateTexImage();
		    	_previewTexture.getTransformMatrix(mSTMatrix);
		    	_updateSurface = false;
	    	}
    	}
    	
    	// Transfer the screen ratio projection
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
        GLES20.glUniform1f(muCRatioHandle, _cRatio);

        // Bind the preview texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, _previewTextureHandle);
        GLES20.glUniform1i(_previewTextureLocation, 0);
        checkGlError("glBindTexture");
    
        // Prepare the triangles
        _vertexbuf.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(
        	maPositionHandle, 3, GLES20.GL_FLOAT, false,
        	TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vertexbuf);
        checkGlError("glVertexAttribPointer maPosition");
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");
        
        _vertexbuf.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(
        	maTextureCoordHandle, 2, GLES20.GL_FLOAT, false,
        	TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vertexbuf);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureCoordHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");

        // Draw two triangles to form a square
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 1, 3);
        checkGlError("glDrawArrays");
    }
    
	@Override
	public synchronized void onFrameAvailable(SurfaceTexture surfaceTexture) {
		_updateSurface = true;
	}
    
	private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }
}
