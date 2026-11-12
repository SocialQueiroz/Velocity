"""
Interior do Aurion VX (Velo 0.08) — refeito com superfícies esculpidas.
Convenções: metros, Y para cima, frente em +Z, esquerda (motorista) em +X.
"""
import math
from meshlib import (Loft, box, obox, cylinder, tube, beam, quad_img, smoothstep, gauss, mix, add, sub, mul, norm, cross)

SEAT_X, SEAT_HIP_Y, SEAT_Z = 0.40, 0.27, -0.30
SW_CENTER = (SEAT_X, 0.86, 0.06)
SW_TILT = math.radians(22)
CL_CENTER = (SEAT_X, 0.877, 0.29)          # centro do quadro de instrumentos
CL_TILT = math.radians(16)
CL_W, CL_H = 0.32, 0.085
GAUGE_U = 0.085                              # deslocamento lateral dos mostradores
SWEEP = math.radians(270)

MATERIALS = {
    "leather":   (30, 28, 28, 255),      # couro preto
    "cognac":    (158, 92, 52, 255),     # couro caramelo
    "cognac_d":  (120, 68, 38, 255),     # caramelo sombreado (laterais/abas)
    "alcantara": (50, 50, 54, 255),
    "stitch":    (226, 168, 24, 255),
    "carpet":    (26, 26, 28, 255),
    "mat":       (38, 38, 42, 255),
    "dash":      (20, 21, 24, 255),
    "piano":     (8, 8, 10, 255),
    "rubber":    (14, 14, 14, 255),
    "alu":       (158, 160, 165, 255),
    "alu_d":     (104, 106, 112, 255),
    "ambient":   (70, 205, 255, 255),    # luz ambiente (emissiva)
    "needle":    (255, 70, 40, 255),     # ponteiros (emissivos)
    "start":     (230, 36, 36, 255),
}

def steering_axis():
    return (0.0, -math.sin(SW_TILT), math.cos(SW_TILT))

def cluster_frame():
    right = (-1.0, 0.0, 0.0)                                   # direita do motorista
    up = (0.0, math.cos(CL_TILT), math.sin(CL_TILT))
    toward_driver = (0.0, math.sin(CL_TILT), -math.cos(CL_TILT))
    return right, up, toward_driver

