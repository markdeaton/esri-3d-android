package com.esri.apl.ea3d.event;

import android.graphics.Color;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.esri.apl.ea3d.MainActivity;
import com.esri.apl.ea3d.R;
import com.esri.apl.ea3d.util.GeometryUtils;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.CameraController;
import com.esri.arcgisruntime.mapping.view.DefaultSceneViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.OrbitLocationCameraController;
import com.esri.arcgisruntime.mapping.view.SceneView;
import com.esri.arcgisruntime.symbology.SceneSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSceneSymbol;

import java.util.Timer;
import java.util.TimerTask;

/** Use the Orbit camera controller to rotate around and around a point of interest */
public class PivotLockTouchListener extends DefaultSceneViewOnTouchListener implements TouchListenerFinalizable {
  private final static String TAG = "PivotLock";
  private static final double PIVOT_CYL_SYMBOL_RADIUS = 2;
  private static final double PIVOT_CYL_SYMBOL_HEIGHT = 250;
  private static final double PIVOT_PT_SYMBOL_RADIUS = 10;

  // Orbit speed & angle variables
  private static final int FRAME_RATE = 48; // Frames per second while orbiting
  private static final long ORBIT_TIME_SEC = 20;
  private static double ORBIT_DEGREES = 360;
  private static double ORBIT_DEGREES_PER_SEC = ORBIT_DEGREES / ORBIT_TIME_SEC;
  // This is really the only one of these constants we need; see startPivot().
  private static double ORBIT_DEGREES_PER_FRAME = ORBIT_DEGREES_PER_SEC / FRAME_RATE;

  private boolean _isOrbiting = false;

  private SceneView mSceneView;
  private MainActivity mMainActivity;

  private FloatingActionButton mBtnEndPivot;
  private Point mPivotPoint;
  /** Distance between observation point and proposed new pivot point on the ground */
  private double mDist;

  private CameraController mOriginalCameraController;

  private GraphicsOverlay mGraphics = new GraphicsOverlay();
  private final SimpleMarkerSceneSymbol mSymbolPoint = new SimpleMarkerSceneSymbol(
      SimpleMarkerSceneSymbol.Style.SPHERE, Color.YELLOW, PIVOT_PT_SYMBOL_RADIUS, PIVOT_PT_SYMBOL_RADIUS,
      PIVOT_PT_SYMBOL_RADIUS, SceneSymbol.AnchorPosition.BOTTOM);
  private final SimpleMarkerSceneSymbol mSymbolCylinder = new SimpleMarkerSceneSymbol(
      SimpleMarkerSceneSymbol.Style.CYLINDER, Color.YELLOW, PIVOT_CYL_SYMBOL_RADIUS, PIVOT_CYL_SYMBOL_HEIGHT,
      PIVOT_CYL_SYMBOL_RADIUS, SceneSymbol.AnchorPosition.BOTTOM);

  // Orbit animation timer
  private Timer mOrbitTimer;

  // Only show help toast once per session
  private boolean _showHelp = true;

  public PivotLockTouchListener(SceneView sceneView, MainActivity mainActivity,
                                FloatingActionButton btnEndPivot) {
    super(sceneView);
    this.mSceneView = sceneView;
    this.mMainActivity = mainActivity;
    this.mBtnEndPivot = btnEndPivot;
    btnEndPivot.setOnClickListener(mOnPivotEndListener);
    this.mOriginalCameraController = sceneView.getCameraController();

    sceneView.getGraphicsOverlays().add(mGraphics);
  }

  public void initialize() {
    if (_showHelp) {
      _showHelp = false;
      Toast toast = Toast.makeText(mSceneView.getContext(),
          R.string.msg_start_pivot_mode,
          Toast.LENGTH_LONG);
      toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
      toast.show();
    }
  }

  /**
   * Tap point is location to orbit around
   *
   * @param motionEvent Event object containing the screen tap point
   * */
  @Override
  public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
    boolean wasOrbiting = _isOrbiting;
    stopPivotAnimation();
    if (wasOrbiting) return true;

    // Get tap location
    android.graphics.Point screenPoint = new android.graphics.Point();
    screenPoint.set((int)motionEvent.getX(), (int)motionEvent.getY());

