package se.embargo.onebit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

import se.embargo.core.databinding.DataBindingContext;
import se.embargo.core.databinding.WidgetProperties;
import se.embargo.core.databinding.observable.IObservableValue;
import se.embargo.core.databinding.observable.WritableValue;
import se.embargo.core.graphics.Bitmaps;
import se.embargo.onebit.filter.AtkinsonFilter;
import se.embargo.onebit.filter.BayerFilter;
import se.embargo.onebit.filter.IBitmapFilter;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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

public class MainActivity extends SherlockActivity {
	private static final int CAMERA_RESPONSE_CODE = 1;
	private static final int GALLERY_RESPONSE_CODE = 2;
	
	private DataBindingContext _binding = new DataBindingContext();
	private IObservableValue<Bitmap> _image = new WritableValue<Bitmap>();
	private Uri _imageuri = null;

	private IBitmapFilter _filter;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);
        _filter = new AtkinsonFilter(this);
        
		final ImageView imageview = (ImageView)findViewById(R.id.image);
		_binding.bindValue(
			WidgetProperties.imageBitmap().observe(imageview),
			_image);
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
            case R.id.takePhoto: {
            	takePhoto();
	            return true;
            }

            case R.id.attachImage: {
	            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
	            intent.setType("image/*");
	            startActivityForResult(intent, GALLERY_RESPONSE_CODE);
	            return true;
            }
            
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void filterImage(File file) {
		Bitmap input = Bitmaps.decodeStream(file, 480, 320);
		
		if (input != null && input.getConfig() != null) {
			Bitmap output = _filter.apply(input);
	        _image.setValue(output);
		}
    }
    
	private void takePhoto() {
		FileOutputStream fd;
		String filename = "1bit.jpg";
		
		// Create the temporary file
		try {
			fd = openFileOutput(filename, Context.MODE_WORLD_WRITEABLE | Context.MODE_APPEND);
			fd.close();
		}
		catch (IOException e) {
            new AlertDialog.Builder(this).setMessage(
            	"Failed to create temporary file '" + filename + "'").setCancelable(true).create().show();
            return;
		}
		
		// Find out the absolute path to the file
        File target = new File(getFilesDir() + "/" + filename);
        _imageuri = Uri.fromFile(target);

        // Send intent to camera app
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, _imageuri);
        startActivityForResult(intent, CAMERA_RESPONSE_CODE);
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
							File file = new File(path);
							filterImage(file);
						}
					}

					query.close();
					break;

				case CAMERA_RESPONSE_CODE:
					if (_imageuri != null) {
						File file = new File(URI.create(_imageuri.toString()));
						filterImage(file);
						_imageuri = null;
					}
					break;
			}
		}
	}
}