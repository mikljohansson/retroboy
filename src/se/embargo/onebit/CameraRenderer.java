package se.embargo.onebit;

import java.io.IOException;
import java.io.InputStream;
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
    private int _texture;
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
    private float _cameraRatio = 1.0f;

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
    	
        Camera.Size size = camera.getParameters().getPreviewSize();
        _cameraRatio = (float)size.width / (float)size.height;
    }
    
	@Override
	public synchronized void onFrameAvailable(SurfaceTexture surfaceTexture) {
		_updateSurface = true;
	}
    
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        _program = createProgram(R.raw.image_vertex_shader, R.raw.image_preview_shader);
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

        // Create the external texture which the camera preview is written to
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        _texture = textures[0];
        _previewTexture = new SurfaceTexture(_texture);
        _previewTexture.setOnFrameAvailableListener(this);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, _texture);
        checkGlError("glBindTexture");

        // No mip-mapping with camera source
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        
        // Clamp to edge is only option
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        Matrix.setLookAtM(mVMatrix, 0, 0, 0, 5f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
        
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
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, _texture);
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
        GLES20.glUniform1f(muCRatioHandle, _cameraRatio);
        
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
