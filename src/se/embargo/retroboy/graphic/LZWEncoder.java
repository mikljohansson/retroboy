package se.embargo.retroboy.graphic;

import java.io.IOException;
import java.io.OutputStream;

/**
 * GIFCOMPR.C - GIF Image compression routines
 *
 * Algorithm: use open addressing double hashing (no chaining) on the
 * prefix code / next character combination. We do a variant of Knuth's
 * algorithm D (vol. 3, sec. 6.4) along with G. Knott's relatively-prime
 * secondary probe. Here, the modular division first probe is gives way
 * to a faster exclusive-or manipulation. Also do block compression with
 * an adaptive reset, whereby the code table is cleared when the compression
 * ratio decreases, but after the table fills. The variable-length output
 * codes are re-sized at this point, and a special CLEAR code is generated
 * for the decompressor. Late addition: construct the table according to
 * file size for noticeable speed improvement on small files. Please direct
 * questions about this implementation to ames!jaw.
 *
 * Lempel-Ziv compression based on 'compress'. GIF modifications by David Rowley (mgardi@watdcsu.waterloo.edu)
 * Adapted from Jef Poskanzer's Java port by way of J. M. G. Elliott. K Weiner 12/00
 */
class LZWEncoder {
	private static final int EOF = -1;
	private static final int BITS = 12;
	private static final int HSIZE = 5003; // 80% occupancy

	private int imgW, imgH;
	private byte[] pixAry;
	private int initCodeSize;
	private int remaining;
	private int curPixel;

	/**
	 * Number of bits per code
	 */
	private int n_bits;
	
	/**
	 * Maximum code, given n_bits
	 */
	private int maxcode;
	
	/**
	 * Should NEVER generate this code
	 */
	private int maxmaxcode = 1 << BITS;

	private int[] htab = new int[HSIZE];
	private int[] codetab = new int[HSIZE];

	/**
	 * For dynamic table sizing
	 */
	private int hsize = HSIZE;
	
	/**
	 * First unused entry
	 */
	private int free_ent = 0;

	/**
	 * After all codes are used up and compression rate changes, start over.
	 */
	private boolean clear_flg = false;

	private int g_init_bits;
	private int ClearCode;
	private int EOFCode;

	private int cur_accum = 0;
	private int cur_bits = 0;

	private int masks[] = { 
		0x0000, 0x0001, 0x0003, 0x0007, 
		0x000F, 0x001F, 0x003F, 0x007F, 
		0x00FF, 0x01FF, 0x03FF, 0x07FF, 
		0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF,
		0xFFFF };

	/**
	 * Number of characters so far in this 'packet'
	 */
	private int a_count;

	/**
	 * Define the storage for the packet accumulator
	 */
	private byte[] accum = new byte[256];

	public LZWEncoder(int width, int height, byte[] pixels, int color_depth) {
		imgW = width;
		imgH = height;
		pixAry = pixels;
		initCodeSize = Math.max(2, color_depth);
	}

	public void encode(OutputStream os) throws IOException {
		// Write "initial code size" byte
		os.write(initCodeSize);

		// Reset navigation variables
		remaining = imgW * imgH;
		curPixel = 0;

		// Compress and write the pixel data
		compress(initCodeSize + 1, os);

		// Write block terminator
		os.write(0);
	}
	
	/**
	 * Add a character to the end of the current packet, and if it is 254 characters, flush the packet to disk.
	 * @param	c				Character to write
	 * @param	outs			Stream to write to
	 * @throws	IOException		If the write fails
	 */
	private void char_out(byte c, OutputStream outs) throws IOException {
		accum[a_count++] = c;
		if (a_count >= 254) {
			flush_char(outs);
		}
	}

	/**
	 * Clear out the hash table for block compress
	 * @param	outs			Stream to write to
	 * @throws	IOException		If the write fails
	 */
	private void cl_block(OutputStream outs) throws IOException {
		cl_hash(hsize);
		free_ent = ClearCode + 2;
		clear_flg = true;

		output(ClearCode, outs);
	}