# ---------------------------------------------------------------- imagens das telas
def register_images(atlas):
    from PIL import Image, ImageDraw, ImageFont
    try: font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 28)
    except Exception: font = ImageFont.load_default()
    K = 4  # superamostragem
    # --- quadro de instrumentos: conta-giros (esq.) e velocímetro (dir.), sem números falsos
    W, H = 160, 42
    im = Image.new("RGBA", (W * K, H * K), (6, 8, 12, 255)); d = ImageDraw.Draw(im)
    px_per_m = W / CL_W
    for cx_m, ticks, red_from in ((-GAUGE_U, 8, 7), (GAUGE_U, 17, None)):
        cx, cy, R = (cx_m / CL_W + 0.5) * W * K, H * K / 2, 0.038 * px_per_m * K
        d.ellipse((cx - R, cy - R, cx + R, cy + R), fill=(12, 15, 22, 255), outline=(60, 70, 86, 255), width=K)
        for i in range(ticks * 2 + 1):
            f = i / (ticks * 2)
            a = math.radians(225 - 270 * f)
            major = i % 2 == 0
            r0 = R * (0.74 if major else 0.84)
            col = (235, 60, 50, 255) if (red_from is not None and f >= red_from / ticks) else ((230, 236, 245, 255) if major else (120, 132, 150, 255))
            d.line((cx + r0 * math.cos(a), cy - r0 * math.sin(a), cx + R * 0.95 * math.cos(a), cy - R * 0.95 * math.sin(a)), fill=col, width=K * (2 if major else 1))
        d.arc((cx - R * 0.62, cy - R * 0.62, cx + R * 0.62, cy + R * 0.62), 135, 45, fill=(40, 150, 220, 255), width=K)
    d.text((W * K / 2, H * K * 0.30), "AURION", fill=(210, 216, 226, 255), font=font, anchor="mm")
    d.line((W * K * 0.40, H * K * 0.52, W * K * 0.60, H * K * 0.52), fill=(226, 168, 24, 255), width=K)
    d.text((W * K / 2, H * K * 0.74), "VX", fill=(226, 168, 24, 255), font=font, anchor="mm")
    atlas.add_image("img_cluster", im.resize((W, H), Image.LANCZOS))
    # --- navegação: mapa escuro, quarteirões, rota, seta de posição
    W, H = 108, 60
    im = Image.new("RGBA", (W * K, H * K), (10, 14, 20, 255)); d = ImageDraw.Draw(im)
    for i in range(-6, 14):
        d.line((i * 40 * K / 2, 0, i * 40 * K / 2 + 120 * K, H * K), fill=(34, 44, 58, 255), width=K * 2)
        d.line((0, i * 30 * K / 2, W * K, i * 30 * K / 2 - 60 * K), fill=(34, 44, 58, 255), width=K * 2)
    d.polygon([(60 * K, 30 * K), (84 * K, 22 * K), (92 * K, 40 * K), (70 * K, 48 * K)], fill=(18, 44, 34, 255))
    route = [(18 * K, 56 * K), (30 * K, 40 * K), (52 * K, 34 * K), (60 * K, 18 * K), (90 * K, 10 * K)]
    d.line(route, fill=(60, 200, 255, 255), width=K * 3, joint="curve")
    ax, ay = route[1]
    d.polygon([(ax, ay - 6 * K), (ax + 4 * K, ay + 4 * K), (ax, ay + 2 * K), (ax - 4 * K, ay + 4 * K)], fill=(255, 255, 255, 255))
    d.rectangle((0, 0, W * K, 8 * K), fill=(16, 22, 32, 255))
    d.rectangle((0, 0, 10 * K, H * K), fill=(16, 22, 32, 255))
    for i in range(4): d.rectangle((3 * K, (13 + i * 11) * K, 7 * K, (17 + i * 11) * K), fill=(90, 104, 124, 255))
    atlas.add_image("img_nav", im.resize((W, H), Image.LANCZOS))
    # --- painel de climatização
    W, H = 80, 24
    im = Image.new("RGBA", (W * K, H * K), (8, 10, 14, 255)); d = ImageDraw.Draw(im)
    for i in range(5):
        d.rectangle(((30 + i * 5) * K, (16 - i * 2) * K, (33 + i * 5) * K, 18 * K), fill=(60, 200, 255, 255) if i < 3 else (40, 52, 66, 255))
    for x in (12, 68):
        d.ellipse(((x - 8) * K, 4 * K, (x + 8) * K, 20 * K), outline=(70, 205, 255, 255), width=K)
    atlas.add_image("img_climate", im.resize((W, H), Image.LANCZOS))

