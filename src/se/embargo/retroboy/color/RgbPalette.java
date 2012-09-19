package se.embargo.retroboy.color;

public class RgbPalette implements IPalette {
	public int getPaletteSize() {
		return Integer.MAX_VALUE;
	}
	
	public int getNearestColor(final int r1, final int g1, final int b1) {
		return 0xff000000 | (b1 << 16) | (g1 << 8) | r1;
	}
	
	public int getNearestColor(final int pixel) {
		return pixel;
	}
}
