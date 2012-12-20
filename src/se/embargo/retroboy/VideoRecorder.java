package se.embargo.retroboy;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

import se.embargo.core.concurrent.ProgressTask;
import se.embargo.core.graphic.Bitmaps;
import se.embargo.core.graphic.Bitmaps.Transform;
import se.embargo.retroboy.color.IIndexedPalette;
import se.embargo.retroboy.filter.AbstractFilter;
import se.embargo.retroboy.graphic.GifEncoder;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

/**
 * Handles animated GIF recording.
 */
public class VideoRecorder extends AbstractFilter {
	private static final String TAG = "GifFilter";
	private static final int MAX_CAPTURED_FRAMES = 100;
	
	private final Activity _context;
	private final ProgressBar _recordProgressBar;
	
	private volatile Transform _transform = null;
	private IIndexedPalette _palette;
	
	private Queue<VideoFrame> _frames = new PriorityBlockingQueue<VideoFrame>();
	private int _framecount = 0;
	
	private RandomAccessFile _frameos;
	private FileChannel _framechan;
	private File _framefile;
	private long _framepos;
	
	private StateChangeListener _listener;
	
	public interface StateChangeListener {
		public void onRecord();
		public void onStop();
		public void onFinish();
	}
	
	public VideoRecorder(Activity context, View parent) {
		_context = context;
		_recordProgressBar = (ProgressBar)parent.findViewById(R.id.recordProgressBar);
		_recordProgressBar.setMax(MAX_CAPTURED_FRAMES);
		_framefile = new File(_context.getCacheDir() + File.separator + "frames.bin");
	}
	
	public boolean isRecording() {
		return _transform != null;
	}
	
	public synchronized void setStateChangeListener(StateChangeListener listener) {
		_listener = listener;
	}
	
