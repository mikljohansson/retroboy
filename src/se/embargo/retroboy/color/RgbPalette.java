package se.embargo.retroboy.color;

/**
 * Returns the input color in RGB space.
 */
public class RgbPalette implements IPalette {
	@Override
	public int getNearestColor(final int r1, final int g1, final int b1) {
		return 0xff000000 | (b1 << 16) | (g1 << 8) | r1;
	}
}
