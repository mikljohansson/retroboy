package se.embargo.onebit;

import se.embargo.onebit.filter.AtkinsonFilter;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Surface;
import android.view.WindowManager;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;

public class MainActivity extends SherlockActivity {
	private CameraPreview _preview;
	private Camera _camera;
	private int _cameraid = -1;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Request full screen window
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		// Force switch to landscape orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		// Setup preview display surface
		setContentView(R.layout.main);
		_preview = (CameraPreview)findViewById(R.id.preview);
		_preview.setFilter(new AtkinsonFilter());
		
		// Check which camera to use
		for (int i = 0, cameras = Camera.getNumberOfCameras(); i < cameras; i++) {
			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(i, info);
			
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				_cameraid = i;
				break;
			}
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();

		if (_cameraid >= 0) {
			_camera = Camera.open(_cameraid);
			
			// Select the smallest available preview size for performance reasons
			Camera.Size previewSize = null;
			for (Camera.Size size : _camera.getParameters().getSupportedPreviewSizes()) {
				if (previewSize == null || size.width < previewSize.width) {
					previewSize = size;
				}
			}
			_camera.getParameters().setPreviewSize(previewSize.width, previewSize.height);

			// Orient the camera according to the current device orientation
			setDisplayOrientation(_cameraid, _camera);
			
			// Start the preview
			_preview.setCamera(_camera);
		}
	}

	@Override
	protected void onPause() {
		if (_camera != null) {
			_camera.stopPreview();  
			_camera.release();
			_camera = null;
		}

		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_options, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	private void setDisplayOrientation(int camid, Camera camera) {
		Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		Camera.getCameraInfo(camid, info);
		
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
			case Surface.ROTATION_0: 
				degrees = 0; 
				break;
			
			case Surface.ROTATION_90: 
				degrees = 90; 
				break;
			
			case Surface.ROTATION_180: 
				degrees = 180; 
				break;
			
			case Surface.ROTATION_270: 
				degrees = 270; 
				break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			// Front facing camera
			result = (info.orientation + degrees) % 360;
			
			// Compensate for the mirroring
			result = (360 - result) % 360;
		} 
		else {
			// Back facing camera
			result = (info.orientation - degrees + 360) % 360;
		}
		
		camera.setDisplayOrientation(result);
	}
}
