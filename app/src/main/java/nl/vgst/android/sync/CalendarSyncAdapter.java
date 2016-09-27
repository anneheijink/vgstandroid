/**
 * Copyright (C) 2014 Iwan Timmer
 *
 * This file is part of VGSTAndroid.
 *
 * VGSTAndroid is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * VGSTAndroid is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VGSTAndroid.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.vgst.android.sync;

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import nl.vgst.android.Api;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class CalendarSyncAdapter extends AbstractThreadedSyncAdapter {

	private static final String TAG = "CalendarSyncAdapter";

	public CalendarSyncAdapter(Context context) {
		super(context, true);
	}

	@Override
	public void onPerformSync(final Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
		try {
			Api api = new Api(account, getContext());
			ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();

			Cursor gcursor = provider.query(Calendars.CONTENT_URI, new String[] {Calendars._ID}, Calendars.ACCOUNT_TYPE + "=?", new String[] {account.type}, null);
			if (!gcursor.moveToNext()) {
				ContentValues values = new ContentValues();
				values.put(Calendars.ACCOUNT_NAME, account.name);
				values.put(Calendars.ACCOUNT_TYPE, account.type);
				values.put(Calendars.NAME, "VGST");
				values.put(Calendars.CALENDAR_DISPLAY_NAME, "VGST");
				values.put(Calendars.CALENDAR_COLOR, -4521848);
				values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ);
				values.put(Calendars.SYNC_EVENTS, true);
				Uri uri = provider.insert(asSyncAdapter(Calendars.CONTENT_URI, account.name, account.type), values);
				gcursor = provider.query(Calendars.CONTENT_URI, new String[] {Calendars._ID}, Calendars.ACCOUNT_TYPE + "=?", new String[] {account.type}, null);
				gcursor.moveToNext();
			}
			long calendarId = gcursor.getLong(0);

			JSONObject data = api.get("activities/api/getEvents");

			Uri rawContactUri = Events.CONTENT_URI.buildUpon().appendQueryParameter(Events.ACCOUNT_NAME, account.name).appendQueryParameter(Events.ACCOUNT_TYPE, account.type).build();
			Cursor c1 = provider.query(rawContactUri, new String[] { Events._ID, Events._SYNC_ID }, null, null, Events._SYNC_ID);

			Set<Long> removeIds = new HashSet<>();
			Map<Long, Long> syncIds = new HashMap<>();
			while (c1.moveToNext()) {
				removeIds.add(c1.getLong(0));
				syncIds.put(Long.parseLong(c1.getString(1)), c1.getLong(0));
			}

			JSONArray events = data.getJSONArray("data");
			for (int i=0;i<events.length();i++) {
				JSONObject event = events.getJSONObject(i);
				long id = event.getLong("id");

				if (syncIds.containsKey(id)) {
					Log.d(TAG, "Update event " + id);
					updateEvent(api, calendarId, account, provider, id, event);
					syncResult.stats.numUpdates++;
					removeIds.remove(syncIds.get(id));
				} else {
					Log.d(TAG, "Create event " + id);
					addEvent(api, calendarId, account, provider, event);
					syncResult.stats.numInserts++;
				}
			}

			for (long id:removeIds) {
				Log.d(TAG, "Delete event " + id);
				ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(Events.CONTENT_URI);
				builder.withSelection(Events._ID + "=?", new String[]{String.valueOf(id)});
				operationList.add(builder.build());
				syncResult.stats.numDeletes++;
			}

			provider.applyBatch(operationList);
		} catch (IOException e) {
			Log.e(TAG, "Kan data niet lezen", e);
			syncResult.stats.numIoExceptions++;
		} catch (RemoteException e) {
			Log.e(TAG, "Probleem met data", e);
			syncResult.databaseError = true;
		} catch (OperationApplicationException e) {
			Log.e(TAG, "Synchronisatie data incorrect", e);
			syncResult.databaseError = true;
		} catch (JSONException e) {
			Log.e(TAG, "Probleem met data", e);
			syncResult.stats.numParseExceptions++;
		} catch (AuthenticatorException e) {
			Log.e(TAG, "Probleem met authenticatie", e);
			syncResult.databaseError = true;
		} catch (OperationCanceledException e) {
			Log.e(TAG, "Probleem met authenticatie", e);
			syncResult.databaseError = true;
		}
	}

	private void updateEvent(Api api, long calendarId, Account account, ContentProviderClient provider, long id, JSONObject event) throws JSONException, RemoteException, OperationApplicationException {
		ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
		ContentProviderOperation.Builder raw = ContentProviderOperation.newUpdate(asSyncAdapter(Events.CONTENT_URI, account.name, account.type));
		raw.withSelection(Events._SYNC_ID + "=? AND " + Events.CALENDAR_ID + "=?", new String[]{String.format("%09d", id), String.format("%09d", calendarId)});
		raw.withValue(Events.DTSTART, String.format("%09d", event.getLong("start")*1000));
		raw.withValue(Events.DTEND, String.format("%09d", event.getLong("end")*1000));
		raw.withValue(Events.TITLE, event.getString("title"));

		operationList.add(raw.build());
		provider.applyBatch(operationList);
		operationList.clear();
	}

	private void addEvent(Api api, long calendarId, Account account, ContentProviderClient provider, JSONObject event) throws JSONException, RemoteException, OperationApplicationException {
		ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
		ContentProviderOperation.Builder raw = ContentProviderOperation.newInsert(asSyncAdapter(Events.CONTENT_URI, account.name, account.type));
		raw.withValue(Events.CALENDAR_ID, calendarId);
		raw.withValue(Events._SYNC_ID, String.format("%09d", event.getLong("id")));
		raw.withValue(Events.DTSTART, String.format("%09d", event.getLong("start")*1000));
		raw.withValue(Events.DTEND, String.format("%09d", event.getLong("end")*1000));
		raw.withValue(Events.TITLE, event.getString("title"));
		raw.withValue(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

		operationList.add(raw.build());
		provider.applyBatch(operationList);
		operationList.clear();
	}

	private static Uri asSyncAdapter(Uri uri, String account, String accountType) {
		return uri.buildUpon()
				.appendQueryParameter(android.provider.CalendarContract.CALLER_IS_SYNCADAPTER,"true")
				.appendQueryParameter(Calendars.ACCOUNT_NAME, account)
				.appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType).build();
	}
}