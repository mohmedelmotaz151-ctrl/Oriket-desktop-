import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const gradlePath = 'android/app/build.gradle';
const manifestPath = 'android/app/src/main/AndroidManifest.xml';

if (!existsSync(gradlePath) || !existsSync(manifestPath)) {
  throw new Error('Capacitor Android project is missing. Run "npx cap add android" first.');
}

const versionCode = process.env.VERSION_CODE || '136';
const versionName = process.env.VERSION_NAME || '1.0.136';

let gradle = readFileSync(gradlePath, 'utf8');
if (!/versionCode\s+\d+/.test(gradle) || !/versionName\s+["'][^"']+["']/.test(gradle)) {
  throw new Error('Could not safely locate Android versionCode/versionName in app/build.gradle');
}
gradle = gradle.replace(/^(\s*)versionCode\s+\d+\s*$/m, '$1versionCode ' + versionCode);
gradle = gradle.replace(/^(\s*)versionName\s+["'][^"']+["']\s*$/m, '$1versionName "' + versionName + '"');

if (!gradle.includes('com.google.android.gms:play-services-ads')) {
  const dependencyBlock = gradle.match(/dependencies\s*\{/);
  if (dependencyBlock) {
    gradle = gradle.replace(dependencyBlock[0], dependencyBlock[0] + '\n    implementation \'com.google.android.gms:play-services-ads:24.9.0\'');
  } else {
    gradle += '\ndependencies {\n    implementation \'com.google.android.gms:play-services-ads:24.9.0\'\n}\n';
  }
}
writeFileSync(gradlePath, gradle);

let manifest = readFileSync(manifestPath, 'utf8');
if (!manifest.includes('android.permission.INTERNET')) {
  manifest = manifest.replace(/<manifest[^>]*>/, (tag) => tag + '\n    <uses-permission android:name="android.permission.INTERNET" />');
}
const appTag = manifest.match(/<application\b[^>]*>/s);
if (!appTag) throw new Error('Could not locate Android application element');
let updatedAppTag = appTag[0];
if (!/android:allowBackup=/.test(updatedAppTag)) {
  updatedAppTag = updatedAppTag.replace(/>$/, ' android:allowBackup="true">');
}
manifest = manifest.replace(appTag[0], updatedAppTag);

if (!manifest.includes('com.google.android.gms.ads.APPLICATION_ID')) {
  manifest = manifest.replace(
    updatedAppTag,
    updatedAppTag + '\n        <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="ca-app-pub-6392100417582868~8391858779" />'
  );
}

const activityTag = manifest.match(/<activity\b(?=[^>]*android:name="[^"]*MainActivity")[^>]*>/s);
if (!activityTag) throw new Error('Could not locate Capacitor MainActivity in AndroidManifest.xml');
if (!/android:screenOrientation=/.test(activityTag[0])) {
  manifest = manifest.replace(activityTag[0], activityTag[0].replace(/>$/, ' android:screenOrientation="landscape">'));
}
writeFileSync(manifestPath, manifest);
/* Restore the Windows Start logo as the Android launcher icon. */
const iconSource = 'public/icons/win11_start.png';
const resDir = 'android/app/src/main/res';
if (existsSync(iconSource)) {
  const densityDirs = ['mipmap-mdpi', 'mipmap-hdpi', 'mipmap-xhdpi', 'mipmap-xxhdpi', 'mipmap-xxxhdpi'];
  for (const dir of densityDirs) {
    mkdirSync(`${resDir}/${dir}`, { recursive: true });
    copyFileSync(iconSource, `${resDir}/${dir}/ic_launcher.png`);
    copyFileSync(iconSource, `${resDir}/${dir}/ic_launcher_round.png`);
  }
  mkdirSync(`${resDir}/drawable`, { recursive: true });
  copyFileSync(iconSource, `${resDir}/drawable/ic_windows_launcher.png`);
  const adaptiveDir = `${resDir}/mipmap-anydpi-v26`;
  mkdirSync(adaptiveDir, { recursive: true });
  const adaptiveIcon = `<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/ic_launcher_background" />\n    <foreground android:drawable="@drawable/ic_windows_launcher" />\n</adaptive-icon>\n`;
  writeFileSync(`${adaptiveDir}/ic_launcher.xml`, adaptiveIcon);
  writeFileSync(`${adaptiveDir}/ic_launcher_round.xml`, adaptiveIcon);
}



/* Install Google Mobile Ads App Open ad support without adding banner/interstitial ads. */
const javaDir = 'android/app/src/main/java/com/orkeit/desktop';
mkdirSync(javaDir, { recursive: true });
const mainActivity = String.raw`package com.orkeit.desktop;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;

public class MainActivity extends BridgeActivity {
    private static final String APP_OPEN_AD_UNIT_ID = "ca-app-pub-6392100417582868/6096867927";
    private AppOpenAd appOpenAd;
    private boolean adShowing = false;

    private void enableImmersiveFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveFullscreen();
        MobileAds.initialize(this, initializationStatus -> loadAppOpenAd());
    }

    @Override
    public void onResume() {
        super.onResume();
        enableImmersiveFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveFullscreen();
    }

    private void loadAppOpenAd() {
        AdRequest request = new AdRequest.Builder().build();
        AppOpenAd.load(this, APP_OPEN_AD_UNIT_ID, request,
            new AppOpenAd.AppOpenAdLoadCallback() {
                @Override
                public void onAdLoaded(AppOpenAd ad) {
                    appOpenAd = ad;
                    if (!isFinishing() && !isDestroyed() && !adShowing) {
                        adShowing = true;
                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                appOpenAd = null;
                                adShowing = false;
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(
                                    com.google.android.gms.ads.AdError adError) {
                                appOpenAd = null;
                                adShowing = false;
                            }
                        });
                        ad.show(MainActivity.this);
                    }
                }

                @Override
                public void onAdFailedToLoad(LoadAdError error) {
                    appOpenAd = null;
                }
            });
    }
}
`;
writeFileSync(javaDir + '/MainActivity.java', mainActivity);

/* Use the original Oriket upload keystore when GitHub Actions secrets are supplied.
   Never commit the keystore or passwords to the repository. */
const keystoreBase64 = process.env.ANDROID_KEYSTORE_BASE64;
if (keystoreBase64) {
  const keystorePassword = process.env.ANDROID_KEYSTORE_PASSWORD;
  const keyPassword = process.env.ANDROID_KEY_PASSWORD || keystorePassword;
  if (!keystorePassword || !keyPassword) {
    throw new Error('Missing ANDROID_KEYSTORE_PASSWORD or ANDROID_KEY_PASSWORD GitHub secret.');
  }
  const keystoreFile = 'orkeit-upload-keystore.p12';
  writeFileSync('android/app/' + keystoreFile, Buffer.from(keystoreBase64.replace(/\\s/g, ''), 'base64'));
  let signedGradle = readFileSync(gradlePath, 'utf8');
  if (!signedGradle.includes('ORKEIT_ORIGINAL_UPLOAD_SIGNING')) {
    signedGradle += '\\n\\n// ORKEIT_ORIGINAL_UPLOAD_SIGNING\\nandroid {\\n    signingConfigs {\\n        release {\\n            storeFile file("' + keystoreFile + '")\\n            storeType "PKCS12"\\n            storePassword System.getenv("ANDROID_KEYSTORE_PASSWORD")\\n            keyAlias System.getenv("ANDROID_KEY_ALIAS") ?: "orkeit-play-upload"\\n            keyPassword System.getenv("ANDROID_KEY_PASSWORD") ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")\\n        }\\n    }\\n    buildTypes {\\n        debug {\\n            signingConfig signingConfigs.release\\n        }\\n        release {\\n            signingConfig signingConfigs.release\\n        }\\n    }\\n}\\n';
    writeFileSync(gradlePath, signedGradle);
  }
  console.log('Original Oriket upload signing configured from environment secrets.');
} else {
  console.log('No upload keystore secret supplied; preserving the repository fallback signing behavior.');
}
console.log('Prepared Capacitor Android project: ' + versionName + ' (versionCode ' + versionCode + '), landscape, stable package ID, and preserved AdMob app ID.');
