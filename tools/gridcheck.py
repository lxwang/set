"""Decode a screencap PNG (pure stdlib) and report card-rectangle alignment.

Cards are the page's largest rectangles of the card-face colour. After all
animations settle they must sit on a strict 3-column grid; a card left with
residual animation translation shows up as an outlying left/top edge.
"""
import sys, zlib, struct
from collections import deque


def decode_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', "not a png"
    pos, idat, w = 8, b'', None
    while pos < len(data):
        (ln,) = struct.unpack('>I', data[pos:pos + 4])
        typ = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + ln]
        if typ == b'IHDR':
            w, h, depth, ctype, _, _, inter = struct.unpack('>IIBBBBB', chunk)
            assert depth == 8 and inter == 0, (depth, inter)
            assert ctype in (2, 6), ctype
            ch = 3 if ctype == 2 else 4
        elif typ == b'IDAT':
            idat += chunk
        elif typ == b'IEND':
            break
        pos += 12 + ln
    raw = zlib.decompress(idat)
    stride = w * ch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        if f == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return w, h, ch, out


def components(w, h, ch, px, scale, bg, tol):
    W, H = w // scale, h // scale
    mask = bytearray(W * H)
    br, bgc, bb = bg
    for y in range(H):
        row = y * scale * w * ch
        for x in range(W):
            i = row + x * scale * ch
            # "card" = anything meaningfully brighter than the board background,
            # which covers face, border, symbols and digits alike.
            if abs((px[i] + px[i + 1] + px[i + 2]) - (br + bgc + bb)) > 24:
                mask[y * W + x] = 1
    seen = bytearray(W * H)
    boxes = []
    for sy in range(H):
        for sx in range(W):
            k = sy * W + sx
            if not mask[k] or seen[k]:
                continue
            q = deque([k]); seen[k] = 1
            x1 = x2 = sx; y1 = y2 = sy; n = 0
            while q:
                c = q.popleft(); cy, cx = divmod(c, W); n += 1
                if cx < x1: x1 = cx
                if cx > x2: x2 = cx
                if cy < y1: y1 = cy
                if cy > y2: y2 = cy
                for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
                    if 0 <= nx < W and 0 <= ny < H:
                        j = ny * W + nx
                        if mask[j] and not seen[j]:
                            seen[j] = 1; q.append(j)
            bw, bh = (x2 + 1 - x1) * scale, (y2 + 1 - y1) * scale
            if n > 1500 and bw > 0.45 * bh and bh > 0.45 * bw and bw > 120 and bh > 120:
                boxes.append([x1 * scale, y1 * scale, (x2 + 1) * scale, (y2 + 1) * scale])
    return boxes


def clusters(vals, tol=6):
    vals = sorted(vals); groups = [[vals[0]]]
    for v in vals[1:]:
        if v - groups[-1][-1] <= tol:
            groups[-1].append(v)
        else:
            groups.append([v])
    return [sum(g) / len(g) for g in groups]


def check(path, bg=None, tol=6, scale=2):
    w, h, ch, px = decode_png(path)
    if bg is None:
        bg = (px[0], px[1], px[2])  # top-left corner is empty board background
    boxes = components(w, h, ch, px, scale, bg, tol)
    boxes.sort(key=lambda b: (b[1], b[0]))
    if not boxes:
        return False, "no card faces found", boxes
    lefts = clusters([b[0] for b in boxes])
    tops = clusters([b[1] for b in boxes])
    rights = clusters([b[2] for b in boxes])
    widths = sorted(b[2] - b[0] for b in boxes)
    heights = sorted(b[3] - b[1] for b in boxes)
    ok = len(lefts) <= 3 and len(tops) <= 4
    msg = (f"cards={len(boxes)} columns={len(lefts)} rows={len(tops)}\n"
           f"  lefts={[round(v) for v in lefts]}\n  tops={[round(v) for v in tops]}\n"
           f"  widths={widths[0]}..{widths[-1]} heights={heights[0]}..{heights[-1]}")
    return ok, msg, boxes


if __name__ == '__main__':
    for p in sys.argv[1:]:
        ok, msg, _ = check(p)
        print(("PASS " if ok else "FAIL ") + p)
        print(msg)
