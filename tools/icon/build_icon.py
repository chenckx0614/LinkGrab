"""Rebuild launcher icon as a seamless full-bleed composition.

Why: an adaptive icon only shows the centre 72/108 of the canvas. Any card
artwork narrower than that always leaves a ring of the background layer
visible, and the card's own inner shadow makes that ring read as a hard dark
border no matter how much it is shrunk.

Approach: paint the card's own gradient (clamp-extended from a clean inner ring)
across the whole canvas as the background layer, then place the card back at a
comfortable size with a soft feathered edge so it melts into that gradient.

usage: python tools/icon/build_icon.py [content_ratio]
"""
import sys, os, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pngtool import decode_png_rgba, encode_png_rgba, bbox, resize_box

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
SRC = os.path.join(ROOT, 'icon.png')
CANVAS = 432
CONTENT_RATIO = float(sys.argv[1]) if len(sys.argv) > 1 else 0.60
INSET = 0.05        # trim the card's outer glow before extending it
FEATHER = 10        # px of alpha feather at the card edge (on the 432 canvas)

NB = ((1, 0), (-1, 0), (0, 1), (0, -1))


def fill_holes(px, w, h):
    """Make every pixel opaque by growing card colours into the corners."""
    out = bytearray(px)
    known = bytearray(w * h)
    for i in range(w * h):
        if out[i * 4 + 3] >= 250:
            known[i] = 1
    q = collections.deque()
    for i in range(w * h):
        if known[i]:
            continue
        x, y = i % w, i // w
        for dx, dy in NB:
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and known[ny * w + nx]:
                q.append(i)
                break
    while q:
        i = q.popleft()
        if known[i]:
            continue
        x, y = i % w, i // w
        r = g = b = n = 0
        for dx, dy in NB:
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and known[ny * w + nx]:
                j = ny * w + nx
                r += out[j * 4]; g += out[j * 4 + 1]; b += out[j * 4 + 2]; n += 1
        if not n:
            continue
        o = i * 4
        out[o] = r // n; out[o + 1] = g // n; out[o + 2] = b // n
        out[o + 3] = 255
        known[i] = 1
        for dx, dy in NB:
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not known[ny * w + nx]:
                q.append(ny * w + nx)
    for i in range(w * h):
        out[i * 4 + 3] = 255
    return bytes(out)


def blur_alpha(px, w, h, radius):
    """Soft box blur of the alpha channel only (keeps colours intact)."""
    src = [px[i * 4 + 3] for i in range(w * h)]
    tmp = [0] * (w * h)
    for y in range(h):
        row = y * w
        acc = 0
        for x in range(-radius, radius + 1):
            acc += src[row + min(max(x, 0), w - 1)]
        for x in range(w):
            tmp[row + x] = acc // (2 * radius + 1)
            acc += src[row + min(x + radius + 1, w - 1)] - src[row + max(x - radius, 0)]
    out_a = [0] * (w * h)
    for x in range(w):
        acc = 0
        for y in range(-radius, radius + 1):
            acc += tmp[min(max(y, 0), h - 1) * w + x]
        for y in range(h):
            out_a[y * w + x] = acc // (2 * radius + 1)
            acc += tmp[min(y + radius + 1, h - 1) * w + x] - tmp[max(y - radius, 0) * w + x]
    out = bytearray(px)
    for i in range(w * h):
        out[i * 4 + 3] = out_a[i]
    return bytes(out)


def crop_card(px, w, h):
    x0, x1, y0, y1 = bbox(px, w, h, 24)
    pad = int((x1 - x0 + 1) * INSET)
    x0 += pad; y0 += pad; x1 -= pad; y1 -= pad
    cw, ch = x1 - x0 + 1, y1 - y0 + 1
    card = bytearray(cw * ch * 4)
    for y in range(ch):
        src = ((y0 + y) * w + x0) * 4
        card[y * cw * 4:(y + 1) * cw * 4] = px[src:src + cw * 4]
    return fill_holes(bytes(card), cw, ch), cw, ch


def place(card, cw, ch, tw, th):
    scaled = resize_box(card, cw, ch, tw, th)
    canvas = bytearray(CANVAS * CANVAS * 4)
    ox, oy = (CANVAS - tw) // 2, (CANVAS - th) // 2
    for y in range(th):
        row = (oy + y) * CANVAS
        for x in range(tw):
            i = (y * tw + x) * 4
            o = (row + ox + x) * 4
            canvas[o:o + 4] = scaled[i:i + 4]
    return canvas, ox, oy, tw, th


def blur_rgb(px, w, h, radius):
    """Separable box blur over colour channels (alpha untouched)."""
    for c in range(3):
        tmp = [0] * (w * h)
        for y in range(h):
            row = y * w
            acc = 0
            for x in range(-radius, radius + 1):
                acc += px[(row + min(max(x, 0), w - 1)) * 4 + c]
            for x in range(w):
                tmp[row + x] = acc // (2 * radius + 1)
                acc += px[(row + min(x + radius + 1, w - 1)) * 4 + c]
                acc -= px[(row + max(x - radius, 0)) * 4 + c]
        for x in range(w):
            acc = 0
            for y in range(-radius, radius + 1):
                acc += tmp[min(max(y, 0), h - 1) * w + x]
            for y in range(h):
                i = (y * w + x) * 4 + c
                px[i] = acc // (2 * radius + 1)
                acc += tmp[min(y + radius + 1, h - 1) * w + x]
                acc -= tmp[max(y - radius, 0) * w + x]
    return bytes(px)


