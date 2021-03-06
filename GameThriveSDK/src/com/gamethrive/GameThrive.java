/**
 * Modified MIT License
 * 
 * Copyright 2015 GameThrive
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by GameThrive.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.gamethrive;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

import org.apache.http.Header;
import org.json.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;

import com.loopj.android.http.*;
import com.stericson.RootTools.internal.RootToolsInternalMethods;

public class GameThrive {
    
	public interface IdsAvailableHandler {
		void idsAvailable(String playerId, String registrationId);
	}
	
	public interface GetTagsHandler {
		void tagsAvailable(JSONObject tags);
	}
	
	/**
     * Tag used on log messages.
     */
    static final String TAG = "GameThrive";
	
	private String appId;
	private Activity appContext;
	
	private String registrationId, playerId = null;
	private JSONObject pendingTags;
	
	private NotificationOpenedHandler notificationOpenedHandler;
	
	static GameThrive instance;
	private boolean foreground = true;

	private IdsAvailableHandler idsAvailableHandler;
	
	private long lastTrackedTime, unSentActiveTime = -1;
	
	private static String lastNotificationIdOpenned;
	private TrackGooglePurchase trackGooglePurchase;
	private TrackAmazonPurchase trackAmazonPurchase;
	
	public static final int VERSION = 010700;
	public static final String STRING_VERSION = "010700";
	
	private PushRegistrator pushRegistrator;
	private AdvertisingIdentifierProvider mainAdIdProvider = new AdvertisingIdProviderGPS();
	
	private int deviceType;
	
	public GameThrive(Activity context, String googleProjectNumber, String gameThriveAppId) {
		this(context, googleProjectNumber, gameThriveAppId, null);
	}
	
	public GameThrive(Activity context, String googleProjectNumber, String gameThriveAppId, NotificationOpenedHandler notificationOpenedHandler) {
		instance = this;
		appId = gameThriveAppId;
		appContext = context;
		this.notificationOpenedHandler = notificationOpenedHandler;
		lastTrackedTime = SystemClock.elapsedRealtime();
		
		try {
			Class.forName("com.amazon.device.iap.PurchasingListener");
			trackAmazonPurchase = new TrackAmazonPurchase(appContext, this);
		} catch (ClassNotFoundException e) {}
		
		try {
		    Class.forName("com.amazon.device.messaging.ADM");
		    pushRegistrator = new PushRegistratorADM();
		    deviceType = 2;
		}
		catch (ClassNotFoundException e) {
			pushRegistrator = new PushRegistratorGPS();
			deviceType = 1;
		}
		
		// Re-register player if the app id changed, this might happen when a dev is testing.
		String oldAppId = GetSavedAppId();
		if (oldAppId != null) {
			if (!oldAppId.equals(appId)) {
				Log.i(TAG, "APP ID changed, clearing player id as it is no longer valid.");
				SavePlayerId(null);
				SaveAppId(appId);
			}
		}
		else
			SaveAppId(appId);
		
		pushRegistrator.registerForPush(appContext, googleProjectNumber, new PushRegistrator.RegisteredHandler() {
			@Override
			public void complete(String id) {
                registerPlayer(id);
			}
		});
        
        // Called from tapping on a Notification from the status bar when the activity is completely dead and not open in any state.
        if (appContext.getIntent() != null && appContext.getIntent().getBundleExtra("data") != null)
        	runNotificationOpenedCallback(appContext.getIntent().getBundleExtra("data"), false, true);
        
        if (TrackGooglePurchase.CanTrack(appContext))
        	trackGooglePurchase = new TrackGooglePurchase(appContext, this);
	}
    
    public void onPaused() {
    	foreground = false;
    	
		if (trackAmazonPurchase != null)
			trackAmazonPurchase.checkListener();
    	
    	long time_elapsed = (long)(((SystemClock.elapsedRealtime() - lastTrackedTime) / 1000d) + 0.5d);
    	if (time_elapsed < 0 || time_elapsed > 604800)
    		return;
    	
    	long unSentActiveTime = GetUnsentActiveTime();
    	long totalTimeActive = unSentActiveTime + time_elapsed;
    	
    	if (totalTimeActive < 30) {
    		SaveUnsentActiveTime(totalTimeActive);
    		return;
    	}
    	
    	if (GetPlayerId() == null)
    		return;
    	
    	JSONObject jsonBody = new JSONObject();
		try {
			jsonBody.put("app_id", appId);
			jsonBody.put("state", "ping");
			jsonBody.put("active_time", totalTimeActive);
			addNetType(jsonBody);
			
			GameThriveRestClient.post(appContext, "players/" + GetPlayerId() + "/on_focus", jsonBody, new JsonHttpResponseHandler() {
				@Override
				public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
					 Log.i(TAG, "sendTags:JSON Failed");
					 throwable.printStackTrace();
				 }
			});
			
			SaveUnsentActiveTime(0);
			lastTrackedTime = SystemClock.elapsedRealtime();
		} catch (Throwable t) {
			t.printStackTrace();
		}
    }
    
    public void onResumed() {
    	foreground = true;
    	lastTrackedTime = SystemClock.elapsedRealtime();
    	
    	if (trackGooglePurchase != null)
    		trackGooglePurchase.trackIAP();
    }
    
    boolean isForeground() {
    	return foreground;
    }
    
    private void addNetType(JSONObject jsonObj) {
    	try {
			ConnectivityManager cm = (ConnectivityManager)appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo netInfo = cm.getActiveNetworkInfo();
			
			int networkType = netInfo.getType();
			int netType = 1;
			if (networkType == ConnectivityManager.TYPE_WIFI || networkType == ConnectivityManager.TYPE_ETHERNET)
				netType = 0;
			jsonObj.put("net_type", netType);
		} catch (Throwable t) {}
    }
	
    private void registerPlayer(String id) {
    	if (id != null)
    		SaveRegistractionId(id);
    	
    	// Must run in its own thread due to the use of getAdvertisingId
    	 new Thread(new Runnable() {
		       public void run() {
		    		try {
		    			JSONObject jsonBody = new JSONObject();
		    			jsonBody.put("app_id", appId);
		    			jsonBody.put("device_type", deviceType);
		    			if (registrationId != null)
		    				jsonBody.put("identifier", registrationId);
		    			
		    			String adId = mainAdIdProvider.getIdentifier(appContext);
		    			// "... must use the advertising ID (when available on a device) in lieu of any other device identifiers ..."
		    			// https://play.google.com/about/developer-content-policy.html
		    			if (adId != null)
		    				jsonBody.put("ad_id", adId);
		    			else {
		    				adId = new AdvertisingIdProviderFallback().getIdentifier(appContext);
		    				if (adId != null)
		    					jsonBody.put("ad_id", adId);
		    			}
		    			
		    			jsonBody.put("device_os", android.os.Build.VERSION.RELEASE);
		    			jsonBody.put("device_model", android.os.Build.MODEL);
		    			jsonBody.put("timezone", Calendar.getInstance().getTimeZone().getRawOffset() / 1000); // converting from milliseconds to seconds
		    			jsonBody.put("language", Locale.getDefault().getLanguage());
		    			jsonBody.put("sdk", STRING_VERSION);
		    			try {
		    				jsonBody.put("game_version", appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0).versionName);
		    			} catch (PackageManager.NameNotFoundException e) {}
		    			
		    			addNetType(jsonBody);
		    			
		    			if (RootToolsInternalMethods.isRooted())
		    				jsonBody.put("rooted", true);
		    			
		    			try {
			    			Field[] fields = Class.forName(appContext.getPackageName() + ".R$raw").getFields();
			    			JSONArray soundList = new JSONArray();
			    			TypedValue fileType = new TypedValue();
			    			String fileName;
			    			
			    			for(int i = 0; i < fields.length; i++) {
			    				appContext.getResources().getValue(fields[i].getInt(null), fileType, true);
			    				fileName = fileType.string.toString().toLowerCase();
			    				
			    				if (fileName.endsWith(".wav") || fileName.endsWith(".mp3"))
			    					soundList.put(fields[i].getName());
			    			}
			    			
			    			if (soundList.length() > 0)
			    				jsonBody.put("sounds", soundList);
		    			}
		    			catch (Throwable e) {}
		    			
		    			
		    			String urlStr;
		    			if (GetPlayerId() == null)
		    				urlStr = "players";
		    			else
		    				urlStr = "players/" + GetPlayerId() + "/on_session";
		    			
	    				JsonHttpResponseHandler jsonHandler = new JsonHttpResponseHandler() {
	    					@Override
	    					public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
	    						try {
	    							if (response.has("id")) {
		    							SavePlayerId(response.getString("id"));
		    							if (pendingTags != null) {
		    								sendTags(pendingTags);
		    								pendingTags = null;
		    							}
		    							
		    							if (idsAvailableHandler != null) {
		    								 appContext.runOnUiThread(new Runnable() {
		    					    			 @Override
		    					    			 public void run() {
		    					    				 idsAvailableHandler.idsAvailable(GetPlayerId(), GetRegistrationId());
				    								 idsAvailableHandler = null;
		    					    			 }
		    					    		 });
		    							}
	    							}
	    						} catch (JSONException e) {
	    							e.printStackTrace();
	    						}
	    					}
	    					
	    					@Override
	    					public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
	    						 Log.w(TAG, "JSON Create or on_session for player Failed", throwable);
	    					}
	    				};
	    				GameThriveRestClient.postSync(appContext, urlStr, jsonBody, jsonHandler);
		    			
		    		} catch (Throwable e) { // JSONException and UnsupportedEncodingException
		    			e.printStackTrace();
		    		}
	            }
	        }).start();
    }
    
    public void sendTag(String key, String value) {
    	try {
			sendTags(new JSONObject().put(key, value));
		} catch (JSONException e) {
			e.printStackTrace();
		}
    }
    
    public void sendTags(JSONObject keyValues) {
    	try {
    		if (GetPlayerId() == null) {
    			if (pendingTags == null)
    				pendingTags = new JSONObject();
    			Iterator<String> keys = keyValues.keys();
				String key;
    			while(keys.hasNext()) {
    				 key = keys.next();
    				pendingTags.put(key, keyValues.get(key));
    			}
    		}
    		else {
				JSONObject jsonBody = new JSONObject();
				jsonBody.put("app_id", appId);
				jsonBody.put("tags", keyValues);
				addNetType(jsonBody);
				
				GameThriveRestClient.put(appContext, "players/" + GetPlayerId(), jsonBody, new JsonHttpResponseHandler() {
					@Override
					public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
						 Log.w(TAG, "sendTags:JSON Failed");
						 throwable.printStackTrace();
					 }
				});
    		}
		} catch (Throwable e) { // JSONException and UnsupportedEncodingException
			e.printStackTrace();
		}
    }
    
    public void getTags(final GetTagsHandler getTagsHandler) {
		GameThriveRestClient.get(appContext, "players/" + GetPlayerId(), new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
				appContext.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						try {
							getTagsHandler.tagsAvailable(response.getJSONObject("tags"));
						} catch (JSONException e) {
							e.printStackTrace();
						}
					}
				});
			}
			
			@Override
			public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
				Log.w(TAG, "getTags:JSON Failed");
				throwable.printStackTrace();
			}
		});
    }
    
    public void deleteTag(String key) {
    	Collection<String> tempList = new ArrayList<String>(1);
    	tempList.add(key);
    	deleteTags(tempList);
    }
    
    public void deleteTags(Collection<String> keys) {
		if (GetPlayerId() == null)
			return;
    	
    	try {
    		JSONObject jsonTags = new JSONObject();
    		for(String key : keys)
    			jsonTags.put(key, "");
    		
			JSONObject jsonBody = new JSONObject();
			jsonBody.put("app_id", appId);
			jsonBody.put("tags", jsonTags);
				
			GameThriveRestClient.put(appContext, "players/" + GetPlayerId(), jsonBody, new JsonHttpResponseHandler() {
				@Override
				public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
					 Log.w(TAG, "deleteTags:JSON Failed");
					 throwable.printStackTrace();
				 }
			});
		} catch (Throwable e) { // JSONException and UnsupportedEncodingException
			e.printStackTrace();
		}
    }
    
    public void idsAvailable(IdsAvailableHandler idsAvailableHandler) {
    	if (GetPlayerId() != null)
    		idsAvailableHandler.idsAvailable(GetPlayerId(), GetRegistrationId());
    	else
    		this.idsAvailableHandler = idsAvailableHandler;
    }
    
    /**
     * Call when player makes an IAP purchase in your app with the amount in USD.
     *
     * @deprecated Automatically tracked.
     * @Deprecated Automatically tracked.
     */
    public void sendPurchase(double amount) {
    	Log.i(TAG, "sendPurchase is deprecated as this is now automatic for Google Play IAP purchases. The method does nothing!");
    }
    
    /**
     * Call when player makes an IAP purchase in your app with the amount in USD.
     *
     * @deprecated Automatically tracked.
     * @Deprecated Automatically tracked.
     */
    public void sendPurchase(BigDecimal amount) {
    	Log.i(TAG, "sendPurchase is deprecated as this is now automatic for Google Play IAP purchases. The method does nothing!");
    }
    
    void sendPurchases(JSONArray purchases, boolean newAsExisting, ResponseHandlerInterface httpHandler) {
    	if (GetPlayerId() == null)
    		return;
    	
    	try {
    		JSONObject jsonBody = new JSONObject();
    		jsonBody.put("app_id", appId);
    		if (newAsExisting)
    			jsonBody.put("existing", true);
    		jsonBody.put("purchases", purchases);
    		
    		if (httpHandler == null)
    			httpHandler = new JsonHttpResponseHandler();
    		
    		GameThriveRestClient.post(appContext, "players/" + GetPlayerId() + "/on_purchase", jsonBody, httpHandler);
    	} catch (Throwable e) { // JSONException and UnsupportedEncodingException
    		e.printStackTrace();
    	}
    }
    
    private void runNotificationOpenedCallback(final Bundle data, final boolean isActive, boolean isUiThread) {
    	try {
    		 JSONObject customJSON = new JSONObject(data.getString("custom"));
    		 
    		 if (!isActive && customJSON.has("u")) {
    			 String url = customJSON.getString("u");
				 if (!url.startsWith("http://") && !url.startsWith("https://"))
					 url = "http://" + url;
    			 Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    			 appContext.startActivity(browserIntent);
    		 }
    		 
    		 if (notificationOpenedHandler != null) {
        		 JSONObject additionalDataJSON = new JSONObject();
        		 
        		 if (customJSON.has("a"))
        			 additionalDataJSON = customJSON.getJSONObject("a");
        		 
        		 if (data.containsKey("title"))
        			 additionalDataJSON.put("title", data.getString("title"));
        		 
        		 if (customJSON.has("u"))
        			 additionalDataJSON.put("launchURL", customJSON.getString("u"));
        		 
        		 if (data.containsKey("sound"))
        			 additionalDataJSON.put("sound", data.getString("sound"));
        		 
        		 if (data.containsKey("sicon"))
        			 additionalDataJSON.put("smallIcon", data.getString("sicon"));
        		 
        		 if (data.containsKey("licon"))
        			 additionalDataJSON.put("largeIcon", data.getString("licon"));
        		 
        		 if (data.containsKey("bicon"))
        			 additionalDataJSON.put("bigPicture", data.getString("bicon"));
        		 
        		 if (additionalDataJSON.equals(new JSONObject()))
        			additionalDataJSON = null;
        		 
        		 final JSONObject finalAdditionalDataJSON = additionalDataJSON;
	    		 Runnable callBack = new Runnable() {
	    			 @Override
	    			 public void run() {
	        			 notificationOpenedHandler.notificationOpened(data.getString("alert"), finalAdditionalDataJSON, isActive);
	    			 }
	    		 };
	    		 
	    		 if (isUiThread)
	    			 callBack.run();
	    		 else
	    			 appContext.runOnUiThread(callBack);
    		 }
		} catch (JSONException e) {
			e.printStackTrace();
		}
    }
    
    // Called when receiving GCM message when app is open and in focus.
    void handleNotificationOpened(Bundle data) {
    	sendNotificationOpened(appContext, data);
    	runNotificationOpenedCallback(data, true, false);
    }
    
    // Called when opening a notification when the app is suspended in the background.
    static void handleNotificationOpened(Context inContext, Bundle data) {
    	sendNotificationOpened(inContext, data);
    	
    	// Open/Resume app when opening the notification.
        Intent launchIntent = inContext.getPackageManager().getLaunchIntentForPackage(inContext.getPackageName()).putExtra("data", data);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        inContext.startActivity(launchIntent);
        if (GameThrive.instance != null)
        	GameThrive.instance.runNotificationOpenedCallback(data, false, false);
    }
    
    private static void sendNotificationOpened(Context inContext, Bundle data) {
    	try {
			JSONObject customJson = new JSONObject(data.getString("custom"));
    		String notificationId = customJson.getString("i");
    		
    		// In some rare cases this can double fire, preventing that here.
    		if (notificationId.equals(lastNotificationIdOpenned))
    			return;
    		
    		lastNotificationIdOpenned = notificationId;
    		
			JSONObject jsonBody = new JSONObject();
			jsonBody.put("app_id", GetSavedAppId(inContext));
			jsonBody.put("player_id", GetSavedPlayerId(inContext));
			jsonBody.put("opened", true);
			
			GameThriveRestClient.put(inContext, "notifications/" + customJson.getString("i"), jsonBody, new JsonHttpResponseHandler() {
				@Override
				public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
					Log.w(TAG, "JSON Send Notification Opened Failed");
					throwable.printStackTrace();
				}
			});
		} catch (Throwable e) { // JSONException and UnsupportedEncodingException
			e.printStackTrace();
		}
    }
    
    private void SaveAppId(String appId) {
    	final SharedPreferences prefs = getGcmPreferences(appContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("GT_APP_ID", appId);
        editor.commit();
    }
    
    private String GetSavedAppId() {
    	final SharedPreferences prefs = getGcmPreferences(appContext);
		return prefs.getString("GT_APP_ID", null);
    }
    
    private static String GetSavedAppId(Context inContext) {
    	final SharedPreferences prefs = getGcmPreferences(inContext);
		return prefs.getString("GT_APP_ID", null);
    }
    
    private static String GetSavedPlayerId(Context inContext) {
    	final SharedPreferences prefs = getGcmPreferences(inContext);
		return prefs.getString("GT_PLAYER_ID", null);
    }
    
    String GetPlayerId() {
    	if (playerId == null) {
    		final SharedPreferences prefs = getGcmPreferences(appContext);
    		playerId = prefs.getString("GT_PLAYER_ID", null);
    	}
    	return playerId;
    }
    
    private void SavePlayerId(String playerId) {
    	this.playerId = playerId;
    	final SharedPreferences prefs = getGcmPreferences(appContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("GT_PLAYER_ID", playerId);
        editor.commit();
    }
    
    public String GetRegistrationId() {
    	if (registrationId == null) {
    		final SharedPreferences prefs = getGcmPreferences(appContext);
    		registrationId = prefs.getString("GT_REGISTRATION_ID", null);
    	}
    	return registrationId;
    }
    
    // If true(default) - Device will always vibrate unless the device is in silent mode.
    // If false - Device will only vibrate when the device is set on it's vibrate only mode.
    public void enableVibrate(boolean enable) {
    	final SharedPreferences prefs = getGcmPreferences(appContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("GT_VIBRATE_ENABLED", enable);
        editor.commit();
    }
    
    static boolean getVibrate(Context appContext) {
    	final SharedPreferences prefs = getGcmPreferences(appContext);
    	return prefs.getBoolean("GT_VIBRATE_ENABLED", true);
    }
    
    // If true(default) - Sound plays when receiving notification. Vibrates when device is on vibrate only mode.
    // If false - Only vibrates unless EnableVibrate(false) was set.
    public void enableSound(boolean enable) {
    	final SharedPreferences prefs = getGcmPreferences(appContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("GT_SOUND_ENABLED", enable);
        editor.commit();
    }
    
    static boolean getSoundEnabled(Context appContext) {
    	final SharedPreferences prefs = getGcmPreferences(appContext);
    	return prefs.getBoolean("GT_SOUND_ENABLED", true);
    }
    
	private void SaveRegistractionId(String registartionId) {
    	this.registrationId = registartionId;
    	final SharedPreferences prefs = getGcmPreferences(appContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("GT_REGISTRATION_ID", registartionId);
        editor.commit();
	}
	
	
	private long GetUnsentActiveTime() {
		if (unSentActiveTime == -1) {
			final SharedPreferences prefs = getGcmPreferences(appContext);
			unSentActiveTime = prefs.getLong("GT_UNSENT_ACTIVE_TIME", 0);
		}
		
		return unSentActiveTime;
	}
	
	private void SaveUnsentActiveTime(long time) {
		unSentActiveTime = time;
    	final SharedPreferences prefs = getGcmPreferences(appContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("GT_UNSENT_ACTIVE_TIME", time);
        editor.commit();
	}
	
    static SharedPreferences getGcmPreferences(Context context) {
        return context.getSharedPreferences(GameThrive.class.getSimpleName(), Context.MODE_PRIVATE);
    }
	
	private static LinkedList<String> notificationsReceivedStack;
	
	private static void GetNotificationsReceived(Context context) {
		if (notificationsReceivedStack == null) {
			notificationsReceivedStack = new LinkedList<String>();
			
			final SharedPreferences prefs = getGcmPreferences(context);
    		String jsonListStr = prefs.getString("GT_RECEIVED_NOTIFICATION_LIST", null);
    		
    		if (jsonListStr != null) {
    			try {
    				JSONArray notificationsReceivedList = new JSONArray(jsonListStr);
    				for (int i = 0; i < notificationsReceivedList.length(); i++)
    					notificationsReceivedStack.push(notificationsReceivedList.getString(i));
    			} catch (JSONException e) {
    				e.printStackTrace();
    			}
    		}
		}
	}
	
	private static void AddNotificationIdToList(String id, Context context) {
		GetNotificationsReceived(context);
		if (notificationsReceivedStack == null)
			return;
		
		if (notificationsReceivedStack.size() >= 10)
			notificationsReceivedStack.removeLast();
		
		notificationsReceivedStack.addFirst(id);
		
		JSONArray jsonArray = new JSONArray();
		String notificationId;
		for(int i = notificationsReceivedStack.size() - 1; i > -1; i--) {
			notificationId = notificationsReceivedStack.get(i);
			if (notificationId == null)
				continue;
			jsonArray.put(notificationsReceivedStack.get(i));
		}
		
		final SharedPreferences prefs = getGcmPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("GT_RECEIVED_NOTIFICATION_LIST", jsonArray.toString());
        editor.commit();
	}
	
	static boolean isDuplicateNotification(String id, Context context) {
		GetNotificationsReceived(context);
		if (notificationsReceivedStack == null || id == null || "".equals(id))
			return false;
		
		if (notificationsReceivedStack.contains(id))
			return true;
		
		AddNotificationIdToList(id, context);
		return false;
	}
	
    static boolean isValidAndNotDuplicated(Context context, Bundle bundle) {
    	if (bundle.isEmpty())
    		return false;
    	
    	try {
    		JSONObject customJSON = new JSONObject(bundle.getString("custom"));
    		return !GameThrive.isDuplicateNotification(customJSON.getString("i"), context);
    	} catch (Throwable t) {
    		t.printStackTrace();
    	}
    	
		return false;
    }
}
