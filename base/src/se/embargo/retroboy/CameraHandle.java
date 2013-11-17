package se.embargo.retroboy;

import android.hardware.Camera;

public class CameraHandle {
	public final CameraProxy camera;
	public final Camera.CameraInfo info;
	public final int id;
	
	public CameraHandle(Camera camera, Camera.CameraInfo info, int id) {
		this.camera = new CameraProxy(camera);
		this.info = info;
		this.id = id;
	}
}