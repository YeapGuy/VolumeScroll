<h1>
  <img src="fastlane/metadata/android/en-US/images/icon.png" alt="VolumeScroll icon" width="36" height="36" style="vertical-align: middle; border-radius: 8px; margin-right: 10px;" />
  <span style="vertical-align: middle;">VolumeScroll</span>
</h1>

**A small Android app that lets you scroll almost anywhere using your volume buttons.**

## Why?

I built this because it's more comfortable for me to read through long articles or essays "page-by-page". It's more like reading a book 😃 Plus, scrolling like this is way more comfortable when you only hold your phone in one hand, e.g. in public transport.

## Features

- ability to select apps in which volume-scrolling will be enabled
- customizable scroll amount
- invert direction option
- dual-volume-button shortcut toggle
- Quick Settings tile
- aesthetic minimalist Material You UI

## Screenshots

<p align="center">
  <a href="fastlane\metadata\android\en-US\images\phoneScreenshots\1.png">
    <img src="fastlane\metadata\android\en-US\images\phoneScreenshots\1.png" width="220"/>
  </a>
  <a href="fastlane\metadata\android\en-US\images\phoneScreenshots\2.png">
    <img src="fastlane\metadata\android\en-US\images\phoneScreenshots\2.png" width="220"/>
  </a>
</p>

## Why Accessibility?

Android does not provide a normal app API for global scrolling in other apps.
Using an Accessibility Service is the only way to do this.

The app doesn't do anything else with the Accesibility permission. The app doesn't need network permission and you can inspect the code yourself.

## Build (Developer)

### Requirements

- Android Studio (latest stable recommended)
- Android SDK 36
- JDK 11+

### Build debug APK

```bash
./gradlew :app:assembleDebug
```

## Contributing

Issues and PRs are welcome.
If you report a bug, please include:

- Android version
- device model
- exact repro steps


## License

[MIT](https://choosealicense.com/licenses/mit/)