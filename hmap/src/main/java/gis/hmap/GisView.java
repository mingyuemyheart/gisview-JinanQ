package gis.hmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathDashPathEffect;
import android.graphics.PathEffect;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.CoordinateConverter;
import com.amap.api.location.DPoint;
import com.supermap.android.maps.AbstractTileLayerView;
import com.supermap.android.maps.BoundingBox;
import com.supermap.android.maps.CoordinateReferenceSystem;
import com.supermap.android.maps.DefaultItemizedOverlay;
import com.supermap.android.maps.DrawableOverlay;
import com.supermap.android.maps.LayerView;
import com.supermap.android.maps.LineOverlay;
import com.supermap.android.maps.MBTilesLayerView;
import com.supermap.android.maps.MapController;
import com.supermap.android.maps.MapView;
import com.supermap.android.maps.MultiPolygon;
import com.supermap.android.maps.MultiPolygonOverlay;
import com.supermap.android.maps.Overlay;
import com.supermap.android.maps.Point2D;
import com.supermap.android.maps.PolygonOverlay;
import com.supermap.android.maps.Tile;
import com.supermap.services.components.commontypes.Feature;
import com.supermap.services.components.commontypes.GeometryType;
import com.supermap.services.components.commontypes.Rectangle2D;
import com.supermap.services.components.commontypes.VectorTileData;


import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import cn.hw.ics.locsdk.LocClient;
import cn.hw.ics.locsdk.LocHttpClient;
import cn.hw.ics.locsdk.LocationServer;

/**
 * TODO: document your custom view class.
 */
