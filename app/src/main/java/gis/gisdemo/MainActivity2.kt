package gis.gisdemo

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import gis.hmap.*
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity2 : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, GeoServiceCallback, IndoorCallback {

    private val mPerms = arrayOf(
            "android.permission.INTERNET",
            "android.permission.LOCATION_HARDWARE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE",
            "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_PHONE_STATE",
            "android.permission.VIBRATE",
            "android.permission.WAKE_LOCK")

    private var cnt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initToolbar()
        initGisView()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
        nav_view.setNavigationItemSelectedListener(this)
    }

    private fun initGisView() {
        gisView.setLogEnable(true)
        gisView.setGisServer("http://42.202.130.191:8090/iserver/services")//测试地址
//        gisView.setGisServer("http://182.16.132.12:8090/iserver/services");//济南本地正式地址

        gisView.loadMap(2, doubleArrayOf(36.65221619825378, 117.16909751245657), "jinanQxiangmu", "jinanQxiangmu")
//        gisView.loadMap(5, new double[]{22.6573017046106460, 114.0576151013374200}, "BTYQ", "BTYQ");
//        List<String> exparam = new ArrayList<>();
//        exparam.add("X-HW-ID=com.huawei.gis_lbs");
//        exparam.add("X-HW-APPKEY=sTcZjQDrvIW5qSf3JnEDMA==");
//        gisView.loadMap(2, new double[] {36.65221619825378, 117.16909751245657}, "BTYQ", "BTYQ", exparam);

        gisView.setRouteFacility(
                arrayOf("Lift", "InOut"),
                arrayOf(GeneralMarker(null, null, resources.getDrawable(R.drawable.elevator, null), 32, 32, null), GeneralMarker(null, null, resources.getDrawable(R.drawable.door, null), 32, 32, null)))
        gisView.setMaxZoomLevel(8)
        checkPermission()
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!PermissionDetect.hasPermissions(this, *mPerms)) {
                ActivityCompat.requestPermissions(this, mPerms, 1001)
            } else {
                initLoc()
            }
        } else {
            initLoc()
        }
    }

    private fun initLoc() {
        GisView.initEngine(applicationContext,
                "NzNhZDZkYzgtYmQyNy00MmQ3LWJjY2UtOGY2YTViZmVhYTYy",
                "xgAUPmQsEPU+iwt+TNJZ7va+Td5ri3EgHp6+pSNS0jY",
                "https://100.95.92.144",
                "/api/66aea767-6429-4b20-8ec0-74f4e58c60fe/HuaweiServer/locationRequest",
                true,
                "Pl0sh9bE8TZBhz8Nz+7PDg==",
                "com.huawei.jinanq.ioc",
                "http://apigw-beta.huawei.com/api/service/Qproject/locationRequest")
    }

    /**
     * 权限的结果回调函数
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
        }
        initLoc()
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gisView.deinitEngine()
    }

    override fun onNavigationItemSelected(p0: MenuItem): Boolean {
        when(p0.itemId) {
            R.id.loadMap -> gisView.loadMap(2, doubleArrayOf(36.65221619825378, 117.16909751245657), "jinanQxiangmu", "jinanQxiangmu")
            R.id.unloadMap -> gisView.destroyMap()
            R.id.encodeAddress -> gisView.getAddressOfLocation(117.16909751245657, 36.65221619825378, this)
            R.id.decodeAddress -> {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("搜索地址")
                val input = EditText(this)
                input.inputType = InputType.TYPE_CLASS_TEXT
                builder.setView(input)
                builder.setPositiveButton("搜索") { dialog, which ->
                    val data = input.text.toString()
                    gisView.getLocationOfAddress(data,  this) //位置搜索（模糊匹配）
                }
                builder.setNegativeButton("取消") { dialog, which -> dialog.cancel() }
                builder.show()
            }
            R.id.loadA03B1 -> gisView.showIndoorMap("A04", "B1", this)
            R.id.loadF1 -> gisView.showIndoorMap("A04","F1", this)
            R.id.loadF2 -> gisView.showIndoorMap("A04","F2", this)
            R.id.loadF3 -> gisView.showIndoorMap("A04","F3", this)
            R.id.loadF4 -> gisView.showIndoorMap("A04","F4", this)
            R.id.roomstyle -> {
                val roomStyle = RoomStyle()
                roomStyle.lineColor = Color.parseColor("#909000")
                roomStyle.lineOpacity = 150
                roomStyle.lineWidth = 2
                roomStyle.fillColor = Color.parseColor("#009090")
                roomStyle.fillOpacity = 255
                gisView.setRoomStyle("A04", "F2", "HY09", roomStyle)
                gisView.setRoomStyle("A04", "F2", "HY10", roomStyle)
                gisView.setRoomStyle("A04", "F2", "HY11", roomStyle)
            }
            R.id.delroomstyle -> {
                gisView.setRoomStyle("A04", "F2", "HY09", null)
                gisView.setRoomStyle("A04", "F2", "HY10", null)
                gisView.setRoomStyle("A04", "F2", "HY11", null)
            }
            R.id.typestyle -> {
                val roomStyle = RoomStyle()
                roomStyle.lineColor = Color.parseColor("#ff0000")
                roomStyle.lineOpacity = 150
                roomStyle.lineWidth = 2
                roomStyle.fillColor = Color.parseColor("#ff9090")
                roomStyle.fillOpacity = 128
                gisView.setRoomStyle("A03", "F1", "洗衣机", "TYPE", roomStyle)
            }
            R.id.deltypestyle -> gisView.setRoomStyle("A03", "F1", "洗衣机", "TYPE", null)
            R.id.loadB1 -> gisView.showIndoorMap("", "B01")
            R.id.loadOutdoor -> gisView.switchOutdoor()
            R.id.addMarker -> {
                val markers = arrayOf(GeneralMarker(
                        doubleArrayOf(22.655299147231652, 114.05824998467759),
                        String.format("marker%d", cnt++),
                        resources.getDrawable(R.drawable.marker_1, null),
                        64, 64, null), GeneralMarker(
                        doubleArrayOf(22.65024607457551, 114.05212154169743),
                        String.format("marker%d", cnt++),
                        resources.getDrawable(R.drawable.marker_2, null),
                        64, 64, null))
                gisView.addMarker("lm01", 999, markers)
            }
            R.id.addMarkerUrl -> {
                val markers = arrayOf(GeneralMarker(
                        doubleArrayOf(36.65221619825378 + (Math.random() - 0.5) / 1000, 117.16909751245657 + (Math.random() - 0.5) / 1000),
                        String.format("marker%d", cnt++), "./images/pic1.png", 64, 64, null), GeneralMarker(
                        doubleArrayOf(36.65221619825378 + (Math.random() - 0.5) / 1000, 117.16909751245657 + (Math.random() - 0.5) / 1000),
                        String.format("marker%d", cnt++), "./images/pic2.png", 64, 64, null))
                gisView.addMarker("lm01", 999, markers)
            }
            R.id.addFlashMarker -> {
                val ani = arrayOf(resources.getDrawable(R.drawable.marker_1, null), resources.getDrawable(R.drawable.marker_2, null), resources.getDrawable(R.drawable.marker_3, null), resources.getDrawable(R.drawable.marker_4, null), resources.getDrawable(R.drawable.marker_5, null))
                val markers = arrayOf(FlashMarker(
                        doubleArrayOf(36.65221619825378 + (Math.random() - 0.5) / 1000, 117.16909751245657 + (Math.random() - 0.5) / 1000),
                        String.format("marker%d", cnt++), ani, 500, 10000, 64, 64, null), FlashMarker(
                        doubleArrayOf(36.65221619825378 + (Math.random() - 0.5) / 1000, 117.16909751245657 + (Math.random() - 0.5) / 1000),
                        String.format("marker%d", cnt++), ani, 500, 10000, 64, 64, null))
                //gisView.addMarker("lm02", 999, markers);
                gisView.addFlashMarker("lm02", 999, markers)
            }
            R.id.addFlashMarkerUrl -> {
                val ani = arrayOf("./images/1.png", "./images/2.png", "./images/3.png", "./images/4.png", "./images/5.png")
                val markers = arrayOf(FlashMarker(
                        doubleArrayOf(36.65221619825378 + (Math.random() - 0.5) / 1000, 117.16909751245657 + (Math.random() - 0.5) / 1000),
                        String.format("marker%d", cnt++), ani, 500, 10000, 64, 64, null), FlashMarker(
                        doubleArrayOf(36.65221619825378 + (Math.random() - 0.5) / 1000, 117.16909751245657 + (Math.random() - 0.5) / 1000),
                        String.format("marker%d", cnt++), ani, 500, 10000, 64, 64, null))
                gisView.addFlashMarker("lm02", 999, markers)
            }
            R.id.zoom1 -> gisView.setZoom(doubleArrayOf(36.65221619825378, 117.16909751245657), 1)
            R.id.zoom7 -> gisView.setZoom(doubleArrayOf(36.65221619825378, 117.16909751245657), 7)
            R.id.zoomIn -> gisView.zoomInMap()
            R.id.zoomOut -> gisView.zoomOutMap()
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    /**
     * 查询地址信息回调
     */
    override fun onQueryAddressFinished(loc: Array<out GeoLocation>?) {
        runOnUiThread {
            for (i in 0 until loc!!.size) {
                val c = loc[i]
                val markers = arrayOf(GeneralMarker(
                        doubleArrayOf(c.lat, c.lng),
                        c.address,
                        resources.getDrawable(R.drawable.tag_pin, null),
                        72, 72, null))
                gisView.addMarker("lm01", 999, markers)
                gisView.addPopup(
                        doubleArrayOf(c.lat, c.lng),
                        c.address,
                        doubleArrayOf(0.0, 0.0),
                        300,
                        100,
                        "hello marker"
                )
            }
        }
    }

    /**
     * 绘制室内地图回调
     */
    override fun done() {
        Toast.makeText(this@MainActivity2, "室内显示完成", Toast.LENGTH_LONG).show()
    }

}