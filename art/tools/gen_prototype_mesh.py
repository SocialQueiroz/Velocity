#!/usr/bin/env python3
"""
Gera a malha PROTÓTIPO v2 do Aurion VX (Velo 0.02) — geometria real (loft de seções),
NÃO é o modelo final (Velo 0.03+). Saída:
  src/main/resources/assets/velocity/vehicle_models/aurion_vx_prototype.{obj,json}
  src/main/resources/assets/velocity/textures/vehicle/aurion_vx_prototype.png

Convenções: metros, Y para cima, frente em +Z, origem no solo no centro do veículo.
Uso: python3 art/tools/gen_prototype_mesh.py   (a partir da raiz do repositório)
"""
import json, math, os, struct, zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/velocity")

# ---------------- ficha (docs/ARCHITECTURE.md §E)
LENGTH, WIDTH = 4.40, 1.92
WHEEL_R, TIRE_W, RIM_R = 0.34, 0.245, 0.235
FRONT_AXLE, REAR_AXLE = 1.28, -1.34
TRACK_HALF = 0.80
ARCH_R = 0.40

# ---------------- atlas 64x64: cada material ocupa uma célula 16x16 de cor sólida
ATLAS = 64
CELLS = {  # material: (célula x, célula y, RGBA)
    "paint":     (0, 0, (235, 235, 235, 255)),   # tingida pela cor de pintura
    "glass":     (1, 0, (30, 42, 56, 170)),
    "trim":      (2, 0, (22, 22, 24, 255)),
    "tire":      (3, 0, (26, 26, 28, 255)),
    "rim":       (0, 1, (170, 174, 180, 255)),
    "underbody": (1, 1, (48, 50, 54, 255)),
    "headlight": (2, 1, (255, 248, 225, 255)),
    "taillight": (3, 1, (220, 24, 24, 255)),
    "chrome":    (0, 2, (205, 208, 214, 255)),
}

def uv_of(mat):
    cx, cy, _ = CELLS[mat]
    # centro da célula; OBJ usa V para cima
    return ((cx * 16 + 8) / ATLAS, 1 - (cy * 16 + 8) / ATLAS)

class Obj:
    def __init__(self):
        self.parts = {}  # part -> mat -> list of faces [(pos, normal|None)]
    def face(self, part, mat, pts, normals=None):
        self.parts.setdefault(part, {}).setdefault(mat, []).append((pts, normals))
    def write(self, path):
        lines = ["# Aurion VX — malha protótipo v2 (gerada por art/tools/gen_prototype_mesh.py)"]
        v, vt, vn = [], [], []
        vt_index = {}
        for m in CELLS:
            vt_index[m] = len(vt) + 1; vt.append(uv_of(m))
        body = []
        tris = 0
        for part, mats in self.parts.items():
            body.append(f"o {part}")
            for mat, faces in mats.items():
                body.append(f"usemtl {mat}")
                for pts, ns in faces:
                    idx = []
                    for i, p in enumerate(pts):
                        v.append(p); vi = len(v)
                        if ns is not None:
                            vn.append(ns[i]); idx.append(f"{vi}/{vt_index[mat]}/{len(vn)}")
                        else:
                            idx.append(f"{vi}/{vt_index[mat]}")
                    body.append("f " + " ".join(idx))
                    tris += len(pts) - 2
        lines += [f"v {x:.4f} {y:.4f} {z:.4f}" for x, y, z in v]
        lines += [f"vt {u:.5f} {t:.5f}" for u, t in vt]
        lines += [f"vn {x:.4f} {y:.4f} {z:.4f}" for x, y, z in vn]
        lines += body
        with open(path, "w") as f:
            f.write("\n".join(lines) + "\n")
        return tris

def sub(a, b): return (a[0]-b[0], a[1]-b[1], a[2]-b[2])
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def norm(a):
    l = math.sqrt(sum(c*c for c in a)) or 1.0
    return (a[0]/l, a[1]/l, a[2]/l)
