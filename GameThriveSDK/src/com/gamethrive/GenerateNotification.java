package com.gamethrive;

import java.math.BigInteger;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

class GenerateNotification {
    private static Context serviceContext = null;
    private static String packageName = null;
    private static Resources contextResources = null;

	public static void fromBundle(Context inServiceContext, Bundle bundle) {
		serviceContext = inServiceContext;
		packageName = serviceContext.getPackageName();
		contextResources =  serviceContext.getResources();
		
        buildNotification(bundle);
	}
    
    private static Intent getNewBaseIntent() {
    	return new Intent(serviceContext, NotificationOpenedActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }
    
    // Put the message into a notification and post it.
    private static void buildNotification(Bundle gcmBundle) {
    	Random random = new Random();
    	
    	int intentId = random.nextInt();
    	int notificationId = random.nextInt();
    	
    	NotificationManager mNotificationManager = (NotificationManager) serviceContext.getSystemService(Context.NOTIFICATION_SERVICE);
        
        PendingIntent contentIntent = PendingIntent.getActivity(serviceContext, intentId, getNewBaseIntent().putExtra("data", gcmBundle), PendingIntent.FLAG_UPDATE_CURRENT);
        
        int notificationIcon = getSmallIconId(gcmBundle);
        
        CharSequence title = gcmBundle.getString("title");
        if (title == null)
        	title = serviceContext.getPackageManager().getApplicationLabel(serviceContext.getApplicationInfo());
        
        int notificationDefaults = 0;
        
        if (GameThrive.getVibrate(serviceContext))
        	notificationDefaults = Notification.DEFAULT_VIBRATE;
        
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(serviceContext)
	        .setAutoCancel(true)
	        .setSmallIcon(notificationIcon) // Small Icon required or notification doesn't display
	        .setContentTitle(title)
	        .setStyle(new NotificationCompat.BigTextStyle().bigText(gcmBundle.getString("alert")))
	        .setTicker(gcmBundle.getString("alert"))
	        .setContentText(gcmBundle.getString("alert"));
        
        // Android 5.0 accent color to use, only works when AndroidManifest.xml is targetSdkVersion >= 21
        if (gcmBundle.containsKey("bgac")) {
        	try {
        		mBuilder.setColor(new BigInteger(gcmBundle.getString("bgac"), 16).intValue());
        	} catch(Throwable t) { } // Can throw if an old android support lib is used or parse error.
        }
        
        if (gcmBundle.containsKey("ledc")) {
        	try {
        		mBuilder.setLights(new BigInteger(gcmBundle.getString("ledc"), 16).intValue(), 2000, 5000);
        	} catch(Throwable t) {
        		notificationDefaults |= Notification.DEFAULT_LIGHTS;
        	} // Can throw if an old android support lib is used or parse error.
        }
        else
        	notificationDefaults |= Notification.DEFAULT_LIGHTS;
        
        try {
            int visibility = Notification.VISIBILITY_PUBLIC;
            if (gcmBundle.containsKey("vis"))
            	visibility = Integer.parseInt(gcmBundle.getString("vis"));
        	mBuilder.setVisibility(visibility);
        } catch(Throwable t) {} // Can throw if an old android support lib is used or parse error
        
        Bitmap largeIcon = getLargeIcon(gcmBundle);
        if (largeIcon != null)
        	mBuilder.setLargeIcon(largeIcon);
        
        Bitmap bigPictureIcon = getBitmapIcon(gcmBundle, "bicon");
        if (bigPictureIcon != null)
        	mBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bigPictureIcon).setSummaryText(gcmBundle.getString("alert")));
        
        if (GameThrive.getSoundEnabled(serviceContext)) {
	        Uri soundUri = getCustomSound(gcmBundle);
	        if (soundUri != null)
	        	mBuilder.setSound(soundUri);
	        else
	        	notificationDefaults |= Notification.DEFAULT_SOUND;
        }
        
        
        mBuilder.setDefaults(notificationDefaults);
        mBuilder.setContentIntent(contentIntent);
		
        addActionButtons(gcmBundle, mBuilder, notificationId);
		
