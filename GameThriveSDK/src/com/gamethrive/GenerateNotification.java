package com.gamethrive;

import java.net.URL;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.R.drawable;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class GenerateNotification {
	
    private static final String DEFAULT_ACTION = "__DEFAULT__";
    
    private static Context serviceContext = null;

	public static void fromBundle(Context inServiceContext, Bundle bundle) {
		if (!GameThrive.isValidAndNotDuplicated(inServiceContext, bundle))
			return;
		
		serviceContext = inServiceContext;
		PrepareBundle(bundle);
		
    	// If GameThrive has been initialized and the app is in focus skip the notification creation and handle everything like it was opened.
		if (GameThrive.instance != null && GameThrive.instance.isForeground()) {
        	final Bundle finalBundle = bundle;
			// This IntentService is meant to be short lived. Make a new thread to do our GameThrive work on.
			new Thread(new Runnable() {
				public void run() {
					GameThrive.instance.handleNotificationOpened(finalBundle);
				}
			}).start();
		 }
		 else // Create notification from the Bundle
             sendNotification(bundle);
	}
	
	 // Format our short keys into more readable ones.
    private static void PrepareBundle(Bundle gcmBundle) {
    	if (gcmBundle.containsKey("o")) {
			try {
		    	JSONObject customJSON = new JSONObject(gcmBundle.getString("custom"));
		    	JSONObject additionalDataJSON;
		    	
		    	if (customJSON.has("a"))
					additionalDataJSON = customJSON.getJSONObject("a");
		   		else
		   			additionalDataJSON = new JSONObject();
		    	
		    	JSONArray buttons = new JSONArray(gcmBundle.getString("o"));
		    	gcmBundle.remove("o");
		    	for(int i = 0; i < buttons.length(); i++) {
		    		JSONObject button = buttons.getJSONObject(i);
		    		
		    		String buttonText = button.getString("n");
		    		button.remove("n");
		    		String buttonId;
		    		if (button.has("i")){
		    			buttonId = button.getString("i");
		    			button.remove("i");
		    		}
		    		else
		    			buttonId = buttonText;
		    		
		    		button.put("id", buttonId);
		    		button.put("text", buttonText);
		    		
		    		if (button.has("p")) {
		    			button.put("icon", button.getString("p"));
		    			button.remove("p");
		    		}
		    	}
		    	
				additionalDataJSON.put("actionButtons", buttons);
				additionalDataJSON.put("actionSelected", DEFAULT_ACTION);
				if (!customJSON.has("a"))
					customJSON.put("a", additionalDataJSON);
		    	
		    	gcmBundle.putString("custom", customJSON.toString());
			} catch (JSONException e) {
				e.printStackTrace();
			}
    	}
    }
    
    private static Intent getNewBaseIntent() {
    	return new Intent(serviceContext, NotificationOpenedActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }
    
    // Put the message into a notification and post it.
    private static void sendNotification(Bundle gcmBundle) {
    	Random random = new Random();
    	
    	int intentId = random.nextInt();
    	int notificationId = random.nextInt();
    	
    	NotificationManager mNotificationManager = (NotificationManager) serviceContext.getSystemService(Context.NOTIFICATION_SERVICE);
        
        PendingIntent contentIntent = PendingIntent.getActivity(serviceContext, intentId, getNewBaseIntent().putExtra("data", gcmBundle), PendingIntent.FLAG_UPDATE_CURRENT);
        
        int notificationIcon = getSmallIconId(gcmBundle);
        
        CharSequence title = gcmBundle.getString("title");
        if (title == null)
        	title = serviceContext.getPackageManager().getApplicationLabel(serviceContext.getApplicationInfo());
        
        int notificationDefaults = Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE;
        
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(serviceContext)
	        .setAutoCancel(true)
	        .setSmallIcon(notificationIcon) // Small Icon required or notification doesn't display
	        .setContentTitle(title)
	        .setStyle(new NotificationCompat.BigTextStyle().bigText(gcmBundle.getString("alert")))
	        .setTicker(gcmBundle.getString("alert"))
	        .setContentText(gcmBundle.getString("alert"));
        
        Bitmap largeIcon = getBitmapIcon(gcmBundle, "licon");
        if (largeIcon != null)
        	mBuilder.setLargeIcon(largeIcon);
        
        Bitmap bigPictureIcon = getBitmapIcon(gcmBundle, "bicon");
        if (bigPictureIcon != null)
        	mBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bigPictureIcon).setSummaryText(gcmBundle.getString("alert")));
        
        if (isValidResourceName(gcmBundle.getString("sound"))) {
        	int soundId = serviceContext.getResources().getIdentifier(gcmBundle.getString("sound"), "raw", serviceContext.getPackageName());
        	if (soundId != 0)
        		mBuilder.setSound(Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + serviceContext.getPackageName() + "/" + soundId));
        	else
            	notificationDefaults |= Notification.DEFAULT_SOUND;
        }
        else
        	notificationDefaults |= Notification.DEFAULT_SOUND;
        
        mBuilder.setDefaults(notificationDefaults);
        mBuilder.setContentIntent(contentIntent);
		
		try {
	        JSONObject customJson = new JSONObject(gcmBundle.getString("custom"));
	        
	        if (customJson.has("a")) {
	        	JSONObject additionalDataJSON = customJson.getJSONObject("a");
	        	if (additionalDataJSON.has("actionButtons")) {
	        		
	            	JSONArray buttons = additionalDataJSON.getJSONArray("actionButtons");
    				
            		for(int i = 0; i < buttons.length(); i++) {
            			JSONObject button = buttons.getJSONObject(i);
            			additionalDataJSON.put("actionSelected", button.getString("id"));
            			
            			Bundle bundle = new Bundle();
            			bundle.putString("custom", customJson.toString());
            			bundle.putString("alert", gcmBundle.getString("alert"));
            			
            			Intent buttonIntent = getNewBaseIntent();
            			buttonIntent.setAction("" + i); // Required to keep each action button from replacing extras of each other
            			buttonIntent.putExtra("notificationId", notificationId);
            			buttonIntent.putExtra("data", bundle);
            			PendingIntent buttonPIntent = PendingIntent.getActivity(serviceContext, notificationId, buttonIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            			
            			int buttonIcon = 0;
            			if (button.has("icon"))
            				buttonIcon = getResourceIcon(button.getString("icon"));
            			
            			mBuilder.addAction(buttonIcon, button.getString("text"), buttonPIntent);
            		}
	        	}
	        }
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
        mNotificationManager.notify(notificationId, mBuilder.build());
    }
    
    private static boolean isValidResourceName(String name) {
    	if (name != null && !name.matches("^[0-9]"))
    		return true;
    	
    	return false;
    }
    
    private static Bitmap getBitmapIcon(Bundle gcmBundle, String key) {
    	if (gcmBundle.containsKey(key)) {
    		String bitmapStr = gcmBundle.getString(key);
    		if (bitmapStr.startsWith("http://") || bitmapStr.startsWith("https://")) {
				try {
					return BitmapFactory.decodeStream(new URL(bitmapStr).openConnection().getInputStream());
				} catch (Throwable t) {
					return null;
				}
    		}
			else if (isValidResourceName(bitmapStr)) {
				int bitmapId = serviceContext.getResources().getIdentifier(bitmapStr, "drawable", serviceContext.getPackageName());
				if (bitmapId == 0) {
					try {
						bitmapId = drawable.class.getField(bitmapStr).getInt(null);
					} catch (Throwable t) {}
				}
				
				if (bitmapId != 0)
					return BitmapFactory.decodeResource(serviceContext.getResources(), bitmapId);
			}
    	}
    	
    	return null;
    }
    
    private static int getResourceIcon(String iconName) {
    	if (!isValidResourceName(iconName))
    		return 0;
    	
    	int notificationIcon = serviceContext.getResources().getIdentifier(iconName, "drawable", serviceContext.getPackageName());
    	if (notificationIcon != 0)
    		return notificationIcon;
    	
		try {
			return drawable.class.getField(iconName).getInt(null);
		} catch (Throwable t) {}
		
		return 0;
    }
    
    private static int getSmallIconId(Bundle gcmBundle) {
        int notificationIcon = 0;
        
        if (gcmBundle.containsKey("sicon")) {
        	notificationIcon = getResourceIcon(gcmBundle.getString("sicon"));
        	if (notificationIcon != 0)
        		return notificationIcon;
        }
        
        notificationIcon = serviceContext.getResources().getIdentifier("gamethrive_statusbar_icon_default", "drawable", serviceContext.getPackageName());
        if (notificationIcon != 0)
    		return notificationIcon;
        
        notificationIcon = serviceContext.getResources().getIdentifier("corona_statusbar_icon_default", "drawable", serviceContext.getPackageName());
        if (notificationIcon != 0)
    		return notificationIcon;
        
	    notificationIcon = serviceContext.getApplicationInfo().icon;
        if (notificationIcon != 0)
    		return notificationIcon;
        
        return drawable.sym_def_app_icon; // Catches case where icon isn't set in the AndroidManifest.xml
    }
}