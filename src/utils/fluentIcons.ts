export function getIconPathForTitle(title: string): string {
  if (title.includes('YouTube Music')) return '/icons/win11_youtubemusic.png';
  if (title.includes('YouTube')) return '/icons/win11_youtube.png';
  if (
    title.includes('مشغل الموسيقى') ||
    title === 'الموسيقى' ||
    title.includes('Music') ||
    title.includes('Media Player')
  ) {
    return '/icons/win11_music.png';
  }
  if (title.includes('زيارات الدفاع المدني')) return '/icons/win11_civil_defense.png';
  if (title.includes('متصفح') || title.includes('الإنترنت') || title.includes('المتصفح')) {
    return '/icons/win11_edge.png';
  }
  if (title.includes('هذا الكمبيوتر') || title.includes('الكمبيوتر') || title.includes('This PC')) {
    return '/icons/win11_thispc.png';
  }
  if (title.includes('سلة المحذوفات') || title.includes('Recycle Bin')) {
    return '/icons/win11_recyclebin_empty.png';
  }
  if (title.includes('مستكشف الملفات') || title.includes('الملفات')) {
    return '/icons/win11_file_explorer.png';
  }
  if (title.includes('PDF')) return '/icons/win11_pdf.png';
  if (title.includes('الفيديو') || title.includes('Videos')) return '/icons/win11_movies.png';
  if (title.includes('الصور') || title.includes('عارض الصور') || title.includes('Pictures')) {
    return '/icons/win11_photos.png';
  }
  if (title.includes('الرسام') || title.includes('Paint')) return '/icons/win11_paint.png';
  if (title.includes('Word') || title.includes('معالج النصوص') || title.includes('Document')) {
    return '/icons/win11_word.png';
  }
  if (title.includes('Excel') || title.includes('الجداول') || title.includes('Book')) {
    return '/icons/win11_excel.png';
  }
  if (title.includes('لوحة التحكم') || title.includes('الإعدادات')) return '/icons/win11_settings.png';
  if (title.includes('إدارة المهام') || title.includes('المهام')) return '/icons/win11_taskmanager.png';
  if (title.includes('Command') || title.includes('Snake')) return '/icons/win11_terminal.png';
  if (title.includes('الألعاب') || title.includes('ألعاب')) return '/icons/win11_games.png';
  if (title.includes('الحاسبة')) return '/icons/win11_calculator.png';
  if (title.includes('المفكرة')) return '/icons/win11_notepad.png';
  if (title.includes('تحديث')) return '/icons/win11_settings.png';
  if (title.includes('التنزيلات') || title.includes('Downloads')) return '/icons/win11_folder_downloads.png';
  if (title.includes('المستندات') || title.includes('Documents')) return '/icons/win11_folder_documents.png';
  if (title.includes('سطح المكتب') || title.includes('Desktop')) return '/icons/win11_folder_desktop.png';
  if (title.includes('المشاريع') || title.includes('Projects')) return '/icons/win11_folder.png';
  if (title.includes('حول') || title.includes('About')) return '/icons/win11_settings.png';
  if (title.includes('الشبكة') || title.includes('Wi-Fi')) return '/icons/win11_wifi.png';
  if (title.includes('Bluetooth')) return '/icons/win11_bluetooth.png';
  if (title.includes('الصوت')) return '/icons/win11_music.png';
  if (title.includes('السطوع') || title.includes('الشاشة')) return '/icons/win11_thispc.png';
  if (title.includes('البطارية')) return '/icons/win11_battery.png';
  if (title.includes('الإشعارات')) return '/icons/win11_bell.png';
  if (title.includes('التاريخ') || title.includes('الوقت')) return '/icons/win11_alarm.png';
  if (title.includes('الخصوصية')) return '/icons/win11_protection.png';
  if (title.includes('الخلفية')) return '/icons/win11_photos.png';
  if (title.includes('التطبيقات')) return '/icons/win11_store.png';
  return '/icons/win11_folder.png';
}

export function getIconForFileName(name: string, isDirectory = false): string {
  if (isDirectory) {
    if (name === 'Documents') return '/icons/win11_folder_documents.png';
    if (name === 'Downloads') return '/icons/win11_folder_downloads.png';
    if (name === 'Pictures') return '/icons/win11_folder_pictures.png';
    if (name === 'Videos') return '/icons/win11_folder_videos.png';
    if (name === 'Music') return '/icons/win11_folder_music.png';
    if (name === 'Desktop') return '/icons/win11_folder_desktop.png';
    return '/icons/win11_folder.png';
  }
  const lower = name.toLowerCase();
  if (lower.endsWith('.pdf')) return '/icons/win11_pdf.png';
  if (lower.endsWith('.png') || lower.endsWith('.jpg') || lower.endsWith('.jpeg') || lower.endsWith('.webp')) {
    return '/icons/win11_photos.png';
  }
  if (lower.endsWith('.mp3') || lower.endsWith('.wav') || lower.endsWith('.m4a') || lower.endsWith('.ogg')) {
    return '/icons/win11_music.png';
  }
  if (lower.endsWith('.mp4') || lower.endsWith('.mkv') || lower.endsWith('.webm')) {
    return '/icons/win11_movies.png';
  }
  if (lower.endsWith('.xls') || lower.endsWith('.xlsx') || lower.endsWith('.csv')) {
    return '/icons/win11_excel.png';
  }
  if (lower.endsWith('.doc') || lower.endsWith('.docx')) {
    return '/icons/win11_word.png';
  }
  return '/icons/win11_notepad.png';
}
