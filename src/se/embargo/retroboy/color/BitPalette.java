package se.embargo.retroboy.color;

public class BitPalette implements IPalette {
	private final int _bits, _mask;
	
	/**
	 * @param	bits	Bit depth per color
	 */
	public BitPalette(int bits) {
		_bits = bits;
		_mask = ~((1 << bits) - 1);
	}
	
	@Override
	public int getNearestColor(int r1, int g1, int b1) {
		return ((b1 & _mask) << 16) | ((g1 & _mask) << 8) | (r1 & _mask);
	}

	@Override
	public int getColorCount() {
		return 1 << _bits;
	}
}