# ---------------------------------------------------------------- painel
def dashboard(m, I):
    xs = [round(-0.80 + 0.04 * i, 4) for i in range(41)]
    secs = []
    for x in xs:
        g = gauss(x - SEAT_X, 0.20)            # capuz do quadro, voltado ao motorista
        p = gauss(x + 0.42, 0.26)              # recorte do lado do passageiro
        c = gauss(x - 0.02, 0.13)              # pilha central
        A = (0.52, 0.31 + 0.02 * p)
        B = (0.70, 0.245 + 0.022 * p - 0.018 * c)
        C = (0.80 + 0.02 * g, 0.240 + 0.036 * p)
        D = (0.848 + 0.085 * g, 0.275 - 0.012 * g + 0.02 * p)
        D2 = (0.856 + 0.03 * g, 0.42)
        E = (0.856, 0.66)
        F = (0.52, 0.66)
        secs.append([(x, y, z) for (y, z) in (A, B, C, D, D2, E, F)])
    loft = Loft(secs, axis=0)
    def mat(i, s, n, x):
        if i == 0: return "cognac"
        if i == 1: return "dash"
        if i == 2: return None if abs(x - SEAT_X) < 0.17 else "leather"     # abertura do quadro
        if i in (3, 4): return "alcantara"
        return "dash"
    loft.emit(m, I, mat, cap_mat="dash", flip=True)
    # friso de alumínio, luz ambiente e costura (interrompidos na coluna/quadro)
    for x0, x1 in ((-0.80, 0.21), (0.59, 0.80)):
        loft.decal(m, I, "alu", 1, x0, x1, 0.80, 0.88, -0.003)
        loft.decal(m, I, "ambient", 1, x0, x1, 0.90, 0.97, -0.003)
        loft.decal(m, I, "stitch", 2, x0, x1, 0.46, 0.52, -0.003)
    loft.decal(m, I, "carbon", 1, -0.74, -0.14, 0.12, 0.72, -0.003)       # aplique do passageiro
    loft.decal(m, I, "alu", 1, -0.52, -0.36, 0.36, 0.48, -0.006)          # plaqueta
    loft.decal(m, I, "piano", 0, -0.70, -0.18, 0.52, 0.55, -0.003)        # linha do porta-luvas
    # quadro de instrumentos rebaixado sob o capuz
    r, u, n = cluster_frame()
    quad_img(m, I, "img_cluster", CL_CENTER, r, u, CL_W, CL_H)
    obox(m, I, "piano", add(CL_CENTER, mul(n, -0.012)), r, u, CL_W + 0.03, CL_H + 0.03, 0.02)
    box(m, I, "dash", SEAT_X - 0.175, 0.795, 0.238, SEAT_X + 0.175, 0.828, 0.30)           # prateleira inferior
    for sx in (-1, 1):
        box(m, I, "dash", SEAT_X + sx * 0.170, 0.80, 0.238, SEAT_X + sx * 0.182, 0.945, 0.31)   # paredes laterais
    # ponteiros (partes animadas: giram em torno do eixo do mostrador)
    for name, gu in (("needle_rpm", -GAUGE_U), ("needle_speed", GAUGE_U)):
        gc = add(CL_CENTER, mul(r, gu))
        dir2 = add(mul(r, math.cos(math.radians(225))), mul(u, math.sin(math.radians(225))))
        perp = cross(n, dir2)
        obox(m, name, "needle", add(add(gc, mul(dir2, 0.017)), mul(n, 0.004)), dir2, perp, 0.034, 0.0035, 0.002)
        tube(m, name, "piano", [add(gc, mul(n, 0.002)), add(gc, mul(n, 0.007))], 0.006, seg=10)
    # tela central flutuante, voltada ao motorista
    sn = norm((0.17, 0.12, -1.0))
    sr = norm(cross((0, 1, 0), sn)); su = cross(sn, sr)
    sc = (0.02, 0.885, 0.212)
    quad_img(m, I, "img_nav", sc, sr, su, 0.27, 0.15)
    obox(m, I, "piano", add(sc, mul(sn, -0.010)), sr, su, 0.29, 0.17, 0.018)
    obox(m, I, "alu_d", add(sc, add(mul(sn, -0.03), mul(su, -0.07))), sr, su, 0.10, 0.06, 0.03)      # suporte
    # difusores centrais finos + redondos nas extremidades
    box(m, I, "piano", -0.13, 0.728, 0.218, 0.17, 0.772, 0.236)
    for k in range(4):
        box(m, I, "alu", -0.125, 0.734 + k * 0.010, 0.214, 0.165, 0.737 + k * 0.010, 0.220)
    for x in (-0.72, 0.72):
        zc = 0.262 if x < 0 else 0.232
        cylinder(m, I, "alu", "z", (x, 0.752, zc), 0.046, 0.03, seg=20, cap_mat="alu", hollow=0.037)
        cylinder(m, I, "piano", "z", (x, 0.752, zc + 0.006), 0.037, 0.012, seg=16)
        for ang in (0, 60, 120):
            a = math.radians(ang)
            obox(m, I, "alu_d", (x, 0.752, zc - 0.006), (math.cos(a), math.sin(a), 0), (0, 0, 1), 0.070, 0.004, 0.006)
    return loft

