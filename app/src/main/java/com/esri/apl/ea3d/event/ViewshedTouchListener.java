package com.esri.apl.ea3d.event;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.AppCompatImageView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.esri.apl.ea3d.R;
import com.esri.arcgisruntime.ArcGISRuntimeException;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geoanalysis.LocationViewshed;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.mapping.view.AnalysisOverlay;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.DefaultSceneViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.SceneView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Perform viewshed using tap or scroll location.
 * To avoid doing viewsheds while this listener isn't active, this assumes: <ul>
 *   <li>Shared preference pref_viewshed_dist won't be changed programmatically</li>
 *   <li>Viewshed distance slider won't be visible and changeable while this touch listener is inactive</li>
 * </ul>
 */
public class ViewshedTouchListener extends DefaultSceneViewOnTouchListener implements TouchListenerFinalizable {
  private static final String TAG = "ViewshedListener";

  private SceneView mSceneView;
  private FloatingActionButton mBtnZoomToViewshed, mBtnReturnToCamera;
  private AppCompatImageView mNavModeIndicator;

  private AnalysisOverlay mAnalyses = new AnalysisOverlay();

  private LocationViewshed mLv1;
  private Point mViewshedPoint;

  /** Make a note of where we were when we zoomed into the viewshed result */
  private Camera mObserverCamera;
  private Camera mViewshedOriginCamera;

  /** Is a viewshed calculation currently in progress? */
  private final static AtomicBoolean mCurrentlyProcessing = new AtomicBoolean(false);

  /** Move the viewshed or pan the view during finger scroll?
   *  True: pan the view
   *  False: move the viewshed */
  private final static AtomicBoolean mScrollModePanView = new AtomicBoolean(false);

