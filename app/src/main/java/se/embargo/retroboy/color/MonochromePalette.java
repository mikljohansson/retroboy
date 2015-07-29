package se.embargo.retroboy.color;

import se.embargo.core.graphic.color.IIndexedPalette;

public class MonochromePalette implements IIndexedPalette {
	private final int _bits, _mask;
	private int[] _colors;
		
	public MonochromePalette(int bits) {
		_bits = bits;
		_mask = ~((1 << (8 - _bits)) - 1);
		_colors = new int[1 << bits];
		
		for (int i = 0; i < _colors.length; i++) {
			final int lum = i << (8 - bits);
			_colors[i] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
		}
	}
	
	@Override
	public int getNearestColor(int r1, int g1, int b1) {
		return ((b1 & _mask) << 16) | ((g1 & _mask) << 8) | (r1 & _mask);
	}

	@Override
	public int getColorCount() {
		return _colors.length;
	}

	@Override
	public int[] getColors() {
		return _colors;
	}

	@Override
	public int getIndex(int color) {
		return (color & 0xff) >> (8 - _bits);
	}
}
