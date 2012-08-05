package se.embargo.retroboy;

import java.io.File;

import se.embargo.core.graphics.Bitmaps;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.MonochromeFilter;
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

public class ImageActivity extends SherlockActivity {
	private static final int GALLERY_RESPONSE_CODE = 1;
	private static final String EXTRA_NAMESPACE = "se.embargo.retroboy.ImageActivity";

	public static final String EXTRA_ACTION = 				EXTRA_NAMESPACE + ".action";
	public static final String EXTRA_ACTION_PICK = 			EXTRA_NAMESPACE + ".action.pick";
	
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

		// Process image in background
		if (_outputpath == null && _inputpath != null) {
			new ProcessImageTask(_inputpath, _outputpath).execute();
		}
		
        // Check if an action has been request
		String action = getIntent().getStringExtra(EXTRA_ACTION);
		getIntent().removeExtra(EXTRA_ACTION);

		// Pick a gallery image to process
    	if (EXTRA_ACTION_PICK.equals(action)) {
        	Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, GALLERY_RESPONSE_CODE);
        }
    }
	
	@Override
	protected void onResume() {
		super.onResume();
		
		// Initialize the activity name
		if (_inputpath != null) {
			String name = new File(_inputpath).getName();
			if (name != null && !name.isEmpty()) {
				setTitle(name);
			}
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
							_outputpath = null;
							_imageview.setImageBitmap(null);
							
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
			Bitmap input = Bitmaps.decodeStream(new File(_inputpath), Pictures.IMAGE_WIDTH, Pictures.IMAGE_HEIGHT);
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
			
			// Write the image to disk
			return Pictures.compress(ImageActivity.this, _inputpath, _outputpath, _output);
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
