package se.embargo.retroboy.graphic;

public class GaussianBlur {
	/**
	 * Creates a Gaussian kernel.
	 * @link			http://www.swageroo.com/wordpress/how-to-program-a-gaussian-blur-without-using-3rd-party-libraries/
	 * @param	sigma	Blurriness factor, e.g. 1.5
	 * @param	width	Width of kernel in pixels
	 * @param	height	Height of kernel in pixels
	 * @return			A matrix of weights to apply to neighboring pixels, size is width * height
	 */
	public static float[] createKernel(double sigma, int width, int height) {
		if (width % 2 == 0 || height % 2 == 0) {
			throw new IllegalArgumentException("Width and height must be odd numbers");
		}
		
		float[] result = new float[width * height];
		int xoffset = width / 2, yoffset = height / 2;
		
		double base = 1 / (2 * Math.PI * sigma * sigma);
		double sum = 0;
		
		// Calculate all the weights
		for (int y = -yoffset, i = 0; y <= yoffset; y++) {
			for (int x = -xoffset; x <= xoffset; x++) {
				double weight = base * Math.exp(-((x * x + y * y) / (2 * sigma * sigma)));
				sum += weight;
				result[i++] = (float)weight;
			}
		}
		
		// Normalize all the weights so the sum is 1
		for (int i = 0; i < result.length; i++) {
			result[i] = (float)((double)result[i] * (1 / sum)); 
		}
		
		return result;
	}
}
