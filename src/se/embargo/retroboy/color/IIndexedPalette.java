package se.embargo.retroboy.color;

public interface IIndexedPalette extends IPalette {
	/**
	 * @return	The colors of the palette.
	 */
	public int[] getColors();
	
	/**
	 * @return	The index of a color from the palette.
	 */
	public int getIndex(final int color);
}
