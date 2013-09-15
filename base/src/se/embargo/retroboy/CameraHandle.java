package se.embargo.retroboy;

import android.hardware.Camera;

public class CameraHandle {
	public final Camera camera;
	public final Camera.CameraInfo info;
	public final int id;
	
	public CameraHandle(Camera camera, Camera.CameraInfo info, int id) {
		this.camera = camera;
		this.info = info;
		this.id = id;
	}
}