package se.embargo.retroboy;

import java.io.File;

import se.embargo.core.graphics.Bitmaps;
import se.embargo.retroboy.filter.BitmapImageFilter;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.MonochromeFilter;
import se.embargo.retroboy.filter.TransformFilter;
import se.embargo.retroboy.filter.YuvFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
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
	private static final String EXTRA_NAMESPACE = "se.embargo.retroboy.ImageActivity";

	public static final String EXTRA_ACTION = 				EXTRA_NAMESPACE + ".action";
	public static final String EXTRA_DATA = 				EXTRA_NAMESPACE + ".data";
	public static final String EXTRA_DATA_WIDTH = 			EXTRA_NAMESPACE + ".data.width";
	public static final String EXTRA_DATA_HEIGHT = 			EXTRA_NAMESPACE + ".data.height";
	public static final String EXTRA_DATA_FACING = 			EXTRA_NAMESPACE + ".data.facing";
	public static final String EXTRA_DATA_ORIENTATION = 	EXTRA_NAMESPACE + ".data.orientation";
	public static final String EXTRA_DATA_ROTATION = 		EXTRA_NAMESPACE + ".data.rotation";
	
	private static final int GALLERY_RESPONSE_CODE = 1;
	
	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();

	private byte[] _inputdata;
	private int _inputwidth, _inputheight, _inputfacing, _inputorientation, _inputrotation;
	
	private String _inputpath;
	private String _outputpath;
	
	private ImageView _imageview;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		// Read the preview frame
		_inputdata = getIntent().getByteArrayExtra(EXTRA_DATA);
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
			_inputpath = savedInstanceState.getString("inputpath");
			_outputpath = savedInstanceState.getString("outputpath");
		}

		_prefs = getSharedPreferences(Pictures.PREFS_NAMESPACE, MODE_PRIVATE);
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);
		
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
		if (_outputpath == null) {
			if (_inputdata != null && _inputwidth > 0 && _inputheight > 0) {
				new ProcessFrameTask(_inputdata, _inputwidth, _inputheight, _inputfacing, _inputorientation, _inputrotation, _outputpath).execute();
			}
			else if (_inputpath != null) {
				new ProcessImageTask(_inputpath, _outputpath).execute();
			}
		}
		
        // Check if an action has been request
		String action = getIntent().getStringExtra(EXTRA_ACTION);
		getIntent().removeExtra(EXTRA_ACTION);

    	if ("pick".equals(action)) {
    		// Pick a gallery image to process
        	Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, GALLERY_RESPONSE_CODE);
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
		 savedInstanceState.putString("inputpath", _inputpath);
		 savedInstanceState.putString("outputpath", _outputpath);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.review_options, menu);
		
		// Set the correct icon for the filter button
		menu.getItem(1).setIcon(Pictures.getFilterDrawableResource(this));
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
            
			case R.id.switchFilterOption: {
				Pictures.toggleImageFilter(this);
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

            case R.id.selectImageOption: {
        		// Pick a gallery image to process
            	Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                intent.setType("image/*");
                startActivityForResult(intent, GALLERY_RESPONSE_CODE);
	            return true;
            }

            case R.id.editSettingsOption: {
				// Start preferences activity
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
			}

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch(requestCode){
				case GALLERY_RESPONSE_CODE:
					// Query the media store for the image details
					Uri contenturi = data.getData();
					Cursor query = getContentResolver().query(
						contenturi, 
						new String[] {MediaStore.MediaColumns.TITLE, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATA}, 
						null, null, null);
					
					// Read the image from disk
					if (query.moveToFirst()) {
						String path = query.getString(query.getColumnIndex(MediaStore.MediaColumns.DATA));
						
						if (path != null) {
							_inputpath = path;
							
							// Process image in background
							new ProcessImageTask(_inputpath, null).execute();
						}
					}

					query.close();
					break;
					
				default:
					super.onActivityResult(requestCode, resultCode, data);
					break;
			}
		}
		else if (_outputpath == null) {
			// Return to parent if no image was selected and none has been processed already
			startParentActivity();
		}
	}
	
    private void startParentActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
	
	private class PreferencesListener implements OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			if ("filter".equals(key)) {
				// Update the action bar icon
				invalidateOptionsMenu();

				// Process image in background
				if (_inputdata != null && _inputwidth > 0 && _inputheight > 0) {
					new ProcessFrameTask(_inputdata, _inputwidth, _inputheight, _inputfacing, _inputorientation, _inputrotation, _outputpath).execute();
				}
				else if (_inputpath != null) {
					new ProcessImageTask(_inputpath, _outputpath).execute();
				}
			}
		}
	}
	
	/**
	 * Process an camera preview frame
	 */
	private class ProcessFrameTask extends AsyncTask<Void, Void, File> {
		private final String _outputpath;
		private final IImageFilter.ImageBuffer _buffer;
		private final Bitmaps.Transform _transform;

		public ProcessFrameTask(byte[] data, int width, int height, int facing, int orientation, int rotation, String outputpath) {
			_outputpath = outputpath;
			_buffer = new IImageFilter.ImageBuffer(data, width, height);
			_transform = Pictures.createTransformMatrix(ReviewActivity.this, Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT, facing, orientation, rotation);
		}

		@Override
		protected File doInBackground(Void... params) {
			// Apply the image filter to the current image			
			CompositeFilter filter = new CompositeFilter();
			filter.add(new YuvFilter(Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT));
			filter.add(new ImageBitmapFilter());
			filter.add(new TransformFilter(_transform));
			filter.add(new BitmapImageFilter());
			filter.add(Pictures.createEffectFilter(ReviewActivity.this));
			filter.add(new ImageBitmapFilter());
			filter.accept(_buffer);
			
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

	/**
	 * Process an image read from disk
	 */
	private class ProcessImageTask extends AsyncTask<Void, Void, File> {
		private final String _inputpath;
		private final String _outputpath;
		private Bitmap _output;

		public ProcessImageTask(String inputpath, String outputpath) {
			_inputpath = inputpath;
			_outputpath = outputpath;
		}

		@Override
		protected File doInBackground(Void... params) {
			// Read the image from disk
			Bitmap input = Bitmaps.decodeStream(new File(_inputpath), Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT);
			IImageFilter.ImageBuffer buffer = new IImageFilter.ImageBuffer(input);

			// Apply the image filter to the current image			
			CompositeFilter filter = new CompositeFilter();
			filter.add(new MonochromeFilter());
			filter.add(Pictures.createEffectFilter(ReviewActivity.this));
			filter.add(new ImageBitmapFilter());
			filter.accept(buffer);
			_output = buffer.bitmap;
			
			// Show the processed image
			publishProgress();
			
			// Write the image to disk
			return Pictures.compress(ReviewActivity.this, _inputpath, _outputpath, _output);
		}
		
		@Override
		protected void onProgressUpdate(Void... params) {
			// Show the processed image
			if (_output != null) {
				_imageview.setImageBitmap(_output);
			}
		}
		
		@Override
		protected void onPostExecute(File result) {
			ReviewActivity.this._outputpath = result.toString();
		}
	}
}
