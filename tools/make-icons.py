#!/usr/bin/env python3
"""生成 PWA PNG 图标（纯标准库，无第三方依赖）。
用法: python3 tools/make-icons.py
输出: public/icon-192.png, public/icon-512.png
"""
import struct
import zlib
from pathlib import Path

BG = (246, 245, 242)
BORDER = (201, 163, 106)
INK = (44, 46, 51)


def rounded_rect(x, y, w, h, r):
    """返回一个判断点是否在圆角矩形内的函数"""
    r = min(r, w / 2, h / 2)

    def inside(px, py):
        if px < x or py < y or px >= x + w or py >= y + h:
            return False
        cx = min(max(px, x + r), x + w - r)
        cy = min(max(py, y + r), y + h - r)
        return (px - cx) ** 2 + (py - cy) ** 2 <= r * r

    return inside


def draw_stroke(pixels, size, x1, y1, x2, y2, thickness, color):
    """粗线段（用于绘制“万”字笔画），超采样后调用"""
    half = thickness / 2
    minx = max(0, int(min(x1, x2) - half - 1))
    maxx = min(size, int(max(x1, x2) + half + 1))
    miny = max(0, int(min(y1, y2) - half - 1))
    maxy = min(size, int(max(y1, y2) + half + 1))
    dx, dy = x2 - x1, y2 - y1
    length_sq = dx * dx + dy * dy or 1
    for py in range(miny, maxy):
        for px in range(minx, maxx):
            t = ((px + 0.5 - x1) * dx + (py + 0.5 - y1) * dy) / length_sq
            t = max(0.0, min(1.0, t))
            qx, qy = x1 + t * dx, y1 + t * dy
            if (px + 0.5 - qx) ** 2 + (py + 0.5 - qy) ** 2 <= half * half:
                pixels[py][px] = color


def render(size, supersample=4):
    s = size * supersample
    img = [[(0, 0, 0, 0)] * s for _ in range(s)]
    card = rounded_rect(0, 0, s, s, s * 0.22)
    border = rounded_rect(s * 0.055, s * 0.055, s * 0.89, s * 0.89, s * 0.18)
    inner = rounded_rect(s * 0.075, s * 0.075, s * 0.85, s * 0.85, s * 0.16)
    for y in range(s):
        row = img[y]
        for x in range(s):
            if card(x, y):
                if border(x, y) and not inner(x, y):
                    row[x] = (*BORDER, 255)
                else:
                    row[x] = (*BG, 255)
    # “万”字：三笔
    t = s * 0.052
    draw_stroke(img, s, s * 0.30, s * 0.315, s * 0.72, s * 0.315, t, (*INK, 255))
    draw_stroke(img, s, s * 0.295, s * 0.45, s * 0.68, s * 0.45, t, (*INK, 255))
    draw_stroke(img, s, s * 0.68, s * 0.45, s * 0.665, s * 0.62, t, (*INK, 255))
    draw_stroke(img, s, s * 0.665, s * 0.62, s * 0.60, s * 0.625, t, (*INK, 255))
    draw_stroke(img, s, s * 0.46, s * 0.33, s * 0.30, s * 0.70, t, (*INK, 255))

    # 下采样
    out = [[(0, 0, 0, 0)] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            r = g = b = a = 0
            for dy in range(supersample):
                for dx in range(supersample):
                    pr, pg, pb, pa = img[y * supersample + dy][x * supersample + dx]
                    r += pr * pa
                    g += pg * pa
                    b += pb * pa
                    a += pa
            n = supersample * supersample
            if a == 0:
                out[y][x] = (0, 0, 0, 0)
            else:
                out[y][x] = (r // a, g // a, b // a, a // n)
    return out


def write_png(path, pixels):
    size = len(pixels)
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("4B", *pixels[y][x]) for x in range(size))
        for y in range(size)
    )
    def chunk(tag, data):
        payload = tag + data
        return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)


if __name__ == "__main__":
    root = Path(__file__).resolve().parent.parent / "public"
    for size in (192, 512):
        write_png(root / f"icon-{size}.png", render(size))
        print(f"written public/icon-{size}.png")
