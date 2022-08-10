
# ChargeRateController
A simple app to enable or disable fast charging mode on Samsung device when battery temperature reaches a certain point (threshold is currently at 36.5 C)

## ADB setup (one time setup only)

Run:

    adb shell pm grant com.sammy.chargerateautomator android.permission.WRITE_SECURE_SETTINGS
