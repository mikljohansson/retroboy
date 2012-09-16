package se.embargo.retroboy;

public class Palettes {
	public static final int[] MONOCHROME = new int[] {
		0xff000000,
		0xffffffff};
	
	public static final int[] GAMEBOY_CAMERA = new int[] {
		0xff000000, 
		0xff858585, 
		0xffaaaaaa, 
		0xffffffff};

	public static final int[] GAMEBOY_SCREEN = new int[] {
		0xff0f380f, 
		0xff306230, 
		0xff0fac8b, 
		0xff0fbc9b};
	
	public static final int[] COMMODORE_64 = new int[] {
		0xFF000000,
		0xFFFFFFFF,
		0xFF2B3768,
		0xFFB2A470,
		0xFF863D6F,
		0xFF438D58,
		0xFF792835,
		0xFF6FC7B8,
		0xFF254F6F,
		0xFF003943,
		0xFF59679A,
		0xFF444444,
		0xFF6C6C6C,
		0xFF84D29A,
		0xFFB55E6C,
		0xFF959595};
	
	public static int getNearestColor(final int r, final int g, final int b, final int[] palette) {
		int color = 0, mindistance = 0xffffff;
		
		// Find palette color with minimum Euclidean distance to pixel 
		for (int i = 0; i < palette.length; i++) {
			final int d1 = r - ((palette[i] & 0x00ff0000) >> 16),
					  d2 = g - ((palette[i] & 0x0000ff00) >> 8),
					  d3 = b - (palette[i] & 0x000000ff);
			final int distance = d1 * d1 + d2 * d2 + d3 * d3;
			
			if (distance < mindistance) {
				color = palette[i];
				mindistance = distance;
			}
		}
		
		return color;
	}
	
	public static int getNearestColor(final int pixel, final int[] palette) {
		return getNearestColor((pixel & 0x00ff0000) >> 16, (pixel & 0x0000ff00) >> 8, (pixel & 0x000000ff), palette);
	}
}
