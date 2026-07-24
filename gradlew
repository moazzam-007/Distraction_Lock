#!/bin/bash
# Force download of Gradle 8.4 since system Gradle is 9.x which breaks AGP 8.2.2
echo "Downloading Gradle 8.4..."
curl -sL -o gradle.zip https://services.gradle.org/distributions/gradle-8.4-bin.zip
unzip -qo gradle.zip
./gradle-8.4/bin/gradle "$@"
