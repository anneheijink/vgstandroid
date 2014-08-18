package nl.vgst.android;

import java.io.IOException;
import java.net.URI;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

public class GCMBroadcastReceiver extends BroadcastReceiver {
	
	private final static String TAG = "GCMBroadcastReceiver";
	
	public void onReceive(Context context, Intent intent) {
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
        String messageType = gcm.getMessageType(intent);
        
        if (messageType.equals(GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE)) {
            String action = intent.getStringExtra("action");
            if (action.equals("message"))
                showNotification(context, intent);
            else if(action.equals("play"))
                playMusic(context, intent);
        } else if (messageType.equals(GoogleCloudMessaging.MESSAGE_TYPE_DELETED))
    		Log.e(TAG, "Error " + intent.getExtras().toString());
        
        setResultCode(Activity.RESULT_OK);
    }

    private void playMusic(Context context, Intent intent) {
        MediaPlayer mp = MediaPlayer.create(context, Uri.parse(intent.getStringExtra("url")));
        mp.setVolume (1.0f, 1.0f);
        mp.start();
    }

	private void showNotification(Context context, Intent intent) {
		NotificationCompat.Builder mBuilder =
		        new NotificationCompat.Builder(context)
		        .setSmallIcon(R.drawable.ic_notification)
		        .setContentTitle(intent.getStringExtra("title"))
		        .setContentText(intent.getStringExtra("message"));
		
		NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the notification later on.
		mNotificationManager.notify(0, mBuilder.build());
		
		try {
			MediaPlayer ring = MediaPlayer.create(context, Settings.System.DEFAULT_ALARM_ALERT_URI);
			if (ring!=null) {
				ring.prepare();
				ring.start();
			}
		} catch (IllegalStateException e) {
			Log.e(TAG, "Can't play ringtone", e);
		} catch (IOException e) {
			Log.e(TAG, "Can't load ringtone", e);
		} 
	}

}
