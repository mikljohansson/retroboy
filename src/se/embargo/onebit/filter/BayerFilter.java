package se.embargo.onebit.filter;


public class BayerFilter implements IImageFilter {
    private static final int[] _bayerThresholdMatrix = new int[] {
    	0, 128, 32, 160, 8, 136, 40, 168, 
    	192, 64, 224, 96, 200, 72, 232, 104, 
    	48, 176, 16, 144, 56, 184, 24, 152, 
    	240, 112, 208, 80, 248, 120, 216, 88, 
    	12, 140, 44, 172, 4, 132, 36, 164, 
    	204, 76, 236, 108, 196, 68, 228, 100, 
    	60, 188, 28, 156, 52, 180, 20, 148, 
    	252, 124, 220, 92, 244, 116, 212, 84};

    @Override
	public void accept(PreviewBuffer buffer) {
		byte[] data = buffer.data;
		int[] image = buffer.image;
		final int width = buffer.width;
		final int height = buffer.height;
		final int stride = buffer.stride;
		
		for (int y = 0, ty = 0; y < height; y += stride, ty++) {
			for (int x = 0, tx = 0; x < width; x += stride, tx++) {
				int i = x + y * width;
				
				// Convert from YUV luminance to monochrome
				int mono = (((int)data[i]) & 0xff) - 16;
				mono = Math.min(Math.max(0, mono), 255);
				
				// Apply the threshold
				int threshold = _bayerThresholdMatrix[tx % 8 + (ty % 8) * 8];
				int lum = mono <= threshold ? 0 : 255;
				image[i] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
			}
		}
	}
}
