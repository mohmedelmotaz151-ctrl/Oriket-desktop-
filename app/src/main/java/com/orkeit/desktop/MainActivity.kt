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
 private val openWindows=mutableListOf<WindowEntry>()
 private var web:WebView?=null
 private data class WindowEntry(val id:Int,val title:String,val frame:View,val taskButton:Button)
 private var activeDownloadId=-1L
 private var updateReceiverRegistered=false
 private val uiHandler=Handler(Looper.getMainLooper())
 private var clockLabel:TextView?=null
 private var clockRunnable:Runnable?=null
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
  initDesktop()
  showDesktop()
  try {
   if(Build.VERSION.SDK_INT>=33) registerReceiver(updateReceiver,IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),RECEIVER_EXPORTED)
   else registerReceiver(updateReceiver,IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
   updateReceiverRegistered=true
  } catch(_:Exception) {}
  checkForUpdates(false)
 }

 override fun onDestroy() {
  if(updateReceiverRegistered) try { unregisterReceiver(updateReceiver) } catch(_:Exception) {}
  clockRunnable?.let { uiHandler.removeCallbacks(it) }
  openWindows.mapNotNull { it.frame as? ViewGroup }.forEach { group ->
   for(i in 0 until group.childCount) (group.getChildAt(i) as? WebView)?.destroy()
  }
  super.onDestroy()
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
   setPadding(8,5,8,5)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(245,245,248,253)); cornerRadius=22f; setStroke(1,Color.rgb(218,226,238)) }
   elevation=12f
  }
  val dock=FrameLayout(this).apply {
   setBackgroundColor(Color.argb(246,224,233,246))
   elevation=14f
  }
  dock.addView(taskbar,FrameLayout.LayoutParams(-2,48,Gravity.CENTER))
  root.addView(dock,LinearLayout.LayoutParams(-1,58))
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
  titlebar.addView(TextView(this).apply { text=windowIcon(title)+"  "+title; textSize=13f; setTextColor(navy); typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER_VERTICAL },LinearLayout.LayoutParams(0,40,1f))
  val minimize=windowButton("—")
  val maximize=windowButton("□")
  val close=windowButton("×")
  titlebar.addView(minimize,LinearLayout.LayoutParams(38,38))
  titlebar.addView(maximize,LinearLayout.LayoutParams(38,38))
  titlebar.addView(close,LinearLayout.LayoutParams(42,38))
  outer.addView(titlebar,LinearLayout.LayoutParams(-1,42))
  val body=FrameLayout(this).apply { setPadding(10,8,10,8); setBackgroundColor(Color.rgb(250,251,254)) }
  outer.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  val lp=FrameLayout.LayoutParams((desktopArea.width*0.78f).toInt().coerceAtLeast(420), (desktopArea.height*0.78f).toInt().coerceAtLeast(280),Gravity.CENTER)
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
  text=label; textSize=18f; isAllCaps=false; setPadding(0,0,0,0); setTextColor(navy)
  background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
  setOnClickListener { }
 }
 private fun windowIcon(title:String):String = when {
  title.contains("المتصفح")->"🌐"
  title.contains("الملفات")->"📁"
  title.contains("الحاسبة")->"🧮"
  title.contains("الرسام")->"🎨"
  title.contains("المفكرة")->"📝"
  title.contains("تحديث")->"🔄"
  else->"🪟"
 }
 private fun taskButton(title:String)=Button(this).apply {
  text=windowIcon(title); textSize=18f; isAllCaps=false; setTextColor(navy)
  setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(235,242,252)))
  contentDescription=title
 }
 private fun task(s:String,a:()->Unit)=Button(this).apply { text=s; textSize=12f; isAllCaps=false; setTextColor(navy); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(239,244,251))); setOnClickListener{a()} }
 private fun action(s:String,a:()->Unit)=Button(this).apply { text=s; textSize=14f; isAllCaps=false; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue)); setOnClickListener{a()} }
 private fun panel(v:View) {
  content.addView(v,FrameLayout.LayoutParams(-1,-1))
 }
 private fun showDesktop() {
  initDesktop()
  // Rebuild only the desktop shortcut layer; open app windows remain independent.
  val old=desktopArea.findViewWithTag<View>("desktop-shortcuts")
  if(old!=null) desktopArea.removeView(old)
  val shortcuts=LinearLayout(this).apply {
   tag="desktop-shortcuts"
   orientation=LinearLayout.VERTICAL
   gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
   setPadding(16,18,16,12)
   background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
  }
  val grid=GridLayout(this).apply { columnCount=2; useDefaultMargins=false }
  val items=listOf(
   Triple("🌐","Chrome / المتصفح",{showBrowser()}), Triple("🖥️","هذا الكمبيوتر",{showMyComputer()}),
   Triple("📄","مشغل PDF",{pickFile("application/pdf")}), Triple("🎬","VLC مشغل الفيديو",{showVideoPlayer()}),
   Triple("🎨","الرسام",{showPaint()}), Triple("📝","Word / معالج النصوص",{showWord()}),
   Triple("📊","Excel / الجداول",{showExcel()}), Triple("⚙️","لوحة التحكم",{showControlPanel()}),
   Triple("📋","إدارة المهام",{showTaskManager()}), Triple("⌨️","Command",{showCommand()}),
   Triple("🎮","ألعاب Windows",{showGames()}), Triple("🧮","الحاسبة",{showCalculator()}),
   Triple("🗒️","المفكرة",{showNotepad()}), Triple("🔄","تحديث التطبيق",{checkForUpdates(true)})
  )
  items.forEach { item ->
   val tile=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(5,8,5,8)
    background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
    isClickable=true; isFocusable=true
    setOnClickListener { item.third.invoke() }
    setOnLongClickListener {
     android.widget.PopupMenu(this@MainActivity,this).apply {
      menu.add("فتح").setOnMenuItemClickListener { item.third.invoke(); true }
      menu.add("تحديث سطح المكتب").setOnMenuItemClickListener { showDesktop(); true }
     }.show()
     true
    }
    setOnHoverListener { v,e ->
     if(e.action==MotionEvent.ACTION_HOVER_ENTER) v.background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(55,255,255,255)); cornerRadius=10f }
     if(e.action==MotionEvent.ACTION_HOVER_EXIT) v.background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
     false
    }
   }
   tile.addView(TextView(this).apply { text=item.first; textSize=30f; gravity=Gravity.CENTER })
   tile.addView(TextView(this).apply { text=item.second; textSize=11f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setShadowLayer(3f,1f,1f,Color.BLACK); setPadding(0,3,0,0) })
   grid.addView(tile,GridLayout.LayoutParams().apply { width=92; height=94; setMargins(2,2,2,2) })
  }
  shortcuts.addView(grid)
  val clock=TextView(this).apply {
   text="Oriket Desktop"; textSize=12f; setTextColor(Color.WHITE); setShadowLayer(3f,1f,1f,Color.BLACK)
   gravity=Gravity.CENTER; setPadding(14,8,14,8)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(85,25,50,90)); cornerRadius=18f }
  }
  shortcuts.addView(clock,LinearLayout.LayoutParams(-2,-2).apply { gravity=Gravity.CENTER_HORIZONTAL; topMargin=12 })
  desktopArea.addView(shortcuts,FrameLayout.LayoutParams(-1,-1))
  shortcuts.bringToFront()
  // Windows stay above shortcuts; move them back to front in stacking order.
  openWindows.forEach { it.frame.bringToFront() }
  val start=taskButton("ابدأ").apply { text="⊞"; textSize=22f; setOnClickListener { showStartMenu() } }
  if(taskbar.childCount==0) taskbar.addView(start)
  val searchButton=taskButton("بحث").apply { text="⌕"; textSize=22f; setOnClickListener { showStartMenu(); desktopArea.findViewWithTag<EditText>("start-search")?.requestFocus() } }
  val browser=taskButton("Chrome").apply { text="🌐"; setOnClickListener { showBrowser() } }
  val files=taskButton("هذا الكمبيوتر").apply { text="📁"; setOnClickListener { showMyComputer() } }
  val paint=taskButton("الرسام").apply { text="🎨"; setOnClickListener { showPaint() } }
  val settings=taskButton("الإعدادات").apply { text="⚙"; setOnClickListener { showControlPanel() } }
  val staticButtons=listOf(searchButton,browser,files,paint,settings)
  staticButtons.forEach { b -> if((0 until taskbar.childCount).none { taskbar.getChildAt(it).contentDescription==b.contentDescription }) taskbar.addView(b) }
 }
 private fun showStartMenu() {
  val existing=desktopArea.findViewWithTag<View>("start-menu")
  if(existing!=null) { desktopArea.removeView(existing); return }
  val menu=LinearLayout(this).apply {
   tag="start-menu"; orientation=LinearLayout.HORIZONTAL; setPadding(10,10,10,10)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(250,248,250,255)); cornerRadius=22f; setStroke(1,border) }
   elevation=32f
  }
  val rail=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(2,8,2,4)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(235,241,250)); cornerRadius=16f }
  }
  rail.addView(TextView(this).apply { text="⊞"; textSize=26f; gravity=Gravity.CENTER; setTextColor(blue); setPadding(0,8,0,20) })
  rail.addView(task("👤"){showAbout()},LinearLayout.LayoutParams(48,48))
  val railSpacer=View(this); rail.addView(railSpacer,LinearLayout.LayoutParams(1,0,1f))
  rail.addView(task("⚙"){desktopArea.removeView(menu);showControlPanel()},LinearLayout.LayoutParams(48,48))
  rail.addView(task("⏻"){AlertDialog.Builder(this).setTitle("إغلاق Oriket Desktop").setMessage("هل تريد الخروج من واجهة سطح المكتب؟").setPositiveButton("خروج"){_,_->finish()}.setNegativeButton("إلغاء",null).show()},LinearLayout.LayoutParams(48,48))
  menu.addView(rail,LinearLayout.LayoutParams(56,-1))
  val main=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(12,4,4,4) }
  val heading=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
  heading.addView(TextView(this).apply { text="ابدأ"; textSize=23f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy) },LinearLayout.LayoutParams(0,42,1f))
  heading.addView(task("كل التطبيقات  ›"){ Toast.makeText(this,"تظهر التطبيقات المثبتة في القائمة أدناه",Toast.LENGTH_SHORT).show() })
  main.addView(heading)
  val search=EditText(this).apply { tag="start-search"; hint="ابحث عن تطبيق أو أداة"; textSize=13f; setSingleLine(true); setPadding(12,2,12,2) }
  main.addView(search,LinearLayout.LayoutParams(-1,42).apply { topMargin=4; bottomMargin=10 })
  main.addView(TextView(this).apply { text="المثبتة"; textSize=14f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(2,4,2,4) })
  val grid=GridLayout(this).apply { columnCount=4; useDefaultMargins=false }
  val items=listOf(
   Triple("🌐","Chrome",{showBrowser()}),Triple("🖥️","هذا الكمبيوتر",{showMyComputer()}),
   Triple("📂","الملفات",{pickFile("*/*")}),Triple("📄","PDF",{pickFile("application/pdf")}),
   Triple("🎬","VLC",{showVideoPlayer()}),Triple("🎨","الرسام",{showPaint()}),
   Triple("📝","Word",{showWord()}),Triple("📊","Excel",{showExcel()}),
   Triple("⚙️","لوحة التحكم",{showControlPanel()}),Triple("📋","المهام",{showTaskManager()}),
   Triple("⌨️","Command",{showCommand()}),Triple("🎮","الألعاب",{showGames()}),
   Triple("🧮","الحاسبة",{showCalculator()}),Triple("🗒️","المفكرة",{showNotepad()}),
   Triple("🔄","تحديث",{checkForUpdates(true)}),Triple("ℹ️","حول التطبيق",{showAbout()})
  )
  items.forEach { item ->
   val b=Button(this).apply {
    text=item.first+"\n"+item.second; isAllCaps=false; textSize=11f
    setTextColor(navy); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(247,249,253)))
    setOnClickListener { desktopArea.removeView(menu); item.third.invoke() }
   }
   grid.addView(b,GridLayout.LayoutParams().apply { width=0;height=66;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(2,2,2,2) })
  }
  main.addView(ScrollView(this).apply { isFillViewport=true; addView(grid) },LinearLayout.LayoutParams(-1,0,1f))
  main.addView(TextView(this).apply { text="موصى به"; textSize=13f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(2,8,2,3) })
  main.addView(TextView(this).apply { text="Oriket Desktop • ${BuildConfig.VERSION_NAME}"; textSize=11f; setTextColor(Color.DKGRAY); setPadding(2,2,2,2) })
  menu.addView(main,LinearLayout.LayoutParams(0,-1,1f))
  desktopArea.addView(menu,FrameLayout.LayoutParams(500,(desktopArea.height*0.86f).toInt().coerceAtLeast(370),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=10 })
  search.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
    val q=s.toString().trim()
    for(i in 0 until grid.childCount) {
     val b=grid.getChildAt(i) as Button
     b.visibility=if(q.isEmpty() || b.text.toString().contains(q,true)) View.VISIBLE else View.GONE
    }
   }
   override fun afterTextChanged(s:android.text.Editable?) {}
  })
 }
 private fun checkForUpdates(manual:Boolean) {
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

 private fun showBrowser(start:String="https://www.google.com") {
  shell("المتصفح")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
  val browser=WebView(this)
  web=browser
  val address=EditText(this).apply { hint="اكتب عنوان موقع أو كلمة بحث"; setSingleLine(true); textSize=13f; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setText(start) }
  fun loadAddress() { val s=address.text.toString().trim(); val target=if(s.startsWith("http://")||s.startsWith("https://")) s else if(s.contains(" ")||!s.contains(".")) "https://www.google.com/search?q="+Uri.encode(s) else "https://"+s; browser.loadUrl(target) }
  row.addView(task("‹"){if(browser.canGoBack())browser.goBack()},LinearLayout.LayoutParams(38,44))
  row.addView(task("›"){if(browser.canGoForward())browser.goForward()},LinearLayout.LayoutParams(38,44))
  row.addView(task("↻"){browser.reload()},LinearLayout.LayoutParams(42,44))
  row.addView(address,LinearLayout.LayoutParams(0,44,1f))
  row.addView(action("اذهب"){loadAddress()},LinearLayout.LayoutParams(72,44))
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
  address.setOnEditorActionListener{_,_,_->loadAddress();true}
 }
 private fun pickFile(type:String) {
  try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type=type },41) }
  catch(_:Exception) { Toast.makeText(this,"تعذر فتح مستكشف الملفات",Toast.LENGTH_LONG).show() }
 }
 @Deprecated("Uses compatible document picker callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==41 && resultCode==RESULT_OK && data?.data!=null) showFile(data.data!!)
  if(requestCode==52 && resultCode==RESULT_OK) {
   val selected=data?.data
   if(selected!=null) {
    val video=desktopArea.findViewWithTag<VideoView>("orkeit-video-view")
    if(video!=null) { video.setVideoURI(selected); video.start() }
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
      val renderer=PdfRenderer(fd)
      if(renderer.pageCount>0) {
       val pg=renderer.openPage(0)
       val bmp=Bitmap.createBitmap(pg.width*2,pg.height*2,Bitmap.Config.ARGB_8888)
       bmp.eraseColor(Color.WHITE); pg.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); pg.close(); renderer.close(); fd.close()
       viewer.addView(ScrollView(this).apply { addView(ImageView(this@MainActivity).apply { setImageBitmap(bmp); scaleType=ImageView.ScaleType.FIT_CENTER },ViewGroup.LayoutParams(-1,900)) })
      } else { renderer.close(); fd.close(); viewer.addView(message("ملف PDF فارغ")) }
     }
    }
    mime.startsWith("text/") || name.endsWith(".txt",true) || name.endsWith(".csv",true) || name.endsWith(".json",true) || name.endsWith(".xml",true) -> {
     val txt=contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()} ?: "تعذر قراءة الملف"
     viewer.addView(ScrollView(this).apply { addView(TextView(this@MainActivity).apply { text=txt; textSize=14f; setTextColor(Color.DKGRAY); setPadding(12,12,12,12) }) })
    }
    else -> viewer.addView(message("تم اختيار الملف، لكن المعاينة الداخلية لا تدعم هذا النوع بعد."))
   }
  } catch(e:Exception) { viewer.addView(message("تعذرت معاينة الملف داخل التطبيق.")) }
 }
 private fun message(s:String)=TextView(this).apply { text=s; textSize=17f; setTextColor(navy); gravity=Gravity.CENTER; setPadding(20,20,20,20) }
 private fun showCalculator() {
  shell("الحاسبة")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val display=EditText(this).apply { textSize=28f; gravity=Gravity.END; inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED; setText("0") }
  page.addView(display,LinearLayout.LayoutParams(-1,65))
  val grid=GridLayout(this).apply { columnCount=4 }
  listOf("7","8","9","÷","4","5","6","×","1","2","3","−","C","0","=","+").forEach { key ->
   val b=action(key) {
    val old=display.text.toString()
    when(key) {
     "C"->display.setText("0")
     "="->try { val op=listOf("+","−","×","÷").first{old.contains(it)}; val parts=old.split(op); val a=parts[0].toDouble(); val z=parts[1].toDouble(); display.setText((when(op){"+"->a+z;"−"->a-z;"×"->a*z;else->a/z}).toString()) } catch(_:Exception){display.setText("خطأ")}
     else->display.setText(if(old=="0") key else old+key)
    }
   }
   grid.addView(b,GridLayout.LayoutParams().apply { width=0; height=56; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(3,3,3,3) })
  }
  page.addView(grid); panel(page)
 }
 private fun showNotepad() {
  shell("المفكرة")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val editor=EditText(this).apply { hint="اكتب ملاحظاتك هنا"; gravity=Gravity.TOP or Gravity.START; textSize=16f }
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
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text="هذا الكمبيوتر"; textSize=22f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(8,4,8,10) })
  val grid=GridLayout(this).apply { columnCount=2 }
  listOf(
   Triple("📂","فتح الملفات",{pickFile("*/*")}),
   Triple("📄","ملفات PDF",{pickFile("application/pdf")}),
   Triple("🖼️","الصور",{pickFile("image/*")}),
   Triple("🎬","الفيديوهات",{showVideoPlayer()}),
   Triple("⬇️","التنزيلات",{try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch(_:Exception) { pickFile("*/*") }}),
   Triple("⚙️","إعدادات الجهاز",{showControlPanel()})
  ).forEach { item ->
   grid.addView(action(item.first+"  "+item.second){ item.third.invoke() },GridLayout.LayoutParams().apply { width=0; height=64; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(4,4,4,4) })
  }
  page.addView(grid)
  page.addView(TextView(this).apply { text="التخزين الظاهر هنا هو تخزين Android الذي يسمح النظام للتطبيق بالوصول إليه. افتح الملفات لاختيار المستندات والصور والفيديوهات."; textSize=13f; setTextColor(navy); setPadding(8,12,8,8) })
  panel(page)
 }
 private fun showControlPanel() {
  shell("لوحة التحكم")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text="لوحة التحكم"; textSize=23f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(8,4,8,12) })
  listOf(
   "الشاشة والاتجاه" to { AlertDialog.Builder(this).setTitle("الشاشة").setMessage("تم تصميم الواجهة للوضع الأفقي. يمكنك تدوير الجهاز من إعدادات Android.").setPositiveButton("حسنًا",null).show() },
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
 private fun showVideoPlayer() {
  shell("VLC مشغل الفيديو")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val bar=LinearLayout(this).apply { gravity=Gravity.CENTER }
  val video=VideoView(this)
  bar.addView(action("فتح فيديو") {
   try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="video/*" },52) }
   catch(_:Exception){Toast.makeText(this,"تعذر فتح منتقي الفيديو",Toast.LENGTH_SHORT).show()}
  })
  bar.addView(action("تشغيل"){video.start()})
  bar.addView(action("إيقاف مؤقت"){video.pause()})
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
  val editor=EditText(this).apply { hint="مستند جديد"; gravity=Gravity.TOP or Gravity.START; textSize=17f; setPadding(24,18,24,18); setBackgroundColor(Color.WHITE) }
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
  page.addView(toolbar); page.addView(ScrollView(this).apply { addView(editor) },LinearLayout.LayoutParams(-1,0,1f)); panel(page)
 }
 private fun showExcel() {
  shell("Excel")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  page.addView(TextView(this).apply { text="Excel — جدول بيانات بسيط"; textSize=19f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy) })
  val grid=GridLayout(this).apply { columnCount=4; rowCount=10 }
  val cells=mutableListOf<EditText>()
  repeat(40) { i ->
   val cell=EditText(this).apply { setSingleLine(true); textSize=12f; gravity=Gravity.CENTER; setPadding(2,2,2,2); setText(if(i<4) listOf("A","B","C","D")[i] else ""); setBackground(android.graphics.drawable.GradientDrawable().apply { setColor(if(i<4) Color.rgb(222,235,247) else Color.WHITE); setStroke(1,border) }) }
   cells.add(cell)
   grid.addView(cell,GridLayout.LayoutParams().apply { width=0; height=42; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(0,0,0,0) })
  }
  page.addView(ScrollView(this).apply { addView(grid) },LinearLayout.LayoutParams(-1,0,1f))
  page.addView(action("تصدير CSV"){
   try {
    val rows=(0 until 10).joinToString("\n"){r->(0 until 4).joinToString(","){col->"\""+cells[r*4+col].text.toString().replace("\"","\"\"")+"\""}}
    val values=ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME,"Oriket-Excel-"+System.currentTimeMillis()+".csv"); put(MediaStore.Downloads.MIME_TYPE,"text/csv") }
    val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)
    if(uri!=null) contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write("\uFEFF"+rows)}
    Toast.makeText(this,if(uri!=null)"تم تصدير الجدول إلى CSV" else "تعذر التصدير",Toast.LENGTH_LONG).show()
   }catch(_:Exception){Toast.makeText(this,"تعذر التصدير",Toast.LENGTH_LONG).show()}
  })
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
  val canvas=PaintCanvas(this)
  val tools=LinearLayout(this).apply { gravity=Gravity.CENTER; orientation=LinearLayout.HORIZONTAL }
  listOf("قلم","خط","مستطيل","دائرة","قص").forEach { name -> tools.addView(task(name){canvas.setTool(name)},LinearLayout.LayoutParams(0,42,1f)) }
  page.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(tools) },LinearLayout.LayoutParams(-1,44))
  val palette=LinearLayout(this).apply { gravity=Gravity.CENTER; orientation=LinearLayout.HORIZONTAL }
  listOf(Color.BLACK,Color.RED,Color.rgb(0,103,192),Color.GREEN,Color.YELLOW,Color.MAGENTA,Color.WHITE).forEach { color ->
   val swatch=Button(this).apply { text="●"; textSize=20f; setTextColor(color); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(235,240,247))); setOnClickListener{canvas.setColor(color)} }
   palette.addView(swatch,LinearLayout.LayoutParams(42,38))
  }
  page.addView(palette)
  val actions=LinearLayout(this).apply { gravity=Gravity.CENTER }
  actions.addView(task("−"){canvas.zoomBy(0.8f)},LinearLayout.LayoutParams(0,42,1f))
  actions.addView(task("تكبير +"){canvas.zoomBy(1.25f)},LinearLayout.LayoutParams(0,42,1f))
  actions.addView(task("تراجع"){canvas.undo()},LinearLayout.LayoutParams(0,42,1f))
  actions.addView(action("مسح"){canvas.clear()},LinearLayout.LayoutParams(0,42,1f))
  actions.addView(action("حفظ PNG"){saveDrawing(canvas)},LinearLayout.LayoutParams(0,42,1f))
  page.addView(actions)
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
 private inner class WindowsWallpaper(c:Context):View(c) {
  private val p=Paint(Paint.ANTI_ALIAS_FLAG)
  override fun onDraw(canvas:Canvas) {
   super.onDraw(canvas)
   val w=width.toFloat(); val h=height.toFloat()
   canvas.drawRect(0f,0f,w,h,Paint().apply { shader=LinearGradient(0f,0f,w,h, intArrayOf(Color.rgb(8,27,70),Color.rgb(0,94,171),Color.rgb(25,151,218),Color.rgb(5,48,111)),null,Shader.TileMode.CLAMP) })
   // Soft, layered blue "bloom" ribbons inspired by the Windows 11 wallpaper.
   val cx=w*.61f; val cy=h*.43f
   val colors=intArrayOf(Color.argb(210,102,203,255),Color.argb(205,29,137,226),Color.argb(225,5,68,157),Color.argb(170,125,222,255))
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
  private var path=Path()
  private var tool="قلم"
  private var startX=0f
  private var startY=0f
  private var endX=0f
  private var endY=0f
  private var zoom=1f
  private var cropRect:RectF?=null
  init { setBackgroundColor(Color.WHITE) }
  fun setTool(value:String){tool=value;Toast.makeText(context,"أداة الرسم: $value",Toast.LENGTH_SHORT).show()}
  fun setColor(value:Int){paint.color=value}
  fun zoomBy(factor:Float){zoom=(zoom*factor).coerceIn(0.5f,3f);invalidate()}
  fun undo(){if(paths.isNotEmpty())paths.removeAt(paths.lastIndex);invalidate()}
  override fun onDraw(c:Canvas) {
   super.onDraw(c); c.drawColor(Color.WHITE)
   c.save(); c.scale(zoom,zoom)
   paths.forEach{c.drawPath(it.first,it.second)}
   if(tool=="قلم") c.drawPath(path,paint)
   else if(path.isEmpty.not()) c.drawPath(path,paint)
   c.restore()
   cropRect?.let { r -> val marker=Paint(3).apply{color=Color.RED;style=Paint.Style.STROKE;strokeWidth=2f;pathEffect=DashPathEffect(floatArrayOf(10f,6f),0f)}; c.drawRect(r,marker) }
  }
  override fun onTouchEvent(e:MotionEvent):Boolean {
   val x=e.x/zoom; val y=e.y/zoom
   when(e.action){
    MotionEvent.ACTION_DOWN->{startX=x;startY=y;endX=x;endY=y;path=Path();path.moveTo(x,y);return true}
    MotionEvent.ACTION_MOVE->{endX=x;endY=y;if(tool=="قلم"){path.lineTo(x,y)}else{path=Path();when(tool){"خط"->path.moveTo(startX,startY).also{path.lineTo(x,y)};"دائرة"->path.addOval(RectF(startX,startY,x,y),Path.Direction.CW);else->path.addRect(RectF(startX,startY,x,y),Path.Direction.CW)}};invalidate();return true}
    MotionEvent.ACTION_UP->{
     endX=x;endY=y
     if(tool=="قص"){cropRect=RectF(minOf(startX,endX),minOf(startY,endY),maxOf(startX,endX),maxOf(startY,endY))}
     else {
      if(tool!="قلم"){path=Path();when(tool){"خط"->{path.moveTo(startX,startY);path.lineTo(endX,endY)};"دائرة"->path.addOval(RectF(startX,startY,endX,endY),Path.Direction.CW);else->path.addRect(RectF(startX,startY,endX,endY),Path.Direction.CW)}}
      paths.add(Pair(Path(path),Paint(paint)))
     }
     path=Path();invalidate();return true
    }
   }
   return true
  }
  fun exportBitmap():Bitmap {
   val full=Bitmap.createBitmap(width.coerceAtLeast(1),height.coerceAtLeast(1),Bitmap.Config.ARGB_8888)
   val cv=Canvas(full);cv.drawColor(Color.WHITE);cv.save();cv.scale(zoom,zoom);paths.forEach{cv.drawPath(it.first,it.second)};cv.restore()
   val r=cropRect
   if(r!=null && r.width()>2 && r.height()>2) {
    val left=r.left.toInt().coerceIn(0,full.width-1);val top=r.top.toInt().coerceIn(0,full.height-1)
    val right=r.right.toInt().coerceIn(left+1,full.width);val bottom=r.bottom.toInt().coerceIn(top+1,full.height)
    return Bitmap.createBitmap(full,left,top,right-left,bottom-top)
   }
   return full
  }
  fun clear(){paths.clear();path.reset();cropRect=null;invalidate()}
 }
}
