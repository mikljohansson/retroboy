package se.embargo.onebit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import se.embargo.core.io.Streams;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

public class CameraRenderer implements GLSurfaceView.Renderer, OnFrameAvailableListener {
    private final float[] _vertices = {
        // X, Y, Z, U, V
    	-1.0f,  1.0f, 0.0f, 0.0f, 1.0f,		// Top left
    	 1.0f,  1.0f, 0.0f, 1.0f, 1.0f,		// Top right
        -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,		// Bottom left
         1.0f, -1.0f, 0.0f, 1.0f, 0.0f		// Bottom right
    };
    
    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

    private Context _context;
    private FloatBuffer _vertexbuf;
    
    private int _program;
    private int _previewTextureHandle;
    private SurfaceTexture _previewTexture;
    private boolean _updateSurface = false;
    
    private int muMVPMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;
    private int muSTMatrixHandle;
    private int muCRatioHandle;

    private float[] mMVPMatrix = new float[16];
    private float[] mVMatrix = new float[16];
    private float[] mSTMatrix = new float[16];
    private float[] mProjMatrix = new float[16];
    
    private Camera _camera = null;
    private Camera.Size _previewsize;

    private int _thresholdTextureHandle;
    private Buffer _thresholdTexture = ByteBuffer.wrap(new byte[] {
    	0, 32, 8, 40, 2, 34, 10, 42, 48, 16, 56, 24, 50, 18, 58, 26, 12, 44, 4, 36, 14, 46, 6, 38, 60, 28, 52, 20, 62, 30, 54, 22, 3, 35, 11, 43, 1, 33, 9, 41, 51, 19, 59, 27, 49, 17, 57, 25, 15, 47, 7, 39, 13, 45, 5, 37, 63, 31, 55, 23, 61, 29, 53, 21
    	//0, 0, 0, 32, 0, 0, 8, 0, 0, 40, 0, 0, 2, 0, 0, 34, 0, 0, 10, 0, 0, 42, 0, 0, 48, 0, 0, 16, 0, 0, 56, 0, 0, 24, 0, 0, 50, 0, 0, 18, 0, 0, 58, 0, 0, 26, 0, 0, 12, 0, 0, 44, 0, 0, 4, 0, 0, 36, 0, 0, 14, 0, 0, 46, 0, 0, 6, 0, 0, 38, 0, 0, 60, 0, 0, 28, 0, 0, 52, 0, 0, 20, 0, 0, 62, 0, 0, 30, 0, 0, 54, 0, 0, 22, 0, 0, 3, 0, 0, 35, 0, 0, 11, 0, 0, 43, 0, 0, 1, 0, 0, 33, 0, 0, 9, 0, 0, 41, 0, 0, 51, 0, 0, 19, 0, 0, 59, 0, 0, 27, 0, 0, 49, 0, 0, 17, 0, 0, 57, 0, 0, 25, 0, 0, 15, 0, 0, 47, 0, 0, 7, 0, 0, 39, 0, 0, 13, 0, 0, 45, 0, 0, 5, 0, 0, 37, 0, 0, 63, 0, 0, 31, 0, 0, 55, 0, 0, 23, 0, 0, 61, 0, 0, 29, 0, 0, 53, 0, 0, 21, 0, 0
    	//0, 0, 0, 0, 32, 0, 0, 0, 8, 0, 0, 0, 40, 0, 0, 0, 2, 0, 0, 0, 34, 0, 0, 0, 10, 0, 0, 0, 42, 0, 0, 0, 48, 0, 0, 0, 16, 0, 0, 0, 56, 0, 0, 0, 24, 0, 0, 0, 50, 0, 0, 0, 18, 0, 0, 0, 58, 0, 0, 0, 26, 0, 0, 0, 12, 0, 0, 0, 44, 0, 0, 0, 4, 0, 0, 0, 36, 0, 0, 0, 14, 0, 0, 0, 46, 0, 0, 0, 6, 0, 0, 0, 38, 0, 0, 0, 60, 0, 0, 0, 28, 0, 0, 0, 52, 0, 0, 0, 20, 0, 0, 0, 62, 0, 0, 0, 30, 0, 0, 0, 54, 0, 0, 0, 22, 0, 0, 0, 3, 0, 0, 0, 35, 0, 0, 0, 11, 0, 0, 0, 43, 0, 0, 0, 1, 0, 0, 0, 33, 0, 0, 0, 9, 0, 0, 0, 41, 0, 0, 0, 51, 0, 0, 0, 19, 0, 0, 0, 59, 0, 0, 0, 27, 0, 0, 0, 49, 0, 0, 0, 17, 0, 0, 0, 57, 0, 0, 0, 25, 0, 0, 0, 15, 0, 0, 0, 47, 0, 0, 0, 7, 0, 0, 0, 39, 0, 0, 0, 13, 0, 0, 0, 45, 0, 0, 0, 5, 0, 0, 0, 37, 0, 0, 0, 63, 0, 0, 0, 31, 0, 0, 0, 55, 0, 0, 0, 23, 0, 0, 0, 61, 0, 0, 0, 29, 0, 0, 0, 53, 0, 0, 0, 21, 0, 0, 0
    	});
    