def add(a, b): return (a[0]+b[0], a[1]+b[1], a[2]+b[2])

def loft(obj, part, sections, mat_of_segment, cap_mat):
    """sections: lista (ordenada em z crescente) de laços [(x,y,z)...] com o mesmo nº de pontos,
    em sentido anti-horário visto de +Z. Normais suaves ao longo de z, vincos entre segmentos."""
    n = len(sections[0]); S = len(sections)
    # normal de cada face (s, i)
    fn = [[None]*n for _ in range(S-1)]
    for s in range(S-1):
        for i in range(n):
            j = (i+1) % n
            a, b, c = sections[s][i], sections[s][j], sections[s+1][j]
            d = sections[s+1][i]
            fn[s][i] = norm(cross(sub(c, a), sub(d, b)))
    for s in range(S-1):
        for i in range(n):
            j = (i+1) % n
            mat = mat_of_segment(i, s, fn[s][i])
            if mat is None: continue
            def vnorm(ss):
                acc = (0, 0, 0)
                for k in (ss-1, ss):
                    if 0 <= k < S-1: acc = add(acc, fn[k][i])
                return norm(acc)
            p = [sections[s][i], sections[s+1][i], sections[s+1][j], sections[s][j]]
            # ordem anti-horária vista de fora
            obj.face(part, mat, [p[0], p[3], p[2], p[1]], [vnorm(s), vnorm(s), vnorm(s+1), vnorm(s+1)])
    # tampas
    for s, flip in ((0, True), (S-1, False)):
        ring = sections[s]
        c = tuple(sum(p[k] for p in ring)/n for k in range(3))
        for i in range(n):
            j = (i+1) % n
            tri = [c, ring[i], ring[j]]
            if flip: tri = [c, ring[j], ring[i]]
            obj.face(part, cap_mat, tri)

def box(obj, part, mat, x0, y0, z0, x1, y1, z1):
    P = lambda x, y, z: (x, y, z)
    faces = [
        [P(x0,y0,z1),P(x1,y0,z1),P(x1,y1,z1),P(x0,y1,z1)],  # +z
        [P(x1,y0,z0),P(x0,y0,z0),P(x0,y1,z0),P(x1,y1,z0)],  # -z
        [P(x1,y0,z1),P(x1,y0,z0),P(x1,y1,z0),P(x1,y1,z1)],  # +x
        [P(x0,y0,z0),P(x0,y0,z1),P(x0,y1,z1),P(x0,y1,z0)],  # -x
        [P(x0,y1,z1),P(x1,y1,z1),P(x1,y1,z0),P(x0,y1,z0)],  # +y
        [P(x0,y0,z0),P(x1,y0,z0),P(x1,y0,z1),P(x0,y0,z1)],  # -y
    ]
    for f in faces: obj.face(part, mat, f)

def smoothstep(e0, e1, x):
    t = max(0.0, min(1.0, (x-e0)/(e1-e0))); return t*t*(3-2*t)

