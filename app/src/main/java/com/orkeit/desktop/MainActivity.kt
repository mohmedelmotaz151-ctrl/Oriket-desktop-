package com.orkeit.desktop

import android.app.Activity
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

class MainActivity : Activity() {
 private val navy=Color.rgb(18,32,54)
 private val blue=Color.rgb(0,103,192)
 private val pale=Color.rgb(242,246,252)
 private val border=Color.rgb(218,226,238)
 private lateinit var root:LinearLayout
 private lateinit var content:FrameLayout
 private var web:WebView?=null

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=navy
  window.navigationBarColor=navy
  window.decorView.layoutDirection=View.LAYOUT_DIRECTION_LTR
  window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  showDesktop()
 }

 private fun shell(title:String) {
  web=null
  root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(pale); layoutDirection=View.LAYOUT_DIRECTION_LTR }
  val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(18,6,18,6); setBackgroundColor(Color.WHITE) }
  header.addView(TextView(this).apply { text="🪟 Oriket Desktop"; textSize=20f; setTextColor(navy); typeface=Typeface.DEFAULT_BOLD },LinearLayout.LayoutParams(0, -2,1f))
  header.addView(TextView(this).apply { text=title; textSize=14f; setTextColor(navy) })
  root.addView(header,LinearLayout.LayoutParams(-1,52))
  content=FrameLayout(this).apply { setPadding(12,10,12,10) }
  root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
  val bar=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(5,4,5,4); setBackgroundColor(Color.WHITE) }
  bar.addView(task("⊞ ابدأ"){showDesktop()})
  bar.addView(task("🌐 المتصفح"){showBrowser()})
  bar.addView(task("📁 الملفات"){pickFile("*/*")})
  bar.addView(task("🧮 الحاسبة"){showCalculator()})
  bar.addView(task("🎨 الرسام"){showPaint()})
  root.addView(bar,LinearLayout.LayoutParams(-1,56))
  setContentView(root)
 }
 private fun task(s:String,a:()->Unit)=Button(this).apply { text=s; textSize=12f; isAllCaps=false; setTextColor(navy); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(239,244,251))); setOnClickListener{a()} }
 private fun action(s:String,a:()->Unit)=Button(this).apply { text=s; textSize=14f; isAllCaps=false; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue)); setOnClickListener{a()} }
 private fun panel(v:View) {
  val p=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(10,8,10,8); background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=20f; setStroke(1,border) }; elevation=3f }
  p.addView(v,LinearLayout.LayoutParams(-1,-1))
  content.addView(p,FrameLayout.LayoutParams(-1,-1))
 }
 private fun showDesktop() {
  shell("سطح المكتب")
  val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(15,12,15,12) }
  page.addView(TextView(this).apply { text="مرحبًا بك"; textSize=27f; setTextColor(navy); typeface=Typeface.DEFAULT_BOLD })
  page.addView(TextView(this).apply { text="مساحة عمل أفقية مستوحاة من Windows 11"; textSize=14f; setTextColor(Color.DKGRAY); setPadding(0,0,0,8) })
  val grid=GridLayout(this).apply { columnCount=5 }
  val items=listOf(
   Triple("🌐","المتصفح",{showBrowser()}), Triple("📁","مستكشف الملفات",{pickFile("*/*")}),
   Triple("📄","ملفات PDF",{pickFile("application/pdf")}), Triple("🧮","الحاسبة",{showCalculator()}),
   Triple("🎨","الرسام",{showPaint()}), Triple("📝","المفكرة",{showNotepad()}),
   Triple("⚙️","الإعدادات",{showAbout()}), Triple("ℹ️","حول التطبيق",{showAbout()})
  )
  items.forEach { item ->
   val tile=LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(5,6,5,6)
    background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=18f; setStroke(1,border) }
    setOnClickListener{item.third.invoke()}
   }
   tile.addView(TextView(this).apply { text=item.first; textSize=31f; gravity=Gravity.CENTER })
   tile.addView(TextView(this).apply { text=item.second; textSize=12f; gravity=Gravity.CENTER; setTextColor(navy); setPadding(0,4,0,0) })
   grid.addView(tile,GridLayout.LayoutParams().apply { width=0; height=108; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(4,4,4,4) })
  }
  page.addView(grid)
  page.addView(TextView(this).apply { text="المتصفح والأدوات والملفات المدعومة تفتح داخل الواجهة نفسها."; textSize=13f; setTextColor(Color.DKGRAY); setPadding(4,10,4,0) })
  panel(page)
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
