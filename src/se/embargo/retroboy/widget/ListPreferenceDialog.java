package se.embargo.retroboy.widget;

import se.embargo.retroboy.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;

/**
 * Shows a dialog with a list of preferences.
 */
public class ListPreferenceDialog implements DialogInterface.OnClickListener {
	private Context _context;
	private SharedPreferences _prefs;
	private final String _key, _defvalue;
	private final int _title, _labels, _values;
	
	public ListPreferenceDialog(Context context, SharedPreferences prefs, String prefname, String prefdefault, int title, int labels, int values) {
		_context = context;
		_prefs = prefs;
		_key = prefname;
		_defvalue = prefdefault;
		_title = title;
		_labels = labels;
		_values = values;
	}
	
	public void show() {
		String contrast = _prefs.getString(_key, _defvalue);
		String[] values = _context.getResources().getStringArray(_values);
		int checkedItem = 0;
		
		for (int i = 0; i < values.length; i++) {
			if (contrast.equals(values[i])) {
				checkedItem = i;
				break;
			}
		}
		
		AlertDialog.Builder builder = new AlertDialog.Builder(_context);
		builder.setTitle(_title);
		builder.setSingleChoiceItems(_labels, checkedItem, this);
		builder.setNegativeButton(R.string.btn_cancel, this);
		builder.create().show();
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		String[] values = _context.getResources().getStringArray(_values);
		if (which >= 0 && which < values.length) {
			SharedPreferences.Editor editor = _prefs.edit();
			editor.putString(_key, values[which]);
			editor.commit();
		}
		
		dialog.dismiss();
	}
}
