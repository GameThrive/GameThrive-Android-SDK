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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.AlertDialog;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;


class PushRegistratorGPS implements PushRegistrator {
	
	private Context appContext;
	private RegisteredHandler registeredHandler;
	
	@Override
	public void registerForPush(Context context, String googleProjectNumber, RegisteredHandler callback) {
		appContext = context;
		registeredHandler = callback;
		
		// Check device for Play Services APK. If check succeeds, proceed with GCM registration.
        if (checkPlayServices())
            registerInBackground(googleProjectNumber);
        else {
            Log.i(GameThrive.TAG, "No valid Google Play services APK found.");
            registeredHandler.complete(null);
        }
	}
	
	private boolean isGooglePlayStoreInstalled() {
	    try {
	    	PackageManager pm = appContext.getPackageManager();
	    	PackageInfo info = pm.getPackageInfo("com.android.vending", PackageManager.GET_ACTIVITIES);
	    	String label = (String) info.applicationInfo.loadLabel(pm);
	    	return (label != null && !label.equals("Market"));
	    }
	    catch (Throwable e) {}
	    
	    return false;
	}
	
    private boolean checkPlayServices() {
        final int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(appContext);
        if (resultCode != ConnectionResult.SUCCESS) {
        	if (GooglePlayServicesUtil.isUserRecoverableError(resultCode) && isGooglePlayStoreInstalled()) {
        		Log.i(GameThrive.TAG, "Google Play services Recoverable Error: " + resultCode);
        		
        		final SharedPreferences prefs = GameThrive.getGcmPreferences(appContext);
        		if (prefs.getBoolean("GT_DO_NOT_SHOW_MISING_GPS", false))
        			return false;
        		
        		AlertDialog.Builder builder = new AlertDialog.Builder(appContext);
        		builder.setMessage("To receive push notifications please press 'Update' to enable 'Google Play services'.")
        			   .setPositiveButton("Update", new OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								try {
				                    GooglePlayServicesUtil.getErrorPendingIntent(resultCode, appContext, 0).send();
				                } catch (CanceledException e) {}
							}
        			   })
        			   .setNegativeButton("Skip", new OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								final SharedPreferences prefs = GameThrive.getGcmPreferences(appContext);
						        SharedPreferences.Editor editor = prefs.edit();
						        editor.putBoolean("GT_DO_NOT_SHOW_MISING_GPS", true);
						        editor.commit();
							}
        			   })
        			   .setNeutralButton("Close", null)
        			   .create().show();
        	}
            else
                Log.i(GameThrive.TAG, "Google Play services error: This device is not supported. Code:" + resultCode);
        	
            return false;
        }
        
        return true;
    }
    
    private void registerInBackground(final String googleProjectNumber) {
    	new Thread(new Runnable() {
    		public void run() {
    			String msg = "";
    			String registrationId = null;
                try {
                	GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(appContext);
                	registrationId = gcm.register(googleProjectNumber);
                    msg = "Device registered, Google Registration ID=" + registrationId;
                } catch (IOException ex) {
                    msg = "Error Getting Google Registration ID:" + ex.getMessage();
                    // TODO: Retry with exponential back-off.
                }
                
                Log.i(GameThrive.TAG, msg);
                registeredHandler.complete(registrationId);
            }
        }).start();
    }
}