# ---------------------------------------------------------------- console central
def console(m, I):
    zs = [round(-0.92 + 0.04 * i, 4) for i in range(30)] + [0.26]
    def top(z):
        return 0.50 + 0.17 * smoothstep(-0.02, 0.26, z) + 0.035 * gauss(z + 0.56, 0.15)
    secs = []
    for z in zs:
        yt = top(z); wb, wt = 0.165, 0.125
        secs.append([(-wb, 0.19, z), (wb, 0.19, z), (wt, yt - 0.03, z), (wt - 0.014, yt, z), (-wt + 0.014, yt, z), (-wt, yt - 0.03, z)])
    loft = Loft(secs)
    loft.emit(m, I, lambda i, s, n, z: [None, "cognac", "alu", "carbon", "alu", "cognac"][i], cap_mat="carbon")
    for seg, (t0, t1) in ((1, (0.86, 0.93)), (5, (0.07, 0.14))):
        loft.decal(m, I, "ambient", seg, -0.86, 0.22, t0, t1, 0.003)
    up = (0, 1, 0)
    # seletor de câmbio: base, haste e pomo arredondado
    yb = top(-0.02)
    box(m, I, "piano", -0.075, yb, -0.11, 0.075, yb + 0.006, 0.07)
    tube(m, I, "alu", [(0, yb, -0.02), (0, yb + 0.055, -0.035)], 0.009, seg=8)
    knob = [(0, yb + 0.050, -0.034), (0, yb + 0.062, -0.037), (0, yb + 0.085, -0.042), (0, yb + 0.104, -0.046), (0, yb + 0.112, -0.048)]
    kr = [0.012, 0.024, 0.028, 0.022, 0.008]
    tube(m, I, "leather", knob, lambda i: kr[i], seg=12)
    tube(m, I, "stitch", [(0, yb + 0.070, -0.0385), (0, yb + 0.074, -0.0395)], 0.0275, seg=12, caps=False)
    # seletor de modos (giratório), botão de partida, porta-copos
    yd = top(-0.19)
    tube(m, I, "alu", [(0.0, yd, -0.19), (0.0, yd + 0.022, -0.19)], 0.030, seg=16)
    tube(m, I, "piano", [(0.0, yd + 0.022, -0.19), (0.0, yd + 0.026, -0.19)], 0.024, seg=16)
    ys = top(0.10)
    slope = norm((0, top(0.14) - top(0.06), 0.08)); sn = cross((1, 0, 0), slope)
    tube(m, I, "alu", [(-0.07, ys, 0.10), add((-0.07, ys, 0.10), mul(sn, -0.006))], 0.022, seg=14)
    tube(m, I, "start", [add((-0.07, ys, 0.10), mul(sn, -0.006)), add((-0.07, ys, 0.10), mul(sn, -0.010))], 0.017, seg=14)
    for zc in (-0.31, -0.40):
        yc = top(zc)
        tube(m, I, "alu_d", [(0.035, yc, zc), (0.035, yc + 0.004, zc)], 0.040, seg=16)
        tube(m, I, "rubber", [(0.035, yc + 0.004, zc), (0.035, yc + 0.005, zc)], 0.034, seg=16)
    # climatização na rampa junto ao painel
    cc = (0.03, (top(0.14) + top(0.22)) / 2 + 0.004, 0.18)
    quad_img(m, I, "img_climate", add(cc, mul(sn, -0.002)), (-1, 0, 0), slope, 0.17, 0.05)
    # apoio de braço estofado
    ya = top(-0.58)
    box(m, I, "leather", -0.115, ya - 0.005, -0.78, 0.115, ya + 0.035, -0.46)
    box(m, I, "stitch", -0.116, ya + 0.020, -0.78, 0.116, ya + 0.024, -0.46)

