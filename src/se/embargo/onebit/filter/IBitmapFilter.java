package se.embargo.onebit.filter;

import android.graphics.Bitmap;

/**
 * Bitmap graphic filter
 */
public interface IBitmapFilter {
	/**
	 * Applies this filter to a bitmap
	 * @param 	bm	The input bitmap may be overwritten by the filter
	 * @return		Returns the filtered bitmap
	 */
	public Bitmap apply(Bitmap input);
}
