package se.embargo.retroboy;

import android.os.Bundle;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;

public class SettingsActivity extends SherlockPreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		getPreferenceManager().setSharedPreferencesName(Pictures.PREFS_NAMESPACE);
		addPreferencesFromResource(R.xml.settings);

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
            case android.R.id.home: {
                // When app icon in action bar clicked, go up
            	startParentActivity();
                return true;
            }

			default:
				return super.onOptionsItemSelected(item);
		}
	}
            
    private void startParentActivity() {
        finish();
    }
}
