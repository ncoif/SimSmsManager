package com.android.maemae.simsmsmanager;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

public class MessageListAdapter extends CursorAdapter {
	
	public MessageListAdapter(Context context, Cursor c, boolean autoRequery) {
		super(context, c, autoRequery);
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		TextView address = (TextView)view.findViewById(R.id.message_item_address);
		address.setText(cursor.getString(cursor.getColumnIndex("address")));
		
		TextView date = (TextView)view.findViewById(R.id.message_item_date);
		long timestamp = cursor.getLong(cursor.getColumnIndex("date"));
		SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy HH:mm");
		date.setText( sdf.format(new Date(timestamp)) );
		
		TextView body = (TextView)view.findViewById(R.id.message_item_body);
		body.setText(cursor.getString(cursor.getColumnIndex("body")));		
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		LayoutInflater inflater = LayoutInflater.from(context);
		View v = inflater.inflate(R.layout.message_item, parent, false);
		bindView(v, context, cursor);
		return v;
	}
	
}
