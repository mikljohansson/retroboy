package se.embargo.retroboy.color;

public class Distances {
	/**
	 * Returns the distance in phychovisual model.
	 * @link	http://bisqwit.iki.fi/story/howto/dither/jy/
	 */
    public static final IColorDistance LUMINANCE = new IColorDistance() {
    	@Override
    	public String toString() {
    		return "luminance";
    	}
    	
    	public double get(int r1,int g1,int b1, int r2,int g2,int b2) {
    		final double l1 = ((double)(r1 * 299 + g1 * 587 + b1 * 114)) / 255000d;
			final double l2 = ((double)(r2 * 299 + g2 * 587 + b2 * 114)) / 255000d;
			final double dl = l1 - l2;
			
			final double dr = r1 - r2,
					  	 dg = g1 - g2,
					  	 db = b1 - b2;
			
			return (dr * dr * 0.299d + dg * dg * 0.587d + db * db * 0.114d) * 0.75d + dl * dl;
    	}
    };
    
    /**
     * Returns the distance in the YUV space.
     * @link	http://www.compuphase.com/cmetric.htm
     */
    public static final IColorDistance YUV = new IColorDistance() {
    	@Override
    	public String toString() {
    		return "yuv";
    	}

    	public double get(int r1,int g1,int b1, int r2,int g2,int b2) {
			final double dr = r1 - r2,
	  			  	 	 dg = g1 - g2,
	  			  	 	 db = b1 - b2;
		
			return Math.sqrt(3d * dr * dr + 4d * dg * dg + 2d * db * db);
	    }
    };

    /**
     * Returns the distance in the LUV space.
     * @link	http://www.compuphase.com/cmetric.htm
     */
    public static final IColorDistance LUV = new IColorDistance() {
    	@Override
    	public String toString() {
    		return "luv";
    	}

    	public double get(int r1,int g1,int b1, int r2,int g2,int b2) {
			final double mr = ((double)(r1 + r2)) / 2;
			final double dr = r1 - r2,
	  	  			  	 dg = g1 - g2,
	  	  			  	 db = b1 - b2;
			
			return Math.sqrt((2d + mr / 256d) * dr * dr + 4d * dg * dg + (2d + (255d - mr) / 256d) * db * db);
    	}
    };
}
