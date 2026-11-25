# F-Droid & Open-Source Store Submission Guide 📱🏪

This guide details how **AnyPods Pro** (`me.anypods.app`) is set up for open-source showcase stores including **F-Droid**, **IzzyOnDroid**, and **Obtainium**.

---

## 🚀 1. IzzyOnDroid (Fastest F-Droid Showcase, ~24–48 Hours)

[IzzyOnDroid](https://apt.izzysoft.de/fdroid/) is the largest and most popular F-Droid third-party repository. Most users of **F-Droid**, **Neo Store**, and **Droid-ify** already have IzzyOnDroid enabled by default.

Because IzzyOnDroid pulls directly from GitHub Releases, your app can be listed almost immediately.

### Submission Steps:
1. Go to the [IzzyOnDroid Inclusion Request issue tracker](https://gitlab.com/IzzyOnDroid/repo/-/issues/new?issue[template]=inclusion-request).
2. Title the issue:
   ```text
   Inclusion Request: AnyPods Pro (me.anypods.app)
   ```
3. Paste the following pre-filled information:
   ```markdown
   ### Application Name
   AnyPods Pro

   ### Package Name
   me.anypods.app

   ### Repository URL
   https://github.com/SHAURYASANYAL3/anypods

   ### License
   GPL-3.0

   ### Release Tag Format
   v* (e.g., v1.3.1)

   ### Fastlane Metadata
   Yes, configured at `fastlane/metadata/android/en-US/` with 512x512 icon, full description, and 5 device screenshots.

   ### Summary
   Universal AirPods features, studio acoustics & precision radar on Android.
   ```
4. Click **Submit Issue**. Once approved, IzzyOnDroid's automated bot will index your releases and showcase AnyPods Pro across all F-Droid client apps.

---

## 🏛️ 2. Official F-Droid (f-droid.org)

To get AnyPods Pro listed in the official core **F-Droid** repository:

1. Fork the official [fdroiddata GitLab repository](https://gitlab.com/fdroid/fdroiddata).
2. Add the file `metadata/me.anypods.app.yml` from this repository into the `metadata/` directory of your `fdroiddata` fork.
3. Commit and open a Merge Request at [fdroiddata Merge Requests](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new).
4. F-Droid's automated builder will verify the build using `./gradlew assembleRelease` and merge it.

The recipe is already created and validated in this repository:
[`metadata/me.anypods.app.yml`](file:///D:/anypods/metadata/me.anypods.app.yml)

---

## ⚡ 3. Obtainium (Live 1-Click Install Right Now)

Users do not need to wait for store approvals. With [Obtainium](https://github.com/ImranR98/Obtainium):

1. Users tap the **1-Click Install via Obtainium** button in the README:
   ```
   https://api.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/SHAURYASANYAL3/anypods
   ```
2. Obtainium opens, grabs the release APK directly from GitHub, and automatically checks for background updates.

---

## 🌐 4. AlternativeTo & Community Showcases

Showcase AnyPods Pro on developer & alternative software discovery directories:
- **AlternativeTo**: Submit AnyPods Pro at [AlternativeTo.net](https://alternativeto.net/software/anypods-pro/) as an open-source alternative to *Apple AirPods Settings*, *Galaxy Wearable*, *Sony Headphones Connect*, *LibrePods*, and *CAPod*.
- **XDA Developers & Reddit**: Share in `r/androidapps`, `r/fossdroid`, and `r/airpods`.
