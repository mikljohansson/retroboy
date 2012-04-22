package se.embargo.onebit;

import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockActivity;

public class CameraActivity extends SherlockActivity {
	private GLSurfaceView _surface;
	private CameraRenderer _renderer;
	private Camera _camera;
	private int _cameraid = -1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		// Check which camera to use
		for (int i = 0, cameras = Camera.getNumberOfCameras(); i < cameras; i++) {
			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(i, info);
			
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				_cameraid = i;
				break;
			}
		}
		
		_surface = new GLSurfaceView(this);
		_surface.setEGLContextClientVersion(2);

		_renderer = new CameraRenderer(this);
		_surface.setRenderer(_renderer);

		setContentView(_surface);
	}

    @Override
    protected void onResume() {
		if (_cameraid >= 0) {
	        _camera = Camera.open(_cameraid);
			_renderer.setCamera(_camera);
		}
        
		super.onResume();
        _surface.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        _surface.onPause();

        if (_camera != null) {
			_camera.stopPreview();  
			_camera.release();
        }
    }
}
