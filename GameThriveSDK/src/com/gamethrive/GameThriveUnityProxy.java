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

package com.gamethrive;

import org.json.JSONException;
import org.json.JSONObject;

import com.unity3d.player.UnityPlayer;

public class GameThriveUnityProxy implements NotificationOpenedHandler {

	private GameThrive gameThrive;
	private String unitylistenerName;
	
	public GameThriveUnityProxy(String listenerName, String googleProjectNumber, String gameThriveAppId) {
		unitylistenerName = listenerName;
		gameThrive = new GameThrive(UnityPlayer.currentActivity, googleProjectNumber, gameThriveAppId, this);
	}
	
	@Override
	public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
		JSONObject outerObject = new JSONObject();
		try {
			outerObject.put("isActive", isActive);
			outerObject.put("alert", message);
			outerObject.put("custom", additionalData);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		UnityPlayer.UnitySendMessage(unitylistenerName, "onPushNotificationReceived", outerObject.toString());
	}
	
	
	public void onPause() {
		gameThrive.onPaused();
	}
	
	public void onResume() {
		gameThrive.onResumed();
	}
	
	public void sendTag(String key, String value) {
		gameThrive.sendTag(key, value);
	}
	
	public void sendPurchase(double amount) {
		gameThrive.sendPurchase(amount);
	}
}
