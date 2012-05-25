package se.embargo.onebit;

import se.embargo.onebit.filter.BayerFilter;
import se.embargo.onebit.filter.BitmapFilter;
import se.embargo.onebit.filter.CompositeFilter;
import se.embargo.onebit.filter.IImageFilter;
import se.embargo.onebit.filter.YuvMonoFilter;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Bundle;
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
		_preview.setFilter(createImageFilter(new BayerFilter(480, 320)));
		
		// Check which camera to use
		for (int i = 0, cameras = Camera.getNumberOfCameras(); i < cameras; i++) {
			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(i, info);
			
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
				_cameraid = i;
				break;
			}
		}
	}
	
	private IImageFilter createImageFilter(IImageFilter filter) {
		CompositeFilter filters = new CompositeFilter();
		filters.add(new YuvMonoFilter());
		//filters.add(new ResizeFilter(480, 320));
		filters.add(filter);
		filters.add(new BitmapFilter());
		return filters;
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
			
			// Start the preview
			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(_cameraid, info);
			_preview.setCamera(_camera, info);
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
}