# ---------------------------------------------------------------- bancos
def seat(m, I, sx):
    x0 = sx * SEAT_X
    W = 0.25
    # --- assento
    zs = [-0.50 + 0.62 * i / 15 for i in range(16)]
    secs = []
    for z in zs:
        ys = 0.262 + 0.035 * smoothstep(-0.12, 0.10, z) - 0.06 * smoothstep(0.07, 0.12, z)
        yb = ys + 0.055 * (1 - 0.6 * smoothstep(-0.05, 0.12, z))
        base = 0.20
        pts = [(-W, base), (W, base), (W, yb - 0.02), (W - 0.02, yb), (W - 0.065, yb - 0.005), (W - 0.085, ys),
               (-W + 0.085, ys), (-W + 0.065, yb - 0.005), (-W + 0.02, yb), (-W, yb - 0.02)]
        secs.append([(x0 + dx, y, z) for dx, y in pts])
    seat_mats = [None, "leather", "leather", "leather", "cognac_d", "cognac", "cognac_d", "leather", "leather", "leather"]
    cushion = Loft(secs)
    cushion.emit(m, I, lambda i, s, n, z: seat_mats[i], cap_mat="leather")
    cushion.decal(m, I, "stitch", 5, -0.44, 0.06, 0.485, 0.515, 0.003)
    # --- encosto com abas e apoio de cabeça integrado
    tilt = math.radians(20)
    along = (0, math.cos(tilt), -math.sin(tilt)); fwd = (0, math.sin(tilt), math.cos(tilt))
    base = (x0, 0.25, -0.44); Lb = 0.90
    ts = [i / 22 for i in range(23)]
    secs = []
    for t in ts:
        w = mix(0.25, 0.27, smoothstep(0.2, 0.6, t))
        w = mix(w, 0.155, smoothstep(0.66, 0.80, t))
        w = mix(w, 0.11, smoothstep(0.93, 1.0, t))
        b = mix(0.10, 0.085, smoothstep(0.15, 0.5, t)) * (1 - smoothstep(0.58, 0.80, t)) * smoothstep(0.0, 0.08, t)
        inner = max(w - 0.085, w * 0.45)
        thick = mix(0.085, 0.06, smoothstep(0.6, 1.0, t))
        head = 0.025 * smoothstep(0.78, 0.9, t) * (1 - smoothstep(0.95, 1.0, t))     # apoio avança um pouco
        pts = [(-w, -thick), (w, -thick), (w, b - 0.02), (w - 0.02, b), (min(w - 0.06, inner + 0.02), b - 0.005), (inner, head),
               (-inner, head), (-min(w - 0.06, inner + 0.02), b - 0.005), (-w + 0.02, b), (-w, b - 0.02)]
        o = add(base, mul(along, t * Lb))
        secs.append([add(add(o, (dx, 0, 0)), mul(fwd, d)) for dx, d in pts])
    back = Loft(secs, coords=ts)
    def back_mat(i, s, n, t):
        if i == 0: return "carbon"
        if i == 5: return "cognac" if t < 0.72 else "leather"
        if i in (4, 6): return "cognac_d" if t < 0.72 else "leather"
        return "leather"
    back.emit(m, I, back_mat, cap_mat="leather", flip=True)
    back.decal(m, I, "stitch", 5, 0.06, 0.70, 0.485, 0.515, -0.003)
    back.decal(m, I, "stitch", 5, 0.80, 0.84, 0.25, 0.75, -0.003)            # filete do apoio de cabeça
    # trilhos
    box(m, I, "alu_d", x0 - 0.22, 0.19, -0.46, x0 - 0.17, 0.215, 0.06)
    box(m, I, "alu_d", x0 + 0.17, 0.19, -0.46, x0 + 0.22, 0.215, 0.06)

# ---------------------------------------------------------------- forro de porta (parte da porta)
def door_panel(m, sx, xw):
    D = "door_l" if sx > 0 else "door_r"
    zs = [round(-0.78 + 0.04 * i, 4) for i in range(30)] + [0.40]
    secs = []
    for z in zs:
        a = smoothstep(-0.76, -0.56, z) * (1 - smoothstep(0.12, 0.34, z))
        arm = 0.045 + 0.065 * a
        prof = [(0.0, 0.24), (0.035, 0.26), (0.04, 0.46), (arm - 0.008, 0.50), (arm, 0.552), (0.045, 0.575),
                (0.04, 0.80), (0.058, 0.845), (0.0, 0.862)]
        secs.append([(sx * (xw - ins), y, z) for ins, y in prof])
    loft = Loft(secs)
    mats = ["carpet", "dash", "cognac_d", "cognac", "leather", "cognac", "leather", "leather", None]
    loft.emit(m, D, lambda i, s, n, z: mats[i], cap_mat="dash", flip=(sx < 0))
    off = 0.003 if sx > 0 else -0.003
    loft.decal(m, D, "ambient", 5, -0.62, 0.26, 0.03, 0.08, off)
    loft.decal(m, D, "alcantara", 5, -0.60, 0.22, 0.30, 0.86, off)             # aplique superior
    loft.decal(m, D, "stitch", 5, -0.60, 0.22, 0.86, 0.885, off)
    loft.decal(m, D, "piano", 4, -0.02, 0.16, 0.15, 0.85, off)                 # comandos dos vidros
    xin = sx * (xw - 0.085)
    tube(m, D, "alu", [(xin, 0.60, -0.14), (sx * (xw - 0.10), 0.615, -0.08), (sx * (xw - 0.10), 0.615, 0.08), (xin, 0.60, 0.14)], 0.010, seg=8)
    cylinder(m, D, "alu", "x", (sx * (xw - 0.043), 0.36, 0.14), 0.062, 0.012, seg=20, cap_mat="alu_d", hollow=0.052)
    cylinder(m, D, "piano", "x", (sx * (xw - 0.041), 0.36, 0.14), 0.052, 0.006, seg=16)

