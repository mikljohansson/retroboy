package se.embargo.retroboy;

import java.io.File;

import se.embargo.core.graphics.Bitmaps;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.MonochromeFilter;
import se.embargo.retroboy.widget.ListPreferenceDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
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
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);
		
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
	protected void onDestroy() {
		dismiss();
		super.onDestroy();
	}
	
	private void dismiss() {
		// Cancel any image processing tasks
		if (_task != null) {
			_task.cancel(false);
			_task = null;
		}
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
					Pictures.PREF_FILTER, Pictures.PREF_FILTER_DEFAULT,
					R.string.pref_title_filter, R.array.pref_filter_labels, R.array.pref_filter_values).show();
				return true;
			}
            
			case R.id.adjustContrastButton: {
				new ListPreferenceDialog(
					this, _prefs, 
					Pictures.PREF_CONTRAST, Pictures.PREF_CONTRAST_DEFAULT,
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
    				Pictures.PREF_RESOLUTION, Pictures.PREF_RESOLUTION_DEFAULT,
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
	private class ProcessImageTask extends AsyncTask<Void, Void, File> {
		private final ImageInfo _inputinfo;
		private final String _outputpath;
		private Bitmap _output;
		private ProgressDialog _progress;

		public ProcessImageTask(ImageInfo inputinfo, String outputpath) {
			_inputinfo = inputinfo;
			_outputpath = outputpath;
			_task = this;
		}
		
		@Override
		protected void onPreExecute() {
			// Show a progress dialog
			_progress = new ProgressDialog(ImageActivity.this);
			_progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			_progress.setIndeterminate(true);
			_progress.setMessage(getResources().getString(R.string.msg_saving_image));
			_progress.show();
		}

		@Override
		protected File doInBackground(Void... params) {
			// Get the resolution and contrast from preferences
			Pictures.Resolution resolution = Pictures.getResolution(_prefs);
			int contrast = Pictures.getContrast(_prefs);

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
			if (!effect.isColorFilter()) {
				filter.add(new MonochromeFilter(contrast));
			}
			
			filter.add(effect);
			filter.add(new ImageBitmapFilter());

			// Apply the image filter to the current image			
			filter.accept(buffer);
			_output = buffer.bitmap;
			
			// Write the image to disk
			File result = Pictures.compress(ImageActivity.this, _inputinfo.filename, _outputpath, _output);
			Log.i(TAG, "Wrote image: " + result);
			return result;
		}
		
		@Override
		protected void onCancelled() {
			// Close the progress dialog
			dismiss();
		}
		
		@Override
		protected void onPostExecute(File result) {
			// Remember the written file in case the filter or contrast is changed
			ImageActivity.this._outputpath = result.toString();
			
			// Show the processed image
			_imageview.setImageBitmap(_output);
			
			// Close the progress dialog
			dismiss();
		}
		
		private void dismiss() {
			if (_task == this) {
				_task = null;
			}
			
			_progress.dismiss();
		}
	}
}
