import React, { useEffect, useRef, useState } from 'react';
import { getWorkspaceFiles } from '../utils/virtualFs';

interface VideoPlayerAppProps {
  initialName?: string;
  initialUrl?: string;
  onNotify: (title: string, msg: string) => void;
}

const KEY_LAST_POS = 'orkeit_last_video_pos';

export const VideoPlayerApp: React.FC<VideoPlayerAppProps> = ({
  initialName,
  initialUrl,
  onNotify,
}) => {
  const [videoUrl, setVideoUrl] = useState<string | undefined>(initialUrl);
  const [videoName, setVideoName] = useState(initialName || 'مشغل الفيديو');
  const videoRef = useRef<HTMLVideoElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const workspaceVideos = getWorkspaceFiles().filter(
    (f) => !f.isDirectory && (f.mimeType.startsWith('video/') || /\.(mp4|webm|mkv)$/i.test(f.name))
  );

  useEffect(() => {
    const el = videoRef.current;
    if (!el || !videoUrl) return;
    const saved = Number(localStorage.getItem(`${KEY_LAST_POS}_${videoName}`) || 0);
    if (saved > 1) {
      el.currentTime = saved;
    }
  }, [videoUrl, videoName]);

  const handlePickVideo = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const url = URL.createObjectURL(file);
    setVideoUrl(url);
    setVideoName(file.name);
    onNotify('مشغل الفيديو', `تم تحميل: ${file.name}`);
    e.target.value = '';
  };

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-2 bg-[#0B1220] text-white rounded-lg p-2.5" dir="rtl">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <img src="/icons/win11_movies.png" alt="" className="w-5 h-5" />
          <span className="text-xs font-bold truncate">{videoName}</span>
        </div>
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          className="px-3 py-1 rounded-md bg-[#0067C0] hover:bg-[#0057A3] text-xs font-semibold cursor-pointer"
        >
          فتح فيديو من الجهاز
        </button>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept="video/*"
        className="hidden"
        onChange={handlePickVideo}
      />

      <div className="flex-1 min-h-0 bg-black rounded-lg flex items-center justify-center overflow-hidden">
        {videoUrl ? (
          <video
            ref={videoRef}
            src={videoUrl}
            controls
            autoPlay
            onTimeUpdate={(e) => {
              localStorage.setItem(
                `${KEY_LAST_POS}_${videoName}`,
                String(Math.floor(e.currentTarget.currentTime))
              );
            }}
            className="w-full h-full max-h-full object-contain"
          />
        ) : (
          <div className="flex flex-col items-center gap-3 text-center p-6">
            <img src="/icons/win11_movies.png" alt="" className="w-12 h-12 opacity-85" />
            <div className="text-xs text-slate-300">
              اختر مقطع فيديو من جهازك لتشغيله في Oriket Video Player
            </div>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="px-4 py-2 rounded-lg bg-[#0067C0] text-xs font-semibold cursor-pointer"
            >
              تصفح مقاطع الفيديو
            </button>
            {workspaceVideos.length > 0 && (
              <div className="flex flex-wrap gap-2 mt-2">
                {workspaceVideos.map((v) => (
                  <button
                    key={v.id}
                    type="button"
                    onClick={() => {
                      setVideoUrl(v.content);
                      setVideoName(v.name);
                    }}
                    className="px-3 py-1 rounded bg-white/10 hover:bg-white/20 text-xs cursor-pointer"
                  >
                    {v.name}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
