package se.embargo.retroboy.color;

/**
 * Returns the nearest color according to the distance metric.
 */
public class DistancePalette implements IIndexedPalette {
	private final IColorDistance _distance;
	private final int[] _colors;
	
	public DistancePalette(IColorDistance distance, int[] colors) {
		_distance = distance;
		_colors = colors;
	}
	
	@Override
	public int getNearestColor(final int r1, final int g1, final int b1) {
		int color = 0;
		double mindistance = Double.MAX_VALUE;
		
		for (int i = 0; i < _colors.length; i++) {
			final int r2 = _colors[i] & 0xff,
					  g2 = (_colors[i] >> 8) & 0xff,
					  b2 = (_colors[i] >> 16) & 0xff;

			final double distance = _distance.get(r1, g1, b1, r2, g2, b2);
			if (distance < mindistance) {
				color = _colors[i];
				mindistance = distance;
			}
		}
		
		return color;
	}

	@Override
	public int[] getColors() {
		return _colors;
	}
	
	@Override
	public int getIndex(final int color) {
		for (int i = 0; i < _colors.length; i++) {
			if ((color & 0xffffff) == (_colors[i] & 0xffffff)) {
				return i;
			}
		}
		
		return 0;
	}
}
