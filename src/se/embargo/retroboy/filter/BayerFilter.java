package se.embargo.retroboy.filter;


public class BayerFilter implements IImageFilter {
	private static final int _patternsize = 8;
	private static final float[] _thresholds = new float[] {
    	0, 128, 32, 160, 8, 136, 40, 168, 
    	192, 64, 224, 96, 200, 72, 232, 104, 
    	48, 176, 16, 144, 56, 184, 24, 152, 
    	225, 112, 208, 80, 233, 120, 216, 88, 
    	12, 140, 44, 172, 4, 132, 36, 164, 
    	204, 76, 236, 108, 196, 68, 228, 100, 
    	60, 188, 28, 156, 52, 180, 20, 148, 
    	237, 124, 220, 92, 229, 116, 212, 84};
    
    @Override
	public void accept(ImageBuffer buffer) {
    	final int[] image = buffer.image.array();
		final int width = buffer.imagewidth, height = buffer.imageheight;
		
		// Factor used to offset the threshold to compensate for too dark or bright images
		final float factor = (float)buffer.threshold / 128;
		
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				final int i = x + y * width;
				final int mono = image[i] & 0xff;
				
				// Apply the threshold
				final int threshold = (int)(_thresholds[x % _patternsize + (y % _patternsize) * _patternsize] * factor);
				final int lum = mono <= threshold ? 0 : 255;
				
				// Output the pixel
				image[i] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
			}
		}
	}

	@Override
	public int getEffectiveWidth(int framewidth, int frameheight) {
		return 0;
	}

	@Override
	public int getEffectiveHeight(int framewidth, int frameheight) {
		return 0;
	}
}
