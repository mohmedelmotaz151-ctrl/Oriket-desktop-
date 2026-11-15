// Desktop settings and notification center are available from the taskbar.
package com.orkeit.desktop

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Environment
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.content.Context
import android.content.ContentValues
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.media.MediaPlayer
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.widget.VideoView
import android.text.InputType
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError

class MainActivity : Activity() {
 private val navy=Color.rgb(18,32,54)
 private val blue=Color.rgb(0,103,192)
 private val pale=Color.rgb(242,246,252)
 private val border=Color.rgb(218,226,238)
 private lateinit var root:LinearLayout
 private lateinit var content:FrameLayout
 private lateinit var desktopArea:FrameLayout
 private lateinit var taskbar:LinearLayout
 private var desktopInitialized=false
 private var windowCounter=0
 private var startupAppOpenAd:AppOpenAd?=null
 private var startupAdLoadInProgress=false
 private var startupAdHandled=false
 private var startupSplashVisible=false
 private var startupAdAttempted=false
 private var bootSequenceShown=false
 private var bootSequenceStartMs=0L
 private var startupTimeoutRunnable:Runnable?=null
 private var startupAdvanceRunnable:Runnable?=null
 private val appOpenAdUnitId="ca-app-pub-6392100417582868/6096867927" // Oriket App Open ad unit; shown at startup only
 private val openWindows=mutableListOf<WindowEntry>()
 private var web:WebView?=null
 private data class WindowEntry(val id:Int,val title:String,val frame:View,val taskButton:Button)
 private var pendingInstallUri:Uri?=null
 private var activeDownloadId=-1L
 private var updateReceiverRegistered=false
 private val uiHandler=Handler(Looper.getMainLooper())
 private var clockLabel:TextView?=null
 private var clockRunnable:Runnable?=null
 private var fileManagerDir:java.io.File?=null
 private var musicPlayer:MediaPlayer?=null
 private var onMusicPermissionGranted:(()->Unit)?=null
 private var onAudioPickedCallback:((Uri)->Unit)?=null
 private var onExcelCsvPickedCallback:((Uri)->Unit)?=null
 private data class PhotoItem(val uri:Uri,val name:String,val date:Long)
 private var selectedPhoto:PhotoItem?=null
 private var editorBitmap:Bitmap?=null
 private var galleryContainer:LinearLayout?=null
 private var galleryGrid:GridLayout?=null
 private var galleryStatus:TextView?=null
 private var galleryPermissionRequested=false
 private data class DesktopNotice(val title:String,val message:String,val time:Long=System.currentTimeMillis())
 private val desktopNotices=mutableListOf<DesktopNotice>()
 private var notificationButton:TextView?=null
 private val updateReceiver=object:BroadcastReceiver() {
  override fun onReceive(context:Context?,intent:Intent?) {
   if(intent?.action!=DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
   val id=intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1L)
   if(id!=activeDownloadId) return
   val manager=getSystemService(DOWNLOAD_SERVICE) as DownloadManager
   val query=DownloadManager.Query().setFilterById(id)
   manager.query(query).use { cursor ->
    if(cursor.moveToFirst()) {
     val status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
     if(status==DownloadManager.STATUS_SUCCESSFUL) {
      val uri=manager.getUriForDownloadedFile(id)
      if(uri!=null) {
       AlertDialog.Builder(this@MainActivity).setTitle("التحديث جاهز")
        .setMessage("تم تنزيل تحديث Oriket Desktop. اضغط تثبيت لإكمال التحديث.")
        .setPositiveButton("تثبيت") { _,_-> installDownloadedApk(uri) }
        .setNegativeButton("لاحقًا",null).show()
      } else Toast.makeText(this@MainActivity,"تعذر الوصول إلى ملف التحديث",Toast.LENGTH_LONG).show()
     } else Toast.makeText(this@MainActivity,"فشل تنزيل التحديث. تحقق من اتصال الإنترنت ثم حاول مجددًا.",Toast.LENGTH_LONG).show()
    }
   }
  }
 }

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=navy
  window.navigationBarColor=navy
  window.decorView.layoutDirection=View.LAYOUT_DIRECTION_LTR
  window.decorView.systemUiVisibility=(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
   or View.SYSTEM_UI_FLAG_FULLSCREEN
   or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
   or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
   or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
   or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
  window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  try {
   MobileAds.initialize(this) {
    runOnUiThread { if(!isFinishing && !isDestroyed) loadStartupAppOpenAd() }
   }
  } catch(e:Exception) {
   android.util.Log.w("OriketAdMob","MobileAds initialization skipped: ${e.message}")
  }
  showBootSequence()
  try {
   if(Build.VERSION.SDK_INT>=33) registerReceiver(updateReceiver,IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),RECEIVER_EXPORTED)
   else registerReceiver(updateReceiver,IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
   updateReceiverRegistered=true
  } catch(_:Exception) {}
 }

 override fun onDestroy() {
  if(updateReceiverRegistered) try { unregisterReceiver(updateReceiver) } catch(_:Exception) {}
  startupTimeoutRunnable?.let { uiHandler.removeCallbacks(it) }
  startupAdvanceRunnable?.let { uiHandler.removeCallbacks(it) }
  clockRunnable?.let { uiHandler.removeCallbacks(it) }
  try { musicPlayer?.stop(); musicPlayer?.release() } catch(_:Exception) {}; musicPlayer=null
  openWindows.mapNotNull { it.frame as? ViewGroup }.forEach { group ->
   for(i in 0 until group.childCount) (group.getChildAt(i) as? WebView)?.destroy()
  }
  super.onDestroy()
 }


