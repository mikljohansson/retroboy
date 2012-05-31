package se.embargo.onebit;

import java.io.File;

import se.embargo.core.graphics.Bitmaps;
import se.embargo.onebit.filter.CompositeFilter;
import se.embargo.onebit.filter.IImageFilter;
import se.embargo.onebit.filter.ImageBitmapFilter;
import se.embargo.onebit.filter.MonochromeFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class ImageActivity extends SherlockActivity {
	public static final String EXTRA_ACTION = "se.embargo.onebit.ImageActivity.EXTRA_ACTION";
	public static final String EXTRA_FILE = "se.embargo.onebit.ImageActivity.EXTRA_FILE";
	public static final String EXTRA_DATA = "se.embargo.onebit.ImageActivity.EXTRA_DATA";

	private static final int GALLERY_RESPONSE_CODE = 1;
	
	private SharedPreferences _prefs;
	
	/**
	 * The listener needs to be kept alive since SharedPrefernces only keeps a weak reference to it
	 */
	private PreferencesListener _prefsListener = new PreferencesListener();

	private IImageFilter _filter;
	private Bitmap _image;
	private String _imagepath;
	
	private ImageView _imageview;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		_prefs = getSharedPreferences(MainActivity.PREFS_NAMESPACE, MODE_PRIVATE);
		_prefs.registerOnSharedPreferenceChangeListener(_prefsListener);
		_prefsListener.onSharedPreferenceChanged(_prefs, "filter");
		
		setContentView(R.layout.image_activity);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		_imageview = (ImageView)findViewById(R.id.processedImage);
        
		// Read the selected image
		byte[] data = getIntent().getByteArrayExtra(EXTRA_DATA);
		_imagepath = getIntent().getStringExtra(EXTRA_FILE);
		getIntent().removeExtra(EXTRA_DATA);
		getIntent().removeExtra(EXTRA_FILE);
		
		// Restore instance state
		if (_imagepath == null && savedInstanceState != null) {
			_imagepath = savedInstanceState.getString("_imagepath");
		}
		
		if (data != null) {
			_image = Bitmaps.decodeByteArray(data, MainActivity.IMAGE_WIDTH, MainActivity.IMAGE_HEIGHT);
		}
		else if (_imagepath != null) {
			_image = Bitmaps.decodeStream(new File(_imagepath), MainActivity.IMAGE_WIDTH, MainActivity.IMAGE_HEIGHT);
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
	protected void onResume() {
		super.onResume();
		refresh();
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		 super.onSaveInstanceState(savedInstanceState);
		 
		 // Store instance state
		 if (_imagepath != null) {
			 savedInstanceState.putString("_imagepath", _imagepath);
		 }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_options, menu);
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

            case R.id.attachImageOption: {
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
					Uri contenturi = data.getData();
					Cursor query = getContentResolver().query(
						contenturi, 
						new String[] {MediaStore.MediaColumns.TITLE, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATA}, 
						null, null, null);
					
					if (query.moveToFirst()) {
						String path = query.getString(query.getColumnIndex(MediaStore.MediaColumns.DATA));
						
						if (path != null) {
							_image = Bitmaps.decodeStream(new File(path), MainActivity.IMAGE_WIDTH, MainActivity.IMAGE_HEIGHT);
							_imagepath = path;
							refresh();
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
	
	private void refresh() {
		if (_image != null) {
			IImageFilter.ImageBuffer buffer = new IImageFilter.ImageBuffer(_image.getWidth(), _image.getHeight());
			_image.copyPixelsToBuffer(buffer.image);
			_filter.accept(buffer);
			_imageview.setImageBitmap(buffer.bitmap);
		}
	}
	
	private class PreferencesListener implements OnSharedPreferenceChangeListener {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			if ("filter".equals(key)) {
				CompositeFilter filter = new CompositeFilter();
				filter.add(new MonochromeFilter());
				filter.add(MainActivity.createEffectFilter(_prefs));
				filter.add(new ImageBitmapFilter());
				_filter = filter;
			}
		}
	}
}
