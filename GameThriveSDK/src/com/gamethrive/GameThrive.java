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

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

import org.apache.http.Header;
import org.json.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.TypedValue;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.ads.identifier.AdvertisingIdClient.Info;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.loopj.android.http.*;

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
	
	private String googleProjectNumber, appId;
	private Activity appContext;
	private GoogleCloudMessaging gcm;
	
	private String registrationId, playerId = null;
	private JSONObject pendingTags;
	
	private NotificationOpenedHandler notificationOpenedHandler;
	
	static GameThrive instance;
	private boolean foreground = true;

	private IdsAvailableHandler idsAvailableHandler;
	
	private long lastTrackedTime;
	
	private static String lastNotificationIdOpenned;
	
	public GameThrive(Activity context, String googleProjectNumber, String gameThriveAppId) {
		this(context, googleProjectNumber, gameThriveAppId, null);
	}
	
	public GameThrive(Activity context, String googleProjectNumber, String gameThriveAppId, NotificationOpenedHandler notificationOpenedHandler) {
		instance = this;
		this.googleProjectNumber = googleProjectNumber;
		appId = gameThriveAppId;
		appContext = context;
		this.notificationOpenedHandler = notificationOpenedHandler;
		lastTrackedTime = SystemClock.elapsedRealtime();
		
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
		
		// Check device for Play Services APK. If check succeeds, proceed with GCM registration.
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(appContext);
            registerInBackground();
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
            registerPlayer();
        }
        
        // Called from tapping on a Notification from the status bar when the activity is completely dead and not open in any state.
        if (appContext.getIntent() != null && appContext.getIntent().getBundleExtra("data") != null)
        	runNotificationOpenedCallback(appContext.getIntent().getBundleExtra("data"), false, true);
	}
	
	/**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(appContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            Log.i(TAG, "Google Player error: This device is not supported. Code:" + resultCode);
            return false;
        }
        return true;
    }
    
    public void onPaused() {
    	foreground = false;
    	
    	if (GetPlayerId() == null)
    		return;
    	
    	JSONObject jsonBody = new JSONObject();
		try {
			jsonBody.put("app_id", appId);
			jsonBody.put("state", "ping");
			jsonBody.put("active_time", (long)(((SystemClock.elapsedRealtime() - lastTrackedTime) / 1000d) + 0.5d));
		
			GameThriveRestClient.post(appContext, "players/" + GetPlayerId() + "/on_focus", jsonBody, new JsonHttpResponseHandler() {
				@Override
				public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
					 Log.i(TAG, "sendTags:JSON Failed");
					 throwable.printStackTrace();
				 }
			});
			
			lastTrackedTime = SystemClock.elapsedRealtime();
		} catch (Throwable t) {
			t.printStackTrace();
		}
    }
    
    public void onResumed() {
    	foreground = true;
    	lastTrackedTime = SystemClock.elapsedRealtime();
    }
    
    boolean isForeground() {
    	return foreground;
    }
    
    /**
     * Registers the application with GCM servers asynchronously.
     */
    private void registerInBackground() {
    	new Thread(new Runnable() {
    		public void run() {
    			String msg = "";
                try {
                    if (gcm == null)
                        gcm = GoogleCloudMessaging.getInstance(appContext);
                    SaveRegistractionId(gcm.register(googleProjectNumber));
                    msg = "Device registered, registration ID=" + registrationId;
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // TODO: Retry with exponential back-off.
                }
                Log.i(TAG, msg + "\n");
            	// If success or failure register the player with our server.
                registerPlayer();
            }
        }).start();
    }

	// Do not call this function from the main thread. Otherwise, 
	// an IllegalStateException will be thrown.
	private String getAdvertisingId() {
		try {
			Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(appContext);
			final String id = adInfo.getId();
			final boolean isLAT = adInfo.isLimitAdTrackingEnabled();
			if (isLAT)
				return "OptedOut"; // Google restricts usage of the id to "build profiles" if the user checks opt out so we can't collect.
			else
				return id;
		}
		catch (IOException e) { Log.d("EXCEPTION", "IOException"); } // Unrecoverable error connecting to Google Play services (e.g., the old version of the service doesn't support getting AdvertisingId).
		catch (GooglePlayServicesNotAvailableException e) { Log.d("EXCEPTION", "GooglePlayServicesNotAvailableException"); } // Google Play services is not available entirely.
		//catch (IllegalStateException e) { Log.d("EXCEPTION", "IllegalStateException"); } // Unknown error
		catch (GooglePlayServicesRepairableException e) { Log.d("EXCEPTION", "GooglePlayServicesRepairableException"); } // Google Play Services is not installed, up-to-date, or enabled

		return null;
	}
	
	// See links on alternative unique IDs for players
	// http://technet.weblineindia.com/mobile/getting-unique-device-id-of-an-android-smartphone/
	// http://stackoverflow.com/questions/2785485/is-there-a-unique-android-device-id

	// Requires android.permission.READ_PHONE_STATE permission
	private String getPhoneId()
	{
		try {
			final String deviceId = ((TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
			return deviceId;
		}
		catch (RuntimeException e) {
			return null;
		}
	}
	
	private String getAndroidId() {
		try {
			final String androidId = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
			// see http://code.google.com/p/android/issues/detail?id=10603 for info on this 'dup' id.
			if (androidId != "9774d56d682e549c")
				return androidId;
		}
		catch (RuntimeException e) {
			return null;
		}

		return null;
	}
	
	// Requires android.permission.ACCESS_WIFI_STATE permission
	private String getWifiMac() {
		try {
			final String m_wlanMacAdd = ((WifiManager)appContext.getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getMacAddress();
			return m_wlanMacAdd;
		}
		catch (RuntimeException e) {
			return null;
		}
	}
	
	private String getAltId() {
		String id;

		id = getPhoneId();
		if (id != null)
			return id;

		// Wifi is more consistent than the Android ID but Corona's deviceID falls back to this
		id = getAndroidId();
		if (id != null)
			return id;
		
		id = getWifiMac();
		if (id != null)
			return id;

		return null;
	}
    
    private void registerPlayer() {
    	// Must run in its own thread due to the use of getAdvertisingId
    	 new Thread(new Runnable() {
		       public void run() {
		    		try {
		    			JSONObject jsonBody = new JSONObject();
		    			jsonBody.put("app_id", appId);
		    			jsonBody.put("device_type", 1);
		    			if (registrationId != null)
		    				jsonBody.put("identifier", registrationId);
		    			
		    			String adId = getAdvertisingId();
		    			// "... must use the advertising ID (when available on a device) in lieu of any other device identifiers ..." - https://play.google.com/about/developer-content-policy.html
		    			if (adId != null)
		    				jsonBody.put("ad_id", adId);
		    			else {
		    				adId = getAltId();
		    				if (adId != null)
		    					jsonBody.put("ad_id", adId);
		    			}
		    			
		    			jsonBody.put("device_os", android.os.Build.VERSION.RELEASE);
		    			jsonBody.put("device_model", android.os.Build.MODEL);
		    			jsonBody.put("timezone", Calendar.getInstance().getTimeZone().getRawOffset() / 1000); // converting from milliseconds to seconds
		    			jsonBody.put("language", Locale.getDefault().getLanguage());
		    			jsonBody.put("sdk", "010301");
		    			try {
		    				jsonBody.put("game_version", appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0).versionName);
		    			}
		    			catch (PackageManager.NameNotFoundException e) {}
		    			
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
		    			catch (Throwable e) {
		    			}
		    			
		    			if (GetPlayerId() == null) {
		    				JsonHttpResponseHandler jsonHandler = new JsonHttpResponseHandler() {
		    					@Override
		    					public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
		    						try {
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
		    						} catch (JSONException e) {
		    							e.printStackTrace();
		    						}
		    					}
		    					
		    					@Override
		    					public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
		    						 Log.i(TAG, "JSON Create player Failed");
		    						 throwable.printStackTrace();
		    					 }

		    				};
		    				GameThriveRestClient.postSync(appContext, "players", jsonBody, jsonHandler);
		    			} else {
		    				GameThriveRestClient.postSync(appContext, "players/" + GetPlayerId() + "/on_session", jsonBody, new JsonHttpResponseHandler() {
		    					@Override
		    					public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
		    						 Log.i(TAG, "JSON OnSession Failed");
		    						 throwable.printStackTrace();
		    					 }
		    				});
		    			}
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
				
				GameThriveRestClient.put(appContext, "players/" + GetPlayerId(), jsonBody, new JsonHttpResponseHandler() {
					@Override
					public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
						 Log.i(TAG, "sendTags:JSON Failed");
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
				Log.i(TAG, "getTags:JSON Failed");
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
					 Log.i(TAG, "deleteTags:JSON Failed");
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
    
    public void sendPurchase(double amount) {
    	sendPurchase(new BigDecimal(amount));
    }
    
    public void sendPurchase(BigDecimal amount) {
    	if (GetPlayerId() == null)
    		return;
    	
    	try {
			JSONObject jsonBody = new JSONObject();
			jsonBody.put("app_id", appId);
			jsonBody.put("amount", amount);
			
			GameThriveRestClient.post(appContext, "players/" + GetPlayerId() +"/on_purchase", jsonBody, new JsonHttpResponseHandler() {
				@Override
				public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
					 Log.i(TAG, "JSON Send Purchase Failed");
					 throwable.printStackTrace();
				    }
			});
		} catch (Throwable e) { // JSONException and UnsupportedEncodingException
			e.printStackTrace();
		}
    }
    
    private void runNotificationOpenedCallback(final Bundle data, final boolean isActive, boolean isUiThread) {
    	try {
    		 JSONObject customJSON = new JSONObject(data.getString("custom"));
    		 final JSONObject additionalDataJSON;
    		 if (customJSON.has("a"))
    			 additionalDataJSON = customJSON.getJSONObject("a");
    		 else
    			 additionalDataJSON = null;
    		 
    		 if (!isActive && customJSON.has("u")) {
    			 String url = customJSON.getString("u");
				 if (!url.startsWith("http://") && !url.startsWith("https://"))
					   url = "http://" + url;
    			 Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    			 appContext.startActivity(browserIntent);
    		 }
    		 
    		 if (notificationOpenedHandler != null) {
	    		 Runnable callBack = new Runnable() {
	    			 @Override
	    			 public void run() {
	        			 notificationOpenedHandler.notificationOpened(data.getString("alert"), additionalDataJSON, isActive);
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
    
    // Call from tapping on a Notification from the status bar when the app is suspended in the background.
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
					Log.i(TAG, "JSON Send Notification Opened Failed");
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
    
    public String GetPlayerId() {
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
    
	private void SaveRegistractionId(String registartionId) {
    	this.registrationId = registartionId;
    	final SharedPreferences prefs = getGcmPreferences(appContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("GT_REGISTRATION_ID", registartionId);
        editor.commit();
	}
    
    private static SharedPreferences getGcmPreferences(Context context) {
        return context.getSharedPreferences(GameThrive.class.getSimpleName(), Context.MODE_PRIVATE);
    }
}
