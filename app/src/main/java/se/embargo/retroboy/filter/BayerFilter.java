package se.embargo.retroboy.filter;

import se.embargo.core.concurrent.IForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.core.graphic.color.IPalette;
import se.embargo.retroboy.color.IPaletteSink;

public class BayerFilter extends AbstractFilter implements IPaletteSink {
	/**
	 * Type of palette to apply.
	 */
	public enum PaletteType { 
		/**
		 * A color palette is being used.
		 */
		Color, 
		
		/**
		 * A monochrome palette is used.
		 */
		Monochrome, 
		
		/**
		 * Monochrome palette with lighting compensation (e.g. Otsu threshold).
		 */
		Threshold
	}
	
	/**
	 * Current color palette.
	 */
	private volatile IPalette _palette;

	/**
	 * Dither matrix.
	 */
	private final int[] _matrix;
	
	/**
	 * Length of matrix side.
	 */
	private final int _patternsize;

	/**
	 * Ratio of color mixing.
	 */
	private final int _mixingratio;
	
	private final IForBody<ImageBuffer> _body;
	
	public BayerFilter(IPalette palette, int[] matrix, PaletteType type) {
		_palette = palette;
		_matrix = matrix;
		_patternsize = (int)Math.sqrt(_matrix.length);
		_mixingratio = _matrix.length / 2;
		
		switch (type) {
			case Color:
				_body = new ColorBody();
				break;
				
			case Threshold:
				_body = new ThresholdBody();
				break;
				
			default:
				_body = new MonochromeBody();
				break;
		}
	}
	
	@Override
	public void accept(IPalette palette) {
		_palette = palette;
	}
    
    @Override
    public boolean isColorFilter() {
    	return _body instanceof ColorBody;
    }

    @Override
    public IPalette getPalette() {
		return _palette;
	}
	
    @Override
	public void accept(ImageBuffer buffer) {
		Parallel.forRange(_body, buffer, 0, buffer.imageheight);
	}
    
    private class ColorBody implements IForBody<ImageBuffer> {
		@Override
		public void run(ImageBuffer buffer, int it, int last) {
	    	final IPalette palette = _palette;
			final int[] image = buffer.image.array();
			final int width = buffer.imagewidth;
			
			for (int y = it; y < last; y++) {
				final int yi = y * width,
						  yt = (y % _patternsize) * _patternsize;
				
				for (int x = 0; x < width; x++) {
					final int i = x + yi;
					final int pixel = image[i];
					final int threshold = _matrix[x % _patternsize + yt];

					final int r1 = Math.max(0, Math.min((pixel & 0x000000ff) + threshold - _mixingratio, 255));
					final int g1 = Math.max(0, Math.min(((pixel & 0x0000ff00) >> 8) + threshold - _mixingratio, 255));
					final int b1 = Math.max(0, Math.min(((pixel & 0x00ff0000) >> 16) + threshold - _mixingratio, 255));
					
					image[i] = (pixel & 0xff000000) | (palette.getNearestColor(r1, g1, b1) & 0xffffff);
				}
			}
		}
    }

    private class MonochromeBody implements IForBody<ImageBuffer> {
    	private final int[] _colors = new int[256];

    	public MonochromeBody() {
			for (int i = 0; i < 256; i++) {
				_colors[i] = (_palette.getNearestColor(i, i, i) & 0xffffff);
			}
		}

		@Override
		public void run(ImageBuffer buffer, int it, int last) {
	    	final int[] image = buffer.image.array();
			final int width = buffer.imagewidth;
			
			for (int y = it; y < last; y++) {
				final int yi = y * width,
						  yt = (y % _patternsize) * _patternsize;
				
				for (int x = 0; x < width; x++) {
					final int i = x + yi;
					final int pixel = image[i];
					final int threshold = _matrix[x % _patternsize + yt];

					final int lum = Math.max(0, Math.min((pixel & 0xff) + threshold - _mixingratio, 255));
					
					image[i] = (pixel & 0xff000000) | _colors[lum];
				}
			}
		}
    }

    private class ThresholdBody implements IForBody<ImageBuffer> {
    	private final int[] _colors = new int[256];
    	private final int _minoffset, _maxoffset;

    	public ThresholdBody() {
			for (int i = 0; i < 256; i++) {
				_colors[i] = (_palette.getNearestColor(i, i, i) & 0xffffff);
			}
			
			int lightingstep = 256 / _palette.getColorCount();
			_minoffset = Math.min(_mixingratio - lightingstep, 0);
			_maxoffset = Math.max(lightingstep - _mixingratio, 0);
		}

		@Override
		public void run(ImageBuffer buffer, int it, int last) {
	    	final int[] image = buffer.image.array();
			final int width = buffer.imagewidth;
			
			// Offset used to compensate for too dark or bright images
			final int offset = Math.max(_minoffset, Math.min(128 - buffer.threshold, _maxoffset));
			
			for (int y = it; y < last; y++) {
				final int yi = y * width,
						  yt = (y % _patternsize) * _patternsize;
				
				for (int x = 0; x < width; x++) {
					final int i = x + yi;
					final int pixel = image[i];
					final int threshold = _matrix[x % _patternsize + yt];

					final int lum = Math.max(0, Math.min((pixel & 0xff) + threshold - _mixingratio + offset, 255));
					
					image[i] = (pixel & 0xff000000) | _colors[lum];
				}
			}
		}
    }
}
