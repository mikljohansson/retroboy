package se.embargo.retroboy.color;

/**
 * Returns the nearest color in the YUV space.
 * @link	http://www.compuphase.com/cmetric.htm
 */
public class IndexedPalette implements IIndexedPalette {
	private final IColorDistance _distance;
	private final int[] _palette;
	
	public IndexedPalette(IColorDistance distance, int[] palette) {
		_distance = distance;
		_palette = palette;
	}
	
	@Override
	public int getNearestColor(final int r1, final int g1, final int b1) {
		return _palette[getNearestIndex(r1, g1, b1)];
	}

	@Override
	public int[] getIndexedColors() {
		return _palette;
	}
	
	@Override
	public int getNearestIndex(final int r1, final int g1, final int b1) {
		int index = 0;
		double mindistance = Double.MAX_VALUE;
		
		for (int i = 0; i < _palette.length; i++) {
			final int r2 = _palette[i] & 0xff,
					  g2 = (_palette[i] >> 8) & 0xff,
					  b2 = (_palette[i] >> 16) & 0xff;

			final double distance = _distance.get(r1, g1, b1, r2, g2, b2);
			if (distance < mindistance) {
				index = i;
				mindistance = distance;
			}
		}
		
		return index;
	}
}
