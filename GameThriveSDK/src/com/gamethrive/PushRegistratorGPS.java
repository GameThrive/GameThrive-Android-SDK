/**
 * Modified MIT License
 * 
 * Copyright 2014 GameThrive
 * 
 * Portions Copyright 2013 Google Inc.
 * This file includes portions from the Google GcmClient demo project
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

import java.io.IOException;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.Activity;
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
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(appContext);
        if (resultCode != ConnectionResult.SUCCESS) {
        	if (GooglePlayServicesUtil.isUserRecoverableError(resultCode) && isGooglePlayStoreInstalled()) {
        		Log.i(GameThrive.TAG, "Google Play services Recoverable Error: " + resultCode);
        		
        		final SharedPreferences prefs = GameThrive.getGcmPreferences(appContext);
        		if (prefs.getBoolean("GT_DO_NOT_SHOW_MISSING_GPS", false))
        			return false;
        		
        		try { ShowUpdateGPSDialog(resultCode); } catch (Throwable t) {}
        	}
            else
                Log.i(GameThrive.TAG, "Google Play services error: This device is not supported. Code:" + resultCode);
        	
            return false;
        }
        
        return true;
    }
    
    private void ShowUpdateGPSDialog(final int resultCode) {
    	((Activity)appContext).runOnUiThread(new Runnable() {
            @Override
            public void run() {
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
						        editor.putBoolean("GT_DO_NOT_SHOW_MISSING_GPS", true);
						        editor.commit();
							}
        			   })
        			   .setNeutralButton("Close", null)
        			   .create().show();
            }
		});
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
