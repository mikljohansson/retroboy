package se.embargo.onebit.filter;

import android.graphics.SurfaceTexture;

public interface IPreviewShader {
	public void setPreviewSize(int width, int height);
	public SurfaceTexture getPreviewTexture();
	public void draw();
}
