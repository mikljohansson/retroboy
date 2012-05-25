package se.embargo.onebit.filter;

public class AtkinsonFilter implements IImageFilter {
	@Override
	public void accept(PreviewBuffer buffer) {
		byte[] data = buffer.data;
		int[] image = buffer.image;
		final int width = buffer.width;
		final int height = buffer.height;
		final int stride = buffer.stride;
		
		for (int y = 0; y < height; y += stride) {
			for (int x = 0; x < width; x += stride) {
				int i = x + y * width;
				
				// Convert from YUV luminance to monochrome
				int mono = (((int)data[i]) & 0xff) - 16;
				mono = Math.min(Math.max(0, mono), 255);
				
				// Apply the threshold
				int lum = mono < 128 ? 0 : 255;
				int err = (mono - lum) / 8;
				image[i] = 0xff000000 | (lum << 16) | (lum << 8) | lum;
				
				/*
				// Output the stride^2 pixel block
				for (int sy = 0; sy < stride; sy++) {
					for (int sx = 0; sx < stride; sx++) {
						image[i + sx + sy * width] = pix;
					}
				}
				*/
	
				// Propagate the error
				if (err != 0) {
					propagate(data, err, i + stride); 
					propagate(data, err, i + stride * 2);
					propagate(data, err, i - stride + width * stride);
					propagate(data, err, i + width);
					propagate(data, err, i + stride + width * stride);
					propagate(data, err, i + width * stride * 2);
				}
			}
		}
	}

	private static void propagate(byte[] data, int err, int i) {
		if (i >= 0 && i < data.length) {
			data[i] = (byte)Math.min(Math.max(0, ((int)data[i] & 0xff) + err), 255);
		}
	}
}