	/**
	 * Start recording frames.
	 * @param	transform	Transform to apply on frames, e.g. rotation.
	 * @param	palette		Fixed indexed palette if one exists 
	 */
	public synchronized void record(Transform transform, IIndexedPalette palette) {
		if (_transform == null) {
			try {
				_frameos = new RandomAccessFile(_framefile, "rw");
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to open scratch file for output", e);
				_framefile.delete();
				return;
			}

			_framechan = _frameos.getChannel();
			_framepos = 0;

			_palette = palette;
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
			
			try {
				_framechan.close();
				_frameos.close();
				_framefile.delete();
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to open scratch file for output", e);
				_framefile.delete();
				return;
			}

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
		final RandomAccessFile frameos = _frameos;
		final FileChannel framechan = _framechan;
		final File framefile = _framefile;
		final Bitmaps.Transform transform = _transform;
		final Queue<VideoFrame> frames = _frames;
		_frames = new PriorityQueue<VideoFrame>();
		reset();
		
		_context.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (_listener != null) {
					_listener.onStop();
				}

				if (!frames.isEmpty()) {
					new EncodeTask(_context, frameos, framechan, framefile, transform, frames, _listener, _palette).execute();
				}
			}
		});
	}
	
	private synchronized void reset() {
		_framecount = 0;
		_frames = new PriorityQueue<VideoFrame>();
		_recordProgressBar.setProgress(0);
		_transform = null;
	}
	
	@Override
	public synchronized void accept(ImageBuffer buffer) {
		if (_transform != null) {
			int pixelcount = buffer.imagewidth * buffer.imageheight, 
				bytes = pixelcount * 4;
			
			// Map a memory block from the scratch file
			ByteBuffer block;
			try {
				block = _framechan.map(FileChannel.MapMode.READ_WRITE, _framepos, bytes);
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to map memory block from scratch file", e);
				stop();
				return;
			}
	
			// Output the frame
			int[] image = buffer.image.array();
			for (int i = 0; i < pixelcount; i++) {
				block.putInt(image[i]);
			}
			
			// Report progress
			_framecount++;
			_framepos += bytes;
			_frames.add(new VideoFrame(block, buffer.imagewidth, buffer.imageheight, buffer.timestamp));
			_recordProgressBar.setProgress(_framecount);
			
			// Stop automatically once the max number of frames has been captured
			if (_framecount >= MAX_CAPTURED_FRAMES) {
				stop();
			}
		}
	}
	
	private static class VideoFrame implements Comparable<VideoFrame> {
		public ByteBuffer block;
		public final int width, height;
		public final long timestamp;
		
		public VideoFrame(ByteBuffer block, int width, int height, long timestamp) {
			this.block = block;
			this.width = width;
			this.height = height;
			this.timestamp = timestamp;
		}

		@Override
		public int compareTo(VideoFrame other) {
			return timestamp < other.timestamp ? -1 : (timestamp == other.timestamp ? 0 : 1);
		}
	}
	
	private static class EncodeTask extends ProgressTask<Void, Integer, Void> {
		private final RandomAccessFile _frameos;
		private final FileChannel _framechan;
		private final File _framefile;
		private final Bitmaps.Transform _transform;
		private final Queue<VideoFrame> _frames;
		private final StateChangeListener _listener;
		private final IIndexedPalette _palette;
		private File _file = null;
		
		public EncodeTask(
				Context context, RandomAccessFile frameos, FileChannel framechan, File framefile, 
				Bitmaps.Transform transform, Queue<VideoFrame> frames, StateChangeListener listener, IIndexedPalette palette) {
			super(context, R.string.title_saving_image, R.string.msg_saving_image);
			setMaxProgress(frames.size());
			setCancelable();
			_frameos = frameos;
			_framechan = framechan;
			_framefile = framefile;
			_transform = transform;
			_frames = frames;
			_listener = listener;
			_palette = palette;
		}

		@Override
		protected Void doInBackground(Void... params) {
			int progress = 0;
			long prevtimestamp = 0;
			_file = Pictures.createOutputFile(getContext(), null, "gif");
			
			OutputStream os = null;
			Bitmap inputbm = null, outputbm = Bitmap.createBitmap(_transform.width, _transform.height, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(outputbm);
			Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
			int[] image = null;
			
			try {
				long ts = System.currentTimeMillis();
				
				// Create output encoder
				os = new BufferedOutputStream(new FileOutputStream(_file)); 
				GifEncoder encoder = new GifEncoder(_palette);
				encoder.setRepeat(0);
				encoder.start(os);
				
				// Encode all frames
				for (VideoFrame frame : _frames) {
					if (isCancelled()) {
						return null;
					}

					// Buffers for reading and transforming the frame
					int size = frame.width * frame.height;
					if (inputbm == null || inputbm.getWidth() != frame.width || inputbm.getHeight() != frame.height) {
						inputbm = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888);
						image = new int[size];
					}
					
					// Read input image
					final ByteBuffer block = frame.block;
					block.rewind();
					
					for (int i = 0; i < size; i++) {
						image[i] = block.getInt();
					}
					
					inputbm.setPixels(image, 0, frame.width, 0, 0, frame.width, frame.height);
					
					// Transform the frame
					canvas.drawBitmap(inputbm, _transform.matrix, paint);
					
					// Calculate the frame delay
					if (prevtimestamp != 0) {
						// Make sure to not multiply the rounding error (the delay is internally expressed in 1/100 seconds)
						long delay = (frame.timestamp - prevtimestamp) / 10000000L * 10000000L;
						encoder.setDelay((int)(delay / 1000000L));					
						prevtimestamp = frame.timestamp - ((frame.timestamp - prevtimestamp) - delay);
					}
					else {
						prevtimestamp = frame.timestamp;
					}
					
					// Encode the frame
					encoder.addFrame(outputbm);
					frame.block = null;
					
					// Publish progress
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
				outputbm.recycle();
				outputbm = null;

				if (inputbm != null) {
					inputbm.recycle();
					inputbm = null;
				}

				if (os != null) {
					try {
						os.close();
					}
					catch (Exception e) {}
				}
				
				try {
					_framechan.close();
					_frameos.close();
					_framefile.delete();
				}
				catch (Exception e) {}
				
				System.gc();
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
