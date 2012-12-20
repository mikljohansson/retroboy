package se.embargo.retroboy.graphic;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import se.embargo.core.concurrent.ForBody;
import se.embargo.core.concurrent.Parallel;
import se.embargo.retroboy.color.IIndexedPalette;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;

public class GifEncoder {
	private static final byte[] ZERO_COLOR_TABLE = new byte[3 * 256];
	
	private int width; // image size

	private int height;

	private int x = 0;

	private int y = 0;

	private int transparent = -1; // transparent color if given

	private int transIndex; // transparent index in color table

	private int repeat = -1; // no repeat

	private int delay = 0; // frame delay (hundredths)

	private boolean started = false; // ready to output frames

	private OutputStream out;

	private Bitmap image; // current frame

	private int[] frame; // BGR int array from frame
	private byte[] pixels; // BGR byte array from frame

	private byte[] indexedPixels; // converted frame indexed to palette

	private int colorDepth; // number of bit planes

	private byte[] colorTab; // RGB palette

	private boolean[] usedEntry = new boolean[256]; // active palette entries

	private int palSize = 7; // color table size (bits-1)

	private int dispose = -1; // disposal code (-1 = use default)

	private boolean closeStream = false; // close stream when finished

	private boolean firstFrame = true;

	private boolean sizeSet = false; // if false, get size from first frame

	private int sample = 10; // default sample interval for quantizer
	
	private final NeuQuant _quant = new NeuQuant();
	private final IIndexedPalette _palette;
	
	public GifEncoder(IIndexedPalette palette) {
		if (palette != null && palette.getColors().length <= 256) {
			_palette = palette;
			
			// Extract the RGB values as bytes
			int[] colors = palette.getColors();
			colorTab = new byte[colors.length * 3];
			
			for (int i = 0, j = 0; i < colors.length; i++, j += 3) {
				colorTab[j] = (byte)(colors[i] & 0xff);
				colorTab[j + 1] = (byte)((colors[i] >> 8) & 0xff);
				colorTab[j + 2] = (byte)((colors[i] >> 16) & 0xff);
			}
		}
		else {
			_palette = null;
		}
	}
	
	/**
	 * Sets the delay time between each frame, or changes it for subsequent
	 * frames (applies to last frame added).
	 * 
	 * @param ms
	 *            int delay time in milliseconds
	 */
	public void setDelay(int ms) {
		delay = ms / 10;
	}

	/**
	 * Sets the GIF frame disposal code for the last added frame and any
	 * subsequent frames. Default is 0 if no transparent color has been set,
	 * otherwise 2.
	 * 
	 * @param code
	 *            int disposal code.
	 */
	public void setDispose(int code) {
		if (code >= 0) {
			dispose = code;
		}
	}

	/**
	 * Sets the number of times the set of GIF frames should be played. Default
	 * is 1; 0 means play indefinitely. Must be invoked before the first image
	 * is added.
	 * 
	 * @param iter
	 *            int number of iterations.
	 * @return
	 */
	public void setRepeat(int iter) {
		if (iter >= 0) {
			repeat = iter;
		}
	}

	/**
	 * Sets the transparent color for the last added frame and any subsequent
	 * frames. Since all colors are subject to modification in the quantization
	 * process, the color in the final palette for each frame closest to the
	 * given color becomes the transparent color for that frame. May be set to
	 * null to indicate no transparent color.
	 * 
	 * @param c
	 *            Color to be treated as transparent on display.
	 */
	public void setTransparent(int c) {
		transparent = c;
	}

	/**
	 * Adds next GIF frame. The frame is not written immediately, but is
	 * actually deferred until the next frame is received so that timing data
	 * can be inserted. Invoking <code>finish()</code> flushes all frames. If
	 * <code>setSize</code> was not invoked, the size of the first image is used
	 * for all subsequent frames.
	 * 
	 * @param im
	 *            BufferedImage containing frame to write.
	 * @return true if successful.
	 */
	public boolean addFrame(Bitmap im) {
		if ((im == null) || !started) {
			return false;
		}
		
		try {
			if (!sizeSet) {
				// use first frame's size
				setSize(im.getWidth(), im.getHeight());
			}
			
			image = im;
			getImagePixels(); // convert to correct format if necessary
			analyzePixels(); // build color table & map pixels
			
			if (firstFrame) {
				writeLSD(); // logical screen descriptior
				writePalette(); // global color table
				if (repeat >= 0) {
					// use NS app extension to indicate reps
					writeNetscapeExt();
				}
			}
			
			writeGraphicCtrlExt(); // write graphic control extension
			writeImageDesc(); // image descriptor
			if (!firstFrame) {
				writePalette(); // local color table
			}

			// Encode and write pixel data
			LZWEncoder encoder = new LZWEncoder(indexedPixels, colorDepth);
			encoder.encode(out);

			firstFrame = false;
		}
		catch (IOException e) {
			return false;
		}

		return true;
	}

