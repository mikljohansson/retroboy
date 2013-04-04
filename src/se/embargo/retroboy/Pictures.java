package se.embargo.retroboy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import se.embargo.core.Strings;
import se.embargo.core.graphic.Bitmaps;
import se.embargo.retroboy.color.BitPalette;
import se.embargo.retroboy.color.DistancePalette;
import se.embargo.retroboy.color.Distances;
import se.embargo.retroboy.color.IPalette;
import se.embargo.retroboy.color.Palettes;
import se.embargo.retroboy.filter.AtkinsonFilter;
import se.embargo.retroboy.filter.BayerFilter;
import se.embargo.retroboy.filter.CompositeFilter;
import se.embargo.retroboy.filter.HalftoneFilter;
import se.embargo.retroboy.filter.IImageFilter;
import se.embargo.retroboy.filter.QuantizeFilter;
import se.embargo.retroboy.filter.RasterFilter;
import se.embargo.retroboy.graphic.DitherMatrixes;
import se.embargo.retroboy.widget.PreferenceListAdapter;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;

public class Pictures {
	private static final String TAG = "Pictures";

	public static final String PREFS_NAMESPACE = "se.embargo.retroboy";
	
	public static final String PREF_FILTER = "filter";
	
	public static final String PREF_FILTER_GAMEBOY_CAMERA = "nintendo_gameboy_camera";
	public static final String PREF_FILTER_AMSTRAD_CPC464 = "amstrad_cpc464";
	public static final String PREF_FILTER_COMMODORE_64 = "commodore_64";
	public static final String PREF_FILTER_AMIGA_500 = "amiga_500";
	public static final String PREF_FILTER_ATKINSON = "atkinson";
	public static final String PREF_FILTER_HALFTONE = "halftone";
	public static final String PREF_FILTER_NONE = "none";

	public static final String PREF_CONTRAST = "contrast";
	public static final String PREF_RESOLUTION = "resolution";
	public static final String PREF_ORIENTATION = "orientation";
	public static final String PREF_EXPOSURE = "exposure";
	public static final String PREF_PALETTE = "palette";
	public static final String PREF_MATRIXSIZE = "matrixsize";
	public static final String PREF_RASTERLEVEL = "rasterlevel";
	public static final String PREF_SCENEMODE = "scenemode";
	private static final String PREF_IMAGECOUNT = "imagecount";
	
	private static final String DIRECTORY = "Retroboy";
	private static final String FILENAME_PATTERN = "IMGR%04d";

	public static class Resolution {
		public final int width, height;
		
		public Resolution(int width, int height) {
			this.width = width;
			this.height = height;
		}
		
		@Override
		public String toString() {
			return width + "x" + height;
		}
	}
	
	/**
	 * @return	The directory where images are stored
	 */
	public static File getStorageDirectory() {
		File result = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/" + DIRECTORY);
		result.mkdirs();
		return result;
	}
	
	/**
	 * Get the contrast adjustment from preferences
	 * @param prefs	Preferences to get the contrast from
	 * @return		The selected contrast adjustment, [-100, 100]
	 */
	public static int getContrast(Context context, SharedPreferences prefs) {
		String contrast = prefs.getString(Pictures.PREF_CONTRAST, context.getResources().getString(R.string.pref_contrast_default));
		try {
			return Integer.parseInt(contrast);
		}
		catch (NumberFormatException e) {}
		
		Log.w(TAG, "Failed to parse contrast preference " + contrast);
		return 0;
	}

	/**
	 * Get the preview resolution
	 * @param prefs	Preferences to get the resolution from
	 * @return		The selected preview resultion
	 */
	public static Resolution getResolution(Context context, SharedPreferences prefs) {
		String resolution = prefs.getString(Pictures.PREF_RESOLUTION, context.getResources().getString(R.string.pref_resolution_default));
		String[] components = resolution.split("x");
		
		if (components.length == 2) {
			try {
				int width = Integer.parseInt(components[0]),
					height = Integer.parseInt(components[1]);
				return new Resolution(width, height);
			}
			catch (NumberFormatException e) {}
		}
		
		Log.w(TAG, "Failed to parse resolution " + resolution);
		return new Resolution(480, 360);
	}
	
