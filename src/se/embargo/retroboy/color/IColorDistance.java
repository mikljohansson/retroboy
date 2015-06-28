package se.embargo.retroboy.color;

/**
 * Returns the distance between two colors.
 */
public interface IColorDistance {
	/**
	 * @return	A number between 0 and 1 indicating the color distance.
	 */
	public double get(int r1,int g1,int b1, int r2,int g2,int b2);
}
