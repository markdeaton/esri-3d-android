package com.esri.apl.ea3d.event;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.esri.apl.ea3d.MainActivity;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.SceneView;

/**
 * Listen to the game rotation vector sensor, which does not include real-world heading info
 * from the magnetometer/compass. This is best indoors or when lots of metal in the area
 * distorts the compass reading, causing bias and drift.<p/>
 * This could be made a function independent of the TouchListeners. For now, make it part
 * of a TouchListener and liberate it later if needed.
 */
class SensorNavigationPositionListener implements SensorEventListener {
  private static final String TAG = "SensorNavListener";
  private static final int SENSOR_RATE = 1000000 / 30; // readings per second ÷ frames per second

  // Game rotation sensor usually (not on every device) begins at heading 270
  // How much to adjust heading based on difference between initial game sensor heading and initial scene heading
  private double mHeadingCompensation;
  private double mHeadingWhenSensorStopped;

  private SceneView mSceneView;
  private MainActivity mParentActivity;
  private SensorManager mSensorManager;

  private final float[] mSensorReading = new float[3];
  private final float[] mRotationMatrix = new float[9];
  private final float[] mRotationMatrixRemapped = new float[9];

  private final float[] mNewOrientationAngles = new float[] {Float.NaN, Float.NaN, Float.NaN};
//  private final float[] mOldOrientationAngles = new float[] {Float.NaN, Float.NaN, Float.NaN};


  public SensorNavigationPositionListener(SceneView sceneView, MainActivity parentActivity) {
    this.mSceneView = sceneView;
    this.mParentActivity = parentActivity;
    mSensorManager = (SensorManager)parentActivity.getSystemService(Context.SENSOR_SERVICE);
  }

  /**
   * This should indicate whether the user has selected this as the navigation mode,
   * not whether sensors are currently actively sensing (e.g. if app has paused).
   * @return Whether the user currently wants to use Sensor Navigation
   */
  public boolean get_isActive() {
    return _isActive;
  }

  /**
   * Set by main activity in response to user menu choice. Should not be set when
   * sensors are deactivated (e.g. when app pauses).
   * @param _isActive Whether the user has chosen this as the navigation mode
   */
  public void set_isActive(boolean _isActive) {
    this._isActive = _isActive;
  }

  private boolean _isActive = false;


  /**
   * Whether to recalibrate against the current scene heading.
   * Whenever the sensor starts, it needs to be recalibrated against a desired heading.
   * Most of the time, this will be the heading when the sensor was last stopped (providing continuity).
   * But when the navigation method is changed within the app, we need to calibrate against the
   * scene's heading.
   * Unfortunately, the sensor itself can't know when it needs to recalibrate itself to the scene
   * heading. Only an outsider caller can know whether the sensor was stopped due to app Pause
   * or due to another navigation mode being chosen.
   * @param _needInitialSceneRecalibration Whether to find a new heading compensation value
   *                                       based on the scene camera heading
   */
  public void set_needInitialSceneRecalibration(boolean _needInitialSceneRecalibration) {
    this._needInitialSceneRecalibration = _needInitialSceneRecalibration;
  }

  private boolean _needInitialSceneRecalibration = true;

  /**
   * Whether the sensor has just started up again and needs recalibration from the last heading viewed.
   * This will be ignored if initial scene recalibration is also needed.
   */
  private boolean _needLastUsedSceneRecalibration = false;

  /**
   * Start collecting position data from the game rotation vector sensor, which does not include
   * the magnetomter/compass (best for use indoors or where lots of metal distorts the magnetic
   * field)
   */
  public void startSensor() {
    _needLastUsedSceneRecalibration = true;

    // Lock rotation to default
    int req = mParentActivity.getRequestedOrientation();

    // Lock screen rotation to device's built-in zero rotation setting
    mParentActivity.setCompassVisibility(false);
    mParentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

    mSensorManager.registerListener(this,
        mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SENSOR_RATE);

  }

