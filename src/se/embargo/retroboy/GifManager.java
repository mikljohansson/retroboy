package se.embargo.retroboy;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

import se.embargo.core.concurrent.ProgressTask;
import se.embargo.core.graphic.Bitmaps;
import se.embargo.core.graphic.Bitmaps.Transform;
import se.embargo.retroboy.filter.AbstractFilter;
import se.embargo.retroboy.graphic.GifEncoder;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

/**
 * Handles animated GIF recording.
 */
public class GifManager extends AbstractFilter {
	private static final String TAG = "GifFilter";
	private static final int MAX_CAPTURED_FRAMES = 100;
	
	private final Activity _context;
	private final ProgressBar _recordProgressBar;
	private volatile Transform _transform = null;
	
	private Queue<BitmapFrame> _frames = new PriorityBlockingQueue<BitmapFrame>();
	private int _framecount = 0;
	private StateChangeListener _listener;
	
	public interface StateChangeListener {
		public void onRecord();
		public void onStop();
		public void onFinish();
	}
	
	public GifManager(Activity context, View parent) {
		_context = context;
		_recordProgressBar = (ProgressBar)parent.findViewById(R.id.recordProgressBar);
		_recordProgressBar.setMax(MAX_CAPTURED_FRAMES);
	}
	
	public boolean isRecording() {
		return _transform != null;
	}
	
	public synchronized void setStateChangeListener(StateChangeListener listener) {
		_listener = listener;
	}
	
	/**
	 * Start recording frames.
	 * @param transform	Transform to apply on frames, e.g. rotation.
	 */
	public synchronized void record(Transform transform) {
		if (_transform == null) {
			_transform = transform;
	
			_context.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (_listener != null) {
						_listener.onRecord();
					}
				}
			});
		}
	}
	
	/**
	 * Stop recording and start outputting captured frames to file.
	 */
	public synchronized void stop() {
		if (_transform != null) {
			finish();
		}
	}
	
	/**
	 * Abort recording without saving picture.
	 */
	public synchronized void abort() {
		if (_transform != null) {
			reset();

			_context.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (_listener != null) {
						_listener.onFinish();
					}
				}
			});
		}
	}
	
	private synchronized void finish() {
		final Queue<BitmapFrame> frames = _frames;
		_frames = new PriorityQueue<BitmapFrame>();
		reset();
		
		_context.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (_listener != null) {
					_listener.onStop();
				}

				if (!frames.isEmpty()) {
					new EncodeTask(_context, frames, _listener).execute();
				}
			}
		});
	}
	
	private synchronized void reset() {
		_transform = null;
		_framecount = 0;
		_recordProgressBar.setProgress(0);
		
		for (BitmapFrame frame : _frames) {
			frame.bitmap.recycle();
			frame.bitmap = null;
		}
		
		_frames = new PriorityQueue<BitmapFrame>();
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		Bitmaps.Transform transform = _transform;
		
		try {
			if (_transform != null) {
				Bitmap bitmap = Bitmaps.transform(buffer.bitmap, transform, Bitmap.Config.RGB_565);
		
				synchronized (this) {
					if (_transform != null) {
						_framecount++;
						_recordProgressBar.setProgress(_framecount);
						_frames.add(new BitmapFrame(bitmap, buffer.timestamp));
						
						if (_framecount >= MAX_CAPTURED_FRAMES) {
							stop();
						}
					}
				}
			}
		}
		catch (OutOfMemoryError e) {
			_transform = null;
			
			for (int i = 0; i < 10; i++) {
				BitmapFrame frame = _frames.poll();
				
				if (frame != null) {
					frame.bitmap.recycle();
					frame.bitmap = null;
					System.gc();
				}
			}
			
			Log.e(TAG, "Stopping GIF video capture due to lack of memory", e);
			finish();
		}
	}
	
	private static class BitmapFrame implements Comparable<BitmapFrame> {
		public Bitmap bitmap;
		public final long timestamp;
		
		public BitmapFrame(Bitmap bm, long ts) {
			bitmap = bm;
			timestamp = ts;
		}

		@Override
		public int compareTo(BitmapFrame other) {
			return timestamp < other.timestamp ? -1 : (timestamp == other.timestamp ? 0 : 1);
		}
	}
	
	private static class EncodeTask extends ProgressTask<Void, Integer, Void> {
		private final Queue<BitmapFrame> _frames;
		private final StateChangeListener _listener;
		private File _file = null;
		
		public EncodeTask(Context context, Queue<BitmapFrame> frames, StateChangeListener listener) {
			super(context, R.string.title_saving_image, R.string.msg_saving_image);
			setMaxProgress(frames.size());
			setCancelable();
			_frames = frames;
			_listener = listener;
		}

		@Override
		protected Void doInBackground(Void... params) {
			int progress = 0;
			long prevtimestamp = 0;
			_file = Pictures.createOutputFile(getContext(), null, "gif");
			
			OutputStream os = null;
			try {
				long ts = System.currentTimeMillis();
				
				// Create output encoder
				os = new BufferedOutputStream(new FileOutputStream(_file)); 
				GifEncoder encoder = new GifEncoder();
				encoder.setRepeat(0);
				encoder.start(os);
				
				// Encode all frames
				for (BitmapFrame frame : _frames) {
					if (isCancelled()) {
						return null;
					}
					
					if (prevtimestamp != 0) {
						// Make sure to not multiply the rounding error (the delay is internally expressed in 1/100 seconds)
						long delay = (frame.timestamp - prevtimestamp) / 10000000L * 10000000L;
						encoder.setDelay((int)(delay / 1000000L));					
						prevtimestamp = frame.timestamp - ((frame.timestamp - prevtimestamp) - delay);
					}
					else {
						prevtimestamp = frame.timestamp;
					}
					
					encoder.addFrame(frame.bitmap);
					frame.bitmap.recycle();
					frame.bitmap = null;
					
					progress++;
					publishProgress(progress);
				}
				
				// Flush image to disk
				encoder.finish();
				os.flush();
				os.close();
				os = null;

				Log.i(TAG, "GIF encoder framerate: " + ((float)_frames.size() / ((float)(System.currentTimeMillis() - ts) / 1000)));
				
				// Tell the gallery about the image
				ContentValues values = new ContentValues();
				values.put(MediaStore.Images.Media.DATA, _file.getAbsolutePath());
				values.put(MediaStore.Images.Media.MIME_TYPE, "image/gif");
				values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
				getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
				
				// Prevent onCancelled() from deleting the file once it's into the gallery
				_file = null;
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to write GIF to:" + _file, e);
				cancel(false);
			}
			finally {
				for (BitmapFrame frame : _frames) {
					if (frame.bitmap != null) {
						frame.bitmap.recycle();
						frame.bitmap = null;
					}
				}

				_frames.clear();
				System.gc();

				if (os != null) {
					try {
						os.close();
					}
					catch (IOException e1) {}
				}
			}

			return null;
		}
		
		@Override
		protected void onCancelled() {
			if (_file != null) {
				_file.delete();
			}
			
			finish();
			super.onCancelled();
		}
		
		@Override
		protected void onPostExecute(Void result) {
			finish();
			super.onPostExecute(result);
		}
		
		@Override
		protected void onProgressUpdate(Integer... progress) {
			setProgress(progress[0]);
		}
		
		private void finish() {
			if (_listener != null) {
				_listener.onFinish();
			}
		}
	}
}
