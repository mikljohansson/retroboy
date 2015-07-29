package se.embargo.retroboy.filter;

import java.util.Arrays;

import se.embargo.core.graphic.color.IPalette;
import se.embargo.core.graphic.color.IColorQuantizer;
import se.embargo.core.graphic.color.NeuQuant;
import se.embargo.retroboy.color.BucketPalette;
import se.embargo.retroboy.color.DistancePalette;
import se.embargo.retroboy.color.Distances;
import se.embargo.retroboy.color.IPaletteSink;

/**
 * Samples frames and quantizes a continuously updated palette. 
 */
public class QuantizeFilter extends AbstractFilter {
	private final IPalette _palette;
	private final IPaletteSink _sink;
	private final IColorQuantizer _quantizer = new NeuQuant();
	
	/**
	 * @param	palette	The raw palette from which colors shall be selected.
	 * @param	sink	Recipient of the selected palette.
	 */
	public QuantizeFilter(IPalette palette, IPaletteSink sink) {
		_palette = palette;
		_sink = sink;
	}
	
	@Override
	public boolean isColorFilter() {
		return true;
	}
	
	@Override
	public synchronized void accept(ImageBuffer buffer) {
		_quantizer.sample(_palette, buffer.image.array(), buffer.imagewidth * buffer.imageheight, 10);
		
		int[] colors = _quantizer.getPalette();
		Arrays.sort(colors);
		
		_sink.accept(new BucketPalette(new DistancePalette(Distances.YUV, colors)));
		/*
		int[] image = buffer.image.array();
		for (int y = buffer.imageheight - 25; y < buffer.imageheight; y++) {
			for (int x = 0; x < buffer.imagewidth; x++) {
				int i = y * buffer.imagewidth + x;
				image[i] = colors[x / (buffer.imagewidth / colors.length)];
			}
		}
		*/
	}
}
