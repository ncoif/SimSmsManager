package com.android.maemae.simsmsmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

public class ManageSimSmsActivity extends Activity implements View.OnCreateContextMenuListener {

	private static final Uri ICC_URI = Uri.parse("content://sms/icc");
	private static final Uri SMS_URI = Uri.parse("content://sms/inbox");
	private static final String TAG = "ManageSimMessages";

	private static final int MENU_COPY_TO_PHONE_MEMORY = 0;
	private static final int MENU_MOVE_TO_PHONE_MEMORY = 1;
	private static final int MENU_DELETE_FROM_SIM = 2;
	
	private static final int OPTION_MENU_COPY_ALL = 0;
	private static final int OPTION_MENU_MOVE_ALL = 1;
	private static final int OPTION_MENU_DELETE_ALL = 2;
	private static final int OPTION_MENU_ABOUT = 3;
	
	private static final int SHOW_LIST = 0;
	private static final int SHOW_EMPTY = 1;
	private static final int SHOW_BUSY = 2;
	private int mState;


	private ContentResolver mContentResolver;
	private Cursor mCursor = null;
	private ListView mSimList;
	private TextView mMessage;
	private MessageListAdapter mListAdapter = null;
	private AsyncQueryHandler mQueryHandler = null;

	private final ContentObserver simChangeObserver = new ContentObserver(new Handler()) {
		@Override
		public void onChange(boolean selfUpdate) {
			refreshMessageList();
		}
	};

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		mContentResolver = getContentResolver();
		mQueryHandler = new QueryHandler(mContentResolver, this);
		setContentView(R.layout.sim_list);
		mSimList = (ListView) findViewById(R.id.messages);
		mMessage = (TextView) findViewById(R.id.empty_message);

