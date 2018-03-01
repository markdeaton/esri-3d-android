package com.esri.apl.ea3d.util;

import com.esri.arcgisruntime.geometry.Point;

/**
 * A utility class to help with miscellaneous geometrical operations
 */
public class GeometryUtils {

  /**
   * Calculate distance from camera to target point
   * @param from camera/observer point (x, y, z in meters)
   * @param to target/destination point (x, y, z in meters)
   * @return distance in meters
   */
  public static double distance(Point from, Point to) {
    // Use Pythagorean Theorem to approximate target distance
    double vDiff = Math.abs(from.getZ() - to.getZ());
    double hDiff = Math.sqrt(
        Math.pow(from.getX() - to.getX(), 2f) +
            Math.pow(from.getY() - to.getY(), 2f)
    );
    // Distance from camera to tap point in meters
    return Math.sqrt(Math.pow(hDiff, 2f) + Math.pow(vDiff, 2f));
  }
}
