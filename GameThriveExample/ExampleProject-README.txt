Instructions to build example with Eclipse.

0. Open the Android SDK Manager and make sure "Android Support Library" and "Google Play services" are installed under the "Extras" section.
   "SDK Path:" is shown on this window, replace <android-sdk> with your path in the below steps.
1. Copy GameThriveSDK.jar into the libs folder.
2. Copy android-support-v4.jar into the libs folder.
    This will be located in (<android-sdk>\extras\android\support\v4\android-support-v4.jar)
3. Import google-play-services_lib into your workspace if you don't have it already.
   3A. Go to File>Import and then to Android>Existing Android Code into Workspace
   3B. Navigate to <android-sdk>\extras\google\google_play_services\libproject\google-play-services_lib and select the project then press Finish.
4. Add Google-play-services_lib as a library to the example project.
   4A. Right click on the GameThriveExample project and go to Properties.
   4B. On the left Select Android and then press the "Add..." button in Library section.
   4C. Pick the google-play-services_lib.
   
See http://docs.gamethrive.com/article/8-android-sdk-setup for screenshots on these steps.