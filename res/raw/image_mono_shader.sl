#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES sTexture;
varying vec2 vTextureCoord;

void main() {
	float mono = dot(texture2D(sTexture, vTextureCoord.xy), vec4(0.299, 0.587, 0.114, 0.0));
	gl_FragColor = vec4(mono, mono, mono, 1.0);
}
