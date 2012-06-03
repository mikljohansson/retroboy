package se.embargo.retroboy;

import java.io.File;

import se.embargo.core.graphics.Bitmaps;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.MonochromeFilter;
import se.embargo.retroboy.filter.ResizeFilter;
import se.embargo.retroboy.filter.YuvFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class ImageActivity extends SherlockActivity {
	private static final String EXTRA_NAMESPACE = "se.embargo.retroboy.ImageActivity";

	public static final String EXTRA_ACTION = 		EXTRA_NAMESPACE + ".action";
	public static final String EXTRA_DATA = 		EXTRA_NAMESPACE + ".data";
	public static final String EXTRA_DATA_WIDTH = 	EXTRA_NAMESPACE + ".data.width";
	public static final String EXTRA_DATA_HEIGHT = 	EXTRA_NAMESPACE + ".data.height";
	public static final String EXTRA_DATA_FACING = 	EXTRA_NAMESPACE + ".data.facing";
	
	private static final int GALLERY_RESPONSE_CODE = 1;
	
	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();

	private byte[] _inputdata;
	private int _inputwidth, _inputheight;
	
	private String _inputpath;
	private String _outputpath;
	
	private ImageView _imageview;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		_prefs = getSharedPreferences(Pictures.PREFS_NAMESPACE, MODE_PRIVATE);
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);
		
		setContentView(R.layout.image_activity);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		_imageview = (ImageView)findViewById(R.id.processedImage);

		// Read the preview frame
		_inputdata = getIntent().getByteArrayExtra(EXTRA_DATA);
		_inputwidth = getIntent().getIntExtra(EXTRA_DATA_WIDTH, 0);
		_inputheight = getIntent().getIntExtra(EXTRA_DATA_HEIGHT, 0);
		getIntent().removeExtra(EXTRA_DATA);
		
		// Restore instance state
		if (savedInstanceState != null) {
			_inputdata = savedInstanceState.getByteArray("inputdata");
			_inputwidth = savedInstanceState.getInt("inputwidth");
			_inputheight = savedInstanceState.getInt("inputheight");
			_inputpath = savedInstanceState.getString("inputpath");
			_outputpath = savedInstanceState.getString("outputpath");
		}

    	// Process image in background
		if (_inputdata != null && _inputwidth > 0 && _inputheight > 0) {
			new ProcessFrameTask(_inputdata, _inputwidth, _inputheight, _outputpath).execute();
		}
		else if (_inputpath != null) {
			new ProcessImageTask(_inputpath, _outputpath).execute();
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
		 savedInstanceState.putByteArray("inputdata", _inputdata);
		 savedInstanceState.putInt("inputwidth", _inputwidth);
		 savedInstanceState.putInt("inputheight", _inputheight);
		 savedInstanceState.putString("inputpath", _inputpath);
		 savedInstanceState.putString("outputpath", _outputpath);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.image_options, menu);
		
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
				if (_inputpath != null) {
					new ProcessImageTask(_inputpath, _outputpath).execute();
				}
			}
		}
	}
	
	/**
	 * Process an camera preview frame
	 */
	private class ProcessFrameTask extends AsyncTask<Void, Void, Void> {
		private String _outputpath;
		private IImageFilter.ImageBuffer _buffer;

		public ProcessFrameTask(byte[] data, int width, int height, String outputpath) {
			_outputpath = outputpath;
			_buffer = new IImageFilter.ImageBuffer(data, width, height);
		}

		@Override
		protected Void doInBackground(Void... params) {
			// Apply the image filter to the current image			
			CompositeFilter filter = new CompositeFilter();
			filter.add(new YuvFilter());
			filter.add(new ResizeFilter(Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT));
			filter.add(Pictures.createEffectFilter(ImageActivity.this));
			filter.add(new ImageBitmapFilter());
			filter.accept(_buffer);
			
			// Show the processed image
			publishProgress();
			
			// Write the image to disk
			Pictures.compress(ImageActivity.this, null, _outputpath, _buffer.bitmap);
			return null;
		}
		
		@Override
		protected void onProgressUpdate(Void... params) {
			// Show the processed image
			if (_buffer.bitmap != null) {
				_imageview.setImageBitmap(_buffer.bitmap);
			}
		}
	}

	/**
	 * Process an image read from disk
	 */
	private class ProcessImageTask extends AsyncTask<Void, Void, Void> {
		private final String _inputpath;
		private String _outputpath;
		private Bitmap _output;

		public ProcessImageTask(String inputpath, String outputpath) {
			_inputpath = inputpath;
			_outputpath = outputpath;
		}

		@Override
		protected Void doInBackground(Void... params) {
			// Read the image from disk
			Bitmap input = Bitmaps.decodeStream(new File(_inputpath), Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT);

			// Apply the image filter to the current image			
			CompositeFilter filter = new CompositeFilter();
			filter.add(new MonochromeFilter());
			filter.add(Pictures.createEffectFilter(ImageActivity.this));
			filter.add(new ImageBitmapFilter());
			
			IImageFilter.ImageBuffer buffer = new IImageFilter.ImageBuffer(input.getWidth(), input.getHeight());
			input.copyPixelsToBuffer(buffer.image);
			filter.accept(buffer);
			_output = buffer.bitmap;
			
			// Show the processed image
			publishProgress();
			
			// Write the image to disk
			Pictures.compress(ImageActivity.this, _inputpath, _outputpath, _output);
			return null;
		}
		
		@Override
		protected void onProgressUpdate(Void... params) {
			// Show the processed image
			if (_output != null) {
				_imageview.setImageBitmap(_output);
			}
		}
	}
}
