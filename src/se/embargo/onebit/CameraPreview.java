package se.embargo.onebit;

import java.util.List;

import se.embargo.onebit.filter.IImageFilter;
import se.embargo.onebit.filter.YuvMonoFilter;
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
		_filter = new YuvMonoFilter();
		
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
			
			requestLayout();
		}
	}

	public void switchCamera(Camera camera) {
	   setCamera(camera);
	   
	   /*
	   try {
		   camera.setPreviewDisplay(_holder);
	   }
	   catch (IOException exception) {
		   Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
	   }
	   */
	   
	   Camera.Parameters parameters = camera.getParameters();
	   parameters.setPreviewSize(_previewSize.width, _previewSize.height);
	   camera.setParameters(parameters);
	   
	   requestLayout();
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		IImageFilter.PreviewBuffer buffer = new IImageFilter.PreviewBuffer(
			data, _previewSize.width, _previewSize.height, 2);
		_filter.accept(buffer);
		_camera.addCallbackBuffer(data);
		
		Canvas canvas = _holder.lockCanvas();
		canvas.drawBitmap(buffer.image, 0, buffer.width, 0, 0, buffer.width, buffer.height, false, null);
		_holder.unlockCanvasAndPost(canvas);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// We purposely disregard child measurements because act as a wrapper to 
		// a SurfaceView that centers the camera preview instead of stretching it.
		int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
		setMeasuredDimension(width, height);

		// Calculate the optimal preview size
		/*
		List<Camera.Size> sizes = _camera.getParameters().getSupportedPreviewSizes();
		if (sizes != null) {
			_previewSize = getOptimalPreviewSize(sizes, width, height);
		}
		*/
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

	public void surfaceCreated(SurfaceHolder holder) {
		/*
		try {
			if (_camera != null) {
				_camera.setPreviewDisplay(holder);
			}
		} 
		catch (IOException exception) {
			Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
		}
		*/
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		if (_camera != null) {
			_camera.stopPreview();
		}
	}

	private static Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
		if (sizes == null) {
			return null;
		}

		final double ASPECT_TOLERANCE = 0.1;
		double targetRatio = (double) w / h;
		double minDiff = Double.MAX_VALUE;
		int targetHeight = h;
		Camera.Size optimalSize = null;

		// Try to find an size match aspect ratio and size
		for (Camera.Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}

		// Cannot find the one match the aspect ratio, ignore the requirement
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Camera.Size size : sizes) {
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		
		return optimalSize;
	}

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
}
