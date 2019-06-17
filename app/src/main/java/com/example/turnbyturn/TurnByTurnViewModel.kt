package com.example.turnbyturn

import android.annotation.SuppressLint
import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.location.Location
import com.mapbox.android.core.location.*
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.services.android.navigation.ui.v5.voice.NavigationSpeechPlayer
import com.mapbox.services.android.navigation.ui.v5.voice.SpeechAnnouncement
import com.mapbox.services.android.navigation.ui.v5.voice.SpeechPlayerProvider
import com.mapbox.services.android.navigation.ui.v5.voice.VoiceInstructionLoader
import com.mapbox.services.android.navigation.v5.location.replay.ReplayRouteLocationEngine
import com.mapbox.services.android.navigation.v5.milestone.Milestone
import com.mapbox.services.android.navigation.v5.milestone.MilestoneEventListener
import com.mapbox.services.android.navigation.v5.milestone.VoiceInstructionMilestone
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import okhttp3.Cache
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.*

private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 1000
private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS: Long = 500
private const val INSTRUCTION_CACHE = "instruction-cache"
private const val TEN_MEGABYTE_CACHE_SIZE: Long = 10 * 1024 * 1024

class TurnByTurnViewModel(application: Application) : AndroidViewModel(application) {

    val location: MutableLiveData<Location> = MutableLiveData()
    val routes: MutableLiveData<MutableList<DirectionsRoute>> = MutableLiveData()
    val progress: MutableLiveData<RouteProgress> = MutableLiveData()
    val mileStone: MutableLiveData<Milestone> = MutableLiveData()
    val locationEngineCallback: MyLocationEngineCallback
    val speechPlayer: NavigationSpeechPlayer

    lateinit var routeToNavigate: DirectionsRoute

    private val accessToken: String = application.resources.getString(R.string.mapbox_access_token)
    private val locationEngine: LocationEngine = LocationEngineProvider.getBestLocationEngine(application)
    private val navigation: MapboxNavigation = MapboxNavigation(application, accessToken)

    init {
        val english = Locale.US.language
        val cache = Cache(File(application.cacheDir, INSTRUCTION_CACHE), TEN_MEGABYTE_CACHE_SIZE)
        val voiceInstructionLoader = VoiceInstructionLoader(getApplication(), accessToken, cache)
        val speechPlayerProvider = SpeechPlayerProvider(getApplication(), english, true, voiceInstructionLoader)
        speechPlayer = NavigationSpeechPlayer(speechPlayerProvider)
        navigation.locationEngine = locationEngine
        navigation.addProgressChangeListener { loc, routeProgress ->
            progress.value = routeProgress
            location.value = loc
        }
        navigation.addMilestoneEventListener { _, _, milestone ->
            mileStone.value = milestone
            if (milestone is VoiceInstructionMilestone) {
                play(milestone)
            }
        }

        locationEngineCallback = MyLocationEngineCallback(location = location)
    }

    fun getNavigation(): MapboxNavigation {
        return navigation
    }

    @SuppressLint("MissingPermission")
    fun requestLocation() {
        val request = buildEngineRequest()
        locationEngine.requestLocationUpdates(request, locationEngineCallback, null)
    }

    fun findRoute(origin: Point, destination: Point) {
        NavigationRoute.builder(this.getApplication())
            .accessToken(accessToken)
            .origin(origin)
            .destination(destination)
            .alternatives(true)
            .build()
            .getRoute(object: Callback<DirectionsResponse> {
                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {

                }

                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    locationEngine.removeLocationUpdates(locationEngineCallback)
                    routes.value = response.body()?.routes()
                }

            })
    }

    fun onNewRouteSelected(route: DirectionsRoute?) {
        routeToNavigate = route!!
    }

    fun startNavigation() {
        routeToNavigate.let {
            val replayRouteLocationEngine = ReplayRouteLocationEngine()
            replayRouteLocationEngine.assign(it)
            navigation.locationEngine = replayRouteLocationEngine
            navigation.startNavigation(it)
        }
    }

    fun stopNavigation() {
        navigation.stopNavigation()
    }

    private fun buildEngineRequest(): LocationEngineRequest {
        return LocationEngineRequest.Builder(UPDATE_INTERVAL_IN_MILLISECONDS)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
            .build()
    }

    private fun play(milestone: VoiceInstructionMilestone) {
        val announcement = SpeechAnnouncement.builder()
                .voiceInstructionMilestone(milestone)
                .build()
        speechPlayer.play(announcement)
    }

    inner class MyLocationEngineCallback(val location: MutableLiveData<Location>): LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult) {
            location.value = result.lastLocation
        }

        override fun onFailure(exception: java.lang.Exception) {

        }

    }

}