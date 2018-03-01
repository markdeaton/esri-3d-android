package com.esri.apl.ea3d.event;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;

import com.esri.apl.ea3d.R;
import com.esri.apl.ea3d.util.GeometryUtils;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointCollection;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.mapping.view.DefaultSceneViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LayerSceneProperties;
import com.esri.arcgisruntime.mapping.view.SceneView;
import com.esri.arcgisruntime.symbology.SceneSymbol;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSceneSymbol;
import com.esri.arcgisruntime.symbology.TextSymbol;

import java.util.Arrays;

/**
 * Helps perform point-to-point measurement on tap
 */
public class MeasurementTouchListener extends DefaultSceneViewOnTouchListener implements TouchListenerFinalizable {
  private static final String TAG = "MeasureListener";

  private SceneView mSceneView;
  private final GraphicsOverlay mGraphics = new GraphicsOverlay();
  private final SimpleMarkerSceneSymbol mEndMarkerSymbol = new SimpleMarkerSceneSymbol(
      SimpleMarkerSceneSymbol.Style.DIAMOND,
      Color.RED,
      18d, 18d, 18d,
      SceneSymbol.AnchorPosition.CENTER);
  private final SimpleMarkerSceneSymbol mStartMarkerSymbol = new SimpleMarkerSceneSymbol(
      SimpleMarkerSceneSymbol.Style.DIAMOND,
      Color.GREEN,
      18d, 18d, 18d,
      SceneSymbol.AnchorPosition.BOTTOM);
  private final SimpleLineSymbol mDistanceLineSymbol = new SimpleLineSymbol(
      SimpleLineSymbol.Style.SOLID, Color.YELLOW, 9f);


  public MeasurementTouchListener(SceneView sceneView) {
    super(sceneView);
    this.mSceneView = sceneView;
    mSceneView.getGraphicsOverlays().add(mGraphics);
  }

  public void cleanup() {
    clearAllTargetGraphics();
  }

  /** Single tap location is measurement destination (observer point is origin) */
  @Override
  public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
    android.graphics.Point screenPoint = new android.graphics.Point();
    screenPoint.set((int)motionEvent.getX(), (int)motionEvent.getY());

    final ListenableFuture<Point> lfDone = mSceneView.screenToLocationAsync(screenPoint);
    lfDone.addDoneListener(new Runnable() {
      @Override
      public void run() {
        if (lfDone.isDone()) {
          try {
            Context ctx = mSceneView.getContext();

            Point ptCameraWGS = mSceneView.getCurrentViewpointCamera().getLocation();
            Point ptTargetWGS = lfDone.get();
            Point ptCameraWM = (Point) GeometryEngine.
                project(ptCameraWGS, SpatialReferences.getWebMercator());
            Point ptTargetWM = (Point) GeometryEngine.project(ptTargetWGS, SpatialReferences.getWebMercator());

            // Calculate distance, bearing from camera
            double dist = GeometryUtils.distance(ptCameraWM, ptTargetWM);
            double heading = bearing(ptCameraWGS, ptTargetWGS);
            Log.d(TAG,"Heading: " + String.format("%f.1", heading));

            clearAllTargetGraphics();
            mGraphics.getSceneProperties().setSurfacePlacement(
                LayerSceneProperties.SurfacePlacement.ABSOLUTE);

            // Add from, to marker graphics
            Graphic gTo = new Graphic();
            gTo.setGeometry(ptTargetWM);
            gTo.setSymbol(mEndMarkerSymbol);
            mGraphics.getGraphics().add(gTo);

            Graphic gFrom = new Graphic();
            gFrom.setGeometry(ptCameraWM);
            gFrom.setSymbol(mStartMarkerSymbol);
            mGraphics.getGraphics().add(gFrom);

            // Add line to target
            Point aryPts[] = { ptCameraWM, ptTargetWM };
            Polyline pl = new Polyline(new PointCollection(Arrays.asList(aryPts)));
            Graphic gLine = new Graphic(pl, mDistanceLineSymbol);
            mGraphics.getGraphics().add(gLine);

            // Add distance text to target
            Point ptMid = midpoint(ptCameraWM, ptTargetWM);
            String sDistLabel = ctx.getString(R.string.distance_line_label, dist, heading);
            TextSymbol symText = new TextSymbol(24, sDistLabel, Color.CYAN,
                TextSymbol.HorizontalAlignment.RIGHT, TextSymbol.VerticalAlignment.BOTTOM);
            symText.setOutlineColor(Color.BLACK); symText.setOutlineWidth(5f);
            Graphic gText = new Graphic();
            gText.setSymbol(symText);
            gText.setGeometry(ptMid);
            mGraphics.getGraphics().add(gText);

            Log.d(TAG, "Got the point");
          } catch (Exception e) {
            Log.e(TAG, "Error finding or displaying measurement results.", e);
          }
        }
      }
    });
    return true;
  }

  /**
   * Calculate bearing from camera to target point
   * @param from camera/observer point (WGS84)
   * @param to target/destination point (WGS84)
   * @return bearing in degrees
   * @see <a href="https://www.movable-type.co.uk/scripts/latlong.html">https://www.movable-type.co.uk/scripts/latlong.html</a>
   */
  private double bearing(Point from, Point to) {
    double lonDiff = to.getX() - from.getX();
    double y = Math.sin(Math.toRadians(lonDiff)) * Math.cos(Math.toRadians(to.getY()));
    double x = Math.cos(Math.toRadians(from.getY())) * Math.sin(Math.toRadians(to.getY())) -
               Math.sin(Math.toRadians(from.getY())) * Math.cos(Math.toRadians(to.getY())) *
                   Math.cos(Math.toRadians(lonDiff));
    double res1 = Math.atan2(y, x);
    double res2 = Math.toDegrees(res1);
    return (res2 + 360) % 360;
  }

  private void clearAllTargetGraphics() {
    mGraphics.getGraphics().clear();
  }

  /**
   * Find the midpoint of a line defined by a start and end point
   * @param ptFrom Starting point of the line
   * @param ptTo End point of the line
   * @return The point (x, y, z) at the midpoint of the line formed by the inputs
   */
  private static Point midpoint(Point ptFrom, Point ptTo) {
    double dx = (ptTo.getX() - ptFrom.getX()) / 2d;
    double dy = (ptTo.getY() - ptFrom.getY()) / 2d;
    double dz = (Double.isNaN(ptFrom.getZ()) || Double.isNaN(ptTo.getZ())) ?
        Double.NaN : (ptTo.getZ() - ptFrom.getZ()) / 2d;
    double x = ptFrom.getX() + dx;
    double y = ptFrom.getY() + dy;
    double z = Double.isNaN(dz) ? Double.NaN : ptFrom.getZ() + dz;
    return new Point(x, y, z, ptFrom.getSpatialReference());
  }
}
