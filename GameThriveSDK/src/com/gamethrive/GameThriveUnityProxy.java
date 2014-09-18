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

import android.app.Activity;

import org.json.JSONObject;

import com.gamethrive.GameThrive.GetTagsHandler;
import com.gamethrive.GameThrive.IdsAvailableHandler;

public class GameThriveUnityProxy implements NotificationOpenedHandler {

	private GameThrive gameThrive;
	private String unitylistenerName;
	private static java.lang.reflect.Method unitySendMessage;
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public GameThriveUnityProxy(String listenerName, String googleProjectNumber, String gameThriveAppId) {
		unitylistenerName = listenerName;
		
		try {
			// We use reflection here so the default proguard config does not get an error for native apps.
			Class unityPlayerClass;
			unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
			unitySendMessage = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);
			
			gameThrive = new GameThrive((Activity)unityPlayerClass.getField("currentActivity").get(null), googleProjectNumber, gameThriveAppId, this);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
	@Override
	public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
		JSONObject outerObject = new JSONObject();
		try {
			outerObject.put("isActive", isActive);
			outerObject.put("alert", message);
			outerObject.put("custom", additionalData);
			unitySendMessage.invoke(null, unitylistenerName, "onPushNotificationReceived", outerObject.toString());
		} catch (Throwable t) {
			t.printStackTrace();
		}
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
	
	public void getTags() {
		gameThrive.getTags(new GetTagsHandler() {
			@Override
			public void tagsAvailable(JSONObject tags) {
				try {
					unitySendMessage.invoke(null, unitylistenerName, "onTagsReceived", tags.toString());
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		});
	}
	
	public void deleteTag(String key) {
		gameThrive.deleteTag(key);
	}
	
	public void idsAvailable() {
		gameThrive.idsAvailable(new IdsAvailableHandler() {
			@Override
			public void idsAvailable(String playerId, String registrationId) {
				JSONObject jsonIds = new JSONObject();
				try {
					jsonIds.put("playerId", playerId);
					if (registrationId != null)
						jsonIds.put("pushToken", registrationId);
					else
						jsonIds.put("pushToken", "");
					
					unitySendMessage.invoke(null, unitylistenerName, "onIdsAvailable", jsonIds.toString());
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		});
	}
}
