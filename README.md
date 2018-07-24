# esri-3d-android
Showcase quartz 3D capabilities in ArcGIS SDK for Android

**Note:** this is a **prototype** meant for demonstration purposes only. It is not meant for commercial use and it is not an Esri developer sample.

**Updated for SDK 100.3.0**
* This app runs best on devices with 3 GB or more of RAM and OpenGL ES 3.0 or above.
* Now supports only devices running KitKat (Android 19) or above.

**Usage Notes:**

1. The "Open" function lets you specify a portal URL and account credentials. But it will only show the list of Web Scenes owned/created by that account--it won't show Web Scenes created by another account but which are allowed to be viewed by the signed-in account. **Upshot:** Plan to view only Web Scenes owned by the account you'll be signing in with.
1. The "Open" function will cache user credentials entered for that portal. If you want to load a webscene from another user, you'll need to exit the app (back button or appswitcher + kill) and restart it to sign in to the portal with a different user.
1. "Open" will show webscenes owned by the signed-in credentials. The app only loads webscenes. You'll need to author and save a webscene with the layers you want, using the account you'll sign into with the app. Make sure that if you specify a surface elevation layer, that you set it to show in the table of contents and that it's checked.
1. In pivot lock mode, tap or drag a point to start rotation around it. Pinch or zoom to move toward or away the pivot point during rotation. Tap again to stop auto-rotation; manual navigation will still be locked around that point. Use the close button ("X") in lower-right to exit pivot mode.
1. In viewshed mode, dragging a finger around on the display normally moves the viewshed analysis as you drag. You can temporarily put it into pan mode by long-pressing before dragging. An icon under the compass should indicate you are in pan mode.