# ---------------------------------------------------------------- carroceria
def body_sections():
    zs = []
    z = -LENGTH/2
    while z < LENGTH/2 - 1e-6:
        near = min(abs(z-FRONT_AXLE), abs(z-REAR_AXLE)) < ARCH_R + 0.05
        zs.append(z); z += 0.035 if near else 0.09
    zs.append(LENGTH/2)
    secs = []
    for z in zs:
        u = (z + LENGTH/2) / LENGTH                    # 0 traseira … 1 frente
        # planta arredondada nas pontas
        end = min(smoothstep(0.0, 0.10, u), smoothstep(1.0, 0.86, u))
        hw = WIDTH/2 * (0.80 + 0.20*end)
        # paralamas traseiros mais largos ("ombros")
        hw += 0.03 * math.exp(-((z-REAR_AXLE)/0.5)**2)
        # altura do topo: capô baixo na frente, tampa traseira mais alta
        top = 0.62 + 0.26*smoothstep(1.0, 0.70, u) + 0.08*smoothstep(0.0, 0.25, u)*(1-smoothstep(0.25, 0.6, u)) + 0.04
        nose = smoothstep(1.0, 0.95, u); tail = smoothstep(0.0, 0.04, u)
        top = 0.40 + (top-0.40) * min(nose, 1.0) * (0.85 + 0.15*tail)
        # borda inferior: sobe nas caixas de roda
        bot = 0.17
        for zc in (FRONT_AXLE, REAR_AXLE):
            d = abs(z-zc)
            if d < ARCH_R:
                bot = max(bot, WHEEL_R + math.sqrt(ARCH_R*ARCH_R - d*d))
        bot = min(bot, top - 0.06)
        belt = top - 0.05
        shoulder = max(bot + 0.02, belt - 0.07)
        lo = min(shoulder - 0.01, bot + 0.10)
        ring = [
            (-hw*0.97, bot, z), (hw*0.97, bot, z) ,
        ]
        # laço anti-horário visto de +Z (x para a esquerda = +X): começa embaixo à direita (-X)
        ring = [
            (-hw*0.94, bot, z),         # 0 baixo dir
            ( hw*0.94, bot, z),         # 1 baixo esq
            ( hw,      lo, z),          # 2
            ( hw,      shoulder, z),    # 3
            ( hw*0.97, belt, z),        # 4 ombro
            ( hw*0.82, top, z),         # 5 topo esq
            (-hw*0.82, top, z),         # 6 topo dir
            (-hw*0.97, belt, z),        # 7
            (-hw,      shoulder, z),    # 8
            (-hw,      lo, z),          # 9
        ]
        secs.append(ring)
    return secs

def body_material(i, s, n):
    if i == 0: return "underbody"
    return "paint"

def cabin_sections():
    secs = []
    z0, z1 = -1.05, 0.62   # traseira da cabine … base do para-brisa
    steps = 30
    for k in range(steps+1):
        z = z0 + (z1-z0)*k/steps
        u = (z - z0)/(z1 - z0)
        base = 0.86 if z > 0 else 0.86 + 0.02*(-z)
        roof = 1.24
        # para-brisa: sobe de 0.62 até ~0.0 ; vidro traseiro: cai de -0.45 até -1.05
        h = smoothstep(1.0, 0.62, u) * smoothstep(0.0, 0.40, u)
        top = base + 0.02 + (roof - base - 0.02) * h
        wb = 0.80 - 0.04*(1-h); wt = 0.58 - 0.10*(1-h)
        win = base + (top-base)*0.28
        secs.append([(-wb, base, z), (wb, base, z), (wb*0.98, win, z), (wt, top, z), (-wt, top, z), (-wb*0.98, win, z)])
    return secs

def cabin_material(i, s, n):
    if i == 0: return None                 # base: escondida dentro da carroceria
    if i == 3:                              # teto: vidro onde é inclinado (para-brisa / traseiro)
        return "glass" if abs(n[2]) > 0.45 else "paint"
    if i in (1, 5): return "trim"           # soleira das janelas
    return "glass"                          # laterais

