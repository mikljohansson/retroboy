/*
 * Binarization filter using Otsu's method
 */

#pragma version(1)
#pragma rs java_package_name(se.embargo.onebit.filter)

rs_allocation gIn;
rs_allocation gOut;
rs_script gScript;

const static float3 gMonoMult = {0.299f, 0.587f, 0.114f};

void root(const uchar4 *v_in, uchar4 *v_out, const void *usrData, uint32_t x, uint32_t y) {
	float4 f4 = rsUnpackColor8888(*v_in);
	float3 mono = dot(f4.rgb, gMonoMult);
	float3 binary = step(*(const float3 *)usrData, mono);
	*v_out = rsPackColorTo8888(binary);
}

void filter() {
	int64_t t1 = rsUptimeMillis();
	
	int32_t width = rsAllocationGetDimX(gIn),
			height = rsAllocationGetDimY(gIn);
	int32_t pixels = width * height;

	rsDebug("Otsu image dimensions", width, height);

	// Build the histogram
	int32_t hist[256] = {0};
	for (int x = 0; x < width; x++) {
		for (int y = 0; y < height; y++) {
			const uchar4 *v_in = (const uchar4 *)rsGetElementAt(gIn, x, y);
			float4 f4 = rsUnpackColor8888(*v_in);
			float mono = dot(f4.rgb, gMonoMult);
			hist[(int)(mono * 255.0f) & 0xff]++;
		}
	}
	
	// Calculate the global threshold
	float sum = 0;
	for (int i = 0; i < 256; i++) {
		sum += (float)(hist[i] * i);
	}
	
	rsDebug("Otsu histogram sum", sum);
	
	float csum = 0;
	int wB = 0;
	int wF = 0;
	
	float fmax = -1.0;
	float threshold = 0;
	
	for (int i = 0; i < 255; i++) {
		// Weight background
		wB += hist[i];
		if (wB == 0) { 
			continue;
		}
	
		// Weight foreground
		wF = pixels - wB;
		if (wF == 0) {
			break;
		}
	
		csum += (float)(hist[i] * i);
	
		float mB = csum / wB;
		float mF = (sum - csum) / wF;
		float sb = (float)wB * (float)wF * (mB - mF) * (mB - mF);
		//float sb = (float)wB * (float)wF * (mF - mB);
	
		// Check if new maximum found
		if (sb > fmax) {
			fmax = sb;
			threshold = i + 1;
		}
	}
	
	int64_t t2 = rsUptimeMillis();
	rsDebug("Otsu threshold found in (ms)", t2 - t1);
	rsDebug("Otsu global threshold", threshold);
	
	// Binarize the image
	float3 usrData = (float)threshold / 255.0f;
	rsForEach(gScript, gIn, gOut, &usrData);

	int64_t t3 = rsUptimeMillis();
	rsDebug("Otsu binarization filter in (ms)", t3 - t2);
	rsDebug("Otsu filter in (ms)", t3 - t1);
}