# ---------------------------------------------------------------- volante (parte animada)
def steering_wheel(m):
    P = "steering_wheel"
    ax = steering_axis()
    up = (0.0, math.cos(SW_TILT), math.sin(SW_TILT))
    right = (-1.0, 0.0, 0.0)
    def at(u, v, w=0.0):
        return add(SW_CENTER, add(mul(right, u), add(mul(up, v), mul(ax, w))))
    R, FLAT, N = 0.178, -0.135, 56
    path, info = [], []
    for k in range(N):
        a = 2 * math.pi * k / N
        u, v = R * math.sin(a), R * math.cos(a)
        if v < FLAT: v = FLAT
        path.append(at(u, v)); info.append((u, v))
    def radius(i):
        u, v = info[i]
        return 0.0195 if (abs(u) > 0.13 and abs(v) < 0.10) else 0.0165
    def rim_mat(i):
        u, v = info[i]
        if abs(u) < 0.014 and v > 0: return "stitch"            # marca de 12 h
        if v <= FLAT + 1e-6: return "carbon"                     # base reta
        if abs(u) > 0.13 and abs(v) < 0.10: return "alcantara"  # empunhaduras
        return "leather"
    tube(m, P, "leather", path, radius, seg=10, closed=True, mat_fn=rim_mat)
    # raios: dois horizontais com comandos e um duplo inferior
    for s in (-1, 1):
        obox(m, P, "alu", at(s * 0.108, 0.004, 0.006), right, up, 0.105, 0.034, 0.010)
        obox(m, P, "piano", at(s * 0.105, 0.004, -0.001), right, up, 0.070, 0.022, 0.006)
        for du in (-0.018, 0.018):
            tube(m, P, "alu_d", [at(s * 0.105 + du, 0.004, -0.004), at(s * 0.105 + du, 0.004, -0.008)], 0.006, seg=8)
        beam(m, P, "alu", at(s * 0.018, -0.045, 0.006), at(s * 0.040, FLAT + 0.006, 0.004), 0.016, 0.010)
        obox(m, P, "alu_d", at(s * 0.150, 0.035, 0.040), right, up, 0.055, 0.085, 0.005)    # aletas de troca
    # cubo arredondado com emblema
    hub = [at(0, 0, 0.034), at(0, 0, 0.010), at(0, 0, -0.008), at(0, 0, -0.016)]
    hr = [0.050, 0.060, 0.056, 0.040]
    tube(m, P, "leather", hub, lambda i: hr[i], seg=18)
    tube(m, P, "alu", [at(0, 0, -0.016), at(0, 0, -0.019)], 0.020, seg=14)
    tube(m, P, "stitch", [at(0, 0, -0.019), at(0, 0, -0.021)], 0.010, seg=10)

def column(m, I):
    ax = steering_axis()
    up = (0.0, math.cos(SW_TILT), math.sin(SW_TILT))
    c = lambda w, u=0.0, v=0.0: add(SW_CENTER, add(mul(ax, w), add(mul((-1, 0, 0), u), mul(up, v))))
    tube(m, I, "dash", [c(0.034), c(0.30)], 0.042, seg=12)
    for s in (-1, 1):   # alavancas de seta/limpador
        tube(m, I, "piano", [c(0.07, s * 0.04), c(0.06, s * 0.15, 0.01)], 0.007, seg=6)
        tube(m, I, "alu_d", [c(0.06, s * 0.15, 0.01), c(0.058, s * 0.175, 0.012)], 0.010, seg=8)