	/**
	 * Reset code table
	 * @param	hsize
	 */
	private void cl_hash(int hsize) {
		for (int i = 0; i < hsize; ++i) {
			htab[i] = -1;
		}
	}

	private void compress(int init_bits, OutputStream outs) throws IOException {
		int fcode;
		int i /* = 0 */;
		int c;
		int ent;
		int disp;
		int hsize_reg;
		int hshift;

		// Set up the globals: g_init_bits - initial number of bits
		g_init_bits = init_bits;

		// Set up the necessary values
		clear_flg = false;
		n_bits = g_init_bits;
		maxcode = (1 << n_bits) - 1;

		ClearCode = 1 << (init_bits - 1);
		EOFCode = ClearCode + 1;
		free_ent = ClearCode + 2;

		a_count = 0; // clear packet

		ent = nextPixel();

		hshift = 0;
		for (fcode = hsize; fcode < 65536; fcode *= 2)
			++hshift;
		hshift = 8 - hshift; // set hash code range bound

		hsize_reg = hsize;
		cl_hash(hsize_reg); // clear hash table

		output(ClearCode, outs);

		outer_loop: while ((c = nextPixel()) != EOF) {
			fcode = (c << BITS) + ent;
			i = (c << hshift) ^ ent; // xor hashing

			if (htab[i] == fcode) {
				ent = codetab[i];
				continue;
			}
			else if (htab[i] >= 0) // non-empty slot
			{
				disp = hsize_reg - i; // secondary hash (after G. Knott)
				if (i == 0)
					disp = 1;
				do {
					if ((i -= disp) < 0)
						i += hsize_reg;

					if (htab[i] == fcode) {
						ent = codetab[i];
						continue outer_loop;
					}
				} while (htab[i] >= 0);
			}
			
			output(ent, outs);
			ent = c;
			
			if (free_ent < maxmaxcode) {
				// code -> hashtable
				codetab[i] = free_ent++;
				htab[i] = fcode;
			}
			else
				cl_block(outs);
		}
		
		// Output the final code.
		output(ent, outs);
		output(EOFCode, outs);
	}

	/**
	 * Flush the packet to disk, and reset the accumulator
	 * @param	outs		Where to write the trailer
	 * @throws 	IOException	If the write failes
	 */
	private void flush_char(OutputStream outs) throws IOException {
		if (a_count > 0) {
			outs.write(a_count);
			outs.write(accum, 0, a_count);
			a_count = 0;
		}
	}

	/**
	 * @return	Return the next pixel from the image
	 */
	private int nextPixel() {
		if (remaining == 0)
			return EOF;

		--remaining;

		byte pix = pixAry[curPixel++];

		return pix & 0xff;
	}

	private void output(int code, OutputStream outs) throws IOException {
		cur_accum &= masks[cur_bits];

		if (cur_bits > 0)
			cur_accum |= (code << cur_bits);
		else
			cur_accum = code;

		cur_bits += n_bits;

		while (cur_bits >= 8) {
			char_out((byte)(cur_accum & 0xff), outs);
			cur_accum >>= 8;
			cur_bits -= 8;
		}

		// If the next entry is going to be too big for the code size,
		// then increase it, if possible.
		if (free_ent > maxcode || clear_flg) {
			if (clear_flg) {
				n_bits = g_init_bits;
				maxcode = (1 << n_bits) - 1;
				clear_flg = false;
			}
			else {
				++n_bits;
				if (n_bits == BITS) {
					maxcode = maxmaxcode;
				}
				else {
					maxcode = (1 << n_bits) - 1;
				}
			}
		}

		if (code == EOFCode) {
			// At EOF, write the rest of the buffer.
			while (cur_bits > 0) {
				char_out((byte)(cur_accum & 0xff), outs);
				cur_accum >>= 8;
				cur_bits -= 8;
			}

			flush_char(outs);
		}
	}
}