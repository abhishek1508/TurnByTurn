package com.example.turnbyturn

import android.Manifest
import android.arch.lifecycle.ViewModelProviders
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.SpannableStringBuilder
import android.view.View.VISIBLE
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraUpdate
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.services.android.navigation.ui.v5.map.NavigationMapboxMap
import com.mapbox.services.android.navigation.ui.v5.summary.SummaryBottomSheet
import com.mapbox.services.android.navigation.v5.milestone.Milestone
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import kotlinx.android.synthetic.main.activity_main.*

const val LOCATION_PERMISSION_REQUEST = 10001
class TurnByTurn : AppCompatActivity(), TurByTurnController.TurnByTurnView {

    private var map: NavigationMapboxMap? = null
    private val viewModel by lazy(mode = LazyThreadSafetyMode.NONE) {
        ViewModelProviders.of(this).get(TurnByTurnViewModel::class.java)
    }

    private val controller by lazy(mode = LazyThreadSafetyMode.NONE) {
        TurByTurnController(viewModel, this)
    }

    // Activity overriden methods //////////////////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init(savedInstanceState)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        controller.onPermissionResult(requestCode, grantResults)
    }

    // Private methods /////////////////////////////////////////////////////////////////////////////////////////////////
    private fun init(savedInstanceState: Bundle?) {
        mapView.onCreate(savedInstanceState)
        showRoute.setOnClickListener { controller.findRoute() }
        navigate.setOnClickListener { controller.onNavigateClicked() }
        cancelNavigation.setOnClickListener { controller.cancelNavigation() }
        navigate.hide()

        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        val permissionGranted = PackageManager.PERMISSION_GRANTED
        val allPermissionsGranted = ContextCompat.checkSelfPermission(this, locationPermission) == permissionGranted
        controller.onPermissionsGranted(allPermissionsGranted)
    }

    // OnMapReadyCallback overriden methods ////////////////////////////////////////////////////////////////////////////
    override fun onMapReady(mapboxMap: MapboxMap) {
        mapboxMap.setStyle(Style.MAPBOX_STREETS) {
            map = NavigationMapboxMap(mapView, mapboxMap)
            map?.setOnRouteSelectionChangeListener(this)
            map?.updateLocationLayerRenderMode(RenderMode.NORMAL)
            controller.buildDynamicCameraFrom(mapboxMap)
        }
    }

    // OnRouteSelectionChangeListener overriden methods ////////////////////////////////////////////////////////////////
    override fun onNewPrimaryRouteSelected(directionsRoute: DirectionsRoute?) {
        controller.onNewRouteSelected(directionsRoute)
    }

    // TurnByTurnView overriden methods ////////////////////////////////////////////////////////////////////////////////
    override fun requestPermissions() {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST)
    }

    override fun initialize() {
        controller.initialize(this)
        mapView.getMapAsync(this)
    }

    override fun updateMapCameraView(cameraUpdate: CameraUpdate, duration: Int) {
        map?.retrieveMap()?.animateCamera(cameraUpdate, duration)
    }

    override fun updateMapWithLocation(location: Location?) {
        map?.updateLocation(location)
    }

    override fun animateCameraToIncDestination(bounds: LatLngBounds, padding: IntArray, duration: Int) {

        map?.retrieveMap()?.let { map ->
            val position = map.getCameraForLatLngBounds(bounds, padding)
            position?.let {
                map.animateCamera(CameraUpdateFactory.newCameraPosition(it), duration)
            }
        }
    }

    override fun animateCameraToIncDestination(
        bounds: LatLngBounds,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int,
        duration: Int
    ) {
        map?.retrieveMap()?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingLeft, paddingTop, paddingRight, paddingBottom))
    }

    override fun showDestinationMarker(destination: Point) {
        map?.addMarker(this, destination)
    }

    override fun showAllRoutes(routes: MutableList<DirectionsRoute>?) {
        map?.drawRoutes(routes!!)
    }

    override fun controlSummaryViewVisibility(visibility: Int) {
        summaryViewGroup.visibility = visibility
    }

    override fun showNavigateFAB() {
        navigate.show()
    }

    override fun hideNavigateFAB() {
        navigate.hide()
    }

    override fun controlRouteButtonVisibility(visibility: Int) {
        showRoute.visibility = visibility
    }

    override fun updateSummaryView(remainingTime: SpannableStringBuilder, remainingDistance: String, eta: String) {
        timeRemaining.text = remainingTime
        distanceRemaining.text = remainingDistance
        arrivalTime.text = eta
    }

    override fun changeCameraTrackingMode(trackingMode: Int) {
        map?.updateCameraTrackingMode(trackingMode)
    }

    override fun changeLocationRenderMode(renderMode: Int) {
        map?.updateLocationLayerRenderMode(renderMode)
    }

    override fun hideAlternativeRoutes() {
        map?.showAlternativeRoutes(false)
    }

    override fun addMapProgressChangeListener(navigation: MapboxNavigation) {
        map?.addProgressChangeListener(navigation)
    }

    override fun controlInstructionViewVisibility(visibility: Int) {
        instructionView.visibility = visibility
    }

    override fun updateInstructionView(progress: RouteProgress) {
        instructionView.updateDistanceWith(progress)
    }

    override fun updateInstructionViewWithMilestone(milestone: Milestone) {
        instructionView.updateBannerInstructionsWith(milestone)
    }

    override fun removeMarkers() {
        map?.clearMarkers()
    }

    override fun removeRoute() {
        map?.removeRoute()
    }

    override fun resetMapPadding(padding: IntArray) {
        map?.adjustLocationIconWith(padding)
    }
}