package se.embargo.retroboy.graphic;

import java.util.Arrays;

/*
 * NeuQuant Neural-Net Quantization Algorithm
 * ------------------------------------------
 * 
 * Copyright (c) 1994 Anthony Dekker
 * 
 * NEUQUANT Neural-Net quantization algorithm by Anthony Dekker, 1994. See
 * "Kohonen neural networks for optimal colour quantization" in "Network:
 * Computation in Neural Systems" Vol. 5 (1994) pp 351-367. for a discussion of
 * the algorithm.
 * 
 * Any party obtaining a copy of these files from the author, directly or
 * indirectly, is granted, free of charge, a full and unrestricted irrevocable,
 * world-wide, paid up, royalty-free, nonexclusive right and license to deal in
 * this software and documentation files (the "Software"), including without
 * limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons who
 * receive copies from any such party to do so, with the only requirement being
 * that this copyright notice remain intact.
 * 
 * Ported to Java 12/00 K Weiner
 */
class NeuQuant {
	/**
	 * Number of colors used 
	 */
	//private static final int NETWORK_SIZE = 256;
	private static final int NETWORK_SIZE = 16;
	private static final int NETWORK_ENTRY_SIZE = 4;
	private static final int NETWORK_TABLE_SIZE = NETWORK_SIZE * NETWORK_ENTRY_SIZE;

	/** 
	 * Four primes near 500 - assume no image has a length so large that it is divisible by all four primes 
	 */
	private static final int prime1 = 499;
	private static final int prime2 = 491;
	private static final int prime3 = 487;
	private static final int prime4 = 503;

	/** 
	 * Minimum size for input image 
	 */
	private static final int minpicturebytes = (3 * prime4);
	private static final int maxnetpos = (NETWORK_SIZE - 1);
	
	/**
	 * Bias for color values 
	 */
	private static final int netbiasshift = 4;
	
	/** 
	 * Number of learning cycles 
	 */
	private static final int ncycles = 100;

	 /** 
	  * Bias for fractions 
	  */
	private static final int intbiasshift = 16;
	private static final int intbias = (((int)1) << intbiasshift);

	/** 
	 * Gamma = 1024 
	 */
	private static final int gammashift = 10;
	//private static final int gamma = (((int)1) << gammashift);

	/**
	 * Beta = 1/1024 
	 */
	private static final int betashift = 10;
	private static final int beta = (intbias >> betashift);
	private static final int betagamma = (intbias << (gammashift - betashift));

	/**
	 * For 256 cols, radius starts at 32.0 biased by 6 bits
	 */
	private static final int initrad = (NETWORK_SIZE >> 3); 
	private static final int radiusbiasshift = 6;
	private static final int radiusbias = (((int)1) << radiusbiasshift);

	/**
	 * and decreases by a factor of 1/30 each cycle
	 */
	private static final int initradius = (initrad * radiusbias);
	private static final int radiusdec = 30;

	/**
	 * Alpha starts at 1.0
	 */
	private static final int alphabiasshift = 10;
	private static final int initalpha = (((int)1) << alphabiasshift);

	/**
	 * Biased by 10 bits
	 */
	private int alphadec;

	/**
	 * adbias and alpharadbias used for radpower calculation 
	 */
	private static final int radbiasshift = 8;
	private static final int radbias = (((int)1) << radbiasshift);
	private static final int alpharadbshift = (alphabiasshift + radbiasshift);
	private static final int alpharadbias = (((int)1) << alpharadbshift);

	/**
	 * The network of neurons
	 */
	private final int[] network = new int[NETWORK_TABLE_SIZE];

	/**
	 * The neuron index for each color
	 */
	private final int[] netindex = new int[256];

	/** 
	 * For network lookup - really 256 
	 */
	private final int[] bias = new int[NETWORK_SIZE];

	/**
	 * Bias and freq arrays for learning 
	 */
	private final int[] freq = new int[NETWORK_SIZE];
	
	/**
	 * Radpower for precomputation
	 */
	private final int[] radpower = new int[initrad];

	/**
	 * Initialize network in range (0,0,0) to (255,255,255) and set parameters
	 */
	public void reset() {
		for (int i = 0, j = 0; i < NETWORK_SIZE; i++, j += NETWORK_ENTRY_SIZE) {
			network[j] = network[j + 1] = network[j + 2] = (i << (netbiasshift + 8)) / NETWORK_SIZE;
			freq[i] = intbias / NETWORK_SIZE;
		}
		
		Arrays.fill(netindex, 0);
		Arrays.fill(bias, 0);
		Arrays.fill(radpower, 0);
	}

