package se.embargo.retroboy;

import java.io.File;

import se.embargo.core.concurrent.ProgressTask;
import se.embargo.core.graphic.Bitmaps;
import se.embargo.core.widget.ListPreferenceDialog;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.ImageBitmapFilter;
import se.embargo.retroboy.filter.MonochromeFilter;
import se.embargo.retroboy.filter.RgbFilter;
import se.embargo.retroboy.widget.PreferenceListAdapter;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class ImageActivity extends SherlockActivity {
	private static final String TAG = "ImageActivity";
	
	public static final String EXTRA_ACTION = "se.embargo.retroboy.ImageActivity.action";
	public static final String EXTRA_ACTION_PICK = "pick";
	
	private static final int GALLERY_RESPONSE_CODE = 1;

	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();

	private ImageInfo _inputinfo;
	private String _outputpath;
	
	private ImageView _imageview;
	
	private ListView _detailedPreferences;
	private PreferenceListAdapter _detailedPreferenceAdapter = new PreferenceListAdapter(this);
	
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

		_detailedPreferences = (ListView)findViewById(R.id.detailedPreferences);
		_detailedPreferences.setOnItemClickListener(_detailedPreferenceAdapter);

		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_FILTER, R.string.pref_filter_default, R.string.menu_option_filter, 
			R.array.pref_filter_labels, R.array.pref_filter_values));

		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_RESOLUTION, R.string.pref_resolution_default, R.string.menu_option_resolution, 
			R.array.pref_resolution_labels, R.array.pref_resolution_values));

		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_CONTRAST, R.string.pref_contrast_default, R.string.menu_option_contrast, 
			R.array.pref_contrast_labels, R.array.pref_contrast_values));
		
		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_MATRIXSIZE, R.string.pref_matrixsize_default, R.string.menu_option_matrixsize, 
			R.array.pref_matrixsize_labels, R.array.pref_matrixsize_values,
			new PreferenceListAdapter.PreferencePredicate(_prefs, 
				Pictures.PREF_FILTER, Pictures.PREF_FILTER_GAMEBOY_CAMERA, new String[] {
					Pictures.PREF_FILTER_GAMEBOY_CAMERA,
					Pictures.PREF_FILTER_AMSTRAD_CPC464,
					Pictures.PREF_FILTER_COMMODORE_64,
					Pictures.PREF_FILTER_AMIGA_500,
			})));
		
		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_RASTERLEVEL, R.string.pref_rasterlevel_default, R.string.menu_option_rasterlevel, 
			R.array.pref_rasterlevel_labels, R.array.pref_rasterlevel_values,
			new PreferenceListAdapter.PreferencePredicate(_prefs, 
				Pictures.PREF_FILTER, Pictures.PREF_FILTER_GAMEBOY_CAMERA, new String[] {
					Pictures.PREF_FILTER_AMSTRAD_CPC464,
					Pictures.PREF_FILTER_COMMODORE_64,
			})));

		_detailedPreferenceAdapter.add(new Pictures.PalettePreferenceItem(this, _prefs));

		_detailedPreferenceAdapter.add(new PreferenceListAdapter.ArrayPreferenceItem(this, _prefs,
			Pictures.PREF_AUTOEXPOSURE, R.string.pref_autoexposure_default, R.string.menu_option_autoexposure, 
			R.array.pref_autoexposure_labels, R.array.pref_autoexposure_values,
			new PreferenceListAdapter.PreferencePredicate(_prefs, 
				Pictures.PREF_FILTER, Pictures.PREF_FILTER_GAMEBOY_CAMERA, new String[] {
					Pictures.PREF_FILTER_GAMEBOY_CAMERA,
					Pictures.PREF_FILTER_ATKINSON,
			})));
		
		// Set the adapter after populating to ensure list height measure is done properly
		_detailedPreferences.setAdapter(_detailedPreferenceAdapter);
		
		// Close the detail preferences when clicked outside
		_imageview.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				reset();
			}
		});
		
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
		setInput(inputuri);
		
        // Check for action parameter
        String action = getIntent().getStringExtra(EXTRA_ACTION);
        if (EXTRA_ACTION_PICK.equals(action) && _inputinfo == null) {
    		pickImage();
        }
    }

	private void pickImage() {
		Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		intent.setType("image/*");
		startActivityForResult(intent, GALLERY_RESPONSE_CODE);
	}

	private void setInput(Uri inputuri) {
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
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			switch(requestCode){
				case GALLERY_RESPONSE_CODE:
					if (data != null) {
						setInput(data.getData());
					}
					break;

				default:
					finish();
					break;
			}
		}
		else {
			finish();
		}
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
		if (item.getItemId() == android.R.id.home) {
            // When app icon in action bar clicked, go up
        	startParentActivity();
            return true;
        }
		else if (item.getItemId() == R.id.shareImageButton) {
        	reset();
        	
        	if (_outputpath != null) {
            	Intent intent = new Intent(Intent.ACTION_SEND);
            	intent.setType("image/png");
            	intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + _outputpath));
            	startActivity(Intent.createChooser(intent, getText(R.string.menu_option_share_image)));
        	}
        	
        	return true;
        }
		else if (item.getItemId() == R.id.pickImageButton) {
			_inputinfo = null;
			_outputpath = null;
			pickImage();
			return true;
		}
		else if (item.getItemId() == R.id.switchFilterButton) {
			reset();
			
			new ListPreferenceDialog(
				this, _prefs, 
				Pictures.PREF_FILTER, getResources().getString(R.string.pref_filter_default),
				R.string.menu_option_filter, R.array.pref_filter_labels, R.array.pref_filter_values).show();
			return true;
		}
		else if (item.getItemId() == R.id.discardImageButton) {
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
		else if (item.getItemId() == R.id.editSettingsButton) {
        	toggleDetailedPreferences();
			return true;
		}
            
		return super.onOptionsItemSelected(item);
	}
	
    private void startParentActivity() {
        finish();
    }
    
	@Override
	public void onBackPressed() {
		// Close the detail preferences if open
		if (_detailedPreferences.getVisibility() == View.VISIBLE) {
			reset();
		}
		else {
			super.onBackPressed();
		}
	}
    
	private void reset() {
		if (_detailedPreferences.getVisibility() == View.VISIBLE) {
			_detailedPreferences.setVisibility(View.GONE);
		}
	}
    
	private void toggleDetailedPreferences() {
		if (_detailedPreferences.getVisibility() != View.VISIBLE) {
			_detailedPreferences.setVisibility(View.VISIBLE);
		}
		else {
			reset();
		}
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
	
	private class PreferencesListener implements SharedPreferences.OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			if (Pictures.PREF_FILTER.equals(key) || 
				Pictures.PREF_CONTRAST.equals(key) || 
				Pictures.PREF_RESOLUTION.equals(key) ||
				Pictures.PREF_MATRIXSIZE.equals(key) ||
				Pictures.PREF_RASTERLEVEL.equals(key) ||
				Pictures.PREF_AUTOEXPOSURE.equals(key) ||
				Pictures.PREF_PALETTE.equals(key)) {
				// Process image in background
				if (_inputinfo != null) {
					new ProcessImageTask(_inputinfo, _outputpath).execute();
				}
			}
			
			_detailedPreferenceAdapter.notifyDataSetChanged();
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
			
			// Check the auto exposure setting
			String autoexposurevalue = _prefs.getString(Pictures.PREF_AUTOEXPOSURE, getResources().getString(R.string.pref_autoexposure_default));
			boolean autoexposure = "auto".equals(autoexposurevalue);
			
			// Create the image filter pipeline
			IImageFilter.ImageBuffer buffer = new IImageFilter.ImageBuffer(input);
			CompositeFilter filter = new CompositeFilter();
			
			IImageFilter effect = Pictures.createEffectFilter(ImageActivity.this);
			if (effect.isColorFilter()) {
				filter.add(new RgbFilter(contrast, autoexposure));
			}
			else {
				filter.add(new MonochromeFilter(contrast, autoexposure));
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
