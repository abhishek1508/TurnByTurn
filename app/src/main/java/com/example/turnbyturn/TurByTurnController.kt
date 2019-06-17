package com.example.turnbyturn

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.Observer
import android.content.pm.PackageManager
import android.location.Location
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.view.View
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdate
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.services.android.navigation.ui.v5.camera.DynamicCamera
import com.mapbox.services.android.navigation.ui.v5.camera.NavigationCamera
import com.mapbox.services.android.navigation.ui.v5.route.OnRouteSelectionChangeListener
import com.mapbox.services.android.navigation.ui.v5.summary.SummaryModel
import com.mapbox.services.android.navigation.v5.milestone.Milestone
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation
import com.mapbox.services.android.navigation.v5.navigation.NavigationConstants
import com.mapbox.services.android.navigation.v5.navigation.NavigationTimeFormat
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgressState
import com.mapbox.services.android.navigation.v5.utils.DistanceFormatter
import com.mapbox.services.android.navigation.v5.utils.LocaleUtils
import com.mapbox.services.android.navigation.v5.utils.time.TimeFormatter.formatTime
import com.mapbox.services.android.navigation.v5.utils.time.TimeFormatter.formatTimeRemaining
import java.util.*

const val PERMISSION_REQUEST_CODE = 1001
private const val DEFAULT_ZOOM = 12.0
private const val DEFAULT_BEARING = 0.0
private const val DEFAULT_TILT = 0.0

class TurByTurnController(val viewModel: TurnByTurnViewModel, val view: TurnByTurnView) {

    private val origin: Point = Point.fromLngLat(-122.396368, 37.791561)
    //private val origin: Point = Point.fromLngLat(-121.953944, 	37.4148716)
    private val destination: Point = Point.fromLngLat(-122.397169, 37.793180)
    //private val destination: Point = Point.fromLngLat(	-121.956151, 37.41817655)
    private var startNavigation = false

    private lateinit var distanceFormatter: DistanceFormatter

    fun onPermissionsGranted(isPermissionGranted: Boolean) {
        if (isPermissionGranted) {
            view.initialize()
        } else {
            view.requestPermissions()
        }
    }

    fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            onPermissionsGranted(grantResults[0] == PackageManager.PERMISSION_GRANTED)
        }
    }

    fun initialize(owner: LifecycleOwner) {
        initializeDistanceFormatter()
        viewModel.location.observe(owner, Observer { onLocationUpdate(it) })
        viewModel.routes.observe(owner, Observer { onRoutesFound(it) })
        viewModel.progress.observe(owner, Observer { onProgressUpdate(it) })
        viewModel.mileStone.observe(owner, Observer { onMilestoneUpdate(it) })
        viewModel.requestLocation()
    }

    fun buildDynamicCameraFrom(mapboxMap: MapboxMap) {
        viewModel.getNavigation().cameraEngine = DynamicCamera(mapboxMap)
    }

    fun findRoute() {
        viewModel.findRoute(origin, destination)
    }

    fun onNewRouteSelected(directionsRoute: DirectionsRoute?) {
        viewModel.onNewRouteSelected(directionsRoute)
    }

    fun onNavigateClicked() {
        startNavigation = true
        view.addMapProgressChangeListener(viewModel.getNavigation())
        view.hideAlternativeRoutes()
        view.changeLocationRenderMode(RenderMode.GPS)
        view.changeCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_NORTH)
        view.controlInstructionViewVisibility(View.VISIBLE)
        view.hideNavigateFAB()
        view.controlRouteButtonVisibility(View.GONE)
        view.controlSummaryViewVisibility(View.VISIBLE)
        viewModel.startNavigation()
    }

    fun cancelNavigation() {
        viewModel.stopNavigation()
        clearNavigationState()
    }

    private fun clearNavigationState() {
        startNavigation = false
        viewModel.requestLocation()
        view.changeLocationRenderMode(RenderMode.NORMAL)
        view.changeCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_NONE)
        view.controlInstructionViewVisibility(View.GONE)
        view.controlRouteButtonVisibility(View.VISIBLE)
        view.controlSummaryViewVisibility(View.GONE)
        view.removeMarkers()
        view.removeRoute()
        view.resetMapPadding(intArrayOf(0, 0, 0, 0))
    }

    private fun buildCameraUpdateFrom(location: Location?): CameraUpdate {
        return CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .zoom(DEFAULT_ZOOM)
                .target(LatLng(location?.latitude!!, location.longitude))
                .bearing(DEFAULT_BEARING)
                .tilt(DEFAULT_TILT)
                .build())
    }

    private fun onMilestoneUpdate(milestone: Milestone?) {
        milestone?.let {
            view.updateInstructionViewWithMilestone(it)
        }
    }

    private fun onProgressUpdate(progress: RouteProgress?) {
        progress?.let {
            view.updateInstructionView(it)
            if (progress.currentState() == RouteProgressState.ROUTE_ARRIVED) {
                viewModel.stopNavigation()
                clearNavigationState()
            } else {
                val distanceRemaining = distanceFormatter.formatDistance(progress.distanceRemaining()).toString()
                val legDurationRemaining = progress.currentLegProgress().durationRemaining()
                val timeRemaining = formatTimeRemaining(MainApplication.instance, legDurationRemaining)
                val time = Calendar.getInstance();
                val isTwentyFourHourFormat = DateFormat.is24HourFormat(MainApplication.instance)
                val arrivalTime = formatTime(time, legDurationRemaining, NavigationTimeFormat.TWELVE_HOURS, isTwentyFourHourFormat)
                view.updateSummaryView(timeRemaining, distanceRemaining, arrivalTime)
            }
        }
    }

    private fun onLocationUpdate(location: Location?) {
        location.let {
            if (!startNavigation) {
                view.updateMapCameraView(buildCameraUpdateFrom(it), 2000)
            } else {

            }
        }
        view.updateMapWithLocation(location)
    }

    private fun onRoutesFound(routes: MutableList<DirectionsRoute>?) {
        val bounds = LatLngBounds.Builder()
            .include(LatLng(origin.latitude(), origin.longitude()))
            .include(LatLng(destination.latitude(), destination.longitude()))
            .build()

        val left = MainApplication.instance?.resources?.getDimension(R.dimen.route_overview_padding_left)?.toInt()
        val top = MainApplication.instance?.resources?.getDimension(R.dimen.route_overview_padding_top)?.toInt()
        val right = MainApplication.instance?.resources?.getDimension(R.dimen.route_overview_padding_right)?.toInt()
        val bottom = MainApplication.instance?.resources?.getDimension(R.dimen.route_overview_padding_bottom)?.toInt()
        val padding = intArrayOf(left!!, top!!, right!!, bottom!!)
        view.animateCameraToIncDestination(bounds, padding, 2000)
        view.showDestinationMarker(destination)
        view.showAllRoutes(routes)
        view.showNavigateFAB()
        viewModel.onNewRouteSelected(routes!![0])
    }

    private fun initializeDistanceFormatter() {
        val localeUtils = LocaleUtils()
        val language = localeUtils.inferDeviceLanguage(MainApplication.instance)
        val unitType = localeUtils.getUnitTypeForDeviceLocale(MainApplication.instance)
        val roundingIncrement = NavigationConstants.ROUNDING_INCREMENT_FIFTY
        distanceFormatter = DistanceFormatter(MainApplication.instance, language, unitType, roundingIncrement)
    }

    interface TurnByTurnView: OnMapReadyCallback, OnRouteSelectionChangeListener {
        fun requestPermissions()

        fun initialize()

        fun updateMapCameraView(cameraUpdate: CameraUpdate, duration: Int)

        fun updateMapWithLocation(location: Location?)

        fun showDestinationMarker(destination: Point)

        fun showAllRoutes(routes: MutableList<DirectionsRoute>?)

        fun animateCameraToIncDestination(bounds: LatLngBounds, padding: IntArray, duration: Int)

        fun animateCameraToIncDestination(bounds: LatLngBounds, paddingLeft: Int, paddingTop: Int, paddingRight: Int, paddingBottom: Int, duration: Int)

        fun addMapProgressChangeListener(navigation: MapboxNavigation)

        fun changeLocationRenderMode(@RenderMode.Mode renderMode: Int)

        fun changeCameraTrackingMode(@NavigationCamera.TrackingMode trackingMode: Int)

        fun hideAlternativeRoutes()

        fun controlInstructionViewVisibility(visibility: Int)

        fun showNavigateFAB()

        fun controlSummaryViewVisibility(visibility: Int)

        fun hideNavigateFAB()

        fun controlRouteButtonVisibility(visibility: Int)

        fun updateInstructionView(progress: RouteProgress)

        fun updateSummaryView(remainingTime: SpannableStringBuilder, remainingDistance: String, eta: String)

        fun updateInstructionViewWithMilestone(milestone: Milestone)

        fun removeRoute()

        fun removeMarkers()

        fun resetMapPadding(padding: IntArray)
    }
}