	/**
	 * @return	RGB color palette
	 */
	private byte[] colorMap() {
		byte[] map = new byte[NETWORK_SIZE * 3];
		int[] index = new int[NETWORK_SIZE];
		for (int j = 0; j < NETWORK_TABLE_SIZE; j += NETWORK_ENTRY_SIZE) {
			index[network[j + 3]] = j;
		}
		
		for (int i = 0, k = 0; i < NETWORK_SIZE; i++) {
			int j = index[i];
			map[k++] = (byte)(network[j + 2]);
			map[k++] = (byte)(network[j + 1]);
			map[k++] = (byte)(network[j]);
		}
		
		return map;
	}

	/**
	 * Insertion sort of network and building of netindex[0..255] (to do after unbias)
	 */
	private void inxbuild() {
		int smallpos, smallval;
		int previouscol, startpos;

		previouscol = 0;
		startpos = 0;
		for (int i = 0, j = 0; i < NETWORK_SIZE; i++, j += NETWORK_ENTRY_SIZE) {
			smallpos = j;
			smallval = network[j + 1];
			
			// Find smallest in i..netsize-1
			for (int k = j + NETWORK_ENTRY_SIZE; k < NETWORK_TABLE_SIZE; k += NETWORK_ENTRY_SIZE) {
				if (network[k + 1] < smallval) {
					smallpos = k;
					smallval = network[k + 1];
				}
			}
			
			// Swap i and smallpos entries
			if (j != smallpos) {
				int k = network[smallpos];
				network[smallpos] = network[j];
				network[j] = k;
				
				k = network[smallpos + 1];
				network[smallpos + 1] = network[j + 1];
				network[j + 1] = k;

				k = network[smallpos + 2];
				network[smallpos + 2] = network[j + 2];
				network[j + 2] = k;

				k = network[smallpos + 3];
				network[smallpos + 3] = network[j + 3];
				network[j + 3] = k;
			}
			
			// smallval entry is now in position i
			if (smallval != previouscol) {
				netindex[previouscol] = (startpos + i) >> 1;
				for (int k = previouscol + 1; k < smallval; k++) {
					netindex[k] = i;
				}
				
				previouscol = smallval;
				startpos = i;
			}
		}
		
		netindex[previouscol] = (startpos + maxnetpos) >> 1;
		for (int k = previouscol + 1; k < 256; k++) {
			// really 256
			netindex[k] = maxnetpos;
		}
	}

	/**
	 * Main learning loop
	 */
	private void learn(byte[] thepicture, int lengthcount, int samplefac) {
		int i, j, b, g, r;
		int radius, rad, alpha, step, delta, samplepixels;
		byte[] p;
		int pix, lim;

		if (lengthcount < minpicturebytes)
			samplefac = 1;
		alphadec = 30 + ((samplefac - 1) / 3);
		p = thepicture;
		pix = 0;
		lim = lengthcount;
		samplepixels = lengthcount / (3 * samplefac);
		delta = samplepixels / ncycles;
		alpha = initalpha;
		radius = initradius;

		rad = radius >> radiusbiasshift;
		if (rad <= 1)
			rad = 0;
		for (i = 0; i < rad; i++)
			radpower[i] = alpha
				* (((rad * rad - i * i) * radbias) / (rad * rad));

		if (lengthcount < minpicturebytes)
			step = 3;
		else if ((lengthcount % prime1) != 0)
			step = 3 * prime1;
		else {
			if ((lengthcount % prime2) != 0)
				step = 3 * prime2;
			else {
				if ((lengthcount % prime3) != 0)
					step = 3 * prime3;
				else
					step = 3 * prime4;
			}
		}

		i = 0;
		while (i < samplepixels) {
			b = (p[pix + 0] & 0xff) << netbiasshift;
			g = (p[pix + 1] & 0xff) << netbiasshift;
			r = (p[pix + 2] & 0xff) << netbiasshift;
			j = contest(b, g, r);

			altersingle(alpha, j, b, g, r);
			if (rad != 0)
				alterneigh(rad, j, b, g, r); /* alter neighbours */

			pix += step;
			if (pix >= lim)
				pix -= lengthcount;

			i++;
			if (delta == 0)
				delta = 1;
			if (i % delta == 0) {
				alpha -= alpha / alphadec;
				radius -= radius / radiusdec;
				rad = radius >> radiusbiasshift;
				if (rad <= 1)
					rad = 0;
				for (j = 0; j < rad; j++)
					radpower[j] = alpha
						* (((rad * rad - j * j) * radbias) / (rad * rad));
			}
		}
	}

