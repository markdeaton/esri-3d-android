/* Copyright 2017 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.esri.apl.ea3d;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatSeekBar;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.apl.ea3d.event.LineOfSightTouchListener;
import com.esri.apl.ea3d.event.MeasurementTouchListener;
import com.esri.apl.ea3d.event.PivotLockTouchListener;
import com.esri.apl.ea3d.event.SensorNavigationTouchListener;
import com.esri.apl.ea3d.event.ViewshedTouchListener;
import com.esri.apl.ea3d.model.Bookmark3D;
import com.esri.apl.ea3d.util.CameraSerialization;
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.ArcGISSceneLayer;
import com.esri.arcgisruntime.layers.ArcGISTiledLayer;
import com.esri.arcgisruntime.layers.Layer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.ArcGISTiledElevationSource;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.DefaultSceneViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.SceneView;
import com.esri.arcgisruntime.mapping.view.ViewpointChangedEvent;
import com.esri.arcgisruntime.mapping.view.ViewpointChangedListener;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalFolder;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.arcgisruntime.portal.PortalUser;
import com.esri.arcgisruntime.portal.PortalUserContent;
import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.lang.StringUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
  // CONSTS
  private final String TAG = "MainActivity";
  private static final int PRC_LOCATION_MOVETO = 2;

  // WIDGETS/UI
  private SceneView mSceneView;
  private AppCompatImageView mCompass;
  private Menu mTBItems;
  private View mLytViewshedDist;
  private AppCompatImageView mViewshedNavMode;

  private LocationManager mLocationManager;
  private Location mLocation;

  // layers
  private List<Layer> mSceneLayers = new ArrayList<>(); // Scene layer, mesh, or feature layer
  private Portal mPortal;

  // bookmarks
  private List<Bookmark3D> mBookmarks = new ArrayList<>();

  // touch listeners
  private MeasurementTouchListener mMeasureTouchListener;
  private LineOfSightTouchListener mLineOfSightTouchListener;
  private ViewshedTouchListener mViewshedTouchListener;
  private SensorNavigationTouchListener mSensorNavTouchListener;
  private PivotLockTouchListener mPivotLockTouchListener;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    int iLicRes = getResources().getIdentifier("license_string_std", "string", getPackageName());
    if (iLicRes != 0) ArcGISRuntimeEnvironment.setLicense(getString(iLicRes));
    AuthenticationManager.CredentialCache.clear();

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Toolbar actionBar = findViewById(R.id.action_bar);
    setSupportActionBar(actionBar);

    // create a scene
    ArcGISScene scene = new ArcGISScene();

    mSceneView = findViewById(R.id.sceneView);
    mSceneView.setScene(scene);
    mSceneView.addViewpointChangedListener(onViewpointChanged);

    // Handle changes to viewshed distance setting
    mLytViewshedDist = findViewById(R.id.lytViewshedDist);
    // Prevent slider panel from passing touches through to Viewshed listener
    mLytViewshedDist.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View view, MotionEvent motionEvent) {
        return true;
      }
    });
    AppCompatSeekBar viewshedDistSlider = (AppCompatSeekBar)findViewById(R.id.viewshedDistSlider);
    // Slider min is unchangeable zero, so we have to jump through some hoops to make it 200 instead
    viewshedDistSlider.setMax(5000);
    int iStartDist = PreferenceManager.getDefaultSharedPreferences(this)
        .getInt(getString(R.string.pref_viewshed_dist),
            getResources().getInteger(R.integer.setting_initial_viewshed_dist_m));
    viewshedDistSlider.setOnSeekBarChangeListener(new OnViewshedDistChangeListener(
        PreferenceManager.getDefaultSharedPreferences(this),
        (TextView)findViewById(R.id.txtViewshedDist)));
    viewshedDistSlider.setProgress(iStartDist);

    // Get Nav mode indicator
    mViewshedNavMode = (AppCompatImageView)findViewById(R.id.viewshedNavModeIndicator);

    // Round floating action buttons for certain touch listeners
    FloatingActionButton btnZoomToViewshed =
        (FloatingActionButton)findViewById(R.id.btnZoomToAnalysisViewpoint);
    FloatingActionButton btnReturnToCamera =
        (FloatingActionButton)findViewById(R.id.btnReturnToCameraViewpoint);
    FloatingActionButton btnPivotLock =
        (FloatingActionButton)findViewById(R.id.btnStopPivot);

    mMeasureTouchListener = new MeasurementTouchListener(mSceneView);
    mLineOfSightTouchListener = new LineOfSightTouchListener(mSceneView);
    mViewshedTouchListener = new ViewshedTouchListener(mSceneView, btnZoomToViewshed, btnReturnToCamera, mViewshedNavMode);
    mSensorNavTouchListener = new SensorNavigationTouchListener(mSceneView, this);
    mPivotLockTouchListener = new PivotLockTouchListener(mSceneView, this, btnPivotLock);

    // Add compass
    mCompass = findViewById(R.id.compass);
    mCompass.setOnClickListener(onCompassClicked);

    // Start loading layers only if this build is meant to be run outside China's firewall
    if (!BuildConfig.IS_BEHIND_FIREWALL) {
      mPortal = new Portal(getString(R.string.default_portal_url));
      mPortal.addDoneLoadingListener(new Runnable() {
        @Override
        public void run() {
          int iWebSceneId = getResources().getIdentifier("esri_webscene_id", "string", getPackageName());
          if (iWebSceneId != 0) loadLayers(getString(iWebSceneId));
        }
      });
      mPortal.loadAsync();
    }

    // If below Marshmallow, permissions were granted at install time. Otherwise, ask the user now.
    if (permissionsNeeded().size() == 0)
      startListeningForLocation();
    else {
      requestPermissions(PRC_LOCATION_MOVETO);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    // pause SceneView
    mSceneView.pause();
    // pause position sensor, if active
    if (mSensorNavTouchListener != null) mSensorNavTouchListener.stopSensor();
  }

  @Override
  protected void onResume() {
    super.onResume();
    // resume SceneView
    mSceneView.resume();
    // resume position sensor, if active
    if (mSensorNavTouchListener != null) mSensorNavTouchListener.startSensor();
  }

  /**
   * Allow for hiding the north arrow when in sensor navigation mode. In that mode,
   * the device may be flipped all different directions, in which case the north arrow is
   * incorrect most of the time--at least from the user's point of view (it's correct from
   * the scene's point of view).
   * @param bVisible Whether the north arrow should be visible or not
   */
  public void setCompassVisibility(boolean bVisible) {
    mCompass.setVisibility(bVisible ? View.VISIBLE : View.INVISIBLE);
  }
  /**
   * extractBasemapLayers
   * Given a JsonArray of basemap layers, returns the first found tiled layer for inclusion in the scene.
   * <br/>
   * <b>Note:</b> OpenStreetMap comes through as its own basemap type (not ArcGISTiledMapServiceLayer).
   * @param aryLyrsJson GSON JsonArray of basemap layers from a WebScene item
   * @return Basemap for inclusion in the scene
   */
  // TODO Remove this once the SDK supports web scenes
  private Basemap extractBasemapLayers(JsonArray aryLyrsJson) {
    Basemap basemap = null;
    for (int i = 0; i < aryLyrsJson.size(); i++) {
      JsonObject jLayer = aryLyrsJson.get(i).getAsJsonObject();
      if (jLayer.get("layerType").getAsString().equals("ArcGISTiledMapServiceLayer")) {
        String sUrl = jLayer.get("url").getAsString();
        ArcGISTiledLayer tlyr = new ArcGISTiledLayer(sUrl);
        basemap = new Basemap(tlyr);
        break;
      }
    }
    return basemap;
  }

  /**
   * extractSceneLayers
   * Given a JsonArray of layers, recursively extracts and returns the ArcGISSceneLayers inside it
   * @param aryLyrsJson GSON JsonArray of layers from a WebScene item
   * @return List of ArcGISSceneLayers found in the array
   */
  // TODO Remove this once the SDK supports web scenes
  private List<Layer> extractSceneLayers(JsonArray aryLyrsJson) {
    ArrayList<Layer> aryLayers = new ArrayList<>();
    String url, name, id; float opacity; boolean visible;

    for (int iLyr = 0; iLyr < aryLyrsJson.size(); iLyr++) {
      JsonObject layerJson = aryLyrsJson.get(iLyr).getAsJsonObject();
      String sLyrType = layerJson.get("layerType").getAsString();
      switch (sLyrType) {
        case "ArcGISSceneServiceLayer":
        case "IntegratedMeshLayer":
//        case "PointCloudLayer": // Not supported yet
          url = layerJson.get("url").getAsString();
          // Some layers evidently don't specify opacity
          opacity = layerJson.has("opacity") ?
              layerJson.get("opacity").getAsFloat() : 1.0f;
          name = layerJson.get("title").getAsString();
          visible = layerJson.get("visibility").getAsBoolean();
          id = layerJson.get("id").getAsString();
          ArcGISSceneLayer lyrScene = new ArcGISSceneLayer(url);
          lyrScene.loadAsync();
          lyrScene.setOpacity(opacity);
          lyrScene.setVisible(visible);
          lyrScene.setName(name);
          lyrScene.setId(id);
          aryLayers.add(lyrScene);
          break;
        case "GroupLayer":
          List<Layer> arySubLayers = extractSceneLayers(layerJson.getAsJsonArray("layers"));
          aryLayers.addAll(arySubLayers);
      }
    }
    return aryLayers;
  }

  /**
   * Parses the JSON for a specified web scene ID.
   * @param sEsriWebsceneId The item ID for the desired web scene to parse and load
   */
  // TODO Remove this once the SDK supports web scenes
  private void loadLayers(String sEsriWebsceneId) {
    PortalItem webscene = new PortalItem(mPortal, sEsriWebsceneId);
    final ListenableFuture<InputStream> listener = webscene.fetchDataAsync();
    listener.addDoneListener(new Runnable() {
      @Override
      public void run() {
        if (listener.isDone()) {
          try {
            InputStream inStream = listener.get();
            JsonParser jsonParser = new JsonParser();
            JsonObject jsonItemData = (JsonObject)jsonParser.parse(new InputStreamReader(inStream, "UTF-8"));

            // Get basemap layers; use the first one found
            if (jsonItemData.getAsJsonObject("baseMap") != null) {
              JsonObject jBasemap = jsonItemData.getAsJsonObject("baseMap");
              if (jBasemap.getAsJsonArray("baseMapLayers") != null) {
                JsonArray jBasemaps = jBasemap.getAsJsonArray("baseMapLayers");
                Basemap basemap = extractBasemapLayers(jBasemaps);
                if (basemap != null) mSceneView.getScene().setBasemap(basemap);
              }
              // Get elevation layers
              if (jBasemap.getAsJsonArray("elevationLayers") != null) {
                JsonArray aryElevLayers = jBasemap.getAsJsonArray("elevationLayers");
                if (aryElevLayers.size() > 0) {
                  mSceneView.getScene().getBaseSurface().getElevationSources().clear();
                  for (JsonElement elevLayer : aryElevLayers) {
                    JsonObject elevLyr = elevLayer.getAsJsonObject();
                    if (elevLyr.get("layerType").getAsString().equals("ArcGISTiledElevationServiceLayer")) {
                      String sUrl = elevLyr.get("url").getAsString();
                      ArcGISTiledElevationSource elevSrc = new ArcGISTiledElevationSource(sUrl);
                      mSceneView.getScene().getBaseSurface().getElevationSources().add(elevSrc);
                    }
                  }
                }
              }
            }

            // Get scene layers
            JsonArray aryLayers = jsonItemData.getAsJsonArray("operationalLayers");
            mSceneView.getScene().getOperationalLayers().clear();

            mSceneLayers = extractSceneLayers(aryLayers);
            for (Layer lyrScene : mSceneLayers)
              mSceneView.getScene().getOperationalLayers().add(lyrScene);
            // Get slides
            JsonArray arySlides = jsonItemData.getAsJsonObject("presentation").getAsJsonArray("slides");
            mBookmarks.clear();
            for (int iSlide = 0; iSlide < arySlides.size(); iSlide++) {
              JsonObject slideJson = arySlides.get(iSlide).getAsJsonObject();
              Bookmark3D slide = new Bookmark3D(slideJson);
              mBookmarks.add(slide);
            }
            // Get initial camera position
            JsonObject jsonInitialCam = jsonItemData.getAsJsonObject("initialState")
                .getAsJsonObject("viewpoint").getAsJsonObject("camera");
            Camera initialCam = CameraSerialization.CameraFromJSON(jsonInitialCam);
            mSceneView.setViewpointCameraAsync(initialCam, 3);
          } catch (InterruptedException | ExecutionException | UnsupportedEncodingException e) {
            Log.e(TAG, "Exception parsing webscene: " + e.getLocalizedMessage());
            e.printStackTrace();
          }

        }
      }
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.action_bar, menu);
    mTBItems = menu;

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() != R.id.mnuTapAction && item.getItemId() != R.id.mnuTapViewshed)
      mLytViewshedDist.setVisibility(View.INVISIBLE);

    switch (item.getItemId()) {
      case R.id.mnuGpsLoc:
        moveToCurrentLocation();
        return true;
      case R.id.mnuLayers:
        showLayers();
        return true;
      case R.id.mnuBookmarks:
        showBookmarks();
        return true;
      case R.id.mnuTapStandardNavigation:
        cleanupNavTouchListeners();
        mSceneView.setOnTouchListener(new DefaultSceneViewOnTouchListener(mSceneView));
        mTBItems.findItem(R.id.mnuTapAction).setIcon(item.getIcon());
        return true;
      case R.id.mnuTapSensorNavigation:
        cleanupNavTouchListeners();
        mSceneView.setOnTouchListener(mSensorNavTouchListener);
        mSensorNavTouchListener.set_isActiveListener(true);
        mTBItems.findItem(R.id.mnuTapAction).setIcon(item.getIcon());
        return true;
      case R.id.mnuTapMeasure:
        cleanupAllTouchListeners();
        mSceneView.setOnTouchListener(mMeasureTouchListener);
        mTBItems.findItem(R.id.mnuTapAction).setIcon(item.getIcon());
        return true;
      case R.id.mnuTapLineOfSight:
        cleanupAllTouchListeners();
        mSceneView.setOnTouchListener(mLineOfSightTouchListener);
        mTBItems.findItem(R.id.mnuTapAction).setIcon(item.getIcon());
        return true;
      case R.id.mnuTapViewshed:
        cleanupAllTouchListeners();
        mSceneView.setOnTouchListener(mViewshedTouchListener);
        mLytViewshedDist.setVisibility(View.VISIBLE);
        mTBItems.findItem(R.id.mnuTapAction).setIcon(item.getIcon());
        mLytViewshedDist.setVisibility(View.VISIBLE);
        return true;
      case R.id.mnuPivotLock:
        cleanupNavTouchListeners();
        mSceneView.setOnTouchListener(mPivotLockTouchListener);
        mTBItems.findItem(R.id.mnuTapAction).setIcon(item.getIcon());
        mPivotLockTouchListener.initialize();
        return true;
      case R.id.mnuCleanupGraphics:
        mViewshedTouchListener.cleanup();
        mMeasureTouchListener.cleanup();
        mLineOfSightTouchListener.cleanup();
        return true;
      case R.id.mnuOpenWebScene:
        getAGOLWebscenesList();
        return true;
      default:
        return super.onOptionsItemSelected(item);

    }
  }
  private void cleanupAllTouchListeners() {
    mMeasureTouchListener.cleanup();
    mLineOfSightTouchListener.cleanup();
    mViewshedTouchListener.cleanup();
    mSensorNavTouchListener.cleanup();
    mPivotLockTouchListener.cleanup();
  }

  private void cleanupNavTouchListeners() {
    mSensorNavTouchListener.cleanup();
    mPivotLockTouchListener.cleanup();
  }

  private void showBookmarks() {
    if (mBookmarks.size() > 0) { // present a list
      AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);

      String[] saBookmarks = new String[mBookmarks.size()];
      for (int iBm = 0; iBm < mBookmarks.size(); iBm++) {
        saBookmarks[iBm] = mBookmarks.get(iBm).get_title();
      }
      getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null, false);
      adb.setTitle(R.string.tb_btn_bookmarks)
          .setItems(saBookmarks, onBookmarkChosen)
          .show();
    } else { // no bookmarks
      Toast toast = Toast.makeText(this, R.string.msg_no_bookmarks, Toast.LENGTH_LONG);
      toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
      toast.show();
    }
  }

  private DialogInterface.OnClickListener onBookmarkChosen = new DialogInterface.OnClickListener() {
    @Override
    public void onClick(DialogInterface dialog, int which) {
      Bookmark3D bookmark = mBookmarks.get(which);
      Log.d(TAG, bookmark.get_title());
      moveToBookmark(bookmark);
    }
  };

  /**
   * Presents a list of layers in the webscene that can be enabled or disabled.
   */
  private void showLayers() {
    AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);

    String[] saLyrs = new String[mSceneLayers.size()];
    boolean[] baLyrVis = new boolean[mSceneLayers.size()];
    for (int iLyr = 0; iLyr < mSceneLayers.size(); iLyr++) {
      Layer lyr = mSceneLayers.get(iLyr);
      saLyrs[iLyr] = lyr.getName() != null ? lyr.getName() : getString(R.string.unnamed_layer);
      baLyrVis[iLyr] = lyr.isVisible();
    }
    getLayoutInflater().inflate(android.R.layout.simple_list_item_multiple_choice,
        null, false);
    adb.setTitle(R.string.tb_btn_layers)
        .setMultiChoiceItems(saLyrs, baLyrVis, onLayerToggled)
        .show();
  }

  /**
   * Log in to AGOL or a portal.
   * Obtain and present a list of webscenes available to the specified user account.
   */
  private void getAGOLWebscenesList() {
    final String PREF_PORTAL_URL = "pref_portal_url";

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    final EditText txtUrl = new EditText(this);
    txtUrl.setLines(1); txtUrl.setMaxLines(1);
    txtUrl.setInputType(InputType.TYPE_TEXT_VARIATION_URI);

    txtUrl.setText(prefs.getString(PREF_PORTAL_URL, getString(R.string.default_portal_url)));
    new AlertDialog.Builder(this)
        .setTitle(R.string.dlg_title_portal_url)
        .setView(txtUrl)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            AuthenticationManager.setAuthenticationChallengeHandler(
                new DefaultAuthenticationChallengeHandler(MainActivity.this));

            final String sUrl = txtUrl.getText().toString();
            if (!URLUtil.isValidUrl(sUrl)) {
              dialog.dismiss();
              Toast toast = Toast.makeText(MainActivity.this, R.string.msg_invalid_portal_url, Toast.LENGTH_LONG);
              toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
              toast.show();
              return;
            }
            // Note that mPortal was loaded up at the end of onCreate()
            mPortal = new Portal(sUrl, true);
            mPortal.addDoneLoadingListener(new Runnable() {
              @Override
              public void run() {
                if (mPortal.getLoadStatus() != LoadStatus.LOADED) {
                  String msg;
                  if (Locale.getDefault().getLanguage().equals(Locale.ENGLISH.getLanguage())) {
                    msg = mPortal.getLoadError().getCause().getLocalizedMessage();
                    if (mPortal.getLoadError().getAdditionalMessage() != null)
                      msg += "; " + mPortal.getLoadError().getAdditionalMessage();
                  } else { // Non-English language; use string resource
                    msg = getString(R.string.err_portal_user_problem);
                  }
                  AlertDialog ad = new AlertDialog.Builder(MainActivity.this)
                      .setMessage(msg)
                      .setPositiveButton(android.R.string.ok, null)
                      .create();
                  ad.show();
                  return;
                }

                // save the url for next time
                prefs.edit().putString(PREF_PORTAL_URL, sUrl).apply();

                final PortalUser user = mPortal.getUser();
                final ListenableFuture<PortalUserContent> contentFuture = user.fetchContentAsync();
                contentFuture.addDoneListener(new Runnable() {
                  @Override
                  public void run() {
                    try {
                      // fetch the content in the users root folder
                      final List<PortalItem> webscenes = new ArrayList<>();
                      //iterate items in root folder...
                      final PortalUserContent portalUserContent = contentFuture.get();
                      for (PortalItem item : portalUserContent.getItems()) {
                        if (item.getType().equals(PortalItem.Type.WEB_SCENE))
                          webscenes.add(item);
                      }
                      //iterate user's folders
                      List<Map.Entry<String, ListenableFuture<List<PortalItem>>>> folderItemResults = new ArrayList<>();
                      for (PortalFolder folder : portalUserContent.getFolders()) {
                        //fetch the items in each folder
                        final ListenableFuture<List<PortalItem>> folderFuture = user.fetchContentInFolderAsync(folder.getFolderId());
                        folderItemResults.add(
                            new AbstractMap.SimpleEntry<>(
                                folder.getTitle(), folderFuture));
                      }
                      for (Map.Entry<String, ListenableFuture<List<PortalItem>>> folderItemResult : folderItemResults) {
                        try {
                          // Use synchronous, blocking get() to wait on all portal item results
                          List<PortalItem> folderItems = folderItemResult.getValue().
                              get(2000, TimeUnit.MILLISECONDS);
                          for (PortalItem folderItem : folderItems)
                            if (folderItem.getType().equals(PortalItem.Type.WEB_SCENE))
                              webscenes.add(folderItem);
                        } catch (Exception exc) {
                          Log.i(TAG, "Error getting items in folder '" + folderItemResult.getKey() +
                              "': " + exc.getLocalizedMessage());
                        }
                      }

                      if (webscenes.size() > 0) {
                        String[] saWebscenes = new String[webscenes.size()];
                        for (int i = 0; i < webscenes.size(); i++)
                          saWebscenes[i] = webscenes.get(i).getTitle();
                        DialogInterface.OnClickListener onWebsceneChosen = new DialogInterface.OnClickListener() {
                          @Override
                          public void onClick(DialogInterface dialog, int which) {
                            PortalItem item = webscenes.get(which);
                            loadLayers(item.getItemId());
                          }
                        };
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.dlg_title_choose_webscene)
                            .setItems(saWebscenes, onWebsceneChosen)
                            .show();
                      } else {
                        Toast toast =
                            Toast.makeText(MainActivity.this, R.string.msg_no_webscenes, Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        toast.show();
                      }
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                  }

                }); // End Runnable
              }
            });
            mPortal.loadAsync();
          }
        })
        .show();
  }
  private DialogInterface.OnMultiChoiceClickListener onLayerToggled = new DialogInterface.OnMultiChoiceClickListener() {
    @Override
    public void onClick(DialogInterface dialogInterface, int i, boolean b) {
      Layer lyr = mSceneLayers.get(i);
      lyr.setVisible(b);
    }
  };

  /**
   * Move the scene to the location specified by the device's GPS.
   * Note that if the device has a GPS, only a single reading was taken on startup. That's
   * the location this method uses.
   */
  private void moveToCurrentLocation() {
    if (mLocation != null) {
      // Location comes in lat/lon, so WGS84
      Point pt = new Point(mLocation.getLongitude(), mLocation.getLatitude(), SpatialReferences.getWgs84());
      ListenableFuture<Double> lfElev = mSceneView.getScene().getBaseSurface().getElevationAsync(pt);
      lfElev.addDoneListener(new Runnable() {
        @Override
        public void run() {
          // Use Surface elevation if possible; or device GPS altitude; or 2m if all else fails
          double elev = 2d;
          try {
            elev = mLocation.getAltitude();
            if (lfElev.isDone() && !lfElev.isCancelled()) elev = lfElev.get() + 2d; // add 2m
          } catch (Exception e) {
            e.printStackTrace();
          }
          Camera cam = new Camera(mLocation.getLatitude(), mLocation.getLongitude(), elev, mLocation.getBearing(), 80, 0);
          mSceneView.setViewpointCameraAsync(cam, 3.0f);
        }
      });
    } else {
      Toast toast = Toast.makeText(MainActivity.this, R.string.err_location_invalid,
          Toast.LENGTH_LONG);
      toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
      toast.show();
    }
  }
  private void moveToBookmark(Bookmark3D bookmark) {
    if (bookmark != null) {
      // Set visible layers
      for (Layer lyr : mSceneLayers) {
        lyr.setVisible(bookmark.get_visibleLayerIds().contains(lyr.getId()));
      }

      // Disable sensor navigation
      revertToStandardNavigation();
      cleanupAllTouchListeners();

      // Pan/zoom
      Camera cam = bookmark.get_camera();
      mSceneView.setViewpointCameraAsync(cam, 4.0f);
    }
  }

  /** Utility for certain touch listeners to revert to standard navigation mode */
  public void revertToStandardNavigation() {
    MenuItem stdNav = mTBItems.findItem(R.id.mnuTapStandardNavigation);
    onOptionsItemSelected(stdNav);
  }

  /** Set the north arrow image orientation */
  private ViewpointChangedListener onViewpointChanged = new ViewpointChangedListener() {
    @Override
    public void viewpointChanged(ViewpointChangedEvent viewpointChangedEvent) {
      try {
        Viewpoint viewpoint = viewpointChangedEvent.getSource()
            .getCurrentViewpoint(Viewpoint.Type.CENTER_AND_SCALE);
        float fCurrentRot = (float) viewpoint.getRotation();
        mCompass.setRotation(360f - fCurrentRot);

      } catch (Exception exc) {
        Log.e(TAG, "ViewpointChanged exception: " + exc.getMessage());
      }
    }
  };
  private View.OnClickListener onCompassClicked = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      Log.d(TAG, "Compass clicked");

      try {
        Camera cam = mSceneView.getCurrentViewpointCamera();
        Camera camNew = cam.rotateTo(0.0d, cam.getPitch(), cam.getRoll());
        mSceneView.setViewpointCameraAsync(camNew);
      } catch (Exception exc) {
        Log.e(TAG, "Error rotating view: " + exc.getLocalizedMessage());
      }
    }
  };



  private LocationListener mLocationListener = new LocationListener() {
    @Override
    public void onLocationChanged(Location location) {
      if (location != null) {
        mLocation = location;
        mLocationManager.removeUpdates(mLocationListener);
        mTBItems.findItem(R.id.mnuGpsLoc).setVisible(true);
      }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}
  };

  private void startListeningForLocation() {
    try {
      mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
      if (mLocationManager != null)
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
    } catch (SecurityException exc) {
      String msg = exc.getLocalizedMessage();
      if (exc.getCause() != null) msg += "; " + exc.getCause().getLocalizedMessage();
      Toast toast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
      toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
      toast.show();
    } catch (IllegalArgumentException exc) {
      Log.w(TAG, "This device has no GPS sensor, so cannot provide the ability to zoom to your current location.");
      // By request, don't show any error message; here's how you would do it:
/*      final String PREF_SHOW_GPS_WARNING = "pref_show_gps_warning";
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      boolean bShowGPSWarning = prefs.getBoolean(PREF_SHOW_GPS_WARNING, true);
      if (bShowGPSWarning) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.tb_btn_gpsloc)
            .setMessage("This device has no GPS sensor, so cannot provide the ability to zoom to your current location.")
            .setPositiveButton(android.R.string.ok, null)
            .show();
        prefs.edit().putBoolean(PREF_SHOW_GPS_WARNING, false).apply();
      }*/
    }
  }
  // Permissions

  /** We can handle viewshed distance changes here in the main activity, since the seekbar
   * will only be visible/changeable when the viewshed touch listener is active. Use a shared
   * preference. The viewshed touch listener can listen for changes to the shared preference
   * and take action accordingly.
   */
  private class OnViewshedDistChangeListener implements SeekBar.OnSeekBarChangeListener {
    /** Shared preferences to update when seekbar changes */
    private SharedPreferences _prefs;
    /** TextView to display new distance value */
    private TextView _txtDist;

    public OnViewshedDistChangeListener(SharedPreferences prefs, TextView txtDist) {
      super();
      this._prefs = prefs;
      this._txtDist = txtDist;
    }
    private int normalizeValue(int i) {
      i /= 200; i *= 200;
      // There's a discrepancy between setting the seekbar via touch vs programmatically.
      // As a result, positions 0 and 200 will both map to 200; all others will map to the 200 below the chosen value
      if (i == 0) i += 200;
      return i;
    }
    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
      // Since max is always zero, we have to bump every setting up by 200. That's why max=4800
      _txtDist.setText(getString(R.string.viewshed_dist_label, normalizeValue(i)));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
      int val = normalizeValue(seekBar.getProgress());
      _prefs.edit().putInt(getString(R.string.pref_viewshed_dist), val).apply();
    }
  }

  /**
   * Checks which permissions still haven't been granted for the creation of a new story map
   * @return String list of permissions still needing to be granted by the user
   */
  private List<String> permissionsNeeded() {
    List<String> permissionsStillNeeded = new ArrayList<>();

    // Request permissions to read location
    if (ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
      permissionsStillNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);

    return permissionsStillNeeded;
  }

  private void requestPermissions(int request_id) {
    // Find out which permissions are still outstanding
    List<String> permissionsNeeded = permissionsNeeded();

    // Request permissions to collect location
    if (permissionsNeeded.size() > 0)
      ActivityCompat.requestPermissions(MainActivity.this,
              permissionsNeeded.toArray(new String[permissionsNeeded.size()]),
              request_id);
  }
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    boolean allPermsGranted = true;
    switch (requestCode) {
      case PRC_LOCATION_MOVETO:
        for (int perm : grantResults)
          if (perm != PackageManager.PERMISSION_GRANTED) {
            allPermsGranted = false;
            break;
          }

        if (allPermsGranted)
          startListeningForLocation();
        else {
          // Determine whether to present rationale
          List<String> permissionRationales = new ArrayList<>();
          if (ActivityCompat.shouldShowRequestPermissionRationale(
              this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionRationales.add(getString(R.string.permission_rationale_fine_location));
          }

          if (permissionRationales.size() > 0) {
            // Not sure whether a dialog or toast is better for this...
/*            AlertDialog dlg = new AlertDialog.Builder(this)
                    .setMessage(StringUtils.join(permissionRationales, "\n\n"))
                    .setTitle("Permission Requested")
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
            dlg.show();*/
            Toast toast = Toast.makeText(this,
                StringUtils.join(permissionRationales, "\n\n"),
                Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
            toast.show();
          }
        }
        break;
    }
  }

}
