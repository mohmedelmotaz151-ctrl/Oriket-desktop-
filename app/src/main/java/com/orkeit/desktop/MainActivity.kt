package com.orkeit.desktop

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Environment
import android.os.Build
import android.os.Bundle
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
 private data class WindowEntry(val id:Int,val title:String,val frame:FrameLayout,val taskButton:Button)
 private var activeDownloadId=-1L
 private var updateReceiverRegistered=false
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
  super.onDestroy()
 }

 private fun initDesktop() {
  if(desktopInitialized) return
  desktopInitialized=true
  root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); layoutDirection=View.LAYOUT_DIRECTION_LTR }
  desktopArea=FrameLayout(this).apply { layoutDirection=View.LAYOUT_DIRECTION_LTR }
  desktopArea.addView(WindowsWallpaper(this),FrameLayout.LayoutParams(-1,-1))
  root.addView(desktopArea,LinearLayout.LayoutParams(-1,0,1f))
  taskbar=LinearLayout(this).apply {
   gravity=Gravity.CENTER
   orientation=LinearLayout.HORIZONTAL
   setPadding(8,5,8,5)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(245,245,248,253)); cornerRadius=22f; setStroke(1,Color.rgb(218,226,238)) }
   elevation=12f
  }
  val dock=FrameLayout(this).apply { setBackgroundColor(Color.rgb(223,232,245)) }
  dock.addView(taskbar,FrameLayout.LayoutParams(-2,48,Gravity.CENTER))
  root.addView(dock,LinearLayout.LayoutParams(-1,58))
  setContentView(root)
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
   if(outer.visibility==View.VISIBLE && outer.alpha>0f) outer.visibility=View.GONE
   else { outer.visibility=View.VISIBLE; outer.bringToFront(); entryButton.isSelected=true }
  }
  taskbar.addView(entryButton)
  desktopArea.addView(outer,lp)
  outer.bringToFront()
  content=body
  var downX=0f; var downY=0f; var startX=0f; var startY=0f
  var maximized=false
  var oldParams:FrameLayout.LayoutParams?=null
  titlebar.setOnTouchListener { _,e ->
   when(e.action) {
    MotionEvent.ACTION_DOWN -> { downX=e.rawX; downY=e.rawY; val p=outer.layoutParams as FrameLayout.LayoutParams; startX=p.leftMargin.toFloat(); startY=p.topMargin.toFloat(); true }
    MotionEvent.ACTION_MOVE -> {
     if(!maximized) {
      val p=outer.layoutParams as FrameLayout.LayoutParams
      p.gravity=Gravity.TOP or Gravity.LEFT
      p.leftMargin=(startX+e.rawX-downX).toInt().coerceIn(-outer.width+100,desktopArea.width-100)
      p.topMargin=(startY+e.rawY-downY).toInt().coerceIn(0,(desktopArea.height-70).coerceAtLeast(0))
      outer.layoutParams=p
     }
     true
    }
    MotionEvent.ACTION_UP -> true
    else -> true
   }
  }
  minimize.setOnClickListener { outer.visibility=View.GONE }
  maximize.setOnClickListener {
   if(!maximized) {
    oldParams=outer.layoutParams as FrameLayout.LayoutParams
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
   desktopArea.removeView(outer)
   taskbar.removeView(entryButton)
   openWindows.removeAll { it.id==id }
   if(web!=null) web?.stopLoading()
   if(openWindows.isNotEmpty()) { openWindows.last().frame.visibility=View.VISIBLE; openWindows.last().frame.bringToFront() }
  }
  outer.setOnTouchListener { v,e -> if(e.action==MotionEvent.ACTION_DOWN) v.bringToFront(); false }
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
   Triple("🌐","المتصفح",{showBrowser()}), Triple("📁","مستكشف الملفات",{pickFile("*/*")}),
   Triple("📄","ملفات PDF",{pickFile("application/pdf")}), Triple("🧮","الحاسبة",{showCalculator()}),
   Triple("🎨","الرسام",{showPaint()}), Triple("📝","المفكرة",{showNotepad()}),
   Triple("⚙️","الإعدادات",{showAbout()}), Triple("🔄","تحديث التطبيق",{checkForUpdates(true)})
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
  val browser=taskButton("المتصفح").apply { text="🌐"; setOnClickListener { showBrowser() } }
  val files=taskButton("الملفات").apply { text="📁"; setOnClickListener { pickFile("*/*") } }
  val calc=taskButton("الحاسبة").apply { text="🧮"; setOnClickListener { showCalculator() } }
  val paint=taskButton("الرسام").apply { text="🎨"; setOnClickListener { showPaint() } }
  val update=taskButton("التحديث").apply { text="🔄"; setOnClickListener { checkForUpdates(true) } }
  val staticButtons=listOf(browser,files,calc,paint,update)
  staticButtons.forEach { b -> if((0 until taskbar.childCount).none { taskbar.getChildAt(it).contentDescription==b.contentDescription }) taskbar.addView(b) }
 }
 private fun showStartMenu() {
  val existing=desktopArea.findViewWithTag<View>("start-menu")
  if(existing!=null) { desktopArea.removeView(existing); return }
  val menu=LinearLayout(this).apply {
   tag="start-menu"; orientation=LinearLayout.VERTICAL; setPadding(18,18,18,18)
   background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(247,248,250,255)); cornerRadius=24f; setStroke(1,border) }
   elevation=30f
  }
  menu.addView(TextView(this).apply { text="ابدأ"; textSize=22f; typeface=Typeface.DEFAULT_BOLD; setTextColor(navy); setPadding(4,0,4,10) })
  val search=EditText(this).apply { hint="ابحث عن تطبيق أو أداة"; textSize=13f; setSingleLine(true) }
  menu.addView(search,LinearLayout.LayoutParams(-1,44))
  val grid=GridLayout(this).apply { columnCount=3 }
  listOf(
   Triple("🌐","المتصفح",{showBrowser()}),Triple("📁","الملفات",{pickFile("*/*")}),
   Triple("🧮","الحاسبة",{showCalculator()}),Triple("🎨","الرسام",{showPaint()}),
   Triple("📝","المفكرة",{showNotepad()}),Triple("⚙️","الإعدادات",{showAbout()})
  ).forEach { item ->
   val b=Button(this).apply { text=item.first+"\n"+item.second; isAllCaps=false; textSize=12f; setOnClickListener { desktopArea.removeView(menu); item.third.invoke() } }
   grid.addView(b,GridLayout.LayoutParams().apply { width=0; height=76; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(3,3,3,3) })
  }
  menu.addView(grid)
  menu.addView(action("إيقاف الواجهة") { finish() })
  desktopArea.addView(menu,FrameLayout.LayoutParams(360,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=10 })
  search.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
    val q=s.toString().trim()
    for(i in 0 until grid.childCount) grid.getChildAt(i).visibility=if(q.isEmpty() || grid.getChildAt(i).let { (it as Button).text.toString().contains(q,true) }) View.VISIBLE else View.GONE
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
  val address=EditText(this).apply { hint="اكتب عنوان موقع أو كلمة بحث"; setSingleLine(true); textSize=14f; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setText(start) }
  val go=action("انتقال"){}
  row.addView(address,LinearLayout.LayoutParams(0,48,1f)); row.addView(go,LinearLayout.LayoutParams(90,48))
  page.addView(row)
  val browser=WebView(this)
  web=browser
  browser.settings.javaScriptEnabled=true
  browser.settings.domStorageEnabled=true
  browser.webViewClient=object:WebViewClient() { override fun onPageFinished(view:WebView?,url:String?) { if(url!=null && !address.hasFocus()) address.setText(url) } }
  browser.webChromeClient=WebChromeClient()
  page.addView(browser,LinearLayout.LayoutParams(-1,0,1f))
  panel(page)
  fun load() { val s=address.text.toString().trim(); val target=if(s.startsWith("http://")||s.startsWith("https://")) s else if(s.contains(" ")||!s.contains(".")) "https://www.google.com/search?q="+Uri.encode(s) else "https://"+s; browser.loadUrl(target) }
  go.setOnClickListener{load()}
  address.setOnEditorActionListener{_,_,_->load();true}
  browser.loadUrl(start)
 }
 private fun pickFile(type:String) {
  try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type=type },41) }
  catch(_:Exception) { Toast.makeText(this,"تعذر فتح مستكشف الملفات",Toast.LENGTH_LONG).show() }
 }
 @Deprecated("Uses compatible document picker callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==41 && resultCode==RESULT_OK && data?.data!=null) showFile(data.data!!)
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
  val buttons=LinearLayout(this).apply { gravity=Gravity.CENTER }
  buttons.addView(action("مسح"){canvas.clear()},LinearLayout.LayoutParams(0,48,1f))
  buttons.addView(action("حفظ PNG"){saveDrawing(canvas)},LinearLayout.LayoutParams(0,48,1f))
  page.addView(buttons); page.addView(canvas,LinearLayout.LayoutParams(-1,0,1f)); panel(page)
 }
 private fun saveDrawing(v:PaintCanvas) {
  try {
   val bmp=Bitmap.createBitmap(v.width.coerceAtLeast(1),v.height.coerceAtLeast(1),Bitmap.Config.ARGB_8888); v.draw(Canvas(bmp))
   val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"orkeit-drawing-"+System.currentTimeMillis()+".png"); put(MediaStore.Images.Media.MIME_TYPE,"image/png"); put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/OrkeitDesktop") }
   val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
   if(uri!=null) contentResolver.openOutputStream(uri)?.use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)}
   Toast.makeText(this,if(uri!=null)"تم حفظ الرسم" else "تعذر الحفظ",Toast.LENGTH_LONG).show()
  } catch(_:Exception){Toast.makeText(this,"تعذر الحفظ",Toast.LENGTH_LONG).show()}
 }
 override fun onBackPressed() { val b=web; if(b!=null && b.canGoBack()) b.goBack() else showDesktop() }
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
  init { setBackgroundColor(Color.WHITE) }
  override fun onDraw(c:Canvas) { super.onDraw(c); c.drawColor(Color.WHITE); paths.forEach{c.drawPath(it.first,it.second)}; c.drawPath(path,paint) }
  override fun onTouchEvent(e:MotionEvent):Boolean { when(e.action){MotionEvent.ACTION_DOWN->{path=Path();path.moveTo(e.x,e.y);invalidate();return true};MotionEvent.ACTION_MOVE->{path.lineTo(e.x,e.y);invalidate();return true};MotionEvent.ACTION_UP->{path.lineTo(e.x,e.y);paths.add(Pair(path,Paint(paint)));path=Path();invalidate();return true}};return true }
  fun clear(){paths.clear();path.reset();invalidate()}
 }
}
