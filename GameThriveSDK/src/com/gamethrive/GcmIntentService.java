/**
 * Copyright 2014 GameThrive
 * Portions Copyright 2013 Google Inc.
 * 
 * This file includes portions from the Google GcmClient demo project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gamethrive;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 */
public class GcmIntentService extends IntentService {
    public static final int NOTIFICATION_ID = 1;
    private NotificationManager mNotificationManager;
    NotificationCompat.Builder builder;

    public GcmIntentService() {
        super("GcmIntentService");
    }
    
    public static final String TAG = "GameThrive";

    @Override
    protected void onHandleIntent(final Intent intent) {
    	Log.i(TAG, "onHandleIntent");
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        // The getMessageType() intent parameter must be the intent you received
        // in your BroadcastReceiver.
        String messageType = gcm.getMessageType(intent);
        
        if (!extras.isEmpty()) {  // has effect of unparcelling Bundle
            /*
             * Filter messages based on message type. Since it is likely that GCM will be
             * extended in the future with new message types, just ignore any message types you're
             * not interested in, or that you don't recognize.
             */
        	 if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
        		 if (GameThrive.instance != null && GameThrive.instance.isForeground()) {
        			 // This IntentService is meant to be short lived. Make a new thread to do our GameThrive work on.
        			 new Thread(new Runnable() {
        			       public void run() {
        			    	   Looper.prepare();
        			    	   GameThrive.instance.handleNotificationOpened(intent.getExtras());
        		            }
        		        }).start();
        		 }
        		 else {
	        		 // Post notification of received message.
	                 sendNotification(extras);
        		 }
             }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }
    
    // Put the message into a notification and post it.
    private void sendNotification(Bundle msg) {
    	Log.i(TAG, "sendNotification:" + msg);
        mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 1000,
                new Intent(this, NotificationOpenedActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("data", msg), PendingIntent.FLAG_UPDATE_CURRENT);

        int notificationDefaults = Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE;
        
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
        .setAutoCancel(true)
        .setSmallIcon(this.getApplicationInfo().icon)
        .setContentTitle(this.getString(this.getApplicationInfo().labelRes))
        .setStyle(new NotificationCompat.BigTextStyle().bigText(msg.getString("alert")))
        .setTicker(msg.getString("alert"))
        .setContentText(msg.getString("alert"));
        
        if (msg.getString("sound") != null) {
        	int soundId = getResources().getIdentifier(msg.getString("sound"), "raw", getPackageName());
        	if (soundId != 0)
        		mBuilder.setSound(Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + soundId));
        	else
            	notificationDefaults |= Notification.DEFAULT_SOUND;
        }
        else
        	notificationDefaults |= Notification.DEFAULT_SOUND;
        
        mBuilder.setDefaults(notificationDefaults);

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }
}