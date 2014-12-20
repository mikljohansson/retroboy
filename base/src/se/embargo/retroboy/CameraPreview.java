package se.embargo.retroboy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.embargo.core.graphic.Bitmaps;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.YuvFilter;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class CameraPreview extends FrameLayout implements Camera.PreviewCallback {
	private static final String TAG = "CameraPreview";
	
	private final SurfaceView _surface;
	private final SurfaceHolder _holder;
	
	private final SurfaceView _dummy;
	
	private CameraHandle _cameraHandle;
	private Camera.Size _previewSize;
	private int _buffersize;
	
	private IImageFilter _filter;
	private Bitmaps.Transform _transform, _prevTransform;
	
	/**
	 * Statistics for framerate calculation
	 */
	private long _framestat = 0;
	private long _laststat = 0;
	
	private final ExecutorService _executor = Executors.newFixedThreadPool(1);
	private final FilterTask _task = new FilterTask();
	
	private enum State { READY, WORKING };
	private State _taskState = State.READY, _inputState = State.WORKING;

	/**
	 * Camera input and filter work buffers
	 */
	private byte[] _input, _output;
	
	/**
	 * Generation count used to avoid rendering frames from previous state
	 */
	private long _generation = 0;
	
	public CameraPreview(Context context) {
		this(context, null);
	}
	
	public CameraPreview(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	@SuppressWarnings("deprecation")
	public CameraPreview(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		// Default filter
		_filter = new YuvFilter(480, 360, 0, true, true);
		
		// Dummy view to make sure that Camera actually delivers preview frames
		_dummy = new SurfaceView(context);
		_dummy.setVisibility(INVISIBLE);
		_dummy.getHolder().addCallback(new DummySurfaceCallback());
		_dummy.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		addView(_dummy, 1, 1);

		// Install a SurfaceHolder.Callback so we get notified when the surface is created and destroyed.
		_surface = new SurfaceView(context);
		_holder = _surface.getHolder();
		_holder.addCallback(new PreviewSurfaceCallback());
		_holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
		addView(_surface);
	}

	public synchronized void setCamera(CameraHandle handle) {
		if (_cameraHandle != null) {
			// Calling these before release() crashes the GT-P7310 with Android 4.0.4
			_cameraHandle.camera.stopPreview();
			_cameraHandle.camera.setPreviewCallbackWithBuffer(null);
			
			// Hide the dummy preview or it will be temporarily visible while closing the app
			_dummy.setVisibility(INVISIBLE);
		}
		
		_cameraHandle = handle;
		
		if (_cameraHandle != null) {
			_previewSize = _cameraHandle.camera.getParameters().getPreviewSize();
			_buffersize = getBufferSize(_cameraHandle);

			// Allocate work buffers
			_input = new byte[_buffersize];
			_output = new byte[_buffersize];
			_inputState = State.WORKING;
			
			// Visible dummy view to make sure that Camera actually delivers preview frames
			_dummy.setVisibility(VISIBLE);
			_cameraHandle.camera.setPreviewDisplay(_dummy.getHolder());

			initPreviewCallback();
			initTransform();

			// Begin the preview
			_framestat = 0;
			_laststat = System.nanoTime();
			_cameraHandle.camera.startPreview();
			Log.i(TAG, "Started preview");
		}
	}
	
	/**
	 * Restarts a paused preview
	 */
	public synchronized void initPreviewCallback() {
		if (_cameraHandle != null) {
			// Clear the buffer queue
			_cameraHandle.camera.setPreviewCallbackWithBuffer(null);
			
			// Install this as the preview handle
			_cameraHandle.camera.addCallbackBuffer(new byte[_buffersize]);
			_cameraHandle.camera.setPreviewCallbackWithBuffer(this);
		}
	}
	
	/**
	 * Sets the active image filter
	 * @param filter	Image filter to use
	 */
	public synchronized void setFilter(IImageFilter filter) {
		_filter = filter;
		initTransform();
	}
	
	/**
	 * @return	The active image filter.
	 */
	public synchronized IImageFilter getFilter() {
		return _filter;
	}
	
	private synchronized void initTransform() {
		Log.i(TAG, "Initializing the transform matrix");
		
		if (_cameraHandle != null && _previewSize != null) {
			int width = getWidth(), height = getHeight();
			
			// Get the current device orientation
			WindowManager windowManager = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
			int rotation = windowManager.getDefaultDisplay().getRotation();
			
			// Check for orientation override
			SharedPreferences prefs = getContext().getSharedPreferences(Pictures.PREFS_NAMESPACE, Context.MODE_PRIVATE);
			int orientation = Pictures.getCameraOrientation(prefs, _cameraHandle.info, _cameraHandle.id);
			Log.i(TAG, "Display rotation " + rotation + ", camera orientation " + orientation);
			
			// Rotate and flip the image when drawing it onto the surface
			int ewidth = _filter.getEffectiveWidth(_previewSize.width, _previewSize.height),
				eheight = _filter.getEffectiveHeight(_previewSize.width, _previewSize.height);
			
			_transform = Pictures.createTransformMatrix(
				ewidth, eheight,
				_cameraHandle.info.facing, orientation, rotation, 
				Math.max(width, height), Math.min(width, height),
				Bitmaps.FLAG_ENLARGE);
		
			// Clear all the canvas buffers
			if (!_transform.equals(_prevTransform)) {
				for (int i = 0; i < 3; i++) {
					Canvas canvas = _holder.lockCanvas();
					if (canvas != null) {
						canvas.drawColor(Color.BLACK);
						_holder.unlockCanvasAndPost(canvas);
					}
				}
			}

			_prevTransform = _transform;
		}
		
		_generation++;
	}
	
	public static int getBufferSize(CameraHandle handle) {
		Camera.Size size = handle.camera.getParameters().getPreviewSize();
		int format = handle.camera.getParameters().getPreviewFormat();
		int bits = ImageFormat.getBitsPerPixel(format);
		return size.width * size.height * bits / 8;
	}
	
	private class PreviewSurfaceCallback implements SurfaceHolder.Callback {
		@Override
		public void surfaceCreated(SurfaceHolder holder) {}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			Log.d(TAG, "Preview surface changed");
			initTransform();
		}
	}
	
	private class DummySurfaceCallback implements SurfaceHolder.Callback {
		@Override
		public void surfaceCreated(SurfaceHolder holder) {}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			synchronized (CameraPreview.this) {
				// Visible dummy view to make sure that Camera actually delivers preview frames
				if (_cameraHandle != null) {
					_dummy.setVisibility(VISIBLE);
					_cameraHandle.camera.setPreviewDisplay(_dummy.getHolder());
				}
			}
		}
	}
	
	@Override
	public synchronized void onPreviewFrame(byte[] data, Camera camera) {
		CameraHandle handle = _cameraHandle;
		
		// data may be null if buffer was too small
		if (handle != null && data != null && data.length == _buffersize) {
			handle.camera.addCallbackBuffer(_input);
			_input = data;
			_inputState = State.READY;
			_task.tryProcess();
		}
	}
	
	private class FilterTask implements Runnable {
		private IImageFilter _taskFilter;
		private IImageFilter.ImageBuffer _buffer;
		private final Paint _paint = new Paint(Paint.FILTER_BITMAP_FLAG);
		private long _taskGeneration;
		
		public void tryProcess() {
			if (_taskState != State.READY || _inputState != State.READY) {
				return;
			}
			
			// Check if buffer is still valid for this frame
			if (_buffer == null || _buffer.framewidth != _previewSize.width || _buffer.frameheight != _previewSize.height) {
				Log.d(TAG, "Allocating ImageBuffer for " + _previewSize.width + "x" + _previewSize.height + " pixels (" + _buffer + ")");
				_buffer = new IImageFilter.ImageBuffer(_previewSize.width, _previewSize.height);
			}
			
			// Reinitialize the buffer with the new data
			_taskFilter = _filter;
			_buffer.reset(_input);
			_input = _output;
			_taskState = State.WORKING;
			_inputState = State.WORKING;
			
			// Start processing
			_taskGeneration = _generation;
			_executor.submit(this);
		}
		
		@Override
		public void run() {
			try {
				// Filter the preview image
				_taskFilter.accept(_buffer);
				
				// Check frame sequence number and drop out-of-sequence frames
				synchronized (CameraPreview.this) {
					// Check if camera has been switched
					if (_taskGeneration != _generation) {
						return;
					}

					Canvas canvas = null;
					try {
						// Release buffer back to camera queue
						_output = _buffer.frame;
						_buffer.frame = null;
	
						// Calculate the framerate
						if (++_framestat >= 25) {
							long ts = System.nanoTime();
							Log.d(TAG, "Framerate: " + ((double)_framestat / (((double)ts - (double)_laststat) / 1000000000d)) + ", threshold: " + _buffer.threshold);
							
							_framestat = 0;
							_laststat = ts;
						}
	
						// Draw and transform camera frame (must not touch mutable CameraPreview state when not holding lock)
						canvas = _holder.lockCanvas();
						if (canvas != null) {
							canvas.drawBitmap(_buffer.bitmap, _transform.matrix, _paint);
						}
					}
					finally {
						// Switch to next buffer
						if (canvas != null) {
							_holder.unlockCanvasAndPost(canvas);
						}
					}
				}
			}
			catch (Exception e) {
				Log.e(TAG, "Unexpected error processing frame", e);
			}
			finally {
				synchronized (CameraPreview.this) {				
					// Check if next frame is already available
					_taskState = State.READY;
					tryProcess();
				}
			}
		}
	}
}
