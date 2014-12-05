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

import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import android.content.Context;
import android.util.Log;

public class AdvertisingIdProviderGPS implements AdvertisingIdentifierProvider {

	@Override
	public String getIdentifier(Context appContext) {
		try {
			AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(appContext);
			final String id = adInfo.getId();
			final boolean isLAT = adInfo.isLimitAdTrackingEnabled();
			if (isLAT)
				return "OptedOut"; // Google restricts usage of the id to "build profiles" if the user checks opt out so we can't collect.
			return id;
		}
		catch (Throwable t) {
			Log.w(GameThrive.TAG, "Error getting Google Ad id: ", t);
		}
		
		// IOException                             = Unrecoverable error connecting to Google Play services (e.g., the old version of the service doesn't support getting AdvertisingId).
		// GooglePlayServicesNotAvailableException = Google Play services is not available entirely.
		// IllegalStateException                   = Unknown error
		// GooglePlayServicesRepairableException   = Google Play Services is not installed, up-to-date, or enabled
		
		return null;
	}
}
