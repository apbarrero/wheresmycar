# Where's My Car 🚗

An Android app that automatically saves your parking location when your Bluetooth device disconnects, so you never forget where you parked again!

## 📱 Purpose

**Where's My Car** solves the common problem of forgetting where you parked your car. The app works by:

1. **Connecting** to a Bluetooth device in your car (stereo, OBD port adapter, etc.)
2. **Monitoring** the connection status in the background
3. **Automatically saving** your GPS location when the device disconnects (when you park and turn off your car)
4. **Showing** you the saved location when you need to find your car later

Perfect for busy parking lots, unfamiliar areas, or just everyday convenience!

## ✨ Features

- 🔵 **Bluetooth Device Selection**: Choose any Bluetooth device to track
- 📍 **Automatic Location Capture**: GPS location saved on device disconnect
- 🔄 **Background Monitoring**: Works even when the app is closed
- 📱 **Modern UI**: Clean Material Design 3 interface with Jetpack Compose
- 🗺️ **Maps Integration**: Open saved location directly in your maps app
- 🔔 **Smart Notifications**: Get notified when locations are saved
- ⚡ **Battery Optimized**: Efficient background service with minimal battery impact

## 🏗️ Project Structure

```
app/src/main/java/com/example/wheresmycar/
├── 📂 data/
│   ├── Models.kt                    # Data classes (ParkingLocation, BluetoothDeviceInfo, AppSettings)
│   └── Repository.kt                # Data persistence with DataStore
├── 📂 bluetooth/
│   └── BluetoothManager.kt          # Bluetooth device discovery and connection monitoring
├── 📂 location/
│   └── LocationManager.kt           # GPS location services with Google Play Services
├── 📂 service/
│   └── ParkingTrackingService.kt    # Foreground service for background monitoring
├── 📂 ui/
│   ├── MainViewModel.kt             # ViewModel with app state management
│   ├── MainScreen.kt                # Main UI screen with Compose
│   └── DeviceSelectionDialog.kt     # Bluetooth device selection dialog
└── MainActivity.kt                  # App entry point
```

### Key Components

- **Repository**: Handles data persistence using Android DataStore
- **BluetoothManager**: Manages device discovery and connection state monitoring
- **LocationManager**: Wraps Google Play Services for GPS location
- **ParkingTrackingService**: Background service that monitors disconnections
- **MainViewModel**: Manages UI state and coordinates between components
- **Compose UI**: Modern declarative UI with Material Design 3

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Arctic Fox or newer
- **Android SDK** level 26 (Android 8.0) or higher
- **Google Play Services** on the target device
- **Physical Android device** (recommended for Bluetooth and location testing)

### Installation

1. **Clone or download** this repository
2. **Open** the project in Android Studio
3. **Sync** the project with Gradle files
4. **Connect** your Android device via USB (or use an emulator)
5. **Build and run** the app

```bash
./gradlew app:build
./gradlew app:installDebug
```

### First-Time Setup

1. **Grant permissions** when prompted:
   - Bluetooth permissions (for device discovery and monitoring)
   - Location permissions (for GPS access)
   - Background location permission (for continuous monitoring)

2. **Select your Bluetooth device**:
   - Tap "Start Tracking" or "Change Device"
   - Choose the device connected to your car
   - Common options: car stereo, OBD-II adapter, Bluetooth FM transmitter

3. **Start tracking** and test by connecting/disconnecting the device

## 🧪 Testing

### Running Tests

The project includes unit tests for core functionality and UI tests for user interactions.

#### Unit Tests
```bash
# Run all unit tests
./gradlew test

# Run unit tests for a specific module
./gradlew app:testDebugUnitTest

# Run tests with coverage report
./gradlew app:testDebugUnitTestCoverage
```

#### Instrumentation Tests
```bash
# Run UI and integration tests
./gradlew connectedAndroidTest

# Run specific instrumentation test class
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.wheresmycar.MainActivityTest
```

#### Manual Testing Scenarios

1. **Permission Flow**:
   - Install app on fresh device
   - Verify permission requests appear
   - Test permission denial/approval flows

2. **Device Selection**:
   - Open device selection dialog
   - Test Bluetooth scanning
   - Verify paired devices appear
   - Test device selection

3. **Location Tracking**:
   - Select a Bluetooth device
   - Enable tracking
   - Connect/disconnect the device
   - Verify location is saved and notification appears

4. **Background Operation**:
   - Enable tracking
   - Put app in background
   - Test device disconnection
   - Verify service continues working

5. **Location Display**:
   - Save a parking location
   - Verify location details are shown
   - Test "Open in Maps" functionality

### Test Data

For testing purposes, you can use:
- **Bluetooth headphones/speakers** as mock car devices
- **Android emulator** with location mocking
- **Test coordinates**: Use known locations for validation

## 📋 Requirements

### Android Permissions

The app requires these permissions for full functionality:

- `BLUETOOTH` & `BLUETOOTH_ADMIN` - Basic Bluetooth access
- `BLUETOOTH_CONNECT` & `BLUETOOTH_SCAN` - Android 12+ Bluetooth permissions
- `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION` - GPS location access
- `ACCESS_BACKGROUND_LOCATION` - Background location (Android 10+)
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_LOCATION` - Background monitoring

### Hardware Requirements

- **Bluetooth** radio (standard on all modern Android devices)
- **GPS/Location services** capability
- **Android 8.0+** (API level 26 or higher)

### Software Dependencies

- **Google Play Services** - For location services
- **Kotlin Coroutines** - For async operations
- **Jetpack Compose** - For modern UI
- **DataStore** - For data persistence

## 🔧 Configuration

### Build Variants

- **Debug**: Development build with logging enabled
- **Release**: Production build with optimizations

### Customization

You can customize the app behavior by modifying:

- **Notification settings** in `ParkingTrackingService.kt`
- **Location accuracy** in `LocationManager.kt`
- **UI theme** in `ui/theme/` directory
- **App permissions** in `AndroidManifest.xml`

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🐛 Troubleshooting

### Common Issues

**App doesn't detect device disconnection**:
- Ensure all Bluetooth permissions are granted
- Check that the device actually disconnects (not just pauses)
- Verify background app restrictions are disabled

**Location not accurate**:
- Ensure location permissions are granted
- Check that GPS is enabled on the device
- Test in an area with good GPS signal

**Background service stops**:
- Disable battery optimization for the app
- Check that background app refresh is enabled
- Verify foreground service notification is visible

**No devices found in scan**:
- Ensure Bluetooth is enabled
- Make target device discoverable
- Check that BLUETOOTH_SCAN permission is granted

### Support

If you encounter issues:
1. Check the troubleshooting section above
2. Look at Android Studio's Logcat for error messages
3. Test with different Bluetooth devices
4. Verify all permissions are properly granted

---

**Happy parking!** 🚗 Never lose your car again with automatic location tracking.