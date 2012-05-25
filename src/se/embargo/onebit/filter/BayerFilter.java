package se.embargo.onebit.filter;


public class BayerFilter implements IImageFilter {
    private static final int[] _bayerThresholdMatrix = new int[] {
    	0, 128, 32, 160, 8, 136, 40, 168, 
    	192, 64, 224, 96, 200, 72, 232, 104, 
    	48, 176, 16, 144, 56, 184, 24, 152, 
    	225, 112, 208, 80, 233, 120, 216, 88, 
    	12, 140, 44, 172, 4, 132, 36, 164, 
    	204, 76, 236, 108, 196, 68, 228, 100, 
    	60, 188, 28, 156, 52, 180, 20, 148, 
    	237, 124, 220, 92, 229, 116, 212, 84};
	
    private int _width, _height;
	
	public BayerFilter(int width, int height) {
		_width = width;
		_height = height;
	}
    
    @Override
	public void accept(ImageBuffer buffer) {
    	final int[] image = buffer.image.array();
		final int width = buffer.width, height = buffer.height;
		final int stride = (int)Math.ceil(Math.max((float)width / _width, (float)height / _height));
		
		for (int y = 0, sy = 0; y < height; y += stride, sy++) {
			for (int x = 0, sx = 0; x < width; x += stride, sx++) {
				final int i = x + y * width;
				
				// Apply the threshold
				final int threshold = _bayerThresholdMatrix[sx % 8 + (sy % 8) * 8];
				final int mono = image[i] & 0xff;
				final int lum = mono <= threshold ? 0 : 255;
				final int pixel = 0xff000000 | (lum << 16) | (lum << 8) | lum;
				
				// Output the pixel block 
				for (int ty = 0; ty < stride; ty++) {
					for (int tx = 0; tx < stride; tx++) {
						image[i + tx + ty * width] = pixel;
					}
				}
			}
		}
	}
}
