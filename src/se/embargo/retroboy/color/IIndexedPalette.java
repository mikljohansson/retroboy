package se.embargo.retroboy.color;

public interface IIndexedPalette extends IPalette {
	/**
	 * @return	The colors of the palette.
	 */
	public int[] getIndexedColors();
	
	/**
	 * @return	The index of the nearest color from the palette.
	 */
	public int getNearestIndex(final int r1, final int g1, final int b1);
}
