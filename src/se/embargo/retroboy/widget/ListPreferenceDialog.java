package se.embargo.retroboy.widget;

import se.embargo.core.databinding.PreferenceProperties;
import se.embargo.core.databinding.observable.IObservableValue;
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
	private final int _title;
	private final String[] _labels, _values;
	private final IObservableValue<String> _value;
	
	public ListPreferenceDialog(Context context, IObservableValue<String> value, int title, String[] labels, String[] values) {
		_context = context;
		_title = title;
		_labels = labels;
		_values = values;
		_value = value;
	}

	public ListPreferenceDialog(Context context, SharedPreferences prefs, String key, String defvalue, int title, String[] labels, String[] values) {
		this(context, PreferenceProperties.string(key, defvalue).observe(prefs), title, labels, values);
	}
	
	public ListPreferenceDialog(Context context, SharedPreferences prefs, String key, String defvalue, int title, int labels, int values) {
		this(context, prefs, key, defvalue, title, 
			context.getResources().getStringArray(labels), 
			context.getResources().getStringArray(values));
	}
	
	public void show() {
		String value = _value.getValue();
		int checkedItem = 0;
		
		for (int i = 0; value != null && i < _values.length; i++) {
			if (value.equals(_values[i])) {
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
			_value.setValue(_values[which]);
		}
		
		dialog.dismiss();
	}
}
