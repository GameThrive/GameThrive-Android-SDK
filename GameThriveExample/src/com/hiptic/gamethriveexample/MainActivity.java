/**
 * Copyright 2014 GameThrive
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

package com.hiptic.gamethriveexample;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;

import com.gamethrive.GameThrive;
import com.gamethrive.NotificationOpenedHandler;

public class MainActivity extends Activity {
	
	// There should only be one GameThrive instance for your whole App across all activities.
	private static GameThrive gameThrive;
	
	static Activity currentActivity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		currentActivity = this;
		
		// Pass in your app's Context, Google Project number, your GameThrive App ID, and a NotificationOpenedHandler
		if (gameThrive == null)
			gameThrive = new GameThrive(this, "703322744261", "5eb5a37e-b458-11e3-ac11-000c2940e62c", new ExampleNotificationOpenedHandler());
	}
	
	// activity_main.xml defines the link to this method from the button.
	public void sendTag(View view) {
		gameThrive.sendTag("key", "valueAndroid");
	}
	
	// activity_main.xml defines the link to this method from the button.
	public void sendPurchase(View view) {
		gameThrive.sendPurchase(12.34);
	}
	
	// onPause and onResume hooks are required so GameThrive knows when to create a notification and when to just call your callback and playtime for segmentation.
	// To save on adding these hooks to every Activity in your app it might be worth extending android.app.Activity to include this logic if you have many Activity classes in your App.
	// Anther option is to use this library https://github.com/BoD/android-activitylifecyclecallbacks-compat
	@Override
	protected void onPause() {
		super.onPause();
		gameThrive.onPaused();
	}
	@Override
	protected void onResume() {
		super.onResume();
		gameThrive.onResumed();
	}
	
	// NotificationOpenedHandler is implemented in its own class instead of adding implements to MainActivity so we don't hold on to a reference of our first activity if it gets recreated.
	private class ExampleNotificationOpenedHandler implements NotificationOpenedHandler {
		/**
		 * Callback to implement in your app to handle when a notification is opened from the Android status bar or
		 * a new one comes in while the app is running.
		 *
		 * @param message        The message string the user seen/should see in the Android status bar.
		 * @param additionalData The additionalData key value pair section you entered in on gamethrive.com.
		 * @param isActive       Was the app in the foreground when the notification was received. 
		 */
		@Override
		public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {		
			String messageTitle = null, messageBody = null;
			
			if (additionalData != null) {
				if (additionalData.has("discount")) {
					try {
						messageTitle = additionalData.getString("discount");
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				else if (additionalData.has("bonusCredits"))
					messageTitle = "Bonus Credits!";
				else if (additionalData.has("actionSelected")) {
					try {
						messageTitle = "Pressed ButtonID:" + additionalData.getString("actionSelected");
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				else
					messageTitle = "Other Extra Data";
				messageBody = message + "\n\n" + additionalData.toString();
			}
			else if (isActive) { // If a push notification is received when the app is being used it does not display in the notification bar so display in the app.
				messageTitle = "GameThrive Message";
				messageBody = message;
			}
			
			// Recommend that you wait until they pause or are finished with the level to show a dialog so you don't interrupted them during gameplay.
			if (messageBody != null)
				SafeAlertDialog(messageTitle, messageBody);
		}
	}
	
	// AlertDialogs do not show on Android 2.3 if they are trying to be displayed while the activity is pause.
	// We sleep for 500ms to wait for the activity to be ready before displaying.
	private static void SafeAlertDialog(final String msgTitle, final String msgBody) {
		new Thread(new Runnable() {
			public void run() {
				try {Thread.sleep(500);} catch(Throwable t) {}

				MainActivity.currentActivity.runOnUiThread(new Runnable() {
					public void run() {
						new AlertDialog.Builder(MainActivity.currentActivity)
						.setTitle(msgTitle)
						.setMessage(msgBody)
						.setCancelable(true)
						.setPositiveButton("OK", null)
						.create().show();
					}});
			}
		}).start();
	}
}