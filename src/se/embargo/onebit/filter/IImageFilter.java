package se.embargo.onebit.filter;

public interface IImageFilter {
	public class PreviewBuffer {
		public byte[] data;
		public int[] image;
		public final int width;
		public final int height;
		public final int stride;

		public PreviewBuffer(byte[] data, int width, int height, int stride) {
			this.data = data;
			this.image = new int[width * height];
			this.width = width;
			this.height = height;
			this.stride = stride;
		}
	}
	
	public void accept(PreviewBuffer buffer);
}
