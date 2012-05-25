package se.embargo.onebit;

import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

import se.embargo.onebit.filter.IImageFilter;
import se.embargo.onebit.filter.MonoFilter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

class CameraPreview extends ViewGroup implements SurfaceHolder.Callback, Camera.PreviewCallback {
	private ExecutorService _threadpool = Executors.newCachedThreadPool();
	private Queue<int[]> _imagepool = new SynchronousQueue<int[]>();
	
	private SurfaceView _surface;
	private SurfaceHolder _holder;
	private Camera _camera;
	private Camera.Size _previewSize;
	
	private IImageFilter _filter;

	public CameraPreview(Context context) {
		this(context, null);
	}
	
	public CameraPreview(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	public CameraPreview(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		// Default filter
		_filter = new MonoFilter();
		
		// Create the surface to render the preview to
		_surface = new SurfaceView(context);
		_holder = _surface.getHolder();
		addView(_surface);

		// Install a SurfaceHolder.Callback so we get notified when the surface is created and destroyed.
		_holder.addCallback(this);
		_holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
	}

	public void setFilter(IImageFilter filter) {
		_filter = filter;
	}
	
	public void setCamera(Camera camera) {
		_camera = camera;
		
		if (_camera != null) {
			_previewSize = _camera.getParameters().getPreviewSize();
			
			_camera.setPreviewCallbackWithBuffer(this);
			_camera.addCallbackBuffer(new byte[getBufferSize(_camera)]);
			_camera.addCallbackBuffer(new byte[getBufferSize(_camera)]);
			
			requestLayout();
		}
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		_threadpool.submit(new FilterTask(data, _previewSize));
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// We purposely disregard child measurements because act as a wrapper to 
		// a SurfaceView that centers the camera preview instead of stretching it.
		int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
		setMeasuredDimension(width, height);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		if (changed && getChildCount() > 0) {
			View child = getChildAt(0);

			int width = r - l;
			int height = b - t;

			int previewWidth = width;
			int previewHeight = height;
			if (_previewSize != null) {
				previewWidth = _previewSize.width;
				previewHeight = _previewSize.height;
			}

			// Center the child SurfaceView within the parent.
			if (width * previewHeight > height * previewWidth) {
				int scaledChildWidth = previewWidth * height / previewHeight;
				child.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
			} 
			else {
				int scaledChildHeight = previewHeight * width / previewWidth;
				child.layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
			}
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {}

	public void surfaceDestroyed(SurfaceHolder holder) {}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// Now that the size is known, set up the camera parameters and begin the preview.
		Camera.Parameters parameters = _camera.getParameters();
		parameters.setPreviewSize(_previewSize.width, _previewSize.height);
		parameters.setPreviewFormat(ImageFormat.NV21);
		requestLayout();

		_camera.setParameters(parameters);
		_camera.startPreview();
	}
	
	private static int getBufferSize(Camera camera) {
		Camera.Size size = camera.getParameters().getPreviewSize();
		int format = camera.getParameters().getPreviewFormat();
		int bits = ImageFormat.getBitsPerPixel(format);
		return size.width * size.height * bits / 8;
	}
	
	private class FilterTask implements Runnable {
		private byte[] _data;
		private Camera.Size _size;
		
		public FilterTask(byte[] data, Camera.Size size) {
			_data = data;
			_size = size;
		}
		
		@Override
		public void run() {
			// Allocate an image buffer
			int[] image = (int[])_imagepool.poll();
			if (image == null) {
				image = new int[_size.width * _size.height];
			}
			
			// Filter the preview image
			IImageFilter.PreviewBuffer buffer = new IImageFilter.PreviewBuffer(
				_data, image, _size.width, _size.height, 2);
			_filter.accept(buffer);
			
			// Draw the preview image
			Canvas canvas = _holder.lockCanvas();
			canvas.drawBitmap(buffer.image, 0, buffer.width, 0.0f, 0.0f, buffer.width, buffer.height, false, null);
			_holder.unlockCanvasAndPost(canvas);
			
			// Release the buffers
			_camera.addCallbackBuffer(buffer.data);
			_imagepool.offer(buffer.image);
		}
	}
}
