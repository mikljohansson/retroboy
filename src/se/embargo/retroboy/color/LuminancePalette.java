package se.embargo.retroboy.color;

/**
 * Returns the nearest color in the psychovisual model weighted on luminance.
 * @link	http://bisqwit.iki.fi/story/howto/dither/jy/
 */
public class LuminancePalette implements IPalette {
	private final int[] _palette;
	
	public LuminancePalette(int[] palette) {
		_palette = palette;
	}
	
	public int getPaletteSize() {
		return _palette.length;
	}
	
	public int getNearestColor(final int r1, final int g1, final int b1) {
		int color = 0;
		int mindistance = Integer.MAX_VALUE;
		
		for (int i = 0; i < _palette.length; i++) {
			final int r2 = _palette[i] & 0xff,
				  	  g2 = (_palette[i] >> 8) & 0xff,
				  	  b2 = (_palette[i] >> 16) & 0xff;

			final int l1 = r1 * 299 + g1 * 587 + b1 * 114;
			final int l2 = r2 * 299 + g2 * 587 + b2 * 114;
			final int dl = (l1 - l2) / 1000;
			
			final int dr = r1 - r2,
					  dg = g1 - g2,
					  db = b1 - b2;
			
			final int distance = (dr * dr * 299 + dg * dg * 587 + db * db * 114) / 4000 * 3 + dl * dl;
			if (distance < mindistance) {
				color = _palette[i];
				mindistance = distance;
			}
		}
		
		return color;
	}
	
	public int getNearestColor(final int pixel) {
		return getNearestColor(pixel & 0xff, (pixel >> 8) & 0xff, (pixel >> 16) & 0xff);
	}
}
