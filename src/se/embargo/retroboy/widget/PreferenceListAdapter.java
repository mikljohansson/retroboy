package se.embargo.retroboy.widget;

import java.util.ArrayList;
import java.util.List;

import se.embargo.core.widget.ListPreferenceDialog;
import se.embargo.retroboy.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class PreferenceListAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {
	private final Context _context;
	private final List<PreferenceItem> _items = new ArrayList<PreferenceItem>();
	
	public PreferenceListAdapter(Context context) {
		_context = context;
	}
	
	public void add(PreferenceItem item) {
		_items.add(item);
	}
	
	@Override
	public int getCount() {
		return _items.size();
	}

	@Override
	public Object getItem(int position) {
		return _items.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
		    LayoutInflater inflater = (LayoutInflater)_context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		    convertView = inflater.inflate(R.layout.detailed_preference_item, parent, false);
		}
		
	    TextView titleView = (TextView)convertView.findViewById(R.id.prefItemTitle);
	    TextView valueView = (TextView)convertView.findViewById(R.id.prefItemValue);

	    PreferenceItem item = _items.get(position);
	    titleView.setText(item._title);
	    valueView.setText(item.getValueLabel());
	    valueView.setEnabled(item.isEnabled());
	    return convertView;
	}
	
	@Override
	public boolean isEnabled(int position) {
		PreferenceItem item = _items.get(position);
		return item.isEnabled();
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		PreferenceItem item = _items.get(position);
		if (item.isEnabled()) {
			item.onClick();
		}
	}

	public static abstract class PreferenceItem {
		public final int _title;
		
		public PreferenceItem(int title) {
			this._title = title;
		}

		public abstract String getValueLabel();
		public abstract void onClick();
		
		public boolean isEnabled() {
			return true;
		}
	}
	
	public static class ArrayPreferenceItem extends PreferenceItem {
		private final Context _context;
		private final SharedPreferences _prefs;
		private final String _key;
		private final int _defvalue, _labels, _values;
		private PreferencePredicate _enabled;		
		
		public ArrayPreferenceItem(
				Context context, SharedPreferences prefs, 
				String key, int defvalue, int title, int labels, int values) {
			super(title);
			_context = context;
			_prefs = prefs;
			_defvalue = defvalue;
			_key = key;
			_labels = labels;
			_values = values;
		}
		
		public ArrayPreferenceItem(
			Context context, SharedPreferences prefs, 
			String key, int defvalue, int title, int labels, int values,
			PreferencePredicate enabled) {
			this(context, prefs, key, defvalue, title, labels, values);
			_enabled = enabled;
		}
		
		@Override
		public final boolean isEnabled() {
			if (_enabled != null) {
				return _enabled.isEnabled();
			}
			
			return super.isEnabled();
		}

		@Override
		public String getValueLabel() {
			String[] labels = _context.getResources().getStringArray(_labels);
			String[] values = _context.getResources().getStringArray(_values);
			String defvalue = _context.getResources().getString(_defvalue);
			String value = _prefs.getString(getPreferenceKey(), defvalue);

			for (int i = 0; i < labels.length && i < values.length; i++) {
				if (value.equals(values[i])) {
					return labels[i];
				}
			}
			
			return "";
		}
		
		@Override
		public void onClick() {
			String defvalue = _context.getResources().getString(_defvalue);
			ListPreferenceDialog dialog = new ListPreferenceDialog(
				_context, _prefs, getPreferenceKey(), defvalue,
				_title, _labels, _values);
			dialog.show();
		}
		
		protected String getPreferenceKey() {
			return _key;
		}
	}
	
	public static class PreferencePredicate {
		private final SharedPreferences _prefs;
		private final String _key, _defvalue;
		private final String[] _values;
		
		public PreferencePredicate(SharedPreferences prefs, String key, String defvalue, String[] values) {
			_prefs = prefs;
			_key = key;
			_defvalue = defvalue;
			_values = values;
		}
		
		public boolean isEnabled() {
			String value = _prefs.getString(_key, _defvalue);
			for (String item : _values) {
				if (value.equals(item)) {
					return true;
				}
			}
			
			return false;
		}
	}
}
