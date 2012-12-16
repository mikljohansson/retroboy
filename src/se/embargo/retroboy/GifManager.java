package se.embargo.retroboy;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.PriorityQueue;
import java.util.Queue;

import se.embargo.core.concurrent.ProgressTask;
import se.embargo.core.graphic.Bitmaps;
import se.embargo.core.graphic.Bitmaps.Transform;
import se.embargo.retroboy.R;
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
	
	private Queue<BitmapFrame> _frames = new PriorityQueue<BitmapFrame>();
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
	
	public synchronized boolean isRecording() {
		return _transform != null;
	}
	
	public void setStateChangeListener(StateChangeListener listener) {
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
			final Queue<BitmapFrame> frames = _frames;
			reset();
			
			_context.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (_listener != null) {
						_listener.onStop();
					}
					
					new EncodeTask(_context, frames, _listener).execute();
				}
			});
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
	
	private synchronized void reset() {
		_transform = null;
		_framecount = 0;
		_frames = new PriorityQueue<BitmapFrame>();
		_recordProgressBar.setProgress(0);
		_recordProgressBar.setSecondaryProgress(0);
	}
	
	@Override
	public void accept(ImageBuffer buffer) {
		Bitmaps.Transform transform = _transform;
		
		if (_transform != null) {
			Bitmap bitmap = Bitmaps.transform(buffer.bitmap, transform);
	
			synchronized (this) {
				if (_transform != null) {
					_framecount++;
					_recordProgressBar.setSecondaryProgress(_framecount);
					_frames.add(new BitmapFrame(bitmap, buffer.timestamp));
					
					if (_framecount >= MAX_CAPTURED_FRAMES) {
						stop();
					}
				}
			}
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
			setCancelable(true);
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
						encoder.setDelay((int)(frame.timestamp - prevtimestamp));					
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
				cancel(true);
			}
			finally {
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
		protected void onCancelled(Void result) {
			if (_file != null) {
				_file.delete();
			}
			
			if (_listener != null) {
				_listener.onFinish();
			}

			super.onCancelled(result);
		}
		
		@Override
		protected void onPostExecute(Void result) {
			if (_listener != null) {
				_listener.onFinish();
			}
			
			super.onPostExecute(result);
		}
		
		@Override
		protected void onProgressUpdate(Integer... progress) {
			setProgress(progress[0]);
		}
	}
}
