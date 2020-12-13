# grape-networking-java
[![codecov.io](https://codecov.io/gh/compscidr/grape-networking-java/graphs/badge.svg?token=UJRIIV9LF5)](https://codecov.io/gh/compscidr/grape-networking-java)
![App Test](https://github.com/compscidr/grape-networking-java/workflows/App%20Test/badge.svg)

A proxy/vpn app on Android. Rewrite of grape-networking in java.

# First setup of Android Studio
- install the checkstyle-idea plugin
- download: https://raw.githubusercontent.com/checkstyle/checkstyle/checkstyle-8.34/src/main/resources/google_checks.xml (or equilvalent version to match the checkstyle gradle file)
- set `File->Settings->Editor->Code Style->Java->Import Scheme->CheckStyle Configuration` to `utility/google_checks.xml`.

# What to do before committing
Github actions is configured to run a bunch of checks. If you want to
test these locally before pushing you can do so as follows:
```
./gradlew lint
./gradlew checkstyle
./gradlew test
```

# Setting up the self-hosted CI runner
- Install java8 jre and jdk
- Ensure its being using with `sudo update-alternatives --config java` and `sudo update-alternatives --config javac`
- Install android studio sdk on host os (verify that this actually has to be done)
- Set $ANDROID_SDK_ROOT to the install location (verify that this actually has to be done) (see `utilities/install-android-studio.sh`)
- Attach a phone, add user to plugdev, setup udev rules: https://developer.android.com/studio/run/device, authorize USB debugging
  - verify with `adb devices` should see a serial number followed by `device`

# TODO
- Setup dependency tree in github:
  - https://docs.github.com/en/free-pro-team@latest/github/visualizing-repository-data-with-graphs/about-the-dependency-graph#supported-package-ecosystems
  - https://github.com/isaacs/github/issues/1620
- Figure out why the instrumented tests are being auto-skipped on the Nexus 6P but running fine on the Moto G7 Power
- Test coverage for new code
- Integrate a crash detection tool like firebase
- Explore metrics pipeline for telemetry

# Similar projects
- https://github.com/LipiLee/ToyShark
- https://github.com/hexene/LocalVPN
- https://github.com/google/vpn-reverse-tether
- https://github.com/httptoolkit/httptoolkit-android
- https://github.com/zhengchun/LocalVPN-Kotlin