# ---------------------------------------------------------------- conjunto
def build(m, half_width):
    I = "interior"
    hw_cab = half_width(-0.2) * 0.86
    xw = hw_cab + 0.012
    # assoalho, tapetes com debrum, caixa de pés, soleiras, laterais fixas (cobrem o avesso da carroceria)
    box(m, I, "carpet", -0.80, 0.17, -0.96, 0.80, 0.19, 0.62)
    box(m, I, "carpet", -0.80, 0.19, 0.57, 0.80, 0.56, 0.62)
    for sx in (1, -1):
        x = sx * SEAT_X
        box(m, I, "mat", x - 0.22, 0.19, 0.00, x + 0.22, 0.196, 0.50)
        for (a, b, c, d) in ((x - 0.22, 0.00, x + 0.22, 0.012), (x - 0.22, 0.488, x + 0.22, 0.50),
                             (x - 0.22, 0.00, x - 0.208, 0.50), (x + 0.208, 0.00, x + 0.22, 0.50)):
            box(m, I, "stitch", a, 0.196, b, c, 0.198, d)
        box(m, I, "alu", sx * 0.73, 0.19, -0.76, sx * (xw - 0.005), 0.232, 0.38)              # soleira
        box(m, I, "carpet", sx * (xw - 0.045), 0.19, 0.40, sx * (xw + 0.03), 0.86, 0.62)      # lateral da caixa de pés
        box(m, I, "leather", sx * (xw - 0.045), 0.19, -0.96, sx * (xw + 0.03), 0.88, -0.78)   # lateral traseira
    # anteparo traseiro com travessa e alto-falante
    box(m, I, "alcantara", -0.82, 0.19, -1.00, 0.82, 1.00, -0.94)
    box(m, I, "carbon", -0.50, 0.42, -0.944, 0.50, 0.74, -0.936)
    tube(m, I, "alu", [(-0.74, 0.84, -0.90), (0.74, 0.84, -0.90)], 0.022, seg=10)
    cylinder(m, I, "alu_d", "z", (0.0, 0.58, -0.934), 0.11, 0.008, seg=24, cap_mat="piano", hollow=0.095)
    # pedais: descanso (esq.), freio, acelerador (dir. do motorista = -X)
    pu = norm((0, 0.94, 0.34))
    for (dx, w, h, y, mat) in ((0.17, 0.07, 0.20, 0.31, "alu_d"), (-0.01, 0.09, 0.07, 0.33, "alu"), (-0.14, 0.05, 0.15, 0.30, "alu")):
        c = (SEAT_X + dx, y, 0.50)
        obox(m, I, mat, c, (-1, 0, 0), pu, w, h, 0.012)
        for k in range(3):
            obox(m, I, "rubber", add(c, mul(pu, (k - 1) * h * 0.28)), (-1, 0, 0), pu, w * 0.8, 0.008, 0.016)
    # para-sóis e retrovisor interno
    for sx in (1, -1):
        obox(m, I, "alcantara", (sx * 0.36, 1.198, -0.03), (1, 0, 0), norm((0, 0.18, 1)), 0.32, 0.13, 0.012)
    tube(m, I, "piano", [(0, 1.205, -0.09), (0, 1.15, -0.13)], 0.008, seg=6)
    mn = norm((0, -0.10, -1.0)); mr = (-1, 0, 0); mu = cross(mn, mr)
    obox(m, I, "piano", (0, 1.135, -0.135), mr, mu, 0.23, 0.062, 0.022)
    obox(m, I, "mirror", add((0, 1.135, -0.135), mul(mn, 0.012)), mr, mu, 0.21, 0.048, 0.002)

    dashboard(m, I)
    console(m, I)
    column(m, I)
    for sx in (1, -1):
        seat(m, I, sx)
        door_panel(m, sx, xw)
    steering_wheel(m)

def parts_json():
    r, u, n = cluster_frame()
    into = mul(n, -1.0)
    def pivot(gu): return list(add(CL_CENTER, mul(r, gu)))
    return {
        "steering_wheel": {"pivot": list(SW_CENTER), "animation": "steering_wheel", "axis": list(steering_axis()), "factor": 12.0},
        "needle_rpm": {"pivot": pivot(-GAUGE_U), "animation": "rpm", "axis": list(into), "factor": SWEEP},
        "needle_speed": {"pivot": pivot(GAUGE_U), "animation": "speed", "axis": list(into), "factor": SWEEP},
    }

def materials_json():
    return {
        "ambient": {"role": "emissive", "light": "engine", "off": "#1A2A33"},
        "needle": {"role": "emissive", "light": "engine", "off": "#4A1A12"},
        "start": {"role": "emissive", "light": "always"},
        "img_cluster": {"role": "emissive", "light": "engine", "off": "#0C0C0C"},
        "img_nav": {"role": "emissive", "light": "engine", "off": "#0C0C0C"},
        "img_climate": {"role": "emissive", "light": "engine", "off": "#0C0C0C"},
    }