	/**
	 * Flushes any pending data and closes output file. If writing to an
	 * OutputStream, the stream is not closed.
	 */
	public boolean finish() {
		if (!started)
			return false;
		boolean ok = true;
		started = false;
		try {
			out.write(0x3b); // gif trailer
			out.flush();
			if (closeStream) {
				out.close();
			}
		}
		catch (IOException e) {
			ok = false;
		}

		// reset for subsequent use
		transIndex = 0;
		out = null;
		image = null;
		closeStream = false;
		firstFrame = true;

		return ok;
	}

	/**
	 * Sets frame rate in frames per second. Equivalent to
	 * <code>setDelay(1000/fps)</code>.
	 * 
	 * @param fps
	 *            float frame rate (frames per second)
	 */
	public void setFrameRate(float fps) {
		if (fps != 0f) {
			delay = (int)(100 / fps);
		}
	}

	/**
	 * Sets quality of color quantization (conversion of images to the maximum
	 * 256 colors allowed by the GIF specification). Lower values (minimum = 1)
	 * produce better colors, but slow processing significantly. 10 is the
	 * default, and produces good color mapping at reasonable speeds. Values
	 * greater than 20 do not yield significant improvements in speed.
	 * 
	 * @param quality
	 *            int greater than 0.
	 * @return
	 */
	public void setQuality(int quality) {
		if (quality < 1)
			quality = 1;
		sample = quality;
	}

	/**
	 * Sets the GIF frame size. The default size is the size of the first frame
	 * added if this method is not invoked.
	 * 
	 * @param w
	 *            int frame width.
	 * @param h
	 *            int frame width.
	 */
	public void setSize(int w, int h) {
		width = w;
		height = h;
		if (width < 1)
			width = 320;
		if (height < 1)
			height = 240;
		sizeSet = true;
	}

