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
- 🔄 **Enhanced Background Monitoring**: Resilient service that survives app kills and reboots
- 🛡️ **Battery Optimization Handling**: Automatic exemption requests and wake lock management
- 🔄 **Multi-Layer Service Recovery**: JobScheduler, AlarmManager, and boot receiver restart mechanisms
- 📱 **Modern UI**: Clean Material Design 3 interface with Jetpack Compose
- 🗺️ **Maps Integration**: Open saved location directly in your maps app
- 🔔 **Smart Notifications**: High-priority notifications with real-time status updates
- ⚡ **Robust Background Operation**: Survives doze mode, battery optimization, and long-term usage
- 🎨 **Professional Icon**: Clean parking sign design with location pin

## 🏗️ Project Structure

```
app/src/main/java/com/apbarrero/wheresmycar/
├── 📂 data/
│   ├── Models.kt                    # Data classes (ParkingLocation, BluetoothDeviceInfo, AppSettings)
│   └── Repository.kt                # Data persistence with DataStore
├── 📂 bluetooth/
│   └── BluetoothManager.kt          # Bluetooth device discovery and connection monitoring
├── 📂 location/
│   └── LocationManager.kt           # GPS location services with Google Play Services
├── 📂 service/
│   ├── ParkingTrackingService.kt    # Enhanced foreground service with wake locks
│   ├── BootReceiver.kt              # Restart service after device reboot
│   └── ServiceRestartJob.kt         # JobScheduler for automatic service recovery
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
- **ParkingTrackingService**: Enhanced foreground service with wake locks and resilience features
- **BootReceiver**: Automatically restarts tracking service after device reboot
- **ServiceRestartJob**: JobScheduler-based service recovery for long-term reliability
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
   - Battery optimization exemption (for reliable background operation)

2. **Configure battery optimization**:
   - When prompted, allow the app to ignore battery optimizations
   - This is crucial for long-term background operation
   - The app will automatically request this exemption

3. **Select your Bluetooth device**:
   - Tap "Start Tracking" or "Change Device"
   - Choose the device connected to your car
   - Common options: car stereo, OBD-II adapter, Bluetooth FM transmitter

4. **Start tracking** and test by connecting/disconnecting the device

5. **Keep the notification visible**:
   - Don't swipe away the "Where's My Car - Active" notification
   - This ensures the service stays running in the background

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
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.apbarrero.wheresmycar.MainActivityTest
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

**Bluetooth Permissions:**
- `BLUETOOTH` & `BLUETOOTH_ADMIN` - Basic Bluetooth access
- `BLUETOOTH_CONNECT` & `BLUETOOTH_SCAN` - Android 12+ Bluetooth permissions

**Location Permissions:**
- `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION` - GPS location access
- `ACCESS_BACKGROUND_LOCATION` - Background location (Android 10+)

**Service & Background Permissions:**
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_LOCATION` - Background monitoring
- `WAKE_LOCK` - Keep CPU awake for Bluetooth monitoring
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - Request battery optimization exemption
- `SCHEDULE_EXACT_ALARM` - Precise service restart scheduling
- `RECEIVE_BOOT_COMPLETED` - Restart service after device reboot

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
- Confirm battery optimization is disabled for the app

**Location not accurate**:
- Ensure location permissions are granted
- Check that GPS is enabled on the device
- Test in an area with good GPS signal

**Background service stops after a day**:
- ⚡ **Most Important**: Disable battery optimization for the app
- Keep the "Where's My Car - Active" notification visible
- Add the app to your device's "Auto-start" apps list (varies by manufacturer)
- Check that background app refresh is enabled
- Ensure wake lock permission is granted
- Verify the app isn't being killed by aggressive battery managers

**Service doesn't restart after reboot**:
- Ensure RECEIVE_BOOT_COMPLETED permission is granted
- Check that the app isn't disabled in startup app managers
- Verify the BootReceiver is enabled in app settings

**No devices found in scan**:
- Ensure Bluetooth is enabled
- Make target device discoverable
- Check that BLUETOOTH_SCAN permission is granted

### Advanced Troubleshooting

**For manufacturer-specific battery optimization**:
- **Samsung**: Settings > Apps > Special access > Optimize battery usage > All apps > Where's My Car > Don't optimize
- **Huawei**: Settings > Apps > Advanced > Ignore battery optimizations > Where's My Car
- **OnePlus**: Settings > Battery > Battery optimization > All apps > Where's My Car > Don't optimize
- **Xiaomi**: Settings > Apps > Manage apps > Where's My Car > Battery saver > No restrictions

**If service still gets killed**:
- Check Android's "Developer options" > "Don't keep activities"
- Review "Background app limits" in developer settings
- Test with different Bluetooth devices
- Monitor Android's Logcat for service termination messages

### Support

If you encounter issues:
1. Check the troubleshooting section above
2. Look at Android Studio's Logcat for error messages
3. Test with different Bluetooth devices
4. Verify all permissions are properly granted

---

**Happy parking!** 🚗 Never lose your car again with automatic location tracking.