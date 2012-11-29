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
	private final int _title;
	private final String[] _labels, _values;
	
	public ListPreferenceDialog(Context context, SharedPreferences prefs, String prefname, String prefdefault, int title, String[] labels, String[] values) {
		_context = context;
		_prefs = prefs;
		_key = prefname;
		_defvalue = prefdefault;
		_title = title;
		_labels = labels;
		_values = values;
	}
	
	public ListPreferenceDialog(Context context, SharedPreferences prefs, String prefname, String prefdefault, int title, int labels, int values) {
		this(context, prefs, prefname, prefdefault, title, 
			context.getResources().getStringArray(labels), 
			context.getResources().getStringArray(values));
	}
	
	public void show() {
		String contrast = _prefs.getString(_key, _defvalue);
		int checkedItem = 0;
		
		for (int i = 0; i < _values.length; i++) {
			if (contrast.equals(_values[i])) {
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
		if (which >= 0 && which < _values.length) {
			SharedPreferences.Editor editor = _prefs.edit();
			editor.putString(_key, _values[which]);
			editor.commit();
		}
		
		dialog.dismiss();
	}
}