	/**
	 * Sets the GIF frame position. The position is 0,0 by default. Useful for
	 * only updating a section of the image
	 * 
	 * @param w
	 *            int frame width.
	 * @param h
	 *            int frame width.
	 */
	public void setPosition(int x, int y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * Initiates GIF file creation on the given stream. The stream is not closed
	 * automatically.
	 * 
	 * @param os
	 *            OutputStream on which GIF images are written.
	 * @return false if initial write failed.
	 */
	public boolean start(OutputStream os) {
		if (os == null)
			return false;
		boolean ok = true;
		closeStream = false;
		out = os;
		try {
			writeString("GIF89a"); // header
		}
		catch (IOException e) {
			ok = false;
		}
		return started = ok;
	}

	/**
	 * Uses the fixed palette to map each input color to a palette index.
	 */
	private class MapPaletteIndex implements ForBody<int[]> {
		@Override
		public void run(final int[] item, int it, final int last) {
			final IIndexedPalette palette = _palette;
			final boolean[] used = usedEntry;
			final byte[] output = indexedPixels;
			
			for (; it < last; it++) { 
				final int index = palette.getIndex(item[it]);
				used[index] = true;
				output[it] = (byte)index;
			}
		}
	}

	/**
	 * Uses the quantizer to map each input color to a palette index.
	 */
	private class MapQuantizedIndex implements ForBody<byte[]> {
		@Override
		public void run(final byte[] item, int it, final int last) {
			final NeuQuant quant = _quant;
			final boolean[] used = usedEntry;
			final byte[] output = indexedPixels;
			
			for (int i = it * 3; it < last; it++, i += 3) { 
				final int index = quant.map(item[i] & 0xff, item[i + 1] & 0xff, item[i + 2] & 0xff);
				used[index] = true;
				output[it] = (byte)index;
			}
		}
	}
	
	private MapPaletteIndex _mapPaletteIndex = new MapPaletteIndex();
	private MapQuantizedIndex _mapQuantizedIndex = new MapQuantizedIndex();
	
	/**
	 * Extracts bitmap pixels into int[] frame
	 */
	private void getImagePixels() {
		// Resize the frame if needed
		if (image.getWidth() != width || image.getHeight() != height) {
			Bitmap temp = Bitmap.createBitmap(width, height, Config.RGB_565);
			Canvas g = new Canvas(temp);
			g.drawBitmap(image, 0, 0, new Paint());
			image = temp;
		}
		
		// Extract the int pixels
		int size = image.getWidth() * image.getHeight();
		if (frame == null || frame.length != size) {
			frame = new int[size];
		}
		image.getPixels(frame, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
	}

	/**
	 * Analyzes image colors and creates color map.
	 */
	private void analyzePixels() {
		if (indexedPixels == null || indexedPixels.length != frame.length) {
			indexedPixels = new byte[frame.length];
		}
		Arrays.fill(usedEntry, false);
		
		if (_palette != null) {
			// Map image pixels to palette indexes
			Parallel.forRange(_mapPaletteIndex, frame, 0, frame.length);
		}
		else {
			// Transform into byte array
			if (pixels == null || pixels.length != frame.length * 3) {
				pixels = new byte[frame.length * 3];
			}
			
			for (int i = 0; i < frame.length; i++) {
				int td = frame[i];
				int tind = i * 3;
				pixels[tind++] = (byte)((td >> 0) & 0xFF);
				pixels[tind++] = (byte)((td >> 8) & 0xFF);
				pixels[tind] = (byte)((td >> 16) & 0xFF);
			}

			// Quantize image to create the reduced palette
			_quant.reset();
			colorTab = _quant.process(pixels, pixels.length, sample);

			// Map image pixels to palette indexes
			Parallel.forRange(_mapQuantizedIndex, pixels, 0, frame.length);
		}
		
		colorDepth = 8;
		palSize = 7;
		
		// Get closest match to transparent color if specified
		if (transparent != -1) {
			transIndex = findClosest(transparent);
		}
	}

	/**
	 * Returns index of palette color closest to c
	 */
	private int findClosest(int c) {
		if (colorTab == null)
			return -1;
		
		int r = (c >> 16) & 0xff;
		int g = (c >> 8) & 0xff;
		int b = (c >> 0) & 0xff;
		int minpos = 0;
		int dmin = 256 * 256 * 256;
		int len = colorTab.length;
		
		for (int i = 0; i < len;) {
			int dr = r - (colorTab[i++] & 0xff);
			int dg = g - (colorTab[i++] & 0xff);
			int db = b - (colorTab[i] & 0xff);
			int d = dr * dr + dg * dg + db * db;
			int index = i / 3;
			if (usedEntry[index] && (d < dmin)) {
				dmin = d;
				minpos = index;
			}
			i++;
		}
		
		return minpos;
	}

	/**
	 * Writes Graphic Control Extension
	 */
	private void writeGraphicCtrlExt() throws IOException {
		out.write(0x21); // extension introducer
		out.write(0xf9); // GCE label
		out.write(4); // data block size
		int transp, disp;
		if (transparent == -1) {
			transp = 0;
			disp = 0; // dispose = no action
		}
		else {
			transp = 1;
			disp = 2; // force clear if using transparent color
		}
		if (dispose >= 0) {
			disp = dispose & 7; // user override
		}
		disp <<= 2;

		// packed fields
		out.write(0 | // 1:3 reserved
			disp | // 4:6 disposal
			0 | // 7 user input - 0 = none
			transp); // 8 transparency flag

		writeShort(delay); // delay x 1/100 sec
		out.write(transIndex); // transparent color index
		out.write(0); // block terminator
	}

	/**
	 * Writes Image Descriptor
	 */
	private void writeImageDesc() throws IOException {
		out.write(0x2c); // image separator
		writeShort(x); // image position x,y = 0,0
		writeShort(y);
		writeShort(width); // image size
		writeShort(height);
		// packed fields
		if (firstFrame) {
			// no LCT - GCT is used for first (or only) frame
			out.write(0);
		}
		else {
			// specify normal LCT
			out.write(0x80 | // 1 local color table 1=yes
				0 | // 2 interlace - 0=no
				0 | // 3 sorted - 0=no
				0 | // 4-5 reserved
				palSize); // 6-8 size of color table
		}
	}

	/**
	 * Writes Logical Screen Descriptor
	 */
	private void writeLSD() throws IOException {
		// logical screen size
		writeShort(width);
		writeShort(height);
		// packed fields
		out.write((0x80 | // 1 : global color table flag = 1 (gct used)
		0x70 | // 2-4 : color resolution = 7
		0x00 | // 5 : gct sort flag = 0
		palSize)); // 6-8 : gct size

		out.write(0); // background color index
		out.write(0); // pixel aspect ratio - assume 1:1
	}

	/**
	 * Writes Netscape application extension to define repeat count.
	 */
	private void writeNetscapeExt() throws IOException {
		out.write(0x21); // extension introducer
		out.write(0xff); // app extension label
		out.write(11); // block size
		writeString("NETSCAPE" + "2.0"); // app id + auth code
		out.write(3); // sub-block size
		out.write(1); // loop sub-block id
		writeShort(repeat); // loop count (extra iterations, 0=repeat forever)
		out.write(0); // block terminator
	}

	/**
	 * Writes color table
	 */
	private void writePalette() throws IOException {
		out.write(colorTab, 0, colorTab.length);
		out.write(ZERO_COLOR_TABLE, 0, ZERO_COLOR_TABLE.length - colorTab.length);
	}

	/**
	 * Write 16-bit value to output stream, LSB first
	 */
	private void writeShort(int value) throws IOException {
		out.write(value & 0xff);
		out.write((value >> 8) & 0xff);
	}

	/**
	 * Writes string to output stream
	 */
	private void writeString(String s) throws IOException {
		for (int i = 0; i < s.length(); i++) {
			out.write((byte)s.charAt(i));
		}
	}
}