# ---------------------------------------------------------------- rodas
def wheel(obj, part, cx, cy, cz, outward, seg=28):
    """outward = +1 (lado esquerdo, +X) ou -1. Pneu com banda, flancos e aro rebaixado com 5 raios."""
    xi = cx - outward*TIRE_W/2; xo = cx + outward*TIRE_W/2
    ang = [2*math.pi*k/seg for k in range(seg)]
    P = lambda x, r, a: (x, cy + r*math.cos(a), cz + r*math.sin(a))
    shoulder = WHEEL_R - 0.025
    xs_o = xo - outward*0.02; xs_i = xi + outward*0.02
    for k in range(seg):
        a, b = ang[k], ang[(k+1) % seg]
        # banda de rodagem
        quad = [P(xs_i, WHEEL_R, a), P(xs_o, WHEEL_R, a), P(xs_o, WHEEL_R, b), P(xs_i, WHEEL_R, b)]
        nrm = [(0, math.cos(a), math.sin(a)), (0, math.cos(a), math.sin(a)), (0, math.cos(b), math.sin(b)), (0, math.cos(b), math.sin(b))]
        if outward < 0: quad, nrm = quad[::-1], nrm[::-1]
        obj.face(part, "tire", quad, nrm)
        # ombros e flancos (externo e interno)
        for xe, xs, sgn in ((xo, xs_o, outward), (xi, xs_i, -outward)):
            q = [P(xs, WHEEL_R, a), P(xe, shoulder, a), P(xe, shoulder, b), P(xs, WHEEL_R, b)]
            if sgn < 0: q = q[::-1]
            obj.face(part, "tire", q)
            q = [P(xe, shoulder, a), P(xe, RIM_R, a), P(xe, RIM_R, b), P(xe, shoulder, b)]
            if sgn < 0: q = q[::-1]
            obj.face(part, "tire", q)
        # aro: barril interno rebaixado
        xr = xo - outward*0.05
        q = [P(xo, RIM_R, a), P(xr, RIM_R*0.96, a), P(xr, RIM_R*0.96, b), P(xo, RIM_R, b)]
        if outward > 0: q = q[::-1]
        obj.face(part, "rim", q)
        # fundo do aro (disco escuro atrás dos raios)
        q = [(xr, cy, cz), P(xr, RIM_R*0.96, b), P(xr, RIM_R*0.96, a)]
        if outward < 0: q = q[::-1]
        obj.face(part, "trim", q)
    # 5 raios em "Y" simplificados + cubo central
    xr = xo - outward*0.05
    for k in range(5):
        a = 2*math.pi*k/5
        for da in (-0.13, 0.13):
            aa = a + da
            r0, r1 = 0.06, RIM_R*0.94
            w = 0.022
            c0 = P(0, r0, a); c1 = P(0, r1, aa)
            # raio como prisma fino entre xr e xo-outward*0.01
            dy, dz = c1[1]-c0[1], c1[2]-c0[2]
            L = math.hypot(dy, dz); py, pz = -dz/L*w, dy/L*w
            x_out = xo - outward*0.012
            pts = lambda x: [(x, c0[1]+py, c0[2]+pz), (x, c1[1]+py, c1[2]+pz), (x, c1[1]-py, c1[2]-pz), (x, c0[1]-py, c0[2]-pz)]
            top = pts(x_out); bot = pts(xr)
            f = top if outward > 0 else top[::-1]
            obj.face(part, "rim", f)
            for m in range(4):
                n2 = (m+1) % 4
                q = [bot[m], bot[n2], top[n2], top[m]]
                if outward < 0: q = q[::-1]
                obj.face(part, "rim", q)
    hub = 0.055
    box(obj, part, "chrome", min(xr, xo)-0.0, cy-hub, cz-hub, max(xr, xo-outward*0.008), cy+hub, cz+hub)

