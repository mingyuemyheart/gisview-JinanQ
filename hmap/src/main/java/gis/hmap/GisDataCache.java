package gis.hmap;

import android.content.ContentValues;
import android.content.Context;
//import android.database.Cursor;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import com.supermap.android.commons.EventStatus;
import com.supermap.android.data.GetFeaturesByGeometryService;
import com.supermap.android.data.GetFeaturesBySQLParameters;
import com.supermap.android.data.GetFeaturesBySQLService;
import com.supermap.android.data.GetFeaturesResult;
import com.supermap.services.components.commontypes.Feature;
import com.supermap.services.components.commontypes.Geometry;
import com.supermap.services.components.commontypes.GeometryType;
import com.supermap.services.components.commontypes.Point2D;
import com.supermap.services.components.commontypes.QueryParameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Ryan on 2019/6/2.
 */

final class GisDataCache {
    private static GisDataCache _instance = null;
    private Context context = null;
//    private Object initIndoorlock = new Object();
//    private Object readIndoorlock = new Object();

    private AtomicInteger indoorCacheCounter = new AtomicInteger();
    private HashMap<String, Drawable> indoorMapCache = new HashMap<>();
    private HashMap<String, Feature> buildingCache = new HashMap<>();
    private HashMap<String, Feature[]> floorsCache = new HashMap<>();
    private HashMap<String, Feature[]> floorLinesCache = new HashMap<>();

    private GisDataCache(Context context) {
        this.context = context;
    }

    public static GisDataCache getInstance(Context context) {
        if (_instance == null)
            _instance = new GisDataCache(context);

        return _instance;
    }

