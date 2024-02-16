package ru.vvdev.yamap.view;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.yandex.mapkit.Animation;
import com.yandex.mapkit.ScreenPoint;
import com.yandex.mapkit.geometry.BoundingBox;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.logo.Alignment;
import com.yandex.mapkit.logo.HorizontalAlignment;
import com.yandex.mapkit.logo.Padding;
import com.yandex.mapkit.logo.VerticalAlignment;
import com.yandex.mapkit.map.CameraListener;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.CameraUpdateReason;
import com.yandex.mapkit.map.InputListener;
import com.yandex.mapkit.map.MapLoadStatistics;
import com.yandex.mapkit.map.MapLoadedListener;
import com.yandex.mapkit.map.MapObject;
import com.yandex.mapkit.map.MapType;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.map.VisibleRegion;
import com.yandex.mapkit.mapview.MapView;

import java.util.ArrayList;

import javax.annotation.Nullable;

import ru.vvdev.yamap.models.ReactMapObject;

public class YamapView extends MapView implements CameraListener, InputListener, MapLoadedListener {
    private float maxFps = 60;

    public YamapView(Context context) {
        super(context);
        getMap().addCameraListener(this);
        getMap().addInputListener(this);
        getMap().setMapLoadedListener(this);
    }

    // REF
    public void setCenter(CameraPosition position, float duration, int animation) {
        if (duration > 0) {
            Animation.Type anim = animation == 0 ? Animation.Type.SMOOTH : Animation.Type.LINEAR;
            getMap().move(position, new Animation(anim, duration), null);
        } else {
            getMap().move(position);
        }
    }

    private WritableMap positionToJSON(CameraPosition position, CameraUpdateReason reason, boolean finished) {
        WritableMap cameraPosition = Arguments.createMap();
        Point point = position.getTarget();
        cameraPosition.putDouble("azimuth", position.getAzimuth());
        cameraPosition.putDouble("tilt", position.getTilt());
        cameraPosition.putDouble("zoom", position.getZoom());
        WritableMap target = Arguments.createMap();
        target.putDouble("lat", point.getLatitude());
        target.putDouble("lon", point.getLongitude());
        cameraPosition.putMap("point", target);
        cameraPosition.putString("reason", reason.toString());
        cameraPosition.putBoolean("finished", finished);

        return cameraPosition;
    }

    private WritableMap screenPointToJSON(ScreenPoint screenPoint) {
        WritableMap result = Arguments.createMap();

        result.putDouble("x", (float) screenPoint.getX());
        result.putDouble("y", (float) screenPoint.getY());

        return result;
    }

    private WritableMap worldPointToJSON(Point worldPoint) {
        WritableMap result = Arguments.createMap();

        result.putDouble("lat", worldPoint.getLatitude());
        result.putDouble("lon", worldPoint.getLongitude());

        return result;
    }

    private WritableMap visibleRegionToJSON(VisibleRegion region) {
        WritableMap result = Arguments.createMap();

        WritableMap bl = Arguments.createMap();
        bl.putDouble("lat", region.getBottomLeft().getLatitude());
        bl.putDouble("lon", region.getBottomLeft().getLongitude());
        result.putMap("bottomLeft", bl);

        WritableMap br = Arguments.createMap();
        br.putDouble("lat", region.getBottomRight().getLatitude());
        br.putDouble("lon", region.getBottomRight().getLongitude());
        result.putMap("bottomRight", br);

        WritableMap tl = Arguments.createMap();
        tl.putDouble("lat", region.getTopLeft().getLatitude());
        tl.putDouble("lon", region.getTopLeft().getLongitude());
        result.putMap("topLeft", tl);

        WritableMap tr = Arguments.createMap();
        tr.putDouble("lat", region.getTopRight().getLatitude());
        tr.putDouble("lon", region.getTopRight().getLongitude());
        result.putMap("topRight", tr);

        return result;
    }

