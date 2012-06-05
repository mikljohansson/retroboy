package se.embargo.retroboy;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.embargo.core.graphics.Bitmaps;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.YuvFilter;
import android.content.Context;
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

class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
	private ExecutorService _threadpool = Executors.newCachedThreadPool();
	private Queue<FilterTask> _bufferpool = new ConcurrentLinkedQueue<FilterTask>();
	
	private SurfaceHolder _holder;
	
	private Camera _camera;
	private Camera.Size _previewSize;
	private Camera.CameraInfo _cameraInfo;
	
	private IImageFilter _filter;
	private Bitmaps.Transform _transform;
	
	private Camera.PreviewCallback _callback;
	
	public CameraPreview(Context context) {
		this(context, null);
	}
	
	public CameraPreview(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	public CameraPreview(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		// Default filter
		_filter = new YuvFilter(Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT);

		// Install a SurfaceHolder.Callback so we get notified when the surface is created and destroyed.
		_holder = getHolder();
		_holder.addCallback(this);
		_holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
	}

	public void setCamera(Camera camera, Camera.CameraInfo cameraInfo) {
		if (_camera != null) {
			_camera.setPreviewCallbackWithBuffer(null);
		}
		
		_camera = camera;
		_cameraInfo = cameraInfo;
		
		if (_camera != null) {
			_previewSize = _camera.getParameters().getPreviewSize();

			_camera.setPreviewCallbackWithBuffer(this);
			_camera.addCallbackBuffer(new byte[getBufferSize(_camera)]);
			
			initTransform();
			startPreview();
		}
	}
	
	public void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
		_callback = callback;
	}

	public void setFilter(IImageFilter filter) {
		_filter = filter;
	}
	
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (_callback != null) {
			// Delegate to an additional callback
			_callback.onPreviewFrame(data, camera);
			_callback = null;
		}
		else {
			// Submit a task to process the image
			FilterTask task = _bufferpool.poll();
			if (task != null) {
				task.init(data, camera);
			}
			else {
				task = new FilterTask(data, camera);
			}
			
			_threadpool.submit(task);
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		initTransform();
	}
	
	private void startPreview() {
		if (_camera != null) {
			// Clear both the canvas buffers
			for (int i = 0; i < 2; i++) {
				Canvas canvas = _holder.lockCanvas();
				if (canvas != null) {
					canvas.drawColor(Color.BLACK);
					_holder.unlockCanvasAndPost(canvas);
				}
			}
			
			// Begin the preview.
			_camera.startPreview();
		}
	}
	
	private void initTransform() {
		if (_cameraInfo != null && _previewSize != null) {
			int width = getWidth(), height = getHeight();
			YuvFilter yuvFilter = new YuvFilter(Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT);
			
			// Get the current device orientation
			WindowManager windowManager = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
			int rotation = windowManager.getDefaultDisplay().getRotation();
	
			// Rotate and flip the image when drawing it onto the surface
			_transform = Pictures.createTransformMatrix(
				getContext(), 
				yuvFilter.getEffectiveWidth(_previewSize.width, _previewSize.height), 
				yuvFilter.getEffectiveHeight(_previewSize.width, _previewSize.height), 
				_cameraInfo.facing, _cameraInfo.orientation, rotation, 
				Math.max(width, height), Math.min(width, height));
		}
	}
	
	public static int getBufferSize(Camera camera) {
		Camera.Size size = camera.getParameters().getPreviewSize();
		int format = camera.getParameters().getPreviewFormat();
		int bits = ImageFormat.getBitsPerPixel(format);
		return size.width * size.height * bits / 8;
	}
	
	private class FilterTask implements Runnable {
		private static final String TAG = "FilterTask";
		private Camera _camera;
		private IImageFilter.ImageBuffer _buffer;
		private Paint _paint = new Paint(Paint.FILTER_BITMAP_FLAG);
		
		public FilterTask(byte[] data, Camera camera) {
			init(data, camera);
		}
		
		public void init(byte[] data, Camera camera) {
			// Check if buffer is still valid for this frame
			if (_buffer == null || _buffer.framewidth != _previewSize.width || _buffer.frameheight != _previewSize.height) {
				Log.i(TAG, "Allocating ImageBuffer for " + _previewSize.width + "x" + _previewSize.height + " pixels (" + _buffer + ")");
				_buffer = new IImageFilter.ImageBuffer(_previewSize.width, _previewSize.height);
			}
			
			// Reinitialize the buffer with the new data
			_buffer.frame = data;
			_camera = camera;
		}
		
		@Override
		public void run() {
			// Filter the preview image
			_filter.accept(_buffer);
			
			// Must hold canvas before releasing camera buffer or out-of-memory will result..
			Canvas canvas = _holder.lockCanvas();
			synchronized (_camera) {
				_camera.addCallbackBuffer(_buffer.frame);
			}
			
			// Draw and transform camera frame
			canvas.drawBitmap(_buffer.bitmap, _transform.matrix, _paint);
			_holder.unlockCanvasAndPost(canvas);
			
			// Release the buffers
			_bufferpool.offer(this);
		}
	}
}
