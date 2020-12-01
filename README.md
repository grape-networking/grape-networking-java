# grape-networking-java
Rewrite of grape-networking in java

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

# TODO:
- instrumented tests with a custom runner with a phone attached
- findbugs (static analysis)

# Setting up the self-hosted CI runner
- Attach a phone
- Install java8 jre and jdk
- Ensure its being using with `sudo update-alternatives --config java` and `sudo update-alternatives --config javac`
- Install android studio sdk
- Set $ANDROID_SDK_ROOT to the install location
