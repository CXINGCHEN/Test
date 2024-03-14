package com.openpositioning.PositionMe.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
//import android.location.LocationRequest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.maps.android.PolyUtil;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.Traj;
import com.openpositioning.PositionMe.sensors.SensorFusion;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IndoorFragment extends Fragment implements
        SeekBar.OnSeekBarChangeListener {

    // Initialize LatLng for position and default zoom for Google Maps
    private LatLng position;
    private float zoom = 19f;
    // GoogleMap object to control map operations
    private GoogleMap googleMap;
    // Maximum transparency value for map overlays
    private static final int TRANSPARENCY_MAX = 100;

    //Coordinates for adding overlays
    private static final LatLng NUCLEUS = new LatLng(55.9227604, -3.1737929);
    private static final LatLng LIBRARY = new LatLng(55.9230749, -3.1751933);

    //Lists for indoor maps
    private List<BitmapDescriptor> nucleus_images = new ArrayList<>();
    private List<BitmapDescriptor> library_images = new ArrayList<>();
    private List<BitmapDescriptor> activeImages;

    //Overlay for buildings
    private GroundOverlay groundOverlay_nucleus;
    private GroundOverlay groundOverlay_library;
    private GroundOverlay activeOverlay;


    private SeekBar transparencyBar;
    private CheckBox NucleusBuilding;
    private CheckBox Library;

    private Polyline currentPolyline;

    //flag for drawing path
    private boolean isDrawing = false;

    private FusedLocationProviderClient fusedLocationClient;

    //Lists for store the coordinates of buildings
    private List<LatLng> NucleusbuildingBoundary = new ArrayList<>();
    private List<LatLng> LibraryBoundary = new ArrayList<>();

    // latitude and longitude for nucleus building
    double nucleuslat1 = 55.9233313;
    double nucleuslng1 = -3.1746281;

    double nucleuslat2 = 55.9228130;
    double nucleuslng2 = -3.1746291;

    double nucleuslat3 = 55.9227886;
    double nucleuslng3 = -3.1741097;

    double nucleuslat4 = 55.9228865;
    double nucleuslng4 = -3.1738707;

    double nucleuslat5 = 55.9233201;
    double nucleuslng5 = -3.1738224;

    // latitude and longitude for library
    double librarylat1 = 55.9227931;
    double librarylng1 = -3.1751974;

    double librarylat2 = 55.9230642;
    double librarylng2 = -3.1751819;

    double librarylat3 = 55.9230653;
    double librarylng3 = -3.1747531;

    double librarylat4 = 55.9228027;
    double librarylng4 = -3.1747632;

    boolean INucleus = false;
    boolean InLibrary = false;


    private SensorManager sensorManager;
    private Sensor pressureSensor;


    // Threshold for detecting floor changes.
    private static final float NUCLEUS_FLOOR_HEIGHT_THRESHOLD = 5.5f;
    private static final float LIBRARY_FLOOR_HEIGHT_THRESHOLD = 4f;

    // Last known elevation, initialize with the starting elevation
    private float lastElevation;
    private boolean isLastElevationSet = false;

    // Current floor level, initialize with the starting floor
    private int currentFloorLevel = 0;
    float cumulativeElevationChange = 0;
    private SensorFusion sensorFusion = SensorFusion.getInstance();

    //Initialise the users' current floor
    int userFloor = 0;
    //Initialise the floor spinner position
    int spinnerPosition = 1;


    public IndoorFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_indoor, container, false);
        // Setup Google Map Fragment and UI elements
        setupMapFragment();
        initializeUI(rootView);
        // Setup buttons for drawing on the map
        setupDrawingButtons(rootView);


        // Load images after initializing UI but before any user interaction
        loadNucleusGroundOverlayImages();
        loadLibraryGroundOverlayImages();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(getActivity());
        return rootView;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onStart() {
        super.onStart();
        fusedLocationClient.requestLocationUpdates(createLocationRequest(), locationCallback, Looper.getMainLooper());
    }

    private LocationRequest createLocationRequest() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(500);
        locationRequest.setFastestInterval(500);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return locationRequest;
    }

    private LocationCallback locationCallback = new LocationCallback() {
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) {
                return;
            }
            for (Location location : locationResult.getLocations()) {
                // Update the polyline with the new location
                onLocationChanged(new LatLng(location.getLatitude(), location.getLongitude()));
                // Add textview to display GNSS position and positioning error
                if (getView() != null) {
                    TextView accuracyTextView = getView().findViewById(R.id.positionAccuracy);
                    accuracyTextView.setText(String.format(Locale.getDefault(), "Lat: %f, Lon: %f\nAccuracy: ± %f",
                            location.getLatitude(),
                            location.getLongitude(),
                            location.getAccuracy()));
                }

            }
        }
    };

    // If the user's location changed, do something
    public void onLocationChanged(LatLng newLocation) {
        updatePath(newLocation);
        if (!isLocationInsideBuilding(newLocation, NucleusbuildingBoundary)) {
            INucleus = false;
        }
        if (!isLocationInsideBuilding(newLocation, LibraryBoundary)) {
            InLibrary = false;
        }
        if (isLocationInsideBuilding(newLocation, NucleusbuildingBoundary) && !INucleus) {
            // User is inside the building boundary, prompt for displaying the indoor map
            INucleus = true;
            promptForIndoorMap();
        } else if (isLocationInsideBuilding(newLocation, LibraryBoundary) && !InLibrary) {
            InLibrary = true;
            promptForIndoorMap();
        }

    }

    private void setupDrawingButtons(View rootView) {
        Button startDrawingButton = rootView.findViewById(R.id.start_drawing_button);
        Button clearDrawingButton = rootView.findViewById(R.id.clear_drawing_button);

        startDrawingButton.setOnClickListener(v -> {
            startDrawing();
        });
        clearDrawingButton.setOnClickListener(v -> {
            clearDrawing();
        });
    }

    //Method to start drawing the path
    private void startDrawing() {
        isDrawing = true;
        if (currentPolyline != null) {
            currentPolyline.remove();
        }
        currentPolyline = googleMap.addPolyline(new PolylineOptions());
        Toast.makeText(getContext(), "Tracking path ... ", Toast.LENGTH_SHORT).show();
    }
    //Method to clear the path
    private void clearDrawing() {
        if (currentPolyline != null) {
            currentPolyline.remove();
            currentPolyline = null;
        }
        isDrawing = false;
    }
    //Method to update the path drawing
    public void updatePath(LatLng newPoint) {
        if (isDrawing && currentPolyline != null) {
            List<LatLng> points = currentPolyline.getPoints();
            points.add(newPoint);
            currentPolyline.setPoints(points);
        }
    }

    private void initializeUI(View rootView) {
        setupMapTypeSpinner(rootView);
        setupTransparencyBar(rootView);
        BuildingSelect(rootView);
    }

    private void setupMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.startMap);
        mapFragment.getMapAsync(this::configureMap);

    }

    @SuppressLint("MissingPermission")
    private void configureMap(GoogleMap mMap) {
        googleMap = mMap;
        mMap.setMyLocationEnabled(true);
        addBuildingBoundaries(mMap);
        //set map UI
        mMap.setMapType(mMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setTiltGesturesEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(true);
        mMap.getUiSettings().setScrollGesturesEnabled(true);
        updateStartPosition();


    }

    private void updateStartPosition() {
        float[] startPosition = sensorFusion.getGNSSLatitude(false);
        position = new LatLng(startPosition[0], startPosition[1]);
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, zoom));
    }


    private void addNucleusGroundOverlay(GoogleMap googleMap) {
        loadNucleusGroundOverlayImages();
        GroundOverlayOptions options = new GroundOverlayOptions()
                .image(nucleus_images.get(1)).anchor(1, 1).position(NUCLEUS, 69f, 73f);
        groundOverlay_nucleus = googleMap.addGroundOverlay(options);
    }

    private void addLibraryGroundOverlay(GoogleMap googleMap) {
        loadLibraryGroundOverlayImages();
        GroundOverlayOptions options1 = new GroundOverlayOptions().image(library_images.get(1)).anchor(0, 0).position(LIBRARY, 28f, 36f);
        groundOverlay_library = googleMap.addGroundOverlay(options1);
    }

    private void removeNucleusGroundOverlay(GoogleMap googleMap) {
        if (groundOverlay_nucleus != null) {
            groundOverlay_nucleus.remove();
            //groundOverlay_nucleus = null; // Clear the reference
        }
    }
    private void removeLibraryGroundOverlay(GoogleMap googleMap) {
        if (groundOverlay_library != null) {
            groundOverlay_library.remove();
            //groundOverlay_library = null; // Clear the reference
        }
    }

    private void loadNucleusGroundOverlayImages() {
        nucleus_images.clear();
        nucleus_images.add(BitmapDescriptorFactory.fromResource(R.drawable.nucleuslg));
        nucleus_images.add(BitmapDescriptorFactory.fromResource(R.drawable.nucleusg));
        nucleus_images.add(BitmapDescriptorFactory.fromResource(R.drawable.nucleus1));
        nucleus_images.add(BitmapDescriptorFactory.fromResource(R.drawable.nucleus2));
        nucleus_images.add(BitmapDescriptorFactory.fromResource(R.drawable.nucleus3));

    }
    private void loadLibraryGroundOverlayImages() {
        library_images.clear();
        library_images.add(BitmapDescriptorFactory.fromResource(R.drawable.librarylg));
        library_images.add(BitmapDescriptorFactory.fromResource(R.drawable.libraryg));
        library_images.add(BitmapDescriptorFactory.fromResource(R.drawable.library1));
        library_images.add(BitmapDescriptorFactory.fromResource(R.drawable.library2));
        library_images.add(BitmapDescriptorFactory.fromResource(R.drawable.library3));
    }
    // Define building boundaries
    private void addBuildingBoundaries(GoogleMap googleMap) {
        // ... Add LatLng points ...
        NucleusbuildingBoundary.add(new LatLng(nucleuslat1, nucleuslng1));
        NucleusbuildingBoundary.add(new LatLng(nucleuslat2, nucleuslng2));
        NucleusbuildingBoundary.add(new LatLng(nucleuslat3, nucleuslng3));
        NucleusbuildingBoundary.add(new LatLng(nucleuslat4, nucleuslng4));
        NucleusbuildingBoundary.add(new LatLng(nucleuslat5, nucleuslng5));
        NucleusbuildingBoundary.add(new LatLng(nucleuslat1, nucleuslng1));

        LibraryBoundary.add(new LatLng(librarylat1, librarylng1));
        LibraryBoundary.add(new LatLng(librarylat2, librarylng2));
        LibraryBoundary.add(new LatLng(librarylat3, librarylng3));
        LibraryBoundary.add(new LatLng(librarylat4, librarylng4));
        LibraryBoundary.add(new LatLng(librarylat1, librarylng1));


        // Create and add polygon for building
        googleMap.addPolygon(new PolygonOptions()
                .addAll(NucleusbuildingBoundary)
                .strokeColor(Color.BLUE)
                .strokeWidth(5));

        // Repeat for library
        googleMap.addPolygon(new PolygonOptions()
                .addAll(LibraryBoundary)
                .strokeColor(Color.RED)
                .strokeWidth(5));
    }

    // Method to check if the user location is within the building boundary
    private boolean isLocationInsideBuilding(LatLng userLocation, List<LatLng> buildingBoundary) {
        return PolyUtil.containsLocation(userLocation, buildingBoundary, true);
    }

    //Method to ask if usesr would like to change to indoor mode
    // and confirm the current floor for the accuracy of floor change detection
    private void promptForIndoorMap() {
        Spinner overlaySpinner = getView().findViewById(R.id.floorLevelSpinner);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Indoor Map Available");
        builder.setMessage("You are inside the building. Would you like to view the indoor map? If so, please enter your current floor level below.");
        sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sensorManager.registerListener(pressureSensorListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        final EditText floorInput = new EditText(getContext());
        floorInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        floorInput.setHint("Enter your current floor here: ");
        builder.setView(floorInput);

        builder.setPositiveButton("Yes", null); // Set null listener initially
        builder.setNegativeButton("No", null);

        final AlertDialog dialog = builder.create();

        // Show the dialog and set the button listeners after dialog is shown
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                if (NucleusBuilding != null && INucleus) {
                    NucleusBuilding.setChecked(true);
                } else if (Library != null && InLibrary) {
                    Library.setChecked(true);
                }
                String floorStr = floorInput.getText().toString();
                userFloor = Integer.parseInt(floorStr);
                if (!floorStr.isEmpty() )  {

                    overlaySpinner.setSelection(floorLevelToSpinnerPosition(userFloor), true);
                    dialog.dismiss(); // Only dismiss the dialog when you're ready
                } else {
                    // Optionally, show an error or request input
                    floorInput.setError("Please enter a valid floor");
                }
            });

            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            negativeButton.setOnClickListener(view -> dialog.dismiss());
        });

        dialog.show();
    }

    //Method to change the indoor maps for different buildings, when user select a specific building.
    private void BuildingSelect(View rootView) {
        NucleusBuilding = rootView.findViewById(R.id.NucleusBuilding_checkbox);
        Library = rootView.findViewById(R.id.Library_checkbox);

        NucleusBuilding.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Library.setChecked(false);
                addNucleusGroundOverlay(googleMap);
                removeLibraryGroundOverlay(googleMap);
                setActiveOverlayAndImages(groundOverlay_nucleus, nucleus_images);
            } else if (!NucleusBuilding.isChecked()) {
                removeNucleusGroundOverlay(googleMap);
                setActiveOverlayAndImages(null, null);
            }
        });

        Library.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                NucleusBuilding.setChecked(false);
                addLibraryGroundOverlay(googleMap);
                removeNucleusGroundOverlay(googleMap);
                setActiveOverlayAndImages(groundOverlay_library, library_images);
            } else if (!Library.isChecked()) {
                removeLibraryGroundOverlay(googleMap);
                setActiveOverlayAndImages(null, null);
            }
        });
    }


    private void checkFloorChange() {
        float currentElevation = sensorFusion.getElevation();
        Log.d("currentElevation", "currentElevation:" + currentElevation);
        float elevationChange = currentElevation - lastElevation;
        Log.d("elevationChange", "elevationChange" + elevationChange);
        cumulativeElevationChange += elevationChange;
        Log.d("cumulativechange", "cumulativechange is:" + cumulativeElevationChange);
        // Accumulate elevation changes
        // Check if the cumulative elevation change is significant enough to indicate a floor change
        if (NucleusBuilding.isChecked()){
            // checkAndHandleFloorChange(currentElevation, NUCLEUS_FLOOR_HEIGHT_THRESHOLD);
        }
        if (Library.isChecked()){
            // checkAndHandleFloorChange(currentElevation, LIBRARY_FLOOR_HEIGHT_THRESHOLD);
        }
    }

    //Method to check if the floor has changed
    private void checkAndHandleFloorChange(float elevation, float floor_height_threshold) {
        Log.d("elevation", "elevation" + elevation);
        if (!isLastElevationSet) {
            lastElevation = elevation;
            isLastElevationSet = true;
            return; // Skip the floor change check this time
        }
        if (Math.abs(elevation - lastElevation) > floor_height_threshold) {
            // Determine if you've moved up or down
            userFloor += (elevation > lastElevation) ? 1 : -1;
            // Update UI or notify the app about the floor change
            updateFloorLevelSpinner(userFloor);
            // Update lastElevation for the next comparison
            lastElevation = elevation;
            isLastElevationSet = true;
            notifyUserAboutFloorChange(userFloor);
        }
    }

    private final SensorEventListener pressureSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
                float pressure = event.values[0];
                float elevation = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure);
                // Check for significant elevation change that might indicate a floor change
                if(NucleusBuilding.isChecked()) {
                    checkAndHandleFloorChange(elevation,NUCLEUS_FLOOR_HEIGHT_THRESHOLD);

                }
                if(NucleusBuilding.isChecked()) {
                    checkAndHandleFloorChange(elevation,LIBRARY_FLOOR_HEIGHT_THRESHOLD);
                }
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Handle accuracy changes here if needed
        }
    };


    // Method to display a toast message to notify the user the floor change
    private void notifyUserAboutFloorChange(int floorLevel) {
        // Use getActivity() to ensure that the context is valid and prevent any crashes
        if (getActivity() != null) {
            // Display a toast message to notify the user
            Toast.makeText(getActivity(), "You are now on floor " + floorLevel, Toast.LENGTH_SHORT).show();

        }
    }


    // Method to update the floor level spinner based on the current floor
    private void updateFloorLevelSpinner(int floorLevel) {
        Spinner overlaySpinner = getView().findViewById(R.id.floorLevelSpinner);
        // Set the spinner to the correct floor index based on the currentFloorLevel
        // This might involve mapping the floor level to the spinner index
        spinnerPosition = floorLevelToSpinnerPosition(floorLevel);
        overlaySpinner.setSelection(spinnerPosition); // Change spinner selection
    }


    // Method to map the floor level to the spinner position
    private int floorLevelToSpinnerPosition(int floorLevel) {
        // Implement this based on how your spinner is set up.
        return floorLevel + 1; // indoor map list start from -1 floor with indicate [0], if user is in floor 0, display [0+1]
    }

    private void setupOverlaySpinner(View rootView) {
        Spinner overlaySpinner = rootView.findViewById(R.id.floorLevelSpinner);
        ArrayAdapter<CharSequence> overlayAdapter = ArrayAdapter.createFromResource(getContext(), R.array.floor_levels, android.R.layout.simple_spinner_dropdown_item);
        overlaySpinner.setAdapter(overlayAdapter);
        //overlaySpinner.setSelection(1);
        overlaySpinner.setSelection(floorLevelToSpinnerPosition(userFloor), true);
        overlaySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (activeOverlay != null && activeImages != null && !activeImages.isEmpty()) {
                    BitmapDescriptor descriptor = activeImages.get(position % activeImages.size());
                    // Log the conditions for debugging, can be deleted
                    Log.d("IndoorFragment", "activeOverlay is null: " + (activeOverlay == null));
                    Log.d("IndoorFragment", "activeImages is null: " + (activeImages == null));
                    Log.d("IndoorFragment", "activeImages is empty: " + (activeImages != null && activeImages.isEmpty()));
                    activeOverlay.setImage(descriptor);
                }
                else {
                    Log.e("IndoorFragment", "Active overlay or images are not set correctly.");
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }


    private void setupMapTypeSpinner(View rootView) {
        Spinner mapTypeSpinner = rootView.findViewById(R.id.mapTypeSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(), R.array.map_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapTypeSpinner.setAdapter(adapter);

        mapTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (googleMap != null) {
                    switch (position) {
                        case 0: googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);break;
                        case 1: googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);break; }
                } else {
                    Log.d("MapType", "GoogleMap not initialized"); } }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action needed here
            }}); }

    private void setActiveOverlayAndImages(GroundOverlay overlay, List<BitmapDescriptor> images) {
        activeOverlay = overlay;
        activeImages = images;
        setupOverlaySpinner(getView()); // Update the spinner based on the selected building
    }


    private void setupTransparencyBar(View rootView) {
        transparencyBar = rootView.findViewById(R.id.transparencySeekBar);
        transparencyBar.setMax(TRANSPARENCY_MAX);
        transparencyBar.setProgress(0);
        transparencyBar.setOnSeekBarChangeListener(this);
    }


    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (groundOverlay_nucleus != null) {
            groundOverlay_nucleus.setTransparency((float) progress / TRANSPARENCY_MAX);
        }
        if (groundOverlay_library != null){
            groundOverlay_library.setTransparency((float) progress / TRANSPARENCY_MAX);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) { }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) { }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sensorFusion != null) {
            //sensorFusion.setOrientationListener(null);
        }
    }
    @Override
    public void onStop() {
        super.onStop();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(pressureSensorListener);
        }
    }
}