        mNotificationManager.notify(notificationId, mBuilder.build());
    }
    
    private static boolean isValidResourceName(String name) {
    	return (name != null && !name.matches("^[0-9]"));
    }
    
    private static Bitmap getLargeIcon(Bundle gcmBundle) {
    	if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB)
    		return null;
    	
    	Bitmap bitmap = getBitmapIcon(gcmBundle, "licon");
    	if (bitmap == null)
    		bitmap = getBitmapFromAssetsOrResourceName("ic_gamethrive_large_icon_default");
    	
    	if (bitmap == null)
    		return null;
    	
    	// Check to see if we need to shrink the bitmap to prevent cropping when displayed.
    	try {
    		int systemLargeIconHeight = (int)contextResources.getDimension(android.R.dimen.notification_large_icon_height);
    		int systemLargeIconWidth = (int)contextResources.getDimension(android.R.dimen.notification_large_icon_width);
    		int bitmapHeight = bitmap.getHeight();
    		int bitmapWidth = bitmap.getWidth();
    		
    		if (bitmapWidth > systemLargeIconWidth || bitmapHeight > systemLargeIconHeight) { 				    			
			    int newWidth = systemLargeIconWidth, newHeight = systemLargeIconHeight;
			    if (bitmapHeight > bitmapWidth) {
			    	float ratio = (float)bitmapWidth / (float)bitmapHeight;
			        newWidth = (int) (newHeight * ratio);
			    } else if (bitmapWidth > bitmapHeight) {
			    	float ratio = (float)bitmapHeight / (float)bitmapWidth;
			        newHeight = (int) (newWidth * ratio);
			    }
			    
			    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    		}
    	} catch (Throwable t) {}
    	
    	return bitmap;
    }
    
    private static Bitmap getBitmapFromAssetsOrResourceName(String bitmapStr) {
    	try {
    		Bitmap bitmap = null;
    		
    		try {
    			bitmap = BitmapFactory.decodeStream(serviceContext.getAssets().open(bitmapStr));
    		} catch(Throwable t) {}
    		
    		if (bitmap != null)
    			return bitmap;
    		
    		final List<String> image_extensions = Arrays.asList(".png", "webp", ".jpg", ".gif", "bmp");
    		for(String extension : image_extensions) {
    			try {
    				bitmap = BitmapFactory.decodeStream(serviceContext.getAssets().open(bitmapStr + extension));
    			} catch(Throwable t) {}
    			if (bitmap != null)
    				return bitmap;
    		}
    		
    		int bitmapId = getResourceIcon(bitmapStr);
    		if (bitmapId != 0)
    			return BitmapFactory.decodeResource(contextResources, bitmapId);
    	} catch(Throwable t) {}
    	
    	return null;
    }
    
    private static Bitmap getBitmapFromURL(String location) {
    	try {
    		return BitmapFactory.decodeStream(new URL(location).openConnection().getInputStream());
		} catch (Throwable t) {}
    	
		return null;
    }
    
    private static Bitmap getBitmapIcon(Bundle gcmBundle, String key) {
    	if (gcmBundle.containsKey(key)) {
    		String bitmapStr = gcmBundle.getString(key);
    		
    		if (bitmapStr.startsWith("http://") || bitmapStr.startsWith("https://"))
    			return getBitmapFromURL(bitmapStr);
    		
    		return getBitmapFromAssetsOrResourceName(bitmapStr);
    	}
    	
    	return null;
    }
    
    private static int getResourceIcon(String iconName) {
    	if (!isValidResourceName(iconName))
    		return 0;
    	
    	int notificationIcon = getDrawableId(iconName);
    	if (notificationIcon != 0)
    		return notificationIcon;
    	
    	// Get system icon resource
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
        
        notificationIcon = getDrawableId("ic_stat_gamethrive_default");
        if (notificationIcon != 0)
    		return notificationIcon;
        
        // Deprecated - Use ic_stat_gamethrive_default instead. gamethrive_statusbar_icon_default will be removed in the future
        notificationIcon = getDrawableId("gamethrive_statusbar_icon_default");
        if (notificationIcon != 0)
    		return notificationIcon;
        
        notificationIcon = getDrawableId("corona_statusbar_icon_default");
        if (notificationIcon != 0)
    		return notificationIcon;
        
        // Launcher icon
	    notificationIcon = serviceContext.getApplicationInfo().icon;
        if (notificationIcon != 0)
    		return notificationIcon;
        
        return drawable.sym_def_app_icon; // Catches case where icon isn't set in the AndroidManifest.xml
    }
    
    private static int getDrawableId(String name) {
    	return contextResources.getIdentifier(name, "drawable", packageName);
    }
    
    private static Uri getCustomSound(Bundle gcmBundle) {
    	int soundId;
    	
    	if (isValidResourceName(gcmBundle.getString("sound"))) {
        	soundId = contextResources.getIdentifier(gcmBundle.getString("sound"), "raw", packageName);
        	if (soundId != 0)
        		return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId);
        }
    	
    	soundId = contextResources.getIdentifier("gamethrive_default_sound", "raw", packageName);
    	if (soundId != 0)
    		return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId);
        
    	return null;
    }
    
    private static void addActionButtons(Bundle gcmBundle, NotificationCompat.Builder mBuilder, int notificationId) {
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
    }
}