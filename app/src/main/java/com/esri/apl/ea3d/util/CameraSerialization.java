package com.esri.apl.ea3d.util;

import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.google.gson.JsonObject;

/** Utilities to help deserialize a scene camera from JSON */
public class CameraSerialization {
  public static Camera CameraFromJSON(JsonObject jsonCamera) {
    Point pt = (Point) Point.fromJson(jsonCamera.getAsJsonObject("position").toString());
    double fPitch = jsonCamera.get("tilt").getAsDouble();
    double fHeading = jsonCamera.get("heading").getAsDouble();
    return new Camera(pt, fHeading, fPitch, 0d);
  }
}
