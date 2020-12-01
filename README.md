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
```

# TODO:
- checkstyle (style enforcement)
- junit (unit, integration tests)
- jacoco (test coverage)
- findbugs (static analysis)
- codecov (historical coverage analytics)