  public ViewshedTouchListener(SceneView sceneView, FloatingActionButton btnZoomToViewshed,
                               FloatingActionButton btnReturnToCamera, AppCompatImageView navModeIndicator) {
    super(sceneView);
    this.mSceneView = sceneView;
    mSceneView.getAnalysisOverlays().add(mAnalyses);

    this.mBtnZoomToViewshed = btnZoomToViewshed;
    mBtnZoomToViewshed.setOnClickListener(mZoomToViewshedClickListener);
    this.mBtnReturnToCamera = btnReturnToCamera;
    mBtnReturnToCamera.setOnClickListener(mReturnToCameraClickListener);

    this.mNavModeIndicator = navModeIndicator;

    /* Listen to shared preferences changing when the user changes viewshed dist slider */
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mSceneView.getContext());
    prefs.registerOnSharedPreferenceChangeListener(onPrefsChanged);
  }

  @Override
  public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
    Log.d(TAG, "onSingleTapConfirmed");
    findViewshedsFromScreenPoint(motionEvent);
    return true;
  }

  /**
   * Start the viewshed analysis by finding where on the screen the user tapped
   * @param motionEvent Standard screen tap event containing screen point
   */
  private void findViewshedsFromScreenPoint(MotionEvent motionEvent) {
    android.graphics.Point screenPoint = new android.graphics.Point();
    screenPoint.set((int) motionEvent.getX(), (int) motionEvent.getY());

    final int observerHeight = mSceneView.getContext().getResources()
        .getInteger(R.integer.geoanalysis_observer_height_m);

    final ListenableFuture<Point> lfDone = mSceneView.screenToLocationAsync(screenPoint);
    lfDone.addDoneListener(() -> {
      if (lfDone.isDone()) {
        try {
          final Point pt = lfDone.get();
          mViewshedPoint = new Point(pt.getX(), pt.getY(), pt.getZ() + observerHeight,
                  pt.getSpatialReference());
          // Start the second part of the analysis now that we know where on earth they tapped
          computeViewshedsFromGeoPoint();
        } catch (Exception exc) {
          Log.e(TAG, "Exception: " + exc.getMessage());
          exc.printStackTrace();
        }
      }
    });
  }

  /** Perform the viewshed analyses. There are three to make a complete circle.
   * <b>Note:</b> Don't create a new analysis for each tap; reuse the old ones where possible.
   */
  private void computeViewshedsFromGeoPoint() {
    Context ctx = mSceneView.getContext();

    // If analysis size stored in prefs, use it; otherwise use the default
    int iDist = PreferenceManager.getDefaultSharedPreferences(ctx)
        .getInt(ctx.getString(R.string.pref_viewshed_dist),
            ctx.getResources().getInteger(R.integer.setting_initial_viewshed_dist_m));

    // Don't try another if the first one's still running
    if (mCurrentlyProcessing.get()) return;
    mCurrentlyProcessing.set(true);
    try {
      // Currently each viewshed is restricted to a 120-degree angle, so do three for
      // a complete circle.
      if (mLv1 == null) {
        mLv1 = new LocationViewshed(mViewshedPoint, 0, 90, 360, 120, 0, iDist);
      }
      else {
        mLv1.setLocation(mViewshedPoint);
        mLv1.setMaxDistance(iDist);
      }
      if (!mAnalyses.getAnalyses().contains(mLv1)) {
        mAnalyses.getAnalyses().add(mLv1);
        Log.d(TAG, "added analysis overlay");
      }

      mViewshedOriginCamera = new Camera(mViewshedPoint, mSceneView.getCurrentViewpointCamera().getHeading(), 100, 0);
      mBtnReturnToCamera.setVisibility(View.INVISIBLE);
      mBtnZoomToViewshed.setVisibility(View.VISIBLE);
    } catch (ArcGISRuntimeException exc) {
      Log.e(TAG,"Exception: " + exc.getMessage() + "; " + exc.getAdditionalMessage());
      exc.printStackTrace();
    } catch (Exception exc) {
      Log.e(TAG, "Exception: " + exc.getMessage());
    } finally {
      mCurrentlyProcessing.set(false);
    }
  }

  /** Dragging should also create a new viewshed analysis for an interactive rolling effect */
  @Override
  public boolean onScroll(MotionEvent motionEventFrom, MotionEvent motionEventTo, float distanceX, float distanceY) {
    Log.d(TAG, "Scroll");
    if (!mCurrentlyProcessing.get()) {
      // Pan mode? Pan the view.
      if (get_panEnabled())
        super.onScroll(motionEventFrom, motionEventTo, distanceX, distanceY);
      else // Viewshed mode? Create new viewsheds.
        findViewshedsFromScreenPoint(motionEventTo);
    }
    return true;
  }

  @Override
  public void onLongPress(MotionEvent motionEvent) {
    Log.d(TAG, "LongPress");
    set_panEnabled(true);
  }

  @Override
  public boolean onSinglePointerUp(MotionEvent motionEvent) {
    Log.d(TAG, "SinglePointerUp");
    set_panEnabled(false);
    return super.onSinglePointerUp(motionEvent);
  }

  private void set_panEnabled(boolean bPan) {
    mScrollModePanView.set(bPan);
    mNavModeIndicator.setVisibility(bPan ? View.VISIBLE : View.INVISIBLE);
  }
  private boolean get_panEnabled() {
    return mScrollModePanView.get();
  }

  private View.OnClickListener mZoomToViewshedClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      if (mViewshedOriginCamera != null) {
        mObserverCamera = mSceneView.getCurrentViewpointCamera();
        mSceneView.setViewpointCameraAsync(mViewshedOriginCamera);
        mBtnZoomToViewshed.setVisibility(View.INVISIBLE);
        mBtnReturnToCamera.setVisibility(View.VISIBLE);
      }
    }
  };

  private View.OnClickListener mReturnToCameraClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      if (mObserverCamera != null) {
        mSceneView.setViewpointCameraAsync(mObserverCamera);
        mBtnReturnToCamera.setVisibility(View.INVISIBLE);
        mBtnZoomToViewshed.setVisibility(View.VISIBLE);
      }
    }
  };

  private SharedPreferences.OnSharedPreferenceChangeListener onPrefsChanged = new SharedPreferences.OnSharedPreferenceChangeListener() {
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
      Context ctx = mSceneView.getContext();

      // Make sure it was the viewshed pref that changed and that there exists an analysis point
      if  (s.equals(ctx.getString(R.string.pref_viewshed_dist))
        && mViewshedPoint != null) {
        computeViewshedsFromGeoPoint();
        Log.d(TAG, ctx.getString(R.string.viewshed_dist_label,
            sharedPreferences.getInt(
                s, ctx.getResources().getInteger(R.integer.setting_initial_viewshed_dist_m))));
      }
    }
  };


  @Override
  public void cleanup() {
    mAnalyses.getAnalyses().clear();
    mLv1 = null;
    mBtnReturnToCamera.setVisibility(View.INVISIBLE);
    mBtnZoomToViewshed.setVisibility(View.INVISIBLE);
  }
}
