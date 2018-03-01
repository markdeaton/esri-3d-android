package com.esri.apl.ea3d.model;

import com.esri.apl.ea3d.util.CameraSerialization;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Custom representation of a web scene slide */
public class Bookmark3D {
  private Camera _camera;
  private String _title;
  private String _description;

  private List<String> _visibleLayerIds;
  private String _id;

  public Bookmark3D(JsonObject jsonObj) {
    _id = jsonObj.get("id").getAsString();
    _title = jsonObj.getAsJsonObject("title").get("text").getAsString();
    _description = jsonObj.getAsJsonObject("description").get("text").getAsString();

    JsonObject jsonCamera = jsonObj.getAsJsonObject("viewpoint").getAsJsonObject("camera");
    _camera = CameraSerialization.CameraFromJSON(jsonCamera);

    JsonArray jsonVisLyrs = jsonObj.getAsJsonArray("visibleLayers");
    _visibleLayerIds = new ArrayList<>();
    for (JsonElement visLyr : jsonVisLyrs) {
      String sLyrId = visLyr.getAsJsonObject().get("id").getAsString();
      _visibleLayerIds.add(sLyrId);
    }
  }

  public Camera get_camera() {
    return _camera;
  }

  public String get_title() {
    return _title;
  }

  public String get_description() {
    return _description;
  }

  public List<String> get_visibleLayerIds() {
    return _visibleLayerIds;
  }

  public void set_visibleLayerIds(List<String> _visibleLayerIds) {
    this._visibleLayerIds = _visibleLayerIds;
  }

  public String get_id() {
    return _id;
  }
}
