package gis.hmap;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.supermap.android.commons.EventStatus;
import com.supermap.android.data.GetFeaturesByGeometryService;
import com.supermap.android.data.GetFeaturesBySQLParameters;
import com.supermap.android.data.GetFeaturesBySQLService;
import com.supermap.android.data.GetFeaturesResult;
import com.supermap.android.maps.Point2D;
import com.supermap.services.components.commontypes.Feature;
import com.supermap.services.components.commontypes.Geometry;
import com.supermap.services.components.commontypes.QueryParameter;
import com.supermap.services.components.commontypes.Rectangle2D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by Ryan on 2018/10/16.
 */

 class QueryUtils {

     private static ExecutorService executorService = Executors.newFixedThreadPool(10);

    public static class BuildingResult {
        public Feature feature;
        public List<Point2D> buildingGeometry;

        public BuildingResult(Feature feature, List<Point2D> buildingGeometry) {
            this.feature = feature;
            this.buildingGeometry = buildingGeometry;
        }
    }

    public static void queryAllBuildings(String name, Handler handler) {
        new Thread(new QueryAllBuildingsRunnable(name, handler)).start();
    }

    private static class QueryAllBuildingsRunnable implements Runnable {
        String name;
        Handler handler;

        public QueryAllBuildingsRunnable(String name, Handler handler) {
            this.name = name;
            this.handler = handler;
        }

        @Override
        public void run() {
            GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
            sqlParameters.datasetNames = new String[] { Common.parkId() + ":buildings" };
            sqlParameters.toIndex = 9999;
            QueryParameter queryParameter = new QueryParameter();
            queryParameter.name = name;
            sqlParameters.queryParameter = queryParameter;

            GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
            MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<BuildingResult> buildings = null;
            GetFeaturesResult building = listener.getReult();
            if (building != null && building.features != null) {
                buildings = new ArrayList<>();
                for (Feature feature : building.features) {
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    BuildingResult br = new BuildingResult(feature, geoPoints);
                    buildings.add(br);
                }
            }

            if (buildings != null && buildings.size() > 0) {
                Message msg = new Message();
                msg.obj = buildings;
                msg.what = Common.QUERY_BUILDINGS;
                handler.sendMessage(msg);
            }
        }
    }

    public static void queryIndoorMap(String mapId, String buildingId, String florid, Handler handler) {
        executorService.execute(new QueryIndoorMapRunnable(mapId, buildingId, florid, handler));
    }

    private static class QueryIndoorMapRunnable implements Runnable {
        String mapId;
        String buildingId;
        String florid;
        Handler handler;

        public QueryIndoorMapRunnable(String mapId, String buildingId, String florid, Handler handler) {
            this.mapId = mapId;
            this.buildingId = buildingId;
            this.florid = florid;
            this.handler = handler;
        }

        @Override
        public void run() {
            GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
            sqlParameters.datasetNames = new String[] { mapId };
            sqlParameters.toIndex = 9999;
            QueryParameter queryParameter = new QueryParameter();
            queryParameter.attributeFilter = "BuildingId = \"" + buildingId + "\"";
            sqlParameters.queryParameter = queryParameter;

            GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
            MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<List<Point2D>> buildingGeometry = null;
            Rectangle2D buildingBounds = null;
            GetFeaturesResult building = listener.getReult();
            if (building != null && building.features != null) {
                buildingGeometry = new ArrayList<>();
                for (Feature feature : building.features) {
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            buildingGeometry.add(partList);
                            num = num + count;
                        }
                    } else {
                        buildingGeometry.add(geoPoints);
                    }
                    if (buildingBounds == null)
                        buildingBounds = geometry.getBounds();
                    else {
                        if (buildingBounds.getLeft() > geometry.getBounds().getLeft())
                            buildingBounds.setLeft(geometry.getBounds().getLeft());
                        if (buildingBounds.getRight() < geometry.getBounds().getRight())
                            buildingBounds.setRight(geometry.getBounds().getRight());
                        if (buildingBounds.getTop() < geometry.getBounds().getTop())
                            buildingBounds.setTop(geometry.getBounds().getTop());
                        if (buildingBounds.getBottom() > geometry.getBounds().getBottom())
                            buildingBounds.setBottom(geometry.getBounds().getBottom());
                    }
                }
            }

            sqlParameters.datasetNames = new String[] { Common.parkId()+":"+florid };
            sqlParameters.queryParameter.attributeFilter = "BuildingId = \"" + buildingId + "\"";
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<ModelData> rooms = null;
            GetFeaturesResult floor = listener.getReult();
            if (floor != null && floor.features != null) {
                rooms = new ArrayList<>();
                for (Feature feature : floor.features) {
                    for (int i = 0; i < feature.fieldValues.length; i++) {
                        Log.e("QueryIndoorMapRunnable", feature.fieldNames[i]+"---"+feature.fieldValues[i]);
                    }
                    Geometry geometry = feature.geometry;
                    List<List<Point2D>> roomPoints = new ArrayList<>();
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            roomPoints.add(partList);
                            num = num + count;
                        }
                    } else {
                        roomPoints.add(geoPoints);
                    }
                    HashMap<String, String> info = new HashMap<>();
                    for (int i=0; i<feature.fieldNames.length; i++)
                        info.put(feature.fieldNames[i], feature.fieldValues[i]);
                    info.put("FLOORID", florid);
                    String key = String.format("%s.%s.%s", buildingId, florid, info.get("SMID"));
                    rooms.add(new ModelData(key, null, roomPoints, info));
                }
            }
            sqlParameters.datasetNames = new String[] { Common.parkId()+":"+florid+"_LINE" };
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            floor = listener.getReult();
            if (floor != null && floor.features != null) {
                if (rooms == null) rooms = new ArrayList<>();
                for (Feature feature : floor.features) {
                    Geometry geometry = feature.geometry;
                    List<List<Point2D>> linePoints = new ArrayList<>();
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            linePoints.add(partList);
                            num = num + count;
                        }
                    } else {
                        linePoints.add(geoPoints);
                    }
                    HashMap<String, String> info = new HashMap<>();
                    for (int i=0; i<feature.fieldNames.length; i++)
                        info.put(feature.fieldNames[i], feature.fieldValues[i]);
                    info.put("FLOORID", florid);
                    String key = String.format("%s.%s.%s", buildingId, florid, info.get("SMID"));
                    ModelData modelData = null;
                    for (ModelData model : rooms) {
                        if (model.key.equalsIgnoreCase(key)) {
                            modelData = model;
                            break;
                        }
                    }
                    if (modelData != null)
                        modelData.outline = linePoints;
                    else
                        rooms.add(new ModelData(key, linePoints, null, info));
                }
            }

            if (buildingGeometry != null || rooms != null) {
                IndoorMapData data = new IndoorMapData(buildingId, florid, buildingGeometry, buildingBounds, rooms);
                Message msg = new Message();
                msg.obj = data;
                msg.what = Common.QUERY_INDOOR_MAP;
                handler.sendMessage(msg);
            }
        }

    }

    public static class BasementMapResult {
        public String floorId;
        public List<List<Point2D>> structureGeometry;
        public List<List<Point2D>> floorGeometry;
        public Rectangle2D structureBounds;

        public BasementMapResult(String floorId, List<List<Point2D>> structureGeometry, Rectangle2D structureBounds, List<List<Point2D>> floorGeometry) {
            this.floorId = floorId;
            this.structureGeometry = structureGeometry;
            this.floorGeometry = floorGeometry;
            this.structureBounds = structureBounds;
        }
    }

    public static void queryBasementMap(String mapId, String florid, Handler handler) {
        new Thread(new QueryBasementMapRunnable(mapId, florid, handler)).start();
    }

    private static class QueryBasementMapRunnable implements Runnable {
        String mapId;
        String florid;
        Handler handler;

        public QueryBasementMapRunnable(String mapId, String florid, Handler handler) {
            this.mapId = mapId;
            this.florid = florid;
            this.handler = handler;
        }

        @Override
        public void run() {
            GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
            sqlParameters.datasetNames = new String[] { Common.parkId()+":"+florid };
            sqlParameters.toIndex = 9999;
            QueryParameter queryParameter = new QueryParameter();
            sqlParameters.queryParameter = queryParameter;

            GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
            MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<List<Point2D>> structureGeometry = null;
            Rectangle2D structureBounds = null;
            GetFeaturesResult structure = listener.getReult();
            if (structure != null && structure.features != null) {
                structureGeometry = new ArrayList<>();
                for (Feature feature : structure.features) {
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            structureGeometry.add(partList);
                            num = num + count;
                        }
                    } else {
                        structureGeometry.add(geoPoints);
                    }
                    if (structureBounds == null)
                        structureBounds = geometry.getBounds();
                    else {
                        if (structureBounds.getLeft() > geometry.getBounds().getLeft())
                            structureBounds.setLeft(geometry.getBounds().getLeft());
                        if (structureBounds.getRight() < geometry.getBounds().getRight())
                            structureBounds.setRight(geometry.getBounds().getRight());
                        if (structureBounds.getTop() < geometry.getBounds().getTop())
                            structureBounds.setTop(geometry.getBounds().getTop());
                        if (structureBounds.getBottom() > geometry.getBounds().getBottom())
                            structureBounds.setBottom(geometry.getBounds().getBottom());
                    }
                }
            }

            sqlParameters.datasetNames = new String[] { Common.parkId()+":"+florid+"_LINE" };

            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<List<Point2D>> floorGeometry = null;
            GetFeaturesResult floor = listener.getReult();
            if (floor != null && floor.features != null) {
                floorGeometry = new ArrayList<>();
                for (Feature feature : floor.features) {
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            floorGeometry.add(partList);
                            num = num + count;
                        }
                    } else {
                        floorGeometry.add(geoPoints);
                    }
                    if (structureBounds == null)
                        structureBounds = geometry.getBounds();
                    else {
                        if (structureBounds.getLeft() > geometry.getBounds().getLeft())
                            structureBounds.setLeft(geometry.getBounds().getLeft());
                        if (structureBounds.getRight() < geometry.getBounds().getRight())
                            structureBounds.setRight(geometry.getBounds().getRight());
                        if (structureBounds.getTop() < geometry.getBounds().getTop())
                            structureBounds.setTop(geometry.getBounds().getTop());
                        if (structureBounds.getBottom() > geometry.getBounds().getBottom())
                            structureBounds.setBottom(geometry.getBounds().getBottom());
                    }
                }
            }

            if (structureGeometry != null || floorGeometry != null) {
                BasementMapResult data = new BasementMapResult(florid, structureGeometry, structureBounds, floorGeometry);
                Message msg = new Message();
                msg.obj = data;
                msg.what = Common.QUERY_BASEMENT_MAP;
                handler.sendMessage(msg);
            }
        }

    }

    public static class PerimeterResult {
        public String parkId;
        public PerimeterStyle alarm;
        public PerimeterStyle normal;
        public List<List<Point2D>> alarmGeometry;
        public List<List<Point2D>> normalGeometry;

        public PerimeterResult(String parkId, PerimeterStyle alarm, PerimeterStyle normal,
                               List<List<Point2D>> alarmGeometry, List<List<Point2D>> normalGeometry) {
            this.parkId = parkId;
            this.alarm = alarm;
            this.normal = normal;
            this.alarmGeometry = alarmGeometry;
            this.normalGeometry = normalGeometry;
        }
    }

    public static void queryPerimeter(String parkId, PerimeterStyle alarm, PerimeterStyle normal, int[] alarmList, Handler handler) {
        new Thread(new QueryPerimeterRunnable(parkId, alarm, normal, alarmList, handler)).start();
    }

    private static class QueryPerimeterRunnable implements Runnable {
        private String parkId;
        private PerimeterStyle alarm;
        private PerimeterStyle normal;
        private int[] alarmList;
        private Handler handler;

        public QueryPerimeterRunnable(String parkId, PerimeterStyle alarm, PerimeterStyle normal, int[] alarmList, Handler handler) {
            this.parkId = parkId;
            this.alarm = alarm;
            this.normal = normal;
            this.alarmList = alarmList;
            this.handler = handler;
        }

        @Override
        public void run() {
            GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
            sqlParameters.datasetNames = new String[] { Common.parkId()+":PMTR" };
            sqlParameters.toIndex = 9999;
            QueryParameter queryParameter = new QueryParameter();
            queryParameter.name = "PMTR@" + Common.parkId();
            sqlParameters.queryParameter = queryParameter;

            GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
            MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<List<Point2D>> alarmGeometry = null;
            List<List<Point2D>> normalGeometry = null;
            GetFeaturesResult perimeter = listener.getReult();
            if (perimeter != null && perimeter.features != null) {
                alarmGeometry = new ArrayList<>();
                normalGeometry = new ArrayList<>();
                for (Feature feature : perimeter.features) {
                    boolean isAlarm = false;
                    for (int def : alarmList) {
                        if (feature.getID() == def) {
                            isAlarm = true;
                            break;
                        }
                    }
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            if (isAlarm)
                                alarmGeometry.add(partList);
                            else
                                normalGeometry.add(partList);
                            num = num + count;
                        }
                    } else {
                        if (isAlarm)
                            alarmGeometry.add(geoPoints);
                        else
                            normalGeometry.add(geoPoints);
                    }
                }
            }

            if (alarmGeometry != null || normalGeometry != null) {
                PerimeterResult data = new PerimeterResult(parkId, alarm, normal, alarmGeometry, normalGeometry);
                Message msg = new Message();
                msg.obj = data;
                msg.what = Common.QUERY_PERIMETER;
                handler.sendMessage(msg);
            }
        }
    }

    public static class ModelResult {
        public List<List<Point2D>> highlightGeometry;
        public List<PresentationStyle> highlightStyle;
        public List<List<Point2D>> normalGeometry;
        public PresentationStyle normalStyle;

        public ModelResult(List<List<Point2D>> highlightGeometry, List<PresentationStyle> highlightStyle, List<List<Point2D>> normalGeometry, PresentationStyle normalStyle) {
            this.highlightGeometry = highlightGeometry;
            this.highlightStyle = highlightStyle;
            this.normalGeometry = normalGeometry;
            this.normalStyle = normalStyle;
        }
    }

    public static void queryModel(List<int[]> modIds, List<PresentationStyle> pss, PresentationStyle other, Handler handler) {
        new Thread(new QueryModelRunnable(modIds, pss, other, handler)).start();
    }

    private static class QueryModelRunnable implements Runnable {
        private List<int[]> modIds;
        private List<PresentationStyle> pss;
        private PresentationStyle other;
        private Handler handler;

        public QueryModelRunnable(List<int[]> modIds, List<PresentationStyle> pss, PresentationStyle other, Handler handler) {
            this.modIds = modIds;
            this.pss = pss;
            this.other = other;
            this.handler = handler;
        }

        @Override
        public void run() {
            GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
            sqlParameters.datasetNames = new String[] { Common.parkId() + ":PARKING" };
            sqlParameters.toIndex = 9999;
            QueryParameter queryParameter = new QueryParameter();
            queryParameter.name = "PARKING@" + Common.parkId();
            sqlParameters.queryParameter = queryParameter;

            GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
            MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
            sqlService.process(sqlParameters, listener);
            try {
                listener.waitUntilProcessed();
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<List<Point2D>> highlightGeometry = null;
            List<PresentationStyle> highlightStyle = null;
            List<List<Point2D>> normalGeometry = null;
            GetFeaturesResult model = listener.getReult();
            if (model != null && model.features != null) {
                highlightGeometry = new ArrayList<>();
                highlightStyle = new ArrayList<>();
                normalGeometry = new ArrayList<>();
                for (Feature feature : model.features) {
                    boolean isHighLight = false;
                    int index = 0;
                    if (modIds != null)
                    for (int[] ids : modIds) {
                        for (int highlights : ids) {
                            if (feature.getID() == highlights) {
                                isHighLight = true;
                                break;
                            }
                        }
                        if (isHighLight)
                            break;
                        index++;
                    }
                    Geometry geometry = feature.geometry;
                    List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                    if (geometry.parts.length > 1) {
                        int num = 0;
                        for (int j = 0; j < geometry.parts.length; j++) {
                            int count = geometry.parts[j];
                            List<Point2D> partList = geoPoints.subList(num, num + count);
                            if (isHighLight) {
                                highlightGeometry.add(partList);
                                if (pss != null && pss.size() > index)
                                    highlightStyle.add(pss.get(index));
                                else
                                    highlightStyle.add(other);
                            }
                            else
                                normalGeometry.add(partList);
                            num = num + count;
                        }
                    } else {
                        if (isHighLight) {
                            highlightGeometry.add(geoPoints);
                            if (pss != null && pss.size() > index)
                                highlightStyle.add(pss.get(index));
                            else
                                highlightStyle.add(other);
                        }
                        else
                            normalGeometry.add(geoPoints);
                    }
                }
            }

            if (highlightGeometry != null || normalGeometry != null) {
                ModelResult data = new ModelResult(highlightGeometry, highlightStyle, normalGeometry, other);
                Message msg = new Message();
                msg.obj = data;
                msg.what = Common.QUERY_MODEL;
                handler.sendMessage(msg);
            }
        }
    }

    private static class MyGetFeaturesEventListener extends GetFeaturesByGeometryService.GetFeaturesEventListener {
        private GetFeaturesResult lastResult;

        public MyGetFeaturesEventListener() {
            super();
            // TODO Auto-generated constructor stub
        }

        public GetFeaturesResult getReult() {
            return lastResult;
        }

        @Override
        public void onGetFeaturesStatusChanged(Object sourceObject, EventStatus status) {
            if (sourceObject instanceof GetFeaturesResult && status.equals(EventStatus.PROCESS_COMPLETE)) {
                lastResult = (GetFeaturesResult) sourceObject;
            }
        }
    }

    private static List<Point2D> getPiontsFromGeometry(Geometry geometry) {
        List<Point2D> geoPoints = new ArrayList<Point2D>();
        com.supermap.services.components.commontypes.Point2D[] points = geometry.points;
        for (com.supermap.services.components.commontypes.Point2D point : points) {
            Point2D geoPoint = new Point2D(point.x, point.y);
            geoPoints.add(geoPoint);
        }
        return geoPoints;
    }

    public static ObjectInfo getBuildingInfo(String parkId, String buildingId) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        sqlParameters.datasetNames = new String[] { parkId+":Buildings" };
        sqlParameters.toIndex = 9999;
        QueryParameter queryParameter = new QueryParameter();
        queryParameter.attributeFilter = "BuildingId = \"" + buildingId + "\"";
        sqlParameters.queryParameter = queryParameter;

        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);
        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GetFeaturesResult building = listener.getReult();
        if (building != null && building.featureCount > 0) {
            Feature feature = building.features[0];
            return new ObjectInfo(feature.fieldNames, feature.fieldValues);
        }

        return null;
    }

    public static ObjectInfo getObjectInfo(String parkId, double lat, double lng) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        sqlParameters.datasetNames = new String[] { parkId+":Buildings" };
        sqlParameters.toIndex = 9999;
        QueryParameter queryParameter = new QueryParameter();
        queryParameter.attributeFilter = String.format("SMSDRIW <= %f AND SMSDRIE >= %f AND SMSDRIN >= %f AND SMSDRIS <= %f", lng, lng, lat, lat);
        sqlParameters.queryParameter = queryParameter;

        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);

        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GetFeaturesResult building = listener.getReult();
        if (building != null && building.featureCount > 0) {
            Feature feature = building.features[0];
            return new ObjectInfo(feature.fieldNames, feature.fieldValues);
        }

        return null;
    }

    public static List<ObjectInfo> getObjects(String table, String filter) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        String [] ds = table.split(",");
        String [] datasets = new String[ds.length];
        for(int i=0; i < ds.length; i ++){
            datasets[i] = Common.parkId()+":"+ds[i];
        }
        sqlParameters.datasetNames = datasets;
        sqlParameters.toIndex = 999999;
        QueryParameter queryParameter = new QueryParameter();
        queryParameter.attributeFilter = filter;
        sqlParameters.queryParameter = queryParameter;

        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);

        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GetFeaturesResult result = listener.getReult();
        if (result != null && result.featureCount > 0) {
            List<ObjectInfo> ret = new ArrayList<>();
            for (Feature feature : result.features)
                ret.add(new ObjectInfo(feature.fieldNames, feature.fieldValues));
            return ret;
        }

        return null;
    }

    public static List<BuildingResult> getBuildings() {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        sqlParameters.datasetNames = new String[] { Common.parkId() + ":buildings" };
        sqlParameters.toIndex = 9999;
        QueryParameter queryParameter = new QueryParameter();
        queryParameter.name = "buildings@" + Common.parkId();
        sqlParameters.queryParameter = queryParameter;

        GetFeaturesBySQLService sqlService = new GetFeaturesBySQLService(Common.getHost() + Common.DATA_URL());
        MyGetFeaturesEventListener listener = new MyGetFeaturesEventListener();
        sqlService.process(sqlParameters, listener);
        try {
            listener.waitUntilProcessed();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<BuildingResult> buildings = null;
        GetFeaturesResult building = listener.getReult();
        if (building != null && building.features != null) {
            buildings = new ArrayList<>();
            for (Feature feature : building.features) {
                Geometry geometry = feature.geometry;
                List<Point2D> geoPoints = getPiontsFromGeometry(geometry);
                BuildingResult br = new BuildingResult(feature, geoPoints);
                buildings.add(br);
            }
        }

        return buildings;
    }
}
