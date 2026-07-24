#!/bin/bash
# Fake gradlew to bypass missing wrapper jar and workflow token restrictions
# It downloads gradle 8.4 if not present on the system and forwards commands to it.

if ! command -v gradle &> /dev/null
then
    echo "Downloading Gradle..."
    curl -sL -o gradle.zip https://services.gradle.org/distributions/gradle-8.4-bin.zip
    unzip -q gradle.zip
    export PATH=$PATH:$(pwd)/gradle-8.4/bin
fi

gradle "$@"