	@SuppressLint("DefaultLocale")
	public static File compress(Context context, String inputname, String outputpath, Bitmap bm) {
		// Create path to output file
		File file;
		if (outputpath != null) {
			// Overwrite the previously processed file
			file = new File(outputpath);

			// Create parent directory as needed
			new File(file.getParent()).mkdirs();
		}
		else {
			file = createOutputFile(context, inputname, "png");
		}
		
		try {
			// Delete old instance of image
			context.getContentResolver().delete(
				MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
				MediaStore.Images.Media.DATA + "=?", new String[] {file.getAbsolutePath()});
			file.delete();
			
			// Write the file to disk
			FileOutputStream os = new FileOutputStream(file);
			boolean written = bm.compress(Bitmap.CompressFormat.PNG, 75, os);
			if (!written) {
				Log.w(TAG, "Failed to write output image to " + file.toString());
			}
			os.flush();
			os.close();
			
			// Tell the gallery about the image
			if (written) {
				ContentValues values = new ContentValues();
				values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
				values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
				values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
				context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
			}
		}
		catch (IOException e) {}
		
		return file;
	}
	
	public static File createOutputFile(Context context, String inputname, String fileext) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAMESPACE, Context.MODE_PRIVATE);
		
		String filename;
		File file;
		
		do {
			if (inputname != null) {
				// Use the original image name
				filename = new File(inputname).getName();
				filename = filename.split("\\.", 2)[0];
				filename += "." + fileext;
			}
			else {
				// Create a new sequential name
				int count = prefs.getInt(PREF_IMAGECOUNT, 0);
				filename = String.format(FILENAME_PATTERN + "." + fileext, count);
				
				// Increment the image count
				SharedPreferences.Editor editor = prefs.edit();
				editor.putInt(PREF_IMAGECOUNT, count + 1);
				editor.commit();
			}
			
			file = new File(getStorageDirectory(), filename);
			inputname = null;
		} while (file.exists());
		
		return file;
	}
	
	public static IImageFilter createEffectFilter(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAMESPACE, Context.MODE_PRIVATE);
		String filtertype = prefs.getString(PREF_FILTER, context.getResources().getString(R.string.pref_filter_default));
		int[] matrix = getMatrix(context, prefs);
		int rasterlevel = Strings.parseInt(prefs.getString(PREF_RASTERLEVEL, 
			context.getResources().getString(R.string.pref_rasterlevel_default)), 4);
		
		if (PREF_FILTER_AMSTRAD_CPC464.equals(filtertype)) {
			return new RasterFilter(context, Distances.LUV, Palettes.AMSTRAD_CPC464, matrix, rasterlevel);
		}

		if (PREF_FILTER_COMMODORE_64.equals(filtertype)) {
			return new RasterFilter(context, Distances.LUV, Palettes.COMMODORE_64_GAMMA_ADJUSTED, matrix, rasterlevel);
			//return new BayerFilter(new BucketPalette(new YuvPalette(Palettes.COMMODORE_64_GAMMA_ADJUSTED)), true);
			//return new YliluomaTriFilter(context, Distances.LUV, Palettes.COMMODORE_64_GAMMA_ADJUSTED);
		}

		if (PREF_FILTER_AMIGA_500.equals(filtertype)) {
			//IPalette palette = new BucketPalette(new DistancePalette(Distances.YUV, Palettes.AMSTRAD_CPC464));
			IPalette palette = new BitPalette(4);
			
			CompositeFilter filter = new CompositeFilter();
			BayerFilter effect = new BayerFilter(palette, matrix, BayerFilter.PaletteType.Color);
			//PaletteFilter effect = new PaletteFilter(palette);
			filter.add(new QuantizeFilter(palette, effect));
			filter.add(effect);
			return filter;
		}
		
		if (PREF_FILTER_ATKINSON.equals(filtertype)) {
			return new AtkinsonFilter();
		}

		if (PREF_FILTER_HALFTONE.equals(filtertype)) {
			return new HalftoneFilter();
		}

		if (PREF_FILTER_NONE.equals(filtertype)) {
			return new BayerFilter(new BitPalette(4), matrix, BayerFilter.PaletteType.Color);
			//return new PaletteFilter(new BitPalette(4));
		}

		// Default to a Nintendo Game Boy filter
		int[] palette;
		String palettename = prefs.getString(PREF_PALETTE, context.getResources().getString(R.string.pref_gameboy_palette_default));
		
		if (palettename.equals("gameboy_screen")) {
			palette = Palettes.GAMEBOY_SCREEN_DESAT;
		}
		else if (palettename.equals("binary")) {
			palette = Palettes.BINARY;
		}
		else {
			palette = Palettes.GAMEBOY_CAMERA;
		}
		
		return new BayerFilter(new DistancePalette(Distances.YUV, palette), matrix, BayerFilter.PaletteType.Threshold);
	}
	
	private static int[] getMatrix(Context context, SharedPreferences prefs) {
		int matrixsize = Strings.parseInt(prefs.getString(PREF_MATRIXSIZE, 
			context.getResources().getString(R.string.pref_matrixsize_default)), 4);
		
		switch (matrixsize) {
			case 8: 
				return DitherMatrixes.MATRIX_8x8;
			
			case 2: 
				return DitherMatrixes.MATRIX_2x2;
		}
		
		return DitherMatrixes.MATRIX_4x4;
	}

	/**
	 * Creates a matrix that rotates and scales an input frame to fit the preview surface.
	 * @param	inputwidth		Input frame width 
	 * @param	inputheight		Input frame height
	 * @param	facing			CameraInfo.facing 
	 * @param	orientation		CameraInfo.orientation
	 * @param	rotation		Display.getRotation()
	 * @param	outputwidth		Width of target surface
	 * @param	outputheight	Height of target surface
	 * @param	flags			Combination of Bitmaps.FLAG_*'s
	 * @return					A matrix to use with a surface
	 */
	public static Bitmaps.Transform createTransformMatrix(
			int inputwidth, int inputheight, int facing, int orientation, int rotation, 
			int outputwidth, int outputheight, int flags) {
		// Check the current window rotation
		int degrees = 0;
		switch (rotation) {
			case Surface.ROTATION_0: degrees = 0; break;
			case Surface.ROTATION_90: degrees = 90; break;
			case Surface.ROTATION_180: degrees = 180; break;
			case Surface.ROTATION_270: degrees = 270; break;
		}

		int rotate;
		boolean mirror;
		if (facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			rotate = (orientation + degrees) % 360;
			mirror = true;
		} 
		else {
			rotate = (orientation - degrees + 360) % 360;
			mirror = false;
		}
		
		return Bitmaps.createTransform(inputwidth, inputheight, outputwidth, outputheight, flags, rotate, mirror);
	}

	/**
	 * Creates a matrix that rotates and scales an input frame to fit the preview surface.
	 * @param	inputwidth		Input frame width 
	 * @param	inputheight		Input frame height
	 * @param	facing			CameraInfo.facing 
	 * @param	orientation		CameraInfo.orientation
	 * @param	rotation		Display.getRotation()
	 * @param	resolution		Output resolution
	 * @return					A matrix to use with a surface
	 */
	public static Bitmaps.Transform createTransformMatrix(
			int inputwidth, int inputheight, int facing, int orientation, int rotation,
			Resolution resolution) {
		int maxwidth, maxheight;
		if (inputwidth >= inputheight) {
			maxwidth = resolution.width;
			maxheight = resolution.height;
		}
		else {
			maxwidth = resolution.height;
			maxheight = resolution.width;
		}
		
		return createTransformMatrix(inputwidth, inputheight, facing, orientation, rotation, maxwidth, maxheight, 0);
	}

	public static int getCameraOrientation(SharedPreferences prefs, Camera.CameraInfo info, int cameraId) {
		int orientation = Strings.parseInt(prefs.getString(PREF_ORIENTATION + "_" + cameraId, "-1"), -1);
		if (orientation < 0) {
			orientation = info.orientation;
		}
		
		return orientation;
	}

	public static class PalettePreferenceItem extends PreferenceListAdapter.ArrayPreferenceItem {
		public PalettePreferenceItem(Context context, SharedPreferences prefs) {
			super(context, prefs,
				Pictures.PREF_PALETTE, R.string.pref_gameboy_palette_default, R.string.menu_option_palette, 
				R.array.pref_gameboy_palette_labels, R.array.pref_gameboy_palette_values,
				new PreferenceListAdapter.PreferencePredicate(prefs, 
					Pictures.PREF_FILTER, Pictures.PREF_FILTER_GAMEBOY_CAMERA, new String[] {
						Pictures.PREF_FILTER_GAMEBOY_CAMERA,
				}));
		}
	}
}
