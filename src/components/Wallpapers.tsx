import React, { useEffect, useRef } from 'react';
import { WallpaperStyle } from '../types/desktop';

export const ScenicLockWallpaper: React.FC<{ className?: string }> = ({ className = '' }) => {
  return (
    <svg
      viewBox="0 0 1200 720"
      preserveAspectRatio="xMidYMid slice"
      className={`w-full h-full pointer-events-none select-none ${className}`}
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="scenicSkyGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="rgb(12, 38, 73)" />
          <stop offset="35%" stopColor="rgb(73, 154, 193)" />
          <stop offset="72%" stopColor="rgb(244, 190, 112)" />
          <stop offset="100%" stopColor="rgb(35, 35, 36)" />
        </linearGradient>
      </defs>
      <rect width="1200" height="720" fill="url(#scenicSkyGrad)" />
      {/* Sun */}
      <circle cx="816" cy="310" r="80" fill="rgb(255, 224, 165)" />
      {/* Sea */}
      <path d="M 0 403 L 1200 382 L 1200 569 L 0 562 Z" fill="rgb(35, 91, 108)" />
      {/* Sand */}
      <path
        d="M 0 526 C 336 497 744 634 1200 518 L 1200 720 L 0 720 Z"
        fill="rgb(231, 166, 96)"
      />
      {/* Left Rock */}
      <path
        d="M 0 0 L 192 0 C 96 130 276 216 144 353 C 96 490 396 562 576 720 L 0 720 Z"
        fill="rgb(22, 27, 29)"
      />
      {/* Right Rock */}
      <path
        d="M 1032 0 L 1200 0 L 1200 720 L 1020 619 C 912 482 1056 360 948 238 Z"
        fill="rgb(22, 27, 29)"
      />
      {/* Soft atmospheric scrim */}
      <rect width="1200" height="720" fill="rgba(255,255,255,0.14)" />
    </svg>
  );
};

export const WindowsWallpaper: React.FC<{ styleIndex: WallpaperStyle }> = ({ styleIndex }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const draw = () => {
      const parent = canvas.parentElement;
      const w = parent ? parent.clientWidth : window.innerWidth;
      const h = parent ? parent.clientHeight : window.innerHeight;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.max(1, Math.floor(w * dpr));
      canvas.height = Math.max(1, Math.floor(h * dpr));
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

      const gradientColors =
        styleIndex === 1
          ? ['rgb(9,15,30)', 'rgb(28,42,68)', 'rgb(49,65,94)', 'rgb(6,12,24)']
          : styleIndex === 2
          ? ['rgb(224,237,250)', 'rgb(174,207,240)', 'rgb(115,169,222)', 'rgb(232,243,253)']
          : ['rgb(206,227,244)', 'rgb(176,209,236)', 'rgb(192,220,242)', 'rgb(219,235,248)'];

      const bgGrad = ctx.createLinearGradient(0, 0, w, h);
      bgGrad.addColorStop(0, gradientColors[0]);
      bgGrad.addColorStop(0.35, gradientColors[1]);
      bgGrad.addColorStop(0.7, gradientColors[2]);
      bgGrad.addColorStop(1, gradientColors[3]);
      ctx.fillStyle = bgGrad;
      ctx.fillRect(0, 0, w, h);

      // Layered Windows 11 Bloom petals
      const cx = w * 0.54;
      const cy = h * 0.49;
      const petalColors =
        styleIndex === 1
          ? [
              'rgba(115,145,195,0.71)',
              'rgba(65,92,143,0.67)',
              'rgba(24,40,74,0.82)',
              'rgba(150,170,205,0.47)',
            ]
          : styleIndex === 2
          ? [
              'rgba(255,255,255,0.67)',
              'rgba(102,162,218,0.63)',
              'rgba(65,125,190,0.71)',
              'rgba(245,250,255,0.51)',
            ]
          : [
              'rgba(16,136,238,0.92)',
              'rgba(0,98,195,0.90)',
              'rgba(0,58,136,0.92)',
              'rgba(64,185,255,0.82)',
            ];

      const minDim = Math.min(w, h);
      for (let i = 0; i < 4; i++) {
        const inset = i * (minDim * 0.032);
        ctx.beginPath();
        ctx.moveTo(cx - w * 0.34, cy + h * 0.3 + inset);
        ctx.bezierCurveTo(
          cx - w * 0.32,
          cy - h * 0.46 + inset,
          cx + w * 0.16,
          cy - h * 0.48 + inset,
          cx + w * 0.34,
          cy - h * 0.04 + inset
        );
        ctx.bezierCurveTo(
          cx + w * 0.44,
          cy + h * 0.28 + inset,
          cx + w * 0.02,
          cy + h * 0.42 + inset,
          cx - w * 0.34,
          cy + h * 0.3 + inset
        );
        ctx.closePath();
        ctx.fillStyle = petalColors[i];
        ctx.fill();
      }

      const rad = ctx.createRadialGradient(cx, cy, 0, cx, cy, h * 0.58);
      rad.addColorStop(0, 'rgba(145,220,255,0.30)');
      rad.addColorStop(1, 'rgba(145,220,255,0)');
      ctx.fillStyle = rad;
      ctx.fillRect(0, 0, w, h);
    };

    draw();
    window.addEventListener('resize', draw);
    return () => window.removeEventListener('resize', draw);
  }, [styleIndex]);

  return (
    <canvas
      ref={canvasRef}
      className="absolute inset-0 w-full h-full pointer-events-none"
      aria-hidden="true"
    />
  );
};