    public void emitCameraPositionToJS(String id) {
        CameraPosition position = getMap().getCameraPosition();
        WritableMap cameraPosition = positionToJSON(position, CameraUpdateReason.valueOf("APPLICATION"), true);
        cameraPosition.putString("id", id);
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "cameraPosition", cameraPosition);
    }

    public void emitVisibleRegionToJS(String id) {
        VisibleRegion visibleRegion = getMap().getVisibleRegion();
        WritableMap result = visibleRegionToJSON(visibleRegion);
        result.putString("id", id);
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "visibleRegion", result);
    }

    public void emitWorldToScreenPoints(ReadableArray worldPoints, String id) {
        WritableArray screenPoints = Arguments.createArray();

        for (int i = 0; i < worldPoints.size(); ++i) {
            ReadableMap p = worldPoints.getMap(i);
            Point worldPoint = new Point(p.getDouble("lat"), p.getDouble("lon"));
            ScreenPoint screenPoint = getMapWindow().worldToScreen(worldPoint);
            screenPoints.pushMap(screenPointToJSON(screenPoint));
        }

        WritableMap result = Arguments.createMap();
        result.putString("id", id);
        result.putArray("screenPoints", screenPoints);

        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "worldToScreenPoints", result);
    }

    public void emitScreenToWorldPoints(ReadableArray screenPoints, String id) {
        WritableArray worldPoints = Arguments.createArray();

        for (int i = 0; i < screenPoints.size(); ++i) {
            ReadableMap p = screenPoints.getMap(i);
            ScreenPoint screenPoint = new ScreenPoint((float) p.getDouble("x"), (float) p.getDouble("y"));
            Point worldPoint = getMapWindow().screenToWorld(screenPoint);
            worldPoints.pushMap(worldPointToJSON(worldPoint));
        }

        WritableMap result = Arguments.createMap();
        result.putString("id", id);
        result.putArray("worldPoints", worldPoints);

        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "screenToWorldPoints", result);
    }

    public void setZoom(Float zoom, float duration, int animation) {
        CameraPosition prevPosition = getMap().getCameraPosition();
        CameraPosition position = new CameraPosition(prevPosition.getTarget(), zoom, prevPosition.getAzimuth(), prevPosition.getTilt());
        setCenter(position, duration, animation);
    }

    public void fitAllMarkers() {
        ArrayList<Point> points = new ArrayList<>();
        for (int i = 0; i < getChildCount(); ++i) {
            Object obj = getChildAt(i);
            if (obj instanceof YamapMarker) {
                YamapMarker marker = (YamapMarker) obj;
                points.add(marker.point);
            }
        }
        fitMarkers(points);
    }

    BoundingBox calculateBoundingBox(ArrayList<Point> points) {
        double minLon = points.get(0).getLongitude();
        double maxLon = points.get(0).getLongitude();
        double minLat = points.get(0).getLatitude();
        double maxLat = points.get(0).getLatitude();

        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).getLongitude() > maxLon) {
                maxLon = points.get(i).getLongitude();
            }

            if (points.get(i).getLongitude() < minLon) {
                minLon = points.get(i).getLongitude();
            }

            if (points.get(i).getLatitude() > maxLat) {
                maxLat = points.get(i).getLatitude();
            }

            if (points.get(i).getLatitude() < minLat) {
                minLat = points.get(i).getLatitude();
            }
        }

        Point southWest = new Point(minLat, minLon);
        Point northEast = new Point(maxLat, maxLon);

        BoundingBox boundingBox = new BoundingBox(southWest, northEast);
        return boundingBox;
    }

    public void fitMarkers(ArrayList<Point> points) {
        if (points.size() == 0) {
            return;
        }
        if (points.size() == 1) {
            Point center = new Point(points.get(0).getLatitude(), points.get(0).getLongitude());
            getMap().move(new CameraPosition(center, 15, 0, 0));
            return;
        }
        CameraPosition cameraPosition = getMap().cameraPosition(calculateBoundingBox(points));
        cameraPosition = new CameraPosition(cameraPosition.getTarget(), cameraPosition.getZoom() - 0.8f, cameraPosition.getAzimuth(), cameraPosition.getTilt());
        getMap().move(cameraPosition, new Animation(Animation.Type.SMOOTH, 0.7f), null);
    }

    public void setMapStyle(@Nullable String style) {
        if (style != null) {
            getMap().setMapStyle(style);
        }
    }

    public void setMapType(@Nullable String type) {
        if (type != null) {
            switch (type) {
                case "none":
                    getMap().setMapType(MapType.NONE);
                    break;

                case "raster":
                    getMap().setMapType(MapType.MAP);
                    break;

                default:
                    getMap().setMapType(MapType.VECTOR_MAP);
                    break;
            }
        }
    }

    public void setInitialRegion(@Nullable ReadableMap params) {
        if ((!params.hasKey("lat") || params.isNull("lat")) || (!params.hasKey("lon") && params.isNull("lon")))
            return;

        Float initialRegionZoom = 10.f;
        Float initialRegionAzimuth = 0.f;
        Float initialRegionTilt = 0.f;

        if (params.hasKey("zoom") && !params.isNull("zoom"))
            initialRegionZoom = (float) params.getDouble("zoom");

        if (params.hasKey("azimuth") && !params.isNull("azimuth"))
            initialRegionAzimuth = (float) params.getDouble("azimuth");

        if (params.hasKey("tilt") && !params.isNull("tilt"))
            initialRegionTilt = (float) params.getDouble("tilt");

        Point initialPosition = new Point(params.getDouble("lat"), params.getDouble("lon"));
        CameraPosition initialCameraPosition = new CameraPosition(initialPosition, initialRegionZoom, initialRegionAzimuth, initialRegionTilt);
        setCenter(initialCameraPosition, 0.f, 0);
    }

    public void setLogoPosition(@Nullable ReadableMap params) {
        HorizontalAlignment horizontalAlignment = HorizontalAlignment.RIGHT;
        VerticalAlignment verticalAlignment = VerticalAlignment.BOTTOM;

        if (params.hasKey("horizontal") && !params.isNull("horizontal")) {
            switch (params.getString("horizontal")) {
                case "left":
                    horizontalAlignment = HorizontalAlignment.LEFT;
                    break;

                case "center":
                    horizontalAlignment = HorizontalAlignment.CENTER;
                    break;

                default:
                    break;
            }
        }

        if (params.hasKey("vertical") && !params.isNull("vertical")) {
            switch (params.getString("vertical")) {
                case "top":
                    verticalAlignment = VerticalAlignment.TOP;
                    break;

                default:
                    break;
            }
        }

        getMap().getLogo().setAlignment(new Alignment(horizontalAlignment, verticalAlignment));
    }

    public void setLogoPadding(@Nullable ReadableMap params) {
        int horizontalPadding = (params.hasKey("horizontal") && !params.isNull("horizontal")) ? params.getInt("horizontal") : 0;
        int verticalPadding = (params.hasKey("vertical") && !params.isNull("vertical")) ? params.getInt("vertical") : 0;
        getMap().getLogo().setPadding(new Padding(horizontalPadding, verticalPadding));
    }

    public void setMaxFps(float fps) {
        maxFps = fps;
        getMapWindow().setMaxFps(maxFps);
    }

    public void setInteractive(boolean interactive) {
        setNoninteractive(!interactive);
    }

    public void setNightMode(Boolean nightMode) {
        getMap().setNightModeEnabled(nightMode);
    }

    public void setScrollGesturesEnabled(Boolean scrollGesturesEnabled) {
        getMap().setScrollGesturesEnabled(scrollGesturesEnabled);
    }

    public void setZoomGesturesEnabled(Boolean zoomGesturesEnabled) {
        getMap().setZoomGesturesEnabled(zoomGesturesEnabled);
    }

    public void setRotateGesturesEnabled(Boolean rotateGesturesEnabled) {
        getMap().setRotateGesturesEnabled(rotateGesturesEnabled);
    }

    public void setFastTapEnabled(Boolean fastTapEnabled) {
        getMap().setFastTapEnabled(fastTapEnabled);
    }

    public void setTiltGesturesEnabled(Boolean tiltGesturesEnabled) {
        getMap().setTiltGesturesEnabled(tiltGesturesEnabled);
    }

    // CHILDREN
    public void addFeature(View child, int index) {
        if (child instanceof YamapMarker) {
            YamapMarker _child = (YamapMarker) child;
            PlacemarkMapObject obj = getMap().getMapObjects().addPlacemark(_child.point);
            _child.setMapObject(obj);
        }
    }

    public void removeChild(int index) {
        if (getChildAt(index) instanceof ReactMapObject) {
            final ReactMapObject child = (ReactMapObject) getChildAt(index);
            if (child == null) return;
            final MapObject mapObject = child.getMapObject();
            if (mapObject == null || !mapObject.isValid()) return;

            getMap().getMapObjects().remove(mapObject);
        }
    }

    @Override
    public void onCameraPositionChanged(@NonNull com.yandex.mapkit.map.Map map, @NonNull CameraPosition cameraPosition, CameraUpdateReason reason, boolean finished) {
        WritableMap positionStart = positionToJSON(cameraPosition, reason, finished);
        WritableMap positionFinish = positionToJSON(cameraPosition, reason, finished);
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "cameraPositionChange", positionStart);

        if (finished) {
            reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "cameraPositionChangeEnd", positionFinish);
        }
    }

    @Override
    public void onMapTap(@NonNull com.yandex.mapkit.map.Map map, @NonNull Point point) {
        WritableMap data = Arguments.createMap();
        data.putDouble("lat", point.getLatitude());
        data.putDouble("lon", point.getLongitude());
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "onMapPress", data);
    }

    @Override
    public void onMapLongTap(@NonNull com.yandex.mapkit.map.Map map, @NonNull Point point) {
        WritableMap data = Arguments.createMap();
        data.putDouble("lat", point.getLatitude());
        data.putDouble("lon", point.getLongitude());
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "onMapLongPress", data);
    }

    @Override
    public void onMapLoaded(MapLoadStatistics statistics) {
        WritableMap data = Arguments.createMap();
        data.putInt("renderObjectCount",statistics.getRenderObjectCount());
        data.putDouble("curZoomModelsLoaded",statistics.getCurZoomModelsLoaded());
        data.putDouble("curZoomPlacemarksLoaded",statistics.getCurZoomPlacemarksLoaded());
        data.putDouble("curZoomLabelsLoaded",statistics.getCurZoomLabelsLoaded());
        data.putDouble("curZoomGeometryLoaded",statistics.getCurZoomGeometryLoaded());
        data.putDouble("tileMemoryUsage",statistics.getTileMemoryUsage());
        data.putDouble("delayedGeometryLoaded",statistics.getDelayedGeometryLoaded());
        data.putDouble("fullyAppeared",statistics.getFullyAppeared());
        data.putDouble("fullyLoaded",statistics.getFullyLoaded());
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(getId(), "onMapLoaded",data);
    }
}
