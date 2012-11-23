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
    	
    	public int get(int r1,int g1,int b1, int r2,int g2,int b2) {
			final int l1 = r1 * 299 + g1 * 587 + b1 * 114;
			final int l2 = r2 * 299 + g2 * 587 + b2 * 114;
			final int dl = (l1 - l2) / 1000;
			
			final int dr = r1 - r2,
					  dg = g1 - g2,
					  db = b1 - b2;
			
			return (dr * dr * 299 + dg * dg * 587 + db * db * 114) / 4000 * 3 + dl * dl;
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

    	public int get(int r1,int g1,int b1, int r2,int g2,int b2) {
			final int dr = r1 - r2,
			  	  dg = g1 - g2,
			  	  db = b1 - b2;
		
			return 3 * dr * dr + 4 * dg * dg + 2 * db * db;
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

    	public int get(int r1,int g1,int b1, int r2,int g2,int b2) {
			final float mr = (r1 + r2) / 2;
			final float dr = r1 - r2, 
						dg = g1 - g2,
						db = b1 - b2;
			
			return (int)((2f + mr / 256f) * dr * dr + 4f * dg * dg + (2f + (255f - mr) / 256f) * db * db);
    	}
    };
}
