package se.embargo.retroboy.color;

/**
 * Approximates an arbitrary palette by expanding it to a larger regular one
 */
public class BucketPalette implements IPalette {
	private static final int _bits = 4;
	private static final int _step = 8 - _bits;
	
	private final IPalette _palette;
	private final int[] _buckets = new int[1 << (_bits * 3)];
	
	private final int _gsb = _bits, 
					  _bsb = _bits * 2;
	
	private final int _rm = (1 << _bits) - 1, 
					  _gm = _rm << _gsb, 
					  _bm = _rm << _bsb;
	
	public BucketPalette(IPalette palette) {
		_palette = palette;
		
		for (int i = 0; i < _buckets.length; i++) {
			_buckets[i] = _palette.getNearestColor((i & _rm) << _bits, ((i & _gm) >> _gsb) << _bits, ((i & _bm) >> _bsb) << _bits);
		}
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
		return getNearestColor((pixel & 0x000000ff), (pixel & 0x0000ff00) >> 8, (pixel & 0x00ff0000) >> 16);
	}
}
