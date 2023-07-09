package tech.nanjo.locationtestversion2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import tech.nanjo.locationtestversion2.databinding.ActivityMainBinding
import java.io.BufferedReader

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationManager: LocationManager

    private fun getSharedFile(): String? {
        val receivedAction = intent.action

        if (receivedAction.equals(Intent.ACTION_SEND)) {
            val receivedUri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

            if (receivedUri != null && receivedUri.path != null) {
                val inputStream = contentResolver.openInputStream(receivedUri)
                if (inputStream != null) {
                    val content = inputStream.bufferedReader().use(BufferedReader::readText)
                    inputStream.close()
                    return content
                }
            }
        }

        return null
    }

    private fun getLocationPermission(): Boolean {
        var permissionGranted = false

        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    false
                ) -> {
                    permissionGranted = true
                }

                permissions.getOrDefault(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    false
                ) -> {
                    permissionGranted = true
                }
            }
        }

        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        return permissionGranted
    }

    private fun setLocationManager() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val gpsLocationListener: LocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val networkLocationListener: LocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (hasGps) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                getLocationPermission()
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500,
                0F,
                gpsLocationListener
            )
        }

        if (hasNetwork) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                getLocationPermission()
            }

            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                500,
                0F,
                networkLocationListener
            )
        }
    }

    private fun getLocation(): Location? {
        var locationByGps: Location? = null
        var locationByNetwork: Location? = null
        var currentLocation: Location? = null

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            getLocationPermission()
        }

        val lastKnownLocationByGps =
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        lastKnownLocationByGps?.let {
            locationByGps = lastKnownLocationByGps
        }

        val lastKnownLocationByNetwork =
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        lastKnownLocationByNetwork?.let {
            locationByNetwork = lastKnownLocationByNetwork
        }

        if (locationByGps != null && locationByNetwork != null) {
            currentLocation = if (locationByGps!!.accuracy > locationByNetwork!!.accuracy) {
                locationByGps
            } else {
                locationByNetwork
            }
        }

        return currentLocation
    }

    fun updateSharedText() {
        val textView: TextView? = findViewById(R.id.textview_first)
        if (textView != null) {
            textView.text = getSharedFile() ?: "No shared text"
        }
    }

    fun updateLocationText() {
        val textView: TextView? = findViewById(R.id.textview_second)
        val location: Location? = getLocation()
        if (textView != null) {
            if (location != null) textView.text =
                "Latitude: ${location.latitude}\nLongitude: ${location.longitude}"
            else textView.text = "Unknown location"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        updateSharedText()

        getLocationPermission()
        setLocationManager()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener {
            updateSharedText()
            updateLocationText()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}