  /**
   * Stop collecting position sensor data
   */
  public void stopSensor() {
    mHeadingWhenSensorStopped = mSceneView.getCurrentViewpointCamera().getHeading();

    // Free screen rotation to use system or user settings
    mParentActivity.setCompassVisibility(true);
    mParentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

    // reset roll to 0
    Camera cam = mSceneView.getCurrentViewpointCamera().rotateTo(
        mSceneView.getCurrentViewpointCamera().getHeading(),
        mSceneView.getCurrentViewpointCamera().getPitch(),
        0);
    mSceneView.setViewpointCameraAsync(cam, 1.0f);

    mSensorManager.unregisterListener(this);
  }

  /**
   * The guts of the functionality. Compute a rotation/tilt/roll change and apply to the scene.
   * <ol>
   *   <li>Get rotation matrix from sensor reading</li>
   *   <li>Get orientation matrix from rotation matrix</li>
   *   <li>Get azimuth, pitch, roll and update scene</li>
   * </ol>
   * @param event Data from one of the two sensors
   * @see <a href="https://developer.android.com/guide/topics/sensors/sensors_position.html#sensors-pos-orient">
   *   https://developer.android.com/guide/topics/sensors/sensors_position.html#sensors-pos-orient</a>
   */
  @Override
  public void onSensorChanged(SensorEvent event) {
    if (!mSceneView.isNavigating()) {
      System.arraycopy(event.values, 0,
          mSensorReading, 0, mSensorReading.length);

      // Get rotation matrix, which is needed to update orientation angles.
      SensorManager.getRotationMatrixFromVector(mRotationMatrix, mSensorReading);

      updateOrientationAnglesFromRotationMatrix();
      updateSceneAbsolute();

    } else Log.d(TAG, "Navigation canceled; already navigating");
  }

  // Compute the three orientation angles based on the most recent readings from
  // the device's accelerometer and magnetometer.
  private void updateOrientationAnglesFromRotationMatrix() {
    // Axis settings for VR "lookthrough" device orientation (not flat on table)
    int axisX = SensorManager.AXIS_MINUS_X;
    int axisY = SensorManager.AXIS_Z;

    boolean bRemap = SensorManager.remapCoordinateSystem(mRotationMatrix, axisX, axisY, mRotationMatrixRemapped);
    if (!bRemap) Log.d(TAG, "Error remapping coordinate systems.");

    SensorManager.getOrientation(mRotationMatrixRemapped, mNewOrientationAngles);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) { /* Ignore; not much we can do here*/ }

  private void updateSceneAbsolute() {
    double azimuth = Math.toDegrees(mNewOrientationAngles[0]);

    // Figure out heading compensation for this session
    if (_needInitialSceneRecalibration) {
      double currentHeading = mSceneView.getCurrentViewpointCamera().getHeading();
      mHeadingCompensation = currentHeading - azimuth;
      set_needInitialSceneRecalibration(false);
    } else if (_needLastUsedSceneRecalibration) {
      mHeadingCompensation = mHeadingWhenSensorStopped - azimuth;
      _needLastUsedSceneRecalibration = false;
    }

    azimuth += mHeadingCompensation;
    azimuth = (azimuth + 360) % 360;
    double pitch = Math.toDegrees(mNewOrientationAngles[1]);
    pitch += 90;
    double roll = Math.toDegrees(mNewOrientationAngles[2]);

    Log.d(TAG, String.format("%f, %f, %f; Heading: %f, Pitch: %f, Roll: %f",
        Math.toDegrees(mSensorReading[0]), mSensorReading[1], mSensorReading[2], azimuth, pitch, roll));

    Camera newCam = mSceneView.getCurrentViewpointCamera().rotateTo(azimuth, pitch, roll);
    mSceneView.setViewpointCameraAsync(newCam, 0);
  }
}