    private int _dimensionVectorHandle;
    
    private static String TAG = "CameraRenderer";
    
    public CameraRenderer(Context context) {
        _context = context;
        _vertexbuf = ByteBuffer.allocateDirect(_vertices.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        _vertexbuf.put(_vertices).position(0);
    
        Matrix.setIdentityM(mSTMatrix, 0);
    }
    
    public SurfaceTexture getPreviewTexture() {
    	return _previewTexture;
    }

    public void setCamera(Camera camera) {
        _camera = camera;
        _previewsize = camera.getParameters().getPreviewSize();
    }
    
	@Override
	public synchronized void onFrameAvailable(SurfaceTexture surfaceTexture) {
		_updateSurface = true;
	}
    
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
    	GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
    	
        _program = createProgram(R.raw.image_vertex_shader, R.raw.image_bayer_shader);
        if (_program == 0) {
            return;
        }
        
        maPositionHandle = GLES20.glGetAttribLocation(_program, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (maPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        
        maTextureHandle = GLES20.glGetAttribLocation(_program, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }

        muMVPMatrixHandle = GLES20.glGetUniformLocation(_program, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (muMVPMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }

        muSTMatrixHandle = GLES20.glGetUniformLocation(_program, "uSTMatrix");
        checkGlError("glGetUniformLocation uSTMatrix");
        if (muSTMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uSTMatrix");
        }
        
        muCRatioHandle = GLES20.glGetUniformLocation(_program, "uCRatio");
        checkGlError("glGetUniformLocation uCRatio");
        if (muCRatioHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uCRatio");
        }

        _dimensionVectorHandle = GLES20.glGetUniformLocation(_program, "uDimension");
        checkGlError("glGetUniformLocation uDimension");
        if (_dimensionVectorHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uDimension");
        }
        
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

        // Bind the Bayer threshold matrix as a texture
        _thresholdTextureHandle = textures[1];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _thresholdTextureHandle);
        checkGlError("glBindTexture");

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 8, 8, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, _thresholdTexture);
        
        // Set the viewpoint
        Matrix.setLookAtM(mVMatrix, 0, 0, 0, 4f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
        
        // Start the preview
		try {
			_camera.setPreviewTexture(_previewTexture);
			_camera.startPreview();
		}
		catch (IOException e) {}
    }

    public void onDrawFrame(GL10 glUnused) {
    	synchronized (this) {
	    	if (_updateSurface) {
		    	_previewTexture.updateTexImage();
		    	_previewTexture.getTransformMatrix(mSTMatrix);
		    	_updateSurface = false;
	    	}
    	}
    	
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(_program);
        checkGlError("glUseProgram");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, _previewTextureHandle);
        checkGlError("glBindTexture");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _thresholdTextureHandle);
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
        	maTextureHandle, 2, GLES20.GL_FLOAT, false,
        	TRIANGLE_VERTICES_DATA_STRIDE_BYTES, _vertexbuf);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");

        // Apply the screen ratio projection
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);
        
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
        GLES20.glUniform1f(muCRatioHandle, (float)_previewsize.width / (float)_previewsize.height);
        GLES20.glUniform2f(_dimensionVectorHandle, _previewsize.width, _previewsize.height);
        
        // Draw two triangles to form a square
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 1, 3);
        checkGlError("glDrawArrays");
    }

    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        
        float ratio = (float)width / height;
        Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
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

    private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }
}
