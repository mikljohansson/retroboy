package se.embargo.retroboy;

import java.io.File;

import se.embargo.core.graphics.Bitmaps;
import se.embargo.retroboy.filter.BitmapImageFilter;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.TransformFilter;
import se.embargo.retroboy.filter.YuvFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class ReviewActivity extends SherlockActivity {
	private static final String EXTRA_NAMESPACE = "se.embargo.retroboy.ReviewActivity";

	public static final String EXTRA_DATA = 				EXTRA_NAMESPACE + ".data";
	public static final String EXTRA_DATA_WIDTH = 			EXTRA_NAMESPACE + ".data.width";
	public static final String EXTRA_DATA_HEIGHT = 			EXTRA_NAMESPACE + ".data.height";
	public static final String EXTRA_DATA_FACING = 			EXTRA_NAMESPACE + ".data.facing";
	public static final String EXTRA_DATA_ORIENTATION = 	EXTRA_NAMESPACE + ".data.orientation";
	public static final String EXTRA_DATA_ROTATION = 		EXTRA_NAMESPACE + ".data.rotation";
	
	private SharedPreferences _prefs;

	static public byte[] _inputdata;
	private int _inputwidth, _inputheight, _inputfacing, _inputorientation, _inputrotation;
	
	private String _outputpath;
	
	private ImageView _imageview;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		// Read the preview frame
		//_inputdata = getIntent().getByteArrayExtra(EXTRA_DATA);
		_inputwidth = getIntent().getIntExtra(EXTRA_DATA_WIDTH, 0);
		_inputheight = getIntent().getIntExtra(EXTRA_DATA_HEIGHT, 0);
		_inputfacing = getIntent().getIntExtra(EXTRA_DATA_FACING, 0);
		_inputorientation = getIntent().getIntExtra(EXTRA_DATA_ORIENTATION, 0);
		_inputrotation = getIntent().getIntExtra(EXTRA_DATA_ROTATION, 0);
		getIntent().removeExtra(EXTRA_DATA);
		
		// Restore instance state
		if (savedInstanceState != null) {
			_inputdata = savedInstanceState.getByteArray(EXTRA_DATA);
			_inputwidth = savedInstanceState.getInt(EXTRA_DATA_WIDTH);
			_inputheight = savedInstanceState.getInt(EXTRA_DATA_HEIGHT);
			_inputfacing = savedInstanceState.getInt(EXTRA_DATA_FACING);
			_inputorientation = savedInstanceState.getInt(EXTRA_DATA_ORIENTATION);
			_inputrotation = savedInstanceState.getInt(EXTRA_DATA_ROTATION);
			_outputpath = savedInstanceState.getString("outputpath");
		}

		_prefs = getSharedPreferences(Pictures.PREFS_NAMESPACE, MODE_PRIVATE);
		
		setContentView(R.layout.review_activity);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		_imageview = (ImageView)findViewById(R.id.processedImage);
		
		// Attempt to read previously processed image
		if (_outputpath != null) {
			Bitmap bm = BitmapFactory.decodeFile(_outputpath);
			if (bm != null) {
				_imageview.setImageBitmap(bm);
			}
			else {
				_outputpath = null;
			}
		}

		// Process image in background
		if (_outputpath == null && _inputdata != null) {
			new ProcessFrameTask(_inputdata, _inputwidth, _inputheight, _inputfacing, _inputorientation, _inputrotation, _outputpath).execute();
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		 super.onSaveInstanceState(savedInstanceState);
		 
		 // Store instance state
		 savedInstanceState.putByteArray(EXTRA_DATA, _inputdata);
		 savedInstanceState.putInt(EXTRA_DATA_WIDTH, _inputwidth);
		 savedInstanceState.putInt(EXTRA_DATA_HEIGHT, _inputheight);
		 savedInstanceState.putInt(EXTRA_DATA_FACING, _inputfacing);
		 savedInstanceState.putInt(EXTRA_DATA_ORIENTATION, _inputorientation);
		 savedInstanceState.putInt(EXTRA_DATA_ROTATION, _inputrotation);
		 savedInstanceState.putString("outputpath", _outputpath);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.review_options, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
            case android.R.id.home: {
                // When app icon in action bar clicked, go up
            	startParentActivity();
                return true;
            }
            
            case R.id.discardImageOption: {
            	// Return to parent activity without saving the image
            	if (_outputpath != null) {
            		// Delete the processed image from disk 
            		new File(_outputpath).delete();
            		
            		// Also remove the image from the gallery
    				getContentResolver().delete(
    					MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
    					MediaStore.Images.Media.DATA + "=?", new String[] {_outputpath});
            	}
            	
            	startParentActivity();
                return true;
            }

			case R.id.switchFilterOption: {
				Pictures.toggleImageFilter(this);
				return true;
			}
            
            case R.id.shareImageOption: {
            	if (_outputpath != null) {
	            	Intent intent = new Intent(Intent.ACTION_SEND);
	            	intent.setType("image/png");
	            	intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + _outputpath));
	            	startActivity(Intent.createChooser(intent, getText(R.string.menu_option_share_image)));
            	}
            	
            	return true;
            }
            
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
    private void startParentActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
		
	/**
	 * Process an camera preview frame
	 */
	private class ProcessFrameTask extends AsyncTask<Void, Void, File> {
		private final String _outputpath;
		private IImageFilter _filter;
		private final IImageFilter.ImageBuffer _buffer;
		private final Bitmaps.Transform _transform;

		public ProcessFrameTask(byte[] data, int width, int height, int facing, int orientation, int rotation, String outputpath) {
			_outputpath = outputpath;
			_buffer = new IImageFilter.ImageBuffer(data, width, height);
			
			// Get the contrast adjustment
			int contrast = 0;
			try {
				contrast = Integer.parseInt(_prefs.getString(Pictures.PREF_CONTRAST, Pictures.PREF_CONTRAST_DEFAULT));
			}
			catch (NumberFormatException e) {}
			
			// Create the image filter pipeline
			YuvFilter yuvFilter = new YuvFilter(Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT, contrast);
			_transform = Pictures.createTransformMatrix(
				ReviewActivity.this, 
				yuvFilter.getEffectiveWidth(width, height), 
				yuvFilter.getEffectiveHeight(width, height), 
				facing, orientation, rotation);
			
			CompositeFilter filter = new CompositeFilter();
			filter.add(yuvFilter);
			filter.add(new ImageBitmapFilter());
			filter.add(new TransformFilter(_transform));
			filter.add(new BitmapImageFilter());
			filter.add(Pictures.createEffectFilter(ReviewActivity.this));
			filter.add(new ImageBitmapFilter());
			_filter = filter;
		}

		@Override
		protected File doInBackground(Void... params) {
			// Apply the image filter to the current image			
			_filter.accept(_buffer);
			
			// Show the processed image
			publishProgress();
			
			// Write the image to disk
			return Pictures.compress(ReviewActivity.this, null, _outputpath, _buffer.bitmap);
		}
		
		@Override
		protected void onProgressUpdate(Void... params) {
			// Show the processed image
			if (_buffer.bitmap != null) {
				_imageview.setImageBitmap(_buffer.bitmap);
			}
		}
		
		@Override
		protected void onPostExecute(File result) {
			ReviewActivity.this._outputpath = result.toString();
		}
	}
}
