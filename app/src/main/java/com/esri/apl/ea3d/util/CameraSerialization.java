package com.esri.apl.ea3d.util;

import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReference;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.google.gson.JsonObject;

import java.util.Map;

/** Utilities to help deserialize a scene camera from JSON */
public class CameraSerialization {
  public static Camera cameraFromJSON(JsonObject jsonCamera) {
    Point pt = (Point) Point.fromJson(jsonCamera.getAsJsonObject("position").toString());
    double fPitch = jsonCamera.get("tilt").getAsDouble();
    double fHeading = jsonCamera.get("heading").getAsDouble();
    return new Camera(pt, fHeading, fPitch, 0d);
  }

  public static Camera cameraFromUnsupportedJSON(Map<String, Object> camera) {
    double fPitch = (double)camera.get("tilt");
    double fHeading = (double)camera.get("heading");
    Map<String, Object> oPoint = (Map<String, Object>)camera.get("position");
    double x = (double)oPoint.get("x"); double y = (double)oPoint.get("y"); double z = (double)oPoint.get("z");
    Map<String, Object> sr = (Map<String, Object>)oPoint.get("spatialReference");
    double wkid = (double)sr.get("wkid");
    SpatialReference spatialReference = SpatialReference.create((int)wkid);
    Point pt = new Point(x, y, z, spatialReference);

    return new Camera(pt, fHeading, fPitch, 0);
  }
}