    public void initIndoorMaps() {
        Common.fixedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                Feature[] all = queryAllBuildings();
                if (all != null) {
                    for (Feature building : all) {
                        Common.fixedThreadPool.execute(new InitFloorMap(building));
                    }

//                    synchronized (initIndoorlock) {
                        while (indoorCacheCounter.get() < all.length) {
                            try {
                                Thread.sleep(200);
                            } catch (Exception e) {
                            }
                        }
//                        synchronized (readIndoorlock) {
//                            IndoorDb indoor = new IndoorDb(context);
//                            indoor.deleteDatabase(context);
//                        }
//                    }
                }
            }
        });
    }

    public Feature getBuilding(String buildingId) {
        String idKey = String.format("%s.%s", Common.parkId(), buildingId);
        if (buildingCache.containsKey(idKey)) {
            return buildingCache.get(idKey);
        } else
            return null;
//            synchronized (readIndoorlock) {
//                IndoorDb indoor = new IndoorDb(context);
//                Feature feature = indoor.queryBuilding(buildingId);
//
//                return  feature;
//            }
    }

    public Feature[] getFloor(String buildingId, String floorId) {
        String idKey = String.format("%s.%s", Common.parkId(), buildingId);
        if (buildingCache.containsKey(idKey)) {
            idKey = String.format("%s.%s.%s", Common.parkId(), buildingId, floorId);
            Feature[] floor = null;
            Feature[] floorLine = null;
            if (floorsCache.containsKey(idKey))
                floor = floorsCache.get(idKey);
            if (floorLinesCache.containsKey(idKey))
                floorLine = floorLinesCache.get(idKey);
            int cnt = 0;
            if (floor != null)
                cnt += floor.length;
            if (floorLine != null)
                cnt += floorLine.length;
            Feature[] result = new Feature[cnt];
            cnt = 0;
            if (floor != null)
                for (Feature feature : floor)
                    result[cnt++] = feature;
            if (floorLine != null)
                for (Feature feature : floorLine)
                    result[cnt++] = feature;

            return result;
        } else
            return null;
//            synchronized (readIndoorlock) {
//                IndoorDb indoor = new IndoorDb(context);
//                Feature[] features = indoor.queryFloor(buildingId, floorId);
//
//                return features;
//            }
    }

    private Feature[] queryAllBuildings() {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        sqlParameters.datasetNames = new String[] { Common.parkId() + ":buildings" };
        sqlParameters.toIndex = 99999;
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

        GetFeaturesResult building = listener.getReult();
        if (building != null && building.features != null) {
            return building.features;
        } else
            return null;
    }

    private Feature[] queryFloors(String buildingId, String floorId) {
        GetFeaturesBySQLParameters sqlParameters = new GetFeaturesBySQLParameters();
        sqlParameters.datasetNames = new String[] { Common.parkId()+":"+floorId };
        sqlParameters.toIndex = 99999;
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

        GetFeaturesResult floor = listener.getReult();
        if (floor != null && floor.features != null) {
            return floor.features;
        } else
            return null;
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

    public static String getFeatureValue(Feature feature, String fieldName) {
        String ret = "";
        for (int i=0; i<feature.fieldNames.length; i++) {
            if (feature.fieldNames[i].equalsIgnoreCase(fieldName)) {
                ret = feature.fieldValues[i];
                break;
            }
        }

        return ret;
    }

    private class InitFloorMap implements Runnable {
        private Feature building;

        public InitFloorMap(Feature building) {
            this.building = building;
        }

        @Override
        public void run() {
            String buildingId = getFeatureValue(building, "BUILDINGID");
            String idkey = String.format("%s.%s", Common.parkId(), buildingId);

            String floorlist = getFeatureValue(building, "FLOORLIST");
            HashMap<String, Feature[]> floorsFeature = new HashMap<>();
            HashMap<String, Feature[]> floorLinesFeature = new HashMap<>();
            if (!TextUtils.isEmpty(floorlist)) {
                String[] floors = floorlist.split(",");
                for (String floorId : floors) {
                    String idkey2 = String.format("%s.%s.%s", Common.parkId(), buildingId, floorId);
                    Feature[] features = queryFloors(buildingId, floorId);
                    floorsFeature.put(idkey2, features);
                    features = queryFloors(buildingId, floorId+"_LINE");
                    floorLinesFeature.put(idkey2, features);
                }
            }
            floorsCache.putAll(floorsFeature);
            floorLinesCache.putAll(floorLinesFeature);
            buildingCache.put(idkey, building);
            Log.i("--indoor", "load: " + idkey);
            indoorCacheCounter.incrementAndGet();

//            synchronized(initIndoorlock) {
//                Log.i("--indoor", "update: " + idkey);
//
//                IndoorDb indoor = new IndoorDb(context);
//                indoor.insertBuilding(idkey, building);
//                indoor.insertFloor(floorsFeature);
//                indoor.insertFloorLine(floorLinesFeature);
//                indoor.closeDb();
//                Log.i("--indoor", "done: " + idkey);
//            }
        }
    }

//    public class IndoorDb extends SQLiteOpenHelper {
//        private static final String DATABASENAME = "IndoorData.db";
//        private static final String ENTITY_BUILDING = "Building";
//        private static final String ENTITY_FLOOR = "Floor";
//        private static final String ENTITY_FLOOR_LINE = "Floor_Line";
//        private SQLiteDatabase db = null;
//
//        public IndoorDb(Context context) {
//            super(context, DATABASENAME, null, 1);
//        }
//
//        @Override
//        public void onCreate(SQLiteDatabase db) {
//            db.execSQL("create table geometries (" +
//                    "id integer primary key," +
//                    "idkey varchar(255)," +
//                    "building varchar(255)," +
//                    "entity varchar(32))");
//            db.execSQL("create table geometries_feature (" +
//                    "id integer primary key," +
//                    "pid integer," +
//                    "feature varchar(255)," +
//                    "value TEXT)");
//            db.execSQL("create table geometries_parts (" +
//                    "id integer primary key," +
//                    "pid integer," +
//                    "geotype varchar(32)," +
//                    "cnt integer)");
//            db.execSQL("create table geometries_points (" +
//                    "id integer primary key," +
//                    "partid integer," +
//                    "x REAL," +
//                    "y REAL)");
//        }
//
//        @Override
//        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
//            //
//        }
//
//        public boolean deleteDatabase(Context context)
//        {
//            return context.deleteDatabase(DATABASENAME);
//        }
//
//        public void closeDb() {
//            if (db != null)
//                db.close();
//            db = null;
//        }
//
//        public void insertBuilding(String idkey, Feature feature) {
//            if (db == null)
//                db = this.getWritableDatabase();
//
//            insert(idkey, ENTITY_BUILDING, feature);
//        }
//        public void insertFloor(HashMap<String, Feature[]> floorsFeature) {
//            if (db == null)
//                db = this.getWritableDatabase();
//
//            db.beginTransaction();
//            try {
//                for (String key : floorsFeature.keySet()) {
//                    Feature[] features = floorsFeature.get(key);
//                    if (features != null) {
//                        for (Feature feature : features)
//                            insert(key, ENTITY_FLOOR, feature);
//                    }
//                }
//                db.setTransactionSuccessful();
//            } catch (Exception e) {
//                Log.e("--indoor", e.getMessage());
//            } finally {
//                db.endTransaction();
//            }
//        }
//        public void insertFloorLine(HashMap<String, Feature[]> floorLinesFeature) {
//            if (db == null)
//                db = this.getWritableDatabase();
//
//            db.beginTransaction();
//            try {
//                for (String key : floorLinesFeature.keySet()) {
//                    Feature[] features = floorLinesFeature.get(key);
//                    if (features != null) {
//                        for (Feature feature : features)
//                            insert(key, ENTITY_FLOOR_LINE, feature);
//                    }
//                }
//                db.setTransactionSuccessful();
//            } catch (Exception e) {
//                Log.e("--indoor", e.getMessage());
//            } finally {
//                db.endTransaction();
//            }
//        }
//
//        private long insert(String idkey, String entity, Feature feature) {
//            int index = 0;
//            for (; index < feature.fieldNames.length; index++) {
//                if (feature.fieldNames[index].equalsIgnoreCase("BUILDINGID"))
//                    break;
//            }
//            String buildingKey = String.format("%s.%s", Common.parkId(), feature.fieldValues[index]);
//
//            ContentValues values = new ContentValues();
//            values.put("idkey", idkey);
//            values.put("building", buildingKey);
//            values.put("entity", entity);
//            long pk = db.insert("geometries", null, values);
//
//            for (index = 0; index < feature.fieldNames.length; index++) {
//                values = new ContentValues();
//                values.put("pid", pk);
//                values.put("feature", feature.fieldNames[index]);
//                values.put("value", feature.fieldValues[index]);
//                db.insert("geometries_feature", null, values);
//            }
//
//            for (int length : feature.geometry.parts) {
//                values = new ContentValues();
//                values.put("pid", pk);
//                values.put("geotype", feature.geometry.type.name());
//                values.put("cnt", length);
//                long partid = db.insert("geometries_parts", null, values);
//                for (Point2D point : feature.geometry.points) {
//                    values = new ContentValues();
//                    values.put("partid", partid);
//                    values.put("x", point.x);
//                    values.put("y", point.y);
//                    db.insert("geometries_points", null, values);
//                }
//            }
//
//            return pk;
//        }
//
//        public Feature queryBuilding(String buildingId) {
//            if (db == null)
//                db = this.getWritableDatabase();
//            Feature feature = null;
//
//            String buildingKey = String.format("%s.%s", Common.parkId(), buildingId);
//            Cursor cursor = db.query("geometries", new String[] {"id"}, "building = ? and entity = ?",
//                    new String[]{buildingKey, ENTITY_BUILDING}, null, null, null);
//            if (cursor.moveToNext()) {
//                feature = new Feature();
//                feature.setID(cursor.getInt(0));
//                String pid = String.format("%d", feature.getID());
//                cursor.close();
//
//                cursor = db.query("geometries_feature", new String[] {"feature", "value"}, "pid = ?",
//                        new String[]{pid}, null, null, null);
//                List<String> names = new ArrayList<>();
//                List<String> values = new ArrayList<>();
//                while (cursor.moveToNext()) {
//                    names.add(cursor.getString(0));
//                    values.add(cursor.getString(1));
//                }
//                feature.fieldNames = names.toArray(new String[0]);
//                feature.fieldValues = values.toArray(new String[0]);
//                cursor.close();
//
//                List<Integer> parts = new ArrayList<>();
//                List<Point2D> points = new ArrayList<>();
//                cursor = db.query("geometries_parts", new String[] {"id", "cnt"}, "pid = ?",
//                        new String[]{pid}, null, null, null);
//                while (cursor.moveToNext()) {
//                    String partid = cursor.getString(0);
//                    parts.add(cursor.getInt(1));
//                    Cursor c = db.query("geometries_points", new String[] {"x", "y"}, "partid = ?",
//                            new String[]{partid}, null, null, null);
//                    while (c.moveToNext()) {
//                        Point2D point = new Point2D();
//                        point.x = c.getDouble(0);
//                        point.y = c.getDouble(1);
//                        points.add(point);
//                    }
//                    c.close();
//                }
//                cursor.close();
//                feature.geometry = new Geometry();
//                feature.geometry.type = GeometryType.REGION;
//                feature.geometry.parts = new int[parts.size()];
//                for (int i=0; i<feature.geometry.parts.length; i++)
//                    feature.geometry.parts[i] = parts.get(i);
//                feature.geometry.points = points.toArray(new Point2D[0]);
//            } else
//                cursor.close();
//
//            return feature;
//        }
//
//        public Feature[] queryFloor(String buildingId, String floorId) {
//            if (db == null)
//                db = this.getWritableDatabase();
//            List<Feature> features = null;
//
//            String idkey = String.format("%s.%s.%s", Common.parkId(), buildingId, floorId);
//            Cursor geocursor = db.query("geometries", new String[] {"id, entity"}, "idkey = ? and entity <> ?",
//                    new String[]{idkey, ENTITY_BUILDING}, null, null, null);
//            features = new ArrayList<>();
//            while (geocursor.moveToNext()) {
//                Feature feature = new Feature();
//                feature.setID(geocursor.getInt(0));
//                String pid = String.format("%d", feature.getID());
//                String entity = geocursor.getString(1);
//
//                Cursor cursor = db.query("geometries_feature", new String[] {"feature", "value"}, "pid = ?",
//                        new String[]{pid}, null, null, null);
//                List<String> names = new ArrayList<>();
//                List<String> values = new ArrayList<>();
//                while (cursor.moveToNext()) {
//                    names.add(cursor.getString(0));
//                    values.add(cursor.getString(1));
//                }
//                feature.fieldNames = names.toArray(new String[0]);
//                feature.fieldValues = values.toArray(new String[0]);
//                cursor.close();
//
//                List<Integer> parts = new ArrayList<>();
//                List<Point2D> points = new ArrayList<>();
//                cursor = db.query("geometries_parts", new String[] {"id", "cnt"}, "pid = ?",
//                        new String[]{pid}, null, null, null);
//                while (cursor.moveToNext()) {
//                    String partid = cursor.getString(0);
//                    parts.add(cursor.getInt(1));
//                    Cursor c = db.query("geometries_points", new String[] {"x", "y"}, "partid = ?",
//                            new String[]{partid}, null, null, null);
//                    while (c.moveToNext()) {
//                        Point2D point = new Point2D();
//                        point.x = c.getDouble(0);
//                        point.y = c.getDouble(1);
//                        points.add(point);
//                    }
//                    c.close();
//                }
//                cursor.close();
//                feature.geometry = new Geometry();
//                if (entity.equalsIgnoreCase(ENTITY_FLOOR))
//                    feature.geometry.type = GeometryType.REGION;
//                else
//                    feature.geometry.type = GeometryType.LINE;
//                feature.geometry.parts = new int[parts.size()];
//                for (int i=0; i<feature.geometry.parts.length; i++)
//                    feature.geometry.parts[i] = parts.get(i);
//                feature.geometry.points = points.toArray(new Point2D[0]);
//                features.add(feature);
//            }
//            geocursor.close();
//
//            return features == null ? null : features.toArray(new Feature[0]);
//        }
//    }
}
