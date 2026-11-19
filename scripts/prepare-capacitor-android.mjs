import { existsSync, readFileSync, writeFileSync } from 'node:fs';

const gradlePath = 'android/app/build.gradle';
const manifestPath = 'android/app/src/main/AndroidManifest.xml';

if (!existsSync(gradlePath) || !existsSync(manifestPath)) {
  throw new Error('Capacitor Android project is missing. Run "npx cap add android" first.');
}

const versionCode = process.env.VERSION_CODE || '136';
const versionName = process.env.VERSION_NAME || '1.0.136';

let gradle = readFileSync(gradlePath, 'utf8');
if (!/versionCode\\s+\\d+/.test(gradle) || !/versionName\\s+["'][^"']+["']/.test(gradle)) {
  throw new Error('Could not safely locate Android versionCode/versionName in app/build.gradle');
}
gradle = gradle.replace(/^(\\s*)versionCode\\s+\\d+\\s*$/m, '$1versionCode ' + versionCode);
gradle = gradle.replace(/^(\\s*)versionName\\s+["'][^"']+["']\\s*$/m, '$1versionName "' + versionName + '"');

if (!gradle.includes('com.google.android.gms:play-services-ads')) {
  const dependencyBlock = gradle.match(/dependencies\\s*\\{/);
  if (dependencyBlock) {
    gradle = gradle.replace(dependencyBlock[0], dependencyBlock[0] + '\\n    implementation \'com.google.android.gms:play-services-ads:24.9.0\'');
  } else {
    gradle += '\\ndependencies {\\n    implementation \'com.google.android.gms:play-services-ads:24.9.0\'\\n}\\n';
  }
}
writeFileSync(gradlePath, gradle);

let manifest = readFileSync(manifestPath, 'utf8');
if (!manifest.includes('android.permission.INTERNET')) {
  manifest = manifest.replace(/<manifest[^>]*>/, (tag) => tag + '\\n    <uses-permission android:name="android.permission.INTERNET" />');
}
const appTag = manifest.match(/<application\\b[^>]*>/s);
if (!appTag) throw new Error('Could not locate Android application element');
let updatedAppTag = appTag[0];
if (!/android:allowBackup=/.test(updatedAppTag)) {
  updatedAppTag = updatedAppTag.replace(/>$/, ' android:allowBackup="true">');
}
manifest = manifest.replace(appTag[0], updatedAppTag);

if (!manifest.includes('com.google.android.gms.ads.APPLICATION_ID')) {
  manifest = manifest.replace(
    updatedAppTag,
    updatedAppTag + '\\n        <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="ca-app-pub-6392100417582868~8391858779" />'
  );
}

const activityTag = manifest.match(/<activity\\b(?=[^>]*android:name="[^"]*MainActivity")[^>]*>/s);
if (!activityTag) throw new Error('Could not locate Capacitor MainActivity in AndroidManifest.xml');
if (!/android:screenOrientation=/.test(activityTag[0])) {
  manifest = manifest.replace(activityTag[0], activityTag[0].replace(/>$/, ' android:screenOrientation="landscape">'));
}
writeFileSync(manifestPath, manifest);
console.log('Prepared Capacitor Android project: ' + versionName + ' (versionCode ' + versionCode + '), landscape, stable package ID, and preserved AdMob app ID.');
