#!/bin/bash
# adapted from https://gist.github.com/zhy0/66d4c5eb3bcfca54be2a0018c3058931#file-ubuntu-cli-install-android-sdk-sh-L18
sudo apt-get install -y openjdk-8-jdk-headless openjdk-8-jre-headless

wget https://dl.google.com/android/android-sdk_r24.4.1-linux.tgz
tar xf android-sdk*-linux.tgz

wget https://dl.google.com/android/repository/android-ndk-r12b-linux-x86_64.zip
unzip android-ndk*.zip

cd android-sdk-linux/tools
./android update sdk --no-ui

./android update sdk --all --no-ui --filter $(seq -s, 27)

# If you need additional packages for your app, check available packages with:
# ./android list sdk --all

echo 'export ANDROID_HOME=$HOME/android-sdk-linux' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools' >> ~/.bashrc

source ~/.bashrc

sudo dpkg --add-architecture i386
sudo apt-get update
sudo apt-get install -y libc6:i386 libstdc++6:i386 zlib1g:i386

mkdir ~/.gradle
echo 'org.gradle.daemon=true' >> ~/.gradle/gradle.properties