def smooth_gradient(card, cw, ch, small=96, margin=0.16):
    """Rebuild the card's own gradient across the full canvas.

    Only the inner band is sampled (the outer ring holds the card's inner
    shadow, rounded-corner fill and the cat's whiskers), then clamped outwards.
    The result is a clean, continuous version of the card gradient.
    """
    s = resize_box(card, cw, ch, small, small)
    m = max(2, int(small * margin))
    hi = small - 1 - m
    buf = bytearray(small * small * 4)
    for y in range(small):
        sy = min(max(y, m), hi)
        for x in range(small):
            sx = min(max(x, m), hi)
            i = (sy * small + sx) * 4
            o = (y * small + x) * 4
            buf[o:o + 3] = s[i:i + 3]
            buf[o + 3] = 255
    big = bytearray(resize_box(bytes(buf), small, small, CANVAS, CANVAS))
    return blur_rgb(big, CANVAS, CANVAS, 5)


def masked_preview(full, path):
    prev = 512
    shown = int(prev * 72 / 108)
    big = resize_box(full, CANVAS, CANVAS, prev, prev)
    r = shown * 0.23
    out = bytearray(prev * prev * 4)
    for y in range(prev):
        for x in range(prev):
            lx = x - (prev - shown) // 2
            ly = y - (prev - shown) // 2
            if not (0 <= lx < shown and 0 <= ly < shown):
                continue
            dx = max(r - (lx + .5), 0, (lx + .5) - (shown - r))
            dy = max(r - (ly + .5), 0, (ly + .5) - (shown - r))
            if dx * dx + dy * dy <= r * r:
                i = (y * prev + x) * 4
                out[i:i + 4] = big[i:i + 4]
                out[i + 3] = 255
    encode_png_rgba(path, prev, prev, bytes(out), 9)


def edge_colour(card, cw, ch, depth=0.03):
    """Average colour just inside the card border — used as a mask fallback."""
    d = max(1, int(min(cw, ch) * depth))
    pts = [(cw // 2, d), (cw // 2, ch - 1 - d), (d, ch // 2), (cw - 1 - d, ch // 2),
           (cw // 2, ch // 2)]
    r = g = b = 0
    for x, y in pts:
        i = (y * cw + x) * 4
        r += card[i]; g += card[i + 1]; b += card[i + 2]
    n = len(pts)
    return bytes((r // n, g // n, b // n))


def main():
    w, h, px = decode_png_rgba(SRC)
    card, cw, ch = crop_card(px, w, h)

    # background: a heavily blurred copy of the card, so anything a wider
    # launcher mask reveals is a soft halo of the same colours instead of a
    # hard band. Cost nothing visually when the card already overflows the crop.
    tiny = resize_box(card, cw, ch, 24, 24)
    pad = bytearray(24 * 24 * 4)
    for i in range(24 * 24):
        pad[i * 4:i * 4 + 4] = tiny[i * 4:i * 4 + 4]
    bg = bytearray(resize_box(bytes(pad), 24, 24, CANVAS, CANVAS))
    bg = bytearray(blur_rgb(bg, CANVAS, CANVAS, 10))

    # foreground: the card, large enough that the mask crops into it
    fw = int(CANVAS * CONTENT_RATIO)
    fh = max(1, round(fw * ch / cw))
    fg_canvas, fox, foy, ftw, fth = place(card, cw, ch, fw, fh)

    # preview composite: background + foreground, then launcher mask
    comp_a = bytearray(bg)
    for i in range(CANVAS * CANVAS):
        o = i * 4
        a = fg_canvas[o + 3]
        if a == 0:
            continue
        inv = 255 - a
        for c in range(3):
            comp_a[o + c] = (fg_canvas[o + c] * a + comp_a[o + c] * inv) // 255
        comp_a[o + 3] = 255

    encode_png_rgba(os.path.join(ROOT, 'app/src/main/res/drawable/ic_launcher_background.png'),
                    CANVAS, CANVAS, bg, 9)
    encode_png_rgba(os.path.join(ROOT, 'app/src/main/res/drawable/ic_launcher_foreground.png'),
                    CANVAS, CANVAS, fg_canvas, 9)

    # legacy launchers show the whole bitmap (no 72/108 crop), so the card has
    # to fill the frame and let the rounded mask trim its corners
    lw = int(CANVAS * 1.02)
    lh = max(1, round(lw * ch / cw))
    lg_canvas, lox, loy, ltw, lth = place(card, cw, ch, lw, lh)
    comp = bytearray(bg)
    for i in range(CANVAS * CANVAS):
        o = i * 4
        a = lg_canvas[o + 3]
        if a == 0:
            continue
        inv = 255 - a
        for c in range(3):
            comp[o + c] = (lg_canvas[o + c] * a + comp[o + c] * inv) // 255
        comp[o + 3] = 255

    # legacy launcher bitmaps: the composed artwork under a rounded-square mask
    radii = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
    for dens, size in radii.items():
        small = resize_box(bytes(comp), CANVAS, CANVAS, size, size)
        r = size * 0.23
        out = bytearray(small)
        for y in range(size):
            for x in range(size):
                cover = 0
                for sy in range(4):
                    for sx in range(4):
                        pxx = x + (sx + .5) / 4
                        pyy = y + (sy + .5) / 4
                        dx = max(r - pxx, 0, pxx - (size - r))
                        dy = max(r - pyy, 0, pyy - (size - r))
                        if dx * dx + dy * dy <= r * r:
                            cover += 1
                i = (y * size + x) * 4
                out[i + 3] = out[i + 3] * cover // 16
        encode_png_rgba(os.path.join(ROOT, 'app/src/main/res/mipmap-%s/ic_launcher.png' % dens),
                        size, size, bytes(out), 9)

    masked_preview(bytes(comp_a), os.path.join(ROOT, 'tools/icon/preview_masked.png'))
    print('card %.0f%% of canvas, feather %dpx' % (CONTENT_RATIO * 100, FEATHER))


if __name__ == '__main__':
    main()
