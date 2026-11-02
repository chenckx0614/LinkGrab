import struct, zlib, math

def decode_png_rgba(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    pos = 8; idat = b''; w = h = 0
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos+4])[0]
        typ = data[pos+4:pos+8]; chunk = data[pos+8:pos+8+ln]
        if typ == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', chunk[:10])
            assert bd == 8 and ct in (2, 6), (bd, ct, path)
        elif typ == b'IDAT': idat += chunk
        elif typ == b'IEND': break
        pos += 12 + ln
    bpp = 4 if ct == 6 else 3
    raw = zlib.decompress(idat); stride = w * bpp
    out = bytearray(h * stride); prev = bytearray(stride); p = 0
    for y in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if f == 1:
            for i in range(bpp, stride): line[i] = (line[i] + line[i-bpp]) & 255
        elif f == 2:
            for i in range(stride): line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                b = prev[i]; c = prev[i-bpp] if i >= bpp else 0
                pa, pb, pc = abs(b-c), abs(a-c), abs(a+b-2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y*stride:(y+1)*stride] = line; prev = line
    if bpp == 4:
        return w, h, bytes(out)
    rgba = bytearray(w*h*4)
    for i in range(w*h):
        rgba[i*4:i*4+3] = out[i*3:i*3+3]
        rgba[i*4+3] = 255
    return w, h, bytes(rgba)

def encode_png_rgba(path, w, h, px, level=6):
    raw = b''.join(b'\x00' + px[y*w*4:(y+1)*w*4] for y in range(h))
    def ch(t, d):
        c = t + d
        return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n'
    png += ch(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    png += ch(b'IDAT', zlib.compress(raw, level))
    png += ch(b'IEND', b'')
    open(path, 'wb').write(png)

def bbox(px, w, h, thresh=24):
    xs = []; ys = []
    for y in range(h):
        row = y * w * 4
        for x in range(w):
            if px[row + x*4 + 3] > thresh:
                xs.append(x); ys.append(y)
    if not xs: return None
    return min(xs), max(xs), min(ys), max(ys)

def resize_box(src, sw, sh, dw, dh):
    out = bytearray(dw*dh*4)
    xmap = []
    for dx in range(dw):
        x0 = dx*sw//dw; x1 = max(x0+1, (dx+1)*sw//dw)
        xmap.append((x0, x1))
    for dy in range(dh):
        y0 = dy*sh//dh; y1 = max(y0+1, (dy+1)*sh//dh)
        for dx in range(dw):
            x0, x1 = xmap[dx]
            r = g = b = a = n = 0
            for sy in range(y0, y1):
                base = sy*sw
                for sx in range(x0, x1):
                    i = (base + sx)*4
                    aa = src[i+3]
                    r += src[i]*aa; g += src[i+1]*aa; b += src[i+2]*aa; a += aa; n += 1
            o = (dy*dw+dx)*4
            if a:
                out[o] = r//a; out[o+1] = g//a; out[o+2] = b//a
            out[o+3] = a//n
    return bytes(out)
