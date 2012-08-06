package se.embargo.retroboy;

import java.io.File;

import se.embargo.core.graphics.Bitmaps;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.MonochromeFilter;
import se.embargo.retroboy.widget.ListPreferenceDialog;
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
import android.provider.MediaStore.Images;
import android.util.Log;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class ImageActivity extends SherlockActivity {
	private static final String TAG = "ImageActivity";
	
	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();

	private String _inputpath;
	private String _outputpath;
	
	private ImageView _imageview;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		// Restore instance state
		if (savedInstanceState != null) {
			_inputpath = savedInstanceState.getString("inputpath");
			_outputpath = savedInstanceState.getString("outputpath");
		}

		_prefs = getSharedPreferences(Pictures.PREFS_NAMESPACE, MODE_PRIVATE);
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);
		
		setContentView(R.layout.image_activity);
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
		
		// Read input from intent
		Uri inputuri = (Uri)getIntent().getExtras().get(Intent.EXTRA_STREAM);
		if (inputuri != null) {
			_inputpath = inputuri.toString();
			Log.i(TAG, "Input image: " + _inputpath);
		}

		// Process image in background
		if (_outputpath == null && _inputpath != null) {
			new ProcessImageTask(_inputpath, _outputpath).execute();
		}
    }
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		 super.onSaveInstanceState(savedInstanceState);
		 
		 // Store instance state
		 savedInstanceState.putString("inputpath", _inputpath);
		 savedInstanceState.putString("outputpath", _outputpath);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.image_options, menu);
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
            
            case R.id.discardImageButton: {
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

			case R.id.switchFilterButton: {
				new ListPreferenceDialog(
					this, _prefs, 
					Pictures.PREF_FILTER, Pictures.PREF_FILTER_DEFAULT,
					R.string.pref_title_filter, R.array.pref_filter_labels, R.array.pref_filter_values).show();
				return true;
			}
            
            case R.id.shareImageButton: {
            	if (_outputpath != null) {
	            	Intent intent = new Intent(Intent.ACTION_SEND);
	            	intent.setType("image/png");
	            	intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + _outputpath));
	            	startActivity(Intent.createChooser(intent, getText(R.string.menu_option_share_image)));
            	}
            	
            	return true;
            }

            case R.id.editSettingsButton: {
				// Start preferences activity
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
			}
            
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
    private void startParentActivity() {
        finish();
    }
	
	private class PreferencesListener implements OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			if (Pictures.PREF_FILTER.equals(key)) {
				// Process image in background
				if (_inputpath != null) {
					new ProcessImageTask(_inputpath, _outputpath).execute();
				}
			}
			else if (Pictures.PREF_CONTRAST.equals(key)) {
				// Process image in background
				if (_inputpath != null) {
					new ProcessImageTask(_inputpath, _outputpath).execute();
				}
			}
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
			Log.i(TAG, "Reading image: " + _inputpath);
			Uri inputuri = Uri.parse(_inputpath);
			Bitmap input = Bitmaps.decodeUri(getContentResolver(), inputuri, Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT);
			IImageFilter.ImageBuffer buffer = new IImageFilter.ImageBuffer(input);
			
			// Get the contrast adjustment
			int contrast = 0;
			try {
				contrast = Integer.parseInt(_prefs.getString(Pictures.PREF_CONTRAST, Pictures.PREF_CONTRAST_DEFAULT));
			}
			catch (NumberFormatException e) {}

			// Apply the image filter to the current image			
			CompositeFilter filter = new CompositeFilter();
			filter.add(new MonochromeFilter(contrast));
			filter.add(Pictures.createEffectFilter(ImageActivity.this));
			filter.add(new ImageBitmapFilter());
			filter.accept(buffer);
			_output = buffer.bitmap;
			
			// Show the processed image
			publishProgress();
			
			// Find the name of the image
			Cursor cursor = null;
			String inputname = null;
			
			try {
				cursor = getContentResolver().query(inputuri, new String[] {Images.Media.DISPLAY_NAME}, null, null, null);
				if (cursor != null && cursor.moveToFirst()) {
					inputname = cursor.getString(0);
					Log.i(TAG, "Image name: " + inputname);					
				}
			}
			finally {
				if (cursor != null) {
					cursor.close();
				}
			}
			
			// Write the image to disk
			File result = Pictures.compress(ImageActivity.this, inputname, _outputpath, _output);
			Log.i(TAG, "Wrote image: " + result);
			return result;
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
			ImageActivity.this._outputpath = result.toString();
		}
	}
}
