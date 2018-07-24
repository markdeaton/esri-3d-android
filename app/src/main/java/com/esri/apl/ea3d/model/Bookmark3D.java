package com.esri.apl.ea3d.model;

import com.esri.apl.ea3d.util.CameraSerialization;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Custom representation of a web scene slide */
public class Bookmark3D {
  private Camera _camera;
  private String _title;

  private List<String> _visibleLayerIds = new ArrayList<>();
  private String _id;

  /** Constructor for use when parsing webscene JSON manually
   *
   * @param jsonObj A JSON Object for the presentation.slides portion of the webscene JSON
   */
  public Bookmark3D(JsonObject jsonObj) {
    _id = jsonObj.get("id").getAsString();
    _title = jsonObj.getAsJsonObject("title").get("text").getAsString();

    JsonObject jsonCamera = jsonObj.getAsJsonObject("viewpoint").getAsJsonObject("camera");
    _camera = CameraSerialization.cameraFromJSON(jsonCamera);

    JsonArray jsonVisLyrs = jsonObj.getAsJsonArray("visibleLayers");
    for (JsonElement visLyr : jsonVisLyrs) {
      String sLyrId = visLyr.getAsJsonObject().get("id").getAsString();
      _visibleLayerIds.add(sLyrId);
    }
  }

  /** Constructor for use when using new 100.3.0+ ability to load webscenes.
   * Note that the SDK still doesn't support slides, but ArcGISScene.getUnsupportedJson()
   * returns a map of item, value pairs in the webscene JSON.
   * @param slide A set of item,value pairs corresponding to a slide within the
   *             presentation.slides portion of the webscene JSON
   */
  public Bookmark3D(Map<String, Object> slide) {
    _id = slide.get("id").toString();
    _title = ((Map<String, Object>)slide.get("title")).get("text").toString();

    Map<String, Object> viewpoint = (Map<String, Object>) slide.get("viewpoint");
    Map<String, Object> camera = (Map<String, Object>) viewpoint.get("camera");
    _camera = CameraSerialization.cameraFromUnsupportedJSON(camera);

    if (slide.containsKey("visibleLayers")) {
      List<Object> visLayers = (List<Object>) slide.get("visibleLayers");
      for (Object visLyr : visLayers) {
        String sLyrId = ((Map<String, Object>) visLyr).get("id").toString();
        _visibleLayerIds.add(sLyrId);
      }
    }
  }

  public Camera get_camera() {
    return _camera;
  }

  public String get_title() {
    return _title;
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
