package com.esri.apl.ea3d.event;

import android.util.Log;
import android.view.MotionEvent;

import com.esri.apl.ea3d.R;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geoanalysis.LineOfSight;
import com.esri.arcgisruntime.geoanalysis.LocationLineOfSight;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.mapping.view.AnalysisOverlay;
import com.esri.arcgisruntime.mapping.view.DefaultSceneViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.SceneView;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Do line-of-sight analysis from touched/dragged locations.
 */
public class LineOfSightTouchListener extends DefaultSceneViewOnTouchListener implements TouchListenerFinalizable {
  private static final String TAG = "LOSListener";

  private SceneView mSceneView;
  private AnalysisOverlay mAnalysis = new AnalysisOverlay();
  private LocationLineOfSight mLOS;

  private final AtomicReference<Point> ptFrom = new AtomicReference<>();

  public LineOfSightTouchListener(SceneView sceneView) {
    super(sceneView);
    this.mSceneView = sceneView;
    mSceneView.getAnalysisOverlays().add(mAnalysis);
  }

  /** Use initial tap location as analysis starting point */
  @Override
  public boolean onSinglePointerDown(MotionEvent motionEvent) {
    Log.d(TAG, "Single pointer down");

    mAnalysis.getAnalyses().clear();

    final int observerHeight = mSceneView.getContext().getResources()
        .getInteger(R.integer.geoanalysis_observer_height_m);

    android.graphics.Point screenPointFrom = new android.graphics.Point();
    screenPointFrom.set((int)motionEvent.getX(), (int)motionEvent.getY());
    final ListenableFuture<Point> lfDoneFrom = mSceneView.screenToLocationAsync(screenPointFrom);
    lfDoneFrom.addDoneListener(new Runnable() {
      @Override
      public void run() {
        if (lfDoneFrom.isDone()) {
          try {
            Point ptObs = lfDoneFrom.get();
            // Add to observer point height to simulate observer eye height
            ptFrom.set(new Point(ptObs.getX(), ptObs.getY(), ptObs.getZ() + observerHeight,
                ptObs.getSpatialReference()));
          } catch (Exception exc) {
            exc.printStackTrace();
          }
        }
      }
    });
    return false;
  }

  /** Use drag point as analysis end point */
  @Override
  public boolean onScroll(MotionEvent motionEventFrom, MotionEvent motionEventTo, float distanceX, float distanceY) {
    Log.d(TAG, "Scroll");

    android.graphics.Point screenPointTo = new android.graphics.Point();
    screenPointTo.set((int)motionEventTo.getX(), (int)motionEventTo.getY());


    final ListenableFuture<Point> lfDoneTo = mSceneView.screenToLocationAsync(screenPointTo);
    lfDoneTo.addDoneListener(new Runnable() {
      @Override
      public void run() {
        if (lfDoneTo.isDone()) {
          try {
            Point ptTo = lfDoneTo.get();

            if (ptFrom.get() != null && ptTo != null) {
              if (mAnalysis.getAnalyses().size() <= 0) { // Create it
                mLOS = new LocationLineOfSight(ptFrom.get(), ptTo);
                LineOfSight.setLineWidth(3);
                mAnalysis.getAnalyses().add(mLOS);
              } else { // Just update the points
                mLOS.setTargetLocation(ptTo);
              }
            }
          } catch (Exception exc) {
            exc.printStackTrace();
          }
        }
      }
    });
    return true;
  }

  @Override
  public void cleanup() {
    mAnalysis.getAnalyses().clear();
    ptFrom.set(null);
  }
}
