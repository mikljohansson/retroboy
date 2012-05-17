#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES sTexture;
//uniform sampler2D sTexture;
uniform sampler2D sThreshold;
uniform vec2 uTargetSize;
varying vec2 vTextureCoord;

void main() {
	vec2 textcoord = floor(vTextureCoord * uTargetSize) / uTargetSize;
	float mono = dot(texture2D(sTexture, textcoord), vec4(0.299, 0.587, 0.114, 0.0));
	
	vec2 threscoord = vTextureCoord * (uTargetSize / vec2(8.0, 8.0));
	float threshold = texture2D(sThreshold, threscoord).r * 4.0;
	
	float result = step(threshold, mono);
	//gl_FragColor = vec4(threshold, threshold, threshold, result);
	gl_FragColor = vec4(result, result, result, 1.0);
}
