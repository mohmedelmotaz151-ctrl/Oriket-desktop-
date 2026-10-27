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
 private val appOpenAdUnitId="ca-app-pub-3940256099942544/9257395921" // Google official test ID; replace with Oriket's App Open ad unit before release
 private val openWindows=mutableListOf<WindowEntry>()
 private var web:WebView?=null
 private data class WindowEntry(val id:Int,val title:String,val frame:View,val taskButton:Button)
 private var activeDownloadId=-1L
 private var updateReceiverRegistered=false
 private val uiHandler=Handler(Looper.getMainLooper())
 private var clockLabel:TextView?=null
 private var clockRunnable:Runnable?=null
 private var fileManagerDir:java.io.File?=null
 private var musicPlayer:MediaPlayer?=null
 private data class PhotoItem(val uri:Uri,val name:String,val date:Long)
 private var selectedPhoto:PhotoItem?=null
 private var editorBitmap:Bitmap?=null
 private var galleryContainer:LinearLayout?=null
 private var galleryGrid:GridLayout?=null
 private var galleryStatus:TextView?=null
 private var galleryPermissionRequested=false
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
  MobileAds.initialize(this) {
   runOnUiThread { loadStartupAppOpenAd() }
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
  if(startupAdAttempted || isFinishing) return
  startupAdAttempted=true
  startupAdLoadInProgress=true
  AppOpenAd.load(
   this,
   appOpenAdUnitId,
   AdRequest.Builder().build(),
   object:AppOpenAd.AppOpenAdLoadCallback() {
    override fun onAdLoaded(ad:AppOpenAd) {
     startupAdLoadInProgress=false
     startupAppOpenAd=ad
     android.util.Log.i("OriketAdMob","App Open ad loaded")
     if(startupSplashVisible && !startupAdHandled && !isFinishing) showStartupAppOpenAd()
    }
    override fun onAdFailedToLoad(error:LoadAdError) {
     startupAdLoadInProgress=false
     startupAppOpenAd=null
     android.util.Log.e("OriketAdMob","App Open failed: code=${error.code}, domain=${error.domain}, message=${error.message}")
     // Ads are optional: never block startup or show a disruptive error toast.
    }
   }
  )
 }

 private fun showStartupAppOpenAd() {
  val ad=startupAppOpenAd ?: return
  if(startupAdHandled || !startupSplashVisible || isFinishing) return
  startupAdHandled=true
  startupAppOpenAd=null
  ad.fullScreenContentCallback=object:FullScreenContentCallback() {
   override fun onAdDismissedFullScreenContent() {
    android.util.Log.i("OriketAdMob","App Open ad dismissed")
    finishStartupSplash()
   }
   override fun onAdFailedToShowFullScreenContent(error:AdError) {
    android.util.Log.e("OriketAdMob","App Open failed to show: ${error.message}")
    finishStartupSplash()
   }
   override fun onAdShowedFullScreenContent() {
    android.util.Log.i("OriketAdMob","App Open ad shown")
   }
  }
  try { ad.show(this) } catch(e:Exception) {
   android.util.Log.e("OriketAdMob","App Open show exception",e)
   finishStartupSplash()
  }
 }

 private fun finishStartupSplash() {
  if(!startupSplashVisible || isFinishing) return
  startupSplashVisible=false
  uiHandler.post { if(!isFinishing) showLocalLoginScreen() }
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
   textSize=30f
   setTextColor(Color.WHITE)
   gravity=Gravity.CENTER
   typeface=Typeface.create("sans-serif-light",Typeface.NORMAL)
   setPadding(0,18,0,4)
   alpha=0f
  }
  panel.addView(title,LinearLayout.LayoutParams(-1,-2))
  val subtitle=TextView(this).apply {
   text="Oriket Desktop"
   textSize=15f
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
  logo.animate().alpha(1f).setDuration(650).start()
  title.animate().alpha(1f).setStartDelay(280).setDuration(650).start()
  subtitle.animate().alpha(1f).setStartDelay(450).setDuration(650).start()
  playDesktopSound("boot")
  // Give the App Open request time to finish before leaving the startup screen.
  // If the ad loads during this window, its callback displays it immediately.
  if(isFirstBootSequence) {
   uiHandler.postDelayed({
    if(!isFinishing && startupSplashVisible) {
     if(startupAppOpenAd!=null && !startupAdHandled) showStartupAppOpenAd()
     else {
      android.util.Log.w("OriketAdMob","Startup ad was not ready within 12 seconds; continuing without an ad")
      finishStartupSplash()
     }
    }
   },12000L)
  } else {
   uiHandler.postDelayed({ if(!isFinishing) showLocalLoginScreen() },1900L)
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
   textSize=30f; setTextColor(Color.WHITE); gravity=Gravity.CENTER
   typeface=Typeface.create("sans-serif-light",Typeface.NORMAL)
   setShadowLayer(5f,1f,2f,Color.BLACK)
   setPadding(0,10,0,8)
  },LinearLayout.LayoutParams(-1,-2))
  card.addView(TextView(this).apply {
   text=""
   textSize=14f; setTextColor(Color.WHITE); gravity=Gravity.CENTER
   setPadding(0,0,0,8)
  },LinearLayout.LayoutParams(-1,-2))
  val username=TextView(this).apply {
   text="◯"
   textSize=64f; setTextColor(Color.WHITE); gravity=Gravity.CENTER
   setShadowLayer(4f,1f,2f,Color.BLACK)
   setPadding(0,0,0,4)
  }
  card.addView(username,LinearLayout.LayoutParams(-1,-2))
  fun makePasswordField(hintText:String):EditText = EditText(this).apply {
   hint=if(hasPassword) "PIN" else hintText
   setSingleLine(true)
   textSize=16f
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
   textSize=13f
   setTextColor(Color.rgb(255,190,190))
   gravity=Gravity.CENTER
   setPadding(0,10,0,0)
  }
  card.addView(message,LinearLayout.LayoutParams(-1,-2))
  val button=Button(this).apply {
   text=if(hasPassword) "تسجيل الدخول" else "حفظ كلمة المرور والمتابعة"
   isAllCaps=false
   textSize=15f
   setTextColor(Color.WHITE)
   background=android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.rgb(0,103,192)); cornerRadius=12f
   }
  }
  card.addView(button,LinearLayout.LayoutParams(-1,52).apply { topMargin=18 })
  card.addView(TextView(this).apply {
   text="كلمة المرور محفوظة على هذا الجهاز فقط"
   textSize=11f; setTextColor(Color.rgb(174,196,224)); gravity=Gravity.CENTER
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
  root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); layoutDirection=View.LAYOUT_DIRECTION_LTR }
  desktopArea=FrameLayout(this).apply { layoutDirection=View.LAYOUT_DIRECTION_LTR }
  desktopArea.addView(WindowsWallpaper(this),FrameLayout.LayoutParams(-1,-1))
  desktopArea.isClickable=true
  desktopArea.setOnLongClickListener {
   android.widget.PopupMenu(this,desktopArea).apply {
    menu.add("عرض قائمة ابدأ").setOnMenuItemClickListener { showStartMenu(); true }
    menu.add("تغيير الخلفية").setOnMenuItemClickListener { showWallpaperPicker(); true }
    menu.add("تحديث سطح المكتب").setOnMenuItemClickListener { showDesktop(); true }
    menu.add("فتح المتصفح").setOnMenuItemClickListener { showBrowser(); true }
    menu.add("فتح مستكشف الملفات").setOnMenuItemClickListener { pickFile("*/*"); true }
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
  taskbar=LinearLayout(this).apply {
   gravity=Gravity.CENTER
   orientation=LinearLayout.HORIZONTAL
   setPadding(12,5,12,5)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(0,248,250,255)); cornerRadius=26f }
   elevation=14f
  }
  val dock=FrameLayout(this).apply {
   setBackgroundColor(Color.argb(225,20,25,35))
   elevation=16f
  }
  val taskScroll=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; overScrollMode=View.OVER_SCROLL_NEVER; clipToPadding=false; addView(taskbar,FrameLayout.LayoutParams(-2,68)) }
  dock.addView(taskScroll,FrameLayout.LayoutParams(-1,72,Gravity.CENTER).apply { leftMargin=4; rightMargin=82 })
  root.addView(dock,LinearLayout.LayoutParams(-1,86))
  setContentView(root)
  clockLabel=TextView(this).apply {
   gravity=Gravity.CENTER
   textSize=11f
   setTextColor(navy)
   setPadding(8,2,10,2)
   setOnClickListener { showQuickSettings() }
   contentDescription="التاريخ والوقت والإعدادات السريعة"
  }
  dock.addView(clockLabel,FrameLayout.LayoutParams(-2,-1,Gravity.RIGHT or Gravity.CENTER_VERTICAL))
  val clock=object:Runnable {
   override fun run() {
    clockLabel?.text=java.text.SimpleDateFormat("HH:mm\n dd/MM/yyyy",Locale.getDefault()).format(java.util.Date())
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
  val outer=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=13f; setStroke(1,Color.rgb(180,195,216)) }
   elevation=18f
   clipToOutline=true
   layoutDirection=View.LAYOUT_DIRECTION_LTR
  }
  val titlebar=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(12,0,4,0)
   setBackgroundColor(Color.rgb(247,249,253))
  }
  titlebar.addView(TextView(this).apply { text=windowIcon(title)+"  "+title; textSize=18f; setTextColor(navy); typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); gravity=Gravity.CENTER_VERTICAL; maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; includeFontPadding=true; setPadding(4,0,8,0) },LinearLayout.LayoutParams(0,50,1f))
  val minimize=windowButton("—")
  val maximize=windowButton("□")
  val close=windowButton("×")
  titlebar.addView(minimize,LinearLayout.LayoutParams(46,46))
  titlebar.addView(maximize,LinearLayout.LayoutParams(46,46))
  titlebar.addView(close,LinearLayout.LayoutParams(48,46))
  outer.addView(titlebar,LinearLayout.LayoutParams(-1,52))
  val body=FrameLayout(this).apply { setPadding(10,8,10,8); setBackgroundColor(Color.rgb(250,251,254)) }
  outer.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  val lp=FrameLayout.LayoutParams((desktopArea.width*0.86f).toInt().coerceIn(280,(desktopArea.width-16).coerceAtLeast(280)), (desktopArea.height*0.84f).toInt().coerceIn(220,(desktopArea.height-16).coerceAtLeast(220)),Gravity.CENTER)
  val entryButton=taskButton(title)
  val entry=WindowEntry(id,title,outer,entryButton)
  openWindows.add(entry)
  entryButton.setOnClickListener {
   if(outer.visibility==View.VISIBLE && outer.isSelected) {
    outer.visibility=View.GONE
    outer.isSelected=false
    entryButton.isSelected=false
   } else {
    outer.visibility=View.VISIBLE
    outer.bringToFront()
    outer.isSelected=true
    openWindows.forEach { it.taskButton.isSelected=it.id==id }
    entryButton.isSelected=true
   }
  }
  taskbar.addView(entryButton)
  desktopArea.addView(outer,lp)
  outer.bringToFront()
  content=body
  var downX=0f; var downY=0f; var startX=0f; var startY=0f
  var maximized=false
  var lastTap=0L
  var oldParams:FrameLayout.LayoutParams?=null
  titlebar.setOnTouchListener { _,e ->
   when(e.action) {
    MotionEvent.ACTION_DOWN -> {
     downX=e.rawX; downY=e.rawY; startX=outer.x; startY=outer.y
     outer.bringToFront()
     openWindows.forEach { it.taskButton.isSelected=it.id==id }
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
  outer.setOnTouchListener { v,e -> if(e.action==MotionEvent.ACTION_DOWN) { v.bringToFront(); openWindows.forEach { it.taskButton.isSelected=it.id==id } }; false }
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
 private fun windowButton(label:String)=Button(this).apply {
  text=label; textSize=18f; isAllCaps=false; minWidth=38; minimumWidth=38; minHeight=38; minimumHeight=38
  gravity=Gravity.CENTER; setPadding(0,0,0,0); setTextColor(navy)
  typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
  background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=9f }
  setOnTouchListener { v,e ->
   if(e.action==MotionEvent.ACTION_DOWN) v.background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(230,237,247)); cornerRadius=9f }
   if(e.action==MotionEvent.ACTION_UP || e.action==MotionEvent.ACTION_CANCEL) v.postDelayed({v.background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=9f }},140)
   false
  }
 }
 private fun windowIcon(title:String):String = when {
  title.contains("YouTube Music")->"🎵"
  title.contains("YouTube")->"▶️"
  title.contains("المتصفح")->"🌐"
  title.contains("الملفات")->"📁"
  title.contains("الحاسبة")->"🧮"
  title.contains("الرسام")->"🎨"
  title.contains("المفكرة")->"📝"
  title.contains("تحديث")->"🔄"
  else->"🪟"
 }
 private fun taskButton(title:String)=Button(this).apply {
  text=windowIcon(title); textSize=23f; isAllCaps=false; minWidth=58; minimumWidth=58; minHeight=54; minimumHeight=54
  gravity=Gravity.CENTER; setPadding(8,5,8,5); setTextColor(navy)
  typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
  background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(235,242,252)); cornerRadius=13f; setStroke(1,Color.rgb(216,226,241)) }
  elevation=2f; contentDescription=title
 }
 private fun task(s:String,a:()->Unit)=Button(this).apply {
  text=s; textSize=13f; minHeight=48; minimumHeight=48; minWidth=0; isAllCaps=false; gravity=Gravity.CENTER
  maxLines=2; minLines=2; includeFontPadding=true; ellipsize=android.text.TextUtils.TruncateAt.END; setLineSpacing(1f,1.05f)
  typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); setTextColor(Color.rgb(24,39,62)); setPadding(9,5,9,5)
  if(Build.VERSION.SDK_INT>=26) setAutoSizeTextTypeUniformWithConfiguration(10,13,1,android.util.TypedValue.COMPLEX_UNIT_SP)
  background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(237,243,251)); cornerRadius=11f; setStroke(1,Color.rgb(216,226,240)) }
  elevation=1.5f; contentDescription=s; setOnClickListener{a()}
 }
 private fun action(s:String,a:()->Unit)=Button(this).apply {
  text=s; textSize=13f; minHeight=48; minimumHeight=48; minWidth=0; isAllCaps=false; gravity=Gravity.CENTER
  maxLines=2; minLines=2; includeFontPadding=true; ellipsize=android.text.TextUtils.TruncateAt.END; setLineSpacing(1f,1.05f)
  typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); setTextColor(Color.WHITE); setPadding(9,5,9,5)
  if(Build.VERSION.SDK_INT>=26) setAutoSizeTextTypeUniformWithConfiguration(10,13,1,android.util.TypedValue.COMPLEX_UNIT_SP)
  background=android.graphics.drawable.GradientDrawable().apply { setColor(blue); cornerRadius=11f; setStroke(1,Color.rgb(0,83,160)) }
  elevation=2f; contentDescription=s; setOnClickListener{a()}
 }
 private fun panel(v:View) {
  content.addView(v,FrameLayout.LayoutParams(-1,-1))
 }
 private inner class Icon3DView(context:Context, private val symbol:String, private val label:String):View(context) {
  private val colors:IntArray = when {
   label.contains("YouTube Music") -> intArrayOf(Color.rgb(255,145,165),Color.rgb(230,35,65),Color.rgb(120,8,30))
   label.contains("YouTube") -> intArrayOf(Color.rgb(255,155,155),Color.rgb(235,35,45),Color.rgb(125,8,18))
   label.contains("مشغل الموسيقى") -> intArrayOf(Color.rgb(230,190,255),Color.rgb(140,65,210),Color.rgb(65,20,115))
   label.contains("الإنترنت") -> intArrayOf(Color.rgb(96,205,255),Color.rgb(0,100,220),Color.rgb(5,45,130))
   label.contains("الكمبيوتر") -> intArrayOf(Color.rgb(180,225,255),Color.rgb(50,115,205),Color.rgb(20,48,112))
   label.contains("PDF") -> intArrayOf(Color.rgb(255,145,145),Color.rgb(220,35,45),Color.rgb(125,12,25))
   label.contains("الملفات") || label.contains("الكمبيوتر") -> intArrayOf(Color.rgb(255,225,135),Color.rgb(225,155,35),Color.rgb(120,75,12))
   label.contains("المتصفح") -> intArrayOf(Color.rgb(125,220,255),Color.rgb(25,125,220),Color.rgb(10,45,120))
   label.contains("الفيديو") -> intArrayOf(Color.rgb(255,185,90),Color.rgb(235,90,25),Color.rgb(135,35,12))
   label.contains("الرسام") -> intArrayOf(Color.rgb(255,190,220),Color.rgb(205,65,145),Color.rgb(105,25,90))
   label.contains("Word") -> intArrayOf(Color.rgb(120,190,255),Color.rgb(25,95,205),Color.rgb(15,40,120))
   label.contains("Excel") -> intArrayOf(Color.rgb(130,240,170),Color.rgb(20,155,85),Color.rgb(8,75,45))
   label.contains("التحكم") -> intArrayOf(Color.rgb(220,225,240),Color.rgb(105,125,160),Color.rgb(45,55,85))
   label.contains("المهام") -> intArrayOf(Color.rgb(175,230,255),Color.rgb(30,135,190),Color.rgb(10,60,105))
   label.contains("Command") -> intArrayOf(Color.rgb(115,145,165),Color.rgb(35,55,75),Color.rgb(8,15,25))
   label.contains("ألعاب") -> intArrayOf(Color.rgb(210,165,255),Color.rgb(125,60,220),Color.rgb(55,20,120))
   label.contains("الحاسبة") -> intArrayOf(Color.rgb(180,245,220),Color.rgb(35,155,125),Color.rgb(10,65,65))
   label.contains("المفكرة") -> intArrayOf(Color.rgb(255,245,160),Color.rgb(225,175,45),Color.rgb(120,75,12))
   label.contains("تحديث") -> intArrayOf(Color.rgb(145,220,255),Color.rgb(30,130,220),Color.rgb(12,55,130))
   else -> intArrayOf(Color.rgb(220,230,245),Color.rgb(100,125,165),Color.rgb(35,48,78))
  }
  private val fill=Paint(Paint.ANTI_ALIAS_FLAG)
  init { setLayerType(View.LAYER_TYPE_SOFTWARE,null); contentDescription=label }
  override fun onDraw(canvas:Canvas) {
   super.onDraw(canvas)
   val side=minOf(width,height).toFloat()
   if(side<=0f) return
   val left=(width-side)/2f+4f; val top=4f
   val right=left+side-8f; val bottom=top+side-8f
   val rect=RectF(left,top,right,bottom)
   fill.style=Paint.Style.FILL
   fill.color=colors[2]; fill.setShadowLayer(5f,0f,5f,Color.argb(120,0,0,0))
   canvas.drawRoundRect(RectF(left+1f,top+2f,right+1f,bottom+2f),side*.2f,side*.2f,fill)
   fill.clearShadowLayer()
   fill.shader=LinearGradient(left,top,left,bottom,colors,null,Shader.TileMode.CLAMP)
   canvas.drawRoundRect(rect,side*.2f,side*.2f,fill)
   fill.shader=null
   fill.style=Paint.Style.STROKE; fill.strokeWidth=1.2f; fill.color=Color.argb(190,255,255,255)
   canvas.drawRoundRect(RectF(left+1f,top+1f,right-1f,bottom-1f),side*.2f,side*.2f,fill)
   fill.style=Paint.Style.FILL
   fill.shader=LinearGradient(left,top,left,top+side*.46f,Color.argb(150,255,255,255),Color.argb(0,255,255,255),Shader.TileMode.CLAMP)
   canvas.drawRoundRect(RectF(left+3f,top+3f,right-3f,top+side*.42f),side*.16f,side*.16f,fill)
   fill.shader=null
   fill.color=Color.WHITE; fill.textAlign=Paint.Align.CENTER; fill.typeface=Typeface.create("sans-serif",Typeface.BOLD)
   fill.textSize=side*.46f
   val fm=fill.fontMetrics
   canvas.drawText(symbol,width/2f,(top+bottom)/2f-(fm.ascent+fm.descent)/2f,fill)
  }
 }
 private fun showDesktop() {
  initDesktop()
  val prefs=getSharedPreferences("orkeit_desktop",Context.MODE_PRIVATE)
  val old=desktopArea.findViewWithTag<View>("desktop-shortcuts")
  if(old!=null) desktopArea.removeView(old)
  val shortcuts=LinearLayout(this).apply {
   tag="desktop-shortcuts"
   orientation=LinearLayout.VERTICAL
   gravity=Gravity.TOP or Gravity.LEFT
   setPadding(8,10,8,8)
   background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
  }
  val desktopColumns=((desktopArea.width-32)/140).coerceIn(2,7)
  val grid=GridLayout(this).apply { columnCount=desktopColumns; useDefaultMargins=false }
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
  items.forEach { item ->
   val displayName=prefs.getString("shortcut_label_${item.second}",item.second) ?: item.second
   val tile=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(5,8,5,8)
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
        showDesktop()
        Toast.makeText(this@MainActivity,"نُقل الاختصار إلى سلة المحذوفات",Toast.LENGTH_SHORT).show()
        true
       }
      }
     }.show()
     true
    }
    setOnHoverListener { v,e ->
     if(e.action==MotionEvent.ACTION_HOVER_ENTER) v.background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(55,255,255,255)); cornerRadius=10f }
     if(e.action==MotionEvent.ACTION_HOVER_EXIT) v.background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
     false
    }
   }
   tile.addView(Icon3DView(this,item.first,item.second),LinearLayout.LayoutParams(76,76))
   tile.addView(TextView(this).apply { text=displayName; textSize=12f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); setShadowLayer(3f,1f,1f,Color.BLACK); setPadding(5,5,5,2); maxLines=2; minLines=2; includeFontPadding=true; ellipsize=android.text.TextUtils.TruncateAt.END; setLineSpacing(1f,1.05f) })
   grid.addView(tile,GridLayout.LayoutParams().apply { width=132; height=136; setMargins(5,5,5,5) })
  }
  shortcuts.addView(grid,LinearLayout.LayoutParams(-1,-2))
  val clock=TextView(this).apply {
   text="Oriket Desktop"; textSize=12f; setTextColor(Color.WHITE); setShadowLayer(3f,1f,1f,Color.BLACK)
   gravity=Gravity.CENTER; setPadding(14,8,14,8)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(85,25,50,90)); cornerRadius=18f }
  }
  shortcuts.addView(clock,LinearLayout.LayoutParams(-2,-2).apply { gravity=Gravity.LEFT; topMargin=8 })
  desktopArea.addView(shortcuts,FrameLayout.LayoutParams(-1,-1))
  shortcuts.bringToFront()
  openWindows.forEach { it.frame.bringToFront() }
  val start=taskButton("ابدأ").apply { text="⊞"; textSize=22f; setOnClickListener { showStartMenu() } }
  if(taskbar.childCount==0) taskbar.addView(start)
  val searchButton=taskButton("بحث").apply { text="⌕"; textSize=22f; setOnClickListener { showStartMenu(); desktopArea.findViewWithTag<EditText>("start-search")?.requestFocus() } }
  val browser=taskButton("متصفح الإنترنت").apply { text="🌐"; setOnClickListener { showBrowser() } }
  val files=taskButton("هذا الكمبيوتر").apply { text="📁"; setOnClickListener { showMyComputer() } }
  val paint=taskButton("الرسام").apply { text="🎨"; setOnClickListener { showPaint() } }
  val settings=taskButton("الإعدادات").apply { text="⚙"; setOnClickListener { showControlPanel() } }
  val staticButtons=listOf(searchButton,browser,files,paint,settings)
  staticButtons.forEach { b -> if((0 until taskbar.childCount).none { taskbar.getChildAt(it).contentDescription==b.contentDescription }) taskbar.addView(b) }
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
  header.addView(TextView(this).apply { text="صور الجهاز";textSize=20f;typeface=Typeface.DEFAULT_BOLD;setTextColor(navy) },LinearLayout.LayoutParams(0,48,1f))
  header.addView(task("＋ إضافة صورة"){ pickPhotoToImport() },LinearLayout.LayoutParams(112,44))
  header.addView(task("↻ تحديث"){ if(hasPhotoPermission()) refreshPhotoGallery() else requestPhotoPermission() },LinearLayout.LayoutParams(96,44))
  page.addView(header)
  galleryStatus=TextView(this).apply { text="جارٍ تحميل الصور…";textSize=13f;setTextColor(navy);setPadding(5,5,5,8) }
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
   grid.addView(TextView(this).apply { text="لا توجد صور ظاهرة. اضغط «إضافة صورة» لاختيار صور من الجهاز.";textSize=16f;setTextColor(navy);gravity=Gravity.CENTER;setPadding(16,24,16,24) },GridLayout.LayoutParams().apply { width=-1;height=120;columnSpec=GridLayout.spec(0,4) })
  }
  photos.forEach { photo ->
   val tile=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(4,4,4,8);isClickable=true;setOnClickListener{openPhotoEditor(photo)} }
   val thumb=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription=photo.name;background=android.graphics.drawable.ColorDrawable(Color.rgb(230,235,243)) }
   try {
    if(Build.VERSION.SDK_INT>=29) thumb.setImageBitmap(contentResolver.loadThumbnail(photo.uri,android.util.Size(240,240),null))
    else contentResolver.openInputStream(photo.uri)?.use { thumb.setImageBitmap(BitmapFactory.decodeStream(it)) }
   } catch(_:Exception) {}
   tile.addView(thumb,LinearLayout.LayoutParams(-1,112))
   tile.addView(TextView(this).apply { text=photo.name;textSize=10f;setTextColor(navy);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER;setPadding(2,3,2,0) },LinearLayout.LayoutParams(-1,34))
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
  page.addView(TextView(this).apply { text=photo.name;textSize=14f;setTextColor(navy);gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE },LinearLayout.LayoutParams(-1,32))
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
  val prefs=getSharedPreferences("orkeit_desktop",Context.MODE_PRIVATE)
  val hidden=prefs.getStringSet("hidden_shortcuts",emptySet())?.toList().orEmpty()
  if(hidden.isEmpty()) {
   AlertDialog.Builder(this).setTitle("سلة المحذوفات").setMessage("سلة المحذوفات فارغة.")
    .setPositiveButton("إغلاق",null).show()
   return
  }
  AlertDialog.Builder(this).setTitle("سلة المحذوفات — اختر اختصارًا لاستعادته")
   .setItems(hidden.toTypedArray()) { _,which ->
    val updated=hidden.toMutableSet()
    val restored=hidden[which]
    updated.remove(restored)
    prefs.edit().putStringSet("hidden_shortcuts",updated).apply()
    showDesktop()
    Toast.makeText(this,"تمت استعادة الاختصار: $restored",Toast.LENGTH_SHORT).show()
   }
   .setNeutralButton("إفراغ السلة") { _,_ ->
    AlertDialog.Builder(this).setTitle("إفراغ سلة المحذوفات؟")
     .setMessage("سيتم حذف الاختصارات من السلة نهائيًا، ولن تُحذف ملفات الهاتف.")
     .setPositiveButton("إفراغ") { _,_ ->
      prefs.edit().putStringSet("hidden_shortcuts",emptySet()).apply()
      showDesktop()
     }.setNegativeButton("إلغاء",null).show()
   }
   .setNegativeButton("إغلاق",null).show()
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
       textSize=25f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
       typeface=Typeface.create("sans-serif-light",Typeface.NORMAL)
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
  val panelWidth=(desktopArea.width*0.92f).toInt().coerceIn(280,(desktopArea.width-16).coerceAtLeast(280))
  val panelHeight=(desktopArea.height*0.90f).toInt().coerceIn(300,(desktopArea.height-16).coerceAtLeast(300))
  val menu=LinearLayout(this).apply {
   tag="start-menu"; orientation=LinearLayout.VERTICAL; setPadding(18,16,18,12)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(247,245,248,253)); cornerRadius=28f; setStroke(1,Color.rgb(220,228,240)) }
   elevation=36f
  }
  val search=EditText(this).apply {
   tag="start-search"; hint="🔎  البحث عن التطبيقات"; textSize=13f; setSingleLine(true)
   setPadding(16,2,16,2); setTextColor(navy); setHintTextColor(Color.rgb(105,120,143))
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=22f; setStroke(1,Color.rgb(220,228,240)) }
  }
  menu.addView(search,LinearLayout.LayoutParams(-1,46))
  val heading=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(2,12,2,4) }
  heading.addView(TextView(this).apply { text="مثبّت"; textSize=18f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy) },LinearLayout.LayoutParams(0,36,1f))
  heading.addView(task("كل التطبيقات  ›"){ search.setText("") })
  menu.addView(heading)
  val grid=GridLayout(this).apply { columnCount=4; useDefaultMargins=false }
  val items=listOf(
   Triple("🌐","متصفح الإنترنت",{showBrowser()}),Triple("🖥️","هذا الكمبيوتر",{showMyComputer()}),
   Triple("📁","مستكشف الملفات",{showMyComputer()}),Triple("📄","PDF",{pickFile("application/pdf")}),
   Triple("🎬","مشغل الفيديو",{showVideoPlayer()}),Triple("🖼️","الصور",{showPhotos()}),Triple("🎵","مشغل الموسيقى",{showMusicPlayer()}),Triple("▶️","YouTube",{showYouTube()}),Triple("🎧","YouTube Music",{showYouTubeMusic()}),Triple("🎨","الرسام",{showPaint()}),
   Triple("📝","Word",{showWord()}),Triple("📊","Excel",{showExcel()}),
   Triple("⚙️","الإعدادات",{showControlPanel()}),Triple("📋","إدارة المهام",{showTaskManager()}),
   Triple("⌨️","Command",{showCommand()}),Triple("🎮","الألعاب",{showGames()}),
   Triple("🧮","الحاسبة",{showCalculator()}),Triple("🗒️","المفكرة",{showNotepad()}),
   Triple("🔄","تحديث التطبيق",{checkForUpdates(true)}),Triple("ℹ️","حول Oriket",{showAbout()})
  )
  items.forEach { item ->
   val tile=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(2,5,2,5)
    background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=12f }
    isClickable=true; isFocusable=true
    setOnClickListener { desktopArea.removeView(menu); item.third.invoke() }
    setOnTouchListener { v,e ->
     if(e.action==MotionEvent.ACTION_DOWN) v.background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(225,235,249)); cornerRadius=12f }
     if(e.action==MotionEvent.ACTION_UP || e.action==MotionEvent.ACTION_CANCEL) v.postDelayed({v.background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius=12f }},160)
     false
    }
   }
   tile.addView(Icon3DView(this,item.first,item.second),LinearLayout.LayoutParams(44,44))
   tile.addView(TextView(this).apply { text=item.second; textSize=12f; gravity=Gravity.CENTER; setTextColor(navy); typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); maxLines=2; minLines=2; includeFontPadding=true; ellipsize=android.text.TextUtils.TruncateAt.END; setLineSpacing(1f,1.02f) },LinearLayout.LayoutParams(-1,36))
   grid.addView(tile,GridLayout.LayoutParams().apply { width=0;height=96;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(4,4,4,4) })
  }
  menu.addView(ScrollView(this).apply { isFillViewport=true; addView(grid) },LinearLayout.LayoutParams(-1,0,1f))
  val recommendedHeading=TextView(this).apply {
   text="موصى به"; textSize=15f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(2,9,2,5)
  }
  menu.addView(recommendedHeading)
  val rec=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  listOf(
   Triple("📄","فتح مستند PDF","عرض ملفات PDF من جهازك"),
   Triple("🎨","الرسام","افتح لوحة الرسم وابدأ مشروعًا جديدًا")
  ).forEachIndexed { index,item ->
   val row=LinearLayout(this).apply {
    orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(8,4,8,4)
    background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(250,251,254)); cornerRadius=12f }
    isClickable=true
    setOnClickListener { desktopArea.removeView(menu); if(index==0) pickFile("application/pdf") else showPaint() }
   }
   row.addView(TextView(this).apply { text=item.first; textSize=23f; gravity=Gravity.CENTER },LinearLayout.LayoutParams(42,42))
   val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
   labels.addView(TextView(this).apply { text=item.second; textSize=12f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy) })
   labels.addView(TextView(this).apply { text=item.third; textSize=10f; setTextColor(Color.DKGRAY) })
   row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
   rec.addView(row,LinearLayout.LayoutParams(-1,48).apply { bottomMargin=3 })
  }
  menu.addView(rec,LinearLayout.LayoutParams(-1,-2))
  val footer=LinearLayout(this).apply {
   gravity=Gravity.CENTER_VERTICAL; setPadding(8,8,8,0)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(235,240,248)); cornerRadius=16f }
  }
  footer.addView(TextView(this).apply { text="👤  Oriket Desktop"; textSize=12f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy) },LinearLayout.LayoutParams(0,42,1f))
  footer.addView(task("⚙"){ desktopArea.removeView(menu); showControlPanel() })
   footer.addView(task("⏻"){ desktopArea.removeView(menu); showPowerMenu() })
  menu.addView(footer,LinearLayout.LayoutParams(-1,48).apply { topMargin=8 })
  desktopArea.addView(menu,FrameLayout.LayoutParams(panelWidth,panelHeight,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=12 })
  menu.bringToFront()
  search.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
    val q=s.toString().trim()
    for(i in 0 until grid.childCount) {
     val tile=grid.getChildAt(i)
     val label=(tile as? ViewGroup)?.let { it.getChildAt(1) as? TextView }?.text?.toString().orEmpty()
     tile.visibility=if(q.isEmpty() || label.contains(q,true)) View.VISIBLE else View.GONE
    }
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
   val intent=Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri,"application/vnd.android.package-archive")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
   }
   startActivity(intent)
  } catch(_:Exception) {
   Toast.makeText(this,"تعذر فتح مثبت الحزم. افتح ملف التحديث من التنزيلات يدويًا.",Toast.LENGTH_LONG).show()
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
   textSize=14f
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
  page.addView(TextView(this).apply { text="📄 $name"; textSize=17f; setTextColor(navy); setPadding(4,4,4,8) })
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
        val pdfPageLabel=TextView(this).apply { text="الصفحة 1 / ${total}"; textSize=13f; setTextColor(navy); gravity=Gravity.CENTER }
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
     viewer.addView(ScrollView(this).apply { addView(TextView(this@MainActivity).apply { text=txt; textSize=14f; setTextColor(Color.DKGRAY); setPadding(12,12,12,12) }) })
    }
    else -> viewer.addView(message("تم اختيار الملف، لكن المعاينة الداخلية لا تدعم هذا النوع بعد."))
   }
  } catch(e:Exception) { viewer.addView(message("تعذرت معاينة الملف داخل التطبيق.")) }
 }
 private fun message(s:String)=TextView(this).apply {
  text=s; textSize=17f; setTextColor(navy); gravity=Gravity.CENTER; setPadding(24,22,24,22)
  typeface=Typeface.create("sans-serif",Typeface.NORMAL); includeFontPadding=true; setLineSpacing(2f,1.08f)
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
  val display=TextView(this).apply { text="0"; textSize=32f; gravity=Gravity.CENTER_VERTICAL or Gravity.END; setTextColor(navy); typeface=Typeface.create("sans-serif-light",Typeface.NORMAL); setPadding(16,10,16,10); background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(239,244,251)); cornerRadius=14f; setStroke(1,border) } }
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
  val editor=EditText(this).apply { hint="اكتب ملاحظاتك هنا"; gravity=Gravity.TOP or Gravity.START; textSize=16f; tag="orkeit-notepad-editor"; setText(notePrefs.getString("draft","").orEmpty()); addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){notePrefs.edit().putString("draft",s.toString()).apply()}; override fun afterTextChanged(s:android.text.Editable?){} }) }
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

 private fun showMyComputer() {
  shell("هذا الكمبيوتر")
  val workspace=java.io.File(filesDir,"OriketWorkspace").apply {
   mkdirs()
   listOf("Documents","Downloads","Pictures","Videos","Projects","Music","Desktop").forEach { java.io.File(this,it).mkdirs() }
  }
  var current:java.io.File?=null
  lateinit var render: () -> Unit
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
  val toolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(7,4,7,4);setBackgroundColor(Color.rgb(246,248,251)) }
  toolbar.addView(task("←"){ if(current!=null){current=current?.parentFile?.takeIf{it.absolutePath.startsWith(workspace.absolutePath + java.io.File.separator)};render()}else showDesktop() },LinearLayout.LayoutParams(44,40))
  toolbar.addView(task("→"){Toast.makeText(this,"لا يوجد مسار تالٍ",Toast.LENGTH_SHORT).show()},LinearLayout.LayoutParams(44,40))
  val address=TextView(this).apply { text="⌂  هذا الكمبيوتر";textSize=14f;setTextColor(navy);setPadding(12,8,12,8);background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=5f;setStroke(1,Color.rgb(210,216,224))} }
  toolbar.addView(address,LinearLayout.LayoutParams(0,40,1f))
  toolbar.addView(task("⟳"){render()},LinearLayout.LayoutParams(44,40))
  page.addView(toolbar)
  val content=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  val sidebar=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(6,8,6,8);setBackgroundColor(Color.rgb(249,250,252)) }
  val main=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(14,10,14,8) }
  content.addView(sidebar,LinearLayout.LayoutParams(150,-1))
  content.addView(ScrollView(this).apply{addView(main)},LinearLayout.LayoutParams(0,-1,1f))
  page.addView(content,LinearLayout.LayoutParams(-1,0,1f))
  fun sectionTitle(text:String) {
   main.addView(TextView(this).apply{this.text=text;textSize=17f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(30,42,60));setPadding(3,8,3,8)})
  }
  fun folderTile(label:String,icon:String,folder:String) {
   val f=java.io.File(workspace,folder).apply{mkdirs()}
   val tile=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(10,5,10,5);background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=5f;setStroke(1,Color.rgb(229,233,239))};isClickable=true;setOnClickListener{current=f;render()}}
   tile.addView(TextView(this).apply{text=icon;textSize=26f;gravity=Gravity.CENTER;setTextColor(blue)},LinearLayout.LayoutParams(45,48))
   tile.addView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL
    addView(TextView(this@MainActivity).apply{text=label;textSize=13f;setTextColor(navy)})
    addView(TextView(this@MainActivity).apply{text=if(f.listFiles().isNullOrEmpty()) "مجلد" else "العناصر: ${f.listFiles()?.size ?: 0}";textSize=10f;setTextColor(Color.GRAY)})
   },LinearLayout.LayoutParams(0,48,1f))
   main.addView(tile,LinearLayout.LayoutParams(-1,58).apply{bottomMargin=5})
  }
  fun driveTile(title:String,sub:String,icon:String,onClick:()->Unit) {
   val tile=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(10,5,10,5);background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=5f;setStroke(1,Color.rgb(220,226,234))};isClickable=true;setOnClickListener{onClick()}}
   tile.addView(TextView(this).apply{text=icon;textSize=30f;gravity=Gravity.CENTER;setTextColor(blue)},LinearLayout.LayoutParams(52,54))
   tile.addView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL
    addView(TextView(this@MainActivity).apply{text=title;textSize=14f;setTextColor(navy)})
    addView(TextView(this@MainActivity).apply{text=sub;textSize=10f;setTextColor(Color.GRAY)})
    val track=ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply{progress=if(title.contains("الهاتف"))45 else 22}
    addView(track,LinearLayout.LayoutParams(-1,5).apply{topMargin=4})
   },LinearLayout.LayoutParams(0,54,1f))
   main.addView(tile,LinearLayout.LayoutParams(-1,64).apply{bottomMargin=7})
  }
  render = {
   address.text=if(current==null)"⌂  هذا الكمبيوتر" else "⌂  هذا الكمبيوتر  ›  "+current!!.name
   sidebar.removeAllViews();main.removeAllViews()
   sidebar.addView(TextView(this).apply{text="الوصول السريع";textSize=12f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.GRAY);setPadding(5,4,0,7)})
   listOf("سطح المكتب" to "🖥","التنزيلات" to "⬇","المستندات" to "📄","الصور" to "🖼","الفيديو" to "🎬","الموسيقى" to "♫","هذا الكمبيوتر" to "💻").forEach { item ->
    sidebar.addView(task(item.second+"  "+item.first){
     current=when(item.first){"سطح المكتب"->java.io.File(workspace,"Desktop");"التنزيلات"->java.io.File(workspace,"Downloads");"المستندات"->java.io.File(workspace,"Documents");"الصور"->java.io.File(workspace,"Pictures");"الفيديو"->java.io.File(workspace,"Videos");"الموسيقى"->java.io.File(workspace,"Music");else->null}
     render()
    },LinearLayout.LayoutParams(-1,38).apply{bottomMargin=2})
   }
   if(current==null) {
    sectionTitle("المجلدات")
    listOf("سطح المكتب" to "Desktop","التنزيلات" to "Downloads","المستندات" to "Documents","الصور" to "Pictures","الفيديو" to "Videos","الموسيقى" to "Music","المشاريع" to "Projects").forEach{folderTile(it.first,"📁",it.second)}
    sectionTitle("الأجهزة ومحركات الأقراص")
    driveTile("القرص المحلي (C:)","مساحة ملفات Oriket الداخلية","💽"){current=workspace;render()}
    driveTile("التخزين الداخلي للهاتف","فتح ملفات الهاتف واستيرادها","📱"){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*"},61)}catch(_:Exception){}}
    main.addView(TextView(this).apply{text="ملاحظة: محركات الأقراص المعروضة واجهة تنظيمية؛ Android لا يتيح للتطبيق إدارة أقراص Windows الفعلية.";textSize=10f;setTextColor(Color.GRAY);setPadding(3,8,3,4)})
   } else {
    main.addView(TextView(this).apply{text=current!!.absolutePath;textSize=11f;setTextColor(Color.GRAY);setPadding(2,2,2,8)})
    main.addView(LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL
     addView(task("＋ مجلد جديد"){val input=EditText(this@MainActivity).apply{hint="اسم المجلد"};AlertDialog.Builder(this@MainActivity).setTitle("مجلد جديد").setView(input).setPositiveButton("إنشاء"){_,_->val n=input.text.toString().trim();if(n.isNotEmpty()&&!n.contains("/")&&java.io.File(current,n).mkdir())render()else Toast.makeText(this@MainActivity,"اسم غير صالح أو موجود",Toast.LENGTH_SHORT).show()}.setNegativeButton("إلغاء",null).show()},LinearLayout.LayoutParams(0,40,1f))
     addView(task("استيراد ملف"){fileManagerDir=current;try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*"},61)}catch(_:Exception){}},LinearLayout.LayoutParams(0,40,1f))
    })
    val entries=current!!.listFiles()?.sortedWith(compareBy<java.io.File>({!it.isDirectory},{it.name.lowercase(Locale.ROOT)})).orEmpty()
    if(entries.isEmpty())main.addView(message("هذا المجلد فارغ"))
    entries.forEach{f->main.addView(task((if(f.isDirectory)"📁  " else "📄  ")+f.name){if(f.isDirectory){current=f;render()}else showFile(Uri.fromFile(f))},LinearLayout.LayoutParams(-1,42).apply{bottomMargin=2})}
   }
  }
  panel(page)
  render()
 }
 private fun showControlPanel() {
  shell("لوحة التحكم")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text="لوحة التحكم"; textSize=23f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(8,4,8,12) })
  listOf(
   "الشاشة والاتجاه" to { AlertDialog.Builder(this).setTitle("الشاشة").setMessage("تم تصميم الواجهة للوضع الأفقي. يمكنك تدوير الجهاز من إعدادات Android.").setPositiveButton("حسنًا",null).show() },
   "خلفية سطح المكتب" to { showWallpaperPicker() },
   "سياسة الخصوصية" to { showPrivacyPolicy() },
   "الشبكة والإنترنت" to { try { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) } catch(_:Exception){} },
   "الصوت" to { try { startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) } catch(_:Exception){} },
   "إعدادات التطبيقات" to { try { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+packageName))) } catch(_:Exception){} },
   "حول Oriket Desktop" to { showAbout() }
  ).forEach { pair -> page.addView(task("⚙  "+pair.first){pair.second.invoke()},LinearLayout.LayoutParams(-1,48).apply { topMargin=3; bottomMargin=3 }) }
  panel(page)
 }
 private fun showTaskManager() {
  shell("إدارة المهام")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text="إدارة المهام"; textSize=22f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(6,4,6,8) })
  val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  fun refresh() {
   list.removeAllViews()
   if(openWindows.isEmpty()) list.addView(message("لا توجد نوافذ مفتوحة"))
   openWindows.toList().forEach { entry ->
    val row=LinearLayout(this@MainActivity).apply { gravity=Gravity.CENTER_VERTICAL }
    row.addView(TextView(this@MainActivity).apply { text=windowIcon(entry.title)+"  "+entry.title; setTextColor(navy); textSize=14f },LinearLayout.LayoutParams(0,44,1f))
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
  val output=TextView(this).apply { text="Oriket Command [محاكاة أوامر داخل التطبيق]\nاكتب help لعرض الأوامر.\n\nC:\\Users\\Oriket>"; typeface=Typeface.MONOSPACE; textSize=13f; setTextColor(Color.rgb(160,245,170)); setPadding(4,4,4,4) }
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
  shell("مشغل الموسيقى")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(10,8,10,8) }
  page.addView(TextView(this).apply { text="مشغل الموسيقى"; textSize=22f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); gravity=Gravity.CENTER; setPadding(4,4,4,8) })
  val currentTitle=TextView(this).apply { text="اضغط تحميل الموسيقى لعرض الأغاني الموجودة على الجهاز"; textSize=17f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); gravity=Gravity.CENTER; maxLines=2; setPadding(8,8,8,8) }
  val currentArtist=TextView(this).apply { text="لم يتم اختيار مقطع"; textSize=14f; setTextColor(Color.DKGRAY); gravity=Gravity.CENTER; maxLines=1 }
  page.addView(currentTitle,LinearLayout.LayoutParams(-1,-2))
  page.addView(currentArtist,LinearLayout.LayoutParams(-1,-2))
  val seek=SeekBar(this)
  val times=TextView(this).apply { text="00:00 / 00:00"; textSize=13f; setTextColor(navy); gravity=Gravity.CENTER }
  page.addView(seek,LinearLayout.LayoutParams(-1,42)); page.addView(times,LinearLayout.LayoutParams(-1,-2))
  val songs=mutableListOf<Uri>(); val titles=mutableListOf<String>(); val artists=mutableListOf<String>()
  var index=0; var shuffle=getSharedPreferences("orkeit_music",Context.MODE_PRIVATE).getBoolean("shuffle",false)
  var prepared=false; var userSeeking=false
  val prefs=getSharedPreferences("orkeit_music",Context.MODE_PRIVATE)
  val progressHandler=Handler(Looper.getMainLooper())
  val progress=object:Runnable { override fun run() {
   val p=musicPlayer
   if(p!=null && prepared) {
    if(!userSeeking) seek.progress=p.currentPosition.coerceAtLeast(0)
    times.text=clockTime(p.currentPosition)+" / "+clockTime(p.duration)
   }
   progressHandler.postDelayed(this,500)
  }}
  fun updateLabels() { currentTitle.text=titles.getOrNull(index) ?: "لا توجد موسيقى"; currentArtist.text=artists.getOrNull(index).orEmpty() }
  fun stopOld() { try { musicPlayer?.stop(); musicPlayer?.release() } catch(_:Exception) {}; musicPlayer=null; prepared=false }
  lateinit var startTrack:(Int)->Unit
  startTrack={ requested ->
   if(songs.isEmpty()) { Toast.makeText(this,"لم يتم العثور على ملفات موسيقى",Toast.LENGTH_LONG).show() }
   else {
    index=((requested%songs.size)+songs.size)%songs.size
    stopOld(); updateLabels()
    try {
     val mp=MediaPlayer(); musicPlayer=mp
     mp.setDataSource(this,songs[index])
     mp.setOnPreparedListener { p ->
      if(musicPlayer!==p) return@setOnPreparedListener
      prepared=true; seek.max=p.duration.coerceAtLeast(1); p.start()
     }
     mp.setOnCompletionListener {
      if(songs.isNotEmpty()) startTrack(if(shuffle && songs.size>1) (0 until songs.size).filter{it!=index}.random() else (index+1)%songs.size)
     }
     mp.setOnErrorListener { _,_,_ -> Toast.makeText(this,"تعذر تشغيل هذا الملف الصوتي",Toast.LENGTH_SHORT).show(); true }
     mp.prepareAsync()
    } catch(_:Exception) { Toast.makeText(this,"تعذر فتح الملف الصوتي",Toast.LENGTH_LONG).show() }
   }
  }
  val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; tag="music-list" }
  fun loadSongs() {
   if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
    requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO),704); return
   }
   if(Build.VERSION.SDK_INT<33 && Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) {
    requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),704); return
   }
   songs.clear(); titles.clear(); artists.clear(); list.removeAllViews()
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
      artists.add(cursor.getString(artistCol)?.takeIf{it.isNotBlank() && it!="<unknown>"} ?: "فنان غير معروف")
     }
    }
    if(songs.isEmpty()) list.addView(message("لم يتم العثور على ملفات موسيقى في الجهاز"))
    songs.forEachIndexed { i,_ ->
     val row=task((i+1).toString()+". "+titles[i]+" — "+artists[i]) { startTrack(i) }
     row.gravity=Gravity.CENTER_VERTICAL or Gravity.START
     list.addView(row,LinearLayout.LayoutParams(-1,48).apply { bottomMargin=3 })
    }
    Toast.makeText(this,"تم العثور على "+songs.size+" مقطع صوتي",Toast.LENGTH_SHORT).show()
    if(songs.isNotEmpty()) startTrack(0)
   } catch(_:Exception) { Toast.makeText(this,"تعذر قراءة مكتبة الموسيقى. تحقق من إذن الملفات.",Toast.LENGTH_LONG).show() }
  }
  fun row(vararg labels:String, handler:(String)->Unit):LinearLayout {
   val result=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
   labels.forEach { label -> result.addView(task(label){handler(label)},LinearLayout.LayoutParams(0,48,1f).apply { setMargins(2,2,2,2) }) }
   return result
  }
  page.addView(row("السابق","تشغيل / إيقاف","التالي") { label ->
   when(label) {
    "السابق" -> if(songs.isNotEmpty()) startTrack(if(shuffle && songs.size>1) (0 until songs.size).filter{it!=index}.random() else (index-1+songs.size)%songs.size)
    "التالي" -> if(songs.isNotEmpty()) startTrack(if(shuffle && songs.size>1) (0 until songs.size).filter{it!=index}.random() else (index+1)%songs.size)
    else -> { val p=musicPlayer; if(p!=null && prepared) { if(p.isPlaying)p.pause() else p.start() } else if(songs.isNotEmpty()) startTrack(index) }
   }
  })
  lateinit var shuffleButton:Button
  shuffleButton=task(if(shuffle)"عشوائي: يعمل" else "عشوائي: متوقف") { shuffle=!shuffle; prefs.edit().putBoolean("shuffle",shuffle).apply(); shuffleButton.text=if(shuffle)"عشوائي: يعمل" else "عشوائي: متوقف" }
  val modes=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
  modes.addView(task("تقديم 10 ثوانٍ"){musicPlayer?.let{if(prepared)it.seekTo((it.currentPosition+10000).coerceAtMost(it.duration))}},LinearLayout.LayoutParams(0,48,1f))
  modes.addView(task("تأخير 10 ثوانٍ"){musicPlayer?.let{if(prepared)it.seekTo((it.currentPosition-10000).coerceAtLeast(0))}},LinearLayout.LayoutParams(0,48,1f))
  modes.addView(shuffleButton,LinearLayout.LayoutParams(0,48,1f))
  page.addView(modes)
  page.addView(action("تحميل الموسيقى من الجهاز"){loadSongs()},LinearLayout.LayoutParams(-1,48).apply { topMargin=4; bottomMargin=4 })
  page.addView(ScrollView(this).apply { isFillViewport=true; addView(list) },LinearLayout.LayoutParams(-1,0,1f))
  seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
   override fun onStartTrackingTouch(bar:SeekBar?) { userSeeking=true }
   override fun onStopTrackingTouch(bar:SeekBar?) { userSeeking=false; if(prepared && bar!=null) musicPlayer?.seekTo(bar.progress) }
   override fun onProgressChanged(bar:SeekBar?,progressValue:Int,fromUser:Boolean) { if(fromUser) times.text=clockTime(progressValue)+" / "+clockTime(musicPlayer?.duration ?: 0) }
  })
  progressHandler.post(progress)
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
  val editor=EditText(this).apply { hint="مستند جديد — ابدأ الكتابة هنا"; gravity=Gravity.TOP or Gravity.START; textSize=17f; setPadding(24,18,24,18); setBackgroundColor(Color.WHITE); tag="orkeit-word-editor"; setText(wordPrefs.getString("draft","").orEmpty()); addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){wordPrefs.edit().putString("draft",s.toString()).apply()}; override fun afterTextChanged(s:android.text.Editable?){} }) }
  toolbar.addView(task("فتح TXT"){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/*"},64)}catch(_:Exception){Toast.makeText(this,"تعذر فتح الملفات",Toast.LENGTH_SHORT).show()}})
  toolbar.addView(task("B"){editor.typeface=if(editor.typeface.isBold) Typeface.create("sans-serif",Typeface.NORMAL) else Typeface.create("sans-serif",Typeface.BOLD)})
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
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text="Excel — جدول البيانات"; textSize=22f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(2,2,2,5) })
  val prefs=getSharedPreferences("orkeit_excel",Context.MODE_PRIVATE)
  val grid=GridLayout(this).apply { columnCount=5; rowCount=11 }
  val cells=mutableListOf<EditText>()
  listOf("صف","A","B","C","D").forEach { h -> grid.addView(TextView(this).apply { text=h; textSize=16f; gravity=Gravity.CENTER; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setBackground(android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(205,224,247)); setStroke(1,border) }) },GridLayout.LayoutParams().apply { width=0; height=46; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) }) }
  val saved=prefs.getString("data","").orEmpty().split("\u001F")
  fun cellIndex(ref:String):Int { val s=ref.trim().uppercase(Locale.ROOT); if(s.length<2)return -1; val col=s[0]-'A'; val row=s.substring(1).toIntOrNull()?.minus(1) ?: return -1; return if(col in 0..3 && row in 0..9) row*4+col else -1 }
  fun cellValue(i:Int,depth:Int=0):Double { if(i !in cells.indices || depth>8)return Double.NaN; val raw=cells[i].text.toString().trim(); if(!raw.startsWith("="))return raw.replace(",",".").toDoubleOrNull() ?: 0.0; val e=raw.drop(1).trim(); val ref=cellIndex(e); if(ref>=0)return cellValue(ref,depth+1); val sum=Regex("(?i)SUM\\(([A-D]\\d+):([A-D]\\d+)\\)").matchEntire(e); if(sum!=null){val x=cellIndex(sum.groupValues[1]);val y=cellIndex(sum.groupValues[2]);if(x>=0&&y>=0)return (minOf(x,y)..maxOf(x,y)).sumOf{cellValue(it,depth+1)}}; val m=Regex("(-?\\d+(?:[.,]\\d+)?)\\s*([+*/-])\\s*(-?\\d+(?:[.,]\\d+)?)").matchEntire(e); if(m!=null){val x=m.groupValues[1].replace(",",".").toDoubleOrNull()?:return Double.NaN;val y=m.groupValues[3].replace(",",".").toDoubleOrNull()?:return Double.NaN;return when(m.groupValues[2]){"+"->x+y;"-"->x-y;"*"->x*y;"/"->if(y==0.0)Double.NaN else x/y;else->Double.NaN}};return Double.NaN }
  fun persist(){prefs.edit().putString("data",cells.joinToString("\u001F"){it.text.toString()}).apply()}
  repeat(40) { i ->
   val cell=EditText(this).apply { setSingleLine(true); textSize=16f; gravity=Gravity.CENTER; setPadding(4,4,4,4); setTextColor(Color.rgb(15,25,40)); setText(saved.getOrNull(i).orEmpty()); setBackground(android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); setStroke(1,border) }); addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){persist()}; override fun afterTextChanged(s:android.text.Editable?){} }) }
   cells.add(cell); grid.addView(cell,GridLayout.LayoutParams().apply { width=0; height=50; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(0,0,0,0) })
  }
  val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  buttons.addView(task("حساب الصيغ") { val results=cells.mapIndexedNotNull { i,e -> if(e.text.toString().trim().startsWith("=")){val v=cellValue(i); ("خلية "+('A'+i%4)+(i/4+1)+" = "+(if(v.isNaN()||v.isInfinite()) "#ERROR" else v.toString()))}else null }; AlertDialog.Builder(this).setTitle("نتائج الصيغ").setMessage(results.joinToString("\n").ifEmpty{"لا توجد صيغ. أمثلة: =A1+B1 أو =SUM(A1:A4)"}).setPositiveButton("حسنًا",null).show() },LinearLayout.LayoutParams(0,50,1f))
  buttons.addView(task("مسح الجدول"){AlertDialog.Builder(this).setTitle("مسح الجدول؟").setPositiveButton("مسح"){_,_->cells.forEach{it.setText("")};persist()}.setNegativeButton("إلغاء",null).show()},LinearLayout.LayoutParams(0,50,1f))
  buttons.addView(action("تصدير CSV"){try{val rows=(0 until 10).joinToString("\n"){r->(0 until 4).joinToString(","){col->val i=r*4+col;val raw=cells[i].text.toString().trim();val value=if(raw.startsWith("="))cellValue(i).let{if(it.isNaN()||it.isInfinite())"#ERROR" else it.toString()}else raw;"\""+value.replace("\"","\"\"")+"\""}};val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,"Oriket-Excel-"+System.currentTimeMillis()+".csv");put(MediaStore.Downloads.MIME_TYPE,"text/csv")};val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri!=null)contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write("\uFEFF"+rows)};Toast.makeText(this,if(uri!=null)"تم تصدير الجدول إلى التنزيلات" else "تعذر التصدير",Toast.LENGTH_LONG).show()}catch(_:Exception){Toast.makeText(this,"تعذر التصدير",Toast.LENGTH_LONG).show()}},LinearLayout.LayoutParams(0,42,1f))
  page.addView(buttons)
  page.addView(TextView(this).apply { text="يُحفظ الجدول تلقائيًا. اكتب رقمًا أو صيغة مثل =A1+B1 أو =SUM(A1:A4)، ثم اضغط حساب الصيغ." ; textSize=14f; setTextColor(navy); setPadding(3,4,3,4) })
  page.addView(ScrollView(this).apply { addView(grid) },LinearLayout.LayoutParams(-1,0,1f))
  panel(page)
 }
 private fun showGames() {
  shell("ألعاب Windows")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text="ألعاب كلاسيكية بسيطة"; textSize=22f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(6,4,6,10) })
  page.addView(action("الثعبان — Snake"){showSnakeGame()})
  page.addView(action("تحدي الأرقام"){showNumberGame()})
  page.addView(TextView(this).apply { text="ألعاب أصلية مرخصة من Microsoft غير مضمنة. هذه ألعاب بسيطة مدمجة في Oriket Desktop."; textSize=12f; setTextColor(navy); setPadding(8,12,8,8) })
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
   p.color=navy; p.textSize=18f; canvas.drawText("Score: $score",12f,24f,p)
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

 private fun showQuickSettings() {
  val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(18,16,18,16); background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(246,249,255)); cornerRadius=22f; setStroke(1,border) } }
  box.addView(TextView(this).apply { text="الإعدادات السريعة"; textSize=20f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(2,2,2,12) })
  val grid=GridLayout(this).apply { columnCount=2 }
  val settings=listOf(
   "Wi-Fi / الشبكة" to { try { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) } catch(_:Exception){} },
   "الصوت" to { try { startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) } catch(_:Exception){} },
   "Bluetooth" to { try { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) } catch(_:Exception){} },
   "إعدادات الجهاز" to { try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch(_:Exception){} },
   "لوحة التحكم" to { showControlPanel() },
   "إظهار/إخفاء شريط النظام" to {
    val hidden=(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN)!=0
    window.decorView.systemUiVisibility=if(hidden) View.SYSTEM_UI_FLAG_LAYOUT_STABLE else (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
   }
  )
  settings.forEach { item ->
   grid.addView(action(item.first){item.second.invoke()},GridLayout.LayoutParams().apply { width=0;height=54;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(4,4,4,4) })
  }
  box.addView(grid)
  box.addView(TextView(this).apply { text="Oriket Desktop • ${BuildConfig.VERSION_NAME}"; textSize=12f; setTextColor(navy); gravity=Gravity.CENTER; setPadding(4,12,4,2) })
  AlertDialog.Builder(this).setView(box).setPositiveButton("إغلاق",null).show()
 }

 private fun showAbout() {
  shell("حول التطبيق")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,20,20,20) }
  page.addView(TextView(this).apply { text="Oriket Desktop\nواجهة أفقية مستوحاة من Windows 11. المتصفح والرسام والحاسبة والمفكرة ومعاينة الصور والنصوص وPDF تعمل داخل التطبيق.\n\nهذه واجهة سطح مكتب وليست نظام Windows فعليًا ولا تشغّل ملفات EXE."; textSize=19f; setTextColor(navy) })
  panel(page)
 }
 private fun showPaint() {
  shell("الرسام")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val canvas=PaintCanvas(this).apply { tag="orkeit-paint-canvas" }
  val tools=LinearLayout(this).apply { gravity=Gravity.CENTER; orientation=LinearLayout.HORIZONTAL }
  listOf("قلم","ممحاة","نص","خط","مستطيل","دائرة","قص").forEach { name -> tools.addView(task(name){canvas.setTool(name)},LinearLayout.LayoutParams(76,50).apply { setMargins(3,0,3,0) }) }
  page.addView(TextView(this).apply { text="أدوات الرسم — اختر أداة ثم ارسم في المساحة البيضاء"; textSize=15f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(6,4,6,6) })
  page.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(tools) },LinearLayout.LayoutParams(-1,54))
  val palette=LinearLayout(this).apply { gravity=Gravity.CENTER; orientation=LinearLayout.HORIZONTAL }
  listOf(Color.BLACK,Color.RED,Color.rgb(0,103,192),Color.GREEN,Color.YELLOW,Color.MAGENTA,Color.WHITE).forEach { color -> palette.addView(Button(this).apply { text="●"; textSize=18f; setTextColor(color); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(235,240,247))); setOnClickListener{canvas.setColor(color)} },LinearLayout.LayoutParams(38,36)) }
  palette.addView(task("لون مخصص"){ val input=EditText(this).apply{hint="#RRGGBB";setSingleLine(true)}; AlertDialog.Builder(this).setTitle("لون مخصص").setView(input).setPositiveButton("تطبيق"){_,_->try{canvas.setColor(Color.parseColor(input.text.toString().trim()))}catch(_:Exception){Toast.makeText(this,"أدخل لونًا مثل #3366FF",Toast.LENGTH_SHORT).show()}}.setNegativeButton("إلغاء",null).show() },LinearLayout.LayoutParams(90,36))
  page.addView(TextView(this).apply { text="الألوان"; textSize=15f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(6,6,6,2) })
  page.addView(HorizontalScrollView(this).apply{addView(palette)},LinearLayout.LayoutParams(-1,48))
  val actions=LinearLayout(this).apply { gravity=Gravity.CENTER }
  actions.addView(task("استيراد صورة"){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*"},62)}catch(_:Exception){}},LinearLayout.LayoutParams(94,42).apply { setMargins(3,0,3,0) })
  actions.addView(task("−"){canvas.zoomBy(0.8f)},LinearLayout.LayoutParams(94,42).apply { setMargins(3,0,3,0) })
  actions.addView(task("تكبير +"){canvas.zoomBy(1.25f)},LinearLayout.LayoutParams(94,42).apply { setMargins(3,0,3,0) })
  actions.addView(task("تراجع"){canvas.undo()},LinearLayout.LayoutParams(94,42).apply { setMargins(3,0,3,0) })
  actions.addView(task("إعادة"){canvas.redo()},LinearLayout.LayoutParams(94,42).apply { setMargins(3,0,3,0) })
  actions.addView(action("مسح"){canvas.clear()},LinearLayout.LayoutParams(94,42).apply { setMargins(3,0,3,0) })
  actions.addView(action("حفظ PNG"){saveDrawing(canvas)},LinearLayout.LayoutParams(94,42).apply { setMargins(3,0,3,0) })
  page.addView(TextView(this).apply { text="إجراءات الرسم"; textSize=15f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(6,6,6,2) })
  page.addView(HorizontalScrollView(this).apply{addView(actions)},LinearLayout.LayoutParams(-1,54))
  page.addView(canvas,LinearLayout.LayoutParams(-1,0,1f))
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
    else -> intArrayOf(Color.rgb(8,27,70),Color.rgb(0,94,171),Color.rgb(25,151,218),Color.rgb(5,48,111))
   }
   canvas.drawRect(0f,0f,w,h,Paint().apply { shader=LinearGradient(0f,0f,w,h,gradient,null,Shader.TileMode.CLAMP) })
   // Layered bloom shapes; presets are saved locally and restored on next launch.
   val cx=w*.61f; val cy=h*.43f
   val colors=when(wallpaperStyle) {
    1 -> intArrayOf(Color.argb(180,115,145,195),Color.argb(170,65,92,143),Color.argb(210,24,40,74),Color.argb(120,150,170,205))
    2 -> intArrayOf(Color.argb(170,255,255,255),Color.argb(160,102,162,218),Color.argb(180,65,125,190),Color.argb(130,245,250,255))
    else -> intArrayOf(Color.argb(210,102,203,255),Color.argb(205,29,137,226),Color.argb(225,5,68,157),Color.argb(170,125,222,255))
   }
   for(i in 0..3) {
    val path=Path()
    val inset=i*24f
    path.moveTo(cx-w*.32f,cy+h*.28f+inset)
    path.cubicTo(cx-w*.30f,cy-h*.42f+inset,cx+w*.17f,cy-h*.44f+inset,cx+w*.32f,cy-h*.06f+inset)
    path.cubicTo(cx+w*.43f,cy+h*.22f+inset,cx+w*.03f,cy+h*.38f+inset,cx-w*.32f,cy+h*.28f+inset)
    p.color=colors[i]; p.style=Paint.Style.FILL; p.shader=null; canvas.drawPath(path,p)
   }
   p.shader=RadialGradient(cx,cy,h*.6f,intArrayOf(Color.argb(70,160,225,255),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
   canvas.drawRect(0f,0f,w,h,p); p.shader=null
  }
 }
 private inner class PaintCanvas(c:Context):View(c) {
  private val paint=Paint(3).apply { color=blue; style=Paint.Style.STROKE; strokeWidth=7f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
  private val paths=mutableListOf<Pair<Path,Paint>>()
  private val undone=mutableListOf<Pair<Path,Paint>>()
  private var path=Path()
  private var tool="قلم"
  private var startX=0f
  private var startY=0f
  private var zoom=1f
  private var cropRect:RectF?=null
  private var imported:Bitmap?=null
  init { setBackgroundColor(Color.WHITE) }
  fun setTool(value:String){tool=value;Toast.makeText(context,"أداة الرسم: $value",Toast.LENGTH_SHORT).show()}
  fun setColor(value:Int){paint.color=value;paint.xfermode=null;paint.strokeWidth=7f}
  fun zoomBy(factor:Float){zoom=(zoom*factor).coerceIn(0.5f,3f);invalidate()}
  fun undo(){if(paths.isNotEmpty())undone.add(paths.removeAt(paths.lastIndex));invalidate()}
  fun redo(){if(undone.isNotEmpty())paths.add(undone.removeAt(undone.lastIndex));invalidate()}
  fun importImage(uri:Uri){try{imported=this@MainActivity.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)};invalidate()}catch(_:Exception){Toast.makeText(context,"تعذر استيراد الصورة",Toast.LENGTH_SHORT).show()}}
  private fun addText(text:String,x:Float,y:Float){val p=Paint(paint).apply{style=Paint.Style.FILL;textSize=32f};val glyph=Path();p.getTextPath(text,0,text.length,x,y,glyph);paths.add(Pair(glyph,p));undone.clear();invalidate()}
  override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.WHITE);c.save();c.scale(zoom,zoom);imported?.let{c.drawBitmap(it,null,RectF(0f,0f,width.toFloat(),height.toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))};paths.forEach{c.drawPath(it.first,it.second)};if(!path.isEmpty)c.drawPath(path,paint);c.restore();cropRect?.let{val p=Paint(3).apply{color=Color.RED;style=Paint.Style.STROKE;pathEffect=DashPathEffect(floatArrayOf(10f,6f),0f)};c.drawRect(it,p)}}
  override fun onTouchEvent(e:MotionEvent):Boolean {
   val x=e.x/zoom;val y=e.y/zoom
   when(e.action){
    MotionEvent.ACTION_DOWN->{startX=x;startY=y;path=Path();path.moveTo(x,y);return true}
    MotionEvent.ACTION_MOVE->{if(tool=="قلم"||tool=="ممحاة")path.lineTo(x,y)else{path=Path();when(tool){"خط"->{path.moveTo(startX,startY);path.lineTo(x,y)};"دائرة"->path.addOval(RectF(startX,startY,x,y),Path.Direction.CW);"مستطيل"->path.addRect(RectF(startX,startY,x,y),Path.Direction.CW);"قص"->path.addRect(RectF(minOf(startX,x),minOf(startY,y),maxOf(startX,x),maxOf(startY,y)),Path.Direction.CW)}};invalidate();return true}
    MotionEvent.ACTION_UP->{if(tool=="نص"){val input=EditText(context).apply{hint="اكتب النص"};AlertDialog.Builder(context).setTitle("إضافة نص").setView(input).setPositiveButton("إضافة"){_,_->if(input.text.isNotBlank())addText(input.text.toString(),x,y)}.setNegativeButton("إلغاء",null).show()}else if(tool=="قص")cropRect=RectF(minOf(startX,x),minOf(startY,y),maxOf(startX,x),maxOf(startY,y))else{val p=Paint(paint);if(tool=="ممحاة"){p.color=Color.WHITE;p.strokeWidth=26f};paths.add(Pair(Path(path),p));undone.clear()};path=Path();invalidate();return true}
   }
   return true
  }
  fun exportBitmap():Bitmap{val bmp=Bitmap.createBitmap(width.coerceAtLeast(1),height.coerceAtLeast(1),Bitmap.Config.ARGB_8888);val cv=Canvas(bmp);cv.drawColor(Color.WHITE);imported?.let{cv.drawBitmap(it,null,RectF(0f,0f,width.toFloat(),height.toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))};cv.save();cv.scale(zoom,zoom);paths.forEach{cv.drawPath(it.first,it.second)};cv.restore();val r=cropRect;if(r!=null&&r.width()>2&&r.height()>2){val l=r.left.toInt().coerceIn(0,bmp.width-1);val t=r.top.toInt().coerceIn(0,bmp.height-1);val rr=r.right.toInt().coerceIn(l+1,bmp.width);val b=r.bottom.toInt().coerceIn(t+1,bmp.height);return Bitmap.createBitmap(bmp,l,t,rr-l,b-t)};return bmp}
  fun clear(){paths.clear();undone.clear();path.reset();cropRect=null;imported=null;invalidate()}
 }
}
