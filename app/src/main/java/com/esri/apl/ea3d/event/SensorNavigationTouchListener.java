package com.esri.apl.ea3d.event;

import com.esri.apl.ea3d.MainActivity;
import com.esri.arcgisruntime.mapping.view.DefaultSceneViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.SceneView;

// TODO Replace this listener with built-in capabilities if/when the SDK supports it
/**
 * Permission by user not needed to use orientation sensors.
 * Pass through start/stop activate/deactivate commands from calling activity.
 * Using a TouchListener also halts analysis taps, so new LOS/Viewsheds/Identifies/Measures
 * can't be performed during the chaos of SensorNavigation mode.
 */
public class SensorNavigationTouchListener extends DefaultSceneViewOnTouchListener
    implements TouchListenerFinalizable {
  private SensorNavigationPositionListener mNavPosListener;

  public SensorNavigationTouchListener(SceneView sceneView, MainActivity mainActivity) {
    super(sceneView);
    this.mNavPosListener = new SensorNavigationPositionListener(sceneView, mainActivity);
  }

  private boolean _isActiveListener = false;
  public boolean get_isActiveListener() {
    return _isActiveListener;
  }
  public void set_isActiveListener(boolean _isActiveListener) {
    this._isActiveListener = _isActiveListener;
    if (_isActiveListener)
      startSensor();
    else
      stopSensor();
  }

  @Override
  public void cleanup() {
    set_isActiveListener(false);
    mNavPosListener.set_needInitialSceneRecalibration(true);
  }

  /**
   * Called when main activity resumes
   */
  public void startSensor() {
    if (get_isActiveListener()) mNavPosListener.startSensor();
  }

  /**
   * Called when main activity pauses
   */
  public void stopSensor() {
    mNavPosListener.stopSensor();
  }
}
