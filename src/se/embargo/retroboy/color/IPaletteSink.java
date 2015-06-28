package se.embargo.retroboy.color;

/**
 * Interface that allows injecting a palette.
 */
public interface IPaletteSink {
	/**
	 * Inject a palette
	 * @param	palette	New palette to inject
	 */
	public void accept(IPalette palette);
}
