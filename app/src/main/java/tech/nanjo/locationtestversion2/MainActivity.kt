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
import android.util.Log
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
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import tech.nanjo.locationtestversion2.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.StringReader

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationManager: LocationManager
    private lateinit var currentRouteName: String

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

    private fun parseGpx(): ArrayList<Location> {
        val result = ArrayList<Location>()
        val xml = getSharedFile()

        if (xml != null) {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()

            xpp.setInput(StringReader(xml))

            var event = xpp.eventType
            var isReadingName = false

            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    if (xpp.name == "metadata") {
                        // Skip metadata
                        xpp.next()
                        xpp.next()
                        xpp.next()
                    }

                    if (xpp.name == "rtept") {
                        val location = Location(null)
                        location.latitude = xpp.getAttributeValue(0).toDouble()
                        location.longitude = xpp.getAttributeValue(1).toDouble()
                        result.add(location)
                    } else if (xpp.name == "name") {
                        isReadingName = true
                    }
                }

                if (event == XmlPullParser.TEXT && isReadingName) {
                    currentRouteName = xpp.text
                    isReadingName = false
                }

                event = xpp.next()
            }

            Log.d("parseGpx", "Route name: $currentRouteName")
        }

        return result
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

    fun lahinpiste(): Int { //foreach komennolla katsotaan lähin piste omaan sijaintiin
        var etaisuus = 0f
        var lahinpiste = 0 //listan indeksi
        var lahinpistex = 0 // väliaikainen arvon määritystä varten
        val omap = getLocation()
        val pisteet = parseGpx()

        if (omap != null) {
            for (xy in pisteet) {
                val lista = FloatArray(3)

                Location.distanceBetween(
                    omap.latitude,
                    omap.longitude,
                    xy.latitude,
                    xy.longitude,
                    lista
                )

                /*
                    Listan sisältö:
                    lista[0]: Etäisyys
                    lista[1]: Alkuperäinen suunta
                    lista[2]: Lopullinen suunta
                */

                val etpist = lista[0]
                if ((etaisuus == 0f) || (etaisuus > etpist)) {
                    etaisuus = etpist
                    lahinpiste = lahinpistex
                }
                lahinpistex++
            }
        }
        return (lahinpiste)
    }
// kun puhelimesta laittaa pilotin päälle, käynnisty uusi säie
    // input: sijainti, reitti, lähin reitin pisteen indeksi ja reitin suunta eikun ei, koska sen pitää kutsua uusia arvoja koko ajan


    fun pilot() {
        Thread(Runnable {

            // kun lista on annettu, pitää laittaa erillinen nappi näyttöön myöhemmin
            while (true) {
                val pisteet = parseGpx()
                val indeksi = lahinpiste()
                val sijainti = getLocation()
                val suunta = sijainti?.bearingTo(pisteet[indeksi]) // suunta pitäisi saada näytölle testaamista varten

                }
                Thread.sleep(1000)
        }).start()
            //pitää sisällä bluetoothin lähetyksen ja kaiken lähetettävän käsittelyn
            //while () pitää lisätä pilotin päällä pitävä osa
            //var sijainti = getLocation()
            //var reitti = parseGpx()
            //var lahindex = lahinpiste() //indeksi
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

        parseGpx()

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