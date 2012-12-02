package se.embargo.retroboy;

import java.io.File;

import se.embargo.core.concurrent.ProgressTask;
import se.embargo.core.graphic.Bitmaps;
import se.embargo.retroboy.filter.ColorFilter;
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
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
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

	private ImageInfo _inputinfo;
	private String _outputpath;
	
	private ImageView _imageview;
	
	private ProcessImageTask _task = null;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		// Restore instance state
		if (savedInstanceState != null) {
			_inputinfo = savedInstanceState.getParcelable("inputinfo");
			_outputpath = savedInstanceState.getString("outputpath");
		}

		_prefs = getSharedPreferences(Pictures.PREFS_NAMESPACE, MODE_PRIVATE);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		setContentView(R.layout.image_activity);
		
		_imageview = (ImageView)findViewById(R.id.processedImage);
		
		// Attempt to read previously processed image
		if (_outputpath != null) {
			Bitmap bm = BitmapFactory.decodeFile(_outputpath);
			if (bm != null) {
				_imageview.setImageBitmap(bm);
			}
			else {
				Log.w(TAG, "Failed to read previously created image " + _outputpath);
				_outputpath = null;
			}
		}
		
		// Read input from intent
		Uri inputuri = (Uri)getIntent().getExtras().get(Intent.EXTRA_STREAM);
		if (inputuri != null) {
			// Find the image info
			Cursor cursor = null;
			
			try {
				cursor = getContentResolver().query(inputuri, new String[] {Images.Media.DISPLAY_NAME, Images.Media.ORIENTATION}, null, null, null);
				if (cursor != null && cursor.moveToFirst()) {
					_inputinfo = new ImageInfo(inputuri, cursor.getString(0), cursor.getInt(1));
					Log.i(TAG, "Image name: " + _inputinfo.filename);					
				}
			}
			finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		}

		// Set application title
		if (_inputinfo != null) {
			getSupportActionBar().setTitle(_inputinfo.filename);
		}
		
		// Process image in background
		if (_outputpath == null && _inputinfo != null) {
			new ProcessImageTask(_inputinfo, _outputpath).execute();
		}
    }

	@Override
	protected void onResume() {
		super.onResume();

		// Listen to preference changes
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);
	}
	
	private void stop() {
		_prefs.unregisterOnSharedPreferenceChangeListener(_prefsListener);

		// Cancel any image processing tasks
		if (_task != null) {
			_task.cancel(false);
			_task = null;
		}
	}
	
	@Override
	protected void onPause() {
		stop();
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		stop();
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		stop();
		super.onDestroy();
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		 super.onSaveInstanceState(savedInstanceState);
		 
		 // Store instance state
		 savedInstanceState.putParcelable("inputinfo", _inputinfo);
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
            
            case R.id.shareImageButton: {
            	if (_outputpath != null) {
	            	Intent intent = new Intent(Intent.ACTION_SEND);
	            	intent.setType("image/png");
	            	intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + _outputpath));
	            	startActivity(Intent.createChooser(intent, getText(R.string.menu_option_share_image)));
            	}
            	
            	return true;
            }

			case R.id.switchFilterButton: {
				new ListPreferenceDialog(
					this, _prefs, 
					Pictures.PREF_FILTER, getResources().getString(R.string.pref_filter_default),
					R.string.pref_title_filter, R.array.pref_filter_labels, R.array.pref_filter_values).show();
				return true;
			}
            
			case R.id.adjustContrastButton: {
				new ListPreferenceDialog(
					this, _prefs, 
					Pictures.PREF_CONTRAST, getResources().getString(R.string.pref_contrast_default),
					R.string.pref_title_contrast, R.array.pref_contrast_labels, R.array.pref_contrast_values).show();
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

            case R.id.editSettingsButton: {
				// Start preferences activity
            	/*
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				*/
    			new ListPreferenceDialog(
    				this, _prefs,
    				Pictures.PREF_RESOLUTION, getResources().getString(R.string.pref_resolution_default),
    				R.string.pref_title_resolution, R.array.pref_resolution_labels, R.array.pref_resolution_values).show();
				return true;
			}
            
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
    private void startParentActivity() {
        finish();
    }
    
    /**
     * Holds information about a gallery image
     */
    private static class ImageInfo implements Parcelable {
    	public Uri uri;
    	public String filename;
    	public int orientation;
    	
    	public ImageInfo(Uri uri, String filename, int orientation) {
    		this.uri = uri;
    		this.filename = filename;
    		this.orientation = orientation;
    	}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(uri.toString());
			dest.writeString(filename);
			dest.writeInt(orientation);
		}
		
		private ImageInfo(Parcel in) {
			uri = Uri.parse(in.readString());
			filename = in.readString();
			orientation = in.readInt();
		}
		
		@SuppressWarnings("unused")
		public static final Parcelable.Creator<ImageInfo> CREATOR = new Parcelable.Creator<ImageInfo>() {
		    public ImageInfo createFromParcel(Parcel in) {
		        return new ImageInfo(in);
		    }
		
		    public ImageInfo[] newArray(int size) {
		        return new ImageInfo[size];
		    }
		};
    }
	
	private class PreferencesListener implements OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			if (Pictures.PREF_FILTER.equals(key) || Pictures.PREF_CONTRAST.equals(key) || Pictures.PREF_RESOLUTION.equals(key)) {
				// Process image in background
				if (_inputinfo != null) {
					new ProcessImageTask(_inputinfo, _outputpath).execute();
				}
			}
		}
	}

	/**
	 * Process an image read from disk
	 */
	private class ProcessImageTask extends ProgressTask<Void, Void, File> {
		private final ImageInfo _inputinfo;
		private final String _previouspath;

		public ProcessImageTask(ImageInfo inputinfo, String outputpath) {
			super(ImageActivity.this, R.string.title_saving_image, R.string.msg_saving_image);
			_inputinfo = inputinfo;
			_previouspath = outputpath;
			_task = this;
		}

		@Override
		protected File doInBackground(Void... params) {
			// Get the resolution and contrast from preferences
			Pictures.Resolution resolution = Pictures.getResolution(ImageActivity.this, _prefs);
			int contrast = Pictures.getContrast(ImageActivity.this, _prefs);

			// Read the image from disk
			Log.i(TAG, "Reading image: " + _inputinfo.uri);
			Bitmap input = Bitmaps.decodeUri(getContentResolver(), _inputinfo.uri, resolution.width, resolution.height);

			// Rotate the image as needed
			if (_inputinfo.orientation != 0) {
				Bitmaps.Transform transform = Bitmaps.createTransform(
					input.getWidth(), input.getHeight(),
					input.getWidth(), input.getHeight(),
					0, _inputinfo.orientation, false);
				input = Bitmaps.transform(input, transform);
			}
			
			// Create the image filter pipeline
			IImageFilter.ImageBuffer buffer = new IImageFilter.ImageBuffer(input);
			CompositeFilter filter = new CompositeFilter();
			
			IImageFilter effect = Pictures.createEffectFilter(ImageActivity.this);
			if (effect.isColorFilter()) {
				filter.add(new ColorFilter(contrast));
			}
			else {
				filter.add(new MonochromeFilter(contrast));
			}
			
			filter.add(effect);
			filter.add(new ImageBitmapFilter());

			// Apply the image filter to the current image			
			filter.accept(buffer);
			
			// Write the image to disk
			File result = Pictures.compress(ImageActivity.this, _inputinfo.filename, _previouspath, buffer.bitmap);
			Log.i(TAG, "Wrote image: " + result);
			return result;
		}
		
		@Override
		protected void onCancelled(File result) {
			if (_task == this) {
				_task = null;
			}

			super.onCancelled(result);
		}
		
		@Override
		protected void onPostExecute(File result) {
			// Remember the written file in case the filter or contrast is changed
			_outputpath = result.toString();
			
			// Show the processed image
			Bitmap bm = BitmapFactory.decodeFile(result.toString());
			if (bm != null) {
				_imageview.setImageBitmap(bm);
			}
			else {
				Log.w(TAG, "Failed to read created image " + result.toString());
			}
			
			if (_task == this) {
				_task = null;
			}

			super.onPostExecute(result);
		}
	}
}
