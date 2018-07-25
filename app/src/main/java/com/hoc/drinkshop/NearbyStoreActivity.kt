package com.hoc.drinkshop

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.location.LocationManager
import android.location.LocationManager.GPS_PROVIDER
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.HttpException
import retrofit2.Retrofit

class NearbyStoreActivity : AppCompatActivity(), OnMapReadyCallback, AnkoLogger {
    override val loggerTag = "MY_NEARBY_TAG"

    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private val subject = BehaviorProcessor.create<Location>()
    private val compositeDisposable = CompositeDisposable()
    private var storeMarkers: List<Marker> = emptyList()

    private var map: GoogleMap? = null
    private val fusedLocationProviderClient by lazy(LazyThreadSafetyMode.NONE) {
        FusedLocationProviderClient(this@NearbyStoreActivity)
    }
    private val locationCallback by lazy(LazyThreadSafetyMode.NONE) {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult?.lastLocation?.let {
                    updateMyLocation(it)
                    subject.onNext(it)
                }
            }
        }
    }
    private val locationRequest by lazy(LazyThreadSafetyMode.NONE) {
        LocationRequest().apply {
            interval = 5_000
            fastestInterval = 3_000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = 10f
        }
    }
    private var currentMarker: Marker? = null
    private var dialog: AlertDialog? = null

    override fun onStart() {
        super.onStart()
        requestLocationUpdate()
        updateNearbyStore()
    }

    private fun updateNearbyStore() {
        subject
            .onBackpressureLatest()
            .switchMap {
                info("switchMap: (${it.latitude}, ${it.longitude})")
                apiService.getNearbyStore(
                    it.latitude,
                    it.longitude,
                    3_000
                ).subscribeOn(Schedulers.io())
            }.observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onError = {
                    when (it) {
                        is HttpException -> it.response()
                            .errorBody()
                            ?.let(retrofit::parseResultErrorMessage)
                        else -> it.message
                    }.let { it ?: "An error occurred" }.let(::toast)
                },
                onNext = {
                    map?.run {
                        info("onNext: ${it.size}")
                        storeMarkers.forEach { it.remove() }
                        storeMarkers = it.map { (_, name, loc, distanceInMetters) ->
                            addMarker(
                                MarkerOptions()
                                    .position(LatLng(loc.lat, loc.lng))
                                    .title(name)
                                    .snippet("Distance: ${distanceInMetters}m")
                                    .icon(
                                        bitmapDescriptorFromVector(
                                            this@NearbyStoreActivity,
                                            R.drawable.ic_store_primary_24dp
                                        )
                                    )
                            )
                        }
                    }
                }
            )
            .addTo(compositeDisposable)
    }

    private fun requestLocationUpdate() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED
        ) return
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }

    override fun onStop() {
        super.onStop()
        removeLocationUpdate()
        compositeDisposable.clear()
    }

    private fun removeLocationUpdate() {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED
        ) return
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nearby_store)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_RC
            )
        }
    }

    private fun updateMyLocation(location: Location) {
        map?.run {
            currentMarker?.remove()
            val latLng = LatLng(location.latitude, location.longitude)
            currentMarker = addMarker(
                MarkerOptions().position(latLng)
                    .title("My Location")
            )
            moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(
                        latLng,
                        16f
                    )
                )
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_RC -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            ACCESS_FINE_LOCATION
                        ) != PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            this,
                            ACCESS_COARSE_LOCATION
                        ) != PERMISSION_GRANTED
                    ) return
                    map?.isMyLocationEnabled = true
                    showDialogEnableGps()
                    requestLocationUpdate()
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            uiSettings.run {
                isZoomControlsEnabled = true
                isCompassEnabled = true
                isMyLocationButtonEnabled = true
            }
        }

        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED
        ) return
        map?.isMyLocationEnabled = true
        showDialogEnableGps()
        requestLocationUpdate()
    }

    private fun showDialogEnableGps() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(GPS_PROVIDER)) {
            dialog = AlertDialog.Builder(this)
                .setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setNegativeButton("No") { dialog, _ -> dialog.cancel() }
                .setPositiveButton("Yes") { dialog, _ ->
                    dialog.dismiss()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .create()
                .apply { show() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.takeIf { it.isShowing }?.dismiss()
    }

    companion object {
        const val LOCATION_PERMISSION_RC = 1
    }
}

fun bitmapDescriptorFromVector(context: Context, @DrawableRes vectorResId: Int): BitmapDescriptor {
    val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)!!.apply {
        setBounds(
            0,
            0,
            intrinsicWidth,
            intrinsicHeight
        )
    }
    val bitmap = Bitmap.createBitmap(
        vectorDrawable.intrinsicWidth,
        vectorDrawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
    )
    vectorDrawable.draw(Canvas(bitmap))
    return BitmapDescriptorFactory.fromBitmap(bitmap)!!
}