		init();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);

		init();
	}

	private void init() {
		updateState(SHOW_BUSY);
		startQuery();
	}

	private class QueryHandler extends AsyncQueryHandler {
		private final ManageSimSmsActivity mParent;

		public QueryHandler(ContentResolver contentResolver, ManageSimSmsActivity parent) {
			super(contentResolver);
			mParent = parent;
		}

		@Override
		protected void onQueryComplete( int token, Object cookie, Cursor cursor) {
			mCursor = cursor;
			if (mCursor != null) {

				if (!mCursor.moveToFirst()) {
					// Let user know the SIM is empty
					updateState(SHOW_EMPTY);
				} else if (mListAdapter == null) {
					mListAdapter = new MessageListAdapter(mParent, mCursor, false);
					mSimList.setAdapter(mListAdapter);
					mSimList.setOnCreateContextMenuListener(mParent);
					updateState(SHOW_LIST);
				} else {
					mListAdapter.changeCursor(mCursor);
					updateState(SHOW_LIST);
				}
				startManagingCursor(mCursor);
			} else {
				// Let user know the SIM is empty
				updateState(SHOW_EMPTY);
			}

		}
	}

	private void startQuery() {
		try {
			mQueryHandler.startQuery(0, null, ICC_URI, null, null, null, null);
		} catch (SQLiteException e) {
			//SqliteWrapper.checkSQLiteException(this, e);
			e.printStackTrace();
		}
	}

	private void refreshMessageList() {
		updateState(SHOW_BUSY);
		if (mCursor != null) {
			stopManagingCursor(mCursor);
			mCursor.close();
		}
		startQuery();
	}

	private void registerSimChangeObserver() {
		mContentResolver.registerContentObserver(ICC_URI, true, simChangeObserver);
	}
	
	@Override
	public void onCreateContextMenu( ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		menu.add(0, MENU_COPY_TO_PHONE_MEMORY, 0, R.string.sim_copy_to_phone_memory);
		menu.add(0, MENU_MOVE_TO_PHONE_MEMORY, 0, R.string.sim_move_to_phone_memory);
		menu.add(0, MENU_DELETE_FROM_SIM, 0, R.string.sim_delete);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		} catch (ClassCastException exception) {
			Log.e(TAG, "Bad menuInfo.", exception);
			return false;
		}

		final Cursor cursor = (Cursor) mListAdapter.getItem(info.position);

		switch (item.getItemId()) {
		case MENU_COPY_TO_PHONE_MEMORY:
			copyToPhoneMemory(cursor);
			return true;
			
		case MENU_MOVE_TO_PHONE_MEMORY:
			moveToPhoneMemory(cursor);
			return true;
			
		case MENU_DELETE_FROM_SIM:
			confirmDeleteDialog(new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					updateState(SHOW_BUSY);
					deleteFromSim(cursor);
					dialog.dismiss();
				}
			}, R.string.confirm_dialog_title_delete, R.string.confirm_delete_SIM_message);
			return true;

		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onResume() {
		super.onResume();
		registerSimChangeObserver();
	}

	@Override
	public void onPause() {
		super.onPause();
		mContentResolver.unregisterContentObserver(simChangeObserver);
	}

	@Override
	public void onBackPressed() {
		finish();
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		
		if (mState == SHOW_LIST && (null != mCursor) && (mCursor.getCount() > 0)) {
			menu.add(0, OPTION_MENU_COPY_ALL, 0, R.string.menu_copy_messages).setIcon(
					android.R.drawable.ic_menu_save);
			menu.add(0, OPTION_MENU_MOVE_ALL, 0, R.string.menu_move_messages).setIcon(
					android.R.drawable.ic_menu_save);			
			menu.add(0, OPTION_MENU_DELETE_ALL, 0, R.string.menu_delete_messages).setIcon(
					android.R.drawable.ic_menu_delete);
		}
		
		menu.add(0, OPTION_MENU_ABOUT, 0, R.string.menu_about).setIcon(
				android.R.drawable.ic_menu_help);
		
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		
		case OPTION_MENU_COPY_ALL:
			confirmDeleteDialog(new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					updateState(SHOW_BUSY);
					copyAllFromSim();
					dialog.dismiss();
				}
			}, R.string.confirm_dialog_title_copy, R.string.confirm_copy_all_SIM_messages);
			break;
		
		case OPTION_MENU_MOVE_ALL:
			confirmDeleteDialog(new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					updateState(SHOW_BUSY);
					moveAllFromSim();
					dialog.dismiss();
				}
			}, R.string.confirm_dialog_title_move, R.string.confirm_move_all_SIM_messages);
			break;
			
		case OPTION_MENU_DELETE_ALL:
			confirmDeleteDialog(new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					updateState(SHOW_BUSY);
					deleteAllFromSim();
					dialog.dismiss();
				}
			}, R.string.confirm_dialog_title_delete, R.string.confirm_delete_all_SIM_messages);
			break;
		
		case OPTION_MENU_ABOUT:
			Intent intentAbout = new Intent(ManageSimSmsActivity.this, AboutActivity.class);
			startActivity(intentAbout);
			break;
			
		}

		return true;
	}

	private void confirmDeleteDialog(OnClickListener listener, int titleId, int messageId) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(titleId);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setCancelable(true);
		builder.setPositiveButton(R.string.yes, listener);
		builder.setNegativeButton(R.string.no, null);
		builder.setMessage(messageId);

		builder.show();
	}

	private void updateState(int state) {
		if (mState == state) {
			return;
		}

		mState = state;
		switch (state) {
		case SHOW_LIST:
			mSimList.setVisibility(View.VISIBLE);
			mMessage.setVisibility(View.GONE);
			setTitle(getString(R.string.sim_manage_messages_title));
			setProgressBarIndeterminateVisibility(false);
			mSimList.requestFocus();
			break;
		case SHOW_EMPTY:
			mSimList.setVisibility(View.GONE);
			mMessage.setVisibility(View.VISIBLE);
			setTitle(getString(R.string.sim_manage_messages_title));
			setProgressBarIndeterminateVisibility(false);
			break;
		case SHOW_BUSY:
			mSimList.setVisibility(View.GONE);
			mMessage.setVisibility(View.GONE);
			setTitle(getString(R.string.refreshing));
			setProgressBarIndeterminateVisibility(true);
			break;
		default:
			Log.e(TAG, "Invalid State");
		}
	}

	/**
	 * Copy the selected SMS to the phone memory.
	 * 
	 * @param cursor the selected SMS
	 */
	private void copyToPhoneMemory(Cursor cursor) {
		
		ContentValues values = new ContentValues();
		values.put("address", cursor.getString(cursor.getColumnIndexOrThrow("address")));
		values.put("body", cursor.getString(cursor.getColumnIndexOrThrow("body")));
		values.put("date", cursor.getLong(cursor.getColumnIndexOrThrow("date")));
		
		try {
			if (isIncomingMessage(cursor)) {
				values.put("status", SmsManager.STATUS_ON_ICC_READ);
				values.put("read", "1");
				getContentResolver().insert(SMS_URI, values);
			} else {
				values.put("status", SmsManager.STATUS_ON_ICC_SENT);
				getContentResolver().insert(SMS_URI, values);
			}
		} catch (SQLiteException e) {
			//SqliteWrapper.checkSQLiteException(this, e);
			e.printStackTrace();
		}
	}
	
	private boolean isIncomingMessage(Cursor cursor) {
		int messageStatus = cursor.getInt(
				cursor.getColumnIndexOrThrow("status"));
		
		return !(messageStatus == SmsManager.STATUS_ON_ICC_SENT);
	}

	/**
	 * Move the selected SMS to the phone memory.
	 * 
	 * @param cursor the selected SMS
	 */
	private void moveToPhoneMemory(Cursor cursor) {
		copyToPhoneMemory(cursor);
		deleteFromSim(cursor);
	}
	
	/**
	 * Delete the selected SMS of the SIM card memory.
	 * 
	 * @param cursor the selected SMS
	 */
	private void deleteFromSim(Cursor cursor) {
		String messageIndexString =
				cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
		Uri simUri = ICC_URI.buildUpon().appendPath(messageIndexString).build();

		//SqliteWrapper.delete(this, mContentResolver, simUri, null, null);
		mContentResolver.delete(simUri, null, null);
	}
	
	/**
	 * Copy all the SMS to the phone memory.
	 */
	private void copyAllFromSim() {
		Cursor cursor = (Cursor) mListAdapter.getCursor();

		if (cursor != null) {
			if (cursor.moveToFirst()) {
				int count = cursor.getCount();

				for (int i = 0; i < count; ++i) {
					copyToPhoneMemory(cursor);
					cursor.moveToNext();
				}
			}
		}
	}
	
	/**
	 * Move all the SMS to the phone memory.
	 */
	private void moveAllFromSim() {
		Cursor cursor = (Cursor) mListAdapter.getCursor();

		if (cursor != null) {
			if (cursor.moveToFirst()) {
				int count = cursor.getCount();

				for (int i = 0; i < count; ++i) {
					moveToPhoneMemory(cursor);
					cursor.moveToNext();
				}
			}
		}
		
	}
	
	/**
	 * Delete all the SMS from the SIM card memory.
	 */
	private void deleteAllFromSim() {
		Cursor cursor = (Cursor) mListAdapter.getCursor();

		if (cursor != null) {
			if (cursor.moveToFirst()) {
				int count = cursor.getCount();

				for (int i = 0; i < count; ++i) {
					deleteFromSim(cursor);
					cursor.moveToNext();
				}
			}
		}
	}
	
}
