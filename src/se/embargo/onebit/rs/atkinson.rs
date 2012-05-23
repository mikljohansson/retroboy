/*
 * Dithering filter using the Atkinson method
 */

#pragma version(1)
#pragma rs java_package_name(se.embargo.onebit.filter)

typedef struct atkinson_data {
	uint32_t width, height;
	rs_allocation *input;
} atkinson_data_t;

rs_allocation gIn;
rs_allocation gOut;
rs_script gMonoScript;

static inline void atkinson_propagate_error(const atkinson_data_t *data, float err, int32_t x, int32_t y) {
	if (x >= 0 && x < data->width && y >= 0 && y < data->height) {
		uchar4 *v_in = (uchar4 *)rsGetElementAt(*data->input, x, y);
		float4 pixel = rsUnpackColor8888(*v_in);
		float mono = pixel[0] + err;
		*v_in = rsPackColorTo8888((float3)mono);
	}
}

void filter() {
	int64_t t1 = rsUptimeMillis();

	// Convert image to monochrome
	rsForEach(gMonoScript, gIn, gOut, 0);

	int64_t t2 = rsUptimeMillis();
	rsDebug("Monochome filter in (ms)", t2 - t1);

	// Apply the Atkinson filter
	atkinson_data_t data = {0};
	data.width = rsAllocationGetDimX(gIn);
	data.height = rsAllocationGetDimY(gIn);
	data.input = &gOut;

	for (int y = 0; y < data.height; y++) {
		for (int x = 0; x < data.width; x++) {
			uchar4 *v_in = (uchar4 *)rsGetElementAt(gOut, x, y);
			float4 mono = rsUnpackColor8888(*v_in);
			
			// Apply the threshold
			float lum = step(0.5f, mono[0]);
			float err = (mono[0] - lum) / 8;
			*v_in = rsPackColorTo8888((float3)lum);

			// Propagate the error
			atkinson_propagate_error(&data, err, (int32_t)x + 1, (int32_t)y); 
			atkinson_propagate_error(&data, err, (int32_t)x + 2, (int32_t)y);
			atkinson_propagate_error(&data, err, (int32_t)x - 1, (int32_t)y + 1);
			atkinson_propagate_error(&data, err, (int32_t)x,     (int32_t)y + 1);
			atkinson_propagate_error(&data, err, (int32_t)x + 1, (int32_t)y + 1);
			atkinson_propagate_error(&data, err, (int32_t)x,     (int32_t)y + 2);
		}
	}

	int64_t t3 = rsUptimeMillis();
	rsDebug("Atkinson filter in (ms)", t3 - t2);
	rsDebug("Total Atkinson filter in (ms)", t3 - t1);
}