 private fun playWindowsInspiredStartupTone() {
  // نغمة قصيرة مولّدة من نغمات النظام، مستوحاة من أجواء بدء تشغيل Windows.
  try {
   val tone=android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC,65)
   val notes=listOf(
    0L to android.media.ToneGenerator.TONE_PROP_ACK,
    170L to android.media.ToneGenerator.TONE_PROP_BEEP,
    360L to android.media.ToneGenerator.TONE_PROP_ACK
   )
   notes.forEach { (delay,kind) ->
    uiHandler.postDelayed({ try { tone.startTone(kind,115) } catch(_:Exception) {} },delay)
   }
   uiHandler.postDelayed({ try { tone.release() } catch(_:Exception) {} },650L)
  } catch(_:Exception) {}
 }

 private fun playDesktopSound(kind:String) {
  try {
   val generator=android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC,70)
   val notes=when(kind) {
    "boot" -> listOf(0L to android.media.ToneGenerator.TONE_PROP_ACK,180L to android.media.ToneGenerator.TONE_PROP_BEEP,360L to android.media.ToneGenerator.TONE_PROP_ACK)
    "start" -> listOf(0L to android.media.ToneGenerator.TONE_PROP_BEEP,110L to android.media.ToneGenerator.TONE_PROP_ACK)
    "sleep" -> listOf(0L to android.media.ToneGenerator.TONE_PROP_BEEP,200L to android.media.ToneGenerator.TONE_PROP_BEEP,400L to android.media.ToneGenerator.TONE_PROP_ACK)
    "shutdown" -> listOf(0L to android.media.ToneGenerator.TONE_PROP_ACK,180L to android.media.ToneGenerator.TONE_PROP_BEEP,360L to android.media.ToneGenerator.TONE_PROP_BEEP)
    else -> listOf(0L to android.media.ToneGenerator.TONE_PROP_BEEP,150L to android.media.ToneGenerator.TONE_PROP_ACK,300L to android.media.ToneGenerator.TONE_PROP_BEEP)
   }
   notes.forEach { (delay,tone) -> uiHandler.postDelayed({ try { generator.startTone(tone,130) } catch(_:Exception) {} },delay) }
   uiHandler.postDelayed({ try { generator.release() } catch(_:Exception) {} },850L)
  } catch(_:Exception) {}
 }

 private fun loadStartupAppOpenAd() {
  if(startupAdAttempted || isFinishing || isDestroyed) return
  startupAdAttempted=true
  startupAdLoadInProgress=true
  try {
   AppOpenAd.load(
    this,
    appOpenAdUnitId,
    AdRequest.Builder().build(),
    object:AppOpenAd.AppOpenAdLoadCallback() {
     override fun onAdLoaded(ad:AppOpenAd) {
      startupAdLoadInProgress=false
      startupAppOpenAd=ad
      android.util.Log.i("OriketAdMob","App Open ad loaded")
      if(startupSplashVisible && !startupAdHandled && !isFinishing && !isDestroyed) showStartupAppOpenAd()
     }
     override fun onAdFailedToLoad(error:LoadAdError) {
      startupAdLoadInProgress=false
      startupAppOpenAd=null
      val msg="App Open skipped: code=${error.code}, domain=${error.domain}, message=${error.message}"
      if(error.code==AdRequest.ERROR_CODE_NO_FILL) android.util.Log.i("OriketAdMob",msg)
      else android.util.Log.w("OriketAdMob",msg)
      // Ads are optional: never block startup or log expected No-Fill inventory responses as fatal errors.
      if(startupSplashVisible && !startupAdHandled && !isFinishing && !isDestroyed) {
       scheduleFinishStartupSplashAfterMinBoot()
      }
     }
    }
   )
  } catch(e:Exception) {
   startupAdLoadInProgress=false
   startupAppOpenAd=null
   android.util.Log.w("OriketAdMob","App Open load exception: ${e.message}")
   if(startupSplashVisible && !startupAdHandled && !isFinishing && !isDestroyed) {
    scheduleFinishStartupSplashAfterMinBoot()
   }
  }
 }

 private fun showStartupAppOpenAd() {
  val ad=startupAppOpenAd ?: return
  if(startupAdHandled || !startupSplashVisible || isFinishing || isDestroyed) return
  startupAdHandled=true
  startupAppOpenAd=null
  startupTimeoutRunnable?.let { uiHandler.removeCallbacks(it) }
  startupTimeoutRunnable=null
  ad.fullScreenContentCallback=object:FullScreenContentCallback() {
   override fun onAdDismissedFullScreenContent() {
    android.util.Log.i("OriketAdMob","App Open ad dismissed")
    finishStartupSplash()
   }
   override fun onAdFailedToShowFullScreenContent(error:AdError) {
    android.util.Log.w("OriketAdMob","App Open failed to show: ${error.message}")
    finishStartupSplash()
   }
   override fun onAdShowedFullScreenContent() {
    android.util.Log.i("OriketAdMob","App Open ad shown")
   }
  }
  try { ad.show(this) } catch(e:Exception) {
   android.util.Log.w("OriketAdMob","App Open show exception: ${e.message}")
   finishStartupSplash()
  }
 }

 private fun scheduleFinishStartupSplashAfterMinBoot() {
  if(!startupSplashVisible || startupAdHandled || isFinishing || isDestroyed) return
  startupTimeoutRunnable?.let { uiHandler.removeCallbacks(it) }
  startupTimeoutRunnable=null
  startupAdvanceRunnable?.let { uiHandler.removeCallbacks(it) }
  val elapsed=android.os.SystemClock.elapsedRealtime()-bootSequenceStartMs
  val remaining=(1900L-elapsed).coerceAtLeast(0L)
  val runnable=Runnable { finishStartupSplash() }
  startupAdvanceRunnable=runnable
  uiHandler.postDelayed(runnable,remaining)
 }

 private fun finishStartupSplash() {
  if(!startupSplashVisible || isFinishing || isDestroyed) return
  startupSplashVisible=false
  startupTimeoutRunnable?.let { uiHandler.removeCallbacks(it) }
  startupTimeoutRunnable=null
  startupAdvanceRunnable?.let { uiHandler.removeCallbacks(it) }
  startupAdvanceRunnable=null
  uiHandler.post { if(!isFinishing && !isDestroyed) showLocalLoginScreen() }
 }

 private fun showBootSequence() {
  val isFirstBootSequence=!bootSequenceShown
  bootSequenceShown=true
  val boot=FrameLayout(this).apply {
   setBackgroundColor(Color.rgb(5,18,42))
   layoutDirection=View.LAYOUT_DIRECTION_LTR
  }
  val panel=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   gravity=Gravity.CENTER
   setPadding(28,24,28,24)
  }
  val logo=ImageView(this).apply {
   setImageResource(R.drawable.ic_windows_launcher)
   scaleType=ImageView.ScaleType.FIT_CENTER
   alpha=0f
   contentDescription="شعار Windows"
  }
  panel.addView(logo,LinearLayout.LayoutParams(132,132))
  val title=TextView(this).apply {
   text="Windows"
   textSize=26.4f
   setTextColor(Color.WHITE)
   gravity=Gravity.CENTER
   typeface=Typeface.create("Arial",Typeface.NORMAL)
   setPadding(0,18,0,4)
   alpha=0f
  }
  panel.addView(title,LinearLayout.LayoutParams(-1,-2))
  val subtitle=TextView(this).apply {
   text="Oriket Desktop"
   textSize=13.2f
   setTextColor(Color.rgb(190,211,239))
   gravity=Gravity.CENTER
   alpha=0f
  }
  panel.addView(subtitle,LinearLayout.LayoutParams(-1,-2))
  val spinner=ProgressBar(this).apply {
   isIndeterminate=true
   indeterminateTintList=android.content.res.ColorStateList.valueOf(Color.WHITE)
  }
  val spinLp=LinearLayout.LayoutParams(38,38).apply { topMargin=36 }
  panel.addView(spinner,spinLp)
  boot.addView(panel,FrameLayout.LayoutParams(-1,-1))
  setContentView(boot)
  startupSplashVisible=isFirstBootSequence
  bootSequenceStartMs=android.os.SystemClock.elapsedRealtime()
  logo.animate().alpha(1f).setDuration(650).start()
  title.animate().alpha(1f).setStartDelay(280).setDuration(650).start()
  subtitle.animate().alpha(1f).setStartDelay(450).setDuration(650).start()
  playDesktopSound("boot")
  // Give the App Open request time to finish before leaving the startup screen.
  // If the ad loads during this window, its callback displays it immediately.
  if(isFirstBootSequence) {
   val timeoutTask=Runnable {
    if(!isFinishing && !isDestroyed && startupSplashVisible && !startupAdHandled) {
     if(startupAppOpenAd!=null) showStartupAppOpenAd()
     else {
      android.util.Log.w("OriketAdMob","Startup ad was not ready within 12 seconds; continuing without an ad")
      finishStartupSplash()
     }
    }
   }
   startupTimeoutRunnable=timeoutTask
   uiHandler.postDelayed(timeoutTask,12000L)
  } else {
   uiHandler.postDelayed({ if(!isFinishing && !isDestroyed) showLocalLoginScreen() },1900L)
  }
 }

 private fun passwordDigest(password:String,salt:String):String {
  val bytes=java.security.MessageDigest.getInstance("SHA-256")
   .digest((salt+":"+password).toByteArray(Charsets.UTF_8))
  return bytes.joinToString("") { "%02x".format(it) }
 }

 private fun showLocalLoginScreen() {
  val prefs=getSharedPreferences("orkeit_desktop",Context.MODE_PRIVATE)
  val hasPassword=!prefs.getString("login_hash",null).isNullOrBlank() &&
   !prefs.getString("login_salt",null).isNullOrBlank()
  val page=FrameLayout(this).apply {
   addView(ScenicLockWallpaper(this@MainActivity),FrameLayout.LayoutParams(-1,-1))
   layoutDirection=View.LAYOUT_DIRECTION_LTR
  }
  val card=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   gravity=Gravity.CENTER_HORIZONTAL
   setPadding(30,12,30,12)
   background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
   elevation=0f
  }
  val logo=ImageView(this).apply {
   setImageResource(R.drawable.ic_windows_launcher)
   scaleType=ImageView.ScaleType.FIT_CENTER
   contentDescription="Windows"
  }
  card.addView(logo,LinearLayout.LayoutParams(86,86))
  card.addView(TextView(this).apply {
   text=if(hasPassword) "SCANOR" else "إعداد تسجيل الدخول"
   textSize=26.4f; setTextColor(Color.WHITE); gravity=Gravity.CENTER
   typeface=Typeface.create("Arial",Typeface.NORMAL)
   setShadowLayer(5f,1f,2f,Color.BLACK)
   setPadding(0,10,0,8)
  },LinearLayout.LayoutParams(-1,-2))
  card.addView(TextView(this).apply {
   text=""
   textSize=12.32f; setTextColor(Color.WHITE); gravity=Gravity.CENTER
   setPadding(0,0,0,8)
  },LinearLayout.LayoutParams(-1,-2))
  val username=TextView(this).apply {
   text="◯"
   textSize=56.32f; setTextColor(Color.WHITE); gravity=Gravity.CENTER
   setShadowLayer(4f,1f,2f,Color.BLACK)
   setPadding(0,0,0,4)
  }
  card.addView(username,LinearLayout.LayoutParams(-1,-2))
  fun makePasswordField(hintText:String):EditText = EditText(this).apply {
   hint=if(hasPassword) "PIN" else hintText
   setSingleLine(true)
   textSize=14.08f
   setTextColor(Color.DKGRAY)
   setHintTextColor(Color.DKGRAY)
   inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.WHITE); cornerRadius=0f
    setStroke(1,Color.rgb(160,160,160))
   }
   setPadding(16,7,16,7)
  }
  val password=makePasswordField(if(hasPassword) "كلمة المرور" else "أنشئ كلمة مرور")
  card.addView(password,LinearLayout.LayoutParams(-1,54))
  val confirm=makePasswordField("أعد كتابة كلمة المرور")
  if(!hasPassword) {
   val confirmLp=LinearLayout.LayoutParams(-1,54).apply { topMargin=12 }
   card.addView(confirm,confirmLp)
  }
  val message=TextView(this).apply {
   text=""
   textSize=11.44f
   setTextColor(Color.rgb(255,190,190))
   gravity=Gravity.CENTER
   setPadding(0,10,0,0)
  }
  card.addView(message,LinearLayout.LayoutParams(-1,-2))
  val button=Button(this).apply {
   text=if(hasPassword) "تسجيل الدخول" else "حفظ كلمة المرور والمتابعة"
   isAllCaps=false
   textSize=13.2f
   setTextColor(Color.WHITE)
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.rgb(0,103,192)); cornerRadius=12f
   }
  }
  card.addView(button,LinearLayout.LayoutParams(-1,52).apply { topMargin=18 })
  card.addView(TextView(this).apply {
   text="كلمة المرور محفوظة على هذا الجهاز فقط"
   textSize=9.68f; setTextColor(Color.rgb(174,196,224)); gravity=Gravity.CENTER
   setPadding(0,18,0,0)
  },LinearLayout.LayoutParams(-1,-2))
  val width=(resources.displayMetrics.widthPixels*0.42f).toInt().coerceIn(300,470)
  page.addView(card,FrameLayout.LayoutParams(width,-2,Gravity.CENTER))
  setContentView(page)
  password.requestFocus()
  button.setOnClickListener {
   val value=password.text.toString()
   if(value.length<6) {
    message.text="استخدم كلمة مرور من 6 أحرف أو أرقام على الأقل."
    return@setOnClickListener
   }
   if(!hasPassword) {
    if(value!=confirm.text.toString()) {
     message.text="كلمتا المرور غير متطابقتين."
     return@setOnClickListener
    }
    val salt=java.util.UUID.randomUUID().toString()
    val hash=passwordDigest(value,salt)
    val saved=prefs.edit().putString("login_salt",salt).putString("login_hash",hash).commit()
    if(!saved) {
     message.text="تعذر حفظ كلمة المرور. حاول مرة أخرى."
     return@setOnClickListener
    }
   } else {
    val salt=prefs.getString("login_salt","").orEmpty()
    val expected=prefs.getString("login_hash","").orEmpty()
    val actual=passwordDigest(value,salt)
    if(!java.security.MessageDigest.isEqual(actual.toByteArray(Charsets.UTF_8),expected.toByteArray(Charsets.UTF_8))) {
     message.text="كلمة المرور غير صحيحة."
     password.text.clear()
     return@setOnClickListener
    }
   }
   button.isEnabled=false
   message.setTextColor(Color.rgb(190,235,205))
   message.text="جارٍ فتح سطح المكتب…"
   uiHandler.postDelayed({
    if(isFinishing) return@postDelayed
    initDesktop()
    showDesktop()
    checkForUpdates(false)
   },250L)
  }
 }

 private fun initDesktop() {
  if(desktopInitialized) return
  desktopInitialized=true
  root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setBackgroundColor(Color.BLACK)
   layoutDirection=View.LAYOUT_DIRECTION_LTR
  }
  desktopArea=FrameLayout(this).apply { layoutDirection=View.LAYOUT_DIRECTION_LTR }
  desktopArea.addView(WindowsWallpaper(this),FrameLayout.LayoutParams(-1,-1))
  desktopArea.isClickable=true
  desktopArea.setOnLongClickListener {
   android.widget.PopupMenu(this,desktopArea).apply {
    menu.add("عرض قائمة ابدأ").setOnMenuItemClickListener { showStartMenu(); true }
    menu.add("تغيير الخلفية").setOnMenuItemClickListener { showWallpaperPicker(); true }
    menu.add("تحديث سطح المكتب").setOnMenuItemClickListener { showDesktop(); true }
    menu.add("فتح المتصفح").setOnMenuItemClickListener { showBrowser(); true }
    menu.add("فتح مستكشف الملفات").setOnMenuItemClickListener { showMyComputer(); true }
   }.show()
   true
  }
  desktopArea.setOnGenericMotionListener { v,e ->
   if(e.action==MotionEvent.ACTION_BUTTON_PRESS && (e.buttonState and MotionEvent.BUTTON_SECONDARY)!=0) {
    android.widget.PopupMenu(this,v).apply {
     menu.add("عرض قائمة ابدأ").setOnMenuItemClickListener { showStartMenu(); true }
     menu.add("تحديث سطح المكتب").setOnMenuItemClickListener { showDesktop(); true }
     menu.add("فتح المتصفح").setOnMenuItemClickListener { showBrowser(); true }
    }.show()
    true
   } else false
  }
  root.addView(desktopArea,LinearLayout.LayoutParams(-1,0,1f))

  // Windows 11-inspired slim translucent light taskbar with centered app icons and right system tray.
  val d=resources.displayMetrics.density
  val dock=FrameLayout(this).apply {
   background=android.graphics.drawable.GradientDrawable(
    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Color.argb(240,242,246,253),Color.argb(245,232,239,248))
   ).apply { setStroke(1,Color.argb(95,186,200,220)) }
   elevation=18f*d
   layoutDirection=View.LAYOUT_DIRECTION_LTR
  }
  taskbar=LinearLayout(this).apply {
   gravity=Gravity.CENTER
   orientation=LinearLayout.HORIZONTAL
   setPadding((2*d).toInt(),0,(2*d).toInt(),0)
   background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
  }
  val startButton=Button(this).apply {
   text=""
   val startD=FluentIcons.createDrawable(this@MainActivity,FluentIcon.WINDOWS_START,(22*d).toInt())
   setCompoundDrawablesWithIntrinsicBounds(null,startD,null,null)
   isAllCaps=false
   minWidth=(38*d).toInt(); minimumWidth=(38*d).toInt(); minHeight=(38*d).toInt(); minimumHeight=(38*d).toInt()
   gravity=Gravity.CENTER
   setPadding(0,(6*d).toInt(),0,0)
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.TRANSPARENT); cornerRadius=6f*d
   }
   contentDescription="ابدأ"
   setOnClickListener { showStartMenu() }
  }
  val searchButton=Button(this).apply {
   text=""
   val searchD=FluentIcons.createDrawable(this@MainActivity,FluentIcon.SEARCH,(18*d).toInt())
   setCompoundDrawablesWithIntrinsicBounds(null,searchD,null,null)
   isAllCaps=false
   minWidth=(36*d).toInt(); minimumWidth=(36*d).toInt(); minHeight=(38*d).toInt(); minimumHeight=(38*d).toInt()
   gravity=Gravity.CENTER
   setPadding(0,(8*d).toInt(),0,0)
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.TRANSPARENT); cornerRadius=6f*d
   }
   contentDescription="بحث"
   setOnClickListener { showStartMenu() }
  }
  val centerGroup=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER
   addView(startButton,LinearLayout.LayoutParams((38*d).toInt(),(38*d).toInt()).apply{ setMargins((1.5f*d).toInt(),0,(1.5f*d).toInt(),0) })
   addView(searchButton,LinearLayout.LayoutParams((36*d).toInt(),(38*d).toInt()).apply{ setMargins((1.5f*d).toInt(),0,(1.5f*d).toInt(),0) })
   addView(taskbar,LinearLayout.LayoutParams(-2,(40*d).toInt()))
  }
  dock.addView(centerGroup,FrameLayout.LayoutParams(-2,(42*d).toInt(),Gravity.CENTER))
  val trayTextColor=Color.rgb(32,44,62)
  val tray=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding((4*d).toInt(),0,(8*d).toInt(),0)
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.TRANSPARENT); cornerRadius=6f*d
   }
   setOnClickListener { showQuickSettings() }
   contentDescription="منطقة النظام والإعدادات السريعة"
  }
  val networkLabel=TextView(this).apply {
   val netD=FluentIcons.createDrawable(this@MainActivity,FluentIcon.WIFI,(15*d).toInt())
   setCompoundDrawablesWithIntrinsicBounds(netD,null,null,null)
   gravity=Gravity.CENTER; setPadding((3*d).toInt(),0,(3*d).toInt(),0)
  }
  val batteryLabel=TextView(this).apply {
   text="100%"; textSize=9.2f; setTextColor(trayTextColor)
   val batD=FluentIcons.createDrawable(this@MainActivity,FluentIcon.BATTERY,(16*d).toInt())
   setCompoundDrawablesWithIntrinsicBounds(batD,null,null,null)
   compoundDrawablePadding=(2*d).toInt()
   gravity=Gravity.CENTER; setPadding((3*d).toInt(),0,(4*d).toInt(),0)
  }
  clockLabel=TextView(this).apply {
   gravity=Gravity.CENTER
   textSize=9.2f
   setTextColor(trayTextColor)
   setLineSpacing(0f,0.95f)
   setPadding((4*d).toInt(),1,(4*d).toInt(),1)
   setOnClickListener { showQuickSettings() }
   contentDescription="التاريخ والوقت والإعدادات السريعة"
  }
  tray.addView(networkLabel,LinearLayout.LayoutParams((22*d).toInt(),(36*d).toInt()))
  tray.addView(batteryLabel,LinearLayout.LayoutParams(-2,(36*d).toInt()))
  notificationButton=TextView(this).apply {
   val notifD=FluentIcons.createDrawable(this@MainActivity,FluentIcon.NOTIFICATIONS,(15*d).toInt())
   setCompoundDrawablesWithIntrinsicBounds(notifD,null,null,null)
   gravity=Gravity.CENTER
   setPadding((4*d).toInt(),0,(3*d).toInt(),0); contentDescription="مركز الإشعارات"
   setOnClickListener { showNotificationCenter() }
  }
  tray.addView(clockLabel,LinearLayout.LayoutParams(-2,(38*d).toInt()))
  tray.addView(notificationButton,LinearLayout.LayoutParams((26*d).toInt(),(36*d).toInt()))
  dock.addView(tray,FrameLayout.LayoutParams(-2,-1,Gravity.RIGHT or Gravity.CENTER_VERTICAL))
  root.addView(dock,LinearLayout.LayoutParams(-1,(44*d).toInt()))
  setContentView(root)

  fun refreshTray() {
   try {
    val batteryIntent=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level=batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1) ?: -1
    val scale=batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,-1) ?: -1
    val percent=if(level>=0 && scale>0) level*100/scale else -1
    val charging=batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS,-1) in listOf(android.os.BatteryManager.BATTERY_STATUS_CHARGING,android.os.BatteryManager.BATTERY_STATUS_FULL)
    batteryLabel.text=(if(charging) "⚡" else "")+(if(percent>=0) "$percent%" else "")
   } catch(_:Exception) { batteryLabel.text="" }
   clockLabel?.text=java.text.SimpleDateFormat("HH:mm\ndd/MM/yyyy",Locale.getDefault()).format(java.util.Date())
  }
  refreshTray()
  val clock=object:Runnable {
   override fun run() {
    refreshTray()
    uiHandler.postDelayed(this,30_000)
   }
  }
  clockRunnable=clock
  uiHandler.post(clock)
 }
 private fun shell(title:String) {
  initDesktop()
  web=null
  windowCounter++
  val id=windowCounter
  val td=resources.displayMetrics.density
  val outer=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=10f*td; setStroke(1,Color.rgb(198,210,226)) }
   elevation=14f*td
   clipToOutline=true
   layoutDirection=View.LAYOUT_DIRECTION_LTR
  }
  val titlebar=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding((10*td).toInt(),0,(4*td).toInt(),0)
   setBackgroundColor(Color.rgb(245,248,252))
  }
  val titleIcon=ImageView(this).apply {
   val dIcon=FluentIcons.createDrawable(this@MainActivity,FluentIcons.getIconForTitle(title),(16*td).toInt())
   setImageDrawable(dIcon)
  }
  titlebar.addView(titleIcon,LinearLayout.LayoutParams((16*td).toInt(),(16*td).toInt()).apply{rightMargin=(6*td).toInt()})
  titlebar.addView(TextView(this).apply { text=title; textSize=12f; setTextColor(navy); typeface=Typeface.create("Arial",Typeface.BOLD); gravity=Gravity.CENTER_VERTICAL; maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; includeFontPadding=true; setPadding((2*td).toInt(),0,(6*td).toInt(),0) },LinearLayout.LayoutParams(0,(36*td).toInt(),1f))
  val minimize=windowButton("—")
  val maximize=windowButton("▢")
  val close=windowButton("✕")
  titlebar.addView(minimize,LinearLayout.LayoutParams((36*td).toInt(),(34*td).toInt()))
  titlebar.addView(maximize,LinearLayout.LayoutParams((36*td).toInt(),(34*td).toInt()))
  titlebar.addView(close,LinearLayout.LayoutParams((38*td).toInt(),(34*td).toInt()))
  outer.addView(titlebar,LinearLayout.LayoutParams(-1,(38*td).toInt()))
  val body=FrameLayout(this).apply { setPadding((8*td).toInt(),(6*td).toInt(),(8*td).toInt(),(6*td).toInt()); setBackgroundColor(Color.rgb(250,251,254)) }
  outer.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  val lp=FrameLayout.LayoutParams((desktopArea.width*0.80f).toInt().coerceIn((280*td).toInt(),(desktopArea.width-(16*td).toInt()).coerceAtLeast((280*td).toInt())), (desktopArea.height*0.82f).toInt().coerceIn((220*td).toInt(),(desktopArea.height-(14*td).toInt()).coerceAtLeast((220*td).toInt())),Gravity.CENTER)
  var maximized=false
  var oldParams:FrameLayout.LayoutParams?=null
  val resizeGrip=TextView(this).apply {
   text="◢"; textSize=12f; gravity=Gravity.CENTER; setTextColor(Color.rgb(105,125,155))
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(225,238,243,250)); cornerRadius=5f*td }
   contentDescription="تغيير حجم النافذة"
  }
  body.addView(resizeGrip,FrameLayout.LayoutParams((22*td).toInt(),(22*td).toInt(),Gravity.BOTTOM or Gravity.RIGHT).apply{rightMargin=(2*td).toInt();bottomMargin=(2*td).toInt()})
  var resizeX=0f; var resizeY=0f; var resizeW=0; var resizeH=0
  resizeGrip.setOnTouchListener { _,e ->
   when(e.action) {
    MotionEvent.ACTION_DOWN -> {
     resizeX=e.rawX; resizeY=e.rawY; resizeW=outer.width; resizeH=outer.height
     outer.bringToFront(); true
    }
    MotionEvent.ACTION_MOVE -> {
     if(!maximized) {
      val p=outer.layoutParams as FrameLayout.LayoutParams
      p.width=(resizeW+e.rawX-resizeX).toInt().coerceIn(280,(desktopArea.width-outer.x).toInt().coerceAtLeast(280))
      p.height=(resizeH+e.rawY-resizeY).toInt().coerceIn(220,(desktopArea.height-outer.y).toInt().coerceAtLeast(220))
      outer.layoutParams=p
     }
     true
    }
    else -> true
   }
  }
  val entryButton=taskButton(title)
  val entry=WindowEntry(id,title,outer,entryButton)
  openWindows.add(entry)
  entryButton.setOnClickListener {
   if(outer.visibility==View.VISIBLE && outer.isSelected) {
    outer.visibility=View.GONE
    outer.isSelected=false
    entryButton.isSelected=false
    entryButton.background=createTaskbarBackground(false)
   } else {
    outer.visibility=View.VISIBLE
    outer.bringToFront()
    outer.isSelected=true
    openWindows.forEach {
     it.taskButton.isSelected=it.id==id
     it.taskButton.background=createTaskbarBackground(it.id==id)
    }
    entryButton.isSelected=true
    entryButton.background=createTaskbarBackground(true)
   }
  }
  taskbar.addView(entryButton)
  desktopArea.addView(outer,lp)
  outer.bringToFront()
  content=body
  var downX=0f; var downY=0f; var startX=0f; var startY=0f
  var lastTap=0L
  titlebar.setOnTouchListener { _,e ->
   when(e.action) {
    MotionEvent.ACTION_DOWN -> {
     downX=e.rawX; downY=e.rawY; startX=outer.x; startY=outer.y
     outer.bringToFront()
     openWindows.forEach {
      it.taskButton.isSelected=it.id==id
      it.taskButton.background=createTaskbarBackground(it.id==id)
     }
     outer.isSelected=true
     true
    }
    MotionEvent.ACTION_MOVE -> {
     if(!maximized) {
      val p=outer.layoutParams as FrameLayout.LayoutParams
      p.gravity=Gravity.TOP or Gravity.LEFT
      p.leftMargin=(startX+e.rawX-downX).toInt().coerceIn(-outer.width+110,(desktopArea.width-110).coerceAtLeast(0))
      p.topMargin=(startY+e.rawY-downY).toInt().coerceIn(0,(desktopArea.height-55).coerceAtLeast(0))
      outer.layoutParams=p
     }
     true
    }
    MotionEvent.ACTION_UP -> {
     val now=System.currentTimeMillis()
     if(now-lastTap<350L) {
      if(!maximized) {
       oldParams=outer.layoutParams as? FrameLayout.LayoutParams
       outer.layoutParams=FrameLayout.LayoutParams(-1,-1)
       maximized=true; maximize.text="❐"
      } else {
       outer.layoutParams=oldParams ?: lp
       maximized=false; maximize.text="□"
      }
      lastTap=0L
     } else {
      lastTap=now
      if(!maximized && desktopArea.width>0) {
       val p=outer.layoutParams as FrameLayout.LayoutParams
       if(e.rawX<24f || e.rawX>desktopArea.width-24f) {
        p.gravity=Gravity.TOP or Gravity.LEFT
        p.leftMargin=if(e.rawX<24f) 0 else desktopArea.width/2
        p.topMargin=0
        p.width=desktopArea.width/2
        p.height=desktopArea.height
        outer.layoutParams=p
       } else if(e.rawY<18f) {
        oldParams=outer.layoutParams as? FrameLayout.LayoutParams
        outer.layoutParams=FrameLayout.LayoutParams(-1,-1)
        maximized=true; maximize.text="❐"
       }
      }
     }
     true
    }
    else -> true
   }
  }
  minimize.setOnClickListener { outer.visibility=View.GONE; outer.isSelected=false; entryButton.isSelected=false }
  maximize.setOnClickListener {
   if(!maximized) {
    oldParams=outer.layoutParams as? FrameLayout.LayoutParams ?: lp
    outer.layoutParams=FrameLayout.LayoutParams(-1,-1)
    maximized=true
    maximize.text="❐"
   } else {
    outer.layoutParams=oldParams ?: lp
    maximized=false
    maximize.text="□"
   }
  }
  close.setOnClickListener {
   (body.findViewWithTag<View>("orkeit-video-view") as? VideoView)?.stopPlayback()
   desktopArea.removeView(outer)
   taskbar.removeView(entryButton)
   openWindows.removeAll { it.id==id }
   if(web!=null) web?.stopLoading()
   if(openWindows.isNotEmpty()) { openWindows.last().frame.visibility=View.VISIBLE; openWindows.last().frame.bringToFront() }
  }
  outer.setOnTouchListener { v,e -> if(e.action==MotionEvent.ACTION_DOWN) { v.bringToFront(); openWindows.forEach { it.taskButton.isSelected=it.id==id; it.taskButton.background=createTaskbarBackground(it.id==id) } }; false }
  outer.setOnGenericMotionListener { v,e ->
   if(e.action==MotionEvent.ACTION_BUTTON_PRESS && (e.buttonState and MotionEvent.BUTTON_SECONDARY)!=0) {
    android.widget.PopupMenu(this,v).apply {
     menu.add("تصغير").setOnMenuItemClickListener { outer.visibility=View.GONE; true }
     menu.add(if(maximized) "استعادة" else "تكبير").setOnMenuItemClickListener {
      if(!maximized) { oldParams=outer.layoutParams as? FrameLayout.LayoutParams ?: lp; outer.layoutParams=FrameLayout.LayoutParams(-1,-1); maximized=true; maximize.text="❐" }
      else { outer.layoutParams=oldParams ?: lp; maximized=false; maximize.text="□" }
      true
     }
     menu.add("إغلاق").setOnMenuItemClickListener { close.performClick(); true }
    }.show()
    true
   } else false
  }
 }
 private fun createTaskbarBackground(selected:Boolean):android.graphics.drawable.Drawable {
  val d=resources.displayMetrics.density
  val corner=6f*d
  val strokeColor=if(selected) Color.argb(45,100,130,170) else Color.TRANSPARENT
  val bgColor=if(selected) Color.argb(180,255,255,255) else Color.TRANSPARENT
  val base=android.graphics.drawable.GradientDrawable().apply {
   setColor(bgColor)
   cornerRadius=corner
   setStroke(1,strokeColor)
  }
  if(!selected) return base
  val pill=android.graphics.drawable.GradientDrawable().apply {
   setColor(Color.rgb(0,120,212))
   cornerRadius=2f*d
  }
  val layer=android.graphics.drawable.LayerDrawable(arrayOf(base,pill))
  val pillW=(14*d).toInt()
  val pillH=(2.5f*d).toInt()
  val padB=(2*d).toInt()
  val insetX=((38*d-pillW)/2).toInt().coerceAtLeast(0)
  layer.setLayerInset(1,insetX,(38*d-pillH-padB).toInt(),insetX,padB)
  return layer
 }
 private fun windowButton(label:String)=Button(this).apply {
  val d=resources.displayMetrics.density
  text=label; textSize=13.5f; isAllCaps=false; minWidth=(34*d).toInt(); minimumWidth=(34*d).toInt(); minHeight=(32*d).toInt(); minimumHeight=(32*d).toInt()
  gravity=Gravity.CENTER; setPadding(0,0,0,0); setTextColor(navy)
  typeface=Typeface.create("Arial",Typeface.NORMAL)
  val isClose = label=="×" || label=="✕"
  background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=6f*d }
  setOnTouchListener { v,e ->
   if(e.action==MotionEvent.ACTION_DOWN) {
    if(isClose) {
     v.background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(232,17,35)); cornerRadius=6f*d }
     setTextColor(Color.WHITE)
    } else {
     v.background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(226,232,240)); cornerRadius=6f*d }
    }
   }
   if(e.action==MotionEvent.ACTION_UP || e.action==MotionEvent.ACTION_CANCEL) {
    v.postDelayed({
     v.background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=6f*d }
     if(isClose) setTextColor(navy)
    },140)
   }
   false
  }
 }
 private fun windowIcon(title:String):String = ""
 private fun taskButton(title:String)=Button(this).apply {
  text=""
  val d=resources.displayMetrics.density
  val iconSizePx=(21*d).toInt()
  val icon=FluentIcons.getIconForTitle(title)
  val dIcon=FluentIcons.createDrawable(this@MainActivity,icon,iconSizePx)
  setCompoundDrawablesWithIntrinsicBounds(null,dIcon,null,null)
  isAllCaps=false; minWidth=(38*d).toInt(); minimumWidth=(38*d).toInt(); minHeight=(38*d).toInt(); minimumHeight=(38*d).toInt()
  gravity=Gravity.CENTER; setPadding(0,(7*d).toInt(),0,0)
  background=createTaskbarBackground(false)
  elevation=0f; contentDescription=title
 }
 private fun task(s:String,a:()->Unit)=Button(this).apply {
  text=s; textSize=11.44f; minHeight=48; minimumHeight=48; minWidth=0; isAllCaps=false; gravity=Gravity.CENTER
  maxLines=2; minLines=2; includeFontPadding=true; ellipsize=android.text.TextUtils.TruncateAt.END; setLineSpacing(1f,1.05f)
  typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(Color.rgb(24,39,62)); setPadding(9,5,9,5)
  if(Build.VERSION.SDK_INT>=26) setAutoSizeTextTypeUniformWithConfiguration(10,13,1,android.util.TypedValue.COMPLEX_UNIT_SP)
  background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(237,243,251)); cornerRadius=11f; setStroke(1,Color.rgb(216,226,240)) }
  elevation=1.5f; contentDescription=s; setOnClickListener{a()}
 }
 private fun action(s:String,a:()->Unit)=Button(this).apply {
  text=s; textSize=11.44f; minHeight=48; minimumHeight=48; minWidth=0; isAllCaps=false; gravity=Gravity.CENTER
  maxLines=2; minLines=2; includeFontPadding=true; ellipsize=android.text.TextUtils.TruncateAt.END; setLineSpacing(1f,1.05f)
  typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(Color.WHITE); setPadding(9,5,9,5)
  if(Build.VERSION.SDK_INT>=26) setAutoSizeTextTypeUniformWithConfiguration(10,13,1,android.util.TypedValue.COMPLEX_UNIT_SP)
  background=android.graphics.drawable.GradientDrawable().apply { setColor(blue); cornerRadius=11f; setStroke(1,Color.rgb(0,83,160)) }
  elevation=2f; contentDescription=s; setOnClickListener{a()}
 }
 private fun panel(v:View) {
  content.addView(v,FrameLayout.LayoutParams(-1,-1))
 }
 private inner class Icon3DView(context:Context, private val symbol:String, private val label:String):View(context) {
  init { setLayerType(View.LAYER_TYPE_SOFTWARE,null); contentDescription=label }
  override fun onDraw(canvas:Canvas) {
   super.onDraw(canvas)
   val bounds=RectF(0f,0f,width.toFloat(),height.toFloat())
   FluentIcons.drawIcon(context,canvas,FluentIcons.getIconForTitle(label),bounds)
  }
 }
 private fun showDesktop() {
  initDesktop()
  val prefs=getSharedPreferences("orkeit_desktop",Context.MODE_PRIVATE)
  val old=desktopArea.findViewWithTag<View>("desktop-shortcuts")
  if(old!=null) desktopArea.removeView(old)
  val density=resources.displayMetrics.density.coerceAtLeast(1f)
  val shortcuts=FrameLayout(this).apply {
   tag="desktop-shortcuts"
   setPadding((8*density).toInt(),(8*density).toInt(),(8*density).toInt(),(6*density).toInt())
   background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
  }
  val desktopHeightDp=((if(desktopArea.height>0) desktopArea.height else (resources.displayMetrics.heightPixels-(46*density).toInt()))/density)
  val rowsPerCol=((desktopHeightDp-16f)/80f).toInt().coerceIn(3,8)
  val grid=GridLayout(this).apply {
   orientation=GridLayout.VERTICAL
   rowCount=rowsPerCol
   useDefaultMargins=false
   alignmentMode=GridLayout.ALIGN_BOUNDS
   setPadding(0,0,0,0)
  }
  val allItems=listOf(
   Triple("🌐","متصفح الإنترنت",{showBrowser()}), Triple("🧯","زيارات الدفاع المدني",{showBrowser("https://orkeitstm.vercel.app","زيارات الدفاع المدني")}), Triple("🖥️","هذا الكمبيوتر",{showMyComputer()}),
   Triple("📄","مشغل PDF",{pickFile("application/pdf")}), Triple("🎬","مشغل الفيديو",{showVideoPlayer()}),
   Triple("🎨","الرسام",{showPaint()}), Triple("🖼️","الصور",{showPhotos()}), Triple("🎵","مشغل الموسيقى",{showMusicPlayer()}), Triple("▶️","YouTube",{showYouTube()}), Triple("🎧","YouTube Music",{showYouTubeMusic()}), Triple("📝","Word / معالج النصوص",{showWord()}),
   Triple("📊","Excel / الجداول",{showExcel()}), Triple("⚙️","لوحة التحكم",{showControlPanel()}),
   Triple("📋","إدارة المهام",{showTaskManager()}), Triple("⌨️","Command",{showCommand()}),
   Triple("🎮","ألعاب Windows",{showGames()}), Triple("🧮","الحاسبة",{showCalculator()}),
   Triple("🗒️","المفكرة",{showNotepad()}), Triple("🔄","تحديث التطبيق",{checkForUpdates(true)}),
   Triple("🗑️","سلة المحذوفات",{showRecycleBin()})
  )
  val hidden=prefs.getStringSet("hidden_shortcuts", emptySet())?.toMutableSet() ?: mutableSetOf()
  val savedOrder=prefs.getString("shortcut_order","").orEmpty().split("|").filter { it.isNotBlank() }
  val items=allItems.filter { it.second !in hidden }.sortedBy { item ->
   val index=savedOrder.indexOf(item.second)
   if(index<0) Int.MAX_VALUE else index
  }
  val tileW=(72*density).toInt()
  val tileH=(74*density).toInt()
  val iconSz=(36*density).toInt()
  items.forEach { item ->
   val displayName=prefs.getString("shortcut_label_${item.second}",item.second) ?: item.second
   val tile=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL or Gravity.TOP
    setPadding((3*density).toInt(),(4*density).toInt(),(3*density).toInt(),(2*density).toInt())
    background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
    isClickable=true; isFocusable=true
    setOnClickListener { item.third.invoke() }
    setOnLongClickListener {
     android.widget.PopupMenu(this@MainActivity,this).apply {
      menu.add("فتح").setOnMenuItemClickListener { item.third.invoke(); true }
      menu.add("إعادة التسمية").setOnMenuItemClickListener {
       val edit=EditText(this@MainActivity).apply { setText(displayName); selectAll() }
       AlertDialog.Builder(this@MainActivity).setTitle("إعادة تسمية الاختصار").setView(edit)
        .setPositiveButton("حفظ") { _,_ ->
         val value=edit.text.toString().trim()
         if(value.isNotEmpty()) {
          prefs.edit().putString("shortcut_label_${item.second}",value).apply()
          showDesktop()
         }
        }.setNegativeButton("إلغاء",null).show()
       true
      }
      fun moveShortcut(delta:Int) {
       val order=allItems.map { it.second }.toMutableList()
       val current=savedOrder.toMutableList().apply {
        allItems.map { it.second }.forEach { if(it !in this) add(it) }
       }
       val from=current.indexOf(item.second)
       val to=(from+delta).coerceIn(0,current.lastIndex)
       if(from>=0 && from!=to) {
        java.util.Collections.swap(current,from,to)
        prefs.edit().putString("shortcut_order",current.joinToString("|")).apply()
        showDesktop()
       }
      }
      menu.add("نقل لأعلى").setOnMenuItemClickListener { moveShortcut(-1); true }
      menu.add("نقل لأسفل").setOnMenuItemClickListener { moveShortcut(1); true }
      menu.add("عرض الخصائص").setOnMenuItemClickListener {
       AlertDialog.Builder(this@MainActivity).setTitle("خصائص الاختصار")
        .setMessage("الاسم: $displayName\nنوع العنصر: اختصار داخل Oriket Desktop\nالوظيفة: فتح التطبيق الداخلي المحدد.")
        .setPositiveButton("حسنًا",null).show()
       true
      }
      if(item.second!="سلة المحذوفات") {
       menu.add("حذف الاختصار").setOnMenuItemClickListener {
        val updated=prefs.getStringSet("hidden_shortcuts", emptySet())?.toMutableSet() ?: mutableSetOf()
        updated.add(item.second)
        prefs.edit().putStringSet("hidden_shortcuts",updated).apply()
        RecycleBinStorage.recordShortcutDeleted(this@MainActivity,item.second)
        showDesktop()
        Toast.makeText(this@MainActivity,"نُقل الاختصار إلى سلة المحذوفات",Toast.LENGTH_SHORT).show()
        true
       }
      }
     }.show()
     true
    }
    setOnHoverListener { v,e ->
     if(e.action==MotionEvent.ACTION_HOVER_ENTER) v.background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(65,255,255,255)); cornerRadius=6f*density }
     if(e.action==MotionEvent.ACTION_HOVER_EXIT) v.background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
     false
    }
   }
   tile.addView(Icon3DView(this,item.first,item.second),LinearLayout.LayoutParams(iconSz,iconSz))
   tile.addView(TextView(this).apply { text=displayName; textSize=9.2f; gravity=Gravity.CENTER_HORIZONTAL; setTextColor(Color.WHITE); typeface=Typeface.create("Arial",Typeface.BOLD); setShadowLayer(4f,0f,1.5f,Color.argb(215,0,18,45)); setPadding((2*density).toInt(),(3*density).toInt(),(2*density).toInt(),0); maxLines=2; includeFontPadding=false; ellipsize=android.text.TextUtils.TruncateAt.END; setLineSpacing(0f,1.02f) },LinearLayout.LayoutParams(-1,-2))
   grid.addView(tile,GridLayout.LayoutParams().apply { width=tileW; height=tileH; setMargins((3*density).toInt(),(2*density).toInt(),(3*density).toInt(),(2*density).toInt()) })
  }
  shortcuts.addView(grid,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.LEFT))
  val clock=TextView(this).apply {
   text="Oriket Desktop"; textSize=9.5f; setTextColor(Color.argb(225,255,255,255)); setShadowLayer(3f,0f,1f,Color.argb(180,0,20,50))
   gravity=Gravity.CENTER; setPadding((10*density).toInt(),(4*density).toInt(),(10*density).toInt(),(4*density).toInt())
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(65,15,40,80)); cornerRadius=12f*density }
  }
  shortcuts.addView(clock,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.RIGHT).apply { rightMargin=(8*density).toInt(); bottomMargin=(4*density).toInt() })
  desktopArea.addView(shortcuts,FrameLayout.LayoutParams(-1,-1))
  shortcuts.post {
   val hDp=(desktopArea.height.toFloat()/density)
   val targetRows=((hDp-16f)/80f).toInt().coerceIn(3,8)
   if(hDp>0f && grid.rowCount!=targetRows) {
    grid.rowCount=targetRows
    grid.requestLayout()
   }
  }
  shortcuts.bringToFront()
  openWindows.forEach { it.frame.bringToFront() }
  val browser=taskButton("متصفح الإنترنت").apply { setOnClickListener { showBrowser() } }
  val files=taskButton("هذا الكمبيوتر").apply { setOnClickListener { showMyComputer() } }
  val paint=taskButton("الرسام").apply { setOnClickListener { showPaint() } }
  val settings=taskButton("الإعدادات").apply { setOnClickListener { showControlPanel() } }
  val staticButtons=listOf(files,browser,paint,settings)
  staticButtons.forEach { b ->
   if((0 until taskbar.childCount).none { taskbar.getChildAt(it).contentDescription==b.contentDescription }) {
    taskbar.addView(b,LinearLayout.LayoutParams((38*density).toInt(),(38*density).toInt()).apply{ setMargins((1.5f*density).toInt(),0,(1.5f*density).toInt(),0) })
   }
  }
 }


 private fun hasPhotoPermission():Boolean {
  return if(Build.VERSION.SDK_INT>=34) {
   checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED ||
    checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")==PackageManager.PERMISSION_GRANTED
  } else if(Build.VERSION.SDK_INT>=33) checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED
  else Build.VERSION.SDK_INT<23 || checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED
 }
 private fun requestPhotoPermission() {
  val perms=when {
   Build.VERSION.SDK_INT>=34 -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES,"android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
   Build.VERSION.SDK_INT>=33 -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
   Build.VERSION.SDK_INT>=23 -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
   else -> emptyArray()
  }
  if(perms.isEmpty()) { refreshPhotoGallery(); return }
  requestPermissions(perms,705)
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==704) {
   val granted=if(Build.VERSION.SDK_INT>=33) checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO)==PackageManager.PERMISSION_GRANTED
   else checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED
   if(granted) onMusicPermissionGranted?.invoke()
   else Toast.makeText(this,"يرجى منح إذن الصوت أو استخدام «فتح ملف صوتي»",Toast.LENGTH_LONG).show()
  }
  if(requestCode==705) {
   if(hasPhotoPermission()) refreshPhotoGallery()
   else {
    galleryStatus?.text="لم يتم منح إذن عرض الصور. يمكنك الضغط على «إضافة صورة» لاختيار صورة يدويًا."
    galleryGrid?.removeAllViews()
   }
  }
 }
 private fun showPhotos() {
  shell("الصور")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(6,4,6,4) }
  val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  header.addView(TextView(this).apply { text="صور الجهاز";textSize=17.6f;typeface=Typeface.create("Arial",Typeface.BOLD);setTextColor(navy) },LinearLayout.LayoutParams(0,48,1f))
  header.addView(task("＋ إضافة صورة"){ pickPhotoToImport() },LinearLayout.LayoutParams(112,44))
  header.addView(task("↻ تحديث"){ if(hasPhotoPermission()) refreshPhotoGallery() else requestPhotoPermission() },LinearLayout.LayoutParams(96,44))
  page.addView(header)
  galleryStatus=TextView(this).apply { text="جارٍ تحميل الصور…";textSize=11.44f;setTextColor(navy);setPadding(5,5,5,8) }
  page.addView(galleryStatus)
  galleryContainer=page
  val scroll=ScrollView(this)
  galleryGrid=GridLayout(this).apply { columnCount=4;useDefaultMargins=false }
  scroll.addView(galleryGrid,FrameLayout.LayoutParams(-1,-2))
  page.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  panel(page)
  if(hasPhotoPermission()) refreshPhotoGallery() else requestPhotoPermission()
 }
 private fun refreshPhotoGallery() {
  val grid=galleryGrid ?: return
  grid.removeAllViews()
  val photos=mutableListOf<PhotoItem>()
  try {
   val collection=MediaStore.Images.Media.EXTERNAL_CONTENT_URI
   val projection=arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.DATE_ADDED)
   contentResolver.query(collection,projection,null,null,MediaStore.Images.Media.DATE_ADDED+" DESC")?.use { c ->
    val idCol=c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
    val nameCol=c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
    val dateCol=c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
    while(c.moveToNext()) {
     val id=c.getLong(idCol); val uri=Uri.withAppendedPath(collection,id.toString())
     photos.add(PhotoItem(uri,c.getString(nameCol) ?: "صورة",c.getLong(dateCol)))
    }
   }
  } catch(_:SecurityException) { galleryStatus?.text="يحتاج التطبيق إلى إذن الوصول إلى الصور."; return }
    catch(_:Exception) { galleryStatus?.text="تعذر تحميل الصور."; return }
  galleryStatus?.text="عدد الصور: ${photos.size} • اضغط على أي صورة لعرضها وتعديلها"
  if(photos.isEmpty()) {
   grid.addView(TextView(this).apply { text="لا توجد صور ظاهرة. اضغط «إضافة صورة» لاختيار صور من الجهاز.";textSize=14.08f;setTextColor(navy);gravity=Gravity.CENTER;setPadding(16,24,16,24) },GridLayout.LayoutParams().apply { width=-1;height=120;columnSpec=GridLayout.spec(0,4) })
  }
  photos.forEach { photo ->
   val tile=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(4,4,4,8);isClickable=true;setOnClickListener{openPhotoEditor(photo)} }
   val thumb=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription=photo.name;background=android.graphics.drawable.ColorDrawable(Color.rgb(230,235,243)) }
   try {
    if(Build.VERSION.SDK_INT>=29) thumb.setImageBitmap(contentResolver.loadThumbnail(photo.uri,android.util.Size(240,240),null))
    else contentResolver.openInputStream(photo.uri)?.use { thumb.setImageBitmap(BitmapFactory.decodeStream(it)) }
   } catch(_:Exception) {}
   tile.addView(thumb,LinearLayout.LayoutParams(-1,112))
   tile.addView(TextView(this).apply { text=photo.name;textSize=8.8f;setTextColor(navy);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER;setPadding(2,3,2,0) },LinearLayout.LayoutParams(-1,34))
   grid.addView(tile,GridLayout.LayoutParams().apply { width=0;height=152;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(3,3,3,3) })
  }
 }
 private fun pickPhotoToImport() {
  try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type="image/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false) },71) }
  catch(_:Exception) { Toast.makeText(this,"تعذر فتح معرض الصور",Toast.LENGTH_LONG).show() }
 }
 private fun saveImportedPhoto(uri:Uri) {
  try {
   val bmp=contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
   if(bmp==null) { Toast.makeText(this,"تعذر قراءة الصورة",Toast.LENGTH_SHORT).show();return }
   savePhotoBitmap(bmp,"orkeit-photo")
   bmp.recycle()
  } catch(_:Exception) { Toast.makeText(this,"تعذر استيراد الصورة",Toast.LENGTH_LONG).show() }
 }
 private fun savePhotoBitmap(bitmap:Bitmap,baseName:String) {
  try {
   val values=ContentValues().apply {
    put(MediaStore.Images.Media.DISPLAY_NAME,baseName+"-"+System.currentTimeMillis()+".jpg")
    put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
    if(Build.VERSION.SDK_INT>=29) put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/OriketDesktop")
   }
   val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
   if(uri==null) { Toast.makeText(this,"تعذر حفظ الصورة",Toast.LENGTH_LONG).show();return }
   val ok=contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG,94,it) } ?: false
   if(!ok) contentResolver.delete(uri,null,null)
   Toast.makeText(this,if(ok)"تم حفظ نسخة في صور الجهاز / OriketDesktop" else "تعذر حفظ الصورة",Toast.LENGTH_LONG).show()
   refreshPhotoGallery()
  } catch(_:Exception) { Toast.makeText(this,"تعذر حفظ الصورة",Toast.LENGTH_LONG).show() }
 }
 private fun openPhotoEditor(photo:PhotoItem) {
  val bitmap=try { contentResolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it) } } catch(_:Exception){null}
  if(bitmap==null) { Toast.makeText(this,"تعذر فتح الصورة",Toast.LENGTH_SHORT).show();return }
  selectedPhoto=photo
  editorBitmap?.recycle();editorBitmap=bitmap
  shell("عارض الصور")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text=photo.name;textSize=12.32f;setTextColor(navy);gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE },LinearLayout.LayoutParams(-1,32))
  val image=ImageView(this).apply { tag="orkeit-photo-preview";setImageBitmap(bitmap);scaleType=ImageView.ScaleType.FIT_CENTER;adjustViewBounds=true;setBackgroundColor(Color.rgb(24,31,43)) }
  page.addView(image,LinearLayout.LayoutParams(-1,0,1f))
  val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER }
  fun add(label:String,fn:()->Unit) { row.addView(task(label,fn),LinearLayout.LayoutParams(0,46,1f).apply { setMargins(2,2,2,2) }) }
  var zoom=1f
  add("− تصغير") { zoom=(zoom/1.25f).coerceAtLeast(0.5f);image.scaleType=ImageView.ScaleType.MATRIX;image.imageMatrix=Matrix().apply{setScale(zoom,zoom)} }
  add("＋ تكبير") { zoom=(zoom*1.25f).coerceAtMost(5f);image.scaleType=ImageView.ScaleType.MATRIX;image.imageMatrix=Matrix().apply{setScale(zoom,zoom)} }
  add("↻ تدوير") { val current=editorBitmap ?: return@add; val matrix=Matrix().apply{postRotate(90f)};val rotated=Bitmap.createBitmap(current,0,0,current.width,current.height,matrix,true);editorBitmap=rotated;if(current!==rotated)current.recycle();image.setImageBitmap(rotated);zoom=1f;image.scaleType=ImageView.ScaleType.FIT_CENTER }
  add("قص مربع") { val current=editorBitmap ?: return@add;val side=minOf(current.width,current.height);val x=(current.width-side)/2;val y=(current.height-side)/2;val cropped=Bitmap.createBitmap(current,x,y,side,side);editorBitmap=cropped;if(current!==cropped)current.recycle();image.setImageBitmap(cropped);zoom=1f;image.scaleType=ImageView.ScaleType.FIT_CENTER }
  add("حفظ نسخة") { editorBitmap?.let { savePhotoBitmap(it,"orkeit-edited") } }
  add("حذف") { confirmDeletePhoto(photo) }
  page.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;addView(row) },LinearLayout.LayoutParams(-1,52))
  val footer=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER }
  footer.addView(action("حفظ التعديلات كصورة جديدة"){editorBitmap?.let{savePhotoBitmap(it,"orkeit-edited")}},LinearLayout.LayoutParams(-1,44))
  page.addView(footer)
  panel(page)
 }
 private fun confirmDeletePhoto(photo:PhotoItem) {
  AlertDialog.Builder(this).setTitle("حذف الصورة؟").setMessage("سيطلب Android تأكيد حذف الصورة من الجهاز.")
   .setPositiveButton("حذف") { _,_ ->
    try {
     if(Build.VERSION.SDK_INT>=30) {
      val pending=MediaStore.createDeleteRequest(contentResolver,listOf(photo.uri))
      startIntentSenderForResult(pending.intentSender,67,null,0,0,0)
     } else {
      val deleted=contentResolver.delete(photo.uri,null,null)>0
      Toast.makeText(this,if(deleted)"تم حذف الصورة" else "لم يتم حذف الصورة",Toast.LENGTH_SHORT).show()
      refreshPhotoGallery()
     }
    } catch(_:Exception) { Toast.makeText(this,"تعذر حذف الصورة. قد تحتاج إلى تأكيد الحذف من تطبيق الصور.",Toast.LENGTH_LONG).show() }
   }.setNegativeButton("إلغاء",null).show()
 }

 private fun showRecycleBin() {
  shell("Recycle Bin")
  val d=resources.displayMetrics.density
  content.setPadding(0,0,0,0)
  content.setBackgroundColor(RecycleBinTheme.WINDOW_BG)
  val outerWindow=content.parent as? LinearLayout
  val windowTitleBar=outerWindow?.getChildAt(0) as? LinearLayout
  windowTitleBar?.setBackgroundColor(RecycleBinTheme.WINDOW_BG)

  var allEntries=RecycleBinStorage.listItems(this)
  val selectedIds=linkedSetOf<String>()
  var activeTab="Manage"
  var ribbonCollapsed=false
  var groupExpanded=true
  var largeIconsMode=true
  var searchQuery=""

  val page=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_LTR
   setBackgroundColor(RecycleBinTheme.WINDOW_BG)
  }

  lateinit var refreshAll:()->Unit
  lateinit var renderContent:()->Unit
  lateinit var renderRibbonBody:()->Unit
  lateinit var updateStatusBar:()->Unit

  fun visibleEntries():List<RecycledEntry> =
   if(searchQuery.isBlank()) allEntries
   else allEntries.filter {
    it.displayName.contains(searchQuery,true) || it.originalLocation.contains(searchQuery,true)
   }

  fun showPropertiesDialog(entry:RecycledEntry?=null) {
   if(entry==null) {
    val totalBytes=allEntries.sumOf { it.sizeBytes }
    val filesCount=allEntries.count { !it.isShortcut }
    val shortcutsCount=allEntries.count { it.isShortcut }
    val box=LinearLayout(this).apply {
     orientation=LinearLayout.VERTICAL
     setPadding((16*d).toInt(),(10*d).toInt(),(16*d).toInt(),(6*d).toInt())
    }
    box.addView(TextView(this).apply {
     text="الموقع: C:\\OriketWorkspace\\\$Recycle.Bin\nإجمالي العناصر: ${allEntries.size} ($filesCount ملفات، $shortcutsCount اختصارات)\nالمساحة المستخدمة: ${RecycleBinStorage.formatSize(totalBytes)}"
     textSize=11.5f; setTextColor(RecycleBinTheme.TEXT_DARK)
    })
    val confirmCheck=CheckBox(this).apply {
     text="عرض مربع حوار تأكيد الحذف"
     isChecked=RecycleBinStorage.isConfirmDeleteEnabled(this@MainActivity)
     setTextColor(RecycleBinTheme.TEXT_DARK)
     setPadding(0,(8*d).toInt(),0,0)
    }
    box.addView(confirmCheck)
    AlertDialog.Builder(this).setTitle("Recycle Bin Properties")
     .setView(box)
     .setPositiveButton("حفظ") { _,_ ->
      RecycleBinStorage.setConfirmDeleteEnabled(this@MainActivity,confirmCheck.isChecked)
     }
     .setNegativeButton("إغلاق",null).show()
   } else {
    val dateFmt=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault())
    val kind=if(entry.isShortcut) "Desktop Shortcut" else if(entry.recycledFile?.isDirectory==true) "Folder" else "File"
    AlertDialog.Builder(this).setTitle("${entry.displayName} Properties")
     .setMessage("الاسم: ${entry.displayName}\nالنوع: $kind\nالموقع الأصلي: ${entry.originalLocation}\nالحجم: ${RecycleBinStorage.formatSize(entry.sizeBytes)}\nتاريخ الحذف: ${dateFmt.format(java.util.Date(entry.deletedAtMs))}")
     .setPositiveButton("استعادة") { _,_ ->
      if(RecycleBinStorage.restoreEntry(this,entry)) {
       if(entry.isShortcut) showDesktop()
       refreshAll()
       Toast.makeText(this,"تمت استعادة: ${entry.displayName}",Toast.LENGTH_SHORT).show()
      }
     }
     .setNegativeButton("إغلاق",null).show()
   }
  }

  fun performEmptyRecycleBin() {
   if(allEntries.isEmpty()) return
   AlertDialog.Builder(this).setTitle("Empty Recycle Bin")
    .setMessage("هل تريد حذف جميع العناصر (${allEntries.size}) نهائيًا؟ لن تُحذف ملفات الهاتف الأساسية.")
    .setPositiveButton("حذف الكل") { _,_ ->
     RecycleBinStorage.emptyAll(this)
     selectedIds.clear()
     showDesktop()
     refreshAll()
     Toast.makeText(this,"تم إفراغ سلة المهملات",Toast.LENGTH_SHORT).show()
    }.setNegativeButton("إلغاء",null).show()
  }

  fun performRestoreAll() {
   if(allEntries.isEmpty()) return
   var restoredAnyShortcut=false
   var restoredCount=0
   allEntries.forEach { entry ->
    if(RecycleBinStorage.restoreEntry(this,entry)) {
     restoredCount++
     if(entry.isShortcut) restoredAnyShortcut=true
    }
   }
   selectedIds.clear()
   if(restoredAnyShortcut) showDesktop()
   refreshAll()
   Toast.makeText(this,"تمت استعادة $restoredCount عنصر",Toast.LENGTH_SHORT).show()
  }

  fun performRestoreSelected() {
   val targets=allEntries.filter { it.id in selectedIds }
   if(targets.isEmpty()) {
    Toast.makeText(this,"حدد عنصرًا أولًا للاستعادة",Toast.LENGTH_SHORT).show()
    return
   }
   var restoredAnyShortcut=false
   var restoredCount=0
   targets.forEach { entry ->
    if(RecycleBinStorage.restoreEntry(this,entry)) {
     restoredCount++
     if(entry.isShortcut) restoredAnyShortcut=true
    }
   }
   selectedIds.clear()
   if(restoredAnyShortcut) showDesktop()
   refreshAll()
   Toast.makeText(this,"تمت استعادة $restoredCount عنصر",Toast.LENGTH_SHORT).show()
  }

  fun performDeleteSelected() {
   val targets=allEntries.filter { it.id in selectedIds }
   if(targets.isEmpty()) return
   AlertDialog.Builder(this).setTitle("Delete Permanently")
    .setMessage("حذف ${targets.size} عنصر نهائيًا من سلة المهملات؟")
    .setPositiveButton("حذف") { _,_ ->
     targets.forEach { RecycleBinStorage.deleteEntryPermanently(this,it) }
     selectedIds.clear()
     showDesktop()
     refreshAll()
    }.setNegativeButton("إلغاء",null).show()
  }

  // 1. Contextual Tab Strip: File | Home | Share | View | [Recycle Bin Tools -> Manage]
  val tabStripRow=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.BOTTOM
   setPadding((4*d).toInt(),(2*d).toInt(),(6*d).toInt(),0)
   setBackgroundColor(RecycleBinTheme.RIBBON_TAB_BG)
  }
  val fileTabBtn=TextView(this).apply {
   text="File"; textSize=10.5f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
   typeface=Typeface.create("Arial",Typeface.NORMAL)
   setPadding((14*d).toInt(),(4*d).toInt(),(14*d).toInt(),(4*d).toInt())
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(RecycleBinTheme.FILE_BLUE)
    cornerRadii=floatArrayOf(2f*d,2f*d,2f*d,2f*d,0f,0f,0f,0f)
   }
   setOnClickListener { anchor ->
    android.widget.PopupMenu(this@MainActivity,anchor).apply {
     menu.add("Open This PC").setOnMenuItemClickListener { showMyComputer(); true }
     menu.add("Empty Recycle Bin").setOnMenuItemClickListener { performEmptyRecycleBin(); true }
     menu.add("Restore all items").setOnMenuItemClickListener { performRestoreAll(); true }
     menu.add("Recycle Bin properties").setOnMenuItemClickListener { showPropertiesDialog(null); true }
     show()
    }
   }
  }
  tabStripRow.addView(fileTabBtn,LinearLayout.LayoutParams(-2,(24*d).toInt()).apply { rightMargin=(2*d).toInt() })

  val tabViews=mutableMapOf<String,TextView>()
  listOf("Home","Share","View").forEach { name ->
   val tv=TextView(this).apply {
    text=name; textSize=10.5f; gravity=Gravity.CENTER; setTextColor(RecycleBinTheme.TEXT_DARK)
    setPadding((11*d).toInt(),(4*d).toInt(),(11*d).toInt(),(4*d).toInt())
    setOnClickListener { activeTab=name; ribbonCollapsed=false; renderRibbonBody() }
   }
   tabViews[name]=tv
   tabStripRow.addView(tv,LinearLayout.LayoutParams(-2,(24*d).toInt()))
  }

  // Contextual "Recycle Bin Tools" purple header badge + "Manage" tab below/beside
  val ctxTabGroup=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(RecycleBinTheme.CTX_PURPLE_BG)
   }
  }
  ctxTabGroup.addView(View(this).apply { setBackgroundColor(RecycleBinTheme.CTX_PURPLE_BAR) },LinearLayout.LayoutParams(-1,(2.5f*d).toInt().coerceAtLeast(2)))
  ctxTabGroup.addView(TextView(this).apply {
   text="Recycle Bin Tools"; textSize=9.5f; gravity=Gravity.CENTER
   typeface=Typeface.create("Arial",Typeface.BOLD)
   setTextColor(RecycleBinTheme.CTX_PURPLE_TEXT)
   setPadding((10*d).toInt(),(1*d).toInt(),(10*d).toInt(),(1*d).toInt())
  })
  val manageTabBtn=TextView(this).apply {
   text="Manage"; textSize=10.5f; gravity=Gravity.CENTER; setTextColor(RecycleBinTheme.TEXT_DARK)
   setPadding((12*d).toInt(),(3*d).toInt(),(12*d).toInt(),(4*d).toInt())
   setOnClickListener { activeTab="Manage"; ribbonCollapsed=false; renderRibbonBody() }
  }
  tabViews["Manage"]=manageTabBtn
  ctxTabGroup.addView(manageTabBtn,LinearLayout.LayoutParams(-1,(24*d).toInt()))
  tabStripRow.addView(ctxTabGroup,LinearLayout.LayoutParams(-2,-2).apply { leftMargin=(4*d).toInt() })
  tabStripRow.addView(View(this),LinearLayout.LayoutParams(0,1,1f))

  val collapseBtn=TextView(this).apply {
   text="⌃"; textSize=12f; gravity=Gravity.CENTER; setTextColor(RecycleBinTheme.TEXT_MUTED)
   contentDescription="طي أو توسيع الشريط"
   setOnClickListener { ribbonCollapsed=!ribbonCollapsed; text=if(ribbonCollapsed) "⌄" else "⌃"; renderRibbonBody() }
  }
  val helpBtn=TextView(this).apply {
   text="?"; textSize=10f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
   typeface=Typeface.create("Arial",Typeface.BOLD)
   background=android.graphics.drawable.GradientDrawable().apply { shape=android.graphics.drawable.GradientDrawable.OVAL; setColor(RecycleBinTheme.FILE_BLUE) }
   setOnClickListener {
    AlertDialog.Builder(this@MainActivity).setTitle("سلة المحذوفات")
     .setMessage("تعرض هذه النافذة الاختصارات والملفات المحذوفة داخل Oriket Desktop.\n• حدد عنصرًا ثم اضغط Restore the selected items لاستعادته.\n• أو اضغط Empty Recycle Bin لإفراغ السلة.")
     .setPositiveButton("فهمت",null).show()
   }
  }
  tabStripRow.addView(collapseBtn,LinearLayout.LayoutParams((22*d).toInt(),(22*d).toInt()))
  tabStripRow.addView(helpBtn,LinearLayout.LayoutParams((16*d).toInt(),(16*d).toInt()).apply { leftMargin=(4*d).toInt(); bottomMargin=(4*d).toInt() })
  page.addView(tabStripRow)

  // 2. Ribbon Body (#F5F6F7) matching screenshot
  val ribbonScroll=HorizontalScrollView(this).apply {
   isHorizontalScrollBarEnabled=false
   setBackgroundColor(RecycleBinTheme.RIBBON_BG)
  }
  val ribbonRow=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((8*d).toInt(),(4*d).toInt(),(8*d).toInt(),(3*d).toInt())
  }
  ribbonScroll.addView(ribbonRow)
  page.addView(ribbonScroll,LinearLayout.LayoutParams(-1,-2))
  val ribbonDivider=View(this).apply { setBackgroundColor(RecycleBinTheme.BORDER) }
  page.addView(ribbonDivider,LinearLayout.LayoutParams(-1,1))

  fun makeRibbonButton(glyph:RecycleGlyph,label:String,enabled:Boolean=true,onClick:()->Unit):LinearLayout {
   val btn=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL or Gravity.TOP
    setPadding((6*d).toInt(),(3*d).toInt(),(6*d).toInt(),(2*d).toInt())
    background=RecycleBinTheme.ribbonBtnBg(d,enabled)
    isClickable=enabled
    alpha=if(enabled) 1f else 0.48f
    if(enabled) setOnClickListener { onClick() }
   }
   val iconView=RecycleRibbonGlyphView(this,glyph,d).apply { setGlyphEnabled(enabled) }
   btn.addView(iconView,LinearLayout.LayoutParams((28*d).toInt(),(28*d).toInt()).apply { bottomMargin=(2*d).toInt() })
   btn.addView(TextView(this).apply {
    text=label; textSize=9.2f; gravity=Gravity.CENTER_HORIZONTAL
    setTextColor(RecycleBinTheme.TEXT_DARK)
    maxLines=2; includeFontPadding=false; setLineSpacing(0f,1.0f)
   })
   return btn
  }

  fun addRibbonGroup(title:String,build:(LinearLayout)->Unit) {
   val grp=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL }
   val inner=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
   build(inner)
   grp.addView(inner,LinearLayout.LayoutParams(-2,(64*d).toInt()))
   grp.addView(TextView(this).apply {
    text=title; textSize=8.5f; gravity=Gravity.CENTER; setTextColor(RecycleBinTheme.TEXT_MUTED); maxLines=1
   },LinearLayout.LayoutParams(-2,-2))
   ribbonRow.addView(grp)
   ribbonRow.addView(View(this).apply { setBackgroundColor(RecycleBinTheme.BORDER) },LinearLayout.LayoutParams(1,(68*d).toInt()).apply {
    leftMargin=(6*d).toInt(); rightMargin=(6*d).toInt()
   })
  }

  renderRibbonBody={
   tabViews.forEach { (k,tv) -> tv.background=RecycleBinTheme.tabBg(d,activeTab==k) }
   ribbonScroll.visibility=if(ribbonCollapsed) View.GONE else View.VISIBLE
   ribbonDivider.visibility=if(ribbonCollapsed) View.GONE else View.VISIBLE
   ribbonRow.removeAllViews()
   val hasAny=allEntries.isNotEmpty()
   val hasSel=selectedIds.isNotEmpty()
   when(activeTab) {
    "Home" -> {
     addRibbonGroup("Select") { g ->
      g.addView(makeRibbonButton(RecycleGlyph.SELECT_ALL,"Select\nall",hasAny) {
       selectedIds.clear(); selectedIds.addAll(visibleEntries().map { it.id })
       renderContent(); renderRibbonBody(); updateStatusBar()
      })
      g.addView(makeRibbonButton(RecycleGlyph.PROPERTIES,"Properties",true) {
       val single=allEntries.firstOrNull { it.id in selectedIds }
       showPropertiesDialog(single)
      })
     }
     addRibbonGroup("Organize") { g ->
      g.addView(makeRibbonButton(RecycleGlyph.DELETE_PERM,"Delete\npermanently",hasSel) { performDeleteSelected() })
      g.addView(makeRibbonButton(RecycleGlyph.RESTORE_SELECTED,"Restore\nselected",hasSel) { performRestoreSelected() })
     }
    }
    "View" -> {
     addRibbonGroup("Layout") { g ->
      val largeBtn=makeRibbonButton(RecycleGlyph.LARGE_ICONS_VIEW,"Large\nicons",true) {
       largeIconsMode=true; renderContent(); updateStatusBar()
      }.apply { background=RecycleBinTheme.ribbonBtnBg(d,true,largeIconsMode) }
      val detailsBtn=makeRibbonButton(RecycleGlyph.DETAILS_VIEW,"Details",true) {
       largeIconsMode=false; renderContent(); updateStatusBar()
      }.apply { background=RecycleBinTheme.ribbonBtnBg(d,true,!largeIconsMode) }
      g.addView(largeBtn)
      g.addView(detailsBtn)
     }
    }
    "Share" -> {
     addRibbonGroup("Recycle Bin") { g ->
      g.addView(makeRibbonButton(RecycleGlyph.PROPERTIES,"Recycle Bin\nproperties",true) { showPropertiesDialog(null) })
      g.addView(makeRibbonButton(RecycleGlyph.RESTORE_ALL,"Restore\nall items",hasAny) { performRestoreAll() })
     }
    }
    else -> { // Default "Manage" (Recycle Bin Tools) matching screenshot
     addRibbonGroup("Manage") { g ->
      g.addView(makeRibbonButton(RecycleGlyph.EMPTY_BIN,"Empty\nRecycle Bin",hasAny) { performEmptyRecycleBin() })
      g.addView(makeRibbonButton(RecycleGlyph.PROPERTIES,"Recycle Bin\nproperties",true) {
       val single=if(selectedIds.size==1) allEntries.firstOrNull { it.id in selectedIds } else null
       showPropertiesDialog(single)
      })
     }
     addRibbonGroup("Restore") { g ->
      g.addView(makeRibbonButton(RecycleGlyph.RESTORE_ALL,"Restore\nall items",hasAny) { performRestoreAll() })
      g.addView(makeRibbonButton(RecycleGlyph.RESTORE_SELECTED,"Restore the\nselected items",hasSel) { performRestoreSelected() })
     }
     if(hasSel) {
      addRibbonGroup("Delete") { g ->
       g.addView(makeRibbonButton(RecycleGlyph.DELETE_PERM,"Delete\npermanently",true) { performDeleteSelected() })
      }
     }
    }
   }
  }

  // 3. Navigation, Address Bar & Search Row (← → ↑ | [Icon > Recycle Bin v ⟳] | [Search Recycle Bin 🔍])
  val navRow=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((6*d).toInt(),(4*d).toInt(),(6*d).toInt(),(4*d).toInt())
   setBackgroundColor(RecycleBinTheme.WINDOW_BG)
  }
  fun navArrow(sym:String,desc:String,onClick:()->Unit)=TextView(this).apply {
   text=sym; textSize=13f; gravity=Gravity.CENTER; setTextColor(RecycleBinTheme.TEXT_MUTED)
   contentDescription=desc
   setOnClickListener { onClick() }
  }
  navRow.addView(navArrow("←","رجوع"){ showMyComputer() },LinearLayout.LayoutParams((22*d).toInt(),(26*d).toInt()))
  navRow.addView(navArrow("→","التالي"){ Toast.makeText(this,"لا يوجد مسار تالٍ",Toast.LENGTH_SHORT).show() },LinearLayout.LayoutParams((22*d).toInt(),(26*d).toInt()))
  navRow.addView(navArrow("↑","الأعلى"){ showDesktop() },LinearLayout.LayoutParams((22*d).toInt(),(26*d).toInt()).apply { rightMargin=(4*d).toInt() })

  val addressBox=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((6*d).toInt(),0,(6*d).toInt(),0)
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.WHITE); setStroke(1,RecycleBinTheme.BORDER); cornerRadius=2f*d
   }
  }
  addressBox.addView(ImageView(this).apply {
   setImageDrawable(FluentIcons.createDrawable(this@MainActivity,FluentIcon.RECYCLE_BIN,(15*d).toInt()))
  },LinearLayout.LayoutParams((15*d).toInt(),(15*d).toInt()).apply { rightMargin=(5*d).toInt() })
  addressBox.addView(TextView(this).apply {
   text="›   Recycle Bin"; textSize=10.5f; setTextColor(RecycleBinTheme.TEXT_DARK); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
  },LinearLayout.LayoutParams(0,-2,1f))
  addressBox.addView(TextView(this).apply {
   text="▾"; textSize=10f; gravity=Gravity.CENTER; setTextColor(RecycleBinTheme.TEXT_MUTED)
   setOnClickListener { anchor ->
    android.widget.PopupMenu(this@MainActivity,anchor).apply {
     menu.add("Desktop").setOnMenuItemClickListener { showMyComputer("Desktop"); true }
     menu.add("This PC").setOnMenuItemClickListener { showMyComputer(); true }
     menu.add("Documents").setOnMenuItemClickListener { showMyComputer("Documents"); true }
     menu.add("Downloads").setOnMenuItemClickListener { showMyComputer("Downloads"); true }
     menu.add("Recycle Bin").setOnMenuItemClickListener { refreshAll(); true }
     show()
    }
   }
  },LinearLayout.LayoutParams((18*d).toInt(),(24*d).toInt()))
  addressBox.addView(View(this).apply { setBackgroundColor(RecycleBinTheme.BORDER_LIGHT) },LinearLayout.LayoutParams(1,(16*d).toInt()))
  addressBox.addView(TextView(this).apply {
   text="⟳"; textSize=12.5f; gravity=Gravity.CENTER; setTextColor(RecycleBinTheme.TEXT_DARK); contentDescription="تحديث"
   setOnClickListener { refreshAll() }
  },LinearLayout.LayoutParams((22*d).toInt(),(24*d).toInt()))
  navRow.addView(addressBox,LinearLayout.LayoutParams(0,(26*d).toInt(),1f).apply { rightMargin=(6*d).toInt() })

  val searchBox=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((6*d).toInt(),0,(6*d).toInt(),0)
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.WHITE); setStroke(1,RecycleBinTheme.BORDER); cornerRadius=2f*d
   }
  }
  val searchInput=EditText(this).apply {
   hint="Search Recycle Bin"; textSize=10f; setSingleLine(true); setTextColor(RecycleBinTheme.TEXT_DARK)
   setHintTextColor(RecycleBinTheme.TEXT_MUTED)
   background=null; setPadding(0,0,0,0)
  }
  searchInput.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
    searchQuery=s?.toString()?.trim().orEmpty()
    renderContent()
    updateStatusBar()
   }
   override fun afterTextChanged(s:android.text.Editable?) {}
  })
  searchBox.addView(searchInput,LinearLayout.LayoutParams(0,-2,1f))
  searchBox.addView(TextView(this).apply { text="🔍"; textSize=10.5f; gravity=Gravity.CENTER; setTextColor(RecycleBinTheme.TEXT_MUTED) })
  navRow.addView(searchBox,LinearLayout.LayoutParams((155*d).toInt(),(26*d).toInt()))
  page.addView(navRow)
  page.addView(View(this).apply { setBackgroundColor(RecycleBinTheme.BORDER_LIGHT) },LinearLayout.LayoutParams(-1,1))

  // 4. Split Pane: Left Navigation Sidebar + Right Content Grid/Details
  val split=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setBackgroundColor(RecycleBinTheme.WINDOW_BG) }
  val navSidebar=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; setPadding((4*d).toInt(),(6*d).toInt(),(4*d).toInt(),(6*d).toInt())
   setBackgroundColor(RecycleBinTheme.WINDOW_BG)
  }
  fun addSidebarRow(label:String,icon:FluentIcon,indentDp:Int,active:Boolean=false,onClick:()->Unit) {
   val row=LinearLayout(this).apply {
    orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
    setPadding(((6+indentDp)*d).toInt(),(4*d).toInt(),(6*d).toInt(),(4*d).toInt())
    background=android.graphics.drawable.GradientDrawable().apply {
     setColor(if(active) RecycleBinTheme.SIDEBAR_ACTIVE_FILL else Color.TRANSPARENT)
     if(active) setStroke(1,RecycleBinTheme.SIDEBAR_ACTIVE_BORDER)
     cornerRadius=2f*d
    }
    isClickable=true
    setOnClickListener { onClick() }
   }
   row.addView(ImageView(this).apply {
    setImageDrawable(FluentIcons.createDrawable(this@MainActivity,icon,(15*d).toInt()))
   },LinearLayout.LayoutParams((15*d).toInt(),(15*d).toInt()).apply { rightMargin=(6*d).toInt() })
   row.addView(TextView(this).apply {
    text=label; textSize=10f; setTextColor(RecycleBinTheme.TEXT_DARK); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
   },LinearLayout.LayoutParams(0,-2,1f))
   navSidebar.addView(row,LinearLayout.LayoutParams(-1,(25*d).toInt()).apply { bottomMargin=(1*d).toInt() })
  }
  addSidebarRow("Quick access",FluentIcon.FOLDER_PROJECTS,0) { showMyComputer() }
  addSidebarRow("Desktop",FluentIcon.FOLDER_DESKTOP,6) { showMyComputer("Desktop") }
  addSidebarRow("This PC",FluentIcon.THIS_PC,0) { showMyComputer() }
  addSidebarRow("Desktop",FluentIcon.FOLDER_DESKTOP,12) { showMyComputer("Desktop") }
  addSidebarRow("Documents",FluentIcon.FOLDER_DOCUMENTS,12) { showMyComputer("Documents") }
  addSidebarRow("Downloads",FluentIcon.FOLDER_DOWNLOADS,12) { showMyComputer("Downloads") }
  addSidebarRow("Music",FluentIcon.FOLDER_MUSIC,12) { showMyComputer("Music") }
  addSidebarRow("Pictures",FluentIcon.FOLDER_PICTURES,12) { showMyComputer("Pictures") }
  addSidebarRow("Videos",FluentIcon.FOLDER_VIDEOS,12) { showMyComputer("Videos") }
  addSidebarRow("Local Disk (C:)",FluentIcon.HARD_DRIVE,12) { showMyComputer("") }
  addSidebarRow("Recycle Bin",FluentIcon.RECYCLE_BIN,0,active=true) { refreshAll() }

  val sideScroll=ScrollView(this).apply { isVerticalScrollBarEnabled=false; addView(navSidebar) }
  split.addView(sideScroll,LinearLayout.LayoutParams((142*d).toInt(),-1))
  split.addView(View(this).apply { setBackgroundColor(RecycleBinTheme.BORDER_LIGHT) },LinearLayout.LayoutParams(1,-1))

  val rightPane=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; setPadding((10*d).toInt(),(6*d).toInt(),(10*d).toInt(),(6*d).toInt())
   setBackgroundColor(RecycleBinTheme.WINDOW_BG)
  }
  val rightScroll=ScrollView(this).apply { addView(rightPane) }
  split.addView(rightScroll,LinearLayout.LayoutParams(0,-1,1f))
  page.addView(split,LinearLayout.LayoutParams(-1,0,1f))

  // 5. Bottom Status Bar: "N items  |  M item selected  Size" + [Details | Large Icons]
  page.addView(View(this).apply { setBackgroundColor(RecycleBinTheme.BORDER_LIGHT) },LinearLayout.LayoutParams(-1,1))
  val statusBar=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((10*d).toInt(),(2*d).toInt(),(8*d).toInt(),(2*d).toInt())
   setBackgroundColor(RecycleBinTheme.STATUS_BG)
  }
  val statusText=TextView(this).apply { text="0 items"; textSize=9.5f; setTextColor(RecycleBinTheme.TEXT_DARK); maxLines=1 }
  val detailsToggle=RecycleRibbonGlyphView(this,RecycleGlyph.DETAILS_VIEW,d).apply {
   contentDescription="عرض التفاصيل"
   setOnClickListener { largeIconsMode=false; renderContent(); updateStatusBar() }
  }
  val largeIconsToggle=RecycleRibbonGlyphView(this,RecycleGlyph.LARGE_ICONS_VIEW,d).apply {
   contentDescription="عرض الأيقونات الكبيرة"
   setOnClickListener { largeIconsMode=true; renderContent(); updateStatusBar() }
  }
  statusBar.addView(statusText,LinearLayout.LayoutParams(0,-2,1f))
  statusBar.addView(detailsToggle,LinearLayout.LayoutParams((22*d).toInt(),(18*d).toInt()).apply { rightMargin=(4*d).toInt() })
  statusBar.addView(largeIconsToggle,LinearLayout.LayoutParams((22*d).toInt(),(18*d).toInt()))
  page.addView(statusBar,LinearLayout.LayoutParams(-1,(24*d).toInt()))

  updateStatusBar={
   val list=visibleEntries()
   val selList=list.filter { it.id in selectedIds }
   val countText="${list.size} ${if(list.size==1) "item" else "items"}"
   val selText=if(selList.isNotEmpty()) {
    val selBytes=selList.sumOf { it.sizeBytes }
    "   |   ${selList.size} ${if(selList.size==1) "item" else "items"} selected  ${RecycleBinStorage.formatSize(selBytes)}"
   } else ""
   statusText.text=countText+selText
   detailsToggle.background=RecycleBinTheme.ribbonBtnBg(d,true,!largeIconsMode)
   largeIconsToggle.background=RecycleBinTheme.ribbonBtnBg(d,true,largeIconsMode)
  }

  renderContent={
   rightPane.removeAllViews()
   val list=visibleEntries()
   if(list.isEmpty()) {
    rightPane.addView(TextView(this).apply {
     text=if(searchQuery.isNotBlank()) "No items match your search." else "This folder is empty.\n\nسلة المحذوفات فارغة — عند حذف اختصار من سطح المكتب أو ملف من «هذا الكمبيوتر» سيظهر هنا."
     textSize=11.5f; gravity=Gravity.CENTER; setTextColor(RecycleBinTheme.TEXT_MUTED)
     setPadding((16*d).toInt(),(36*d).toInt(),(16*d).toInt(),(16*d).toInt())
    },LinearLayout.LayoutParams(-1,-2))
   } else {
    // Collapsible Group Header matching "⌄ Earlier this year (N)"
    val headerRow=LinearLayout(this).apply {
     orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
     setPadding((2*d).toInt(),(2*d).toInt(),(2*d).toInt(),(6*d).toInt())
     isClickable=true
     setOnClickListener { groupExpanded=!groupExpanded; renderContent() }
    }
    headerRow.addView(TextView(this).apply {
     text="${if(groupExpanded) "⌄" else "›"}  Recycle Bin items (${list.size})"
     textSize=11.5f; typeface=Typeface.create("Arial",Typeface.BOLD)
     setTextColor(RecycleBinTheme.GROUP_HEADER_BLUE)
    })
    headerRow.addView(View(this).apply { setBackgroundColor(RecycleBinTheme.BORDER_LIGHT) },LinearLayout.LayoutParams(0,1,1f).apply { leftMargin=(8*d).toInt() })
    rightPane.addView(headerRow)

    if(groupExpanded) {
     if(largeIconsMode) {
      val availW=(rightPane.width.takeIf { it>0 } ?: (desktopArea.width*0.52f).toInt()).coerceAtLeast((220*d).toInt())
      val cols=(availW/(92*d).toInt()).coerceIn(2,7)
      val grid=GridLayout(this).apply { columnCount=cols }
      list.forEach { entry ->
       val selected=entry.id in selectedIds
       val card=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL or Gravity.TOP
        setPadding((6*d).toInt(),(6*d).toInt(),(6*d).toInt(),(5*d).toInt())
        background=RecycleBinTheme.itemCardBg(d,selected)
        isClickable=true
        setOnClickListener {
         if(entry.id in selectedIds) selectedIds.remove(entry.id) else { selectedIds.clear(); selectedIds.add(entry.id) }
         renderContent(); renderRibbonBody(); updateStatusBar()
        }
        setOnLongClickListener { anchor ->
         selectedIds.clear(); selectedIds.add(entry.id)
         renderContent(); renderRibbonBody(); updateStatusBar()
         android.widget.PopupMenu(this@MainActivity,anchor).apply {
          menu.add("استعادة (Restore)").setOnMenuItemClickListener { performRestoreSelected(); true }
          menu.add("حذف نهائي (Delete)").setOnMenuItemClickListener { performDeleteSelected(); true }
          menu.add("خصائص (Properties)").setOnMenuItemClickListener { showPropertiesDialog(entry); true }
          show()
         }
         true
        }
       }
       val thumbBmp=RecycleBinStorage.decodeThumbnail(entry.recycledFile,(56*d).toInt())
       val preview=ImageView(this).apply {
        scaleType=if(thumbBmp!=null) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        contentDescription=entry.displayName
        if(thumbBmp!=null) setImageBitmap(thumbBmp)
        else setImageDrawable(FluentIcons.createDrawable(this@MainActivity,entry.icon,(42*d).toInt()))
       }
       card.addView(preview,LinearLayout.LayoutParams((56*d).toInt(),(42*d).toInt()).apply { bottomMargin=(4*d).toInt() })
       card.addView(TextView(this).apply {
        text=entry.displayName; textSize=9.2f; gravity=Gravity.CENTER_HORIZONTAL
        setTextColor(RecycleBinTheme.TEXT_DARK); maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END
        includeFontPadding=false; setLineSpacing(0f,1.05f)
       },LinearLayout.LayoutParams(-1,-2))
       grid.addView(card,GridLayout.LayoutParams().apply {
        width=0; height=(88*d).toInt(); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f)
        setMargins((3*d).toInt(),(2*d).toInt(),(3*d).toInt(),(4*d).toInt())
       })
      }
      rightPane.addView(grid,LinearLayout.LayoutParams(-1,-2))
     } else {
      val dateFmt=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault())
      list.forEach { entry ->
       val selected=entry.id in selectedIds
       val row=LinearLayout(this).apply {
        orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
        setPadding((6*d).toInt(),(5*d).toInt(),(6*d).toInt(),(5*d).toInt())
        background=RecycleBinTheme.itemCardBg(d,selected)
        isClickable=true
        setOnClickListener {
         if(entry.id in selectedIds) selectedIds.remove(entry.id) else { selectedIds.clear(); selectedIds.add(entry.id) }
         renderContent(); renderRibbonBody(); updateStatusBar()
        }
        setOnLongClickListener { anchor ->
         selectedIds.clear(); selectedIds.add(entry.id)
         renderContent(); renderRibbonBody(); updateStatusBar()
         android.widget.PopupMenu(this@MainActivity,anchor).apply {
          menu.add("استعادة (Restore)").setOnMenuItemClickListener { performRestoreSelected(); true }
          menu.add("حذف نهائي (Delete)").setOnMenuItemClickListener { performDeleteSelected(); true }
          menu.add("خصائص (Properties)").setOnMenuItemClickListener { showPropertiesDialog(entry); true }
          show()
         }
         true
        }
       }
       row.addView(ImageView(this).apply {
        setImageDrawable(FluentIcons.createDrawable(this@MainActivity,entry.icon,(20*d).toInt()))
       },LinearLayout.LayoutParams((20*d).toInt(),(20*d).toInt()).apply { rightMargin=(8*d).toInt() })
       row.addView(TextView(this).apply {
        text=entry.displayName; textSize=10f; setTextColor(RecycleBinTheme.TEXT_DARK); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
       },LinearLayout.LayoutParams(0,-2,1.3f))
       row.addView(TextView(this).apply {
        text=entry.originalLocation; textSize=9.2f; setTextColor(RecycleBinTheme.TEXT_MUTED); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.MIDDLE
       },LinearLayout.LayoutParams(0,-2,1f))
       row.addView(TextView(this).apply {
        text=dateFmt.format(java.util.Date(entry.deletedAtMs)); textSize=9f; setTextColor(RecycleBinTheme.TEXT_MUTED); gravity=Gravity.END
       },LinearLayout.LayoutParams((86*d).toInt(),-2))
       row.addView(TextView(this).apply {
        text=RecycleBinStorage.formatSize(entry.sizeBytes); textSize=9f; setTextColor(RecycleBinTheme.TEXT_MUTED); gravity=Gravity.END
       },LinearLayout.LayoutParams((54*d).toInt(),-2))
       rightPane.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(2*d).toInt() })
      }
     }
    }
   }
  }

  refreshAll={
   allEntries=RecycleBinStorage.listItems(this)
   selectedIds.retainAll(allEntries.map { it.id }.toSet())
   renderRibbonBody()
   renderContent()
   updateStatusBar()
  }

  panel(page)
  refreshAll()
  rightPane.post { if(largeIconsMode && allEntries.isNotEmpty()) renderContent() }
 }
 private fun showPowerMenu() {
  val options=arrayOf("🌙  سكون","⏻  إيقاف Oriket Desktop","🔄  إعادة تشغيل Oriket Desktop")
  AlertDialog.Builder(this).setTitle("خيارات الطاقة").setItems(options) { _,which ->
   when(which) {
    0 -> {
     playDesktopSound("sleep")
     val screen=FrameLayout(this).apply {
      addView(ScenicLockWallpaper(this@MainActivity),FrameLayout.LayoutParams(-1,-1))
      isClickable=true
      val shade=View(this@MainActivity).apply { setBackgroundColor(Color.argb(45,0,0,0)) }
      addView(shade,FrameLayout.LayoutParams(-1,-1))
      val clock=TextView(this@MainActivity).apply {
       text=java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(java.util.Date())+"\\n"+
        java.text.SimpleDateFormat("EEEE، d MMMM yyyy",Locale("ar")).format(java.util.Date())+
        "\\n\\nالمس الشاشة للعودة"
       textSize=22f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
       typeface=Typeface.create("Arial",Typeface.NORMAL)
       setShadowLayer(5f,1f,2f,Color.BLACK)
      }
      addView(clock,FrameLayout.LayoutParams(-1,-1))
      setOnClickListener { desktopArea.removeView(this); playDesktopSound("start"); showDesktop() }
     }
     desktopArea.addView(screen,FrameLayout.LayoutParams(-1,-1)); screen.bringToFront()
    }
    1 -> AlertDialog.Builder(this).setTitle("إيقاف التشغيل")
     .setMessage("سيتم إغلاق Oriket Desktop فقط؛ لا يستطيع التطبيق إطفاء هاتف Android نفسه.")
     .setPositiveButton("إيقاف") { _,_ -> playDesktopSound("shutdown"); uiHandler.postDelayed({ finish() },650L) }
     .setNegativeButton("إلغاء",null).show()
    2 -> AlertDialog.Builder(this).setTitle("إعادة التشغيل")
     .setMessage("سيُعاد تشغيل واجهة Oriket Desktop، وليس هاتف Android.")
     .setPositiveButton("إعادة التشغيل") { _,_ ->
      playDesktopSound("restart")
      openWindows.clear()
      desktopInitialized=false
      uiHandler.postDelayed({ if(!isFinishing) showBootSequence() },500L)
     }.setNegativeButton("إلغاء",null).show()
   }
  }.setNegativeButton("إلغاء",null).show()
 }

 private fun showStartMenu() {
  val existing=desktopArea.findViewWithTag<View>("start-menu")
  if(existing!=null) { desktopArea.removeView(existing); return }
  val d=resources.displayMetrics.density.coerceAtLeast(1f)
  val availableWidth=(desktopArea.width-(20*d).toInt()).coerceAtLeast((280*d).toInt())
  val panelWidth=(desktopArea.width*0.72f).toInt().coerceIn((290*d).toInt(),minOf((520*d).toInt(),availableWidth))
  val panelHeight=(desktopArea.height*0.88f).toInt().coerceIn((280*d).toInt(),(desktopArea.height-(14*d).toInt()).coerceAtLeast((280*d).toInt()))
  val menu=LinearLayout(this).apply {
   tag="start-menu"; orientation=LinearLayout.VERTICAL; setPadding(0,(14*d).toInt(),0,0)
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.argb(248,245,248,253)); cornerRadius=14f*d
    setStroke(1,Color.rgb(214,224,238))
   }
   elevation=28f*d
   clipToOutline=true
  }
  val bodyWrap=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding((18*d).toInt(),0,(18*d).toInt(),(8*d).toInt())
  }
  val search=EditText(this).apply {
   tag="start-search"; hint="⌕  البحث عن التطبيقات والإعدادات"
   textSize=11.2f; setSingleLine(true)
   setPadding((14*d).toInt(),0,(14*d).toInt(),0); setTextColor(navy); setHintTextColor(Color.rgb(105,120,143))
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.WHITE); cornerRadius=18f*d; setStroke(1,Color.rgb(210,222,239))
   }
  }
  bodyWrap.addView(search,LinearLayout.LayoutParams(-1,(36*d).toInt()))
  val colCount=if(panelWidth/d >= 420f) 6 else 4
  val grid=GridLayout(this).apply { columnCount=colCount; useDefaultMargins=false }
  lateinit var allButton:Button
  var expandedApps=false
  val heading=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding((2*d).toInt(),(10*d).toInt(),(2*d).toInt(),(4*d).toInt()) }
  heading.addView(TextView(this).apply {
   text="التطبيقات المثبّتة"; textSize=11.5f
   typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy)
  },LinearLayout.LayoutParams(0,(28*d).toInt(),1f))
  allButton=task("كل التطبيقات  ›") {
   expandedApps=!expandedApps
   grid.columnCount=if(expandedApps) 2 else colCount
   allButton.text=if(expandedApps) "عرض المختصر  ‹" else "كل التطبيقات  ›"
  }
  allButton.textSize=9.5f
  allButton.minHeight=(26*d).toInt(); allButton.minimumHeight=(26*d).toInt()
  allButton.setPadding((10*d).toInt(),0,(10*d).toInt(),0)
  heading.addView(allButton,LinearLayout.LayoutParams(-2,(28*d).toInt()))
  bodyWrap.addView(heading)
  val items=listOf(
   Triple("🌐","متصفح الإنترنت",{showBrowser()}),Triple("🖥️","هذا الكمبيوتر",{showMyComputer()}),
   Triple("📁","مستكشف الملفات",{showMyComputer()}),Triple("📄","PDF",{pickFile("application/pdf")}),
   Triple("🎬","مشغل الفيديو",{showVideoPlayer()}),Triple("🖼️","الصور",{showPhotos()}),
   Triple("🎵","مشغل الموسيقى",{showMusicPlayer()}),Triple("▶️","YouTube",{showYouTube()}),
   Triple("🎧","YouTube Music",{showYouTubeMusic()}),Triple("🎨","الرسام",{showPaint()}),
   Triple("📝","Word",{showWord()}),Triple("📊","Excel",{showExcel()}),
   Triple("⚙️","الإعدادات",{showControlPanel()}),Triple("📋","إدارة المهام",{showTaskManager()}),
   Triple("⌨️","Command",{showCommand()}),Triple("🎮","الألعاب",{showGames()}),
   Triple("🧮","الحاسبة",{showCalculator()}),Triple("🗒️","المفكرة",{showNotepad()}),
   Triple("🔄","تحديث التطبيق",{checkForUpdates(true)}),Triple("ℹ️","حول Oriket",{showAbout()})
  )
  val empty=TextView(this).apply {
   text="لا توجد تطبيقات مطابقة لبحثك"; textSize=11f
   setTextColor(Color.rgb(105,120,143)); gravity=Gravity.CENTER
   visibility=View.GONE
  }
  items.forEach { item ->
   val tile=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding((2*d).toInt(),(4*d).toInt(),(2*d).toInt(),(4*d).toInt())
    background=android.graphics.drawable.GradientDrawable().apply {
     setColor(Color.TRANSPARENT); cornerRadius=8f*d
    }
    isClickable=true; isFocusable=true
    setOnClickListener { desktopArea.removeView(menu); item.third.invoke() }
    setOnTouchListener { v,e ->
     if(e.action==MotionEvent.ACTION_DOWN) v.background=android.graphics.drawable.GradientDrawable().apply {
      setColor(Color.rgb(228,237,250)); cornerRadius=8f*d
     }
     if(e.action==MotionEvent.ACTION_UP || e.action==MotionEvent.ACTION_CANCEL)
      v.postDelayed({v.background=android.graphics.drawable.GradientDrawable().apply {
       setColor(Color.TRANSPARENT); cornerRadius=8f*d
      }},160)
     false
    }
   }
   tile.addView(Icon3DView(this,item.first,item.second),LinearLayout.LayoutParams((30*d).toInt(),(30*d).toInt()))
   tile.addView(TextView(this).apply {
    text=item.second; textSize=8.8f; gravity=Gravity.CENTER
    setTextColor(navy); typeface=Typeface.create("Arial",Typeface.NORMAL)
    maxLines=2; minLines=2; ellipsize=android.text.TextUtils.TruncateAt.END
    setLineSpacing(0f,1.02f)
    setPadding(0,(3*d).toInt(),0,0)
   },LinearLayout.LayoutParams(-1,(26*d).toInt()))
   grid.addView(tile,GridLayout.LayoutParams().apply {
    width=0; height=(68*d).toInt(); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f)
    setMargins((2*d).toInt(),(2*d).toInt(),(2*d).toInt(),(2*d).toInt())
   })
  }
  val appsScroll=ScrollView(this).apply {
   isFillViewport=true
   addView(LinearLayout(this@MainActivity).apply {
    orientation=LinearLayout.VERTICAL
    addView(grid,LinearLayout.LayoutParams(-1,-2))
    addView(empty,LinearLayout.LayoutParams(-1,(60*d).toInt()))
   })
  }
  bodyWrap.addView(appsScroll,LinearLayout.LayoutParams(-1,0,1f))
  val recommendedHeading=TextView(this).apply {
   text="موصى به"; textSize=11f; typeface=Typeface.create("Arial",Typeface.BOLD)
   setTextColor(navy); setPadding((2*d).toInt(),(6*d).toInt(),(2*d).toInt(),(4*d).toInt())
  }
  bodyWrap.addView(recommendedHeading)
  val rec=GridLayout(this).apply { columnCount=2; useDefaultMargins=false }
  listOf(
   Triple("📄","فتح مستند PDF","اختر ملف PDF من جهازك"),
   Triple("🎨","الرسام","افتح لوحة الرسم وابدأ مشروعًا جديدًا")
  ).forEachIndexed { index,item ->
   val row=LinearLayout(this).apply {
    orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
    setPadding((6*d).toInt(),(3*d).toInt(),(6*d).toInt(),(3*d).toInt())
    background=android.graphics.drawable.GradientDrawable().apply {
     setColor(Color.rgb(252,253,255)); cornerRadius=8f*d
     setStroke(1,Color.rgb(232,237,245))
    }
    isClickable=true
    setOnClickListener {
     desktopArea.removeView(menu)
     if(index==0) pickFile("application/pdf") else showPaint()
    }
   }
   val recIcon = ImageView(this).apply {
    val iconType = if(index==0) FluentIcon.PDF_READER else FluentIcon.PAINT
    val recD = FluentIcons.createDrawable(this@MainActivity, iconType, (24*d).toInt())
    setImageDrawable(recD)
   }
   row.addView(recIcon,LinearLayout.LayoutParams((28*d).toInt(),(28*d).toInt()).apply{ rightMargin=(6*d).toInt() })
   val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
   labels.addView(TextView(this).apply {
    text=item.second; textSize=9.8f; typeface=Typeface.create("Arial",Typeface.BOLD)
    setTextColor(navy); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
   })
   labels.addView(TextView(this).apply {
    text=item.third; textSize=8.4f; setTextColor(Color.GRAY); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
   })
   row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
   rec.addView(row,GridLayout.LayoutParams().apply {
    width=0; height=(40*d).toInt(); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f)
    setMargins((2*d).toInt(),(2*d).toInt(),(2*d).toInt(),(2*d).toInt())
   })
  }
  bodyWrap.addView(rec,LinearLayout.LayoutParams(-1,-2))
  menu.addView(bodyWrap,LinearLayout.LayoutParams(-1,0,1f))
  val footer=LinearLayout(this).apply {
   gravity=Gravity.CENTER_VERTICAL; setPadding((18*d).toInt(),(4*d).toInt(),(16*d).toInt(),(4*d).toInt())
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.rgb(235,241,249)); setStroke(1,Color.rgb(220,229,242))
   }
  }
  val avatarView=ImageView(this).apply {
   setImageDrawable(FluentIcons.createDrawable(this@MainActivity,FluentIcon.WINDOWS_START,(18*d).toInt()))
  }
  footer.addView(avatarView,LinearLayout.LayoutParams((22*d).toInt(),(22*d).toInt()).apply{ rightMargin=(8*d).toInt() })
  footer.addView(TextView(this).apply {
   text="Oriket Desktop"; textSize=10.5f
   typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy)
  },LinearLayout.LayoutParams(0,(36*d).toInt(),1f).apply{ gravity=Gravity.CENTER_VERTICAL })
  footer.addView(task("⚙") { desktopArea.removeView(menu); showControlPanel() }.apply { minHeight=(32*d).toInt(); minimumHeight=(32*d).toInt(); setPadding((10*d).toInt(),0,(10*d).toInt(),0) },LinearLayout.LayoutParams((36*d).toInt(),(32*d).toInt()).apply{ rightMargin=(6*d).toInt() })
  footer.addView(task("⏻") { desktopArea.removeView(menu); showPowerMenu() }.apply { minHeight=(32*d).toInt(); minimumHeight=(32*d).toInt(); setPadding((10*d).toInt(),0,(10*d).toInt(),0) },LinearLayout.LayoutParams((36*d).toInt(),(32*d).toInt()))
  menu.addView(footer,LinearLayout.LayoutParams(-1,(46*d).toInt()))
  desktopArea.addView(menu,FrameLayout.LayoutParams(panelWidth,panelHeight,
   Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=(8*d).toInt() })
  menu.bringToFront()
  search.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
    val q=s.toString().trim()
    var visibleCount=0
    for(i in 0 until grid.childCount) {
     val tile=grid.getChildAt(i)
     val label=(tile as? ViewGroup)?.let {
      it.getChildAt(1) as? TextView
     }?.text?.toString().orEmpty()
     val match=q.isEmpty() || label.contains(q,true)
     tile.visibility=if(match) View.VISIBLE else View.GONE
     if(match) visibleCount++
    }
    empty.visibility=if(visibleCount==0) View.VISIBLE else View.GONE
    recommendedHeading.visibility=if(q.isEmpty()) View.VISIBLE else View.GONE
    rec.visibility=if(q.isEmpty()) View.VISIBLE else View.GONE
   }
   override fun afterTextChanged(s:android.text.Editable?) {}
  })
 }
 private fun checkForUpdates(manual:Boolean) {
  if (BuildConfig.PLAY_DISTRIBUTION) {
   if (manual) Toast.makeText(this,"تصل تحديثات نسخة Google Play من متجر Google Play.",Toast.LENGTH_LONG).show()
   return
  }
  if(manual) Toast.makeText(this,"جارٍ التحقق من وجود تحديث…",Toast.LENGTH_SHORT).show()
  Thread {
   var latestCode=-1
   var downloadUrl:String?=null
   var releaseName="تحديث جديد"
   try {
    val connection=(URL("https://api.github.com/repos/mohmedelmotaz151-ctrl/Oriket-desktop-/releases/tags/latest").openConnection() as HttpURLConnection)
    connection.connectTimeout=10000
    connection.readTimeout=10000
    connection.setRequestProperty("Accept","application/vnd.github+json")
    connection.setRequestProperty("User-Agent","Oriket-Desktop-Android")
    val json=connection.inputStream.bufferedReader().use{it.readText()}
    connection.disconnect()
    val release=JSONObject(json)
    releaseName=release.optString("name","Oriket Desktop")
    val body=release.optString("body","")
    val match=Regex("\"versionCode\"\\s*:\\s*(\\d+)").find(body)
    latestCode=match?.groupValues?.get(1)?.toIntOrNull() ?: -1
    val assets=release.optJSONArray("assets")
    if(assets!=null) for(i in 0 until assets.length()) {
     val asset=assets.getJSONObject(i)
     if(asset.optString("name")=="orkeit-desktop.apk") { downloadUrl=asset.optString("browser_download_url"); break }
    }
   } catch(_:Exception) {}
   runOnUiThread {
    if(latestCode>BuildConfig.VERSION_CODE && !downloadUrl.isNullOrBlank()) {
     AlertDialog.Builder(this).setTitle("يتوفر تحديث لـ Oriket Desktop")
      .setMessage("$releaseName\nالإصدار المثبت: ${BuildConfig.VERSION_NAME}\nالإصدار الجديد: $latestCode\n\nهل تريد تنزيل التحديث الآن؟")
      .setPositiveButton("تنزيل التحديث") { _,_-> downloadUpdate(downloadUrl!!) }
      .setNegativeButton("لاحقًا",null).show()
    } else if(manual) {
     val msg=if(latestCode<0 || downloadUrl.isNullOrBlank()) "تعذر العثور على إصدار منشور. حاول لاحقًا." else "أنت تستخدم أحدث إصدار متاح."
     Toast.makeText(this,msg,Toast.LENGTH_LONG).show()
    }
   }
  }.start()
 }

 private fun downloadUpdate(url:String) {
  try {
   val request=DownloadManager.Request(Uri.parse(url))
    .setTitle("تحديث Oriket Desktop")
    .setDescription("جارٍ تنزيل الإصدار الجديد")
    .setMimeType("application/vnd.android.package-archive")
    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    .setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,"orkeit-desktop-update.apk")
   activeDownloadId=(getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
   Toast.makeText(this,"بدأ تنزيل التحديث. سيظهر إشعار عند اكتماله.",Toast.LENGTH_LONG).show()
  } catch(_:Exception) {
   Toast.makeText(this,"تعذر بدء التنزيل. تحقق من اتصال الإنترنت.",Toast.LENGTH_LONG).show()
  }
 }

 private fun installDownloadedApk(uri:Uri) {
  try {
   // Android 8+ requires explicit approval for installs initiated by this app.
   if(Build.VERSION.SDK_INT>=26 && !packageManager.canRequestPackageInstalls()) {
    pendingInstallUri=uri
    AlertDialog.Builder(this)
     .setTitle("السماح بتثبيت التحديث")
     .setMessage("فعّل السماح من هذا المصدر لتثبيت تحديث Oriket Desktop فوق النسخة الحالية مع الاحتفاظ ببيانات التطبيق. لا تحذف النسخة الحالية.")
     .setPositiveButton("فتح الإعدادات") { _,_->
      try {
       startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
      } catch(_:Exception) {
       Toast.makeText(this,"افتح إعدادات Android ثم اسمح بتثبيت التطبيقات من هذا المصدر.",Toast.LENGTH_LONG).show()
      }
     }
     .setNegativeButton("إلغاء",null).show()
    return
   }
   val intent=Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri,"application/vnd.android.package-archive")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
   }
   startActivity(intent)
  } catch(_:Exception) {
   Toast.makeText(this,"تعذر فتح مثبت الحزم. افتح ملف التحديث من التنزيلات يدويًا.",Toast.LENGTH_LONG).show()
  }
 }

 override fun onResume() {
  super.onResume()
  if(Build.VERSION.SDK_INT>=26 && packageManager.canRequestPackageInstalls()) {
   val uri=pendingInstallUri
   if(uri!=null) {
    pendingInstallUri=null
    uiHandler.post { installDownloadedApk(uri) }
   }
  }
 }

 private fun showBrowser(start:String="https://www.google.com", windowTitle:String="المتصفح") {
  shell(windowTitle)
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(2,2,2,4) }
  val browser=WebView(this)
  web=browser
  val address=EditText(this).apply {
   hint="اكتب رابط الموقع مثل example.com أو ابحث في Google"
   setSingleLine(true)
   textSize=12.32f
   inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
   imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_GO
   setText(start)
   setSelectAllOnFocus(true)
   contentDescription="شريط عنوان الموقع والبحث"
   setPadding(10,0,10,0)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=10f; setStroke(1,blue) }
  }
  fun loadAddress() {
   val s=address.text.toString().trim()
   if(s.isEmpty()) { address.error="اكتب رابط موقع أو كلمة للبحث"; address.requestFocus(); return }
   val target=when {
    s.startsWith("http://",true)||s.startsWith("https://",true) -> s
    s.contains(" ")||(!s.contains(".") && !s.startsWith("www.",true)) -> "https://www.google.com/search?q="+Uri.encode(s)
    else -> "https://"+s
   }
   try { browser.loadUrl(target); address.clearFocus() }
   catch(_:Exception) { address.error="تعذر فتح الرابط؛ تحقق من العنوان" }
  }
  row.addView(task("‹"){if(browser.canGoBack())browser.goBack()},LinearLayout.LayoutParams(34,44))
  row.addView(task("›"){if(browser.canGoForward())browser.goForward()},LinearLayout.LayoutParams(34,44))
  row.addView(task("↻"){browser.reload()},LinearLayout.LayoutParams(38,44))
  row.addView(address,LinearLayout.LayoutParams(0,44,1f))
  row.addView(action("اذهب"){loadAddress()},LinearLayout.LayoutParams(60,44))
  page.addView(row)
  val bar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
  bar.addView(task("الرئيسية"){browser.loadUrl("https://www.google.com")})
  bar.addView(task("إضافة للمفضلة"){ 
   val url=browser.url ?: return@task
   val prefs=getSharedPreferences("browser_bookmarks",Context.MODE_PRIVATE)
   val saved=prefs.getStringSet("urls",emptySet())?.toMutableSet() ?: mutableSetOf()
   saved.add(url); prefs.edit().putStringSet("urls",saved).apply()
   Toast.makeText(this,"تمت إضافة الصفحة للمفضلة",Toast.LENGTH_SHORT).show()
  })
  bar.addView(task("المفضلة"){ 
   val saved=getSharedPreferences("browser_bookmarks",Context.MODE_PRIVATE).getStringSet("urls",emptySet())?.toList() ?: emptyList()
   if(saved.isEmpty()) Toast.makeText(this,"لا توجد صفحات محفوظة",Toast.LENGTH_SHORT).show()
   else AlertDialog.Builder(this).setTitle("المفضلة").setItems(saved.toTypedArray()){_,which->browser.loadUrl(saved[which])}.show()
  })
  page.addView(bar)
  browser.settings.javaScriptEnabled=true
  browser.settings.domStorageEnabled=true
  browser.settings.loadsImagesAutomatically=true
  browser.webViewClient=object:WebViewClient() { override fun onPageFinished(view:WebView?,url:String?) { if(url!=null && !address.hasFocus()) address.setText(url) } }
  browser.webChromeClient=WebChromeClient()
  browser.setDownloadListener { url,_,contentDisposition,mimeType,_ ->
   try {
    val request=DownloadManager.Request(Uri.parse(url)).setTitle(contentDisposition ?: "تنزيل من المتصفح").setMimeType(mimeType).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,"browser-download-"+System.currentTimeMillis())
    (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    Toast.makeText(this,"بدأ تنزيل الملف",Toast.LENGTH_SHORT).show()
   } catch(_:Exception){Toast.makeText(this,"تعذر تنزيل الملف",Toast.LENGTH_SHORT).show()}
  }
  page.addView(browser,LinearLayout.LayoutParams(-1,0,1f))
  panel(page)
  browser.loadUrl(start)
  address.setOnEditorActionListener{_,actionId,_->if(actionId==android.view.inputmethod.EditorInfo.IME_ACTION_GO || actionId==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){loadAddress();true}else{loadAddress();true}}
 }
 private fun pickFile(type:String) {
  try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type=type },41) }
  catch(_:Exception) { Toast.makeText(this,"تعذر فتح مستكشف الملفات",Toast.LENGTH_LONG).show() }
 }
 @Deprecated("Uses compatible document picker callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==41 && resultCode==RESULT_OK && data?.data!=null) showFile(data.data!!)
  if(requestCode==71 && resultCode==RESULT_OK && data?.data!=null) { try { val uri=data.data!!; contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); saveImportedPhoto(uri) } catch(_:Exception) { Toast.makeText(this,"تعذر استيراد الصورة",Toast.LENGTH_SHORT).show() } }
  if(requestCode==67) { editorBitmap?.recycle(); editorBitmap=null; refreshPhotoGallery() }
  if(requestCode==62 && resultCode==RESULT_OK && data?.data!=null) { (desktopArea.findViewWithTag<View>("orkeit-paint-canvas") as? PaintCanvas)?.importImage(data.data!!) }
  if(requestCode==61 && resultCode==RESULT_OK && data?.data!=null) { try { val uri=data.data!!;val name=contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else "imported-file"} ?: "imported-file";val dir=fileManagerDir ?: java.io.File(filesDir,"OriketWorkspace").apply{mkdirs()};val dest=java.io.File(dir,name);contentResolver.openInputStream(uri)?.use{input->dest.outputStream().use{input.copyTo(it)}};fileManagerDir=dir;Toast.makeText(this,"تم استيراد الملف",Toast.LENGTH_SHORT).show();showMyComputer()}catch(_:Exception){Toast.makeText(this,"تعذر الاستيراد",Toast.LENGTH_SHORT).show()} }
  if((requestCode==63 || requestCode==64) && resultCode==RESULT_OK && data?.data!=null) {
   try {
    val loaded=contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use{it.readText()} ?: ""
    val tag=if(requestCode==63)"orkeit-notepad-editor" else "orkeit-word-editor"
    val editor=desktopArea.findViewWithTag<EditText>(tag)
    if(editor!=null){editor.setText(loaded);Toast.makeText(this,"تم فتح الملف",Toast.LENGTH_SHORT).show()}
    else Toast.makeText(this,"افتح المفكرة أو Word أولًا",Toast.LENGTH_SHORT).show()
   } catch(_:Exception){Toast.makeText(this,"تعذرت قراءة الملف النصي",Toast.LENGTH_SHORT).show()}
  }
  if(requestCode==72 && resultCode==RESULT_OK && data?.data!=null) {
   val selected=data.data!!
   try { contentResolver.takePersistableUriPermission(selected,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception) {}
   onAudioPickedCallback?.invoke(selected)
  }
  if(requestCode==73 && resultCode==RESULT_OK && data?.data!=null) {
   val selected=data.data!!
   try { contentResolver.takePersistableUriPermission(selected,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception) {}
   onExcelCsvPickedCallback?.invoke(selected)
  }
  if(requestCode==52 && resultCode==RESULT_OK) {
   val selected=data?.data
   if(selected!=null) {
    try { contentResolver.takePersistableUriPermission(selected,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception) {}
    getSharedPreferences("orkeit_video",Context.MODE_PRIVATE).edit().putString("uri",selected.toString()).putInt("position",0).apply()
    val video=desktopArea.findViewWithTag<VideoView>("orkeit-video-view")
    if(video!=null) { video.setVideoURI(selected); video.setOnPreparedListener { player -> player.seekTo(0); video.start() } }
   }
  }
 }
 private fun showFile(uri:Uri) {
  val mime=contentResolver.getType(uri) ?: "application/octet-stream"
  val name=try { contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else "ملف" } ?: "ملف" } catch(_:Exception){"ملف"}
  shell("الملفات")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text="📄 $name"; textSize=14.96f; setTextColor(navy); setPadding(4,4,4,8) })
  val viewer=FrameLayout(this)
  page.addView(viewer,LinearLayout.LayoutParams(-1,0,1f))
  panel(page)
  try {
   when {
    mime.startsWith("image/") -> viewer.addView(ImageView(this).apply { setImageURI(uri); scaleType=ImageView.ScaleType.FIT_CENTER },FrameLayout.LayoutParams(-1,-1))
    mime=="application/pdf" || name.lowercase(Locale.ROOT).endsWith(".pdf") -> {
     val fd=contentResolver.openFileDescriptor(uri,"r")
     if(fd!=null) {
      try {
       val renderer=PdfRenderer(fd)
       if(renderer.pageCount>0) {
        var pageIndex=0
        var zoom=1f
        var closed=false
        val total=renderer.pageCount
        val controls=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        val pdfPageLabel=TextView(this).apply { text="الصفحة 1 / ${total}"; textSize=11.44f; setTextColor(navy); gravity=Gravity.CENTER }
        val previous=task("‹ السابق"){if(pageIndex>0){pageIndex--;renderPdfPage(renderer,pageIndex,zoom,viewer);pdfPageLabel.text="الصفحة ${pageIndex+1} / ${total}"}}
        val next=task("التالي ›"){if(pageIndex<total-1){pageIndex++;renderPdfPage(renderer,pageIndex,zoom,viewer);pdfPageLabel.text="الصفحة ${pageIndex+1} / ${total}"}}
        controls.addView(previous,LinearLayout.LayoutParams(0,42,1f))
        controls.addView(pdfPageLabel,LinearLayout.LayoutParams(0,42,1.2f))
        controls.addView(next,LinearLayout.LayoutParams(0,42,1f))
        controls.addView(task("−"){zoom=(zoom/1.2f).coerceAtLeast(0.5f);renderPdfPage(renderer,pageIndex,zoom,viewer)},LinearLayout.LayoutParams(42,42))
        controls.addView(task("+"){zoom=(zoom*1.2f).coerceAtMost(2.5f);renderPdfPage(renderer,pageIndex,zoom,viewer)},LinearLayout.LayoutParams(42,42))
        page.addView(controls)
        renderPdfPage(renderer,pageIndex,zoom,viewer)
        viewer.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
         override fun onViewAttachedToWindow(v:View){}
         override fun onViewDetachedFromWindow(v:View){if(!closed){closed=true;try{renderer.close()}catch(_:Exception){};try{fd.close()}catch(_:Exception){}}}
        })
       } else { renderer.close(); viewer.addView(message("ملف PDF فارغ")) }
      } catch(_:Exception) { try{fd.close()}catch(_:Exception){}; viewer.addView(message("تعذر عرض ملف PDF")) }
     } else viewer.addView(message("تعذر فتح ملف PDF"))
    }
    mime.startsWith("text/") || name.endsWith(".txt",true) || name.endsWith(".csv",true) || name.endsWith(".json",true) || name.endsWith(".xml",true) -> {
     val txt=contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()} ?: "تعذر قراءة الملف"
     viewer.addView(ScrollView(this).apply { addView(TextView(this@MainActivity).apply { text=txt; textSize=12.32f; setTextColor(Color.DKGRAY); setPadding(12,12,12,12) }) })
    }
    else -> viewer.addView(message("تم اختيار الملف، لكن المعاينة الداخلية لا تدعم هذا النوع بعد."))
   }
  } catch(e:Exception) { viewer.addView(message("تعذرت معاينة الملف داخل التطبيق.")) }
 }
 private fun message(s:String)=TextView(this).apply {
  text=s; textSize=14.96f; setTextColor(navy); gravity=Gravity.CENTER; setPadding(24,22,24,22)
  typeface=Typeface.create("Arial",Typeface.NORMAL); includeFontPadding=true; setLineSpacing(2f,1.08f)
  background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=16f; setStroke(1,border) }
 }
 private fun renderPdfPage(renderer:PdfRenderer,index:Int,zoom:Float,container:FrameLayout) {
  try {
   val pg=renderer.openPage(index)
   val scale=minOf(1.4f,1400f/maxOf(pg.width,pg.height))*zoom
   val bmp=Bitmap.createBitmap((pg.width*scale).toInt().coerceAtLeast(1),(pg.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
   bmp.eraseColor(Color.WHITE)
   pg.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
   pg.close()
   container.removeAllViews()
   container.addView(ScrollView(this).apply { addView(ImageView(this@MainActivity).apply { setImageBitmap(bmp); scaleType=ImageView.ScaleType.FIT_CENTER; adjustViewBounds=true },ViewGroup.LayoutParams(-1,-2)) },FrameLayout.LayoutParams(-1,-1))
  } catch(_:Exception) { container.removeAllViews();container.addView(message("تعذر عرض هذه الصفحة")) }
 }
 private fun showCalculator() {
  shell("الحاسبة")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(8,8,8,8) }
  val display=TextView(this).apply { text="0"; textSize=28.16f; gravity=Gravity.CENTER_VERTICAL or Gravity.END; setTextColor(navy); typeface=Typeface.create("Arial",Typeface.NORMAL); setPadding(16,10,16,10); background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(239,244,251)); cornerRadius=14f; setStroke(1,border) } }
  page.addView(display,LinearLayout.LayoutParams(-1,76))
  val expression=StringBuilder()
  class ExpressionParser(private val source:String) {
   private var p=0
   fun parse():Double { val v=addSub(); if(p<source.length) throw IllegalArgumentException(); return v }
   private fun addSub():Double { var v=mulDiv(); while(p<source.length) { val op=source[p]; if(op!='+'&&op!='-') break; p++; val r=mulDiv(); v=if(op=='+')v+r else v-r }; return v }
   private fun mulDiv():Double { var v=atom(); while(p<source.length) { val op=source[p]; if(op!='×'&&op!='÷'&&op!='*'&&op!='/') break; p++; val r=atom(); v=when(op){'×','*'->v*r;else->{if(r==0.0)throw ArithmeticException();v/r}} }; return v }
   private fun atom():Double { if(p>=source.length)throw IllegalArgumentException(); if(source[p]=='+'||source[p]=='-'){val sign=source[p++];val n=atom();return if(sign=='-')-n else n}; if(source[p]=='('){p++;val n=addSub();if(p>=source.length||source[p]!=')')throw IllegalArgumentException();p++;return n}; val start=p; while(p<source.length&&(source[p].isDigit()||source[p]=='.'))p++; if(start==p)throw IllegalArgumentException(); return source.substring(start,p).toDouble() }
  }
  fun render(){display.text=if(expression.isEmpty())"0" else expression.toString()}
  val grid=GridLayout(this).apply { columnCount=4 }
  listOf("C","⌫","(",")","7","8","9","÷","4","5","6","×","1","2","3","−","%","0",".","+","=","00","±").forEach { key ->
   val b=action(key) {
    when(key) {
     "C"->{expression.clear();render()}
     "⌫"->{if(expression.isNotEmpty())expression.deleteCharAt(expression.lastIndex);render()}
     "="->{try { var src=expression.toString().replace('−','-').replace('÷','÷'); if(src.isNotBlank()){val value=ExpressionParser(src).parse(); if(!value.isFinite())throw ArithmeticException(); expression.clear();expression.append(if(value%1.0==0.0)value.toLong().toString() else String.format(Locale.US,"%.8f",value).trimEnd('0').trimEnd('.'));render()} }catch(_:Exception){display.text="خطأ في العملية";expression.clear()} }
     "%"->{if(expression.isNotEmpty()){expression.append("/100");render()}}
     "±"->{if(expression.isNotEmpty()){expression.insert(0,"-");render()}}
     else->{if(key=="00"&&expression.isEmpty())return@action;expression.append(when(key){"−"->"-";else->key});render()}
    }
   }
   grid.addView(b,GridLayout.LayoutParams().apply { width=0; height=54; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(3,3,3,3) })
  }
  page.addView(grid,LinearLayout.LayoutParams(-1,-2))
  page.addView(action("نسخ النتيجة"){ val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Oriket Calculator",display.text));Toast.makeText(this,"تم نسخ النتيجة",Toast.LENGTH_SHORT).show() })
  panel(page)
 }
 private fun showNotepad() {
  shell("المفكرة")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val notePrefs=getSharedPreferences("orkeit_notepad",Context.MODE_PRIVATE)
  val editor=EditText(this).apply { hint="اكتب ملاحظاتك هنا"; gravity=Gravity.TOP or Gravity.START; textSize=14.08f; tag="orkeit-notepad-editor"; setText(notePrefs.getString("draft","").orEmpty()); addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){notePrefs.edit().putString("draft",s.toString()).apply()}; override fun afterTextChanged(s:android.text.Editable?){} }) }
  page.addView(action("فتح ملف TXT"){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/*"},63)}catch(_:Exception){Toast.makeText(this,"تعذر فتح الملفات",Toast.LENGTH_SHORT).show()}},LinearLayout.LayoutParams(-1,44))
  page.addView(editor,LinearLayout.LayoutParams(-1,0,1f))
  page.addView(action("حفظ TXT") {
   try {
    val values=ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME,"orkeit-note-"+System.currentTimeMillis()+".txt"); put(MediaStore.Downloads.MIME_TYPE,"text/plain") }
    val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)
    if(uri!=null) contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(editor.text.toString())}
    Toast.makeText(this,if(uri!=null)"تم الحفظ في التنزيلات" else "تعذر الحفظ",Toast.LENGTH_LONG).show()
   } catch(_:Exception){Toast.makeText(this,"تعذر الحفظ",Toast.LENGTH_LONG).show()}
  })
  panel(page)
 }

 private fun showMyComputer(initialFolder:String?=null) {
  shell("هذا الكمبيوتر")
  val workspace=java.io.File(filesDir,"OriketWorkspace").apply {
   mkdirs()
   listOf("Documents","Downloads","Pictures","Videos","Projects","Music","Desktop").forEach { java.io.File(this,it).mkdirs() }
  }
  var current:java.io.File?=when {
   initialFolder==null -> null
   initialFolder.isEmpty() -> workspace
   else -> java.io.File(workspace,initialFolder).apply { mkdirs() }
  }
  lateinit var render: () -> Unit
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
  val toolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(7,4,7,4);setBackgroundColor(Color.rgb(246,248,251)) }
  toolbar.addView(task("←"){ if(current!=null){current=current?.parentFile?.takeIf{it.absolutePath.startsWith(workspace.absolutePath + java.io.File.separator)};render()}else showDesktop() },LinearLayout.LayoutParams(44,40))
  toolbar.addView(task("→"){Toast.makeText(this,"لا يوجد مسار تالٍ",Toast.LENGTH_SHORT).show()},LinearLayout.LayoutParams(44,40))
  val address=TextView(this).apply { text="⌂  هذا الكمبيوتر";textSize=12.32f;setTextColor(navy);setPadding(12,8,12,8);background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=5f;setStroke(1,Color.rgb(210,216,224))} }
  toolbar.addView(address,LinearLayout.LayoutParams(0,40,1f))
  val fileSearch=EditText(this).apply {
   hint="بحث في المجلد"
   textSize=10.56f
   setSingleLine(true)
   setTextColor(navy)
   setPadding(9,0,9,0)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=7f; setStroke(1,Color.rgb(220,226,234)) }
  }
  toolbar.addView(fileSearch,LinearLayout.LayoutParams(150,40).apply{leftMargin=5})
  toolbar.addView(task("⟳"){render()},LinearLayout.LayoutParams(44,40))
  page.addView(toolbar)
  val content=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  val sidebar=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(6,8,6,8);setBackgroundColor(Color.rgb(249,250,252)) }
  val main=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(14,10,14,8) }
  content.addView(sidebar,LinearLayout.LayoutParams(150,-1))
  content.addView(ScrollView(this).apply{addView(main)},LinearLayout.LayoutParams(0,-1,1f))
  page.addView(content,LinearLayout.LayoutParams(-1,0,1f))
  fun sectionTitle(text:String) {
   main.addView(TextView(this).apply{this.text=text;textSize=14.96f;typeface=Typeface.create("Arial",Typeface.BOLD);setTextColor(Color.rgb(30,42,60));setPadding(3,8,3,8)})
  }
  fun folderTile(label:String,icon:String,folder:String) {
   val f=java.io.File(workspace,folder).apply{mkdirs()}
   val tile=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(10,5,10,5);background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=8f;setStroke(1,Color.rgb(229,233,239))};isClickable=true;setOnClickListener{current=f;render()}}
   val folderIcon=when(folder){
    "Documents"->FluentIcon.FOLDER_DOCUMENTS
    "Downloads"->FluentIcon.FOLDER_DOWNLOADS
    "Pictures"->FluentIcon.FOLDER_PICTURES
    "Videos"->FluentIcon.FOLDER_VIDEOS
    "Music"->FluentIcon.FOLDER_MUSIC
    "Desktop"->FluentIcon.FOLDER_DESKTOP
    "Projects"->FluentIcon.FOLDER_PROJECTS
    else->FluentIcon.FOLDER
   }
   val fIcon=ImageView(this).apply {
    val dIcon=FluentIcons.createDrawable(this@MainActivity,folderIcon,(38*resources.displayMetrics.density).toInt())
    setImageDrawable(dIcon)
   }
   tile.addView(fIcon,LinearLayout.LayoutParams((45*resources.displayMetrics.density).toInt(),(48*resources.displayMetrics.density).toInt()))
   tile.addView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL
    addView(TextView(this@MainActivity).apply{text=label;textSize=11.44f;setTextColor(navy);typeface=Typeface.create("Arial",Typeface.BOLD)})
    addView(TextView(this@MainActivity).apply{text=if(f.listFiles().isNullOrEmpty()) "مجلد" else "العناصر: ${f.listFiles()?.size ?: 0}";textSize=8.8f;setTextColor(Color.GRAY)})
   },LinearLayout.LayoutParams(0,48,1f))
   main.addView(tile,LinearLayout.LayoutParams(-1,58).apply{bottomMargin=5})
  }
  fun driveTile(title:String,sub:String,driveIcon:FluentIcon,onClick:()->Unit) {
   val tile=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(10,5,10,5);background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=8f;setStroke(1,Color.rgb(220,226,234))};isClickable=true;setOnClickListener{onClick()}}
   val dIcon=ImageView(this).apply {
    val d=FluentIcons.createDrawable(this@MainActivity,driveIcon,(42*resources.displayMetrics.density).toInt())
    setImageDrawable(d)
   }
   tile.addView(dIcon,LinearLayout.LayoutParams((52*resources.displayMetrics.density).toInt(),(54*resources.displayMetrics.density).toInt()))
   tile.addView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL
    addView(TextView(this@MainActivity).apply{text=title;textSize=12.32f;setTextColor(navy);typeface=Typeface.create("Arial",Typeface.BOLD)})
    addView(TextView(this@MainActivity).apply{text=sub;textSize=8.8f;setTextColor(Color.GRAY)})
    val track=ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply{progress=if(title.contains("الهاتف"))45 else 22}
    addView(track,LinearLayout.LayoutParams(-1,5).apply{topMargin=4})
   },LinearLayout.LayoutParams(0,54,1f))
   main.addView(tile,LinearLayout.LayoutParams(-1,64).apply{bottomMargin=7})
  }
  render = {
   address.text=if(current==null)"⌂  هذا الكمبيوتر" else "⌂  هذا الكمبيوتر  ›  "+current!!.name
   sidebar.removeAllViews();main.removeAllViews()
   sidebar.addView(TextView(this).apply{text="الوصول السريع";textSize=10.56f;typeface=Typeface.create("Arial",Typeface.BOLD);setTextColor(Color.GRAY);setPadding(5,4,0,7)})
   val sideItems=listOf(
    Triple("سطح المكتب",FluentIcon.FOLDER_DESKTOP,"Desktop"),
    Triple("التنزيلات",FluentIcon.FOLDER_DOWNLOADS,"Downloads"),
    Triple("المستندات",FluentIcon.FOLDER_DOCUMENTS,"Documents"),
    Triple("الصور",FluentIcon.FOLDER_PICTURES,"Pictures"),
    Triple("الفيديو",FluentIcon.FOLDER_VIDEOS,"Videos"),
    Triple("الموسيقى",FluentIcon.FOLDER_MUSIC,"Music"),
    Triple("هذا الكمبيوتر",FluentIcon.THIS_PC,"")
   )
   sideItems.forEach { (label,icon,folder) ->
    val b=task("  "+label){
     current=if(folder.isNotEmpty()) java.io.File(workspace,folder) else null
     render()
    }
    val d=FluentIcons.createDrawable(this@MainActivity,icon,(18*resources.displayMetrics.density).toInt())
    b.setCompoundDrawablesWithIntrinsicBounds(d,null,null,null)
    b.gravity=Gravity.CENTER_VERTICAL or Gravity.START
    b.setPadding((8*resources.displayMetrics.density).toInt(),0,(8*resources.displayMetrics.density).toInt(),0)
    sidebar.addView(b,LinearLayout.LayoutParams(-1,38).apply{bottomMargin=2})
   }
   if(current==null) {
    sectionTitle("المجلدات")
    listOf("سطح المكتب" to "Desktop","التنزيلات" to "Downloads","المستندات" to "Documents","الصور" to "Pictures","الفيديو" to "Videos","الموسيقى" to "Music","المشاريع" to "Projects").forEach{folderTile(it.first,"📁",it.second)}
    sectionTitle("الأجهزة ومحركات الأقراص")
    driveTile("القرص المحلي (C:)","مساحة ملفات Oriket الداخلية",FluentIcon.HARD_DRIVE){current=workspace;render()}
    driveTile("التخزين الداخلي للهاتف","فتح ملفات الهاتف واستيرادها",FluentIcon.SMARTPHONE){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*"},61)}catch(_:Exception){}}
    main.addView(TextView(this).apply{text="ملاحظة: محركات الأقراص المعروضة واجهة تنظيمية؛ Android لا يتيح للتطبيق إدارة أقراص Windows الفعلية.";textSize=8.8f;setTextColor(Color.GRAY);setPadding(3,8,3,4)})
   } else {
    main.addView(TextView(this).apply{text=current!!.absolutePath;textSize=9.68f;setTextColor(Color.GRAY);setPadding(2,2,2,8)})
    main.addView(LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL
     addView(task("＋ مجلد جديد"){val input=EditText(this@MainActivity).apply{hint="اسم المجلد"};AlertDialog.Builder(this@MainActivity).setTitle("مجلد جديد").setView(input).setPositiveButton("إنشاء"){_,_->val n=input.text.toString().trim();if(n.isNotEmpty()&&!n.contains("/")&&java.io.File(current,n).mkdir())render()else Toast.makeText(this@MainActivity,"اسم غير صالح أو موجود",Toast.LENGTH_SHORT).show()}.setNegativeButton("إلغاء",null).show()},LinearLayout.LayoutParams(0,40,1f))
     addView(task("استيراد ملف"){fileManagerDir=current;try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*"},61)}catch(_:Exception){}},LinearLayout.LayoutParams(0,40,1f))
    })
    val allEntries=current!!.listFiles()?.sortedWith(compareBy<java.io.File>({!it.isDirectory},{it.name.lowercase(Locale.ROOT)})).orEmpty()
    val query=fileSearch.text.toString().trim()
    val entries=allEntries.filter { query.isEmpty() || it.name.contains(query,true) }
    if(entries.isEmpty())main.addView(message(if(query.isEmpty())"هذا المجلد فارغ" else "لا توجد نتائج مطابقة"))
    entries.forEach{f->
     val row=LinearLayout(this).apply {
      orientation=LinearLayout.HORIZONTAL
      gravity=Gravity.CENTER_VERTICAL
      background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=6f;setStroke(1,Color.rgb(237,240,245))}
      val fileIcon=when {
       f.isDirectory -> FluentIcon.FOLDER
       f.name.endsWith(".txt",true) || f.name.endsWith(".doc",true) || f.name.endsWith(".docx",true) -> FluentIcon.NOTEPAD
       f.name.endsWith(".pdf",true) -> FluentIcon.PDF_READER
       f.name.endsWith(".jpg",true) || f.name.endsWith(".png",true) || f.name.endsWith(".webp",true) -> FluentIcon.PHOTOS
       f.name.endsWith(".mp4",true) || f.name.endsWith(".mkv",true) -> FluentIcon.VIDEOS
       f.name.endsWith(".mp3",true) || f.name.endsWith(".wav",true) -> FluentIcon.MUSIC
       f.name.endsWith(".xls",true) || f.name.endsWith(".xlsx",true) -> FluentIcon.SPREADSHEET
       else -> FluentIcon.NOTEPAD
      }
      val fBtn=task("  "+f.name){if(f.isDirectory){current=f;fileSearch.setText("");render()}else showFile(Uri.fromFile(f))}
      val fDrawable=FluentIcons.createDrawable(this@MainActivity,fileIcon,(22*resources.displayMetrics.density).toInt())
      fBtn.setCompoundDrawablesWithIntrinsicBounds(fDrawable,null,null,null)
      fBtn.gravity=Gravity.CENTER_VERTICAL or Gravity.START
      fBtn.setPadding((8*resources.displayMetrics.density).toInt(),0,(8*resources.displayMetrics.density).toInt(),0)
      addView(fBtn,LinearLayout.LayoutParams(0,42,1f))
      lateinit var more:Button
      more=task("⋯"){
       android.widget.PopupMenu(this@MainActivity,more).apply {
        menu.add("فتح").setOnMenuItemClickListener { if(f.isDirectory){current=f;render()}else showFile(Uri.fromFile(f));true }
        menu.add("إعادة تسمية").setOnMenuItemClickListener {
         val edit=EditText(this@MainActivity).apply{setText(f.name);selectAll()}
         AlertDialog.Builder(this@MainActivity).setTitle("إعادة تسمية").setView(edit).setPositiveButton("حفظ"){_,_->
          val name=edit.text.toString().trim()
          if(name.isNotEmpty()&&!name.contains("/")&&java.io.File(f.parentFile,name).exists().not()) { if(f.renameTo(java.io.File(f.parentFile,name)))render() else Toast.makeText(this@MainActivity,"تعذرت إعادة التسمية",Toast.LENGTH_SHORT).show() }
         }.setNegativeButton("إلغاء",null).show()
         true
        }
        menu.add("حذف").setOnMenuItemClickListener {
         val doRecycle={
          if(RecycleBinStorage.moveFileToRecycleBin(this@MainActivity,f)) {
           render()
           Toast.makeText(this@MainActivity,"نُقل الملف إلى سلة المحذوفات",Toast.LENGTH_SHORT).show()
          } else Toast.makeText(this@MainActivity,"تعذر نقل الملف إلى السلة",Toast.LENGTH_SHORT).show()
         }
         if(RecycleBinStorage.isConfirmDeleteEnabled(this@MainActivity)) {
          AlertDialog.Builder(this@MainActivity).setTitle("نقل إلى سلة المحذوفات؟")
           .setMessage("هل تريد نقل ${f.name} إلى سلة المحذوفات؟")
           .setPositiveButton("نقل إلى السلة") { _,_ -> doRecycle() }
           .setNeutralButton("حذف نهائي") { _,_ ->
            val ok=if(f.isDirectory) f.deleteRecursively() else f.delete()
            if(ok) render() else Toast.makeText(this@MainActivity,"تعذر الحذف",Toast.LENGTH_SHORT).show()
           }
           .setNegativeButton("إلغاء",null).show()
         } else doRecycle()
         true
        }
       }.show()
      }
      addView(more,LinearLayout.LayoutParams(42,42))
     }
     main.addView(row,LinearLayout.LayoutParams(-1,44).apply{bottomMargin=3})
    }
   }
  }
  fileSearch.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { render() }
   override fun afterTextChanged(s:android.text.Editable?) {}
  })
  panel(page)
  render()
 }
 private fun showControlPanel() {
  shell("الإعدادات")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(6,4,6,4) }
  page.addView(TextView(this).apply {
   text="الإعدادات"; textSize=21f; typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy); setPadding(8,2,8,2)
  })
  page.addView(TextView(this).apply {
   text="اضبط جهازك وتجربة Oriket Desktop"; textSize=11f; setTextColor(Color.rgb(105,118,138)); setPadding(8,0,8,10)
  })
  val search=EditText(this).apply {
   hint="بحث عن إعداد"; textSize=11.5f; setSingleLine(true); setPadding(13,2,13,2); setTextColor(navy)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=18f; setStroke(1,border) }
  }
  page.addView(search,LinearLayout.LayoutParams(-1,42).apply { bottomMargin=8 })
  val groups=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val entries=listOf(
   arrayOf("📶","الشبكة والإنترنت","Wi-Fi وبيانات الاتصال",android.provider.Settings.ACTION_WIFI_SETTINGS),
   arrayOf("ᛒ","Bluetooth","الأجهزة والاتصال اللاسلكي",android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
   arrayOf("🖥️","الشاشة والسطوع","السطوع وحجم العرض",android.provider.Settings.ACTION_DISPLAY_SETTINGS),
   arrayOf("🔊","الصوت","مستوى الصوت ونغمات النظام",android.provider.Settings.ACTION_SOUND_SETTINGS),
   arrayOf("🔋","البطارية","استهلاك الطاقة ووضع التوفير",android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS),
   arrayOf("▣","الإشعارات","إدارة إشعارات Oriket Desktop",android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS),
   arrayOf("◷","التاريخ والوقت","الوقت والمنطقة الزمنية",android.provider.Settings.ACTION_DATE_SETTINGS),
   arrayOf("📱","التطبيقات","معلومات Oriket Desktop وأذوناته",android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
   arrayOf("🎨","الخلفية والتخصيص","تغيير خلفية سطح المكتب", "orkeit-wallpaper"),
   arrayOf("🔒","الخصوصية","سياسة الخصوصية", "orkeit-privacy"),
   arrayOf("ℹ️","حول التطبيق","الإصدار ومعلومات التطبيق", "orkeit-about")
  )
  val rows=mutableListOf<Pair<View,String>>()
  entries.forEach { item ->
   val card=LinearLayout(this).apply {
    orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(10,5,10,5)
    background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=12f; setStroke(1,border) }
    isClickable=true; isFocusable=true
    setOnClickListener {
     when(item[3]) {
      "orkeit-wallpaper" -> showWallpaperPicker()
      "orkeit-privacy" -> showPrivacyPolicy()
      "orkeit-about" -> showAbout()
      android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS -> try {
       startActivity(Intent(item[3]).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName))
      } catch(_:Exception) { Toast.makeText(this@MainActivity,"تعذر فتح إعدادات الإشعارات",Toast.LENGTH_SHORT).show() }
      android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> try {
       startActivity(Intent(item[3],Uri.parse("package:$packageName")))
      } catch(_:Exception) { Toast.makeText(this@MainActivity,"تعذر فتح معلومات التطبيق",Toast.LENGTH_SHORT).show() }
      else -> try { startActivity(Intent(item[3])) }
       catch(_:Exception) { Toast.makeText(this@MainActivity,"هذا الإعداد غير متاح على هذا الجهاز",Toast.LENGTH_SHORT).show() }
     }
    }
   }
   val cIcon=ImageView(this).apply {
    val fIcon=when(item[1]) {
     "الشبكة والإنترنت" -> FluentIcon.WIFI
     "Bluetooth" -> FluentIcon.BLUETOOTH
     "الشاشة والسطوع" -> FluentIcon.THIS_PC
     "الصوت" -> FluentIcon.MUSIC
     "البطارية" -> FluentIcon.BATTERY
     "الإشعارات" -> FluentIcon.NOTIFICATIONS
     "التاريخ والوقت" -> FluentIcon.DATETIME
     "التطبيقات" -> FluentIcon.APPS
     "الخلفية والتخصيص" -> FluentIcon.WALLPAPER
     "الخصوصية" -> FluentIcon.SECURITY
     "حول التطبيق" -> FluentIcon.ABOUT
     else -> FluentIcon.SETTINGS
    }
    setImageDrawable(FluentIcons.createDrawable(this@MainActivity,fIcon,(28*resources.displayMetrics.density).toInt()))
   }
   card.addView(cIcon,LinearLayout.LayoutParams((38*resources.displayMetrics.density).toInt(),(42*resources.displayMetrics.density).toInt()))
   val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL }
   labels.addView(TextView(this).apply { text=item[1]; textSize=12f; typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy) })
   labels.addView(TextView(this).apply { text=item[2]; textSize=9.5f; setTextColor(Color.rgb(112,124,142)); setPadding(0,2,0,0) })
   card.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
   card.addView(TextView(this).apply { text="‹"; textSize=22f; setTextColor(Color.rgb(125,140,160)); gravity=Gravity.CENTER },LinearLayout.LayoutParams(24,44))
   rows.add(card to (item[1]+" "+item[2]))
  }
  rows.forEach { groups.addView(it.first,LinearLayout.LayoutParams(-1,58).apply { bottomMargin=6 }) }
  page.addView(ScrollView(this).apply { isFillViewport=true; addView(groups) },LinearLayout.LayoutParams(-1,0,1f))
  search.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
    val q=s.toString().trim()
    rows.forEach { (view,label) -> view.visibility=if(q.isEmpty() || label.contains(q,true)) View.VISIBLE else View.GONE }
   }
   override fun afterTextChanged(s:android.text.Editable?) {}
  })
  panel(page)
 }

 private fun showTaskManager() {
  shell("إدارة المهام")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text="إدارة المهام"; textSize=19.36f; typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy); setPadding(6,4,6,8) })
  val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  fun refresh() {
   list.removeAllViews()
   if(openWindows.isEmpty()) list.addView(message("لا توجد نوافذ مفتوحة"))
   openWindows.toList().forEach { entry ->
    val row=LinearLayout(this@MainActivity).apply { gravity=Gravity.CENTER_VERTICAL }
    val tIcon=ImageView(this@MainActivity).apply {
     setImageDrawable(FluentIcons.createDrawable(this@MainActivity,FluentIcons.getIconForTitle(entry.title),(24*resources.displayMetrics.density).toInt()))
    }
    row.addView(tIcon,LinearLayout.LayoutParams((32*resources.displayMetrics.density).toInt(),(32*resources.displayMetrics.density).toInt()))
    row.addView(TextView(this@MainActivity).apply { text="  "+entry.title; setTextColor(navy); textSize=12.32f; gravity=Gravity.CENTER_VERTICAL },LinearLayout.LayoutParams(0,44,1f))
    row.addView(task("إنهاء المهمة"){ if(entry.id!=openWindows.firstOrNull{it.title=="إدارة المهام"}?.id) { desktopArea.removeView(entry.frame); taskbar.removeView(entry.taskButton); openWindows.removeAll{it.id==entry.id}; refresh() } })
    list.addView(row)
   }
  }
  page.addView(action("تحديث القائمة"){refresh()})
  page.addView(ScrollView(this).apply { addView(list) },LinearLayout.LayoutParams(-1,0,1f))
  panel(page); refresh()
 }
 private fun showCommand() {
  shell("Command")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(12,18,24)); setPadding(8,8,8,8) }
  val output=TextView(this).apply { text="Oriket Command [محاكاة أوامر داخل التطبيق]\nاكتب help لعرض الأوامر.\n\nC:\\Users\\Oriket>"; typeface=Typeface.MONOSPACE; textSize=11.44f; setTextColor(Color.rgb(160,245,170)); setPadding(4,4,4,4) }
  val scroll=ScrollView(this).apply { addView(output) }
  val input=EditText(this).apply { hint="اكتب أمرًا مثل help أو dir"; setSingleLine(true); typeface=Typeface.MONOSPACE; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); setBackgroundColor(Color.rgb(30,38,46)) }
  page.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); page.addView(input,LinearLayout.LayoutParams(-1,48))
  input.setOnEditorActionListener { _,_,_ ->
   val cmd=input.text.toString().trim(); input.setText("")
   val result=when {
    cmd.equals("help",true)->"help  dir  cls  ver  date  time  whoami  echo <text>  calc"
    cmd.equals("dir",true)->" Directory of C:\\Users\\Oriket\n Desktop   Documents   Downloads   Pictures   Videos"
    cmd.equals("cls",true)->{output.text=""; ""}
    cmd.equals("ver",true)->"Oriket Desktop [Windows 11-inspired shell]"
    cmd.equals("date",true)->java.text.SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(java.util.Date())
    cmd.equals("time",true)->java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(java.util.Date())
    cmd.equals("whoami",true)->"Oriket\\User"
    cmd.startsWith("echo ",true)->cmd.substringAfter(" ")
    cmd.equals("calc",true)->"افتح تطبيق الحاسبة من قائمة ابدأ."
    else->"الأمر غير معروف. اكتب help. (لا يتم تنفيذ أوامر Windows الحقيقية على Android)"
   }
   if(!cmd.equals("cls",true)) output.append("\n"+result)
   output.append("\n\nC:\\Users\\Oriket>")
   scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}
   true
  }
  panel(page)
 }
 private fun showYouTube() { showBrowser("https://www.youtube.com", "YouTube") }

 private fun showYouTubeMusic() { showBrowser("https://music.youtube.com", "YouTube Music") }

 private fun showMusicPlayer() {
  shell("Windows Media Player")
  val d=resources.displayMetrics.density
  val prefs=getSharedPreferences("orkeit_music",Context.MODE_PRIVATE)
  val songs=mutableListOf<Uri>()
  val titles=mutableListOf<String>()
  val artists=mutableListOf<String>()
  var index=0
  var shuffle=prefs.getBoolean("shuffle",false)
  var repeat=prefs.getBoolean("repeat",false)
  var volumeLevel=prefs.getFloat("volume",0.85f).coerceIn(0f,1f)
  var muted=false
  var playbackSpeed=1.0f
  var prepared=false
  var userSeeking=false
  var showOverlayInfo=true
  var activeTab=0 // 0=Now Playing, 1=Library, 2=Queue, 3=Effects
  val navHistory=mutableListOf(0)
  var navPointer=0

  // Theme the shell window in classic WMP 11 dark Aero obsidian glass
  content.setPadding(0,0,0,0)
  content.setBackgroundColor(Color.rgb(6,12,24))
  val outerWindow=content.parent as? LinearLayout
  val windowTitleBar=outerWindow?.getChildAt(0) as? LinearLayout
  val windowMaximizeBtn=windowTitleBar?.getChildAt(3) as? TextView
  val windowTitleLabel=windowTitleBar?.getChildAt(1) as? TextView
  outerWindow?.background=android.graphics.drawable.GradientDrawable().apply {
   setColor(Color.rgb(7,12,22))
   cornerRadius=10f*d
   setStroke((1.2f*d).toInt().coerceAtLeast(1),Color.rgb(58,78,108))
  }
  windowTitleBar?.apply {
   background=Wmp11Style.darkAeroBarBackground()
   (getChildAt(1) as? TextView)?.setTextColor(Color.rgb(235,244,255))
   (getChildAt(2) as? TextView)?.apply {
    setTextColor(Color.rgb(220,232,248))
    background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(36,255,255,255)); cornerRadius=4f*d }
   }
   (getChildAt(3) as? TextView)?.apply {
    setTextColor(Color.rgb(220,232,248))
    background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(36,255,255,255)); cornerRadius=4f*d }
   }
   (getChildAt(4) as? TextView)?.apply {
    setTextColor(Color.WHITE)
    background=android.graphics.drawable.GradientDrawable(
     android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
     intArrayOf(Color.rgb(218,86,80),Color.rgb(162,40,38))
    ).apply { cornerRadius=4f*d; setStroke(1,Color.argb(120,255,190,185)) }
   }
  }
  (content.getChildAt(0) as? TextView)?.setTextColor(Color.argb(160,130,175,230))

  val page=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_LTR
   setBackgroundColor(Color.rgb(6,12,24))
  }

  // 1. Top WMP 11 Aero Tab Strip (Back/Forward orbs + Now Playing / Library / Queue / Effects + Brand badge)
  val topNavBar=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding((8*d).toInt(),(3*d).toInt(),(8*d).toInt(),0)
   background=Wmp11Style.darkAeroBarBackground()
  }
  val navOrbCapsule=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((3*d).toInt(),(2*d).toInt(),(3*d).toInt(),(2*d).toInt())
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.rgb(12,18,30)); cornerRadius=14f*d; setStroke(1,Color.rgb(64,84,114))
   }
  }
  lateinit var backNavBtn:TextView
  lateinit var fwdNavBtn:TextView
  lateinit var switchToTab:(Int,Boolean)->Unit
  backNavBtn=TextView(this).apply {
   text="←"; textSize=12f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
   typeface=Typeface.create("Arial",Typeface.BOLD)
   background=Wmp11Style.navOrbBackground(d,false)
   contentDescription="رجوع"
   setOnClickListener {
    if(navPointer>0) { navPointer--; switchToTab(navHistory[navPointer],false) }
   }
  }
  fwdNavBtn=TextView(this).apply {
   text="→"; textSize=12f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
   typeface=Typeface.create("Arial",Typeface.BOLD)
   background=Wmp11Style.navOrbBackground(d,false)
   contentDescription="تقديم"
   setOnClickListener {
    if(navPointer<navHistory.size-1) { navPointer++; switchToTab(navHistory[navPointer],false) }
   }
  }
  navOrbCapsule.addView(backNavBtn,LinearLayout.LayoutParams((22*d).toInt(),(22*d).toInt()).apply { rightMargin=(3*d).toInt() })
  navOrbCapsule.addView(fwdNavBtn,LinearLayout.LayoutParams((22*d).toInt(),(22*d).toInt()))
  topNavBar.addView(navOrbCapsule,LinearLayout.LayoutParams(-2,(26*d).toInt()).apply { rightMargin=(10*d).toInt() })

  val tabStrip=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.BOTTOM or Gravity.START }
  val tabNames=listOf("Now Playing","Library","Queue","Effects")
  val tabViews=mutableListOf<TextView>()
  tabNames.forEachIndexed { idx,label ->
   val tabBtn=TextView(this).apply {
    text=if(idx==0) "$label ▾" else label
    textSize=10.5f; gravity=Gravity.CENTER
    setTextColor(if(idx==0) Color.WHITE else Color.rgb(185,202,226))
    typeface=Typeface.create("Arial",if(idx==0) Typeface.BOLD else Typeface.NORMAL)
    setPadding((10*d).toInt(),(3*d).toInt(),(10*d).toInt(),(4*d).toInt())
    background=if(idx==0) Wmp11Style.activeTabBackground(d) else Wmp11Style.inactiveTabBackground(d)
    maxLines=1
    setOnClickListener { switchToTab(idx,true) }
   }
   tabViews.add(tabBtn)
   tabStrip.addView(tabBtn,LinearLayout.LayoutParams(-2,(27*d).toInt()).apply { rightMargin=(3*d).toInt() })
  }
  topNavBar.addView(HorizontalScrollView(this).apply {
   isHorizontalScrollBarEnabled=false
   addView(tabStrip)
  },LinearLayout.LayoutParams(0,(30*d).toInt(),1f))

  val openFileQuick=TextView(this).apply {
   text="📂"; textSize=12.5f; gravity=Gravity.CENTER
   setTextColor(Color.rgb(188,224,255))
   contentDescription="فتح ملف صوتي"
   setPadding((6*d).toInt(),0,(6*d).toInt(),0)
   background=Wmp11Style.podButtonBackground(d,false)
  }
  val brandWord=TextView(this).apply {
   text="ORIKET"; textSize=10.5f; gravity=Gravity.CENTER
   typeface=Typeface.create("Arial",Typeface.BOLD)
   letterSpacing=0.12f
   setTextColor(Color.rgb(212,226,245))
   setPadding((8*d).toInt(),0,(4*d).toInt(),0)
  }
  topNavBar.addView(openFileQuick,LinearLayout.LayoutParams((30*d).toInt(),(24*d).toInt()).apply { leftMargin=(6*d).toInt() })
  topNavBar.addView(brandWord,LinearLayout.LayoutParams(-2,(24*d).toInt()))
  page.addView(topNavBar,LinearLayout.LayoutParams(-1,(34*d).toInt()))
  page.addView(View(this).apply { setBackgroundColor(Color.rgb(54,88,136)) },LinearLayout.LayoutParams(-1,(1*d).toInt().coerceAtLeast(1)))

  // 2. Main Visual & Content Stage
  val stage=FrameLayout(this).apply { setBackgroundColor(Color.rgb(2,10,26)) }
  val waveView=Wmp11WaveCanvasView(this)
  stage.addView(waveView,FrameLayout.LayoutParams(-1,-1))

  // Subtle Now-Playing HUD overlay over the luminous blue wave stage (tap canvas to toggle clean mode)
  val nowPlayingOverlay=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; gravity=Gravity.BOTTOM or Gravity.START
   setPadding((14*d).toInt(),(10*d).toInt(),(14*d).toInt(),(12*d).toInt())
  }
  val overlayTitle=TextView(this).apply {
   text="Windows Media Player"; textSize=15f; typeface=Typeface.create("Arial",Typeface.BOLD)
   setTextColor(Color.argb(235,245,250,255))
   setShadowLayer(4f*d,0f,1f*d,Color.argb(200,0,8,24))
   maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
  }
  val overlaySubtitle=TextView(this).apply {
   text="اضغط «تحميل الموسيقى» أو «📂» لاختيار أغاني الجهاز"; textSize=11f
   setTextColor(Color.argb(215,176,225,255))
   setShadowLayer(3f*d,0f,1f*d,Color.argb(180,0,8,24))
   maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
   setPadding(0,(2*d).toInt(),0,(8*d).toInt())
  }
  val quickActionsOverlay=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  val scanOverlayBtn=TextView(this).apply {
   text="♪ تحميل موسيقى الجهاز"; textSize=10.5f; gravity=Gravity.CENTER
   setTextColor(Color.WHITE); typeface=Typeface.create("Arial",Typeface.BOLD)
   setPadding((12*d).toInt(),(6*d).toInt(),(12*d).toInt(),(6*d).toInt())
   background=Wmp11Style.podButtonBackground(d,true)
  }
  val pickFileOverlayBtn=TextView(this).apply {
   text="📂 فتح ملف صوتي"; textSize=10.5f; gravity=Gravity.CENTER
   setTextColor(Color.rgb(225,240,255))
   setPadding((12*d).toInt(),(6*d).toInt(),(12*d).toInt(),(6*d).toInt())
   background=Wmp11Style.podButtonBackground(d,false)
  }
  quickActionsOverlay.addView(scanOverlayBtn,LinearLayout.LayoutParams(-2,(30*d).toInt()).apply { rightMargin=(8*d).toInt() })
  quickActionsOverlay.addView(pickFileOverlayBtn,LinearLayout.LayoutParams(-2,(30*d).toInt()))
  nowPlayingOverlay.addView(overlayTitle)
  nowPlayingOverlay.addView(overlaySubtitle)
  nowPlayingOverlay.addView(quickActionsOverlay)
  stage.addView(nowPlayingOverlay,FrameLayout.LayoutParams(-1,-1))

  waveView.setOnClickListener {
   showOverlayInfo=!showOverlayInfo
   nowPlayingOverlay.animate().alpha(if(showOverlayInfo) 1f else 0f).setDuration(180).withEndAction {
    nowPlayingOverlay.visibility=if(showOverlayInfo) View.VISIBLE else View.GONE
   }.start()
  }

  // Library / Queue / Effects Translucent Dark Aero Overlay Panel
  val secondaryPanel=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; visibility=View.GONE
   setPadding((12*d).toInt(),(10*d).toInt(),(12*d).toInt(),(10*d).toInt())
   setBackgroundColor(Color.argb(214,7,15,30))
  }
  val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; tag="music-list" }
  val queueList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val libraryToolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  val searchSongsInput=EditText(this).apply {
   hint="ابحث في المكتبة..."; setHintTextColor(Color.rgb(138,162,196))
   setTextColor(Color.WHITE); textSize=11.5f; setSingleLine(true)
   setPadding((10*d).toInt(),(4*d).toInt(),(10*d).toInt(),(4*d).toInt())
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.argb(190,14,26,46)); cornerRadius=8f*d; setStroke(1,Color.rgb(66,98,142))
   }
  }
  val scanLibBtn=TextView(this).apply {
   text="↻ مسح الجهاز"; textSize=10.5f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
   typeface=Typeface.create("Arial",Typeface.BOLD)
   setPadding((10*d).toInt(),0,(10*d).toInt(),0)
   background=Wmp11Style.podButtonBackground(d,true)
  }
  val pickLibBtn=TextView(this).apply {
   text="＋ ملف صوتي"; textSize=10.5f; gravity=Gravity.CENTER; setTextColor(Color.rgb(225,240,255))
   setPadding((10*d).toInt(),0,(10*d).toInt(),0)
   background=Wmp11Style.podButtonBackground(d,false)
  }
  libraryToolbar.addView(searchSongsInput,LinearLayout.LayoutParams(0,(30*d).toInt(),1f).apply { rightMargin=(6*d).toInt() })
  libraryToolbar.addView(scanLibBtn,LinearLayout.LayoutParams(-2,(30*d).toInt()).apply { rightMargin=(6*d).toInt() })
  libraryToolbar.addView(pickLibBtn,LinearLayout.LayoutParams(-2,(30*d).toInt()))

  val libraryScroll=ScrollView(this).apply {
   isFillViewport=true
   addView(list)
  }
  val queueScroll=ScrollView(this).apply {
   isFillViewport=true; visibility=View.GONE
   addView(queueList)
  }
  val effectsContainer=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; visibility=View.GONE
   setPadding((6*d).toInt(),(4*d).toInt(),(6*d).toInt(),(4*d).toInt())
  }
  secondaryPanel.addView(libraryToolbar,LinearLayout.LayoutParams(-1,(34*d).toInt()).apply { bottomMargin=(8*d).toInt() })
  secondaryPanel.addView(libraryScroll,LinearLayout.LayoutParams(-1,0,1f))
  secondaryPanel.addView(queueScroll,LinearLayout.LayoutParams(-1,0,1f))
  secondaryPanel.addView(effectsContainer,LinearLayout.LayoutParams(-1,0,1f))
  stage.addView(secondaryPanel,FrameLayout.LayoutParams(-1,-1))

  page.addView(stage,LinearLayout.LayoutParams(-1,0,1f))

  // 3. Thin Luminous Cyan SeekBar Strip (matches bottom edge of canvas in WMP 11 screenshot)
  val seekBar=SeekBar(this).apply {
   setPadding((6*d).toInt(),0,(6*d).toInt(),0)
   setBackgroundColor(Color.rgb(10,18,32))
   progressTintList=android.content.res.ColorStateList.valueOf(Color.rgb(52,188,255))
   progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(28,44,70))
   thumbTintList=android.content.res.ColorStateList.valueOf(Color.rgb(122,222,255))
  }
  page.addView(seekBar,LinearLayout.LayoutParams(-1,(16*d).toInt()))

  // 4. Bottom WMP 11 Aero Control Deck
  val bottomDeck=FrameLayout(this).apply {
   background=Wmp11Style.darkAeroBarBackground()
  }
  val deckRow=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding((10*d).toInt(),(4*d).toInt(),(10*d).toInt(),(4*d).toInt())
  }

  // Left: Mini WMP wave thumb + Track title + Timestamp
  val miniThumbFrame=FrameLayout(this).apply {
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.rgb(4,16,38)); cornerRadius=3f*d; setStroke(1,Color.rgb(125,152,190))
   }
   setPadding(1,1,1,1)
   addView(Wmp11WaveCanvasView(this@MainActivity).apply { isMiniThumbnail=true },FrameLayout.LayoutParams(-1,-1))
  }
  val trackMetaBox=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL }
  val deckTrackTitle=TextView(this).apply {
   text="Oriket Media"; textSize=10.5f; typeface=Typeface.create("Arial",Typeface.BOLD)
   setTextColor(Color.rgb(236,244,255)); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
  }
  val deckTimeLabel=TextView(this).apply {
   text="00:00 / 00:00"; textSize=9.5f; setTextColor(Color.rgb(156,192,232)); maxLines=1
  }
  trackMetaBox.addView(deckTrackTitle)
  trackMetaBox.addView(deckTimeLabel)
  val leftDeckCluster=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  leftDeckCluster.addView(miniThumbFrame,LinearLayout.LayoutParams((36*d).toInt(),(26*d).toInt()).apply { rightMargin=(8*d).toInt() })
  leftDeckCluster.addView(trackMetaBox,LinearLayout.LayoutParams(0,-2,1f))
  deckRow.addView(leftDeckCluster,LinearLayout.LayoutParams(0,-2,1.0f))

  // Center: Iconic WMP 11 Dark Capsule Pod with Shuffle, Repeat, Stop, Prev, 3D Blue Play Orb, Next, Mute, Volume Slider
  val podContainer=FrameLayout(this)
  val controlPod=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding((8*d).toInt(),(3*d).toInt(),(8*d).toInt(),(3*d).toInt())
   background=Wmp11Style.controlPodBackground(d)
  }

  lateinit var shuffleIconBtn:TextView
  lateinit var repeatIconBtn:TextView
  lateinit var muteIconBtn:TextView
  val playOrb=Wmp11PlayOrbView(this,d).apply { contentDescription="تشغيل أو إيقاف مؤقت" }

  shuffleIconBtn=TextView(this).apply {
   text="⇄"; textSize=13f; gravity=Gravity.CENTER; contentDescription="تشغيل عشوائي"
   setTextColor(if(shuffle) Color.rgb(88,214,255) else Color.rgb(176,194,218))
  }
  repeatIconBtn=TextView(this).apply {
   text="↻"; textSize=13f; gravity=Gravity.CENTER; contentDescription="تكرار"
   setTextColor(if(repeat) Color.rgb(88,214,255) else Color.rgb(176,194,218))
  }
  val stopIconBtn=TextView(this).apply {
   text="■"; textSize=10.5f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
   contentDescription="إيقاف"
   background=Wmp11Style.podButtonBackground(d,false)
  }
  val prevIconBtn=TextView(this).apply {
   text="|◀◀"; textSize=9.5f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
   typeface=Typeface.create("Arial",Typeface.BOLD); contentDescription="السابق"
   background=Wmp11Style.podButtonBackground(d,false)
  }
  val nextIconBtn=TextView(this).apply {
   text="▶▶|"; textSize=9.5f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
   typeface=Typeface.create("Arial",Typeface.BOLD); contentDescription="التالي"
   background=Wmp11Style.podButtonBackground(d,false)
  }
  muteIconBtn=TextView(this).apply {
   text="🔊"; textSize=11.5f; gravity=Gravity.CENTER; setTextColor(Color.rgb(220,236,255))
   contentDescription="كتم الصوت"
  }
  val volumeSeek=SeekBar(this).apply {
   max=100; progress=(volumeLevel*100).toInt()
   setPadding((4*d).toInt(),0,(4*d).toInt(),0)
   progressTintList=android.content.res.ColorStateList.valueOf(Color.rgb(42,164,255))
   progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(36,52,78))
   thumbTintList=android.content.res.ColorStateList.valueOf(Color.rgb(66,184,255))
   contentDescription="مستوى الصوت"
  }

  controlPod.addView(shuffleIconBtn,LinearLayout.LayoutParams((22*d).toInt(),(26*d).toInt()).apply { rightMargin=(2*d).toInt() })
  controlPod.addView(repeatIconBtn,LinearLayout.LayoutParams((22*d).toInt(),(26*d).toInt()).apply { rightMargin=(3*d).toInt() })
  controlPod.addView(stopIconBtn,LinearLayout.LayoutParams((24*d).toInt(),(26*d).toInt()).apply { rightMargin=(3*d).toInt() })
  controlPod.addView(prevIconBtn,LinearLayout.LayoutParams((32*d).toInt(),(26*d).toInt()).apply { rightMargin=(20*d).toInt() })
  controlPod.addView(nextIconBtn,LinearLayout.LayoutParams((32*d).toInt(),(26*d).toInt()).apply { leftMargin=(20*d).toInt(); rightMargin=(5*d).toInt() })
  controlPod.addView(muteIconBtn,LinearLayout.LayoutParams((22*d).toInt(),(26*d).toInt()).apply { rightMargin=(2*d).toInt() })
  controlPod.addView(volumeSeek,LinearLayout.LayoutParams((56*d).toInt(),(24*d).toInt()))

  podContainer.addView(controlPod,FrameLayout.LayoutParams(-2,(34*d).toInt(),Gravity.CENTER))
  // Overlay the iconic circular 3D cobalt Play Orb slightly taller than the pill pod
  val orbOffsetX=(22f+2f+22f+3f+24f+3f+32f+20f-(56f+2f+22f+5f+32f+20f))/2f
  podContainer.addView(playOrb,FrameLayout.LayoutParams((44*d).toInt(),(44*d).toInt(),Gravity.CENTER).apply {
   leftMargin=(orbOffsetX*d).toInt()
  })
  deckRow.addView(podContainer,LinearLayout.LayoutParams(-2,(46*d).toInt()))

  // Right cluster: Switch to Library & Fullscreen toggle (mirrors WMP 11 bottom-right icons)
  val rightDeckCluster=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.END or Gravity.CENTER_VERTICAL }
  val switchLibBtn=TextView(this).apply {
   text="▤"; textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.rgb(206,224,248))
   contentDescription="التبديل إلى المكتبة"
   background=Wmp11Style.podButtonBackground(d,false)
  }
  val fullToggleBtn=TextView(this).apply {
   text="⛶"; textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.rgb(206,224,248))
   contentDescription="ملء الشاشة"
   background=Wmp11Style.podButtonBackground(d,false)
   setOnClickListener { windowMaximizeBtn?.performClick() }
  }
  rightDeckCluster.addView(switchLibBtn,LinearLayout.LayoutParams((28*d).toInt(),(26*d).toInt()).apply { rightMargin=(6*d).toInt() })
  rightDeckCluster.addView(fullToggleBtn,LinearLayout.LayoutParams((28*d).toInt(),(26*d).toInt()))
  deckRow.addView(rightDeckCluster,LinearLayout.LayoutParams(0,-2,0.55f))

  bottomDeck.addView(deckRow,FrameLayout.LayoutParams(-1,-1))
  page.addView(bottomDeck,LinearLayout.LayoutParams(-1,(54*d).toInt()))

  // --- Playback & Library Logic ---
  val progressHandler=Handler(Looper.getMainLooper())
  var playerAttached=true
  lateinit var renderSongRows:(String)->Unit
  lateinit var renderQueueRows:()->Unit
  lateinit var renderEffectsPanel:()->Unit

  fun applyPlayerAudioSettings(mp:MediaPlayer) {
   try {
    val eff=if(muted) 0f else volumeLevel
    mp.setVolume(eff,eff)
   } catch(_:Exception) {}
   try {
    mp.playbackParams=mp.playbackParams.setSpeed(playbackSpeed)
   } catch(_:Exception) {}
  }

  fun updatePlaybackVisuals() {
   val p=musicPlayer
   val active=p!=null && prepared && try { p.isPlaying } catch(_:Exception) { false }
   playOrb.playing=active
   waveView.isPlayingActive=active
   if(p!=null && prepared) {
    try {
     val pos=p.currentPosition.coerceAtLeast(0)
     val dur=p.duration.coerceAtLeast(0)
     if(!userSeeking) {
      if(seekBar.max!=dur.coerceAtLeast(1)) seekBar.max=dur.coerceAtLeast(1)
      seekBar.progress=pos
     }
     deckTimeLabel.text="${clockTime(pos)} / ${clockTime(dur)}"
    } catch(_:Exception) {}
   }
  }

  val progressTick=object:Runnable {
   override fun run() {
    if(!playerAttached) return
    updatePlaybackVisuals()
    progressHandler.postDelayed(this,350L)
   }
  }

  fun updateLabels() {
   val t=titles.getOrNull(index)
   val a=artists.getOrNull(index)
   if(t!=null) {
    deckTrackTitle.text=t
    overlayTitle.text=t
    overlaySubtitle.text=a?.takeIf { it.isNotBlank() } ?: "Windows Media Player"
    windowTitleLabel?.text="Windows Media Player — $t"
   } else {
    deckTrackTitle.text="Oriket Media"
    overlayTitle.text="Windows Media Player"
    overlaySubtitle.text="اضغط «تحميل موسيقى الجهاز» أو «📂» لاختيار أغاني"
    windowTitleLabel?.text="Windows Media Player"
   }
   renderSongRows(searchSongsInput.text.toString())
   renderQueueRows()
  }

  fun stopOld() {
   try { musicPlayer?.stop(); musicPlayer?.release() } catch(_:Exception) {}
   musicPlayer=null
   prepared=false
   playOrb.playing=false
   waveView.isPlayingActive=false
  }

  lateinit var startTrack:(Int)->Unit
  startTrack={ requested ->
   if(songs.isEmpty()) {
    Toast.makeText(this,"لم يتم العثور على ملفات موسيقى بعد",Toast.LENGTH_SHORT).show()
   } else {
    index=((requested%songs.size)+songs.size)%songs.size
    stopOld(); updateLabels()
    try {
     val mp=MediaPlayer(); musicPlayer=mp
     mp.setDataSource(this,songs[index])
     mp.setOnPreparedListener { p ->
      if(musicPlayer!==p) return@setOnPreparedListener
      prepared=true
      seekBar.max=p.duration.coerceAtLeast(1)
      applyPlayerAudioSettings(p)
      p.start()
      updatePlaybackVisuals()
     }
     mp.setOnCompletionListener {
      if(songs.isEmpty()) return@setOnCompletionListener
      when {
       repeat -> startTrack(index)
       shuffle && songs.size>1 -> startTrack((0 until songs.size).filter { it!=index }.random())
       else -> startTrack((index+1)%songs.size)
      }
     }
     mp.setOnErrorListener { _,_,_ ->
      Toast.makeText(this,"تعذر تشغيل هذا الملف الصوتي",Toast.LENGTH_SHORT).show(); true
     }
     mp.prepareAsync()
    } catch(_:Exception) {
     Toast.makeText(this,"تعذر فتح الملف الصوتي",Toast.LENGTH_SHORT).show()
    }
   }
  }

  renderSongRows={ filter ->
   list.removeAllViews()
   val q=filter.trim().lowercase(Locale.ROOT)
   val matchedIndices=songs.indices.filter { i ->
    q.isEmpty() || titles[i].lowercase(Locale.ROOT).contains(q) || artists[i].lowercase(Locale.ROOT).contains(q)
   }
   if(songs.isEmpty()) {
    list.addView(TextView(this).apply {
     text="المكتبة فارغة حاليًا.\nاضغط «↻ مسح الجهاز» للبحث عن مقاطع MP3/WAV أو «＋ ملف صوتي» لاختيار ملف مباشرة."
     textSize=11.5f; gravity=Gravity.CENTER; setTextColor(Color.rgb(178,208,242))
     setPadding((14*d).toInt(),(26*d).toInt(),(14*d).toInt(),(26*d).toInt())
    },LinearLayout.LayoutParams(-1,-2))
   } else if(matchedIndices.isEmpty()) {
    list.addView(TextView(this).apply {
     text="لا توجد نتائج مطابقة للبحث «$filter»"
     textSize=11.5f; gravity=Gravity.CENTER; setTextColor(Color.rgb(178,208,242))
     setPadding((12*d).toInt(),(20*d).toInt(),(12*d).toInt(),(20*d).toInt())
    },LinearLayout.LayoutParams(-1,-2))
   } else {
    matchedIndices.forEach { i ->
     val isActive=(i==index && musicPlayer!=null)
     val row=LinearLayout(this).apply {
      orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
      setPadding((10*d).toInt(),(6*d).toInt(),(10*d).toInt(),(6*d).toInt())
      background=android.graphics.drawable.GradientDrawable().apply {
       setColor(if(isActive) Color.argb(180,20,86,162) else Color.argb(110,12,24,44))
       cornerRadius=6f*d
       if(isActive) setStroke(1,Color.rgb(86,196,255))
      }
      setOnClickListener { startTrack(i) }
     }
     val noteIcon=ImageView(this).apply {
      setImageDrawable(FluentIcons.createDrawable(this@MainActivity,FluentIcon.MUSIC,(18*d).toInt()))
     }
     val textCol=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
     textCol.addView(TextView(this).apply {
      text="${i+1}. ${titles[i]}"; textSize=11f
      typeface=Typeface.create("Arial",if(isActive) Typeface.BOLD else Typeface.NORMAL)
      setTextColor(if(isActive) Color.rgb(142,232,255) else Color.WHITE)
      maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
     })
     textCol.addView(TextView(this).apply {
      text=artists[i]; textSize=9.5f
      setTextColor(Color.rgb(152,182,218)); maxLines=1
     })
     row.addView(noteIcon,LinearLayout.LayoutParams((20*d).toInt(),(20*d).toInt()).apply { rightMargin=(8*d).toInt() })
     row.addView(textCol,LinearLayout.LayoutParams(0,-2,1f))
     if(isActive) {
      row.addView(TextView(this).apply {
       text="♪ الآن"; textSize=9.5f; setTextColor(Color.rgb(98,224,255))
       typeface=Typeface.create("Arial",Typeface.BOLD)
      })
     }
     list.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(4*d).toInt() })
    }
   }
  }

  renderQueueRows={
   queueList.removeAllViews()
   if(songs.isEmpty()) {
    queueList.addView(TextView(this).apply {
     text="قائمة الانتظار فارغة. أضف مقاطع صوتية من المكتبة أو افتح ملفًا صوتيًا."
     textSize=11.5f; gravity=Gravity.CENTER; setTextColor(Color.rgb(178,208,242))
     setPadding((14*d).toInt(),(26*d).toInt(),(14*d).toInt(),(26*d).toInt())
    },LinearLayout.LayoutParams(-1,-2))
   } else {
    val ordered=(0 until songs.size).map { (index+it)%songs.size }
    ordered.forEachIndexed { pos,trackIdx ->
     val row=TextView(this).apply {
      text=if(pos==0) "▶ الآن: ${titles[trackIdx]} — ${artists[trackIdx]}" else "${pos}. ${titles[trackIdx]}"
      textSize=11f; gravity=Gravity.CENTER_VERTICAL or Gravity.START
      setTextColor(if(pos==0) Color.rgb(112,226,255) else Color.rgb(225,236,250))
      typeface=Typeface.create("Arial",if(pos==0) Typeface.BOLD else Typeface.NORMAL)
      setPadding((10*d).toInt(),(8*d).toInt(),(10*d).toInt(),(8*d).toInt())
      background=android.graphics.drawable.GradientDrawable().apply {
       setColor(if(pos==0) Color.argb(170,18,78,148) else Color.argb(100,12,24,44))
       cornerRadius=6f*d
      }
      setOnClickListener { startTrack(trackIdx) }
     }
     queueList.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(4*d).toInt() })
    }
   }
  }

  renderEffectsPanel={
   effectsContainer.removeAllViews()
   effectsContainer.addView(TextView(this).apply {
    text="سرعة التشغيل (Playback Speed)"; textSize=12f; typeface=Typeface.create("Arial",Typeface.BOLD)
    setTextColor(Color.rgb(172,220,255)); setPadding(0,(4*d).toInt(),0,(8*d).toInt())
   })
   val speedRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.START }
   listOf(0.75f,1.0f,1.25f,1.5f).forEach { spd ->
    val selected=(kotlin.math.abs(playbackSpeed-spd)<0.02f)
    val btn=TextView(this).apply {
     text="${spd}x"; textSize=11f; gravity=Gravity.CENTER
     setTextColor(Color.WHITE); typeface=Typeface.create("Arial",if(selected) Typeface.BOLD else Typeface.NORMAL)
     setPadding((14*d).toInt(),(6*d).toInt(),(14*d).toInt(),(6*d).toInt())
     background=Wmp11Style.podButtonBackground(d,selected)
     setOnClickListener {
      playbackSpeed=spd
      musicPlayer?.let { if(prepared) applyPlayerAudioSettings(it) }
      renderEffectsPanel()
     }
    }
    speedRow.addView(btn,LinearLayout.LayoutParams(-2,(32*d).toInt()).apply { rightMargin=(8*d).toInt() })
   }
   effectsContainer.addView(speedRow,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(14*d).toInt() })

   effectsContainer.addView(TextView(this).apply {
    text="بيانات المقطع الحالي"; textSize=12f; typeface=Typeface.create("Arial",Typeface.BOLD)
    setTextColor(Color.rgb(172,220,255)); setPadding(0,(4*d).toInt(),0,(6*d).toInt())
   })
   val infoText=if(songs.isNotEmpty()) {
    "العنوان: ${titles.getOrNull(index) ?: "-"}\nالفنان: ${artists.getOrNull(index) ?: "-"}\nالتكرار: ${if(repeat) "مفعل" else "متوقف"}   •   العشوائي: ${if(shuffle) "مفعل" else "متوقف"}\nمستوى الصوت: ${(volumeLevel*100).toInt()}%"
   } else "لم يتم تحميل مقاطع صوتية بعد."
   effectsContainer.addView(TextView(this).apply {
    text=infoText; textSize=11f; setTextColor(Color.rgb(218,232,248))
    setPadding((12*d).toInt(),(10*d).toInt(),(12*d).toInt(),(10*d).toInt())
    background=android.graphics.drawable.GradientDrawable().apply {
     setColor(Color.argb(140,12,26,48)); cornerRadius=8f*d; setStroke(1,Color.rgb(54,82,124))
    }
   })
  }

  val loadSongs:()->Unit = {
   if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
    requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO),704)
   } else if(Build.VERSION.SDK_INT<33 && Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) {
    requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),704)
   } else {
    songs.clear(); titles.clear(); artists.clear()
    try {
     val collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
     val projection=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST)
     contentResolver.query(collection,projection,MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC")?.use { cursor ->
      val idCol=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
      val titleCol=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
      val artistCol=cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
      while(cursor.moveToNext()) {
       val id=cursor.getLong(idCol)
       songs.add(Uri.withAppendedPath(collection,id.toString()))
       titles.add(cursor.getString(titleCol) ?: "مقطع صوتي")
       artists.add(cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it!="<unknown>" } ?: "فنان غير معروف")
      }
     }
     updateLabels()
     Toast.makeText(this,"تم العثور على ${songs.size} مقطع صوتي",Toast.LENGTH_SHORT).show()
     if(songs.isNotEmpty()) startTrack(0)
    } catch(_:Exception) {
     Toast.makeText(this,"تعذر قراءة مكتبة الموسيقى. تحقق من الإذن أو اختر ملفًا صوتيًا.",Toast.LENGTH_LONG).show()
    }
   }
  }

  val pickSingleAudio:()->Unit = {
   try {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
     addCategory(Intent.CATEGORY_OPENABLE)
     type="audio/*"
    },72)
   } catch(_:Exception) {
    Toast.makeText(this,"تعذر فتح منتقي الملفات الصوتية",Toast.LENGTH_SHORT).show()
   }
  }

  onMusicPermissionGranted={ if(playerAttached) loadSongs() }
  onAudioPickedCallback={ pickedUri ->
   if(playerAttached) {
    val name=try {
     contentResolver.query(pickedUri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
      if(it.moveToFirst()) it.getString(0) else null
     }
    } catch(_:Exception) { null } ?: pickedUri.lastPathSegment?.substringAfterLast('/') ?: "ملف صوتي"
    songs.add(pickedUri)
    titles.add(name.substringBeforeLast('.'))
    artists.add("ملف محلي")
    startTrack(songs.size-1)
   }
  }

  switchToTab={ targetTab,pushHistory ->
   activeTab=targetTab
   if(pushHistory && navHistory.getOrNull(navPointer)!=targetTab) {
    while(navHistory.size-1>navPointer) navHistory.removeAt(navHistory.size-1)
    navHistory.add(targetTab)
    navPointer=navHistory.size-1
   }
   backNavBtn.background=Wmp11Style.navOrbBackground(d,navPointer>0)
   fwdNavBtn.background=Wmp11Style.navOrbBackground(d,navPointer<navHistory.size-1)
   tabViews.forEachIndexed { idx,tv ->
    val active=(idx==targetTab)
    tv.text=if(active) "${tabNames[idx]} ▾" else tabNames[idx]
    tv.setTextColor(if(active) Color.WHITE else Color.rgb(185,202,226))
    tv.typeface=Typeface.create("Arial",if(active) Typeface.BOLD else Typeface.NORMAL)
    tv.background=if(active) Wmp11Style.activeTabBackground(d) else Wmp11Style.inactiveTabBackground(d)
   }
   secondaryPanel.visibility=if(targetTab==0) View.GONE else View.VISIBLE
   nowPlayingOverlay.visibility=if(targetTab==0 && showOverlayInfo) View.VISIBLE else View.GONE
   libraryToolbar.visibility=if(targetTab==1) View.VISIBLE else View.GONE
   libraryScroll.visibility=if(targetTab==1) View.VISIBLE else View.GONE
   queueScroll.visibility=if(targetTab==2) View.VISIBLE else View.GONE
   effectsContainer.visibility=if(targetTab==3) View.VISIBLE else View.GONE
   switchLibBtn.background=Wmp11Style.podButtonBackground(d,targetTab==1)
   if(targetTab==1) renderSongRows(searchSongsInput.text.toString())
   if(targetTab==2) renderQueueRows()
   if(targetTab==3) renderEffectsPanel()
  }

  // Wire button & slider listeners
  scanOverlayBtn.setOnClickListener { loadSongs() }
  pickFileOverlayBtn.setOnClickListener { pickSingleAudio() }
  openFileQuick.setOnClickListener { pickSingleAudio() }
  scanLibBtn.setOnClickListener { loadSongs() }
  pickLibBtn.setOnClickListener { pickSingleAudio() }
  switchLibBtn.setOnClickListener { switchToTab(if(activeTab==1) 0 else 1,true) }

  searchSongsInput.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { renderSongRows(s?.toString().orEmpty()) }
   override fun afterTextChanged(s:android.text.Editable?) {}
  })

  shuffleIconBtn.setOnClickListener {
   shuffle=!shuffle
   prefs.edit().putBoolean("shuffle",shuffle).apply()
   shuffleIconBtn.setTextColor(if(shuffle) Color.rgb(88,214,255) else Color.rgb(176,194,218))
   if(activeTab==3) renderEffectsPanel()
  }
  repeatIconBtn.setOnClickListener {
   repeat=!repeat
   prefs.edit().putBoolean("repeat",repeat).apply()
   repeatIconBtn.setTextColor(if(repeat) Color.rgb(88,214,255) else Color.rgb(176,194,218))
   if(activeTab==3) renderEffectsPanel()
  }
  stopIconBtn.setOnClickListener {
   val p=musicPlayer
   if(p!=null && prepared) {
    try { if(p.isPlaying) p.pause(); p.seekTo(0) } catch(_:Exception) {}
    seekBar.progress=0
    deckTimeLabel.text="00:00 / ${clockTime(try { p.duration } catch(_:Exception) { 0 })}"
    updatePlaybackVisuals()
   }
  }
  prevIconBtn.setOnClickListener {
   if(songs.isNotEmpty()) startTrack(if(shuffle && songs.size>1) (0 until songs.size).filter { it!=index }.random() else (index-1+songs.size)%songs.size)
  }
  nextIconBtn.setOnClickListener {
   if(songs.isNotEmpty()) startTrack(if(shuffle && songs.size>1) (0 until songs.size).filter { it!=index }.random() else (index+1)%songs.size)
  }
  playOrb.setOnClickListener {
   val p=musicPlayer
   if(p!=null && prepared) {
    if(p.isPlaying) p.pause() else p.start()
    updatePlaybackVisuals()
   } else if(songs.isNotEmpty()) {
    startTrack(index)
   } else {
    loadSongs()
   }
  }
  playOrb.setOnTouchListener { _,ev ->
   when(ev.action) {
    MotionEvent.ACTION_DOWN -> playOrb.pressedOrb=true
    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> playOrb.pressedOrb=false
   }
   false
  }
  muteIconBtn.setOnClickListener {
   muted=!muted
   muteIconBtn.text=if(muted) "🔇" else "🔊"
   muteIconBtn.setTextColor(if(muted) Color.rgb(255,140,140) else Color.rgb(220,236,255))
   musicPlayer?.let { applyPlayerAudioSettings(it) }
  }
  volumeSeek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
   override fun onStartTrackingTouch(bar:SeekBar?) {}
   override fun onStopTrackingTouch(bar:SeekBar?) {}
   override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) {
    if(fromUser) {
     volumeLevel=(value/100f).coerceIn(0f,1f)
     if(muted && value>0) { muted=false; muteIconBtn.text="🔊"; muteIconBtn.setTextColor(Color.rgb(220,236,255)) }
     prefs.edit().putFloat("volume",volumeLevel).apply()
     musicPlayer?.let { applyPlayerAudioSettings(it) }
    }
   }
  })
  seekBar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
   override fun onStartTrackingTouch(bar:SeekBar?) { userSeeking=true }
   override fun onStopTrackingTouch(bar:SeekBar?) {
    userSeeking=false
    if(prepared && bar!=null) try { musicPlayer?.seekTo(bar.progress) } catch(_:Exception) {}
   }
   override fun onProgressChanged(bar:SeekBar?,progressValue:Int,fromUser:Boolean) {
    if(fromUser) deckTimeLabel.text="${clockTime(progressValue)} / ${clockTime(musicPlayer?.duration ?: 0)}"
   }
  })

  page.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener {
   override fun onViewAttachedToWindow(v:View) {
    playerAttached=true
    progressHandler.removeCallbacks(progressTick)
    progressHandler.post(progressTick)
   }
   override fun onViewDetachedFromWindow(v:View) {
    playerAttached=false
    progressHandler.removeCallbacks(progressTick)
    onMusicPermissionGranted=null
    onAudioPickedCallback=null
    stopOld()
   }
  })

  renderSongRows("")
  panel(page)
 }
 private fun clockTime(ms:Int):String { val seconds=ms.coerceAtLeast(0)/1000; return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60) }

 private fun showVideoPlayer() {
  shell("مشغل الفيديو")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val bar=LinearLayout(this).apply { gravity=Gravity.CENTER }
  val video=VideoView(this)
  val videoPrefs=getSharedPreferences("orkeit_video",Context.MODE_PRIVATE)
  videoPrefs.getString("uri",null)?.let { saved -> try { video.setVideoURI(Uri.parse(saved)); video.setOnPreparedListener { player -> player.seekTo(videoPrefs.getInt("position",0)) } } catch(_:Exception) {} }
  bar.addView(action("فتح فيديو") {
   try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="video/*" },52) }
   catch(_:Exception){Toast.makeText(this,"تعذر فتح منتقي الفيديو",Toast.LENGTH_SHORT).show()}
  })
  bar.addView(action("تشغيل"){video.start()})
  bar.addView(action("إيقاف مؤقت"){ if(video.isPlaying)video.pause(); videoPrefs.edit().putInt("position",video.currentPosition).apply() })
  page.addView(bar)
  video.setMediaController(MediaController(this))
  page.addView(video,LinearLayout.LayoutParams(-1,0,1f))
  panel(page)
  video.tag="orkeit-video-view"
 }
 private fun showWord() {
  shell("Word")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val toolbar=LinearLayout(this).apply { gravity=Gravity.CENTER }
  val wordPrefs=getSharedPreferences("orkeit_word",Context.MODE_PRIVATE)
  val editor=EditText(this).apply { hint="مستند جديد — ابدأ الكتابة هنا"; gravity=Gravity.TOP or Gravity.START; textSize=14.96f; setPadding(24,18,24,18); setBackgroundColor(Color.WHITE); tag="orkeit-word-editor"; setText(wordPrefs.getString("draft","").orEmpty()); addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){wordPrefs.edit().putString("draft",s.toString()).apply()}; override fun afterTextChanged(s:android.text.Editable?){} }) }
  toolbar.addView(task("فتح TXT"){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/*"},64)}catch(_:Exception){Toast.makeText(this,"تعذر فتح الملفات",Toast.LENGTH_SHORT).show()}})
  toolbar.addView(task("B"){editor.typeface=if(editor.typeface.isBold) Typeface.create("Arial",Typeface.NORMAL) else Typeface.create("Arial",Typeface.BOLD)})
  toolbar.addView(task("A+"){editor.textSize=(editor.textSize/ getResources().displayMetrics.scaledDensity + 2f).coerceAtMost(40f)})
  toolbar.addView(task("A−"){editor.textSize=(editor.textSize/ getResources().displayMetrics.scaledDensity - 2f).coerceAtLeast(10f)})
  toolbar.addView(action("حفظ TXT"){
   try {
    val values=ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME,"Oriket-Word-"+System.currentTimeMillis()+".txt"); put(MediaStore.Downloads.MIME_TYPE,"text/plain") }
    val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)
    if(uri!=null) contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(editor.text.toString())}
    Toast.makeText(this,if(uri!=null)"تم حفظ المستند في التنزيلات" else "تعذر الحفظ",Toast.LENGTH_LONG).show()
   }catch(_:Exception){Toast.makeText(this,"تعذر الحفظ",Toast.LENGTH_LONG).show()}
  })
  page.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=true; addView(toolbar) },LinearLayout.LayoutParams(-1,54)); page.addView(ScrollView(this).apply { addView(editor) },LinearLayout.LayoutParams(-1,0,1f)); panel(page)
 }
 private fun showExcel() {
  shell("Excel")
  val d=resources.displayMetrics.density
  val prefs=getSharedPreferences("orkeit_excel",Context.MODE_PRIVATE)
  val numCols=8 // Columns A..H like Microsoft Excel screenshot
  var numRows=16 // Rows 1..16 initially, expandable
  val totalInit=numRows*numCols

  // Multi-sheet workbook state (backward-compatible with legacy "data" 4x10 key)
  data class ExcelSheet(
   var name:String,
   var cells:MutableList<String>,
   var boldSet:MutableSet<Int> = mutableSetOf(),
   var alignMap:MutableMap<Int,Int> = mutableMapOf(),
   var tableFilterOn:Boolean = true,
   var stripedRows:Boolean = true,
   var activeFilterCol:Int = -1,
   var activeFilterVal:String? = null
  )

  val sheets=mutableListOf<ExcelSheet>()
  var activeSheetIdx=prefs.getInt("active_sheet",0).coerceAtLeast(0)

  fun loadWorkbook() {
   sheets.clear()
   val jsonStr=prefs.getString("sheets_v2",null)
   if(!jsonStr.isNullOrBlank()) {
    try {
     val arr=org.json.JSONArray(jsonStr)
     for(s in 0 until arr.length()) {
      val obj=arr.getJSONObject(s)
      val name=obj.optString("name","Sheet${s+1}")
      val cellArr=obj.optJSONArray("cells")
      val list=MutableList(totalInit) { "" }
      if(cellArr!=null) {
       val count=maxOf(totalInit,cellArr.length())
       while(list.size<count) list.add("")
       for(i in 0 until cellArr.length()) list[i]=cellArr.optString(i,"")
      }
      val bolds=mutableSetOf<Int>()
      obj.optJSONArray("bold")?.let { ba -> for(i in 0 until ba.length()) bolds.add(ba.optInt(i)) }
      val filterOn=obj.optBoolean("filter",true)
      val stripes=obj.optBoolean("stripes",true)
      sheets.add(ExcelSheet(name,list,bolds,tableFilterOn=filterOn,stripedRows=stripes))
     }
    } catch(_:Exception) {}
   }
   if(sheets.isEmpty()) {
    val legacy=prefs.getString("data","").orEmpty().split("\u001F")
    val sheet1Cells=MutableList(totalInit) { "" }
    if(legacy.any { it.isNotBlank() }) {
     // Migrate saved 4x10 cells cleanly into 8x16 Sheet1
     for(r in 0 until 10) {
      for(c in 0 until 4) {
       val oldIdx=r*4+c
       if(oldIdx<legacy.size) sheet1Cells[r*numCols+c]=legacy[oldIdx]
      }
     }
    }
    sheets.add(ExcelSheet("Sheet1",sheet1Cells,tableFilterOn=true,stripedRows=true))
    sheets.add(ExcelSheet("Sheet2",MutableList(totalInit) { "" },tableFilterOn=false,stripedRows=true))
   }
   if(activeSheetIdx !in sheets.indices) activeSheetIdx=0
   numRows=(sheets[activeSheetIdx].cells.size/numCols).coerceAtLeast(16)
  }
  loadWorkbook()

  fun saveWorkbook() {
   try {
    val arr=org.json.JSONArray()
    sheets.forEach { sh ->
     val obj=JSONObject()
     obj.put("name",sh.name)
     val ca=org.json.JSONArray()
     sh.cells.forEach { ca.put(it) }
     obj.put("cells",ca)
     val ba=org.json.JSONArray()
     sh.boldSet.forEach { ba.put(it) }
     obj.put("bold",ba)
     obj.put("filter",sh.tableFilterOn)
     obj.put("stripes",sh.stripedRows)
     arr.put(obj)
    }
    // Also mirror Sheet1's 4x10 slice into legacy "data" key
    val s0=sheets.firstOrNull()?.cells
    val legacySlice=if(s0!=null) (0 until 10).flatMap { r -> (0 until 4).map { c -> s0.getOrElse(r*numCols+c) { "" } } }.joinToString("\u001F") else ""
    prefs.edit().putString("sheets_v2",arr.toString()).putInt("active_sheet",activeSheetIdx).putString("data",legacySlice).apply()
   } catch(_:Exception) {}
  }

  // Undo / Redo snapshots
  val undoStack=mutableListOf<List<String>>()
  val redoStack=mutableListOf<List<String>>()
  fun pushUndo() {
   val snap=sheets[activeSheetIdx].cells.toList()
   if(undoStack.lastOrNull()!=snap) {
    undoStack.add(snap)
    if(undoStack.size>30) undoStack.removeAt(0)
    redoStack.clear()
   }
  }

  // Theme the shell window in classic Microsoft Excel Forest Green (#217346)
  content.setPadding(0,0,0,0)
  content.setBackgroundColor(Color.WHITE)
  val outerWindow=content.parent as? LinearLayout
  val windowTitleBar=outerWindow?.getChildAt(0) as? LinearLayout
  val windowTitleLabel=windowTitleBar?.getChildAt(1) as? TextView
  outerWindow?.background=android.graphics.drawable.GradientDrawable().apply {
   setColor(Color.WHITE); cornerRadius=8f*d
   setStroke((1.2f*d).toInt().coerceAtLeast(1),ExcelTheme.EXCEL_GREEN_DARK)
  }
  windowTitleBar?.apply {
   setBackgroundColor(ExcelTheme.EXCEL_GREEN)
   windowTitleLabel?.apply {
    text="Book1 - Excel"
    setTextColor(Color.WHITE)
    gravity=Gravity.CENTER
   }
   (getChildAt(2) as? TextView)?.setTextColor(Color.WHITE)
   (getChildAt(3) as? TextView)?.setTextColor(Color.WHITE)
   (getChildAt(4) as? TextView)?.setTextColor(Color.WHITE)
  }

  // Inject quick access buttons (Save, Undo, Redo) directly into the green Excel titlebar
  val quickBar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  fun makeTitleQuickBtn(icon:String,desc:String,onClick:()->Unit):TextView = TextView(this).apply {
   text=icon; textSize=12f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
   contentDescription=desc; setPadding((5*d).toInt(),0,(5*d).toInt(),0)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(35,255,255,255)); cornerRadius=4f*d }
   setOnClickListener { onClick() }
  }
  lateinit var exportCsvAction:()->Unit
  lateinit var undoAction:()->Unit
  lateinit var redoAction:()->Unit
  quickBar.addView(makeTitleQuickBtn("💾","حفظ وتصدير") { saveWorkbook(); exportCsvAction() },LinearLayout.LayoutParams((26*d).toInt(),(24*d).toInt()).apply { rightMargin=(3*d).toInt() })
  quickBar.addView(makeTitleQuickBtn("↶","تراجع") { undoAction() },LinearLayout.LayoutParams((24*d).toInt(),(24*d).toInt()).apply { rightMargin=(3*d).toInt() })
  quickBar.addView(makeTitleQuickBtn("↷","إعادة") { redoAction() },LinearLayout.LayoutParams((24*d).toInt(),(24*d).toInt()).apply { rightMargin=(6*d).toInt() })
  windowTitleBar?.addView(quickBar,1)

  val page=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_LTR
   setBackgroundColor(Color.WHITE)
  }

  // State variables for selection, Ribbon, Zoom, and Grid
  var selRow=0; var selCol=0
  var activeRibbonIdx=3 // Start on "Data" tab (matches Microsoft Excel screenshot!)
  var showFormulasMode=false
  var zoomPct=100
  var suppressSync=false
  val cellEdits=mutableListOf<EditText>()
  val cellContainers=mutableListOf<LinearLayout>()
  val colHeaderViews=mutableListOf<TextView>()
  val rowHeaderViews=mutableListOf<TextView>()
  val rowWrappers=mutableListOf<LinearLayout>()

  lateinit var nameBox:TextView
  lateinit var formulaInput:EditText
  lateinit var statusText:TextView
  lateinit var zoomLabel:TextView
  lateinit var zoomSlider:SeekBar
  lateinit var rebuildGrid:()->Unit
  lateinit var refreshSelectionAndCells:()->Unit
  lateinit var rebuildSheetTabs:()->Unit

  // 1. Excel Green Quick Strip / Ribbon Tab Strip (File, Home, Insert, Formulas, Data, View)
  val ribbonTabStrip=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((8*d).toInt(),(2*d).toInt(),(8*d).toInt(),0)
   setBackgroundColor(ExcelTheme.RIBBON_BG)
  }
  val ribbonTabNames=listOf("File","Home","Formulas","Data","View")
  val ribbonTabButtons=mutableListOf<TextView>()
  val ribbonToolArea=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((8*d).toInt(),(4*d).toInt(),(8*d).toInt(),(4*d).toInt())
   setBackgroundColor(Color.WHITE)
  }
  val ribbonScroller=HorizontalScrollView(this).apply {
   isHorizontalScrollBarEnabled=false
   setBackgroundColor(Color.WHITE)
   addView(ribbonToolArea)
  }

  fun addRibbonTool(icon:String,label:String,active:Boolean=false,onClick:()->Unit) {
   val item=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
    setPadding((8*d).toInt(),(3*d).toInt(),(8*d).toInt(),(3*d).toInt())
    background=ExcelTheme.ribbonToolBackground(d,active)
    setOnClickListener { onClick() }
   }
   item.addView(TextView(this).apply {
    text=icon; textSize=14f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.EXCEL_GREEN_DARK)
   })
   item.addView(TextView(this).apply {
    text=label; textSize=9f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.TEXT_DARK); maxLines=1
   })
   ribbonToolArea.addView(item,LinearLayout.LayoutParams(-2,(46*d).toInt()).apply { rightMargin=(3*d).toInt() })
  }
  fun addRibbonDivider() {
   ribbonToolArea.addView(View(this).apply { setBackgroundColor(ExcelTheme.RIBBON_GROUP_DIV) },LinearLayout.LayoutParams((1*d).toInt().coerceAtLeast(1),(38*d).toInt()).apply {
    leftMargin=(4*d).toInt(); rightMargin=(6*d).toInt()
   })
  }

  val importCsvAction:()->Unit = {
   try {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
     addCategory(Intent.CATEGORY_OPENABLE)
     type="*/*"
    },73)
   } catch(_:Exception) { Toast.makeText(this,"تعذر فتح منتقي ملفات CSV",Toast.LENGTH_SHORT).show() }
  }

  exportCsvAction={
   try {
    val sh=sheets[activeSheetIdx]
    val getRaw:(Int,Int)->String={ r,c -> sh.cells.getOrElse(r*numCols+c) { "" } }
    val rows=(0 until numRows).joinToString("\n") { r ->
     (0 until numCols).joinToString(",") { col ->
      val raw=getRaw(r,col).trim()
      val value=if(raw.startsWith("=")) ExcelFormulaEngine.evaluateDisplay(raw,numRows,numCols,getRaw) else raw
      "\""+value.replace("\"","\"\"")+"\""
     }
    }
    val values=ContentValues().apply {
     put(MediaStore.Downloads.DISPLAY_NAME,"Oriket-${sh.name}-${System.currentTimeMillis()}.csv")
     put(MediaStore.Downloads.MIME_TYPE,"text/csv")
    }
    val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)
    if(uri!=null) contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write("\uFEFF"+rows) }
    Toast.makeText(this,if(uri!=null)"تم تصدير ${sh.name}.csv إلى التنزيلات" else "تعذر التصدير",Toast.LENGTH_LONG).show()
   } catch(_:Exception) { Toast.makeText(this,"تعذر التصدير",Toast.LENGTH_LONG).show() }
  }

  fun sortByColumn(col:Int,ascending:Boolean) {
   val sh=sheets[activeSheetIdx]
   pushUndo()
   val startRow=if(sh.tableFilterOn) 1 else 0
   val dataRows=(startRow until numRows).map { r ->
    (0 until numCols).map { c -> sh.cells[r*numCols+c] }
   }
   val nonEmpty=dataRows.filter { row -> row.any { it.isNotBlank() } }
   val empty=List(dataRows.size-nonEmpty.size) { List(numCols) { "" } }
   val sorted=nonEmpty.sortedWith { a,b ->
    val av=a[col].trim(); val bv=b[col].trim()
    val an=av.replace(",",".").toDoubleOrNull(); val bn=bv.replace(",",".").toDoubleOrNull()
    val cmp=if(an!=null && bn!=null) an.compareTo(bn) else av.lowercase(Locale.ROOT).compareTo(bv.lowercase(Locale.ROOT))
    if(ascending) cmp else -cmp
   }
   (sorted+empty).forEachIndexed { idx,rowVals ->
    val r=startRow+idx
    for(c in 0 until numCols) sh.cells[r*numCols+c]=rowVals[c]
   }
   saveWorkbook(); rebuildGrid()
  }

  fun insertFormulaIntoSelected(formula:String) {
   val sh=sheets[activeSheetIdx]
   pushUndo()
   val idx=selRow*numCols+selCol
   sh.cells[idx]=formula
   saveWorkbook()
   refreshSelectionAndCells()
  }

  lateinit var renderRibbonTools:(Int)->Unit
  renderRibbonTools={ tabIdx ->
   activeRibbonIdx=tabIdx
   ribbonTabButtons.forEachIndexed { i,tv ->
    val active=(i==tabIdx)
    tv.setTextColor(if(active) ExcelTheme.EXCEL_GREEN else ExcelTheme.TEXT_DARK)
    tv.typeface=Typeface.create("Arial",if(active) Typeface.BOLD else Typeface.NORMAL)
    tv.background=if(active) ExcelTheme.activeRibbonTabDrawable(d) else null
   }
   ribbonToolArea.removeAllViews()
   val sh=sheets[activeSheetIdx]
   when(tabIdx) {
    0 -> { // File
     addRibbonTool("📂","فتح CSV") { importCsvAction() }
     addRibbonTool("📤","تصدير CSV") { exportCsvAction() }
     addRibbonDivider()
     addRibbonTool("💾","حفظ") { saveWorkbook(); Toast.makeText(this,"تم حفظ المصنف",Toast.LENGTH_SHORT).show() }
     addRibbonTool("🆕","ورقة جديدة") {
      AlertDialog.Builder(this).setTitle("مسح الورقة الحالية؟")
       .setPositiveButton("مسح") { _,_ -> pushUndo(); sh.cells.fill(""); sh.boldSet.clear(); sh.activeFilterVal=null; saveWorkbook(); rebuildGrid() }
       .setNegativeButton("إلغاء",null).show()
     }
    }
    1 -> { // Home
     addRibbonTool("B","عريض",sh.boldSet.contains(selRow*numCols+selCol)) {
      val idx=selRow*numCols+selCol
      if(sh.boldSet.contains(idx)) sh.boldSet.remove(idx) else sh.boldSet.add(idx)
      saveWorkbook(); refreshSelectionAndCells(); renderRibbonTools(1)
     }
     addRibbonTool("⇤","محاذاة يسار") { sh.alignMap[selRow*numCols+selCol]=Gravity.START or Gravity.CENTER_VERTICAL; refreshSelectionAndCells() }
     addRibbonTool("≡","توسيط") { sh.alignMap[selRow*numCols+selCol]=Gravity.CENTER; refreshSelectionAndCells() }
     addRibbonTool("⇥","محاذاة يمين") { sh.alignMap[selRow*numCols+selCol]=Gravity.END or Gravity.CENTER_VERTICAL; refreshSelectionAndCells() }
     addRibbonDivider()
     addRibbonTool("∑","جمع تلقائي") {
      val colL=ExcelFormulaEngine.colName(selCol)
      val endR=maxOf(1,selRow)
      val startR=if(sh.tableFilterOn && endR>1) 2 else 1
      insertFormulaIntoSelected("=SUM(${colL}$startR:${colL}$endR)")
     }
     addRibbonTool("🧹","مسح الخلية") { pushUndo(); sh.cells[selRow*numCols+selCol]=""; saveWorkbook(); refreshSelectionAndCells() }
     addRibbonTool("🗑","مسح الكل") {
      AlertDialog.Builder(this).setTitle("مسح كل خلايا ${sh.name}؟")
       .setPositiveButton("مسح") { _,_ -> pushUndo(); sh.cells.fill(""); sh.boldSet.clear(); saveWorkbook(); rebuildGrid() }
       .setNegativeButton("إلغاء",null).show()
     }
    }
    2 -> { // Formulas
     val colL=ExcelFormulaEngine.colName(selCol)
     val endR=maxOf(1,selRow)
     val startR=if(sh.tableFilterOn && endR>1) 2 else 1
     addRibbonTool("∑","SUM") { insertFormulaIntoSelected("=SUM(${colL}$startR:${colL}$endR)") }
     addRibbonTool("x̄","AVERAGE") { insertFormulaIntoSelected("=AVERAGE(${colL}$startR:${colL}$endR)") }
     addRibbonTool("↓","MIN") { insertFormulaIntoSelected("=MIN(${colL}$startR:${colL}$endR)") }
     addRibbonTool("↑","MAX") { insertFormulaIntoSelected("=MAX(${colL}$startR:${colL}$endR)") }
     addRibbonTool("#","COUNT") { insertFormulaIntoSelected("=COUNT(${colL}$startR:${colL}$endR)") }
     addRibbonDivider()
     addRibbonTool("fx","عرض الصيغ",showFormulasMode) {
      showFormulasMode=!showFormulasMode; refreshSelectionAndCells(); renderRibbonTools(2)
     }
     addRibbonTool("✓","حساب الكل") {
      val getRaw:(Int,Int)->String={ r,c -> sh.cells.getOrElse(r*numCols+c) { "" } }
      val results=mutableListOf<String>()
      for(r in 0 until numRows) for(c in 0 until numCols) {
       val raw=getRaw(r,c).trim()
       if(raw.startsWith("=")) results.add("${ExcelFormulaEngine.cellRef(r,c)}  ($raw) = ${ExcelFormulaEngine.evaluateDisplay(raw,numRows,numCols,getRaw)}")
      }
      refreshSelectionAndCells()
      AlertDialog.Builder(this).setTitle("نتائج الصيغ")
       .setMessage(results.joinToString("\n").ifEmpty { "لا توجد صيغ. أمثلة: =A1+B1 أو =SUM(A1:A5) أو =AVERAGE(D2:D8)" })
       .setPositiveButton("حسنًا",null).show()
     }
    }
    3 -> { // Data (Exact Microsoft Excel screenshot layout!)
     addRibbonTool("📥","استيراد CSV") { importCsvAction() }
     addRibbonTool("📤","تصدير CSV") { exportCsvAction() }
     addRibbonDivider()
     addRibbonTool("A↓Z","فرز تصاعدي") { sortByColumn(selCol,true) }
     addRibbonTool("Z↓A","فرز تنازلي") { sortByColumn(selCol,false) }
     addRibbonTool("⧩","Filter",sh.tableFilterOn) {
      sh.tableFilterOn=!sh.tableFilterOn
      if(!sh.tableFilterOn) { sh.activeFilterCol=-1; sh.activeFilterVal=null }
      saveWorkbook(); rebuildGrid(); renderRibbonTools(3)
     }
     addRibbonTool("✖","إلغاء التصفية",sh.activeFilterVal!=null) {
      sh.activeFilterCol=-1; sh.activeFilterVal=null; rebuildGrid(); renderRibbonTools(3)
     }
     addRibbonDivider()
     addRibbonTool("⇄","تقليم العمود") {
      pushUndo()
      for(r in 0 until numRows) { val i=r*numCols+selCol; sh.cells[i]=sh.cells[i].trim() }
      saveWorkbook(); rebuildGrid()
     }
     addRibbonTool("↴","ملء لأسفل") {
      if(selRow+1<numRows) {
       pushUndo()
       sh.cells[(selRow+1)*numCols+selCol]=sh.cells[selRow*numCols+selCol]
       saveWorkbook(); refreshSelectionAndCells()
      }
     }
    }
    4 -> { // View
     addRibbonTool("▤","صفوف مخططة",sh.stripedRows) {
      sh.stripedRows=!sh.stripedRows; saveWorkbook(); refreshSelectionAndCells(); renderRibbonTools(4)
     }
     addRibbonTool("＋","إضافة 4 صفوف") {
      numRows+=4
      repeat(4*numCols) { sh.cells.add("") }
      saveWorkbook(); rebuildGrid()
     }
     addRibbonDivider()
     addRibbonTool("🔍+","تكبير") { zoomPct=(zoomPct+15).coerceAtMost(140); zoomSlider.progress=zoomPct-70; rebuildGrid() }
     addRibbonTool("100%","افتراضي") { zoomPct=100; zoomSlider.progress=30; rebuildGrid() }
     addRibbonTool("🔍-","تصغير") { zoomPct=(zoomPct-15).coerceAtLeast(70); zoomSlider.progress=zoomPct-70; rebuildGrid() }
    }
   }
  }

  ribbonTabNames.forEachIndexed { i,name ->
   val tv=TextView(this).apply {
    text=name; textSize=11f; gravity=Gravity.CENTER
    setPadding((10*d).toInt(),(4*d).toInt(),(10*d).toInt(),(5*d).toInt())
    setOnClickListener { renderRibbonTools(i) }
   }
   ribbonTabButtons.add(tv)
   ribbonTabStrip.addView(tv,LinearLayout.LayoutParams(-2,(28*d).toInt()).apply { rightMargin=(2*d).toInt() })
  }
  page.addView(ribbonTabStrip,LinearLayout.LayoutParams(-1,(30*d).toInt()))
  page.addView(ribbonScroller,LinearLayout.LayoutParams(-1,(52*d).toInt()))
  page.addView(View(this).apply { setBackgroundColor(Color.rgb(214,214,214)) },LinearLayout.LayoutParams(-1,1))

  // 2. Excel Formula Bar (Name Box A1 | ✕ ✓ fx | Formula EditText)
  val formulaBar=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((6*d).toInt(),(4*d).toInt(),(6*d).toInt(),(4*d).toInt())
   setBackgroundColor(Color.rgb(245,245,245))
  }
  nameBox=TextView(this).apply {
   text="A1"; textSize=11f; gravity=Gravity.CENTER
   setTextColor(ExcelTheme.TEXT_DARK); typeface=Typeface.create("Arial",Typeface.BOLD)
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.WHITE); cornerRadius=2f*d; setStroke(1,Color.rgb(198,198,198))
   }
   setOnClickListener {
    val jumpInput=EditText(this@MainActivity).apply { hint="مثل C4 أو H12"; setSingleLine(true) }
    AlertDialog.Builder(this@MainActivity).setTitle("انتقال إلى خلية").setView(jumpInput)
     .setPositiveButton("انتقال") { _,_ ->
      ExcelFormulaEngine.parseRef(jumpInput.text.toString(),numCols,numRows)?.let { (r,c) ->
       selRow=r; selCol=c; refreshSelectionAndCells()
      }
     }.setNegativeButton("إلغاء",null).show()
   }
  }
  val cancelEditBtn=TextView(this).apply {
   text="✕"; textSize=11.5f; gravity=Gravity.CENTER; setTextColor(Color.rgb(168,58,52))
   contentDescription="إلغاء التعديل"
  }
  val commitEditBtn=TextView(this).apply {
   text="✓"; textSize=12f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.EXCEL_GREEN)
   typeface=Typeface.create("Arial",Typeface.BOLD); contentDescription="تأكيد"
  }
  val fxPickerBtn=TextView(this).apply {
   text="fx"; textSize=11.5f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.TEXT_DARK)
   typeface=Typeface.create("serif",Typeface.ITALIC or Typeface.BOLD); contentDescription="إدراج دالة"
   setOnClickListener {
    val colL=ExcelFormulaEngine.colName(selCol)
    val endR=maxOf(1,selRow)
    val items=arrayOf(
     "=SUM(${colL}1:${colL}$endR)",
     "=AVERAGE(${colL}1:${colL}$endR)",
     "=MIN(${colL}1:${colL}$endR)",
     "=MAX(${colL}1:${colL}$endR)",
     "=COUNT(${colL}1:${colL}$endR)",
     "=A1+B1"
    )
    AlertDialog.Builder(this@MainActivity).setTitle("إدراج صيغة")
     .setItems(items) { _,w -> insertFormulaIntoSelected(items[w]) }.show()
   }
  }
  val formulaControlsBox=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.WHITE); setStroke(1,Color.rgb(206,206,206))
   }
  }
  formulaControlsBox.addView(cancelEditBtn,LinearLayout.LayoutParams((24*d).toInt(),(26*d).toInt()))
  formulaControlsBox.addView(commitEditBtn,LinearLayout.LayoutParams((24*d).toInt(),(26*d).toInt()))
  formulaControlsBox.addView(fxPickerBtn,LinearLayout.LayoutParams((26*d).toInt(),(26*d).toInt()))

  formulaInput=EditText(this).apply {
   hint="أدخل قيمة أو صيغة مثل =SUM(A1:A4)"; setHintTextColor(Color.rgb(160,166,174))
   textSize=11.5f; setSingleLine(true); setTextColor(ExcelTheme.TEXT_DARK)
   setPadding((8*d).toInt(),(2*d).toInt(),(8*d).toInt(),(2*d).toInt())
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.WHITE); setStroke(1,Color.rgb(198,198,198))
   }
  }
  formulaBar.addView(nameBox,LinearLayout.LayoutParams((54*d).toInt(),(28*d).toInt()).apply { rightMargin=(6*d).toInt() })
  formulaBar.addView(formulaControlsBox,LinearLayout.LayoutParams(-2,(28*d).toInt()).apply { rightMargin=(6*d).toInt() })
  formulaBar.addView(formulaInput,LinearLayout.LayoutParams(0,(28*d).toInt(),1f))
  page.addView(formulaBar,LinearLayout.LayoutParams(-1,(36*d).toInt()))
  page.addView(View(this).apply { setBackgroundColor(Color.rgb(212,212,212)) },LinearLayout.LayoutParams(-1,1))

  // 3. Main 2D Scrollable Spreadsheet Grid
  val gridContainer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val hScroll=HorizontalScrollView(this).apply { isFillViewport=true }
  val vScroll=ScrollView(this).apply { isFillViewport=true; addView(gridContainer) }
  hScroll.addView(vScroll)
  page.addView(hScroll,LinearLayout.LayoutParams(-1,0,1f))

  fun showColumnFilterMenu(col:Int,anchor:View) {
   val sh=sheets[activeSheetIdx]
   android.widget.PopupMenu(this,anchor).apply {
    menu.add("فرز من الألف إلى الياء (A→Z)").setOnMenuItemClickListener { sortByColumn(col,true); true }
    menu.add("فرز من الياء إلى الألف (Z→A)").setOnMenuItemClickListener { sortByColumn(col,false); true }
    menu.add("تصفية حسب قيمة...").setOnMenuItemClickListener {
     val distinct=((if(sh.tableFilterOn) 1 else 0) until numRows)
      .map { sh.cells[it*numCols+col].trim() }.filter { it.isNotEmpty() }.distinct().take(20)
     if(distinct.isEmpty()) {
      Toast.makeText(this@MainActivity,"لا توجد قيم للتصفية في العمود ${ExcelFormulaEngine.colName(col)}",Toast.LENGTH_SHORT).show()
     } else {
      val opts=arrayOf("عرض الكل")+distinct.toTypedArray()
      AlertDialog.Builder(this@MainActivity).setTitle("تصفية العمود ${ExcelFormulaEngine.colName(col)}")
       .setItems(opts) { _,w ->
        if(w==0) { sh.activeFilterCol=-1; sh.activeFilterVal=null }
        else { sh.activeFilterCol=col; sh.activeFilterVal=opts[w] }
        rebuildGrid(); renderRibbonTools(activeRibbonIdx)
       }.show()
     }
     true
    }
    if(sh.activeFilterVal!=null) {
     menu.add("إلغاء التصفية").setOnMenuItemClickListener {
      sh.activeFilterCol=-1; sh.activeFilterVal=null; rebuildGrid(); renderRibbonTools(activeRibbonIdx); true
     }
    }
    show()
   }
  }

  refreshSelectionAndCells={
   val sh=sheets[activeSheetIdx]
   val getRaw:(Int,Int)->String={ r,c -> sh.cells.getOrElse(r*numCols+c) { "" } }
   nameBox.text=ExcelFormulaEngine.cellRef(selRow,selCol)

   // Highlight active column & row headers
   colHeaderViews.forEachIndexed { c,tv ->
    val active=(c==selCol)
    tv.setBackgroundColor(if(active) ExcelTheme.EXCEL_GREEN_SOFT else ExcelTheme.HEADER_BG)
    tv.setTextColor(if(active) ExcelTheme.EXCEL_GREEN_DARK else ExcelTheme.TEXT_DARK)
    tv.typeface=Typeface.create("Arial",if(active) Typeface.BOLD else Typeface.NORMAL)
    (tv.background as? android.graphics.drawable.GradientDrawable) ?: Unit
    tv.background=android.graphics.drawable.GradientDrawable().apply {
     setColor(if(active) ExcelTheme.EXCEL_GREEN_SOFT else ExcelTheme.HEADER_BG)
     setStroke(1,ExcelTheme.GRID_LINE)
    }
   }
   rowHeaderViews.forEachIndexed { r,tv ->
    val active=(r==selRow)
    tv.background=android.graphics.drawable.GradientDrawable().apply {
     setColor(if(active) ExcelTheme.EXCEL_GREEN_SOFT else ExcelTheme.HEADER_BG)
     setStroke(1,ExcelTheme.GRID_LINE)
    }
    tv.setTextColor(if(active) ExcelTheme.EXCEL_GREEN_DARK else ExcelTheme.TEXT_DARK)
    tv.typeface=Typeface.create("Arial",if(active) Typeface.BOLD else Typeface.NORMAL)
   }

   suppressSync=true
   val activeRaw=getRaw(selRow,selCol)
   if(formulaInput.text.toString()!=activeRaw) {
    formulaInput.setText(activeRaw)
    formulaInput.setSelection(activeRaw.length)
   }
   for(r in 0 until numRows) {
    for(c in 0 until numCols) {
     val idx=r*numCols+c
     if(idx>=cellEdits.size) continue
     val ed=cellEdits[idx]
     val box=cellContainers[idx]
     val isSel=(r==selRow && c==selCol)
     val isTableHeader=(r==0 && sh.tableFilterOn)
     val isStriped=(sh.stripedRows && !isTableHeader && r%2==1)
     box.background=ExcelTheme.cellBackground(d,isSel,null,isTableHeader,isStriped)

     val raw=getRaw(r,c)
     val shown=if(isSel || showFormulasMode) raw else ExcelFormulaEngine.evaluateDisplay(raw,numRows,numCols,getRaw)
     if(!ed.isFocused && ed.text.toString()!=shown) ed.setText(shown)

     val isBold=isTableHeader || sh.boldSet.contains(idx)
     ed.typeface=Typeface.create("Arial",if(isBold) Typeface.BOLD else Typeface.NORMAL)
     ed.setTextColor(if(isTableHeader) Color.WHITE else ExcelTheme.TEXT_DARK)
     val customAlign=sh.alignMap[idx]
     val isNumeric=shown.replace(",",".").toDoubleOrNull()!=null
     ed.gravity=customAlign ?: (if(!isTableHeader && isNumeric) (Gravity.END or Gravity.CENTER_VERTICAL) else (Gravity.START or Gravity.CENTER_VERTICAL))
    }
   }
   suppressSync=false

   // Update status bar live summary (Sum & Avg of selected column)
   val colNums=((if(sh.tableFilterOn) 1 else 0) until numRows).mapNotNull { r ->
    val raw=getRaw(r,selCol).trim()
    if(raw.isEmpty()) null
    else {
     val v=if(raw.startsWith("=")) ExcelFormulaEngine.evaluateNumeric(raw,numRows,numCols,getRaw) else raw.replace(",",".").toDoubleOrNull()
     if(v!=null && !v.isNaN() && !v.isInfinite()) v else null
    }
   }
   statusText.text=if(colNums.isNotEmpty()) {
    "Ready   •   ${ExcelFormulaEngine.colName(selCol)}: Sum ${ExcelFormulaEngine.formatNumber(colNums.sum())}   Avg ${ExcelFormulaEngine.formatNumber(colNums.average())}   Count ${colNums.size}"
   } else "Ready"
  }

  rebuildGrid={
   val sh=sheets[activeSheetIdx]
   val scale=zoomPct/100f
   val colW=(84*d*scale).toInt().coerceAtLeast((56*d).toInt())
   val rowH=(27*d*scale).toInt().coerceAtLeast((22*d).toInt())
   val rowNumW=(34*d*scale).toInt().coerceAtLeast((28*d).toInt())
   val fontSp=(11.2f*scale).coerceIn(8.5f,16f)

   gridContainer.removeAllViews()
   cellEdits.clear(); cellContainers.clear()
   colHeaderViews.clear(); rowHeaderViews.clear(); rowWrappers.clear()

   // Column Headers Row (Corner + A..H)
   val headerRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
   val corner=TextView(this).apply {
    text="◢"; textSize=8.5f; gravity=Gravity.BOTTOM or Gravity.END
    setPadding(0,0,(3*d).toInt(),(2*d).toInt())
    setTextColor(Color.rgb(142,148,158))
    background=android.graphics.drawable.GradientDrawable().apply { setColor(ExcelTheme.HEADER_BG); setStroke(1,ExcelTheme.GRID_LINE) }
    setOnClickListener { selRow=0; selCol=0; refreshSelectionAndCells() }
   }
   headerRow.addView(corner,LinearLayout.LayoutParams(rowNumW,rowH))
   for(c in 0 until numCols) {
    val ch=TextView(this).apply {
     text=ExcelFormulaEngine.colName(c); textSize=fontSp; gravity=Gravity.CENTER
     background=android.graphics.drawable.GradientDrawable().apply { setColor(ExcelTheme.HEADER_BG); setStroke(1,ExcelTheme.GRID_LINE) }
     setOnClickListener { selCol=c; refreshSelectionAndCells() }
    }
    colHeaderViews.add(ch)
    headerRow.addView(ch,LinearLayout.LayoutParams(colW,rowH))
   }
   gridContainer.addView(headerRow)

   for(r in 0 until numRows) {
    val rowLine=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
    val rowHdr=TextView(this).apply {
     text=(r+1).toString(); textSize=fontSp*0.92f; gravity=Gravity.CENTER
     background=android.graphics.drawable.GradientDrawable().apply { setColor(ExcelTheme.HEADER_BG); setStroke(1,ExcelTheme.GRID_LINE) }
     setOnClickListener { selRow=r; refreshSelectionAndCells() }
    }
    rowHeaderViews.add(rowHdr)
    rowLine.addView(rowHdr,LinearLayout.LayoutParams(rowNumW,rowH))

    for(c in 0 until numCols) {
     val idx=r*numCols+c
     val cellBox=LinearLayout(this).apply {
      orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
      setPadding((4*d).toInt(),0,(2*d).toInt(),0)
     }
     val ed=EditText(this).apply {
      setSingleLine(true); textSize=fontSp; setBackgroundColor(Color.TRANSPARENT)
      setPadding(0,0,0,0); includeFontPadding=false
      setOnFocusChangeListener { _,hasFocus ->
       if(hasFocus) {
        val prevIdx=selRow*numCols+selCol
        if(prevIdx!=idx) pushUndo()
        selRow=r; selCol=c
        val raw=sh.cells.getOrElse(idx) { "" }
        suppressSync=true
        if(text.toString()!=raw) { setText(raw); setSelection(raw.length) }
        suppressSync=false
        refreshSelectionAndCells()
       } else {
        refreshSelectionAndCells()
       }
      }
      addTextChangedListener(object:android.text.TextWatcher {
       override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
       override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
        if(suppressSync || !isFocused) return
        val txt=s?.toString().orEmpty()
        sh.cells[idx]=txt
        suppressSync=true
        if(selRow==r && selCol==c && formulaInput.text.toString()!=txt) {
         formulaInput.setText(txt); formulaInput.setSelection(txt.length)
        }
        suppressSync=false
        saveWorkbook()
       }
       override fun afterTextChanged(s:android.text.Editable?) {}
      })
     }
     cellBox.addView(ed,LinearLayout.LayoutParams(0,-1,1f))
     if(r==0 && sh.tableFilterOn) {
      val dropBtn=TextView(this).apply {
       text="▾"; textSize=9.5f; gravity=Gravity.CENTER
       setTextColor(Color.rgb(42,68,112))
       background=android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.rgb(228,236,248)); cornerRadius=2f*d
       }
       setOnClickListener { showColumnFilterMenu(c,it) }
      }
      cellBox.addView(dropBtn,LinearLayout.LayoutParams((16*d*scale).toInt(),(16*d*scale).toInt()).apply { leftMargin=(2*d).toInt() })
     }
     cellEdits.add(ed)
     cellContainers.add(cellBox)
     rowLine.addView(cellBox,LinearLayout.LayoutParams(colW,rowH))
    }

    // Apply row visibility if active filter is set
    val visible=if(sh.activeFilterVal!=null && sh.activeFilterCol in 0 until numCols && r>=(if(sh.tableFilterOn) 1 else 0)) {
     sh.cells.getOrElse(r*numCols+sh.activeFilterCol) { "" }.trim()==sh.activeFilterVal
    } else true
    rowLine.visibility=if(visible) View.VISIBLE else View.GONE
    rowWrappers.add(rowLine)
    gridContainer.addView(rowLine)
   }
   zoomLabel.text="${zoomPct}%"
   refreshSelectionAndCells()
  }

  // Formula Bar Listeners
  formulaInput.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
    if(suppressSync || !formulaInput.isFocused) return
    val txt=s?.toString().orEmpty()
    val idx=selRow*numCols+selCol
    sheets[activeSheetIdx].cells[idx]=txt
    suppressSync=true
    cellEdits.getOrNull(idx)?.setText(txt)
    suppressSync=false
    saveWorkbook()
   }
   override fun afterTextChanged(s:android.text.Editable?) {}
  })
  commitEditBtn.setOnClickListener {
   pushUndo()
   val idx=selRow*numCols+selCol
   sheets[activeSheetIdx].cells[idx]=formulaInput.text.toString()
   saveWorkbook()
   if(selRow+1<numRows) selRow++
   formulaInput.clearFocus()
   cellEdits.getOrNull(idx)?.clearFocus()
   refreshSelectionAndCells()
  }
  cancelEditBtn.setOnClickListener {
   val prev=undoStack.lastOrNull()
   if(prev!=null) {
    val idx=selRow*numCols+selCol
    sheets[activeSheetIdx].cells[idx]=prev.getOrElse(idx) { "" }
    saveWorkbook()
   }
   formulaInput.clearFocus()
   refreshSelectionAndCells()
  }

  undoAction={
   if(undoStack.isNotEmpty()) {
    redoStack.add(sheets[activeSheetIdx].cells.toList())
    val prev=undoStack.removeAt(undoStack.size-1)
    sheets[activeSheetIdx].cells.clear(); sheets[activeSheetIdx].cells.addAll(prev)
    numRows=(sheets[activeSheetIdx].cells.size/numCols).coerceAtLeast(16)
    saveWorkbook(); rebuildGrid()
   }
  }
  redoAction={
   if(redoStack.isNotEmpty()) {
    undoStack.add(sheets[activeSheetIdx].cells.toList())
    val next=redoStack.removeAt(redoStack.size-1)
    sheets[activeSheetIdx].cells.clear(); sheets[activeSheetIdx].cells.addAll(next)
    numRows=(sheets[activeSheetIdx].cells.size/numCols).coerceAtLeast(16)
    saveWorkbook(); rebuildGrid()
   }
  }

  // 4. Bottom Sheet Tabs Bar (< > | Sheet1 | Sheet2 | +)
  val sheetBar=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((8*d).toInt(),(2*d).toInt(),(8*d).toInt(),0)
   setBackgroundColor(Color.rgb(230,230,230))
  }
  val sheetTabContainer=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.BOTTOM }
  val addSheetBtn=TextView(this).apply {
   text="＋"; textSize=13f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.TEXT_DARK)
   contentDescription="إضافة ورقة عمل"
   background=android.graphics.drawable.GradientDrawable().apply {
    shape=android.graphics.drawable.GradientDrawable.OVAL
    setColor(Color.WHITE); setStroke(1,Color.rgb(188,188,188))
   }
   setOnClickListener {
    val newName="Sheet${sheets.size+1}"
    sheets.add(ExcelSheet(newName,MutableList(totalInit) { "" },tableFilterOn=false,stripedRows=true))
    activeSheetIdx=sheets.size-1
    numRows=16; selRow=0; selCol=0; undoStack.clear(); redoStack.clear()
    saveWorkbook(); rebuildSheetTabs(); rebuildGrid()
   }
  }
  rebuildSheetTabs={
   sheetTabContainer.removeAllViews()
   sheets.forEachIndexed { i,sh ->
    val active=(i==activeSheetIdx)
    val tab=TextView(this).apply {
     text=sh.name; textSize=10.5f; gravity=Gravity.CENTER
     setTextColor(if(active) ExcelTheme.EXCEL_GREEN else ExcelTheme.TEXT_DARK)
     typeface=Typeface.create("Arial",if(active) Typeface.BOLD else Typeface.NORMAL)
     setPadding((12*d).toInt(),(3*d).toInt(),(12*d).toInt(),(4*d).toInt())
     background=if(active) ExcelTheme.activeSheetTabDrawable(d) else null
     setOnClickListener {
      if(activeSheetIdx!=i) {
       activeSheetIdx=i
       numRows=(sheets[activeSheetIdx].cells.size/numCols).coerceAtLeast(16)
       selRow=0; selCol=0; undoStack.clear(); redoStack.clear()
       saveWorkbook(); rebuildSheetTabs(); rebuildGrid(); renderRibbonTools(activeRibbonIdx)
      }
     }
     setOnLongClickListener {
      val nameInput=EditText(this@MainActivity).apply { setText(sh.name); setSingleLine(true) }
      AlertDialog.Builder(this@MainActivity).setTitle("إعادة تسمية الورقة").setView(nameInput)
       .setPositiveButton("حفظ") { _,_ ->
        val n=nameInput.text.toString().trim()
        if(n.isNotEmpty()) { sh.name=n; saveWorkbook(); rebuildSheetTabs() }
       }.setNegativeButton("إلغاء",null).show()
      true
     }
    }
    sheetTabContainer.addView(tab,LinearLayout.LayoutParams(-2,(25*d).toInt()).apply { rightMargin=(4*d).toInt() })
   }
   sheetTabContainer.addView(addSheetBtn,LinearLayout.LayoutParams((20*d).toInt(),(20*d).toInt()).apply {
    leftMargin=(4*d).toInt(); bottomMargin=(2*d).toInt()
   })
  }
  sheetBar.addView(TextView(this).apply {
   text="◂  ▸"; textSize=10.5f; setTextColor(Color.rgb(122,126,134)); setPadding(0,0,(10*d).toInt(),0)
  })
  sheetBar.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(sheetTabContainer) },LinearLayout.LayoutParams(0,(26*d).toInt(),1f))
  page.addView(sheetBar,LinearLayout.LayoutParams(-1,(28*d).toInt()))

  // 5. Bottom Status Bar (Ready | View toggles | Zoom Slider - o + 100%)
  val statusBar=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((10*d).toInt(),(2*d).toInt(),(10*d).toInt(),(2*d).toInt())
   setBackgroundColor(ExcelTheme.RIBBON_BG)
  }
  statusText=TextView(this).apply {
   text="Ready"; textSize=10f; setTextColor(ExcelTheme.TEXT_MUTED); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
  }
  val viewNormalBtn=TextView(this).apply {
   text="⊞"; textSize=12f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.TEXT_DARK); contentDescription="عرض عادي"
   setOnClickListener { showFormulasMode=false; refreshSelectionAndCells() }
  }
  val viewStripeBtn=TextView(this).apply {
   text="▤"; textSize=12f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.TEXT_DARK); contentDescription="صفوف مخططة"
   setOnClickListener { val sh=sheets[activeSheetIdx]; sh.stripedRows=!sh.stripedRows; saveWorkbook(); refreshSelectionAndCells() }
  }
  val viewFxBtn=TextView(this).apply {
   text="fx"; textSize=10f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.TEXT_DARK); contentDescription="عرض الصيغ"
   typeface=Typeface.create("serif",Typeface.ITALIC or Typeface.BOLD)
   setOnClickListener { showFormulasMode=!showFormulasMode; refreshSelectionAndCells() }
  }
  val zoomOutBtn=TextView(this).apply {
   text="−"; textSize=13f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.TEXT_DARK)
   setOnClickListener { zoomPct=(zoomPct-10).coerceAtLeast(70); zoomSlider.progress=zoomPct-70; rebuildGrid() }
  }
  zoomSlider=SeekBar(this).apply {
   max=70; progress=30 // 70%..140%
   setPadding((2*d).toInt(),0,(2*d).toInt(),0)
   progressTintList=android.content.res.ColorStateList.valueOf(ExcelTheme.EXCEL_GREEN)
   thumbTintList=android.content.res.ColorStateList.valueOf(ExcelTheme.EXCEL_GREEN)
   setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
    override fun onStartTrackingTouch(bar:SeekBar?) {}
    override fun onStopTrackingTouch(bar:SeekBar?) { rebuildGrid() }
    override fun onProgressChanged(bar:SeekBar?,p:Int,fromUser:Boolean) {
     if(fromUser) { zoomPct=70+p; zoomLabel.text="${zoomPct}%" }
    }
   })
  }
  val zoomInBtn=TextView(this).apply {
   text="+"; textSize=13f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.TEXT_DARK)
   setOnClickListener { zoomPct=(zoomPct+10).coerceAtMost(140); zoomSlider.progress=zoomPct-70; rebuildGrid() }
  }
  zoomLabel=TextView(this).apply { text="100%"; textSize=10f; gravity=Gravity.CENTER; setTextColor(ExcelTheme.TEXT_DARK) }

  statusBar.addView(statusText,LinearLayout.LayoutParams(0,-2,1f))
  statusBar.addView(viewNormalBtn,LinearLayout.LayoutParams((24*d).toInt(),(22*d).toInt()))
  statusBar.addView(viewStripeBtn,LinearLayout.LayoutParams((24*d).toInt(),(22*d).toInt()))
  statusBar.addView(viewFxBtn,LinearLayout.LayoutParams((24*d).toInt(),(22*d).toInt()).apply { rightMargin=(8*d).toInt() })
  statusBar.addView(zoomOutBtn,LinearLayout.LayoutParams((20*d).toInt(),(22*d).toInt()))
  statusBar.addView(zoomSlider,LinearLayout.LayoutParams((76*d).toInt(),(20*d).toInt()))
  statusBar.addView(zoomInBtn,LinearLayout.LayoutParams((20*d).toInt(),(22*d).toInt()))
  statusBar.addView(zoomLabel,LinearLayout.LayoutParams((36*d).toInt(),(22*d).toInt()))
  page.addView(View(this).apply { setBackgroundColor(Color.rgb(212,212,212)) },LinearLayout.LayoutParams(-1,1))
  page.addView(statusBar,LinearLayout.LayoutParams(-1,(26*d).toInt()))

  // CSV Import Handler
  onExcelCsvPickedCallback={ uri ->
   try {
    val rawText=contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
    val parsed=ExcelCsvParser.parse(rawText)
    if(parsed.isNotEmpty()) {
     pushUndo()
     val sh=sheets[activeSheetIdx]
     numRows=maxOf(16,parsed.size.coerceAtMost(60))
     sh.cells=MutableList(numRows*numCols) { "" }
     parsed.take(numRows).forEachIndexed { r,rowVals ->
      rowVals.take(numCols).forEachIndexed { c,v -> sh.cells[r*numCols+c]=v }
     }
     sh.tableFilterOn=true; sh.stripedRows=true; sh.activeFilterVal=null
     val fileName=try {
      contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
       if(it.moveToFirst()) it.getString(0) else null
      }
     } catch(_:Exception) { null }
     if(!fileName.isNullOrBlank()) {
      sh.name=fileName.substringBeforeLast('.').take(16)
      windowTitleLabel?.text="$fileName - Excel"
     }
     saveWorkbook(); rebuildSheetTabs(); rebuildGrid()
     Toast.makeText(this,"تم استيراد ${parsed.size} صف من CSV",Toast.LENGTH_SHORT).show()
    } else Toast.makeText(this,"ملف CSV فارغ",Toast.LENGTH_SHORT).show()
   } catch(_:Exception) {
    Toast.makeText(this,"تعذر قراءة ملف CSV",Toast.LENGTH_SHORT).show()
   }
  }
  page.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener {
   override fun onViewAttachedToWindow(v:View) {}
   override fun onViewDetachedFromWindow(v:View) { onExcelCsvPickedCallback=null }
  })

  renderRibbonTools(3)
  rebuildSheetTabs()
  rebuildGrid()
  panel(page)
 }
 private fun showGames() {
  shell("ألعاب Windows")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text="ألعاب كلاسيكية بسيطة"; textSize=19.36f; typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy); setPadding(6,4,6,10) })
  page.addView(action("الثعبان — Snake"){showSnakeGame()})
  page.addView(action("تحدي الأرقام"){showNumberGame()})
  page.addView(TextView(this).apply { text="ألعاب أصلية مرخصة من Microsoft غير مضمنة. هذه ألعاب بسيطة مدمجة في Oriket Desktop."; textSize=10.56f; setTextColor(navy); setPadding(8,12,8,8) })
  panel(page)
 }
 private fun showNumberGame() {
  var target=(1..20).random(); var attempts=0
  val input=EditText(this).apply { hint="خمن رقمًا من 1 إلى 20"; inputType=InputType.TYPE_CLASS_NUMBER }
  AlertDialog.Builder(this).setTitle("تحدي الأرقام").setView(input)
   .setPositiveButton("تحقق"){_,_-> attempts++; val n=input.text.toString().toIntOrNull(); val msg=when { n==null->"أدخل رقمًا صحيحًا"; n==target->"أحسنت! عدد المحاولات: $attempts"; n<target->"الرقم أكبر"; else->"الرقم أصغر" }; Toast.makeText(this,msg,Toast.LENGTH_LONG).show(); if(n!=target) showNumberGameRound(target,attempts) }
   .setNegativeButton("إغلاق",null).show()
 }
 private fun showNumberGameRound(target:Int,attempts:Int) {
  val input=EditText(this).apply { hint="محاولة جديدة"; inputType=InputType.TYPE_CLASS_NUMBER }
  AlertDialog.Builder(this).setTitle("تحدي الأرقام").setMessage("عدد المحاولات: $attempts").setView(input).setPositiveButton("تحقق"){_,_-> val n=input.text.toString().toIntOrNull(); val msg=when { n==null->"أدخل رقمًا"; n==target->"أحسنت!"; n<target->"الرقم أكبر"; else->"الرقم أصغر" }; Toast.makeText(this,msg,Toast.LENGTH_LONG).show(); if(n!=target) showNumberGameRound(target,attempts+1) }.setNegativeButton("إغلاق",null).show()
 }
 private fun showSnakeGame() {
  shell("Snake")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val info=TextView(this).apply { text="لعبة الثعبان البسيطة — استخدم أزرار الاتجاهات"; gravity=Gravity.CENTER; setTextColor(navy) }
  val game=SnakeView(this)
  page.addView(info)
  page.addView(game,LinearLayout.LayoutParams(-1,0,1f))
  val controls=GridLayout(this).apply { columnCount=3 }
  listOf("↑","←","↓","→").forEach { dir->controls.addView(action(dir){game.move(dir)},GridLayout.LayoutParams().apply{width=0;height=48;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(2,2,2,2)})}
  page.addView(controls)
  page.addView(action("إعادة اللعبة"){game.reset()})
  panel(page)
 }
 private inner class SnakeView(c:Context):View(c) {
  private val p=Paint(3); private var snake=mutableListOf(Pair(5,5),Pair(4,5),Pair(3,5)); private var food=Pair(9,7); private var dx=1; private var dy=0; private var score=0
  override fun onDraw(canvas:Canvas) {
   super.onDraw(canvas); canvas.drawColor(Color.rgb(246,250,246)); val cell=(width.coerceAtMost(height)/14f).coerceAtLeast(8f); val ox=(width-cell*14)/2; val oy=(height-cell*14)/2
   p.color=Color.rgb(40,160,80); snake.forEach{canvas.drawRoundRect(ox+it.first*cell,oy+it.second*cell,ox+(it.first+1)*cell-2,oy+(it.second+1)*cell-2,5f,5f,p)}
   p.color=Color.RED; canvas.drawCircle(ox+(food.first+.5f)*cell,oy+(food.second+.5f)*cell,cell*.35f,p)
   p.color=navy; p.textSize=15.84f; canvas.drawText("Score: $score",12f,24f,p)
  }
  fun move(d:String) {
   when(d){"↑"->{dx=0;dy=-1};"↓"->{dx=0;dy=1};"←"->{dx=-1;dy=0};"→"->{dx=1;dy=0}}
   val head=snake.first(); val next=Pair((head.first+dx+14)%14,(head.second+dy+14)%14)
   snake.add(0,next)
   if(next==food){score++; food=Pair((0..13).random(),(0..13).random())} else snake.removeAt(snake.lastIndex)
   if(snake.drop(1).contains(next)){reset(); Toast.makeText(context,"انتهت اللعبة",Toast.LENGTH_SHORT).show()} else invalidate()
  }
  fun reset(){snake=mutableListOf(Pair(5,5),Pair(4,5),Pair(3,5));food=Pair(9,7);dx=1;dy=0;score=0;invalidate()}
 }

 private fun showPrivacyPolicy() {
  AlertDialog.Builder(this)
   .setTitle("سياسة الخصوصية")
   .setMessage("تُحفظ إعدادات سطح المكتب محليًا على الجهاز. عند اختيار ملف، يستخدم التطبيق منتقي ملفات Android. المواقع التي تفتحها قد تعالج بيانات وفق سياساتها الخاصة. نسخة Google Play لا تثبّت تحديثات APK من خارج المتجر. اقرأ السياسة الكاملة قبل الاستخدام.")
   .setPositiveButton("قراءة السياسة الكاملة") { _,_ ->
    try {
     startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://mohmedelmotaz151-ctrl.github.io/Oriket-desktop-/privacy-policy.html")))
    } catch(_:Exception) {
     Toast.makeText(this,"تعذر فتح صفحة سياسة الخصوصية",Toast.LENGTH_LONG).show()
    }
   }
   .setNegativeButton("إغلاق",null)
   .show()
 }

 private fun showWallpaperPicker() {
  val labels=arrayOf("Windows أزرق","داكن","فاتح")
  val current=getSharedPreferences("orkeit_desktop",Context.MODE_PRIVATE).getInt("wallpaper_style",0)
  AlertDialog.Builder(this)
   .setTitle("خلفية سطح المكتب")
   .setSingleChoiceItems(labels,current) { dialog,which ->
    getSharedPreferences("orkeit_desktop",Context.MODE_PRIVATE).edit().putInt("wallpaper_style",which).apply()
    if(desktopArea.childCount>0) desktopArea.getChildAt(0).invalidate()
    Toast.makeText(this,"تم حفظ الخلفية",Toast.LENGTH_SHORT).show()
    dialog.dismiss()
   }
   .setNegativeButton("إلغاء",null)
   .show()
 }

 private fun addDesktopNotice(title:String,message:String) {
  desktopNotices.add(0,DesktopNotice(title,message))
  while(desktopNotices.size>30) desktopNotices.removeAt(desktopNotices.lastIndex)
  notificationButton?.text=if(desktopNotices.isEmpty()) "♧" else "♧ •"
 }
 private fun showNotificationCenter() {
  val panel=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; setPadding(16,14,16,12)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(247,249,253)); cornerRadius=22f; setStroke(1,border) }
  }
  val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  header.addView(TextView(this).apply {
   text="مركز الإشعارات"; textSize=18f; typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy)
  },LinearLayout.LayoutParams(0,44,1f))
  header.addView(task("مسح الكل") {
   desktopNotices.clear()
   notificationButton?.text="♧"
   Toast.makeText(this,"تم مسح الإشعارات",Toast.LENGTH_SHORT).show()
   showNotificationCenter()
  })
  panel.addView(header)
  val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  if(desktopNotices.isEmpty()) {
   list.addView(TextView(this).apply {
    text="لا توجد إشعارات جديدة\nستظهر هنا تنبيهات التطبيق والتنزيلات والتحديثات."
    textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.rgb(105,118,138)); setPadding(12,36,12,36)
   },LinearLayout.LayoutParams(-1,-2))
  } else {
   desktopNotices.forEach { notice ->
    val card=LinearLayout(this).apply {
     orientation=LinearLayout.VERTICAL; setPadding(12,9,12,9)
     background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=13f; setStroke(1,border) }
    }
    card.addView(TextView(this).apply {
     text=notice.title; textSize=13f; typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy)
    })
    card.addView(TextView(this).apply {
     text=notice.message; textSize=11.5f; setTextColor(Color.rgb(65,78,98)); setPadding(0,4,0,3)
    })
    card.addView(TextView(this).apply {
     text=java.text.SimpleDateFormat("HH:mm  dd/MM/yyyy",Locale.getDefault()).format(java.util.Date(notice.time))
     textSize=9.5f; setTextColor(Color.rgb(125,137,154))
    })
    list.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=7 })
   }
  }
  panel.addView(ScrollView(this).apply { isFillViewport=true; addView(list) },LinearLayout.LayoutParams(-1,0,1f))
  panel.addView(action("إعدادات إشعارات التطبيق") {
   try { startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName)) }
   catch(_:Exception) { try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch(_:Exception) {} }
  })
  AlertDialog.Builder(this).setView(panel).setPositiveButton("إغلاق",null).show()
 }
 private fun showQuickSettings() {
  fun openSystemSetting(action:String, fallback:String=android.provider.Settings.ACTION_SETTINGS) {
   try { startActivity(Intent(action)) }
   catch(_:Exception) { try { startActivity(Intent(fallback)) } catch(_:Exception) { Toast.makeText(this,"تعذر فتح إعدادات الجهاز",Toast.LENGTH_SHORT).show() } }
  }
  val box=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; setPadding(18,16,18,14)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(246,249,255)); cornerRadius=24f; setStroke(1,border) }
  }
  val titleRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  titleRow.addView(TextView(this).apply {
   text="مركز التحكم"; textSize=18f; typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy)
  },LinearLayout.LayoutParams(0,42,1f))
  titleRow.addView(TextView(this).apply {
   text="Oriket"; textSize=10.5f; setTextColor(Color.rgb(95,112,135))
  })
  box.addView(titleRow)
  box.addView(TextView(this).apply {
   text="اختصارات سريعة لإعدادات Android"; textSize=10.5f; setTextColor(Color.rgb(105,118,138)); setPadding(2,0,2,12)
  })
  val grid=GridLayout(this).apply { columnCount=2; useDefaultMargins=false }
  val settings=listOf(
   Triple("◉","Wi-Fi والشبكة",android.provider.Settings.ACTION_WIFI_SETTINGS),
   Triple("◈","Bluetooth",android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
   Triple("♫","الصوت",android.provider.Settings.ACTION_SOUND_SETTINGS),
   Triple("☼","الشاشة والسطوع",android.provider.Settings.ACTION_DISPLAY_SETTINGS),
   Triple("🔋","البطارية",android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS),
   Triple("◷","التاريخ والوقت",android.provider.Settings.ACTION_DATE_SETTINGS),
   Triple("▣","الإشعارات",android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS),
   Triple("⚙","إعدادات الجهاز",android.provider.Settings.ACTION_SETTINGS)
  )
  settings.forEach { item ->
   val tile=LinearLayout(this).apply {
    orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(10,7,10,7)
    background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=13f; setStroke(1,Color.rgb(222,229,239)) }
    isClickable=true; isFocusable=true
    setOnClickListener {
     if(item.third==android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
      try { startActivity(Intent(item.third).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName)) }
      catch(_:Exception) { openSystemSetting(android.provider.Settings.ACTION_SETTINGS) }
     } else openSystemSetting(item.third)
    }
   }
   val qIcon=ImageView(this).apply {
    val fIcon=when(item.second) {
     "Wi-Fi والشبكة" -> FluentIcon.WIFI
     "Bluetooth" -> FluentIcon.BLUETOOTH
     "الصوت" -> FluentIcon.MUSIC
     "الشاشة والسطوع" -> FluentIcon.THIS_PC
     "البطارية" -> FluentIcon.BATTERY
     "التاريخ والوقت" -> FluentIcon.SETTINGS
     "الإشعارات" -> FluentIcon.NOTIFICATIONS
     "إعدادات الجهاز" -> FluentIcon.SETTINGS
     else -> FluentIcon.SETTINGS
    }
    setImageDrawable(FluentIcons.createDrawable(this@MainActivity,fIcon,(24*resources.displayMetrics.density).toInt()))
   }
   tile.addView(qIcon,LinearLayout.LayoutParams((34*resources.displayMetrics.density).toInt(),(38*resources.displayMetrics.density).toInt()))
   tile.addView(TextView(this).apply { text=item.second; textSize=11.2f; typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy); gravity=Gravity.CENTER_VERTICAL },LinearLayout.LayoutParams(0,42,1f))
   grid.addView(tile,GridLayout.LayoutParams().apply {
    width=0; height=58; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(4,4,4,4)
   })
  }
  box.addView(grid)
  val divider=View(this).apply { setBackgroundColor(Color.rgb(224,230,239)) }
  box.addView(divider,LinearLayout.LayoutParams(-1,1).apply { topMargin=10; bottomMargin=8 })
  box.addView(TextView(this).apply {
   text="إظهار أو إخفاء أشرطة Android"; textSize=11.5f; typeface=Typeface.create("Arial",Typeface.BOLD); setTextColor(navy); setPadding(2,2,2,6)
  })
  box.addView(action("تبديل وضع ملء الشاشة") {
   val hidden=(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN)!=0
   window.decorView.systemUiVisibility=if(hidden) View.SYSTEM_UI_FLAG_LAYOUT_STABLE else
    (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
  })
  box.addView(TextView(this).apply {
   text="Oriket Desktop • ${BuildConfig.VERSION_NAME}"; textSize=9.8f; setTextColor(Color.rgb(110,122,140)); gravity=Gravity.CENTER; setPadding(4,12,4,0)
  })
  AlertDialog.Builder(this).setView(box).setPositiveButton("إغلاق",null).show()
 }

 private fun showAbout() {
  shell("حول التطبيق")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,20,20,20) }
  page.addView(TextView(this).apply { text="Oriket Desktop\nواجهة أفقية مستوحاة من Windows 11. المتصفح والرسام والحاسبة والمفكرة ومعاينة الصور والنصوص وPDF تعمل داخل التطبيق.\n\nهذه واجهة سطح مكتب وليست نظام Windows فعليًا ولا تشغّل ملفات EXE."; textSize=16.72f; setTextColor(navy) })
  panel(page)
 }
 private fun showPaint() {
  shell("Paint")
  val d=resources.displayMetrics.density
  content.setPadding(0,0,0,0)
  content.setBackgroundColor(Win11PaintTheme.CANVAS_STAGE_BG)

  val outerWindow=content.parent as? LinearLayout
  val windowTitleBar=outerWindow?.getChildAt(0) as? LinearLayout
  windowTitleBar?.setBackgroundColor(Win11PaintTheme.MICA_BG)

  val canvas=PaintCanvas(this).apply { tag="orkeit-paint-canvas" }
  var color1=Color.BLACK
  var color2=Color.WHITE
  var editingColorSlot=1
  var currentTool="قلم"
  var currentBrushSize=5f
  var gridlinesOn=false
  val customColors=MutableList(10) { Color.TRANSPARENT }

  val page=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_LTR
   setBackgroundColor(Win11PaintTheme.MICA_BG)
  }

  lateinit var refreshRibbonSelection:()->Unit
  lateinit var updateStatusBar:()->Unit
  lateinit var color1Btn:View
  lateinit var color2Btn:View
  val customSwatches=mutableListOf<View>()
  val toolContainers=mutableMapOf<String,View>()

  fun applyColorChoice(col:Int) {
   if(editingColorSlot==1) {
    color1=col
    canvas.setColor(col)
    color1Btn.background=Win11PaintTheme.primarySwatchCircle(d,color1,true)
   } else {
    color2=col
    color2Btn.background=Win11PaintTheme.primarySwatchCircle(d,color2,true)
   }
  }

  fun selectTool(toolName:String) {
   currentTool=toolName
   canvas.setTool(toolName)
   refreshRibbonSelection()
   updateStatusBar()
  }

  fun openImagePicker() {
   try {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
     addCategory(Intent.CATEGORY_OPENABLE)
     type="image/*"
    },62)
   } catch(_:Exception) { Toast.makeText(this,"تعذر فتح الصور",Toast.LENGTH_SHORT).show() }
  }

  fun showCustomColorDialog() {
   val box=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL
    setPadding((16*d).toInt(),(10*d).toInt(),(16*d).toInt(),(6*d).toInt())
   }
   val preview=View(this).apply {
    background=android.graphics.drawable.GradientDrawable().apply {
     setColor(if(editingColorSlot==1) color1 else color2); cornerRadius=8f*d; setStroke(1,Color.GRAY)
    }
   }
   val hexInput=EditText(this).apply {
    hint="#3366FF"; setSingleLine(true)
    setText(String.format("#%06X",0xFFFFFF and (if(editingColorSlot==1) color1 else color2)))
   }
   var chosen=if(editingColorSlot==1) color1 else color2
   fun updateFromRgb(r:Int,g:Int,b:Int) {
    chosen=Color.rgb(r,g,b)
    preview.background=android.graphics.drawable.GradientDrawable().apply {
     setColor(chosen); cornerRadius=8f*d; setStroke(1,Color.GRAY)
    }
    hexInput.setText(String.format("#%06X",0xFFFFFF and chosen))
   }
   val rBar=SeekBar(this).apply { max=255; progress=Color.red(chosen) }
   val gBar=SeekBar(this).apply { max=255; progress=Color.green(chosen) }
   val bBar=SeekBar(this).apply { max=255; progress=Color.blue(chosen) }
   val rgbListener=object:SeekBar.OnSeekBarChangeListener {
    override fun onStartTrackingTouch(p0:SeekBar?) {}
    override fun onStopTrackingTouch(p0:SeekBar?) {}
    override fun onProgressChanged(p0:SeekBar?,p1:Int,fromUser:Boolean) {
     if(fromUser) updateFromRgb(rBar.progress,gBar.progress,bBar.progress)
    }
   }
   rBar.setOnSeekBarChangeListener(rgbListener)
   gBar.setOnSeekBarChangeListener(rgbListener)
   bBar.setOnSeekBarChangeListener(rgbListener)
   box.addView(preview,LinearLayout.LayoutParams(-1,(36*d).toInt()).apply { bottomMargin=(8*d).toInt() })
   box.addView(TextView(this).apply { text="أحمر (R)" }); box.addView(rBar)
   box.addView(TextView(this).apply { text="أخضر (G)" }); box.addView(gBar)
   box.addView(TextView(this).apply { text="أزرق (B)" }); box.addView(bBar)
   box.addView(hexInput)
   AlertDialog.Builder(this).setTitle("تعديل الألوان (Edit Colors)").setView(box)
    .setPositiveButton("تطبيق") { _,_ ->
     val finalColor=try { Color.parseColor(hexInput.text.toString().trim()) } catch(_:Exception) { chosen }
     applyColorChoice(finalColor)
     val emptySlot=customColors.indexOfFirst { it==Color.TRANSPARENT }.let { if(it>=0) it else 0 }
     customColors[emptySlot]=finalColor
     customSwatches.getOrNull(emptySlot)?.background=Win11PaintTheme.swatchCircle(d,finalColor,true)
    }
    .setNegativeButton("إلغاء",null).show()
  }

  // 1. Top Menu Row (File | Edit | View | Save | Undo | Redo ... Grid / Settings)
  val menuRow=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((10*d).toInt(),(2*d).toInt(),(10*d).toInt(),(2*d).toInt())
   setBackgroundColor(Win11PaintTheme.MICA_BG)
  }
  fun makeTopMenuBtn(label:String,onClick:(View)->Unit):TextView = TextView(this).apply {
   text=label; textSize=11f; gravity=Gravity.CENTER; setTextColor(Win11PaintTheme.TEXT_DARK)
   setPadding((9*d).toInt(),(3*d).toInt(),(9*d).toInt(),(3*d).toInt())
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=4f*d }
   setOnClickListener(onClick)
  }
  val fileMenuBtn=makeTopMenuBtn("File") { anchor ->
   android.widget.PopupMenu(this,anchor).apply {
    menu.add("جديد (مسح اللوحة)").setOnMenuItemClickListener { canvas.clear(); true }
    menu.add("فتح صورة...").setOnMenuItemClickListener { openImagePicker(); true }
    menu.add("حفظ كصورة PNG").setOnMenuItemClickListener { saveDrawing(canvas); true }
    menu.add("خلفية بيضاء").setOnMenuItemClickListener { canvas.setWhiteBackground(); true }
    show()
   }
  }
  val editMenuBtn=makeTopMenuBtn("Edit") { anchor ->
   android.widget.PopupMenu(this,anchor).apply {
    menu.add("تراجع (Undo)").setOnMenuItemClickListener { canvas.undo(); true }
    menu.add("إعادة (Redo)").setOnMenuItemClickListener { canvas.redo(); true }
    menu.add("قص إلى التحديد (Crop)").setOnMenuItemClickListener { canvas.applyCrop(); true }
    menu.add("إلغاء التحديد").setOnMenuItemClickListener { canvas.clearSelection(); true }
    menu.add("تدوير 90° لليمين").setOnMenuItemClickListener { canvas.rotate90(); true }
    menu.add("قلب أفقي").setOnMenuItemClickListener { canvas.flipHorizontal(); true }
    show()
   }
  }
  val viewMenuBtn=makeTopMenuBtn("View") { anchor ->
   android.widget.PopupMenu(this,anchor).apply {
    menu.add(if(gridlinesOn) "✓ شبكة الرسم (Gridlines)" else "شبكة الرسم (Gridlines)").setOnMenuItemClickListener {
     gridlinesOn=!gridlinesOn; canvas.setShowGrid(gridlinesOn); true
    }
    menu.add("تكبير 150%").setOnMenuItemClickListener { canvas.setZoomFactor(1.5f); updateStatusBar(); true }
    menu.add("حجم قياسي 100%").setOnMenuItemClickListener { canvas.setZoomFactor(1.0f); updateStatusBar(); true }
    menu.add("تصغير 75%").setOnMenuItemClickListener { canvas.setZoomFactor(0.75f); updateStatusBar(); true }
    show()
   }
  }
  val quickSaveBtn=makeTopMenuBtn("💾") { saveDrawing(canvas) }.apply { contentDescription="حفظ الرسم" }
  val quickUndoBtn=makeTopMenuBtn("↶") { canvas.undo() }.apply { contentDescription="تراجع"; textSize=13f }
  val quickRedoBtn=makeTopMenuBtn("↷") { canvas.redo() }.apply { contentDescription="إعادة"; textSize=13f }
  val gridToggleBtn=makeTopMenuBtn("⊞") { gridlinesOn=!gridlinesOn; canvas.setShowGrid(gridlinesOn) }.apply { contentDescription="شبكة الرسم" }

  menuRow.addView(fileMenuBtn)
  menuRow.addView(editMenuBtn)
  menuRow.addView(viewMenuBtn)
  menuRow.addView(View(this).apply { setBackgroundColor(Win11PaintTheme.BORDER_SUBTLE) },LinearLayout.LayoutParams(1,(16*d).toInt()).apply {
   leftMargin=(4*d).toInt(); rightMargin=(4*d).toInt()
  })
  menuRow.addView(quickSaveBtn)
  menuRow.addView(quickUndoBtn)
  menuRow.addView(quickRedoBtn)
  menuRow.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
  menuRow.addView(gridToggleBtn)
  page.addView(menuRow,LinearLayout.LayoutParams(-1,(28*d).toInt()))

  // 2. Windows 11 Paint Ribbon Bar
  val ribbonCard=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((8*d).toInt(),(4*d).toInt(),(8*d).toInt(),(4*d).toInt())
   setBackgroundColor(Win11PaintTheme.RIBBON_BG)
  }
  fun ribbonGroup(title:String,build:(LinearLayout)->Unit) {
   val col=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL }
   val body=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
   build(body)
   col.addView(body,LinearLayout.LayoutParams(-2,(46*d).toInt()))
   col.addView(TextView(this).apply {
    text=title; textSize=8.5f; gravity=Gravity.CENTER; setTextColor(Win11PaintTheme.TEXT_MUTED); maxLines=1
   },LinearLayout.LayoutParams(-2,-2))
   ribbonCard.addView(col)
   ribbonCard.addView(View(this).apply { setBackgroundColor(Win11PaintTheme.BORDER_SUBTLE) },LinearLayout.LayoutParams(1,(50*d).toInt()).apply {
    leftMargin=(6*d).toInt(); rightMargin=(6*d).toInt()
   })
  }

  fun makeIconCell(glyph:String,toolKey:String?=null,wDp:Int=26,hDp:Int=22,onClick:(View)->Unit):TextView {
   val tv=TextView(this).apply {
    text=glyph; textSize=12f; gravity=Gravity.CENTER; setTextColor(Win11PaintTheme.TEXT_DARK)
    background=Win11PaintTheme.toolButtonBg(d,false)
    setOnClickListener { onClick(it) }
   }
   if(toolKey!=null) toolContainers[toolKey]=tv
   return tv
  }

  // Group 1: Selection
  ribbonGroup("Selection") { g ->
   val selBtn=makeIconCell("⛶","تحديد",38,40) { selectTool("تحديد") }.apply { textSize=17f }
   g.addView(selBtn,LinearLayout.LayoutParams((38*d).toInt(),(40*d).toInt()))
  }

  // Group 2: Image (Crop, Rotate/Flip, Fill Toggle)
  ribbonGroup("Image") { g ->
   val col=GridLayout(this).apply { columnCount=2; rowCount=2 }
   val cropBtn=makeIconCell("✂","قص",26,22) {
    if(canvas.hasSelection()) canvas.applyCrop() else selectTool("قص")
   }
   val rotBtn=makeIconCell("↻",null,26,22) { canvas.rotate90() }
   val flipBtn=makeIconCell("⇄",null,26,22) { canvas.flipHorizontal() }
   val fillShapeBtn=makeIconCell("◩",null,26,22) {
    val next=!canvas.isShapeFilled()
    canvas.setFilled(next)
    it.background=Win11PaintTheme.toolButtonBg(d,next)
   }
   listOf(cropBtn,rotBtn,flipBtn,fillShapeBtn).forEach { b ->
    col.addView(b,GridLayout.LayoutParams().apply {
     width=(26*d).toInt(); height=(22*d).toInt(); setMargins((1*d).toInt(),(1*d).toInt(),(1*d).toInt(),(1*d).toInt())
    })
   }
   g.addView(col)
  }

  // Group 3: Tools (2x3 grid matching Windows 11 Paint: Pencil, Fill, Text, Eraser, Eyedropper, Zoom)
  ribbonGroup("Tools") { g ->
   val grid=GridLayout(this).apply { columnCount=3; rowCount=2 }
   val items=listOf(
    "✎" to "قلم رصاص",
    "🪣" to "تعبئة",
    "A" to "نص",
    "▭" to "ممحاة",
    "🧪" to "قطارة",
    "🔍" to "مكبر"
   )
   items.forEach { (glyph,key) ->
    val btn=makeIconCell(glyph,key,26,22) {
     if(key=="مكبر") {
      val nextZoom=if(canvas.getZoomFactor()>=2f) 1f else canvas.getZoomFactor()+0.25f
      canvas.setZoomFactor(nextZoom)
      updateStatusBar()
     } else selectTool(key)
    }
    grid.addView(btn,GridLayout.LayoutParams().apply {
     width=(26*d).toInt(); height=(22*d).toInt(); setMargins((1*d).toInt(),(1*d).toInt(),(1*d).toInt(),(1*d).toInt())
    })
   }
   g.addView(grid)
  }

  // Group 4: Brushes dropdown
  ribbonGroup("Brushes") { g ->
   val brushBtn=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
    setPadding((6*d).toInt(),(2*d).toInt(),(6*d).toInt(),(2*d).toInt())
    background=Win11PaintTheme.toolButtonBg(d,true)
    setOnClickListener { anchor ->
     android.widget.PopupMenu(this@MainActivity,anchor).apply {
      listOf("قلم" to "قلم ناعم","فرشاة" to "فرشاة زيتية","قلم رصاص" to "قلم رصاص","تحديد عريض" to "قلم خطاط").forEach { (k,label) ->
       menu.add(label).setOnMenuItemClickListener { selectTool(k); true }
      }
      show()
     }
    }
   }
   toolContainers["قلم"]=brushBtn
   toolContainers["فرشاة"]=brushBtn
   toolContainers["تحديد عريض"]=brushBtn
   brushBtn.addView(TextView(this).apply { text="🖌"; textSize=16f; gravity=Gravity.CENTER })
   brushBtn.addView(TextView(this).apply { text="▾"; textSize=9f; gravity=Gravity.CENTER; setTextColor(Win11PaintTheme.TEXT_MUTED) })
   g.addView(brushBtn,LinearLayout.LayoutParams((36*d).toInt(),(42*d).toInt()))
  }

  // Group 5: Shapes (compact 2x3 shape grid)
  ribbonGroup("Shapes") { g ->
   val grid=GridLayout(this).apply { columnCount=3; rowCount=2 }
   listOf("◯" to "دائرة","▭" to "مستطيل","▱" to "مستطيل دائري","△" to "مثلث","╱" to "خط","↗" to "سهم").forEach { (glyph,key) ->
    val btn=makeIconCell(glyph,key,24,22) { selectTool(key) }.apply { textSize=11f }
    grid.addView(btn,GridLayout.LayoutParams().apply {
     width=(24*d).toInt(); height=(22*d).toInt(); setMargins((1*d).toInt(),(1*d).toInt(),(1*d).toInt(),(1*d).toInt())
    })
   }
   g.addView(grid)
  }

  // Group 6: Size selector
  ribbonGroup("Size") { g ->
   val sizeBtn=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
    setPadding((6*d).toInt(),(2*d).toInt(),(6*d).toInt(),(2*d).toInt())
    background=Win11PaintTheme.toolButtonBg(d,false)
    setOnClickListener {
     val wrap=LinearLayout(this@MainActivity).apply {
      orientation=LinearLayout.VERTICAL; setPadding((16*d).toInt(),(10*d).toInt(),(16*d).toInt(),(8*d).toInt())
     }
     val lbl=TextView(this@MainActivity).apply { text="حجم الأداة: ${currentBrushSize.toInt()} px"; textSize=12f }
     val bar=SeekBar(this@MainActivity).apply {
      max=49; progress=(currentBrushSize.toInt()-1).coerceIn(0,49)
      setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
       override fun onStartTrackingTouch(p0:SeekBar?) {}
       override fun onStopTrackingTouch(p0:SeekBar?) {}
       override fun onProgressChanged(p0:SeekBar?,p:Int,fromUser:Boolean) {
        currentBrushSize=(p+1).toFloat(); lbl.text="حجم الأداة: ${p+1} px"
        canvas.setBrushSize(currentBrushSize); updateStatusBar()
       }
      })
     }
     val quickRow=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
     listOf(2,5,10,20,35).forEach { sz ->
      quickRow.addView(task("${sz}px") {
       currentBrushSize=sz.toFloat(); bar.progress=sz-1; lbl.text="حجم الأداة: $sz px"
       canvas.setBrushSize(currentBrushSize); updateStatusBar()
      },LinearLayout.LayoutParams(0,(36*d).toInt(),1f).apply { setMargins(2,4,2,0) })
     }
     wrap.addView(lbl); wrap.addView(bar); wrap.addView(quickRow)
     AlertDialog.Builder(this@MainActivity).setTitle("حجم الفرشاة والأداة").setView(wrap).setPositiveButton("تم",null).show()
    }
   }
   sizeBtn.addView(TextView(this).apply { text="≡"; textSize=16f; gravity=Gravity.CENTER; setTextColor(Win11PaintTheme.TEXT_DARK) })
   sizeBtn.addView(TextView(this).apply { text="▾"; textSize=9f; gravity=Gravity.CENTER; setTextColor(Win11PaintTheme.TEXT_MUTED) })
   g.addView(sizeBtn,LinearLayout.LayoutParams((32*d).toInt(),(42*d).toInt()))
  }

  // Group 7: Colors (Color1/Color2 + 3x10 Circular Palette + Rainbow Color Wheel)
  ribbonGroup("Colors") { g ->
   val primaryStack=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
    setPadding(0,0,(6*d).toInt(),0)
   }
   color1Btn=View(this).apply {
    background=Win11PaintTheme.primarySwatchCircle(d,color1,true)
    contentDescription="اللون الأساسي"
    setOnClickListener {
     editingColorSlot=1
     canvas.setColor(color1)
     color1Btn.background=Win11PaintTheme.primarySwatchCircle(d,color1,true)
     color2Btn.background=Win11PaintTheme.primarySwatchCircle(d,color2,false)
    }
   }
   color2Btn=View(this).apply {
    background=Win11PaintTheme.primarySwatchCircle(d,color2,false)
    contentDescription="اللون الثانوي"
    setOnClickListener {
     editingColorSlot=2
     canvas.setColor(color2)
     color1Btn.background=Win11PaintTheme.primarySwatchCircle(d,color1,false)
     color2Btn.background=Win11PaintTheme.primarySwatchCircle(d,color2,true)
    }
   }
   primaryStack.addView(color1Btn,LinearLayout.LayoutParams((20*d).toInt(),(20*d).toInt()).apply { bottomMargin=(3*d).toInt() })
   primaryStack.addView(color2Btn,LinearLayout.LayoutParams((18*d).toInt(),(18*d).toInt()))
   g.addView(primaryStack)

   val swatchesGrid=GridLayout(this).apply { columnCount=10; rowCount=3 }
   Win11PaintTheme.ROW1_COLORS.forEach { col ->
    val sw=View(this).apply {
     background=Win11PaintTheme.swatchCircle(d,col,false)
     contentDescription="لون"
     setOnClickListener { applyColorChoice(col) }
    }
    swatchesGrid.addView(sw,GridLayout.LayoutParams().apply {
     width=(13*d).toInt(); height=(13*d).toInt(); setMargins((1.5f*d).toInt(),(1*d).toInt(),(1.5f*d).toInt(),(1*d).toInt())
    })
   }
   Win11PaintTheme.ROW2_COLORS.forEach { col ->
    val sw=View(this).apply {
     background=Win11PaintTheme.swatchCircle(d,col,false)
     contentDescription="لون"
     setOnClickListener { applyColorChoice(col) }
    }
    swatchesGrid.addView(sw,GridLayout.LayoutParams().apply {
     width=(13*d).toInt(); height=(13*d).toInt(); setMargins((1.5f*d).toInt(),(1*d).toInt(),(1.5f*d).toInt(),(1*d).toInt())
    })
   }
   repeat(10) { slotIdx ->
    val sw=View(this).apply {
     background=Win11PaintTheme.swatchCircle(d,Color.rgb(244,246,250),false)
     contentDescription="لون مخصص"
     setOnClickListener {
      val c=customColors[slotIdx]
      if(c!=Color.TRANSPARENT) applyColorChoice(c) else showCustomColorDialog()
     }
    }
    customSwatches.add(sw)
    swatchesGrid.addView(sw,GridLayout.LayoutParams().apply {
     width=(13*d).toInt(); height=(13*d).toInt(); setMargins((1.5f*d).toInt(),(1*d).toInt(),(1.5f*d).toInt(),(1*d).toInt())
    })
   }
   g.addView(swatchesGrid,LinearLayout.LayoutParams(-2,-2).apply { rightMargin=(6*d).toInt() })

   val wheel=RainbowWheelView(this,d).apply {
    contentDescription="تعديل الألوان"
    setOnClickListener { showCustomColorDialog() }
   }
   g.addView(wheel,LinearLayout.LayoutParams((26*d).toInt(),(26*d).toInt()))
  }

  // Group 8: Photo & Reset
  ribbonGroup("Canvas") { g ->
   val openBtn=makeIconCell("🖼",null,30,36) { openImagePicker() }.apply { textSize=15f; contentDescription="فتح صورة" }
   val clearBtn=makeIconCell("🗑",null,30,36) {
    AlertDialog.Builder(this).setMessage("هل تريد مسح اللوحة بالكامل؟")
     .setPositiveButton("مسح") { _,_ -> canvas.clear() }.setNegativeButton("إلغاء",null).show()
   }.apply { textSize=14f; contentDescription="مسح اللوحة" }
   g.addView(openBtn,LinearLayout.LayoutParams((30*d).toInt(),(36*d).toInt()).apply { rightMargin=(2*d).toInt() })
   g.addView(clearBtn,LinearLayout.LayoutParams((30*d).toInt(),(36*d).toInt()))
  }

  page.addView(HorizontalScrollView(this).apply {
   isHorizontalScrollBarEnabled=false
   setBackgroundColor(Win11PaintTheme.RIBBON_BG)
   addView(ribbonCard)
  },LinearLayout.LayoutParams(-1,(66*d).toInt()))
  page.addView(View(this).apply { setBackgroundColor(Win11PaintTheme.BORDER_SUBTLE) },LinearLayout.LayoutParams(-1,1))

  // 3. Centered White Artboard Stage (#E5EAF1 backdrop matching screenshot)
  val canvasStage=FrameLayout(this).apply {
   setPadding((18*d).toInt(),(14*d).toInt(),(18*d).toInt(),(14*d).toInt())
   setBackgroundColor(Win11PaintTheme.CANVAS_STAGE_BG)
  }
  val sheetFrame=FrameLayout(this).apply {
   elevation=5f*d
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.WHITE); setStroke(1,Color.rgb(196,204,218))
   }
  }
  sheetFrame.addView(canvas,FrameLayout.LayoutParams(-1,-1))
  canvasStage.addView(sheetFrame,FrameLayout.LayoutParams(-1,-1))
  page.addView(canvasStage,LinearLayout.LayoutParams(-1,0,1f))

  // 4. Windows 11 Paint Bottom Status Bar (Coordinates | Canvas Size | Tool | Zoom Slider)
  val statusBar=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
   setPadding((10*d).toInt(),(2*d).toInt(),(10*d).toInt(),(2*d).toInt())
   setBackgroundColor(Win11PaintTheme.MICA_BG)
  }
  val cursorCoordLabel=TextView(this).apply { text="📍 0, 0 px"; textSize=9.5f; setTextColor(Win11PaintTheme.TEXT_MUTED) }
  val canvasDimLabel=TextView(this).apply { text="📐 0 × 0 px"; textSize=9.5f; setTextColor(Win11PaintTheme.TEXT_MUTED) }
  val activeToolLabel=TextView(this).apply { text="🖌 قلم • 5px"; textSize=9.5f; setTextColor(Win11PaintTheme.TEXT_MUTED) }
  val fitBtn=TextView(this).apply {
   text="⛶"; textSize=12f; gravity=Gravity.CENTER; setTextColor(Win11PaintTheme.TEXT_DARK); contentDescription="حجم 100%"
   setOnClickListener { canvas.setZoomFactor(1f); updateStatusBar() }
  }
  val zoomPctLabel=TextView(this).apply { text="100%"; textSize=9.5f; gravity=Gravity.CENTER; setTextColor(Win11PaintTheme.TEXT_DARK) }
  val zoomOutBtn=TextView(this).apply {
   text="−"; textSize=13f; gravity=Gravity.CENTER; setTextColor(Win11PaintTheme.TEXT_DARK)
   setOnClickListener { canvas.setZoomFactor((canvas.getZoomFactor()-0.25f).coerceAtLeast(0.5f)); updateStatusBar() }
  }
  val zoomBar=SeekBar(this).apply {
   max=200; progress=50 // 50%..250%
   progressTintList=android.content.res.ColorStateList.valueOf(Win11PaintTheme.ACCENT_BLUE)
   thumbTintList=android.content.res.ColorStateList.valueOf(Win11PaintTheme.ACCENT_BLUE)
   setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
    override fun onStartTrackingTouch(p0:SeekBar?) {}
    override fun onStopTrackingTouch(p0:SeekBar?) {}
    override fun onProgressChanged(p0:SeekBar?,p:Int,fromUser:Boolean) {
     if(fromUser) {
      val z=(50+p)/100f
      canvas.setZoomFactor(z)
      zoomPctLabel.text="${(z*100).toInt()}%"
     }
    }
   })
  }
  val zoomInBtn=TextView(this).apply {
   text="+"; textSize=13f; gravity=Gravity.CENTER; setTextColor(Win11PaintTheme.TEXT_DARK)
   setOnClickListener { canvas.setZoomFactor((canvas.getZoomFactor()+0.25f).coerceAtMost(2.5f)); updateStatusBar() }
  }

  statusBar.addView(cursorCoordLabel,LinearLayout.LayoutParams(-2,-2).apply { rightMargin=(14*d).toInt() })
  statusBar.addView(canvasDimLabel,LinearLayout.LayoutParams(-2,-2).apply { rightMargin=(14*d).toInt() })
  statusBar.addView(activeToolLabel,LinearLayout.LayoutParams(0,-2,1f))
  statusBar.addView(fitBtn,LinearLayout.LayoutParams((22*d).toInt(),(22*d).toInt()).apply { rightMargin=(4*d).toInt() })
  statusBar.addView(zoomPctLabel,LinearLayout.LayoutParams((36*d).toInt(),(22*d).toInt()))
  statusBar.addView(zoomOutBtn,LinearLayout.LayoutParams((20*d).toInt(),(22*d).toInt()))
  statusBar.addView(zoomBar,LinearLayout.LayoutParams((76*d).toInt(),(20*d).toInt()))
  statusBar.addView(zoomInBtn,LinearLayout.LayoutParams((20*d).toInt(),(22*d).toInt()))
  page.addView(View(this).apply { setBackgroundColor(Win11PaintTheme.BORDER_SUBTLE) },LinearLayout.LayoutParams(-1,1))
  page.addView(statusBar,LinearLayout.LayoutParams(-1,(26*d).toInt()))

  refreshRibbonSelection={
   toolContainers.values.distinct().forEach { it.background=Win11PaintTheme.toolButtonBg(d,false) }
   toolContainers[currentTool]?.background=Win11PaintTheme.toolButtonBg(d,true)
  }
  updateStatusBar={
   val zPct=(canvas.getZoomFactor()*100).toInt()
   zoomPctLabel.text="${zPct}%"
   zoomBar.progress=(zPct-50).coerceIn(0,200)
   activeToolLabel.text="🖌 $currentTool • ${currentBrushSize.toInt()}px"
  }

  canvas.onCursorMove={ px,py -> cursorCoordLabel.text="📍 $px, $py px" }
  canvas.onCanvasSizeChanged={ cw,ch -> canvasDimLabel.text="📐 $cw × $ch px" }
  canvas.onColorPickedByDropper={ sampled ->
   applyColorChoice(sampled)
   selectTool("قلم")
  }

  refreshRibbonSelection()
  updateStatusBar()
  panel(page)
 }

 private fun saveDrawing(v:PaintCanvas) {
  try {
   val bmp=v.exportBitmap()
   val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"orkeit-drawing-"+System.currentTimeMillis()+".png"); put(MediaStore.Images.Media.MIME_TYPE,"image/png"); put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/OrkeitDesktop") }
   val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
   if(uri!=null) contentResolver.openOutputStream(uri)?.use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)}
   Toast.makeText(this,if(uri!=null)"تم حفظ الرسم" else "تعذر الحفظ",Toast.LENGTH_LONG).show()
  } catch(_:Exception){Toast.makeText(this,"تعذر الحفظ",Toast.LENGTH_LONG).show()}
 }
 override fun onBackPressed() {
  val start=desktopArea.findViewWithTag<View>("start-menu")
  if(start!=null) { desktopArea.removeView(start); return }
  val quick=desktopArea.findViewWithTag<View>("quick-panel")
  if(quick!=null) { desktopArea.removeView(quick); return }
  val b=web
  if(b!=null && b.canGoBack()) b.goBack() else showDesktop()
 }
 override fun dispatchKeyEvent(event:android.view.KeyEvent):Boolean {
  if(event.action==android.view.KeyEvent.ACTION_DOWN) {
   val ctrl=(event.metaState and android.view.KeyEvent.META_CTRL_ON)!=0
   val shift=(event.metaState and android.view.KeyEvent.META_SHIFT_ON)!=0
   when {
    ctrl && shift && event.keyCode==android.view.KeyEvent.KEYCODE_ESCAPE -> { showTaskManager(); return true }
    event.keyCode==android.view.KeyEvent.KEYCODE_F5 -> { showDesktop(); return true }
    event.keyCode==android.view.KeyEvent.KEYCODE_ESCAPE -> {
     val menu=desktopArea.findViewWithTag<View>("start-menu")
     if(menu!=null) { desktopArea.removeView(menu); return true }
    }
    event.keyCode==android.view.KeyEvent.KEYCODE_META_LEFT || event.keyCode==android.view.KeyEvent.KEYCODE_META_RIGHT -> { showStartMenu(); return true }
   }
  }
  return super.dispatchKeyEvent(event)
 }
 private inner class ScenicLockWallpaper(c:Context):View(c) {
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  override fun onDraw(canvas:Canvas) {
   super.onDraw(canvas)
   val w=width.toFloat(); val h=height.toFloat()
   canvas.drawRect(0f,0f,w,h,Paint().apply{shader=LinearGradient(0f,0f,0f,h,intArrayOf(Color.rgb(12,38,73),Color.rgb(73,154,193),Color.rgb(244,190,112),Color.rgb(35,35,36)),null,Shader.TileMode.CLAMP)})
   paint.shader=null; paint.color=Color.rgb(255,224,165); canvas.drawCircle(w*.68f,h*.43f,h*.11f,paint)
   paint.color=Color.rgb(35,91,108)
   val sea=Path().apply{moveTo(0f,h*.56f);lineTo(w,h*.53f);lineTo(w,h*.79f);lineTo(0f,h*.78f);close()};canvas.drawPath(sea,paint)
   paint.color=Color.rgb(231,166,96)
   val sand=Path().apply{moveTo(0f,h*.73f);cubicTo(w*.28f,h*.69f,w*.62f,h*.88f,w,h*.72f);lineTo(w,h);lineTo(0f,h);close()};canvas.drawPath(sand,paint)
   paint.color=Color.rgb(22,27,29)
   val leftRock=Path().apply{moveTo(0f,0f);lineTo(w*.16f,0f);cubicTo(w*.08f,h*.18f,w*.23f,h*.3f,w*.12f,h*.49f);cubicTo(w*.08f,h*.68f,w*.33f,h*.78f,w*.48f,h);lineTo(0f,h);close()};canvas.drawPath(leftRock,paint)
   val rightRock=Path().apply{moveTo(w*.86f,0f);lineTo(w,0f);lineTo(w,h);lineTo(w*.85f,h*.86f);cubicTo(w*.76f,h*.67f,w*.88f,h*.5f,w*.79f,h*.33f);close()};canvas.drawPath(rightRock,paint)
   paint.color=Color.argb(35,255,255,255);canvas.drawRect(0f,0f,w,h,paint)
  }
 }
 private inner class WindowsWallpaper(c:Context):View(c) {
  private val p=Paint(Paint.ANTI_ALIAS_FLAG)
  override fun onDraw(canvas:Canvas) {
   super.onDraw(canvas)
   val w=width.toFloat(); val h=height.toFloat()
   val wallpaperStyle=getSharedPreferences("orkeit_desktop",Context.MODE_PRIVATE).getInt("wallpaper_style",0)
   val gradient=when(wallpaperStyle) {
    1 -> intArrayOf(Color.rgb(9,15,30),Color.rgb(28,42,68),Color.rgb(49,65,94),Color.rgb(6,12,24))
    2 -> intArrayOf(Color.rgb(224,237,250),Color.rgb(174,207,240),Color.rgb(115,169,222),Color.rgb(232,243,253))
    else -> intArrayOf(Color.rgb(206,227,244),Color.rgb(176,209,236),Color.rgb(192,220,242),Color.rgb(219,235,248))
   }
   canvas.drawRect(0f,0f,w,h,Paint().apply { shader=LinearGradient(0f,0f,w,h,gradient,null,Shader.TileMode.CLAMP) })
   // Layered Windows 11 Bloom petals centered smoothly
   val cx=w*.54f; val cy=h*.49f
   val colors=when(wallpaperStyle) {
    1 -> intArrayOf(Color.argb(180,115,145,195),Color.argb(170,65,92,143),Color.argb(210,24,40,74),Color.argb(120,150,170,205))
    2 -> intArrayOf(Color.argb(170,255,255,255),Color.argb(160,102,162,218),Color.argb(180,65,125,190),Color.argb(130,245,250,255))
    else -> intArrayOf(Color.argb(235,16,136,238),Color.argb(230,0,98,195),Color.argb(235,0,58,136),Color.argb(210,64,185,255))
   }
   for(i in 0..3) {
    val path=Path()
    val inset=i*(minOf(w,h)*0.032f)
    path.moveTo(cx-w*.34f,cy+h*.30f+inset)
    path.cubicTo(cx-w*.32f,cy-h*.46f+inset,cx+w*.16f,cy-h*.48f+inset,cx+w*.34f,cy-h*.04f+inset)
    path.cubicTo(cx+w*.44f,cy+h*.28f+inset,cx+w*.02f,cy+h*.42f+inset,cx-w*.34f,cy+h*.30f+inset)
    p.color=colors[i]; p.style=Paint.Style.FILL; p.shader=null; canvas.drawPath(path,p)
   }
   p.shader=RadialGradient(cx,cy,h*.58f,intArrayOf(Color.argb(75,145,220,255),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
   canvas.drawRect(0f,0f,w,h,p); p.shader=null
  }
 }
 private inner class PaintCanvas(c:Context):View(c) {
  private val currentPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;style=Paint.Style.STROKE;strokeWidth=5f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
  private val paths=mutableListOf<Pair<Path,Paint>>()
  private val undone=mutableListOf<Pair<Path,Paint>>()
  private var activePath=Path()
  private var tool="قلم"
  private var startX=0f
  private var startY=0f
  private var zoom=1f
  private var cropRect:RectF?=null
  private var imported:Bitmap?=null
  private var filledShapes=false
  private var whiteBackground=true
  private var showGrid=false
  var onCursorMove:((Int,Int)->Unit)?=null
  var onCanvasSizeChanged:((Int,Int)->Unit)?=null
  var onColorPickedByDropper:((Int)->Unit)?=null
  init{isFocusable=true;isFocusableInTouchMode=true;setLayerType(View.LAYER_TYPE_SOFTWARE,null);setBackgroundColor(Color.WHITE)}
  override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);if(w>0&&h>0)onCanvasSizeChanged?.invoke(w,h)}
  fun setTool(value:String){tool=value;currentPaint.xfermode=null;invalidate()}
  fun setColor(value:Int){currentPaint.color=value;currentPaint.xfermode=null;invalidate()}
  fun setBrushSize(value:Float){currentPaint.strokeWidth=value.coerceIn(1f,50f);invalidate()}
  fun setFilled(value:Boolean){filledShapes=value;invalidate()}
  fun isShapeFilled():Boolean=filledShapes
  fun setShowGrid(value:Boolean){showGrid=value;invalidate()}
  fun setWhiteBackground(){whiteBackground=true;invalidate()}
  fun setZoomFactor(factor:Float){zoom=factor.coerceIn(0.5f,2.5f);invalidate()}
  fun getZoomFactor():Float=zoom
  fun zoomBy(factor:Float){zoom=(zoom*factor).coerceIn(0.25f,5f);invalidate()}
  fun hasSelection():Boolean=cropRect?.let{it.width()>2f&&it.height()>2f}==true
  fun clearSelection(){cropRect=null;invalidate()}
  fun applyCrop(){if(!hasSelection())return;try{imported=exportBitmap();paths.clear();undone.clear();cropRect=null;invalidate()}catch(_:Exception){}}
  fun rotate90(){try{val src=exportBitmap();val m=Matrix().apply{postRotate(90f)};imported=Bitmap.createBitmap(src,0,0,src.width,src.height,m,true);paths.clear();undone.clear();invalidate()}catch(_:Exception){}}
  fun flipHorizontal(){try{val src=exportBitmap();val m=Matrix().apply{postScale(-1f,1f,src.width/2f,src.height/2f)};imported=Bitmap.createBitmap(src,0,0,src.width,src.height,m,true);paths.clear();undone.clear();invalidate()}catch(_:Exception){}}
  fun undo(){if(paths.isNotEmpty())undone.add(paths.removeAt(paths.lastIndex));invalidate()}
  fun redo(){if(undone.isNotEmpty())paths.add(undone.removeAt(undone.lastIndex));invalidate()}
  fun importImage(uri:Uri){try{imported=this@MainActivity.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)};invalidate()}catch(_:Exception){Toast.makeText(context,"تعذر استيراد الصورة",Toast.LENGTH_SHORT).show()}}
  private fun shapePath(x:Float,y:Float):Path {
   val p=Path()
   val l=minOf(startX,x);val r=maxOf(startX,x);val t=minOf(startY,y);val b=maxOf(startY,y)
   when(tool){
    "خط"->{p.moveTo(startX,startY);p.lineTo(x,y)}
    "سهم"->{p.moveTo(startX,startY);p.lineTo(x,y);val angle=kotlin.math.atan2((y-startY).toDouble(),(x-startX).toDouble());val head=(currentPaint.strokeWidth*3.2f).coerceAtLeast(12f);p.moveTo(x,y);p.lineTo((x-head*kotlin.math.cos(angle-0.48)).toFloat(),(y-head*kotlin.math.sin(angle-0.48)).toFloat());p.moveTo(x,y);p.lineTo((x-head*kotlin.math.cos(angle+0.48)).toFloat(),(y-head*kotlin.math.sin(angle+0.48)).toFloat())}
    "مستطيل"->{p.addRect(l,t,r,b,Path.Direction.CW)}
    "مستطيل دائري"->{p.addRoundRect(RectF(l,t,r,b),18f,18f,Path.Direction.CW)}
    "دائرة"->{p.addOval(RectF(l,t,r,b),Path.Direction.CW)}
    "مثلث"->{p.moveTo((l+r)/2f,t);p.lineTo(r,b);p.lineTo(l,b);p.close()}
    else->{p.moveTo(startX,startY);p.lineTo(x,y)}
   }
   return p
  }
  private fun paintForTool():Paint=Paint(currentPaint).apply{
   when(tool){
    "ممحاة"->{color=Color.WHITE;strokeWidth=(currentPaint.strokeWidth*2.5f).coerceAtMost(100f);style=Paint.Style.STROKE}
    "قلم رصاص"->{strokeWidth=(currentPaint.strokeWidth*0.55f).coerceAtLeast(1f);alpha=190}
    "فرشاة"->{strokeWidth=(currentPaint.strokeWidth*1.4f).coerceAtMost(64f);alpha=230}
    "تحديد عريض"->{strokeWidth=(currentPaint.strokeWidth*1.8f).coerceAtMost(72f);strokeCap=Paint.Cap.SQUARE}
    "تعبئة"->{style=Paint.Style.FILL}
    "مستطيل","مستطيل دائري","دائرة","مثلث"->{style=if(filledShapes)Paint.Style.FILL else Paint.Style.STROKE}
    else->{style=Paint.Style.STROKE}
   }
  }
  private fun addText(text:String,x:Float,y:Float){val p=Paint(currentPaint).apply{style=Paint.Style.FILL;textSize=(currentPaint.strokeWidth*5f).coerceIn(18f,96f)};val glyph=Path();p.getTextPath(text,0,text.length,x,y,glyph);paths.add(Pair(glyph,p));undone.clear();invalidate()}
  override fun onDraw(c:Canvas){
   super.onDraw(c);c.drawColor(if(whiteBackground)Color.WHITE else Color.TRANSPARENT);c.save();c.scale(zoom,zoom)
   imported?.let{c.drawBitmap(it,null,RectF(0f,0f,width/zoom,height/zoom),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))}
   paths.forEach{c.drawPath(it.first,it.second)}
   if(!activePath.isEmpty)c.drawPath(activePath,paintForTool())
   if(showGrid){val gp=Paint().apply{color=Color.argb(38,100,130,175);strokeWidth=1f};val step=32f;var gx=step;while(gx<width/zoom){c.drawLine(gx,0f,gx,height/zoom,gp);gx+=step};var gy=step;while(gy<height/zoom){c.drawLine(0f,gy,width/zoom,gy,gp);gy+=step}}
   c.restore()
   cropRect?.let{val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(0,120,215);style=Paint.Style.STROKE;strokeWidth=2f;pathEffect=DashPathEffect(floatArrayOf(9f,6f),0f)};c.drawRect(it,p)}
  }
  override fun onTouchEvent(e:MotionEvent):Boolean{
   val x=e.x/zoom;val y=e.y/zoom
   onCursorMove?.invoke(x.toInt().coerceAtLeast(0),y.toInt().coerceAtLeast(0))
   when(e.action){
    MotionEvent.ACTION_DOWN->{
     requestFocus();startX=x;startY=y;activePath=Path()
     when(tool){
      "قطارة"->{
       try{val bmp=exportBitmap();val px=x.toInt().coerceIn(0,bmp.width-1);val py=y.toInt().coerceIn(0,bmp.height-1);onColorPickedByDropper?.invoke(bmp.getPixel(px,py))}catch(_:Exception){}
       return true
      }
      "تعبئة"->{
       try{
        val bmp=exportBitmap()
        val px=x.toInt().coerceIn(0,bmp.width-1)
        val py=y.toInt().coerceIn(0,bmp.height-1)
        if(PaintFloodFiller.floodFill(bmp,px,py,currentPaint.color)){imported=bmp;paths.clear();undone.clear();invalidate()}
       }catch(_:Exception){}
       return true
      }
      "قلم","فرشاة","قلم رصاص","تحديد عريض","ممحاة"->{activePath.moveTo(x,y)}
     }
     invalidate();return true
    }
    MotionEvent.ACTION_MOVE->{
     if(tool=="قلم"||tool=="فرشاة"||tool=="قلم رصاص"||tool=="تحديد عريض"||tool=="ممحاة") activePath.lineTo(x,y)
     else if(tool!="تعبئة"&&tool!="قطارة") activePath=shapePath(x,y)
     invalidate();return true
    }
    MotionEvent.ACTION_UP->{
     when(tool){
      "نص"->{val input=EditText(context).apply{hint="اكتب النص هنا";minLines=2};AlertDialog.Builder(context).setTitle("إضافة نص").setView(input).setPositiveButton("إضافة"){_,_->if(input.text.isNotBlank())addText(input.text.toString(),x,y)}.setNegativeButton("إلغاء",null).show();activePath=Path()}
      "قص","تحديد"->{cropRect=RectF(minOf(startX,x),minOf(startY,y),maxOf(startX,x),maxOf(startY,y));activePath=Path()}
      "تعبئة","قطارة"->{activePath=Path()}
      else->{val p=paintForTool();val completed=if(tool=="خط"||tool=="سهم"||tool=="مستطيل"||tool=="مستطيل دائري"||tool=="دائرة"||tool=="مثلث")shapePath(x,y)else Path(activePath);paths.add(Pair(completed,p));undone.clear();activePath=Path()}
     }
     invalidate();return true
    }
   }
   return true
  }
  fun exportBitmap():Bitmap{
   val bmp=Bitmap.createBitmap((width/zoom).toInt().coerceAtLeast(1),(height/zoom).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
   val cv=Canvas(bmp);cv.drawColor(Color.WHITE)
   imported?.let{cv.drawBitmap(it,null,RectF(0f,0f,bmp.width.toFloat(),bmp.height.toFloat()),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))}
   paths.forEach{cv.drawPath(it.first,it.second)}
   val r=cropRect
   if(r!=null&&r.width()>2&&r.height()>2){val l=r.left.toInt().coerceIn(0,bmp.width-1);val t=r.top.toInt().coerceIn(0,bmp.height-1);val rr=r.right.toInt().coerceIn(l+1,bmp.width);val b=r.bottom.toInt().coerceIn(t+1,bmp.height);return Bitmap.createBitmap(bmp,l,t,rr-l,b-t)}
   return bmp
  }
  fun clear(){paths.clear();undone.clear();activePath.reset();cropRect=null;imported=null;invalidate()}
 }
}
