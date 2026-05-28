# Wheresmycar — Project Instructions

## Testing Philosophy: TDD

Follow Test-Driven Development for all new features and non-trivial bug fixes:

1. Write a failing test that describes the desired behaviour.
2. Write the minimum production code to make it pass.
3. Refactor, keeping tests green.

### What to unit-test (JVM, fast, `app/src/test/`)

- `Repository` — DataStore reads/writes (use `TestScope` + `datastore-testing` artifact)
- `MainViewModel` — state transitions, error paths
- `LocationManager` — the age/accuracy validation logic is pure and must be covered
- Any new business-logic class extracted from `ParkingTrackingService`
- Data model transformations

### What NOT to unit-test with mocks

Don't mock the Android framework (`Context`, `BluetoothManager`, `LocationManager` system class) just to reach the code under test. If a class is only testable with heavy mocking of Android internals, it's a sign the logic needs to be extracted into a plain Kotlin class first.

### Instrumented tests (`app/src/androidTest/`)

Reserve for flows that genuinely require a device: Compose UI interactions, permission grant flows, foreground service lifecycle. These are slow — don't TDD with them.

### Architecture rule that enables testing

Business logic must live in plain Kotlin classes, not inside `Service`, `BroadcastReceiver`, or `Activity`. Android lifecycle classes should be thin orchestration shells.

## Dependencies to add when writing tests

```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:<version>")
testImplementation("androidx.datastore:datastore-preferences-core:<version>") // for TestScope DataStore
testImplementation("io.mockk:mockk:<version>") // preferred over Mockito for Kotlin
```

## Code Quality

- No test, no merge for new logic classes.
- Prefer `Result<T>` return types over exceptions for domain operations.
- All `catch (e: Exception)` blocks must log at minimum — silent swallowing is a bug.