public class GisView extends RelativeLayout
        implements Overlay.OverlayTapListener, MapView.MapViewEventListener, AMapLocationListener {
    public static  String TAG = "GISView";

    private static final String calculatdRouteKey = "[calculatdRoute]";
    private static final String indoorKeyTemplate = "indoor[building:%s]";
    private static final String basementKeyTemplate = "indoor[basement:%s]";
    private static final String indoorStyleKeyTemplate = "indoorStyle[%s:%s:%s]";
    private static final String perimeterKey = "[perimeter]";
    private static final String modelsKey = "[models]";
    private static final String buildingTouchKey = "[buildingTouch]";

    private static GisView _instance = null;
    protected MapView mapView;
    protected AbstractTileLayerView mapLayer;
    protected Map<String, List<Overlay>> namedOverlays;
    protected LineOverlay routeOverlay;
    protected List<MarkerListener> mMarkerListener;
    protected List<BuildingListener> mBuildingListener;
    protected List<ModelListener> mModelListener;
    protected List<ZoomListener> mZoomListener;
    protected List<MapListener> mMapListener;
    protected IndoorCallback indoorCallback;
    protected static List<LocationListener> mPosListener;
    protected TouchOverlay touchOverlay;
    protected int mHideLevel = 1;
    protected List<QueryUtils.BuildingResult> buildings;
    protected Handler handler;
    protected List<HeatmapDrawable> heatmapList;
    protected HashMap<String, IndoorMapData> openIndoors;
    protected HashMap<String, QueryUtils.BasementMapResult> openBasements;
    protected List<String> openedMaps;
    protected NetWorkAnalystUtil.CalculatedRoute calculatedRoute;
    protected String currIndoorMap = "";
    protected HashMap<String, GeneralMarker> defaultFacilities;
    protected boolean isShowHighLight = false;
    protected int maxZoomLevel = 5;
    protected static HashMap<String, String> floorMapper;
    protected int lastZoomLevel = -1;
    protected int indoorZoomLevel = 0;
    protected ZoomToIndoorListener mZoomToIndoorListener;
    protected String defaultZoomIndoor = "F01";

    //声明AMapLocationClient类对象
    public AMapLocationClient mLocationClient = null;
    protected static boolean isLocatorRunning = false;
    protected static LocationServer locationServer = null;

    public GisView(Context context) {
        super(context);
        init(context,null, 0,0);
    }

    public GisView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0,0);
    }

    public GisView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle,0);
    }

    public GisView(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        init(context, attrs, defStyle, defStyleRes);
    }

    @Override
    public void onLocationChanged(AMapLocation amapLocation) {
        if (amapLocation.getErrorCode() == 0) {
            if (!BestLocation.isIndoorValidate()) {
                double lat = amapLocation.getLatitude();
                double lng = amapLocation.getLongitude();
                if (amapLocation.getCoordType() == AMapLocation.COORD_TYPE_GCJ02) {
                    double[] res = CoordsHelper.gcj02_To_Gps84(lat, lng);
                    lat = res[0]; lng = res[1];
                }
                BestLocation.updateGlobal(
                        lat,
                        lng,
                        amapLocation.getAddress(),
                        amapLocation.getTime());
                if (amapLocation.getBearing() != 0.0)
                    BestLocation.getInstance().direction = amapLocation.getBearing();
                GeoLocation p = getMyLocation();
                if (p != null && mPosListener.size() > 0 && isLocatorRunning) {
                    LocationEvent le = new LocationEvent(
                            BestLocation.getInstance().location,
                            BestLocation.getInstance().lng,
                            BestLocation.getInstance().lat,
                            BestLocation.getInstance().direction,
                            "",
                            "");
                    for (LocationListener listener : mPosListener) {
                        try {
                            listener.onLocation(le);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }else {
            //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
            Log.e("AmapError","location Error, ErrCode:"
                    + amapLocation.getErrorCode() + ", errInfo:"
                    + amapLocation.getErrorInfo());
        }
    }

    /**
     * 采用最好的方式获取定位信息
     */
    public GeoLocation getMyLocation() {
        GeoLocation p = null;
        if (BestLocation.isIndoorValidate()) {
            p = new GeoLocation();
            p.address = "室内位置";
            p.lng = BestLocation.getInstance().lng;
            p.lat = BestLocation.getInstance().lat;
            p.direction = BestLocation.getInstance().direction;
            p.buildingId = BestLocation.getInstance().buildingId;
            p.floorId = BestLocation.getInstance().floorId;
            p.time = new Date(BestLocation.getInstance().getUpdateTime());

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Common.getLogger(null).log(Level.INFO, String.format("getMyLocation: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                    p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));

            return p;
        } else if (BestLocation.isGlobalValidate()) {
            p = new GeoLocation();
            p.address = BestLocation.getInstance().location;
            p.lng = BestLocation.getInstance().lng;
            p.lat = BestLocation.getInstance().lat;
            p.direction = BestLocation.getInstance().direction;
            p.buildingId = "";
            p.floorId = "";
            p.time = new Date(BestLocation.getInstance().getUpdateTime());

            if (p.lat != 0 && p.lng != 0) {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Common.getLogger(null).log(Level.INFO, String.format("getMyLocation: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                        p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));
                return p;
            }
        }

        Criteria c = new Criteria();//Criteria类是设置定位的标准信息（系统会根据你的要求，匹配最适合你的定位供应商），一个定位的辅助信息的类
        c.setPowerRequirement(Criteria.POWER_LOW);//设置低耗电
        c.setAltitudeRequired(true);//设置需要海拔
        c.setBearingAccuracy(Criteria.ACCURACY_COARSE);//设置COARSE精度标准
        c.setAccuracy(Criteria.ACCURACY_LOW);//设置低精度
        //... Criteria 还有其他属性，就不一一介绍了
        Location best = LocationUtils.getBestLocation(getContext(), c);
        if (best == null) {
            Location net = LocationUtils.getNetWorkLocation(getContext());
            if (net == null) {
                return null;
            } else {
                BestLocation.updateGlobal(net.getLatitude(), net.getLongitude(), "实时位置", net.getTime());
                p = new GeoLocation();
                p.address = "网络位置";
                p.lng = net.getLongitude();
                p.lat = net.getLatitude();
                p.direction = BestLocation.getInstance().direction;
                p.time = new Date(net.getTime());

                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Common.getLogger(null).log(Level.INFO, String.format("getMyLocation: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                        p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));

                return p;
            }
        } else {
            BestLocation.updateGlobal(best.getLatitude(), best.getLongitude(), "实时位置", best.getTime());
            p = new GeoLocation();
            p.address = "实时位置";
            p.lng = best.getLongitude();
            p.lat = best.getLatitude();
            p.direction = BestLocation.getInstance().direction;
            p.time = new Date(best.getTime());

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Common.getLogger(null).log(Level.INFO, String.format("getMyLocation: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                    p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));

            return p;
        }
    }

    public static GeoLocation getMyLocation(Context context) {
        if (_instance != null)
            return _instance.getMyLocation();

        GeoLocation p = null;
        if (BestLocation.isIndoorValidate()) {
            p = new GeoLocation();
            p.address = BestLocation.getInstance().location;
            p.lng = BestLocation.getInstance().lng;
            p.lat = BestLocation.getInstance().lat;
            p.direction = BestLocation.getInstance().direction;
            p.buildingId = BestLocation.getInstance().buildingId;
            p.floorId = BestLocation.getInstance().floorId;
            p.time = new Date(BestLocation.getInstance().getUpdateTime());

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Common.getLogger(null).log(Level.INFO, String.format("getMyLocation2: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                    p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));

            return p;
        }
//        else if (BestLocation.isGlobalValidate()) {
//            p = new GeoLocation();
//            p.address = BestLocation.getInstance().location;
//            p.lng = BestLocation.getInstance().lng;
//            p.lat = BestLocation.getInstance().lat;
//            p.buildingId = "";
//            p.floorId = "";
//            p.time = new Date(BestLocation.getInstance().getUpdateTime());
//
//            return p;
//        }

        Criteria c = new Criteria();//Criteria类是设置定位的标准信息（系统会根据你的要求，匹配最适合你的定位供应商），一个定位的辅助信息的类
        c.setPowerRequirement(Criteria.POWER_LOW);//设置低耗电
        c.setAltitudeRequired(true);//设置需要海拔
        c.setBearingAccuracy(Criteria.ACCURACY_COARSE);//设置COARSE精度标准
        c.setAccuracy(Criteria.ACCURACY_LOW);//设置低精度
        //... Criteria 还有其他属性，就不一一介绍了
        Location best = LocationUtils.getBestLocation(context, c);
        if (best == null) {
            Location net = LocationUtils.getNetWorkLocation(context);
            if (net == null) {
                return null;
            } else {
                BestLocation.updateGlobal(net.getLatitude(), net.getLongitude(), "实时位置", net.getTime());
                p = new GeoLocation();
                p.address = "网络位置";
                p.lng = net.getLongitude();
                p.lat = net.getLatitude();
                p.direction = BestLocation.getInstance().direction;
                p.time = new Date(net.getTime());

                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Common.getLogger(null).log(Level.INFO, String.format("getMyLocation2: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                        p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));

                return p;
            }
        } else {
            BestLocation.updateGlobal(best.getLatitude(), best.getLongitude(), "实时位置", best.getTime());
            p = new GeoLocation();
            p.address = "实时位置";
            p.lng = best.getLongitude();
            p.lat = best.getLatitude();
            p.direction = BestLocation.getInstance().direction;
            p.time = new Date(best.getTime());

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Common.getLogger(null).log(Level.INFO, String.format("getMyLocation2: latitude: %f, longitude: %f, updateTime: %s, direction: %f, address: %s, buildingId: %s, floorId: %s",
                    p.lat, p.lng, formatter.format(p.time), p.direction, p.address, p.buildingId, p.floorId));

            return p;
        }
    }

    private void init(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        _instance = this;
        View inflate = inflate(context, R.layout.gisview, this);
        mapView = inflate.findViewById(R.id.mapview);
        mapView.addMapViewEventListener(this);
        touchOverlay = new TouchOverlay();
        mapView.getOverlays().add(touchOverlay);

        namedOverlays = new HashMap<>();
        mMarkerListener = new ArrayList<>();
        mBuildingListener = new ArrayList<>();
        mModelListener = new ArrayList<>();
        mZoomListener = new ArrayList<>();
        mMapListener = new ArrayList<>();
        handler = new ExecuteFinished(this);
        heatmapList = new ArrayList<>();
        openIndoors = new HashMap<>();
        openBasements = new HashMap<>();
        openedMaps = new ArrayList<>();
        defaultFacilities = new HashMap<>();
        mZoomToIndoorListener = null;

        //初始化日志
        Common.getLogger(context).log(Level.INFO, "gis module init.");
        //初始化定位
        mLocationClient = new AMapLocationClient(context);
        //设置定位回调监听
        mLocationClient.setLocationListener(this);
        AMapLocationClientOption option = new AMapLocationClientOption();
        option.setLocationPurpose(AMapLocationClientOption.AMapLocationPurpose.Transport);
        option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        option.setOnceLocation(false);
        option.setNeedAddress(true);
        option.setSensorEnable(true);
        option.setHttpTimeOut(10000);
        if(mLocationClient != null){
            mLocationClient.setLocationOption(option);
            //设置场景模式后最好调用一次stop，再调用start以保证场景模式生效
            mLocationClient.stopLocation();
            mLocationClient.startLocation();
        }
    }

    public void setGisServer(String gisUrl) {
        Common.CreateInstance(gisUrl);
    }

    public void setRTLSServer(String rtlsUrl) {
        Common.initRtlsLicenseHost(rtlsUrl);
    }

    public void setFloorMapper(HashMap<String, String> mapper) {
        floorMapper = mapper;
    }

    public static void initEngine(Context context, String key, String secret,
                           String baseUrl, String evnUri, boolean isGate, String gateAppKey, String gateId, String gateUrl) {
        Common.getLogger(null).log(Level.INFO, String.format("Init iVAS: setBaseUrl=%s; setURI=%s; setIsGateEv=%s; setGateId=%s; setGateUrl=%s",
                baseUrl, evnUri, isGate ? "true" : "false", gateId, gateUrl));
        LocHttpClient.setBaseUrl(baseUrl);
        LocHttpClient.setURI(evnUri);
        LocHttpClient.setGateAppKey(gateAppKey);
        LocHttpClient.setGateId(gateId);
        LocHttpClient.setGateUrl(gateUrl);
        LocHttpClient.setIsGateEv(isGate);
        mPosListener = new ArrayList<>();

        if (locationServer != null) {
            locationServer.stopLoc();
            locationServer.destory();
            locationServer = null;
        }
        //初始化楼层映射
        floorMapper = new HashMap<>();
        floorMapper.put("-5", "B5");
        floorMapper.put("-4", "B4");
        floorMapper.put("-3", "B3");
        floorMapper.put("-2", "B2");
        floorMapper.put("-1", "B1");
        floorMapper.put("1", "F1");
        floorMapper.put("2", "F1A");
        floorMapper.put("3", "F2");
        floorMapper.put("4", "F3");
        floorMapper.put("5", "F4");
        floorMapper.put("6", "F5");
        floorMapper.put("7", "F6");
        floorMapper.put("8", "F7");
        floorMapper.put("9", "F8");
        floorMapper.put("10", "F9");
        floorMapper.put("11", "F10");
        floorMapper.put("12", "F11");
        floorMapper.put("13", "F12");
        floorMapper.put("14", "F13");
        floorMapper.put("15", "F14");
        floorMapper.put("16", "F15");
        floorMapper.put("17", "F16");
        floorMapper.put("18", "F17");
        floorMapper.put("19", "F18");
        floorMapper.put("20", "F19");
        floorMapper.put("21", "F20");
        //初始化定位
        locationServer = LocationServer.getInstance(context, key, secret, new LocClient.OnLocResultReceivedListener() {
            @Override
            public void onSuccess(int i, Map<String, String> map) {
                String msg = "iVas success: ";
                for (Map.Entry<String, String> entry : map.entrySet())
                    msg += String.format("%s=%s; ", entry.getKey(), entry.getValue());
                Common.getLogger(null).log(Level.INFO, msg);
                double lat, lng;
                long timestamp;
                lat = Double.parseDouble(map.get("latitude"));
                lng = Double.parseDouble(map.get("longitude"));
                timestamp = Long.parseLong(map.get("timestamp"));
                if (map.get("loctype").equalsIgnoreCase("200")) {
                    //室内
                    String locstr = map.get("location");
                    Log.e("ivas", String.format("onSuccess->locstr: %s", locstr));
//                    Integer floor = 0;
//                    try {
//                        JSONObject json = new JSONObject(locstr);
//                        floor = json.getInt("z");
//                    } catch (Exception e) {
//                        Log.e("iVas", e.getMessage());
//                    }
                    String floor = "";
                    try {
                        floor = floorMapper.get(map.get("floor"));
                    } catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                    BestLocation.updateIndoor(lat, lng, map.get("building_id"), floor, timestamp);
                    BestLocation.getInstance().location = "室内位置";
                    if (mPosListener.size() > 0 && isLocatorRunning) {
                        LocationEvent le = new LocationEvent(
                                BestLocation.getInstance().location,
                                BestLocation.getInstance().lng,
                                BestLocation.getInstance().lat,
                                BestLocation.getInstance().direction,
                                BestLocation.getInstance().buildingId,
                                BestLocation.getInstance().floorId);
                        le.indoor = true;
                        for (LocationListener listener : mPosListener) {
                            try {
                                Common.getLogger(null).log(Level.INFO, String.format("ivas onSuccess: fire event %s", listener.toString()));
                                listener.onLocation(le);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    Log.e("ivas", String.format("onSuccess: %d", i));
                } else if (map.get("loctype").equalsIgnoreCase("100")) {
                    //室外
                    BestLocation.goOutdoor();
                }
            }

            @Override
            public void onFailed(String s, String s1) {
                Log.e("ivas", String.format("code: %s, error: %s", s, s1));
                Common.getLogger(null).log(Level.INFO, String.format("ivas onFailed: code: %s, error: %s", s, s1));
            }
        });
    }

    public void deinitEngine(){
        if (mLocationClient != null) {
            mLocationClient.stopLocation();
        }
    }

    public void getLocationOfAddress(final String address,final int count, final GeoServiceCallback callback){


        new Thread(new Runnable(){
            @Override
            public void run() {
               String ret = "[]";
                try {
                    ret =  GisView.getStringFromURL(Common.getHost() + Common.GEO_CODE_URL() + "?address="+address+"&fromIndex=0&toIndex=10&maxReturn=" + count);

                }
                catch (Exception ex){
                    Log.d(TAG, "getLocationOfAddress: "+ex.getMessage());
                }

                JSONArray arr = new JSONArray();
                try {
                    arr = new JSONArray(ret);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                GeoLocation [] res = new GeoLocation [arr.length()];


                for (int i = 0;i < arr.length(); i ++){
                    res[i] = new GeoLocation();
                    try {
                        JSONObject obj = arr.getJSONObject(i);
                        res[i].address = obj.getString("address");
                        res[i].lng = obj.getJSONObject("location").getDouble("x");
                        res[i].lat = obj.getJSONObject("location").getDouble("y") ;
                        res[i].score = obj.getDouble("score");
                    }catch (Exception e){

                    }

                }

                if(callback != null)
                    callback.onQueryAddressFinished(res);

            }
        }).start();
    }

    public void getAddressOfLocation(final double lng, final double lat,final double radius, final int count, final GeoServiceCallback callback){


        new Thread(new Runnable(){
            @Override
            public void run() {
                String ret = "[]";
                try {
                    String url = Common.getHost() + Common.GEO_DECODE_URL() + "?x=" + lng+ "&y=" + lat+ "&geoDecodingRadius="+radius+"&fromIndex=0&toIndex=10&maxReturn=" + count;
                    ret =  GisView.getStringFromURL(url);

                }
                catch (Exception ex){
                    Log.d(TAG, "getLocationOfAddress: "+ex.getMessage());
                }

                JSONArray arr = new JSONArray();
                try {
                    arr = new JSONArray(ret);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                GeoLocation [] res = new GeoLocation [arr.length()];


                for (int i = 0;i < arr.length(); i ++){
                    res[i] = new GeoLocation();
                    try {
                        JSONObject obj = arr.getJSONObject(i);
                        res[i].address = obj.getString("address");
                        res[i].lng = obj.getJSONObject("location").getDouble("x");
                        res[i].lat = obj.getJSONObject("location").getDouble("y") ;
                        res[i].score = obj.getDouble("score");
                    }catch (Exception e){

                    }

                }

                if(callback != null)
                    callback.onQueryAddressFinished(res);

            }
        }).start();
    }


    public static String getStringFromURL(String urlString) throws IOException, JSONException {
        HttpClient httpclient = new DefaultHttpClient();

        HttpGet httpget= new HttpGet(urlString);

        HttpResponse response = httpclient.execute(httpget);

        if(response.getStatusLine().getStatusCode()==200){
            String server_response = EntityUtils.toString(response.getEntity());
            Log.i("Server response", server_response );
            return server_response;
        } else {
            Log.i("Server response", "Failed to get server response" );
            return "[]";
        }
    }

    @Override
    public void onTap(Point2D point2D, MapView mapView) {
        List<Overlay> overlays = mapView.getOverlays();
        Rect rect = null;
        for (Overlay ov : overlays) {
            if (ov instanceof DefaultItemizedOverlay) {
                DefaultItemizedOverlay overlay = (DefaultItemizedOverlay) ov;
                if (overlay.getFocus() == null)
                    continue;

                MarkerEvent me = new MarkerEvent();
                me.eventType = TargetEvent.Press;
                me.position = new double[]{point2D.x, point2D.y};
                me.marker = ((OverlayItemEx) overlay.getFocus()).marker;
                me.width = me.marker.width;
                me.height = me.marker.height;
                me.tag = me.marker.tag;
                me.markerId = me.marker.markerId;

                Point base = mapView.toScreenPoint(new Point2D(me.marker.position[1], me.marker.position[0]));
                int wid = me.marker.width;
                int hei = me.marker.height;
                rect = new Rect(base.x - wid / 2, base.y - hei, base.x + wid / 2, base.y);

                for (MarkerListener listener : mMarkerListener) {
                    listener.markerEvent(me);
                }
                break;
            }
        }
        if (rect != null) {
            List<MarkerEvent> events = new ArrayList<>();
            for (Overlay ov : overlays) {
                if (ov instanceof DefaultItemizedOverlay) {
                    DefaultItemizedOverlay overlay = (DefaultItemizedOverlay) ov;

                    OverlayItemEx item = (OverlayItemEx) overlay.getItem(0);
                    Point base = mapView.toScreenPoint(new Point2D(item.marker.position[1], item.marker.position[0]));
                    int wid = item.marker.width;
                    int hei = item.marker.height;
                    Rect self = new Rect(base.x - wid / 2, base.y - hei, base.x + wid / 2, base.y);

                    if (!(rect.left > self.right || rect.right < self.left || rect.top > self.bottom || rect.bottom < self.top)) {

                        MarkerEvent me = new MarkerEvent();
                        me.eventType = TargetEvent.Press;
                        me.position = new double[]{point2D.x, point2D.y};
                        me.marker = item.marker;
                        me.width = me.marker.width;
                        me.height = me.marker.height;
                        me.tag = me.marker.tag;
                        me.markerId = me.marker.markerId;
                        events.add(me);
                    }
                }
            }
            for (MarkerListener listener : mMarkerListener) {
                listener.markerEvent(events.toArray(new MarkerEvent[0]));
            }
        }
    }

    @Override
    public void onTap(Point2D point2D, Overlay overlay, MapView mapView) {
        if (overlay instanceof PolygonOverlay) {
            if (mBuildingListener.size() > 0) {
                String ovKey = overlay.getKey();
                if (TextUtils.isEmpty(ovKey) || !ovKey.startsWith("building:"))
                    return;

                List<Overlay> overlays = mapView.getOverlays();
                for (Overlay ov : overlays) {
                    String key = ov.getKey();
                    if (!TextUtils.isEmpty(key) && key.startsWith("building:")) {
                        PolygonOverlay unselect = (PolygonOverlay) ov;
                        if (unselect != null)
                            unselect.setLinePaint(getBuildingSelectPaint(false));
                    }
                }
                PolygonOverlay ov = (PolygonOverlay) overlay;
                ov.setLinePaint(getBuildingSelectPaint(true));
                mapView.invalidate();

                if (buildings != null) {
                    for (QueryUtils.BuildingResult br : buildings) {
                        String k = String.format("building:[%d]", br.feature.getID());
                        if (ovKey.equalsIgnoreCase(k)) {
                            Point point = new Point();
                            mapView.getProjection().toPixels(point2D, point);
                            BuildingEvent be = new BuildingEvent(
                                    TargetEvent.Press,
                                    new double[]{point.x, point.y},
                                    br.feature.fieldNames,
                                    br.feature.fieldValues);
                            for (BuildingListener listener : mBuildingListener) {
                                listener.buildingEvent(be);
                            }
                        }
                    }
                }
            }
            if (mModelListener.size() > 0) {
                Iterator iter = openIndoors.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, IndoorMapData> entry = (Map.Entry<String, IndoorMapData>) iter.next();
                    IndoorMapData indoorMapData = entry.getValue();
                    for (ModelData modelData : indoorMapData.rooms) {
                        boolean isHit = false;
                        if (modelData.geometry != null) {
                            for (List<Point2D> geoPoints : modelData.geometry) {
                                isHit = isInPolygon(point2D, geoPoints.toArray(new Point2D[0]));
                                if (isHit)
                                    break;
                            }
                        }
                        if (isHit) {
                            ModelEvent me = new ModelEvent(
                                    TargetEvent.Press,
                                    new double[]{point2D.x, point2D.y},
                                    modelData.features);
                            for (ModelListener listener : mModelListener)
                                listener.modelEvent(me);
                            break;
                        }
                    }
                }
            }
        }
    }

    public boolean isInPolygon(Point2D point, Point2D[] points) {
        int nCross = 0;
        for (int i = 0; i < points.length; i++) {
            Point2D p1 = points[i];
            Point2D p2 = points[(i + 1) % points.length];
            // 求解 y=p.y 与 p1 p2 的交点
            // p1p2 与 y=p0.y平行
            if (p1.y == p2.y)
                continue;
            // 交点在p1p2延长线上
            if (point.y < Math.min(p1.y, p2.y))
                continue;
            // 交点在p1p2延长线上
            if (point.y >= Math.max(p1.y, p2.y))
                continue;
            // 求交点的 X 坐标
            double x = (double) (point.y - p1.y) * (double) (p2.x - p1.x)
                    / (double) (p2.y - p1.y) + p1.x;
            // 只统计单边交点
            if (x > point.x)
                nCross++;
        }
        return (nCross % 2 == 1);
    }

    @Override
    public void mapLoaded(MapView mapView) {
        //
    }

    @Override
    public void longTouch(MapView mapView) {
        fitMapWithNoBlank(mapView);
    }

    @Override
    public void touch(MapView mapView) {
    }

    @Override
    public void moveStart(MapView mapView) {
        fitHeatmapToView(false);
        refreshOpenMaps();
        indoorDetect();
    }

    @Override
    public void move(MapView mapView) {
        fitMapWithNoBlank(mapView);
        refreshOpenMaps();
        indoorDetect();
    }

    @Override
    public void moveEnd(MapView mapView) {
        fitMapWithNoBlank(mapView);
        refreshOpenMaps();
        indoorDetect();
    }

    @Override
    public void zoomStart(MapView mapView) {
        fitHeatmapToView(true);
        ZoomEvent ze = new ZoomEvent(Zoom.ZoomStart, mapView.getZoomLevel());
        for (ZoomListener listener : mZoomListener) {
            listener.zoomEvent(ze);
        }
    }

    @Override
    public void zoomEnd(MapView mapView) {
//        System.out.print(String.format("---> cur:%d\n", mapView.getZoomLevel()));
        refreshOpenMaps();
        fitHeatmapToView(true);
        switchMarkerHide();
        ZoomEvent ze = new ZoomEvent(Zoom.ZoomEnd, mapView.getZoomLevel());
        for (ZoomListener listener : mZoomListener) {
            listener.zoomEvent(ze);
        }
        fitMapWithNoBlank(mapView);
        indoorDetect();
    }

    private void indoorDetect() {
        if (indoorZoomLevel > 0) {
            int curZoomLevel = mapView.getZoomLevel();
            if (curZoomLevel >= indoorZoomLevel/* && (lastZoomLevel == -1 || lastZoomLevel < indoorZoomLevel)*/) {
//                System.out.print(String.format("---> cur:%d, last:%d, set:%d\n", curZoomLevel, lastZoomLevel, indoorZoomLevel));
                Point2D point2D = mapView.getCenter();
                String buildingId = "";
                if (buildings != null) {
                    for (QueryUtils.BuildingResult br : buildings) {
                        if (isInPolygon(point2D, br.buildingGeometry.toArray(new Point2D[0]))) {
                            for (int i = 0; i < br.feature.fieldNames.length; i++) {
//                            System.out.print(String.format("---> field:%s, value:%s\n", br.feature.fieldNames[i], br.feature.fieldValues[i]));
                                if (br.feature.fieldNames[i].equalsIgnoreCase("BUILDINGID")) {
                                    buildingId = br.feature.fieldValues[i];
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
                if (!TextUtils.isEmpty(buildingId)) {
//                    System.out.print(String.format("---> field:%s\n", buildingId));
                    if (mZoomToIndoorListener != null) {
                        ZoomToIndoorEvent ztie = new ZoomToIndoorEvent();
                        ztie.zoomLevel = curZoomLevel;
                        ztie.center = new double[2];
                        ztie.center[0] = point2D.y;
                        ztie.center[1] = point2D.x;
                        ztie.buildingId = buildingId;
                        mZoomToIndoorListener.zoomEvent(ztie);
                    } else {
                        showIndoorMap(buildingId, defaultZoomIndoor);
                    }
                }
            }
            if (curZoomLevel < indoorZoomLevel/* && (lastZoomLevel == -1 || lastZoomLevel>= indoorZoomLevel)*/) {
//                System.out.print(String.format("---> cancel cur:%d, last:%d, set:%d\n", curZoomLevel, lastZoomLevel, indoorZoomLevel));
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(500);
                        } catch (Exception e) {}
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                switchOutdoor();
                            }
                        });
                    }
                }).start();
            }
        }
        lastZoomLevel = mapView.getZoomLevel();
    }

    private void refreshOpenMaps() {
        Iterator iter = openIndoors.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, IndoorMapData> entry = (Map.Entry<String, IndoorMapData>) iter.next();
            IndoorMapData indoorMapData = entry.getValue();
            Message msg = new Message();
            msg.obj = indoorMapData;
            msg.what = Common.QUERY_INDOOR_MAP;
            handler.sendMessage(msg);
        }
        iter = openBasements.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, QueryUtils.BasementMapResult> entry = (Map.Entry<String, QueryUtils.BasementMapResult>) iter.next();
            QueryUtils.BasementMapResult basementData = entry.getValue();
            Message msg = new Message();
            msg.obj = basementData;
            msg.what = Common.QUERY_BASEMENT_MAP;
            handler.sendMessage(msg);
        }
    }

    private void fitMapWithNoBlank(MapView mapView) {
        BoundingBox viewBounds = mapView.getViewBounds();
        BoundingBox mapBounds = mapView.getBounds();
        if (viewBounds == null || mapBounds == null)
            return;

        boolean needReset = false;
        if (viewBounds.getWidth() > mapBounds.getWidth()) {
            double ratio = viewBounds.getHeight() / viewBounds.getWidth();
            viewBounds.leftTop.x = mapBounds.leftTop.x;
            viewBounds.rightBottom.x = mapBounds.rightBottom.x;
            double height = viewBounds.getWidth() * ratio;
            double c = viewBounds.getCenter().y;
            viewBounds.leftTop.y = c + height / 2;
            viewBounds.rightBottom.y = c - height / 2;
            needReset = true;
        } else if (viewBounds.getWidth() < mapBounds.getWidth()) {
            if (viewBounds.getLeft() < mapBounds.getLeft()) {
                double diff = mapBounds.getLeft() - viewBounds.getLeft();
                viewBounds.leftTop.x += diff;
                viewBounds.rightBottom.x += diff;
                needReset = true;
            }
            if (viewBounds.getRight() > mapBounds.getRight()) {
                double diff = viewBounds.getRight() - mapBounds.getRight();
                viewBounds.rightBottom.x -= diff;
                viewBounds.leftTop.x -= diff;
                needReset = true;
            }
        }
        if (viewBounds.getHeight() > mapBounds.getHeight()) {
            double ratio = viewBounds.getHeight() / viewBounds.getWidth();
            viewBounds.leftTop.y = mapBounds.leftTop.y;
            viewBounds.rightBottom.y = mapBounds.rightBottom.y;
            double width = viewBounds.getHeight() / ratio;
            double c = viewBounds.getCenter().x;
            viewBounds.leftTop.x = c - width / 2;
            viewBounds.rightBottom.x = c + width / 2;
            needReset = true;
        } else if (viewBounds.getHeight() < mapBounds.getHeight()) {
            if (viewBounds.getTop() > mapBounds.getTop()) {
                Log.d("----", "21");
                double diff = viewBounds.getTop() - mapBounds.getTop();
                viewBounds.leftTop.y -= diff;
                viewBounds.rightBottom.y -= diff;
                needReset = true;
            }
            if (viewBounds.getBottom() < mapBounds.getBottom()) {
                Log.d("----", "22");
                double diff = mapBounds.getBottom() - viewBounds.getBottom();
                viewBounds.rightBottom.y += diff;
                viewBounds.leftTop.y += diff;
                needReset = true;
            }
        }
        if (needReset)
            mapView.setViewBounds(viewBounds);
    }

    private void fitHeatmapToView(boolean recalc) {
        if (heatmapList.size() > 0) {
            for (HeatmapDrawable hp : heatmapList) {
                Point leftTop = new Point();
                Point rightBottom = new Point();
                mapView.getProjection().toPixels(new Point2D(hp.geoLeft, hp.geoTop), leftTop);
                mapView.getProjection().toPixels(new Point2D(hp.geoRight, hp.geoBottom), rightBottom);
                hp.fitToMapView(leftTop.x, leftTop.y, rightBottom.x, rightBottom.y, recalc);
            }
        }
    }

    class TouchOverlay extends Overlay {
        private long touchTime = -1;
        @Override
        public boolean onTouchEvent(MotionEvent event, final MapView mapView) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchTime = (new Date()).getTime();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                final int touchX = Math.round(event.getX());
                final int touchY = Math.round(event.getY());
                // 记录点击位置
                final Point2D touchPoint = mapView.getProjection().fromPixels(touchX, touchY);
                long time = (new Date()).getTime();
                if (!(touchTime > -1 && time - touchTime > 2000)) {
                    MapEvent me = new MapEvent(TargetEvent.Press,
                            new int[]{touchX, touchY},
                            new double[]{touchPoint.y, touchPoint.x},
                            new String[0]);
                    Message msg = new Message();
                    msg.obj = me;
                    msg.what = Common.EVENT_MAP_TAP;
                    handler.sendMessage(msg);
                    return false;
                }
                if (mMapListener.size() > 0) {
                    // 获取地理信息
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String ret = "[]";
                            try {
                                String url = Common.getHost() + Common.GEO_DECODE_URL() + "?x=" + touchPoint.x + "&y=" + touchPoint.y + "&geoDecodingRadius=" + 0.0001 + "&fromIndex=0&toIndex=10&maxReturn=" + 5;
                                ret = GisView.getStringFromURL(url);
                            } catch (Exception ex) {
                                Log.d(TAG, "getLocationOfAddress: " + ex.getMessage());
                            }

                            JSONArray arr = new JSONArray();
                            try {
                                arr = new JSONArray(ret);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            String[] addrs = new String[arr.length()];

                            for (int i = 0; i < arr.length(); i++) {
                                try {
                                    JSONObject obj = arr.getJSONObject(i);
                                    addrs[i] = obj.getString("address");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            // 引发事件
                            MapEvent me = new MapEvent(TargetEvent.LongPress,
                                    new int[]{touchX, touchY},
                                    new double[]{touchPoint.y, touchPoint.x},
                                    addrs);
                            Message msg = new Message();
                            msg.obj = me;
                            msg.what = Common.EVENT_MAP_TAP;
                            handler.sendMessage(msg);
                        }
                    }).start();
                }
            }

            return false;
        }
    }

    private void loadOffLineMaps(String name, int zoom, double[] center) {
        mapLayer = new LayerView(getContext());
        mapLayer.setURL(Common.getHost() + Common.MAP_URL());
//        mapLayer.setExtParams(Common.extParam());
//        darkLayer = new MBTilesLayerView(getContext(), name);
//        backMapLayer = new LayerView(getContext());
//        backMapLayer.setURL(Common.getHost() + Common.BACKMAP_URL());

        double scales[] = { 1.0 / 295829350.48418, 1.0 / 149514675.2421, 1.0 / 9000000, 1.0 / 4000000, 1.0 / 2000000};

//        mapView.addLayer(backMapLayer);
        mapView.addLayer(mapLayer);

        MapController controller = mapView.getController();
        controller.setCenter(new Point2D(center[1], center[0]));
        controller.setZoom(zoom);

        QueryUtils.queryAllBuildings("buildings@" + Common.parkId(), handler);
        GisDataCache.getInstance(this.getContext()).initIndoorMaps();
        handler.sendEmptyMessage(Common.START_TIMER);
    }

    class GVLayerView extends LayerView {
        public GVLayerView(Context context) {
            super(context);
        }
    }

    public void loadMap(int zoom, double[] center) {
        loadMap(zoom, center,"VankeNew", "DS");
    }

    public void loadMap(int zoom, double[] center, String workspace, String parkId) {
        loadMap(zoom, center, workspace, parkId, new ArrayList<String>());
    }

    public void loadMap(int zoom, double[] center, String workspace, String parkId, List<String> extParam) {
        String tilefile = "yuanqu_black_-82352802_256X256_PNG.mbtiles";
        String tilepath = "";
        Common.setCurrentZone(workspace, parkId);
        Common.setExtParam(extParam);

//        if (!TextUtils.isEmpty(tilepath = Common.tileFileExists(tilefile)))
            loadOffLineMaps(tilepath, zoom, center);
//        else
//            Common.downloadMBTiles(tilefile);

        //
    }

    public void setCenter(double lat, double lng) {
        MapController controller = mapView.getController();
        controller.setCenter(new Point2D(lng, lat));
        fitHeatmapToView(false);
    }

    public double[] getCenter() {
        Point2D point2D = mapView.getCenter();
        double[] result = new double[2];
        result[0] = point2D.y;
        result[1] = point2D.x;

        return result;
    }

    public void setZoom(double[] center, int zoom) {
        MapController controller = mapView.getController();
        controller.setCenter(new Point2D(center[1], center[0]));
        controller.setZoom(zoom);
    }

    public void setMaxZoomLevel(int level) {
        maxZoomLevel = level;
    }

    public void zoomInMap() {
        mapView.zoomIn();
    }

    public void zoomOutMap() {
        mapView.zoomOut();
    }

    public int getZoom() {
        return mapView.getZoomLevel();
    }

    public void setSwitchIndoor(int zoomLevel, ZoomToIndoorListener listener, String defaultZoomIndoor) {
        this.indoorZoomLevel = zoomLevel;
        this.mZoomToIndoorListener = listener;
        if (!TextUtils.isEmpty(defaultZoomIndoor))
            this.defaultZoomIndoor = defaultZoomIndoor;
    }

    public void destroyMap() {
        handler.sendEmptyMessage(Common.STOP_TIMER);
        mapView.removeAllLayers();
    }

    public void changeMarkerPosition(String markerId, double lat, double lng) {
        changeMarkerPosition(markerId, lat, lng, null);
    }

    public void changeMarkerPosition(String markerId, double lat, double lng, Drawable marker) {
        List<Overlay> ovls = mapView.getOverlays();
        for (Overlay ov : ovls) {
            if (ov instanceof DefaultItemizedOverlay) {
                DefaultItemizedOverlay overlay = (DefaultItemizedOverlay) ov;
                for (int i = 0; i < overlay.size(); i++) {
                    OverlayItemEx item = (OverlayItemEx) overlay.getItem(i);
                    if (item.getTitle().equalsIgnoreCase(markerId)) {
                        item.setPoint(new Point2D(lng, lat));
                        if (marker != null)
                            item.setMarker(marker);
                        mapView.invalidate();
                        return;
                    }
                }
            }
        }
    }

    public void addMarker(String layerId, int layerIndex, GeneralMarker[] markers) {
        List<GeneralMarker> drawableMarkers = new ArrayList<>();
        List<GeneralMarker> urlMarkers = new ArrayList<>();
        for (GeneralMarker marker : markers) {
            if (marker.image == null) {
                if (!TextUtils.isEmpty(marker.imagePath))
                    urlMarkers.add(marker);
            } else
                drawableMarkers.add(marker);
        }

        if (drawableMarkers.size() > 0) {
            DefaultItemizedOverlay overlay = new DefaultItemizedOverlay(null);
            for (GeneralMarker marker : drawableMarkers) {
                OverlayItemEx item = new OverlayItemEx(new Point2D(marker.position[1], marker.position[0]), marker.markerId, marker.markerId, marker);
                MarkerDrawable drawable = new MarkerDrawable(marker.image, marker.width, marker.height);
                item.setMarker(drawable);
                overlay.addItem(item);
            }
            overlay.setTapListener(this);
            overlay.setZIndex(9000 + mapView.getOverlays().size());
            mapView.getOverlays().add(overlay);
            if (namedOverlays.containsKey(layerId))
                namedOverlays.get(layerId).add(overlay);
            else {
                List<Overlay> ovlys = new ArrayList<>();
                ovlys.add(overlay);
                namedOverlays.put(layerId, ovlys);
            }
            mapView.invalidate();
        }
        if (urlMarkers.size() > 0) {
            UrlMarkerMaker.makeUrlMarker(
                    layerId,
                    layerIndex,
                    urlMarkers.toArray(new GeneralMarker[urlMarkers.size()]),
                    handler);
        }
    }

    public void addMarker(String layerId, int layerIndex, FlashMarker[] markers) {
        List<FlashMarker> drawableMarkers = new ArrayList<>();
        List<FlashMarker> urlMarkers = new ArrayList<>();
        for (FlashMarker marker : markers) {
            if (marker.images == null) {
                if (marker.imagesPath != null)
                    urlMarkers.add(marker);
            } else
                drawableMarkers.add(marker);
        }

        if (drawableMarkers.size() > 0) {
            DefaultItemizedOverlay overlay = new DefaultItemizedOverlay(null);
            final List<MarkerAnimation> anilst = new ArrayList<>();
            for (FlashMarker marker : markers) {
                MarkerAnimation animation = new MarkerAnimation();
                for (Drawable d : marker.images) {
                    animation.addFrame(d);
                }
                animation.setNewSize(marker.width, marker.height);
                animation.setInterval(marker.interval);
                animation.setDuration(marker.duration);
                animation.setCallback(new Drawable.Callback() {
                    @Override
                    public void invalidateDrawable(@NonNull Drawable who) {
                        mapView.post(new Runnable() {
                            @Override
                            public void run() {
                                mapView.invalidate();
                            }
                        });
                    }

                    @Override
                    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                        mapView.post(new Runnable() {
                            @Override
                            public void run() {
                                mapView.invalidate();
                            }
                        });
                    }

                    @Override
                    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                        mapView.post(new Runnable() {
                            @Override
                            public void run() {
                                mapView.invalidate();
                            }
                        });
                    }
                });
                anilst.add(animation);
                OverlayItemEx item = new OverlayItemEx(new Point2D(marker.position[1], marker.position[0]), marker.markerId, marker.markerId, marker);
                item.setMarker(animation);
                overlay.addItem(item);
            }
            overlay.setTapListener(this);
            overlay.setZIndex(9000 + mapView.getOverlays().size());
            mapView.getOverlays().add(overlay);
            if (namedOverlays.containsKey(layerId))
                namedOverlays.get(layerId).add(overlay);
            else {
                List<Overlay> ovlys = new ArrayList<>();
                ovlys.add(overlay);
                namedOverlays.put(layerId, ovlys);
            }
            mapView.invalidate();
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    for (MarkerAnimation ani : anilst) {
                        ani.start();
                    }
                }
            });
        }
        if (urlMarkers.size() > 0) {
            UrlMarkerMaker.makeUrlMarker(
                    layerId,
                    layerIndex,
                    urlMarkers.toArray(new FlashMarker[urlMarkers.size()]),
                    handler);
        }
    }

    public void addFlashMarker(String layerId, int layerIndex, FlashMarker[] markers) {
        addMarker(layerId, layerIndex, markers);
    }

    public void deleteMarker(String markerId) {
        List<Overlay> ovls = mapView.getOverlays();
        for (Overlay ov : ovls) {
            if (ov instanceof DefaultItemizedOverlay) {
                DefaultItemizedOverlay overlay = (DefaultItemizedOverlay) ov;
                for (int i = 0; i < overlay.size(); i++) {
                    OverlayItemEx item = (OverlayItemEx) overlay.getItem(i);
                    if (item.getTitle().equalsIgnoreCase(markerId)) {
                        overlay.removeItem(item);
                        mapView.invalidate();
                        return;
                    }
                }
            }
        }
    }

    public void deleteLayer(String layerId) {
        List<Overlay> ovlys = namedOverlays.get(layerId);
        if (ovlys != null && ovlys.size() > 0) {
            for (Overlay ov : ovlys)
                mapView.getOverlays().remove(ov);
            mapView.invalidate();
            namedOverlays.remove(layerId);
        }
    }

    public void addMarkerListener(MarkerListener listener) {
        if (!mMarkerListener.contains(listener))
            mMarkerListener.add(listener);
    }

    public void removeMarkerListener(MarkerListener listener) {
        mMarkerListener.remove(listener);
    }

    public Object addPopup(double[] position, String content, double[] offset, int width, int height, Object tag) {
        SimplePopupOverlay overlay = new SimplePopupOverlay();
        overlay.setCoord(position);
        overlay.setContent(content);
        overlay.setOffset(offset);
        overlay.setSize(width, height);
        overlay.setTag(tag);
        overlay.setZIndex(9000 + mapView.getOverlays().size());
        mapView.getOverlays().add(overlay);
        mapView.invalidate();
        return overlay;
    }

    public Object addPopup(double[] position, View popupView, double[] offset, int width, int height, Object tag) {
        PopupOverlay overlay = new PopupOverlay();
        overlay.setCoord(position);
        overlay.setView(popupView);
        overlay.setOffset(offset);
        overlay.setTag(tag);
        overlay.setZIndex(9000 + mapView.getOverlays().size());
        mapView.addView(overlay.popView);
        mapView.getOverlays().add(overlay);
        mapView.invalidate();
        return overlay;
    }

    public void closePopup() {
        List<Overlay> ovlys = mapView.getOverlays();
        List<Overlay> ovdels = new ArrayList<>();
        if (ovlys != null && ovlys.size() > 0) {
            for (Overlay ov : ovlys) {
                if (ov instanceof SimplePopupOverlay || ov instanceof PopupOverlay){
                    Log.d(TAG, "closePopup: " + ov.getKey());
                    ovdels.add(ov);
                }
            }
            for (Overlay ov : ovdels)
                mapView.getOverlays().remove(ov);
            ovdels.clear();
            mapView.invalidate();
        }
    }
    public void closePopup(Object obj) {
        List<Overlay> ovlys = mapView.getOverlays();
        Overlay olay = (Overlay)obj;
        if (ovlys != null && ovlys.size() > 0) {
            for (Overlay ov : ovlys) {
                if (ov.getKey() == olay.getKey()){
                    Log.d(TAG, "closePopup: " + ov.getKey());
                    mapView.getOverlays().remove(ov);
                    mapView.invalidate();
                    break;
                }
            }
        }
    }

    public void showIndoorMap(String buildingId, String floorid, IndoorCallback callback) {
        indoorCallback = callback;
        showIndoorMap(buildingId, floorid);
    }


    public void showIndoorMap(String buildingId, String floorid) {
        Common.getLogger(null).log(Level.INFO, String.format("showIndoorMap: buildingId=%s; floorid=%s", buildingId, floorid));
        if (TextUtils.isEmpty(floorid)) {
            clearOpensMap();
            mapView.invalidate();
        } else {
            String newIndoorMap;
            if (TextUtils.isEmpty(buildingId))
                newIndoorMap = "Basement:" + floorid;
            else
                newIndoorMap = buildingId + ":" + floorid;
            Iterator<String> iterator = openedMaps.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().equalsIgnoreCase(newIndoorMap))
                    return;
            }
            clearOpensMap();
            currIndoorMap = newIndoorMap;

            if (TextUtils.isEmpty(buildingId)) {
                QueryUtils.queryBasementMap(Common.parkId(), floorid, handler);
            } else {
//                Feature buildingFeature = GisDataCache.getInstance(this.getContext()).getBuilding(buildingId);
//                Feature[] floorFeatures = GisDataCache.getInstance(this.getContext()).getFloor(buildingId, floorid);
//                if (buildingFeature != null && floorFeatures != null && floorFeatures.length > 0 ) {
//                    Log.e("queryIndoorMap1", buildingFeature.toString()+","+floorFeatures.toString());
//                    List<List<Point2D>> buildingGeometry = new ArrayList<>();
//                    if (buildingFeature.geometry != null) {
//                        int index = 0;
//                        for (int i=0; i<buildingFeature.geometry.parts.length; i++) {
//                            List<Point2D> point2DS = new ArrayList<>();
//                            for (int j=0; j<buildingFeature.geometry.parts[i]; j++) {
//                                Point2D point2D = new Point2D();
//                                point2D.x = buildingFeature.geometry.points[index].x;
//                                point2D.y = buildingFeature.geometry.points[index].y;
//                                point2DS.add(point2D);
//                                index++;
//                            }
//                            buildingGeometry.add(point2DS);
//                        }
//                    }
//                    List<ModelData> rooms = new ArrayList<>();
//                    for (Feature feature : floorFeatures) {
//                        if (feature == null)
//                            continue;
//                        if (feature.geometry == null)
//                            continue;
//
//                        HashMap<String, String> info = new HashMap<>();
//                        for (int i=0; i<feature.fieldNames.length; i++)
//                            info.put(feature.fieldNames[i], feature.fieldValues[i]);
//                        info.put("FLOORID", floorid);
//                        String key = String.format("%s.%s.%s", buildingId, floorid, info.get("SMID"));
//                        List<List<Point2D>> geometry = new ArrayList<>();
//                        int index = 0;
//                        for (int i=0; i<feature.geometry.parts.length; i++) {
//                            List<Point2D> point2DS = new ArrayList<>();
//                            for (int j=0; j<feature.geometry.parts[i]; j++) {
//                                Point2D point2D = new Point2D();
//                                point2D.x = feature.geometry.points[index].x;
//                                point2D.y = feature.geometry.points[index].y;
//                                point2DS.add(point2D);
//                                index++;
//                            }
//                            geometry.add(point2DS);
//                        }
//                        if (feature.geometry.type == GeometryType.REGION) {
//                            ModelData room = new ModelData(key, null, geometry, info);
//                            rooms.add(room);
//                        } else {
//                            ModelData room = new ModelData(key, geometry, null, info);
//                            rooms.add(room);
//                        }
//                    }
//                    IndoorMapData indoorMapData = new IndoorMapData(
//                            buildingId,
//                            floorid,
//                            buildingGeometry,
//                            buildingFeature.geometry.getBounds(),
//                            rooms);
//                    Message msg = new Message();
//                    msg.obj = indoorMapData;
//                    msg.what = Common.QUERY_INDOOR_MAP;
//                    handler.sendMessage(msg);
//                } else {
                    QueryUtils.queryIndoorMap(Common.parkId() + ":Buildings", buildingId, floorid, handler);
//                }
            }
        }
    }

    private void clearOpensMap() {
        currIndoorMap = "";
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, List<Overlay>> entry : namedOverlays.entrySet()) {
            if (entry.getKey().startsWith("indoor[building:")) {
                keys.add(entry.getKey());
                List<Overlay> ovls = entry.getValue();
                for (Overlay ov : ovls)
                    mapView.getOverlays().remove(ov);
            }
        }
        for (String key : keys)
            namedOverlays.remove(key);

        keys.clear();
        for (Map.Entry<String, List<Overlay>> entry : namedOverlays.entrySet()) {
            if (entry.getKey().startsWith("indoor[basement:")) {
                keys.add(entry.getKey());
                List<Overlay> ovls = entry.getValue();
                for (Overlay ov : ovls)
                    mapView.getOverlays().remove(ov);
            }
        }
        for (String key : keys)
            namedOverlays.remove(key);

        keys.clear();
        for (Map.Entry<String, List<Overlay>> entry : namedOverlays.entrySet()) {
            if (entry.getKey().startsWith("indoorStyle[")) {
                keys.add(entry.getKey());
                List<Overlay> ovls = entry.getValue();
                for (Overlay ov : ovls)
                    mapView.getOverlays().remove(ov);
            }
        }
        for (String key : keys)
            namedOverlays.remove(key);

        openIndoors.clear();
        openBasements.clear();
        openedMaps.clear();
    }

    public void setRoomStyle(String buildingId, String floorId, String key, String type, RoomStyle roomStyle) {
        boolean isUpdated = false;

        String indoorKey = String.format(indoorKeyTemplate, buildingId);
        String styleKey = String.format(indoorStyleKeyTemplate, buildingId, floorId, type);
        List<Overlay> ovls = null;
        if (namedOverlays.containsKey(styleKey)) {
            ovls = namedOverlays.get(styleKey);
            List<Overlay> old = new ArrayList<>();
            for (Overlay ov : ovls) {
                if (ov.getKey().startsWith(key)) {
                    mapView.getOverlays().remove(ov);
                    old.add(ov);
                    isUpdated = true;
                }
            }
            for (Overlay ov : old)
                ovls.remove(ov);
        }

        if (roomStyle != null && openIndoors.containsKey(indoorKey)) {
            IndoorMapData indoorMapData = openIndoors.get(indoorKey);
            for (ModelData modelData : indoorMapData.rooms) {
                String smId = null;
                String keyvalue = null;
                if (modelData.features.containsKey("SMID"))
                    smId = modelData.features.get("SMID");
                if (modelData.features.containsKey(type))
                    keyvalue = modelData.features.get(type);
                if (keyvalue != null && keyvalue.equalsIgnoreCase(key)) {
                    if (ovls == null) {
                        ovls = new ArrayList<>();
                        namedOverlays.put(styleKey, ovls);
                    }
                    if (modelData.geometry != null) {
                        Paint paint = new Paint();
                        paint.setAntiAlias(true);
                        paint.setStyle(Paint.Style.FILL_AND_STROKE);
                        paint.setColor(roomStyle.fillColor);
                        paint.setAlpha(roomStyle.fillOpacity);

                        for (List<Point2D> points : modelData.geometry) {
                            PolygonOverlay ov = new PolygonOverlay(paint);
                            ov.setShowPoints(false);
                            ov.setData(points);
                            ov.setKey(keyvalue + smId);
                            ovls.add(ov);
                            mapView.getOverlays().add(ov);
                        }
                    } else {
                        Paint paint = new Paint();
                        paint.setAntiAlias(true);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(roomStyle.lineColor);
                        paint.setAlpha(roomStyle.lineOpacity);
                        paint.setStrokeWidth(roomStyle.lineWidth);

                        for (List<Point2D> points : modelData.outline) {
                            LineOverlay ov = new LineOverlay(paint);
                            ov.setShowPoints(false);
                            ov.setData(points);
                            ov.setKey(keyvalue + smId);
                            ovls.add(ov);
                            mapView.getOverlays().add(ov);
                        }
                    }
                    isUpdated = true;
                }
            }
        }

        if (isUpdated)
            mapView.invalidate();
    }

    public void setRoomStyle(String buildingId, String floorId, String roomId, RoomStyle roomStyle) {
        setRoomStyle(buildingId, floorId, roomId, "ROOMID", roomStyle);
    }

    public void switchOutdoor() {
        showIndoorMap("", null);
    }

    public void drawCustomPath(RoutePoint[] points) {
        List<Point2D> point2DS = new ArrayList<>();
        for (RoutePoint p : points) {
            Point2D point2D = new Point2D(p.coords[1], p.coords[0]);
            point2DS.add(point2D);
        }
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(points[0].color);
        paint.setAlpha(points[0].opacity);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(points[0].width);
        routeOverlay = new LineOverlay(paint);
        routeOverlay.setLinePaint(paint);
        routeOverlay.setPointPaint(paint);
        routeOverlay.setShowPoints(false);
        routeOverlay.setData(point2DS);
        mapView.getOverlays().add(routeOverlay);
        mapView.invalidate();
    }

    public void calcRoutePath(RoutePoint start, RoutePoint end, RoutePoint[] way) {
        PresentationStyle ps = new PresentationStyle();
        ps.lineWidth = 20;
        ps.fillColor = Color.parseColor("#0216F2");
        ps.opacity = 120;
        NetWorkAnalystUtil.excutePathService(mapView, start, end, way, ps, handler);
    }

    public void calcRoutePath(RoutePoint start, RoutePoint end, RoutePoint[] way, PresentationStyle ps) {
        NetWorkAnalystUtil.excutePathService(mapView, start, end, way, ps, handler);
    }

    public void setRouteFacility(String[] keys, GeneralMarker[] markers) {
        if (keys == null || markers == null)
            return;

        int len = Math.min(keys.length, markers.length);
        for (int i=0; i<len; i++)
            defaultFacilities.put(keys[i], markers[i]);
    }

    public void clearPath() {
        calculatedRoute = null;
        if (routeOverlay != null) {
            mapView.getOverlays().remove(routeOverlay);
            routeOverlay.setData(new ArrayList<Point2D>());
            routeOverlay = null;
        }

        if (namedOverlays.containsKey(calculatdRouteKey)) {
            List<Overlay> ovls = namedOverlays.get(calculatdRouteKey);
            for (Overlay ov: ovls)
                mapView.getOverlays().remove(ov);
            namedOverlays.remove(calculatdRouteKey);
        }
        mapView.invalidate();
    }

    public boolean isInRoute(double lat, double lng, double delta) {
        if (calculatedRoute != null) {
            double deltaLng = (delta / 1000) * 20 / 104;
            if (lat >= 20 && lat < 26)
                deltaLng = (delta / 1000) * 26 / 100;
            else if (lat >= 26 && lat < 30)
                deltaLng = (delta / 1000) * 30 / 100;
            else if (lat >= 30 && lat < 36)
                deltaLng = (delta / 1000) * 36 / 100;
            else if (lat >= 36 && lat < 40)
                deltaLng = (delta / 1000) * 40 / 100;
            else if (lat >= 40 && lat < 44)
                deltaLng = (delta / 1000) * 44 / 100;
            else if (lat >= 44)
                deltaLng = (delta / 1000) * 51 / 100;
            double deltaLat = (delta / 1000) * 1 / 111;
            for (com.supermap.services.components.commontypes.Path[] paths : calculatedRoute.route) {
                if (paths == null)
                    continue;
                for (com.supermap.services.components.commontypes.Path path : paths) {
                    com.supermap.services.components.commontypes.Point2D[] route = path.route.points;
                    if (route.length == 1) {
                        if (Math.abs(route[0].x - lng) <= deltaLng && Math.abs(route[0].y - lat) <= deltaLat)
                            return true;
                        else
                            continue;
                    }
                    for (int i = 0; i < route.length - 1; i++) {
                        boolean isInSection = false;
                        if (route[i].y + deltaLat > lat && route[i + 1].y - deltaLat < lat && route[i].x + deltaLng > lng && route[i + 1].x - deltaLng < lng)
                            isInSection = true;
                        else if (route[i].y + deltaLat > lat && route[i + 1].y - deltaLat < lat && route[i + 1].x + deltaLng > lng && route[i].x - deltaLng < lng)
                            isInSection = true;
                        else if (route[i + 1].y + deltaLat > lat && route[i].y - deltaLat < lat && route[i].x + deltaLng > lng && route[i + 1].x - deltaLng < lng)
                            isInSection = true;
                        else if (route[i + 1].y + deltaLat > lat && route[i].y - deltaLat < lat && route[i + 1].x + deltaLng > lng && route[i].x - deltaLng < lng)
                            isInSection = true;
                        if (isInSection) {
                            double x1 = route[i].x;
                            double y1 = route[i].y;
                            double x2 = route[i + 1].x;
                            double y2 = route[i + 1].y;
                            double A = (y1 - y2) / (x1 - x2);
                            double B = -1;
                            double C = y1 - A * x1;
                            double d = Math.abs((A * lng + B * lat + C) / (Math.sqrt(A * A + B * B)));
                            return (d <= deltaLat) && (d <= deltaLng);
                        }
                    }
                }
            }
        }
        return false;
    }

    public void showHeatMap(HeatPoint[] points, int radius, double opacity) {
        HeatmapDrawable heatmapDrawable = new HeatmapDrawable(points, radius, handler);
        heatmapList.add(heatmapDrawable);
        Point leftTop = new Point();
        Point rightBottom = new Point();
        mapView.getProjection().toPixels(new Point2D(heatmapDrawable.geoLeft, heatmapDrawable.geoTop), leftTop);
        mapView.getProjection().toPixels(new Point2D(heatmapDrawable.geoRight, heatmapDrawable.geoBottom), rightBottom);

        DrawableOverlay overlay = new DrawableOverlay(heatmapDrawable,
                new BoundingBox(new Point2D(heatmapDrawable.geoLeft, heatmapDrawable.geoTop),
                        new Point2D(heatmapDrawable.geoRight, heatmapDrawable.geoBottom)));

        mapView.getOverlays().add(overlay);

        heatmapDrawable.fitToMapView(leftTop.x, leftTop.y, rightBottom.x, rightBottom.y, true);
    }

    public void clearHeatMap() {
        List<Overlay> ovlys = mapView.getOverlays();
        List<Overlay> ovdels = new ArrayList<>();
        if (ovlys != null && ovlys.size() > 0) {
            for (Overlay ov : ovlys) {
                if (ov instanceof DrawableOverlay)
                    ovdels.add(ov);
            }
            for (Overlay ov : ovdels)
                mapView.getOverlays().remove(ov);
            mapView.invalidate();
        }
        heatmapList.clear();
    }

    public void displayPerimeter(String parkId,
                                 String normalColor, int normalWidth, int normalOpacity,
                                 String alarmColor, int alarmWidth, int alarmOpacity,
                                 int[] alarmList) {
        PerimeterStyle alarm = new PerimeterStyle(Color.parseColor(alarmColor), alarmWidth, alarmOpacity);
        PerimeterStyle normal = new PerimeterStyle(Color.parseColor(normalColor), normalWidth, normalOpacity);
        displayPerimeter(parkId, alarm, normal, alarmList);
    }

    public void displayPerimeter(String parkId, PerimeterStyle alarm, PerimeterStyle normal, int[] alarmList) {

        QueryUtils.queryPerimeter(parkId, alarm, normal, alarmList, handler);
    }

    public void removePerimeter() {
        if (namedOverlays.containsKey(perimeterKey)) {
            List<Overlay> ovls = namedOverlays.get(perimeterKey);
            for (Overlay ov: ovls)
                mapView.getOverlays().remove(ov);
            namedOverlays.remove(perimeterKey);
            mapView.invalidate();
        }
    }

    public void showModelHighlight(String parkId, int[] modId) {
        List<int[]> ids = new ArrayList<>();
        ids.add(modId);
        List<PresentationStyle> pss = new ArrayList<>();
        PresentationStyle hps = new PresentationStyle();
        hps.lineWidth = 1;
        hps.fillColor = Color.parseColor("#5CE7FF");
        hps.opacity = 150;
        pss.add(hps);
        PresentationStyle ps = new PresentationStyle();
        ps.lineWidth = 1;
        ps.fillColor = Color.parseColor("#2B94BF");
        ps.opacity = 150;
        showModelHighlight(ids, pss, ps);
    }

    public void showModelHighlight(List<int[]> modIds, List<PresentationStyle> pss, PresentationStyle other) {
        isShowHighLight = true;
        QueryUtils.queryModel(modIds, pss, other, handler);
    }

    public void removeModelhighlighting() {
        isShowHighLight = false;
        if (namedOverlays.containsKey(modelsKey)) {
            List<Overlay> ovls = namedOverlays.get(modelsKey);
            for (Overlay ov: ovls)
                mapView.getOverlays().remove(ov);
            ovls.clear();
            namedOverlays.remove(modelsKey);
            mapView.invalidate();
        }
    }

    public void queryObject(final String parkId, final String address, final QueryCallback callback) {
        Common.fixedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                String addr = null;
                double lng = 0, lat = 0;
                try {
                    String ret = "[]";
                    try {
                        String enc_addr = URLEncoder.encode(address, "UTF-8");
                        String url = Common.getHost() + Common.GEO_CODE_URL() + "?address="+enc_addr+"&fromIndex=0&toIndex=10&maxReturn=1";
                        if (!TextUtils.isEmpty(Common.ROMA_KEY))
                            url += "&" + Common.ROMA_KEY;
                        ret =  GisView.getStringFromURL(url);
                    }
                    catch (Exception ex){
                        Log.d(TAG, "getLocationOfAddress: "+ex.getMessage());
                    }

                    JSONArray arr = new JSONArray();
                    try {
                        arr = new JSONArray(ret);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (arr.length() > 0){
                        try {
                            JSONObject obj = arr.getJSONObject(0);
                            addr = obj.getString("address");
                            lng = obj.getJSONObject("location").getDouble("x");
                            lat = obj.getJSONObject("location").getDouble("y") ;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!TextUtils.isEmpty(addr)) {
                    ObjectInfo info = QueryUtils.getObjectInfo(parkId, lat, lng);
                    if (info != null) {
                        info.address = addr;
                        info.lng = lng;
                        info.lat = lat;
                        if (callback != null)
                            callback.onQueryFinished(info);
                    }
                }
            }
        });
    }

    public void getBuldingInfo(final String parkId, final String buildingId, final QueryCallback callback) {
        Common.fixedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
            ObjectInfo info = QueryUtils.getBuildingInfo(parkId, buildingId);

            if (info != null) {
                try {
                    String ret = "[]";
                    try {
                        String enc_addr = URLEncoder.encode(info.getStrParam("NAME"), "UTF-8");
                        String url = Common.getHost() + Common.GEO_CODE_URL() + "?address="+enc_addr+"&fromIndex=0&toIndex=10&maxReturn=1";
                        if (!TextUtils.isEmpty(Common.ROMA_KEY))
                            url += "&" + Common.ROMA_KEY;
                        ret =  GisView.getStringFromURL(url);
                    }
                    catch (Exception ex){
                        Log.d(TAG, "getLocationOfAddress: "+ex.getMessage());
                    }

                    JSONArray arr = new JSONArray();
                    try {
                        arr = new JSONArray(ret);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (arr.length() > 0){
                        try {
                            JSONObject obj = arr.getJSONObject(0);
                            info.address = obj.getString("address");
                            info.lng = obj.getJSONObject("location").getDouble("x");
                            info.lat = obj.getJSONObject("location").getDouble("y") ;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (callback != null)
                    callback.onQueryFinished(info);
            }
            }
        });
    }

    public List<ObjectInfo> query(String dataset, String filter) {
        return QueryUtils.getObjects(dataset, filter);
    }

    public void addBuildingListener(BuildingListener listener) {
        if (!mBuildingListener.contains(listener))
            mBuildingListener.add(listener);
    }

    public void removeBuildingListener(BuildingListener listener) {
        mBuildingListener.remove(listener);
        List<Overlay> overlays = mapView.getOverlays();
        for (Overlay ov : overlays) {
            String key = ov.getKey();
            if (!TextUtils.isEmpty(key) && key.startsWith("building:")) {
                PolygonOverlay unselect = (PolygonOverlay) ov;
                if (unselect != null)
                    unselect.setLinePaint(getBuildingSelectPaint(false));
            }
        }
        mapView.invalidate();
    }

    public void addModelListener(ModelListener listener) {
        if (!mModelListener.contains(listener))
            mModelListener.add(listener);
    }

    public void removeModelListener(ModelListener listener) {
        mModelListener.remove(listener);
    }

    public void addZoomListener(ZoomListener listener) {
        if (!mZoomListener.contains(listener))
            mZoomListener.add(listener);
    }

    public void removeZoomListener(ZoomListener listener) {
        mZoomListener.remove(listener);
    }

    public void addMapListener(MapListener listener) {
        if (!mMapListener.contains(listener))
            mMapListener.add(listener);
    }

    public void removeMapListener(MapListener listener) {
        mMapListener.remove(listener);
    }

    public void setHideLevel(int level) {
        mHideLevel = level;
        switchMarkerHide();
    }

    private void switchMarkerHide() {
        boolean show;
        if (mapView.getZoomLevel() > mHideLevel)
            show = true;
        else
            show = false;
        List<Overlay> overlays = mapView.getOverlays();
        for (Overlay ov : overlays) {
            if (ov instanceof DefaultItemizedOverlay) {
                DefaultItemizedOverlay overlay = (DefaultItemizedOverlay) ov;
                for (int i = 0, cnt = overlay.size(); i < cnt; i++) {
                    Drawable d = overlay.getItem(i).getMarker(0);
                    d.setVisible(show, true);
                    d.invalidateSelf();
                }
            }
        }
        mapView.invalidate();
    }

    public static void addLocateListener(LocationListener listener) {
        if (!mPosListener.contains(listener))
            mPosListener.add(listener);
    }

    public static void removeLocateListener(LocationListener listener) {
        mPosListener.remove(listener);
    }

    public static void startLocate() {
        if (locationServer != null) {
            locationServer.startLoc();
            if (_instance != null) {
                if (_instance.mLocationClient != null) {
                    _instance.mLocationClient.stopLocation();
                    _instance.mLocationClient.startLocation();
                }
            }
            isLocatorRunning = true;
        }
    }

    public static void stopLocate() {
        isLocatorRunning = false;
        if (locationServer != null) {
            locationServer.stopLoc();
        }
        if (_instance != null) {
            if (_instance.mLocationClient != null)
                _instance.mLocationClient.stopLocation();
        }
    }

    static class ExecuteFinished extends Handler {
        private GisView host;
        ExecuteFinished(GisView host) {
            this.host = host;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Common.ADD_GENERAL_MARKER:
                    UrlMarkerMaker.UrlGeneralMarker gmarker = (UrlMarkerMaker.UrlGeneralMarker) msg.obj;
                    host.addMarker(gmarker.layerId, gmarker.layerIndex, gmarker.markers);
                    break;
                case Common.ADD_FLASH_MARKER:
                    UrlMarkerMaker.UrlFlashMarker fmarker = (UrlMarkerMaker.UrlFlashMarker) msg.obj;
                    host.addMarker(fmarker.layerId, fmarker.layerIndex, fmarker.markers);
                    break;
                case Common.QUERY_BUILDINGS:
                    List<QueryUtils.BuildingResult> buildings = (List<QueryUtils.BuildingResult>) msg.obj;
                    host.initBuildingEvent(buildings);
                    break;
                case Common.QUERY_INDOOR_MAP:
                    IndoorMapData indoor = (IndoorMapData) msg.obj;
                    host.renderIndoorMap(indoor);
                    break;
                case Common.QUERY_BASEMENT_MAP:
                    QueryUtils.BasementMapResult basement = (QueryUtils.BasementMapResult) msg.obj;
                    host.renderBasementMap(basement);
                    break;
                case Common.QUERY_PERIMETER:
                    QueryUtils.PerimeterResult perimeter = (QueryUtils.PerimeterResult) msg.obj;
                    host.renderPerimeter(perimeter);
                    break;
                case Common.QUERY_MODEL:
                    QueryUtils.ModelResult model = (QueryUtils.ModelResult) msg.obj;
                    host.renderModel(model);
                    break;
                case Common.ANALYST_ROUTE:
                    NetWorkAnalystUtil.CalculatedRoute route = (NetWorkAnalystUtil.CalculatedRoute) msg.obj;
                    host.renderCalculatdRoute(route);
                    break;
                case Common.HEAT_MAP_CALC_END:
                    host.mapView.invalidate();
                case Common.EVENT_MAP_TAP:
                    if (host.mMapListener.size() > 0) {
                        for (MapListener listener : host.mMapListener)
                            listener.mapEvent((MapEvent) msg.obj);
                    }
                    break;
                case Common.START_TIMER:
                    host.handler.removeMessages(Common.START_TIMER);
                    MapController controller = host.mapView.getController();
                    if (host.mapView.getZoomLevel() > host.maxZoomLevel)
                        controller.setZoom(host.maxZoomLevel);
                    host.fitMapWithNoBlank(host.mapView);
                    host.handler.sendEmptyMessageDelayed(0, 100);
                    break;
                case Common.STOP_TIMER:
                    host.handler.removeMessages(Common.START_TIMER);
                    break;
                default:
                    break;
            }
        }
    }

    private void initBuildingEvent(List<QueryUtils.BuildingResult> buildings) {
        this.buildings = buildings;

        List<Overlay> ovls;
        if (namedOverlays.containsKey(buildingTouchKey)) {
            ovls = namedOverlays.get(buildingTouchKey);
            for (Overlay ov : ovls)
                mapView.getOverlays().remove(ov);
            ovls.clear();
        } else {
            ovls = new ArrayList<>();
        }

        if (buildings != null) {
            for (QueryUtils.BuildingResult br : buildings) {
                PolygonOverlay building = new PolygonOverlay(getBuildingSelectPaint(false));
                building.setData(br.buildingGeometry);
                building.setShowPoints(false);
                building.setTapListener(this);
                building.setKey(String.format("building:[%d]", br.feature.getID()));
                ovls.add(building);
                mapView.getOverlays().add(building);
            }
        }
        mapView.invalidate();
    }

    private void renderIndoorMap(IndoorMapData indoor) {
        String indoorKey = String.format(indoorKeyTemplate, indoor.buildingId);
        List<Overlay> ovls = null;
        if (namedOverlays.containsKey(indoorKey)) {
            ovls = namedOverlays.get(indoorKey);
        }

        Canvas canvas = null;
        Bitmap baseBmp = null;
        Path pathBuild = null;
        Path path = null;
        Path pathLine = null;
        double r = 0;
        Point2D leftTop = new Point2D();
        Point2D rightBottom = new Point2D();
        if ((indoor.buildingGeometry != null && indoor.buildingGeometry.size() > 0)
                ||(indoor.rooms != null && indoor.rooms.size() > 0)) {
            int dw = mapView.getWidth();
            int dh = mapView.getHeight();
            leftTop = mapView.toMapPoint(new Point(0, 0));
            rightBottom = mapView.toMapPoint(new Point(dw, dh));
            r = (double) dw / (rightBottom.x - leftTop.x);
//            Log.i("--indoor", String.format("%d, %d; base: %f, %f", dw, dh, leftTop.x, leftTop.y));
            if (dw > 10 && dh > 10)
                baseBmp = Bitmap.createBitmap((int) dw, (int) dh, Bitmap.Config.ARGB_8888);
            if (baseBmp != null) {
                canvas = new Canvas(baseBmp);
                canvas.drawColor(Color.TRANSPARENT);
                pathBuild = new Path();
                path = new Path();
                pathLine = new Path();
            }
        }

        if (indoor.buildingGeometry != null && indoor.buildingGeometry.size() > 0) {
            for (List<Point2D> obj : indoor.buildingGeometry) {
                double x, y;
                x = (obj.get(0).x - leftTop.x) * r;
                y = (leftTop.y - obj.get(0).y) * r;
                pathBuild.moveTo((float) x, (float) y);
                for (int i=1; i<obj.size(); i++) {
                    x = (obj.get(i).x - leftTop.x) * r;
                    y = (leftTop.y - obj.get(i).y) * r;
                    pathBuild.lineTo((float) x, (float) y);
                }
            }
        }

        if (indoor.rooms != null && indoor.rooms.size() > 0) {
            for (ModelData roomData : indoor.rooms) {
                double x, y;
                if (roomData.geometry != null) {
                    for (List<Point2D> obj : roomData.geometry) {
                        x = (obj.get(0).x - leftTop.x) * r;
                        y = (leftTop.y - obj.get(0).y) * r;
                        path.moveTo((float) x, (float) y);
                        for (int i=1; i<obj.size(); i++) {
                            x = (obj.get(i).x - leftTop.x) * r;
                            y = (leftTop.y - obj.get(i).y) * r;
                            path.lineTo((float) x, (float) y);
                        }
                    }
                }
                if (roomData.outline != null) {
                    for (List<Point2D> obj : roomData.outline) {
                        x = (obj.get(0).x - leftTop.x) * r;
                        y = (leftTop.y - obj.get(0).y) * r;
                        path.moveTo((float) x, (float) y);
                        for (int i=1; i<obj.size(); i++) {
                            x = (obj.get(i).x - leftTop.x) * r;
                            y = (leftTop.y - obj.get(i).y) * r;
                            path.lineTo((float) x, (float) y);
                        }
                    }
                }
            }
        }
        if (canvas != null) {
            openIndoors.put(indoorKey, indoor);
            openedMaps.add(String.format("%s:%s", indoor.buildingId, indoor.floorId));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.parseColor("#D8D8D8"));
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1);
            canvas.drawPath(pathBuild, paint);

            Paint linepaint = new Paint();
            linepaint.setAntiAlias(true);
            linepaint.setColor(Color.parseColor("#000000"));
            linepaint.setAlpha(128);
            linepaint.setStyle(Paint.Style.STROKE);
            linepaint.setStrokeWidth(1f);
            canvas.drawPath(pathLine, linepaint);

            Paint rgnpaint = new Paint();
            rgnpaint.setAntiAlias(true);
            rgnpaint.setColor(Color.parseColor("#000000"));
            rgnpaint.setAlpha(255);
            rgnpaint.setStyle(Paint.Style.STROKE);
            rgnpaint.setStrokeWidth(1f);
            canvas.drawPath(path, rgnpaint);

            if (ovls == null) {
                ovls = new ArrayList<>();
                namedOverlays.put(indoorKey, ovls);
            }
            boolean needNewOverlay = true;
            for (Overlay overlay : ovls) {
                if (overlay instanceof DrawableOverlay) {
                    needNewOverlay = false;
                    break;
                }
            }
            if (needNewOverlay) {
                DrawableOverlay floorOverlay = new DrawableOverlay();
                floorOverlay.setDrawable(new BitmapDrawable(getResources(), baseBmp), new BoundingBox(leftTop, rightBottom));
                ovls.add(floorOverlay);
                mapView.getOverlays().add(floorOverlay);
            } else {
                for (Overlay overlay : ovls) {
                    if (overlay instanceof DrawableOverlay) {
                        DrawableOverlay floorOverlay = (DrawableOverlay) overlay;
                        floorOverlay.setDrawable(new BitmapDrawable(getResources(), baseBmp), new BoundingBox(leftTop, rightBottom));
                        break;
                    }
                }
            }
        }
        mapView.invalidate();
        renderCalculatdRoute(calculatedRoute);
        if (indoorCallback != null) {
            indoorCallback.done();
            indoorCallback = null;
        }
    }

    private void renderBasementMap(QueryUtils.BasementMapResult basement) {
        String basementKey = String.format(basementKeyTemplate, basement.floorId);
        List<Overlay> ovls = null;
        if (namedOverlays.containsKey(basementKey)) {
            ovls = namedOverlays.get(basementKey);
        }

        Canvas canvas = null;
        Bitmap baseBmp = null;
        Path pathStruct = null;
        Path path = null;
        double r = 0;
        Point2D leftTop = new Point2D();
        Point2D rightBottom = new Point2D();
        if ((basement.structureGeometry != null && basement.structureGeometry.size() > 0)
                ||(basement.floorGeometry != null && basement.floorGeometry.size() > 0)) {
            int dw = mapView.getWidth();
            int dh = mapView.getHeight();
            leftTop = mapView.toMapPoint(new Point(0, 0));
            rightBottom = mapView.toMapPoint(new Point(dw, dh));
            r = (double) dw / (rightBottom.x - leftTop.x);
//            Log.i("--basement", String.format("%d, %d; base: %f, %f", dw, dh, leftTop.x, leftTop.y));
            if (dw > 10 && dh > 10)
                baseBmp = Bitmap.createBitmap((int) dw, (int) dh, Bitmap.Config.ARGB_8888);
            if (baseBmp != null) {
                canvas = new Canvas(baseBmp);
                canvas.drawColor(Color.TRANSPARENT);
                pathStruct = new Path();
                path = new Path();
            }
        }

        if (basement.structureGeometry != null && basement.structureGeometry.size() > 0) {
            for (List<Point2D> obj : basement.structureGeometry) {
                double x, y;
                x = (obj.get(0).x - leftTop.x) * r;
                y = (leftTop.y - obj.get(0).y) * r;
                pathStruct.moveTo((float) x, (float) y);
                for (int i=1; i<obj.size(); i++) {
                    x = (obj.get(i).x - leftTop.x) * r;
                    y = (leftTop.y - obj.get(i).y) * r;
                    pathStruct.lineTo((float) x, (float) y);
                }
            }
        }

        if (basement.floorGeometry != null && basement.floorGeometry.size() > 0) {
            for (List<Point2D> obj : basement.floorGeometry) {
                double x, y;
                x = (obj.get(0).x - leftTop.x) * r;
                y = (leftTop.y - obj.get(0).y) * r;
                path.moveTo((float) x, (float) y);
                for (int i=1; i<obj.size(); i++) {
                    x = (obj.get(i).x - leftTop.x) * r;
                    y = (leftTop.y - obj.get(i).y) * r;
                    path.lineTo((float) x, (float) y);
                }
            }
        }
        if (canvas != null) {
            openBasements.put(basementKey, basement);
            openedMaps.add(String.format("Basement:%s", basement.floorId));
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.parseColor("#2F4F4F"));
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1f);
            canvas.drawPath(pathStruct, paint);
            paint.setColor(Color.parseColor("#5CE7FF"));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(pathStruct, paint);

            paint.setColor(Color.parseColor("#2F4F4F"));
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1f);
            canvas.drawPath(path, paint);
            paint.setColor(Color.parseColor("#5CE7FF"));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);

            if (ovls == null) {
                ovls = new ArrayList<>();
                namedOverlays.put(basementKey, ovls);
            }
            boolean needNewOverlay = true;
            for (Overlay overlay : ovls) {
                if (overlay instanceof DrawableOverlay) {
                    needNewOverlay = false;
                    break;
                }
            }
            if (needNewOverlay) {
                DrawableOverlay floorOverlay = new DrawableOverlay();
                floorOverlay.setDrawable(new BitmapDrawable(getResources(), baseBmp), new BoundingBox(leftTop, rightBottom));
                ovls.add(floorOverlay);
                mapView.getOverlays().add(floorOverlay);
            } else {
                for (Overlay overlay : ovls) {
                    if (overlay instanceof DrawableOverlay) {
                        DrawableOverlay floorOverlay = (DrawableOverlay) overlay;
                        floorOverlay.setDrawable(new BitmapDrawable(getResources(), baseBmp), new BoundingBox(leftTop, rightBottom));
                        break;
                    }
                }
            }
        }
        mapView.invalidate();
        renderCalculatdRoute(calculatedRoute);
        if (indoorCallback != null) {
            indoorCallback.done();
            indoorCallback = null;
        }
    }

    private void renderCalculatdRoute(NetWorkAnalystUtil.CalculatedRoute route) {
        List<Overlay> ovls;
        if (namedOverlays.containsKey(calculatdRouteKey)) {
            ovls = namedOverlays.get(calculatdRouteKey);
            for (Overlay ov: ovls)
                mapView.getOverlays().remove(ov);
            ovls.clear();
        } else {
            ovls = new ArrayList<>();
            namedOverlays.put(calculatdRouteKey, ovls);
        }

        if (route == null || route.route == null || route.route.size() <= 0)
            return;

//        Path shape = new Path();
//        shape.moveTo(0, 40);
//        shape.lineTo(20, 0);
//        shape.lineTo(40, 40);
//        PathEffect pathEffect = new PathDashPathEffect(shape, 100, 20, PathDashPathEffect.Style.MORPH);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
//        if (route.start != null)
//            paint.setColor(route.start.color);
//        else if (route.end != null)
//            paint.setColor(route.end.color);
//        else if (route.way != null && route.way.length > 0)
//            paint.setColor(route.way[0].color);
//        else
        paint.setColor(route.presentationStyle.fillColor);
        paint.setAlpha(route.presentationStyle.opacity);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setPathEffect(new CornerPathEffect(20));
        paint.setStrokeWidth(route.presentationStyle.lineWidth);

        calculatedRoute = route;
        // 绘制室外路线
        int index = 0;
        for (String range : calculatedRoute.range) {
            if (range.equalsIgnoreCase("Outdoor")) {
                com.supermap.services.components.commontypes.Path[] paths = calculatedRoute.route.get(index);
                for (com.supermap.services.components.commontypes.Path path : paths) {
                    if (path.route != null && path.route.points != null && path.route.points.length > 0) {
                        List<Point2D> point2DS = new ArrayList<>();
                        for (com.supermap.services.components.commontypes.Point2D point : path.route.points)
                            point2DS.add(new Point2D(point.x, point.y));
                        LineOverlay overlay = new LineOverlay(paint);
                        overlay.setLinePaint(paint);
                        overlay.setPointPaint(paint);
                        overlay.setShowPoints(false);
                        overlay.setData(point2DS);
                        ovls.add(overlay);
                    }
                }
            }
            index++;
        }
        // 绘制室内路线
        index = 0;
        for (String range : calculatedRoute.range) {
            if (range.equalsIgnoreCase(currIndoorMap)) {
                com.supermap.services.components.commontypes.Path[] paths = calculatedRoute.route.get(index);
                for (com.supermap.services.components.commontypes.Path path : paths) {
                    if (path.route != null && path.route.points != null && path.route.points.length > 0) {
                        List<Point2D> point2DS = new ArrayList<>();
                        for (com.supermap.services.components.commontypes.Point2D point : path.route.points)
                            point2DS.add(new Point2D(point.x, point.y));
                        LineOverlay overlay = new LineOverlay(paint);
                        overlay.setLinePaint(paint);
                        overlay.setPointPaint(paint);
                        overlay.setShowPoints(false);
                        overlay.setData(point2DS);
                        ovls.add(overlay);
                    }
                }
            }
            index++;
        }
//        for (List<Point2D> point2DS : route.route) {
//            if (point2DS.size() > 0) {
//                if (route.start != null)
//                    point2DS.add(0, new Point2D(route.start.coords[1], route.start.coords[0]));
//                if (route.end != null)
//                    point2DS.add(new Point2D(route.end.coords[1], route.end.coords[0]));
//                LineOverlay overlay = new LineOverlay(paint);
//                overlay.setLinePaint(paint);
//                overlay.setPointPaint(paint);
//                overlay.setShowPoints(false);
//                overlay.setData(point2DS);
//                ovls.add(overlay);
//            }
//        }

        DefaultItemizedOverlay overlay = new DefaultItemizedOverlay(null);
        Marker marker;
        OverlayItemEx item;
        MarkerDrawable drawable;
        if (route.start != null) {
            if (route.start.marker == null) {
                marker = new Marker(route.start.coords, "起", 40, 65, route.start);
                item = new OverlayItemEx(
                        new Point2D(marker.position[1], marker.position[0]),
                        marker.markerId, marker.markerId, marker);
                drawable = new MarkerDrawable(getContext().getResources().getDrawable(R.drawable.green_marker, null), marker.width, marker.height);
            } else {
                marker = new Marker(route.start.coords, "起", route.start.mkWidth, route.start.mkHeight, route.start);
                item = new OverlayItemEx(
                        new Point2D(marker.position[1], marker.position[0]),
                        marker.markerId, marker.markerId, marker);
                drawable = new MarkerDrawable(route.start.marker, marker.width, marker.height);
            }
            item.setMarker(drawable);
            overlay.addItem(item);
        }
        if (route.end != null) {
            if (route.end.marker == null) {
                marker = new Marker(route.end.coords, "终", 40, 65, route.end);
                item = new OverlayItemEx(
                        new Point2D(marker.position[1], marker.position[0]),
                        marker.markerId, marker.markerId, marker);
                drawable = new MarkerDrawable(getContext().getResources().getDrawable(R.drawable.red_marker, null), marker.width, marker.height);
            } else {
                marker = new Marker(route.end.coords, "终", route.end.mkWidth, route.end.mkHeight, route.end);
                item = new OverlayItemEx(
                        new Point2D(marker.position[1], marker.position[0]),
                        marker.markerId, marker.markerId, marker);
                drawable = new MarkerDrawable(route.end.marker, marker.width, marker.height);
            }
            item.setMarker(drawable);
            overlay.addItem(item);
        }
        if (route.way != null) {
            for (int wi = 0; wi < route.way.length; wi++) {
                if (!TextUtils.isEmpty(route.way[wi].floorid)) {
                    String l;
                    if (TextUtils.isEmpty(route.way[wi].buildingId))
                        l = String.format("Basement:%s", route.way[wi].floorid);
                    else
                        l = String.format("%s:%s", route.way[wi].buildingId, route.way[wi].floorid);
                    if (!currIndoorMap.equalsIgnoreCase(l))
                        continue;
                }
                if (route.way[wi].marker != null) {
                    marker = new Marker(route.way[wi].coords, "终", route.way[wi].mkWidth, route.way[wi].mkHeight, route.way[wi]);
                    item = new OverlayItemEx(
                            new Point2D(marker.position[1], marker.position[0]),
                            marker.markerId, marker.markerId, marker);
                    drawable = new MarkerDrawable(route.way[wi].marker, marker.width, marker.height);
                    item.setMarker(drawable);
                    overlay.addItem(item);
                }
            }
        }
        for (List<NetWorkAnalystUtil.WayPoint> wayPoints : route.wayPoints) {
            for (NetWorkAnalystUtil.WayPoint wayPoint : wayPoints) {
                if (!TextUtils.isEmpty(wayPoint.floor)) {
                    String l;
                    if (TextUtils.isEmpty(wayPoint.building))
                        l = String.format("Basement:%s", wayPoint.floor);
                    else
                        l = String.format("%s:%s", wayPoint.building, wayPoint.floor);
                    if (!currIndoorMap.equalsIgnoreCase(l))
                        continue;

                    if (wayPoint.catalog.equalsIgnoreCase("Lift")) {
                        if (defaultFacilities.containsKey("Lift")) {
                            GeneralMarker mk = defaultFacilities.get("Lift");
                            if (mk == null)
                                continue;

                            double[] coords = new double[]{wayPoint.point.y, wayPoint.point.x};
                            String mkid = UUID.randomUUID().toString().replaceAll("-", "");
                            marker = new Marker(coords, mkid, mk.width, mk.height, null);
                            item = new OverlayItemEx(
                                    new Point2D(marker.position[1], marker.position[0]),
                                    marker.markerId, marker.markerId, marker);
                            drawable = new MarkerDrawable(mk.image, mk.width, mk.height);
                            item.setMarker(drawable);
                            overlay.addItem(item);
                        }
                    } else if (wayPoint.catalog.equalsIgnoreCase("InOut")) {
                        if (defaultFacilities.containsKey("InOut")) {
                            GeneralMarker mk = defaultFacilities.get("InOut");
                            if (mk == null)
                                continue;

                            double[] coords = new double[]{wayPoint.point.y, wayPoint.point.x};
                            String mkid = UUID.randomUUID().toString().replaceAll("-", "");
                            marker = new Marker(coords, mkid, mk.width, mk.height, null);
                            item = new OverlayItemEx(
                                    new Point2D(marker.position[1], marker.position[0]),
                                    marker.markerId, marker.markerId, marker);
                            drawable = new MarkerDrawable(mk.image, mk.width, mk.height);
                            item.setMarker(drawable);
                            overlay.addItem(item);
                        }
                    }
                }
            }
        }
        ovls.add(overlay);
        mapView.getOverlays().addAll(ovls);
        mapView.invalidate();
    }

    private void renderPerimeter(QueryUtils.PerimeterResult perimeter) {
        List<Overlay> ovls;
        if (namedOverlays.containsKey(perimeterKey)) {
            ovls = namedOverlays.get(perimeterKey);
        } else {
            ovls = new ArrayList<>();
            namedOverlays.put(perimeterKey, ovls);
        }

        if (perimeter.normalGeometry != null) {
            Paint normalPaint = new Paint();
            normalPaint.setAntiAlias(true);
            normalPaint.setColor(Color.BLUE);
            normalPaint.setAlpha(150);
            normalPaint.setStyle(Paint.Style.STROKE);
            normalPaint.setStrokeJoin(Paint.Join.ROUND);
            normalPaint.setPathEffect(new CornerPathEffect(5));
            normalPaint.setStrokeWidth(10);

            for (List<Point2D> point2DS : perimeter.normalGeometry) {
                if (point2DS.size() > 0) {
                    LineOverlay overlay = new LineOverlay(normalPaint);
                    overlay.setLinePaint(normalPaint);
                    overlay.setShowPoints(false);
                    overlay.setData(point2DS);
                    ovls.add(overlay);
                    mapView.getOverlays().add(overlay);
                }
            }
        }

        if (perimeter.alarmGeometry != null) {
            Paint alarmPaint = new Paint();
            alarmPaint.setAntiAlias(true);
            alarmPaint.setColor(Color.RED);
            alarmPaint.setAlpha(150);
            alarmPaint.setStyle(Paint.Style.STROKE);
            alarmPaint.setStrokeJoin(Paint.Join.ROUND);
            alarmPaint.setPathEffect(new CornerPathEffect(5));
            alarmPaint.setStrokeWidth(10);

            for (List<Point2D> point2DS : perimeter.alarmGeometry) {
                if (point2DS.size() > 0) {
                    LineOverlay overlay = new LineOverlay(alarmPaint);
                    overlay.setLinePaint(alarmPaint);
                    overlay.setShowPoints(false);
                    overlay.setData(point2DS);
                    ovls.add(overlay);
                    mapView.getOverlays().add(overlay);
                }
            }
        }

        mapView.invalidate();
    }

    private void renderModel(QueryUtils.ModelResult model) {
        List<Overlay> ovls;
        if (namedOverlays.containsKey(modelsKey)) {
            ovls = namedOverlays.get(modelsKey);
            for (Overlay ov: ovls)
                mapView.getOverlays().remove(ov);
            ovls.clear();
        } else {
            ovls = new ArrayList<>();
            namedOverlays.put(modelsKey, ovls);
        }

        if (isShowHighLight) {
            if (model.normalGeometry != null) {
                Paint normalPaint = new Paint();
                normalPaint.setAntiAlias(true);
                normalPaint.setColor(model.normalStyle.fillColor);
                normalPaint.setAlpha(model.normalStyle.opacity);
                normalPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                normalPaint.setStrokeJoin(Paint.Join.ROUND);
                normalPaint.setStrokeWidth(model.normalStyle.lineWidth);

                for (List<Point2D> point2DS : model.normalGeometry) {
                    if (point2DS.size() > 0) {
                        PolygonOverlay lot = new PolygonOverlay(normalPaint);
                        lot.setShowPoints(false);
                        lot.setData(point2DS);
                        ovls.add(lot);
                    }
                }
            }

            if (model.highlightGeometry != null) {
                for (int i=0; i<model.highlightGeometry.size(); i++) {
                    List<Point2D> point2DS = model.highlightGeometry.get(i);
                    if (point2DS.size() > 0) {
                        PresentationStyle ps = model.highlightStyle.get(i);
                        Paint paint = new Paint();
                        paint.setAntiAlias(true);
                        paint.setColor(ps.fillColor);
                        paint.setAlpha(ps.opacity);
                        paint.setStyle(Paint.Style.FILL_AND_STROKE);
                        paint.setStrokeJoin(Paint.Join.ROUND);
                        paint.setStrokeWidth(ps.lineWidth);

                        PolygonOverlay lot = new PolygonOverlay(paint);
                        lot.setShowPoints(false);
                        lot.setData(point2DS);
                        ovls.add(lot);
                    }
                }
            }
        }
        if (ovls != null && ovls.size() > 0)
            mapView.getOverlays().addAll(ovls);

        mapView.invalidate();
    }

    private Paint getBuildingSelectPaint(boolean select) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#2262CC"));
        if (select)
            paint.setAlpha(128);
        else
            paint.setAlpha(0);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(10);

        return paint;
    }
}
