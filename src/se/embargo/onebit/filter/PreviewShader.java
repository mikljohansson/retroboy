package se.embargo.onebit.filter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import se.embargo.core.io.Streams;
import se.embargo.onebit.R;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

public class PreviewShader implements IPreviewShader, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "PreviewShader";

    private static final int TARGET_WIDTH = 480;
    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

    private Context _context;
    private int _program;

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

    private int[] _sourceSize, _targetSize;
    private int _targetSizeLocation;

    public PreviewShader(Context context, int fragmentShader) {
    	_context = context;
    	
        // Compile the shader program
    	_program = createProgram(R.raw.image_vertex_shader, fragmentShader);
        if (_program == 0) {
            return;
        }
        
        // Allocate buffer to hold vertices
    	_vertexbuf = ByteBuffer.allocateDirect(_vertices.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        _vertexbuf.put(_vertices).position(0);

        maPositionHandle = getAttributeLocation("aPosition");
        maTextureCoordHandle = getAttributeLocation("aTextureCoord");
        muMVPMatrixHandle = getUniformLocation("uMVPMatrix");

        Matrix.setIdentityM(mSTMatrix, 0);
        muSTMatrixHandle = getUniformLocation("uSTMatrix");

        muCRatioHandle = getUniformLocation("uCRatio");
        _previewTextureLocation = getUniformLocation("sTexture");
        
        // Create the external texture which the camera preview is written to
        int[] textures = new int[2];
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
	}

    @Override
    public void setPreviewSize(int width, int height) {
        _sourceSize = new int[] {width, height};
        _targetSize = new int[] {TARGET_WIDTH, (int)((float)TARGET_WIDTH * ((float)_sourceSize[1] / _sourceSize[0]))};
    }
    
    public int[] getTargetSize() {
    	return _targetSize;
    }
    
    @Override
    public SurfaceTexture getPreviewTexture() {
    	return _previewTexture;
    }
    
    @Override
    public void draw() {
    	synchronized (this) {
	    	if (_updateSurface) {
		    	_previewTexture.updateTexImage();
		    	_previewTexture.getTransformMatrix(mSTMatrix);
		    	_updateSurface = false;
	    	}
    	}
    	
        GLES20.glUseProgram(_program);
        checkGlError("glUseProgram");

        // Apply the screen ratio projection
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
        GLES20.glUniform1f(muCRatioHandle, (float)_sourceSize[0] / _sourceSize[1]);
    	
        // Transfer the texture sizes
        GLES20.glUniform2f(_targetSizeLocation, _targetSize[0], _targetSize[1]);
    
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
    
	private int getAttributeLocation(String name) {
		int handle = GLES20.glGetAttribLocation(_program, name);
        checkGlError("glGetAttribLocation " + name);
        if (handle == -1) {
            throw new RuntimeException("Could not get attrib location for " + name);
        }
        
        return handle;
	}
    
	private int getUniformLocation(String name) {
		int handle = GLES20.glGetUniformLocation(_program, name);
        checkGlError("glGetUniformLocation " + name);
        if (handle == -1) {
            throw new RuntimeException("Could not get uniform location for " + name);
        }
        
        return handle;
	}

	private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }
	
    private int loadShader(int shaderType, int sourceid) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            // Read the shader source from file
        	InputStream is = _context.getResources().openRawResource(sourceid);
            String source;
			try {
				source = Streams.toString(is);
			}
			catch (IOException e) {
				return 0;
			}
        	
        	// Compile shader
            int[] compileStatus = new int[1];
			GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        
        return shader;
    }

    private int createProgram(int vertexid, int fragmentid) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexid);
        if (vertexShader == 0) {
            return 0;
        }

        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentid);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            
            int[] linkStatus = new int[1];
            GLES20.glLinkProgram(program);
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        
        return program;
    }
}
