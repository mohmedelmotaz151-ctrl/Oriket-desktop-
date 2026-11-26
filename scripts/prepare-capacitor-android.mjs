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
mainActivity = mainActivity.replace('        super.onCreate(savedInstanceState);', '        registerPlugin(TermuxBridgePlugin.class);\\n        super.onCreate(savedInstanceState);');
writeFileSync(javaDir + '/TermuxBridgePlugin.java', "package com.orkeit.desktop;\n\nimport android.app.PendingIntent;\nimport android.content.Intent;\nimport android.content.pm.PackageManager;\nimport android.os.Build;\nimport com.getcapacitor.JSObject;\nimport com.getcapacitor.Plugin;\nimport com.getcapacitor.PluginCall;\nimport com.getcapacitor.PluginMethod;\nimport com.getcapacitor.PermissionState;\nimport com.getcapacitor.annotation.CapacitorPlugin;\nimport com.getcapacitor.annotation.Permission;\nimport com.getcapacitor.annotation.PermissionCallback;\nimport java.util.UUID;\nimport java.util.concurrent.ConcurrentHashMap;\n\n@CapacitorPlugin(name = \"TermuxBridge\", permissions = {\n    @Permission(alias = \"run_command\", strings = {\"com.termux.permission.RUN_COMMAND\"})\n})\npublic class TermuxBridgePlugin extends Plugin {\n    private static final ConcurrentHashMap<String, PluginCall> PENDING = new ConcurrentHashMap<>();\n\n    @PluginMethod\n    public void checkInstalled(PluginCall call) {\n        boolean installed;\n        try {\n            if (Build.VERSION.SDK_INT >= 33) {\n                getContext().getPackageManager().getPackageInfo(\"com.termux\", PackageManager.PackageInfoFlags.of(0));\n            } else {\n                getContext().getPackageManager().getPackageInfo(\"com.termux\", 0);\n            }\n            installed = true;\n        } catch (Exception error) {\n            installed = false;\n        }\n        JSObject result = new JSObject();\n        result.put(\"installed\", installed);\n        call.resolve(result);\n    }\n\n    @PluginMethod\n    public void openTermux(PluginCall call) {\n        Intent launch = getContext().getPackageManager().getLaunchIntentForPackage(\"com.termux\");\n        if (launch == null) {\n            call.reject(\"Termux is not installed. Install it from F-Droid first.\");\n            return;\n        }\n        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);\n        getContext().startActivity(launch);\n        call.resolve();\n    }\n\n    @PluginMethod\n    public void runCommand(PluginCall call) {\n        if (getPermissionState(\"run_command\") != PermissionState.GRANTED) {\n            requestPermissionForAlias(\"run_command\", call, \"permissionCallback\");\n            return;\n        }\n        executeCommand(call);\n    }\n\n    @PermissionCallback\n    private void permissionCallback(PluginCall call) {\n        if (getPermissionState(\"run_command\") != PermissionState.GRANTED) {\n            call.reject(\"Allow 'Run commands in Termux environment' for Oriket Desktop in Android app permissions.\");\n            return;\n        }\n        executeCommand(call);\n    }\n\n    private void executeCommand(PluginCall call) {\n        String command = call.getString(\"command\", \"\").trim();\n        String workingDirectory = call.getString(\"workingDirectory\", \"~/\");\n        if (command.isEmpty()) {\n            call.reject(\"Command cannot be empty.\");\n            return;\n        }\n        if (command.length() > 6000) {\n            call.reject(\"Command is too long (maximum 6000 characters).\");\n            return;\n        }\n\n        String id = UUID.randomUUID().toString();\n        call.setKeepAlive(true);\n        PENDING.put(id, call);\n\n        Intent intent = new Intent();\n        intent.setClassName(\"com.termux\", \"com.termux.app.RunCommandService\");\n        intent.setAction(\"com.termux.RUN_COMMAND\");\n        intent.putExtra(\"com.termux.RUN_COMMAND_PATH\", \"/data/data/com.termux/files/usr/bin/bash\");\n        intent.putExtra(\"com.termux.RUN_COMMAND_ARGUMENTS\", new String[]{\"-lc\", command});\n        intent.putExtra(\"com.termux.RUN_COMMAND_WORKDIR\", workingDirectory);\n        intent.putExtra(\"com.termux.RUN_COMMAND_BACKGROUND\", true);\n        intent.putExtra(\"com.termux.RUN_COMMAND_COMMAND_LABEL\", \"Oriket Desktop terminal\");\n        intent.putExtra(\"com.termux.RUN_COMMAND_COMMAND_DESCRIPTION\", \"Runs a command entered by the device owner.\");\n\n        Intent resultIntent = new Intent(getContext(), TermuxResultService.class);\n        resultIntent.putExtra(\"execution_id\", id);\n        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT;\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;\n        PendingIntent pendingIntent = PendingIntent.getService(getContext(), id.hashCode(), resultIntent, flags);\n        intent.putExtra(\"com.termux.RUN_COMMAND_PENDING_INTENT\", pendingIntent);\n\n        try {\n            getContext().startService(intent);\n        } catch (Exception error) {\n            PENDING.remove(id);\n            call.reject(\"Could not start Termux. Check allow-external-apps=true. \" + error.getMessage());\n        }\n    }\n\n    static void complete(String id, JSObject result) {\n        PluginCall call = PENDING.remove(id);\n        if (call != null) call.resolve(result);\n    }\n\n    static void fail(String id, String message) {\n        PluginCall call = PENDING.remove(id);\n        if (call != null) call.reject(message);\n    }\n}\n");\nwriteFileSync(javaDir + '/TermuxResultService.java', "package com.orkeit.desktop;\n\nimport android.app.IntentService;\nimport android.content.Intent;\nimport android.os.Bundle;\nimport android.os.Handler;\nimport android.os.Looper;\nimport com.getcapacitor.JSObject;\n\npublic class TermuxResultService extends IntentService {\n    public TermuxResultService() { super(\"TermuxResultService\"); }\n\n    @Override\n    protected void onHandleIntent(Intent intent) {\n        if (intent == null) return;\n        String id = intent.getStringExtra(\"execution_id\");\n        if (id == null) return;\n        Bundle bundle = intent.getBundleExtra(\"result\");\n        if (bundle == null) {\n            TermuxBridgePlugin.fail(id, \"Termux returned no result. Check allow-external-apps=true and update Termux.\");\n            return;\n        }\n        JSObject result = new JSObject();\n        result.put(\"stdout\", bundle.getString(\"stdout\", \"\"));\n        result.put(\"stderr\", bundle.getString(\"stderr\", \"\"));\n        result.put(\"exitCode\", bundle.getInt(\"exitCode\", -1));\n        result.put(\"errorCode\", bundle.getInt(\"err\", 0));\n        result.put(\"errorMessage\", bundle.getString(\"errmsg\", \"\"));\n        TermuxBridgePlugin.complete(id, result);\n    }\n}\n");\nwriteFileSync(javaDir + '/MainActivity.java', mainActivity);

console.log('Prepared Capacitor Android project: ' + versionName + ' (versionCode ' + versionCode + '), landscape, stable package ID, and preserved AdMob app ID.');
