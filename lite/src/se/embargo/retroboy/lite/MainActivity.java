package se.embargo.retroboy.lite;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;

public class MainActivity extends se.embargo.retroboy.MainActivity {
	private static final String AD_UNIT_ID = "ca-app-pub-7852293465552528/9756871897";
	private AdView _ad;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Create the adView
	    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
	    params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
	    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);

	    int padding = (int)(getResources().getDisplayMetrics().density * 8);
	    _ad = new AdView(this, AdSize.BANNER, AD_UNIT_ID);
	    _ad.setLayoutParams(params);
	    _ad.setPadding(padding, padding, padding, padding);
	    
	    RelativeLayout layout = (RelativeLayout)findViewById(R.id.cameraPreviewLayout);
	    layout.addView(_ad, 1);
	    layout.requestLayout();
	    layout.invalidate();
	    
	    // Initiate a request to load it with an ad
	    AdRequest request = new AdRequest();
	    request.addTestDevice(AdRequest.TEST_EMULATOR);
	    request.addTestDevice("A86642863B36B2DEC80461DCE79CF381");	// White GT-I9300
	    _ad.loadAd(request);
	}
	
	@Override
	public void onDestroy() {
		if (_ad != null) {
			_ad.destroy();
		}
		
		super.onDestroy();
	}
}
