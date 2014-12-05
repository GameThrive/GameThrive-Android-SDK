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

import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;

public class AdvertisingIdProviderFallback implements AdvertisingIdentifierProvider {
	
	private static final List<String> INVALID_PHONE_IDS = Arrays.asList(
		"", "0", "unknown", "739463", "000000000000000","111111111111111","352005048247251","012345678912345", "012345678901237", "88508850885050", "0123456789abcde",
		"004999010640000", "862280010599525", "52443443484950", "355195000000017", "001068000000006", "358673013795895", "355692547693084", "004400152020000", "8552502717594321",
		"113456798945455", "012379000772883", "111111111111119", "358701042909755", "358000043654134", "345630000000115", "356299046587760", "356591000000222");
	
	@Override
	public String getIdentifier(Context appContext) {
		String id;

		id = getPhoneId(appContext);
		if (id != null)
			return id;
		
		id = getAndroidId(appContext);
		if (id != null)
			return id;
		
		return getWifiMac(appContext);
	}

	// Requires android.permission.READ_PHONE_STATE permission
	private String getPhoneId(Context appContext) {
		try {
			final String phoneId = ((TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
			 if (phoneId != null && !INVALID_PHONE_IDS.contains(phoneId))
				 return phoneId;
		}
		catch (RuntimeException e) {}
		return null;
	}
	
	private String getAndroidId(Context appContext) {
		try {
			final String androidId = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
			// see http://code.google.com/p/android/issues/detail?id=10603 for info on this 'dup' id.
			if (androidId != "9774d56d682e549c")
				return androidId;
		}
		catch (RuntimeException e) {}

		return null;
	}
	
	// Requires android.permission.ACCESS_WIFI_STATE permission
	private String getWifiMac(Context appContext) {
		try {
			return ((WifiManager)appContext.getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getMacAddress();
		}
		catch (RuntimeException e) {}
		
		return null;
	}
}