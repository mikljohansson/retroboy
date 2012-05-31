package se.embargo.onebit;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.embargo.onebit.filter.IImageFilter;
import se.embargo.onebit.filter.YuvImageFilter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
	private ExecutorService _threadpool = Executors.newCachedThreadPool();
	private Queue<FilterTask> _bufferpool = new ConcurrentLinkedQueue<FilterTask>();
	
	private SurfaceHolder _holder;
	
	private Camera _camera;
	private Camera.Size _previewSize;
	private Camera.CameraInfo _cameraInfo;
	
	private IImageFilter _filter;
	private Matrix _transform = new Matrix();
	
	public CameraPreview(Context context) {
		this(context, null);
	}
	
	public CameraPreview(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	public CameraPreview(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		// Default filter
		_filter = new YuvImageFilter();

		// Install a SurfaceHolder.Callback so we get notified when the surface is created and destroyed.
		_holder = getHolder();
		_holder.addCallback(this);
		_holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
	}

	public void setCamera(Camera camera, Camera.CameraInfo cameraInfo) {
		boolean start = _camera != null;
		_camera = camera;
		_cameraInfo = cameraInfo;
		
		if (_camera != null) {
			_previewSize = _camera.getParameters().getPreviewSize();

			_camera.setPreviewCallbackWithBuffer(this);
			_camera.addCallbackBuffer(new byte[getBufferSize(_camera)]);
			_camera.addCallbackBuffer(new byte[getBufferSize(_camera)]);
			
			_transform = createTransformMatrix(cameraInfo, getWidth(), getHeight());
			
			if (start) {
				startPreview();
			}
		}
	}

	public void setFilter(IImageFilter filter) {
		_filter = filter;
	}
	
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		FilterTask task = _bufferpool.poll();
		if (task != null) {
			task.init(data, camera);
		}
		else {
			task = new FilterTask(data, camera);
		}
		
		_threadpool.submit(task);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		_transform = createTransformMatrix(_cameraInfo, width, height);
		startPreview();
	}
	
	private void startPreview() {
		if (_camera != null) {
			// Now that the size is known, set up the camera parameters and begin the preview.
			Camera.Parameters parameters = _camera.getParameters();
			parameters.setPreviewSize(_previewSize.width, _previewSize.height);
			parameters.setPreviewFormat(ImageFormat.NV21);
			
			_camera.setParameters(parameters);
			_camera.startPreview();
		}
	}
	
	private static int getBufferSize(Camera camera) {
		Camera.Size size = camera.getParameters().getPreviewSize();
		int format = camera.getParameters().getPreviewFormat();
		int bits = ImageFormat.getBitsPerPixel(format);
		return size.width * size.height * bits / 8;
	}
	
	private static Matrix createTransformMatrix(CameraInfo cameraInfo, int width, int height) {
		Matrix transform = new Matrix();
		if (cameraInfo != null) {
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				// Flip the image if the camera is facing the front to achieve a mirror effect
				transform.setScale(-1, 1);
				transform.preRotate(-90.0f);
				transform.postTranslate(width, height);
			}
			else {
				transform.setScale(1, 1);
				transform.preRotate(90.0f);
				transform.postTranslate(width, 0);
			}
		}
		
		return transform;
	}
	
	private class FilterTask implements Runnable {
		private Camera _camera;
		private Camera.Size _previewSize;
		private IImageFilter.ImageBuffer _buffer;
		
		public FilterTask(byte[] data, Camera camera) {
			init(data, camera);
		}
		
		public void init(byte[] data, Camera camera) {
			_camera = camera;
			_previewSize = camera.getParameters().getPreviewSize();
			
			if (_buffer == null || _buffer.width != _previewSize.width || _buffer.height != _previewSize.height) {
				_buffer = new IImageFilter.ImageBuffer(_previewSize.width, _previewSize.height);
			}

			_buffer.data = data;
		}
		
		@Override
		public void run() {
			// Filter the preview image
			_filter.accept(_buffer);
			
			// Draw the preview image
			Canvas canvas = _holder.lockCanvas();
			canvas.drawBitmap(_buffer.bitmap, _transform, null);
			_holder.unlockCanvasAndPost(canvas);
			
			// Release the buffers
			_camera.addCallbackBuffer(_buffer.data);
			_bufferpool.offer(this);
		}
	}
}
