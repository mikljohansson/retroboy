#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES sTexture;
varying vec2 vTextureCoord;

void main() {
	//gl_FragColor = vec4 (1, 0, 0, 1.0);
	gl_FragColor = texture2D(sTexture, vTextureCoord);
}