	/**
	 * Search for BGR values 0..255 (after net is unbiased) and return color index
	 */
	public int map(int b, int g, int r) {
		int i, j, dist, bestd;
		int best;

		bestd = 1000; /* biggest possible dist is 256*3 */
		best = -1;
		i = netindex[g] * NETWORK_ENTRY_SIZE; /* index on g */
		j = i - NETWORK_ENTRY_SIZE; /* start at netindex[g] and work outwards */

		while ((i < NETWORK_TABLE_SIZE) || (j >= 0)) {
			if (i < NETWORK_TABLE_SIZE) {
				dist = network[i + 1] - g;
				
				if (dist >= bestd) {
					// Stop iteration
					i = NETWORK_TABLE_SIZE;
				}
				else {
					dist = 
						Math.abs(dist) + 
						Math.abs(network[i] - b) + 
						Math.abs(network[i + 2] - r);
					
					if (dist < bestd) {
						bestd = dist;
						best = network[i + 3];
					}

					i += NETWORK_ENTRY_SIZE;
				}
			}
			
			if (j >= 0) {
				dist = g - network[j + 1];
				if (dist >= bestd) {
					// Stop iteration
					j = -1;
				}
				else {
					dist = 
						Math.abs(dist) + 
						Math.abs(network[j] - b) + 
						Math.abs(network[j + 2] - r);
					
					if (dist < bestd) {
						bestd = dist;
						best = network[j + 3];
					}
					
					j -= NETWORK_ENTRY_SIZE;
				}
			}
		}
		
		return best;
	}

	/**
	 * @return	RGB color palette
	 */
	public byte[] process(byte[] thepicture, int lengthcount, int samplefac) {
		learn(thepicture, lengthcount, samplefac);
		unbiasnet();
		inxbuild();
		return colorMap();
	}

	/**
	 * Unbias network to give byte values 0..255 and record position i to prepare for sort
	 */
	private void unbiasnet() {
		for (int i = 0, j = 0; i < NETWORK_SIZE; i++, j += NETWORK_ENTRY_SIZE) {
			network[j] >>= netbiasshift;
			network[j + 1] >>= netbiasshift;
			network[j + 2] >>= netbiasshift;
			
			// Record color number		
			network[j + 3] = i;
		}
	}

	/**
	 * Move adjacent neurons by precomputed alpha*(1-((i-j)^2/[r]^2)) in radpower[|i-j|]
	 */
	private void alterneigh(final int rad, final int i, final int b, final int g, final int r) {
		int j, k, lo, hi, a, m;
		lo = Math.max(i - rad, -1) * NETWORK_ENTRY_SIZE;
		hi = Math.min(i + rad, NETWORK_SIZE) * NETWORK_ENTRY_SIZE;

		j = (i + 1) * NETWORK_ENTRY_SIZE;
		k = (i - 1) * NETWORK_ENTRY_SIZE;
		m = 1;
		
		while ((j < hi) || (k > lo)) {
			a = radpower[m++];
			
			if (j < hi) {
				network[j] -= (a * (network[j] - b)) / alpharadbias;
				network[j + 1] -= (a * (network[j + 1] - g)) / alpharadbias;
				network[j + 2] -= (a * (network[j + 2] - r)) / alpharadbias;
				j += NETWORK_ENTRY_SIZE;
			}

			if (k > lo) {
				network[k] -= (a * (network[k] - b)) / alpharadbias;
				network[k + 1] -= (a * (network[k + 1] - g)) / alpharadbias;
				network[k + 2] -= (a * (network[k + 2] - r)) / alpharadbias;
				k -= NETWORK_ENTRY_SIZE;
			}
		}
	}

	/**
	 * Move neuron i towards biased (b,g,r) by factor alpha
	 */
	private final void altersingle(int alpha, int i, int b, int g, int r) {
		final int j = i * NETWORK_ENTRY_SIZE;
		network[j] -= (alpha * (network[j] - b)) / initalpha;
		network[j + 1] -= (alpha * (network[j + 1] - g)) / initalpha;
		network[j + 2] -= (alpha * (network[j + 2] - r)) / initalpha;
	}

	/**
	 * Search for biased BGR values
	 * 
	 * Finds closest neuron (min dist) and updates freq
	 * Finds best neuron (min dist-bias) and returns position
	 * For frequently chosen neurons, freq[i] is high and bias[i] is negative
	 */
	private int contest(int b, int g, int r) {
		int biasdist, betafreq;
		int bestpos = -1, bestbiaspos = bestpos, bestdist = Integer.MAX_VALUE, bestbiasdist = bestdist;

		for (int i = 0, j = 0; i < NETWORK_SIZE; i++, j += NETWORK_ENTRY_SIZE) {
			final int dist = 
				Math.abs(network[j] - b) + 
				Math.abs(network[j + 1] - g) + 
				Math.abs(network[j + 2] - r);
			
			if (dist < bestdist) {
				bestdist = dist;
				bestpos = i;
			}
			
			biasdist = dist - ((bias[i]) >> (intbiasshift - netbiasshift));
			if (biasdist < bestbiasdist) {
				bestbiasdist = biasdist;
				bestbiaspos = i;
			}
			
			betafreq = (freq[i] >> betashift);
			freq[i] -= betafreq;
			bias[i] += (betafreq << gammashift);
		}
		
		freq[bestpos] += beta;
		bias[bestpos] -= betagamma;
		return bestbiaspos;
	}
}
