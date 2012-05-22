#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES sTexture;
//uniform sampler2D sTexture;
uniform vec2 uTargetSize;
varying vec2 vTextureCoord;

vec2 atkinson_coordinate(float x, float y) {
	vec2 source = floor(vTextureCoord * uTargetSize);
	return vec2(source.x + x, source.y + y) / uTargetSize;
}

float atkinson_error(float x, float y, float bias) {
	vec2 coord = atkinson_coordinate(x, y);
	float mono = dot(texture2D(sTexture, coord), vec4(0.299, 0.587, 0.114, 0.0)) + bias;
	float result = step(0.5, mono);
	return mono - result;
}

float atkinson_contrib(float x, float y) {
	float incoming =
		atkinson_error(-1.0 + x, y, 0.0) +
		atkinson_error(-2.0 + x, y, 0.0) +
		atkinson_error(-1.0 + x, 1.0 + y, 0.0) +
		atkinson_error( x, 		 1.0 + y, 0.0) +
		atkinson_error( x, 		 2.0 + y, 0.0) +
		atkinson_error( 1.0 + x, 1.0 + y, 0.0);
	return atkinson_error(x, y, incoming * 0.2) * 0.2; 	
}

void main() {
	vec2 coord = atkinson_coordinate(0.0, 0.0);
	float mono = dot(texture2D(sTexture, coord), vec4(0.299, 0.587, 0.114, 0.0));

	mono += atkinson_contrib(-1.0, 0.0);
	mono += atkinson_contrib(-2.0, 0.0);
	mono += atkinson_contrib(-1.0, 1.0);
	mono += atkinson_contrib( 0.0, 1.0);
	mono += atkinson_contrib( 0.0, 2.0);
	mono += atkinson_contrib( 1.0, 1.0);

	float result = step(0.5, mono);
	gl_FragColor = vec4(result, result, result, 1.0);
}
