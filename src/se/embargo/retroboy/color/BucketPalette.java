package se.embargo.retroboy.color;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;

/**
 * Approximates an arbitrary palette by expanding it to a larger regular one.
 */
public class BucketPalette implements IPalette {
	/**
	 * Number of most significant bits to store per color channel.
	 */
	private static final int _bits = 4;
	
	/**
	 * Number of bits to shift a color.
	 */
	private static final int _step = 8 - _bits;
	
	/**
	 * Palette to sample colors from.
	 */
	private final IPalette _palette;
	
	/**
	 * Each bucket corresponds to an RGB color and stores the color the inner palette returned.
	 */
	private final int[] _buckets = new int[1 << (_bits * 3)];
	
	/**
	 * Number of bits to shift green and blue colors. 
	 */
	private final int _gsb = _bits, 
					  _bsb = _bits * 2;
	
	/**
	 * Red, green and blue bit masks.
	 */
	private final int _rm = (1 << _bits) - 1, 
					  _gm = _rm << _gsb, 
					  _bm = _rm << _bsb;
	
	public BucketPalette(IPalette palette) {
		_palette = palette;

		// Initialize the cached buckets
		Parallel.forRange(new ForBody<int[]>() {
			@Override
			public void run(int[] item, int it, int last) {
				for (int i = it; i < last; i++) {
					final int r = (i & _rm) << _step,
							  g = ((i & _gm) >> _gsb) << _step,
							  b = ((i & _bm) >> _bsb) << _step;
					
					item[i] = _palette.getNearestColor(r, g, b);
				}
			}
		}, _buckets, 0, _buckets.length);
	}
	
	@Override
	public int getPaletteSize() {
		return _palette.getPaletteSize();
	}

	@Override
	public int getNearestColor(final int r1, final int g1, final int b1) {
		return _buckets[(r1 >> _step) | ((g1 >> _step) << _gsb) | ((b1 >> _step) << _bsb)];
	}

	public int getNearestColor(final int pixel) {
		return getNearestColor(pixel & 0xff, (pixel >> 8) & 0xff, (pixel >> 16) & 0xff);
	}
}