    final ListenableFuture<Point> lfGetLoc = mSceneView.screenToLocationAsync(screenPoint);
    lfGetLoc.addDoneListener(new Runnable() {
      @Override
      public void run() {
        if (lfGetLoc.isDone()) {
          try {
            mPivotPoint = lfGetLoc.get();
            Point ptPivotWM = (Point) GeometryEngine.project(mPivotPoint, SpatialReferences.getWebMercator());

            // Get camera distance from tap location, for proper camera placement when orbiting
            Point ptCam = mSceneView.getCurrentViewpointCamera().getLocation();
            Point ptCamWM = (Point)GeometryEngine.project(ptCam, SpatialReferences.getWebMercator());

            mDist = GeometryUtils.distance(ptCamWM, ptPivotWM);

            startPivot();

            mGraphics.getGraphics().clear();
            Graphic g = new Graphic(mPivotPoint, mSymbolCylinder);
            mGraphics.getGraphics().add(g);
          } catch (Exception exc) {
            Log.e(TAG, "Exception getting location", exc);
          }
        }
      }
    });
    return true;
  }

  /** Allow the user to drag around a graphic for the prospective pivot location
   *
   * @param motionEventFrom Drag origin point
   * @param motionEventTo Drag dstination point
   * @param distanceX Drag horizontal distance
   * @param distanceY Drag vertical distance
   * @return Whether the drag/scroll action was handled by our custom code
   */
  @Override
  public boolean onScroll(MotionEvent motionEventFrom, MotionEvent motionEventTo, float distanceX, float distanceY) {
    stopPivotAnimation();
    // Get tap location

    android.graphics.Point screenPoint = new android.graphics.Point();
    screenPoint.set((int)motionEventTo.getX(), (int)motionEventTo.getY());

    final ListenableFuture<Point> lfGetLoc = mSceneView.screenToLocationAsync(screenPoint);
    lfGetLoc.addDoneListener(new Runnable() {
      @Override
      public void run() {
        if (lfGetLoc.isDone()) {
          try {
            mPivotPoint = lfGetLoc.get();

            if (!_isOrbiting) {
              mGraphics.getGraphics().clear();
              Graphic g = new Graphic(mPivotPoint, mSymbolPoint);
              mGraphics.getGraphics().add(g);
              Log.d(TAG, "Placed point");
            }
          } catch (Exception exc) {
            Log.e(TAG, "Exception getting location", exc);
          }
        }
      }
    });
    return true;
  }

  /** When drag motion is released, begin the pivot operation */
  @Override
  public boolean onFling() {
    // Make a note of the current camera controller
    Point ptPivotWM = (Point)GeometryEngine.project(mPivotPoint, SpatialReferences.getWebMercator());

    // Get camera distance from tap location
    Point ptCam = mSceneView.getCurrentViewpointCamera().getLocation();
    Point ptCamWM = (Point)GeometryEngine.project(ptCam, SpatialReferences.getWebMercator());

    mDist = GeometryUtils.distance(ptCamWM, ptPivotWM);

    startPivot();

    mGraphics.getGraphics().clear();
    Graphic g = new Graphic(mPivotPoint, mSymbolCylinder);
    mGraphics.getGraphics().add(g);
    Log.d(TAG, "Placed cylinder");

    return true;
  }

  private void startPivot() {
    OrbitLocationCameraController newController =
        new OrbitLocationCameraController(mPivotPoint, mDist);
    Log.d(TAG, "Orbit dist: " + mDist);
    mSceneView.setCameraController(newController);

    // Use a timer to move the view by the proper degrees every frame
    TimerTask tt = new TimerTask() {
      @Override
      public void run() {
        Camera newCam = mSceneView.getCurrentViewpointCamera().rotateAround(
            mPivotPoint, ORBIT_DEGREES_PER_FRAME, 0, 0);
        mSceneView.setViewpointCameraAsync(newCam, 0);
      }
    };
    mOrbitTimer = new Timer();
    mOrbitTimer.schedule(tt, 0, 1000/FRAME_RATE);
    _isOrbiting = true;
    mBtnEndPivot.setVisibility(View.VISIBLE);
  }

  /** Stop the auto-rotation associated with a new pivot lock. Leave the lock in place for
   *  manual navigation.
   */
  private void stopPivotAnimation() {
    if (mOrbitTimer != null) mOrbitTimer.cancel();
    _isOrbiting = false;
  }

  private View.OnClickListener mOnPivotEndListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      mMainActivity.revertToStandardNavigation();
      cleanup();
    }
  };

  @Override
  public void cleanup() {
    stopPivotAnimation();
    mGraphics.getGraphics().clear();
    mBtnEndPivot.setVisibility(View.INVISIBLE);
    mSceneView.setCameraController(mOriginalCameraController);
  }
}
