package com.example.turnbyturn

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.mapbox.android.core.location.*
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdate
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.services.android.navigation.ui.v5.map.NavigationMapboxMap
import com.mapbox.services.android.navigation.ui.v5.route.OnRouteSelectionChangeListener
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


const val LOCATION_PERMISSION_REQUEST = 1001
private const val DEFAULT_ZOOM = 12.0
private const val DEFAULT_BEARING = 0.0
private const val DEFAULT_TILT = 0.0
private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 1000
private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS: Long = 500

class MainActivity : AppCompatActivity(), OnMapReadyCallback, OnRouteSelectionChangeListener {

    private var map: NavigationMapboxMap? = null
    private var locationEngine: LocationEngine? = null
    private var navigation: MapboxNavigation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        navigation = MapboxNavigation(application, resources.getString(R.string.mapbox_access_token))
        requestPermissions()

        showRoute.setOnClickListener {
            val origin = Point.fromLngLat(-122.396449, 37.791256)
            val destination = Point.fromLngLat(-122.479752, 37.830322)

            NavigationRoute.builder(this@MainActivity)
                    .accessToken(resources.getString(R.string.mapbox_access_token))
                    .origin(origin)
                    .destination(destination)
                    .alternatives(true)
                    .build()
                    .getRoute(object : Callback<DirectionsResponse> {
                        override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                            map?.drawRoutes(response.body()?.routes()!!)
                            val bounds = LatLngBounds.Builder()
                                    .include(LatLng(origin.latitude(), origin.longitude()))
                                    .include(LatLng(destination.latitude(), destination.longitude()))
                                    .build()
                            val left = resources.getDimension(R.dimen.route_overview_padding_left).toInt()
                            val top = resources.getDimension(R.dimen.route_overview_padding_top).toInt()
                            val right = resources.getDimension(R.dimen.route_overview_padding_right).toInt()
                            val bottom = resources.getDimension(R.dimen.route_overview_padding_bottom).toInt()
                            val padding = intArrayOf(left, top, right, bottom)
                            updateMapCameraFor(bounds, padding, 2000)
                        }

                        override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                        }
                    })

        }
    }

    public override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    public override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    private fun requestPermissions() {
        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        val permissionGranted = PackageManager.PERMISSION_GRANTED
        val allPermissionsGranted = ContextCompat.checkSelfPermission(this, locationPermission) == permissionGranted
        onPermissionGranted(allPermissionsGranted)
    }

    private fun onPermissionGranted(permissionGranted: Boolean) {
        if (!permissionGranted) {
            val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST)
        } else {
            locationEngine = LocationEngineProvider.getBestLocationEngine(application)
            navigation!!.locationEngine = locationEngine as LocationEngine
            requestLocation()
            mapView.getMapAsync(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            onPermissionGranted(granted)
        }
    }

    private fun buildCameraUpdateFrom(location: Location): CameraUpdate {
        return CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                .zoom(DEFAULT_ZOOM)
                .target(LatLng(location.latitude, location.longitude))
                .bearing(DEFAULT_BEARING)
                .tilt(DEFAULT_TILT)
                .build())
    }

    fun updateMapCameraFor(bounds: LatLngBounds, padding: IntArray, duration: Int) {
        map?.retrieveMap()?.let { map ->
            val position = map.getCameraForLatLngBounds(bounds, padding)
            position?.let {
                map.animateCamera(CameraUpdateFactory.newCameraPosition(it), duration)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        val request = buildEngineRequest()
        locationEngine?.requestLocationUpdates(request, object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult?) {
                navigation?.addProgressChangeListener(object : ProgressChangeListener {
                    override fun onProgressChange(location: Location?, routeProgress: RouteProgress?) {

                    }
                })
                map?.retrieveMap()?.animateCamera(buildCameraUpdateFrom(result?.lastLocation!!), 2000)
                map?.updateLocation(result?.lastLocation)
            }

            override fun onFailure(exception: Exception) {

            }
        }, null)
    }

    private fun buildEngineRequest(): LocationEngineRequest {
        return LocationEngineRequest.Builder(UPDATE_INTERVAL_IN_MILLISECONDS)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
                .build()
    }

    // OnMapReadyCallback functions ////////////////////////////////////////////////////////////////
    override fun onMapReady(mapboxMap: MapboxMap) {
        mapboxMap.setStyle(Style.MAPBOX_STREETS) {
            map = NavigationMapboxMap(mapView, mapboxMap)
            map?.setOnRouteSelectionChangeListener(this)
        }
    }

    // OnRouteSelectionChangeListener functions ////////////////////////////////////////////////////
    override fun onNewPrimaryRouteSelected(directionsRoute: DirectionsRoute?) {
        navigation?.startNavigation(directionsRoute!!)
    }
}
