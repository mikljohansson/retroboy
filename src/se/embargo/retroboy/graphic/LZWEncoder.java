package se.embargo.retroboy.graphic;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

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
	private static final int BITS = 12;
	private static final int HSIZE = 5003; // 80% occupancy

	private static final int masks[] = { 
		0x0000, 0x0001, 0x0003, 0x0007, 
		0x000F, 0x001F, 0x003F, 0x007F, 
		0x00FF, 0x01FF, 0x03FF, 0x07FF, 
		0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF,
		0xFFFF };

	private byte[] _pixels;
	private int _initcodesize;

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
	 * Number of used hash code entries.
	 */
	private int _codecount = 0;

	private int g_init_bits;
	private int _clearcode;
	private int _eofcode;

	private int cur_accum = 0;
	private int cur_bits = 0;

	/**
	 * Number of characters so far in this 'packet'
	 */
	private int _packetpos;

	/**
	 * Define the storage for the packet accumulator
	 */
	private byte[] _packet = new byte[256];

	public LZWEncoder(byte[] pixels, int color_depth) {
		_pixels = pixels;
		_initcodesize = Math.max(2, color_depth);
	}

	public void encode(OutputStream os) throws IOException {
		// Write "initial code size" byte
		os.write(_initcodesize);

		// Compress and write the pixel data
		compress(_initcodesize + 1, os);

		// Write block terminator
		os.write(0);
	}

	/**
	 * Clear out the code table for block compress
	 * @param	outs			Stream to write to
	 * @throws	IOException		If the write fails
	 */
	private void clearBlock(OutputStream outs) throws IOException {
		Arrays.fill(htab, 0, hsize, -1);
		_codecount = _clearcode + 2;
		output(_clearcode, outs);
	}

	private void compress(int init_bits, OutputStream outs) throws IOException {
		int fcode;
		int i /* = 0 */;
		int disp;
		int hsize_reg;
		int hshift;

		// Set up the globals: g_init_bits - initial number of bits
		g_init_bits = init_bits;

		// Set up the necessary values
		n_bits = g_init_bits;
		maxcode = (1 << n_bits) - 1;

		_clearcode = 1 << (init_bits - 1);
		_eofcode = _clearcode + 1;
		_codecount = _clearcode + 2;

		_packetpos = 0; // clear packet

		hshift = 0;
		for (fcode = hsize; fcode < 65536; fcode *= 2)
			++hshift;
		hshift = 8 - hshift; // set hash code range bound

		// Clear code table
		hsize_reg = hsize;
		Arrays.fill(htab, 0, hsize_reg, -1);
		output(_clearcode, outs);

		int bytepos = 0;
		int ent = _pixels[bytepos++] & 0xff, c;
		outer_loop: while (bytepos < _pixels.length) {
			c = _pixels[bytepos++] & 0xff;
			
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
			
			if (_codecount < maxmaxcode) {
				// code -> hashtable
				codetab[i] = _codecount++;
				htab[i] = fcode;
			}
			else {
				clearBlock(outs);
			}
		}
		
		// Output the final code.
		output(ent, outs);
		output(_eofcode, outs);
	}
	
	/**
	 * Flush the packet to disk, and reset the accumulator
	 * @param	outs		Where to write the trailer
	 * @throws 	IOException	If the write failes
	 */
	private void flushPacket(OutputStream outs) throws IOException {
		if (_packetpos > 0) {
			outs.write(_packetpos);
			outs.write(_packet, 0, _packetpos);
			_packetpos = 0;
		}
	}

	private void output(int code, OutputStream outs) throws IOException {
		cur_accum &= masks[cur_bits];

		if (cur_bits > 0)
			cur_accum |= (code << cur_bits);
		else
			cur_accum = code;

		cur_bits += n_bits;

		while (cur_bits >= 8) {
			_packet[_packetpos++] = (byte)(cur_accum & 0xff);
			if (_packetpos >= 254) {
				flushPacket(outs);
			}
			
			cur_accum >>= 8;
			cur_bits -= 8;
		}

		// If the next entry is going to be too big for the code size,
		// then increase it, if possible.
		if (code == _clearcode) {
			n_bits = g_init_bits;
			maxcode = (1 << n_bits) - 1;
		}
		else if (_codecount > maxcode) {
			++n_bits;
			if (n_bits == BITS) {
				maxcode = maxmaxcode;
			}
			else {
				maxcode = (1 << n_bits) - 1;
			}
		}

		if (code == _eofcode) {
			// At EOF, write the rest of the buffer.
			while (cur_bits > 0) {
				_packet[_packetpos++] = (byte)(cur_accum & 0xff);
				if (_packetpos >= 254) {
					flushPacket(outs);
				}

				cur_accum >>= 8;
				cur_bits -= 8;
			}

			flushPacket(outs);
		}
	}
}