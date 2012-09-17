package se.embargo.retroboy.color;

public interface IPalette {
	public int getPaletteSize();
	public int getNearestColor(final int r1, final int g1, final int b1);
	public int getNearestColor(final int pixel);
}
