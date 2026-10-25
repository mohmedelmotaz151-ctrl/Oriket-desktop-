package com.orkeit.desktop

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.provider.MediaStore
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.content.ContentValues
import android.graphics.Typeface
import android.widget.*
import android.text.InputType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
 private val navy = Color.rgb(16,43,78)
 private val blue = Color.rgb(23,105,170)
 private val pale = Color.rgb(233,242,252)
 private lateinit var root: LinearLayout
 private lateinit var content: LinearLayout
 private lateinit var clock: TextView

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = navy
  window.navigationBarColor = navy
  window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
  showDesktop()
 }

 private fun base() {
  root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(pale); layoutDirection=View.LAYOUT_DIRECTION_RTL }
  val header = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(18,14,18,14); setBackgroundColor(Color.WHITE) }
  val brand = TextView(this).apply { text="🪟 Oriket Desktop\nمساحة عمل ذكية"; textSize=18f; setTextColor(navy); typeface=Typeface.DEFAULT_BOLD }
  header.addView(brand, LinearLayout.LayoutParams(0,-2,1f))
  clock = TextView(this).apply { textSize=13f; setTextColor(navy) }
  header.addView(clock)
  root.addView(header)
  val scroll=ScrollView(this)
  content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(14,16,14,16) }
  scroll.addView(content)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val taskbar=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(8,8,8,8); setBackgroundColor(Color.WHITE) }
  taskbar.addView(button("⊞ ابدأ",true){showDesktop()})
  taskbar.addView(button("🌐"){showBrowser()})
  taskbar.addView(button("📁"){pickFiles("*/*",true)})
  taskbar.addView(button("🎨"){showPaint()})
  root.addView(taskbar)
  setContentView(root)
  clock.text=SimpleDateFormat("EEE hh:mm a",Locale("ar")).format(Date())
 }
 private fun button(label:String, primary:Boolean=false, action:()->Unit):Button =
  Button(this).apply { text=label; textSize=13f; isAllCaps=false; setTextColor(if(primary) Color.WHITE else navy); setBackgroundTintList(android.content.res.ColorStateList.valueOf(if(primary) navy else Color.rgb(239,245,252))); setOnClickListener{action()} }
 private fun heading(text:String) { content.addView(TextView(this).apply { this.text=text; textSize=20f; setTextColor(navy); typeface=Typeface.DEFAULT_BOLD; setPadding(4,4,4,12) }) }
 private fun card(text:String, action:()->Unit) {
  val b=Button(this).apply { this.text=text; textSize=16f; isAllCaps=false; gravity=Gravity.CENTER; setTextColor(navy); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE)); setPadding(10,24,10,24); setOnClickListener{action()} }
  val lp=LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,5,0,7); content.addView(b,lp)
 }
 private fun showDesktop() {
  base(); heading("مرحبًا بك في Oriket Desktop")
  val grid=GridLayout(this).apply { columnCount=2 }
  val apps=listOf<Pair<String,()->Unit>>(
   "🌐\nالمتصفح" to {showBrowser()},
   "🎨\nالرسام" to {showPaint()},
   "📄\nملفات PDF" to {pickFiles("application/pdf",false)},
   "📁\nملفات الهاتف" to {pickFiles("*/*",true)},
   "🧮\nالحاسبة" to {launchPackage("com.google.android.calculator")},
   "⚙️\nالإعدادات" to {openSystemSettings()},
   "🖥️\nحول التطبيق" to {showAbout()}
  )
  apps.forEach { item ->
   val b=Button(this).apply { text=item.first; textSize=17f; isAllCaps=false; setTextColor(navy); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE)); setOnClickListener{item.second()} }
   val p=GridLayout.LayoutParams().apply { width=0; height=150; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(5,5,5,5) }
   grid.addView(b,p)
  }
  content.addView(grid)
  content.addView(TextView(this).apply { text="تطبيق أندرويد أصلي بواجهة سطح مكتب، وليس صفحة ويب.\nيمكنه فتح المواقع، الرسم باللمس، واختيار ملفات الهاتف."; textSize=14f; setTextColor(Color.DKGRAY); setPadding(8,20,8,8) })
 }
 private fun showBrowser() {
  base(); heading("🌐 فتح موقع")
  val field=EditText(this).apply { hint="أدخل عنوان الموقع مثل https://google.com"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setText("https://www.google.com"); textSize=16f }
  content.addView(field)
  content.addView(button("فتح في المتصفح",true) {
   var address=field.text.toString().trim()
   if(address.isNotEmpty()) {
    if(!address.startsWith("http://") && !address.startsWith("https://")) address="https://"+address
    try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(address))) }
    catch(_:Exception) { Toast.makeText(this,"تعذر فتح الموقع",Toast.LENGTH_LONG).show() }
   }
  })
  listOf("Google" to "https://www.google.com","YouTube" to "https://www.youtube.com","Bing" to "https://www.bing.com").forEach { pair -> card("فتح "+pair.first){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(pair.second)))} }
  content.addView(TextView(this).apply { text="يفتح التطبيق متصفح أندرويد المثبت على جهازك. لا يتضمن Chrome أو إضافاته بشكل مدمج."; textSize=13f; setTextColor(Color.DKGRAY) })
 }
 private fun pickFiles(mime:String,multiple:Boolean) {
  val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type=mime; putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple) }
  try { startActivityForResult(intent,41) } catch(_:Exception) { Toast.makeText(this,"لا يوجد تطبيق مناسب لفتح الملفات",Toast.LENGTH_LONG).show() }
 }
 @Deprecated("Uses compatible document picker callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==41 && resultCode==RESULT_OK && data!=null) {
   val uris=mutableListOf<Uri>()
   data.data?.let{uris.add(it)}
   data.clipData?.let{clip-> for(i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)}
   uris.distinct().forEach { uri ->
    try { startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri,contentResolver.getType(uri) ?: "*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
    catch(_:Exception) { Toast.makeText(this,"لا يوجد عارض مناسب لهذا الملف",Toast.LENGTH_LONG).show() }
   }
  }
 }
 private fun showPaint() {
  base(); heading("🎨 الرسام")
  val controls=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  val canvasView=PaintCanvas(this)
  controls.addView(button("مسح"){canvasView.clearCanvas()},LinearLayout.LayoutParams(0,-2,1f))
  controls.addView(button("حفظ PNG",true){saveDrawing(canvasView)},LinearLayout.LayoutParams(0,-2,1f))
  content.addView(controls)
  content.addView(canvasView,LinearLayout.LayoutParams(-1,650))
  content.addView(TextView(this).apply { text="ارسم بإصبعك ثم اضغط حفظ PNG لحفظ الصورة في معرض الهاتف."; textSize=13f; setTextColor(Color.DKGRAY) })
 }
 private fun saveDrawing(view:PaintCanvas) {
  val bitmap=android.graphics.Bitmap.createBitmap(view.width.coerceAtLeast(1),view.height.coerceAtLeast(1),android.graphics.Bitmap.Config.ARGB_8888)
  view.draw(Canvas(bitmap))
  try {
   val values=ContentValues().apply {
    put(MediaStore.Images.Media.DISPLAY_NAME,"orkeit-drawing-"+System.currentTimeMillis()+".png")
    put(MediaStore.Images.Media.MIME_TYPE,"image/png")
    put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/OrkeitDesktop")
   }
   val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
   if(uri!=null) contentResolver.openOutputStream(uri)?.use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   Toast.makeText(this,if(uri!=null)"تم حفظ الرسم في الصور" else "تعذر حفظ الصورة",Toast.LENGTH_LONG).show()
  } catch(_:Exception) { Toast.makeText(this,"تعذر حفظ الصورة",Toast.LENGTH_LONG).show() }
 }
 private fun showAbout() {
  base(); heading("حول Oriket Desktop")
  content.addView(TextView(this).apply {
   text="هذا تطبيق Android أصلي بواجهة سطح مكتب مستقلة. يوفّر فتح المواقع في المتصفح المثبت، اختيار ملفات الهاتف وفتحها بالتطبيقات المناسبة، ورسّامًا يعمل باللمس ويحفظ PNG.\n\nتنبيه: ليس نظام Windows ولا يشغّل برامج EXE أو تطبيقات Windows المكتبية. تشغيل Windows الحقيقي يتطلب محاكاة/توافقًا متخصصًا أو الاتصال بجهاز Windows."
   textSize=16f; setTextColor(navy); setPadding(8,8,8,8)
  })
 }
 private fun openSystemSettings() { try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch(_:Exception) { showAbout() } }
 private fun launchPackage(packageName:String) {
  val intent=packageManager.getLaunchIntentForPackage(packageName)
  if(intent!=null) startActivity(intent) else Toast.makeText(this,"التطبيق غير مثبت على الهاتف",Toast.LENGTH_LONG).show()
 }
 private inner class PaintCanvas(context:Context):View(context) {
  private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=blue; style=Paint.Style.STROKE; strokeWidth=8f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
  private val paths=mutableListOf<Pair<Path,Paint>>()
  private var current=Path()
  init { setBackgroundColor(Color.WHITE) }
  override fun onDraw(canvas:Canvas) { super.onDraw(canvas); canvas.drawColor(Color.WHITE); paths.forEach{canvas.drawPath(it.first,it.second)}; canvas.drawPath(current,stroke) }
  override fun onTouchEvent(event:MotionEvent):Boolean {
   when(event.action) {
    MotionEvent.ACTION_DOWN->{current=Path();current.moveTo(event.x,event.y);invalidate();return true}
    MotionEvent.ACTION_MOVE->{current.lineTo(event.x,event.y);invalidate();return true}
    MotionEvent.ACTION_UP->{current.lineTo(event.x,event.y);paths.add(Pair(current,Paint(stroke)));current=Path();invalidate();return true}
   };return true
  }
  fun clearCanvas(){paths.clear();current.reset();invalidate()}
 }
}