# ---------------------------------------------------------------- montagem
def main():
    o = Obj()
    loft(o, "body", body_sections(), body_material, "paint")
    loft(o, "cabin", cabin_sections(), cabin_material, "glass")
    # banheira interna (visível pelas caixas de roda), assoalho
    box(o, "underbody", "underbody", -0.56, 0.15, -2.0, 0.56, 0.62, 2.0)
    # faróis (frente, +Z), lanterna contínua (traseira)
    for sx in (1, -1):
        x0, x1 = sorted((sx*0.42, sx*0.82))
        box(o, "headlights", "headlight", x0, 0.62, 2.10, x1, 0.70, 2.205)
        box(o, "mirrors", "paint", *sorted((sx*0.80, sx*1.02))[:1], 0.98, 0.42, *sorted((sx*0.80, sx*1.02))[1:], 1.06, 0.55)
        box(o, "exhaust", "chrome", *(sorted((sx*0.30, sx*0.46))[:1]), 0.20, -2.24, *(sorted((sx*0.30, sx*0.46))[1:]), 0.27, -2.14)
    box(o, "taillights", "taillight", -0.84, 0.80, -2.215, 0.84, 0.86, -2.17)
    box(o, "grille", "trim", -0.55, 0.26, 2.14, 0.55, 0.42, 2.215)
    box(o, "diffuser", "trim", -0.62, 0.17, -2.22, 0.62, 0.30, -2.05)
    # rodas: geometria em posição absoluta; pivôs no JSON
    wheels = {"wheel_fl": (TRACK_HALF, FRONT_AXLE, 1), "wheel_fr": (-TRACK_HALF, FRONT_AXLE, -1),
              "wheel_rl": (TRACK_HALF, REAR_AXLE, 1), "wheel_rr": (-TRACK_HALF, REAR_AXLE, -1)}
    for name, (x, z, side) in wheels.items():
        wheel(o, name, x, WHEEL_R, z, side)

    os.makedirs(os.path.join(ASSETS, "vehicle_models"), exist_ok=True)
    tris = o.write(os.path.join(ASSETS, "vehicle_models/aurion_vx_prototype.obj"))

    parts = {
        "steer_fl": {"parent": "root", "pivot": [TRACK_HALF, WHEEL_R, FRONT_AXLE], "animation": "steer"},
        "steer_fr": {"parent": "root", "pivot": [-TRACK_HALF, WHEEL_R, FRONT_AXLE], "animation": "steer"},
        "wheel_fl": {"parent": "steer_fl", "pivot": [TRACK_HALF, WHEEL_R, FRONT_AXLE], "animation": "wheel_spin"},
        "wheel_fr": {"parent": "steer_fr", "pivot": [-TRACK_HALF, WHEEL_R, FRONT_AXLE], "animation": "wheel_spin"},
        "wheel_rl": {"parent": "root", "pivot": [TRACK_HALF, WHEEL_R, REAR_AXLE], "animation": "wheel_spin"},
        "wheel_rr": {"parent": "root", "pivot": [-TRACK_HALF, WHEEL_R, REAR_AXLE], "animation": "wheel_spin"},
    }
    desc = {
        "_comment": "PROTÓTIPO v2 (Velo 0.02). Gerado por art/tools/gen_prototype_mesh.py — não editar à mão.",
        "mesh": "velocity:vehicle_models/aurion_vx_prototype.obj",
        "texture": "velocity:textures/vehicle/aurion_vx_prototype.png",
        "default_paint": "#B3181F",
        "parts": parts,
        "materials": {
            "paint": {"role": "paint"},
            "glass": {"role": "translucent"},
            "headlight": {"role": "emissive", "light": "headlights"},
            "taillight": {"role": "emissive", "light": "tail"},
        },
    }
    with open(os.path.join(ASSETS, "vehicle_models/aurion_vx_prototype.json"), "w") as f:
        json.dump(desc, f, indent=2, ensure_ascii=False); f.write("\n")

    # atlas
    px = [[(0, 0, 0, 0)]*ATLAS for _ in range(ATLAS)]
    for _, (cx, cy, rgba) in CELLS.items():
        for y in range(cy*16, cy*16+16):
            for x in range(cx*16, cx*16+16):
                px[y][x] = rgba
    raw = b"".join(b"\0" + bytes(c for p in row for c in p) for row in px)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    os.makedirs(os.path.join(ASSETS, "textures/vehicle"), exist_ok=True)
    with open(os.path.join(ASSETS, "textures/vehicle/aurion_vx_prototype.png"), "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", ATLAS, ATLAS, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    print(f"OK: {tris} triângulos")

if __name__ == "__main__":
    main()
