package se.embargo.retroboy.graphic;

import se.embargo.retroboy.color.IPalette;

/**
 * A continous color quantizer. 
 */
public interface IColorQuantizer {
	/**
	 * Sample an frame and add it into the quantizer state.
	 * @param palette	Palette to apply to pixels
	 * @param frame		BGR pixels to sample
	 * @param stride	Number of pixels to step
	 */
	public void sample(IPalette palette, int[] frame, int length, int stride);
	
	/**
	 * Return the currently quantized palette 
	 * @return			The quantized palette colors.
	 */
	public int[] getPalette();
}
