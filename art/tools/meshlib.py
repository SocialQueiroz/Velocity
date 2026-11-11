"""
meshlib — geometria procedural para veículos do VELOCITY.

Convenções: metros, Y para cima, frente em +Z, esquerda em +X, origem no solo no centro.
Saída: OBJ (o/usemtl/f com vt/vn) + atlas PNG de cores sólidas por material.
"""
import math, struct, zlib

# ---------------------------------------------------------------- vetores
def sub(a, b): return (a[0]-b[0], a[1]-b[1], a[2]-b[2])
def add(a, b): return (a[0]+b[0], a[1]+b[1], a[2]+b[2])
def mul(a, s): return (a[0]*s, a[1]*s, a[2]*s)
def lerp3(a, b, t): return (a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t, a[2]+(b[2]-a[2])*t)
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def norm(a):
    l = math.sqrt(a[0]*a[0]+a[1]*a[1]+a[2]*a[2]) or 1.0
    return (a[0]/l, a[1]/l, a[2]/l)
def smoothstep(e0, e1, x):
    t = max(0.0, min(1.0, (x-e0)/(e1-e0))); return t*t*(3-2*t)
def gauss(x, w): return math.exp(-(x/w)**2)
def mix(a, b, t): return a + (b-a)*t

# ---------------------------------------------------------------- atlas
class Atlas:
    """Cada material ocupa uma célula 16x16 de cor sólida num atlas 128x128."""
    SIZE = 128
    def __init__(self, colors):
        self.colors = colors            # nome -> (r,g,b,a)
        self.cells = {}
        for i, name in enumerate(colors):
            self.cells[name] = (i % 8, i // 8)
    def uv(self, mat):
        cx, cy = self.cells[mat]
        return ((cx*16+8)/self.SIZE, 1-(cy*16+8)/self.SIZE)
    def write_png(self, path):
        S = self.SIZE
        px = [[(0, 0, 0, 0)]*S for _ in range(S)]
        for name, (cx, cy) in self.cells.items():
            c = self.colors[name]
            for y in range(cy*16, cy*16+16):
                for x in range(cx*16, cx*16+16):
                    px[y][x] = c
        raw = b"".join(b"\0"+bytes(v for p in row for v in p) for row in px)
        ch = lambda t, d: struct.pack(">I", len(d))+t+d+struct.pack(">I", zlib.crc32(t+d) & 0xffffffff)
        with open(path, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n"+ch(b"IHDR", struct.pack(">IIBBBBB", S, S, 8, 6, 0, 0, 0))
                    + ch(b"IDAT", zlib.compress(raw, 9))+ch(b"IEND", b""))

# ---------------------------------------------------------------- malha
class Mesh:
    def __init__(self, atlas):
        self.atlas = atlas
        self.parts = {}
    def face(self, part, mat, pts, normals=None):
        if mat not in self.atlas.cells: raise KeyError("material desconhecido: " + mat)
        self.parts.setdefault(part, {}).setdefault(mat, []).append((list(pts), normals))
    def triangles(self):
        return sum(len(p)-2 for mats in self.parts.values() for faces in mats.values() for p, _ in faces)
    def write_obj(self, path, header):
        v, vn, body = [], [], []
        vt = {m: i+1 for i, m in enumerate(self.atlas.cells)}
        for part, mats in self.parts.items():
            body.append(f"o {part}")
            for mat, faces in mats.items():
                body.append(f"usemtl {mat}")
                for pts, ns in faces:
                    idx = []
                    for i, p in enumerate(pts):
                        v.append(p)
                        if ns is not None:
                            vn.append(ns[i]); idx.append(f"{len(v)}/{vt[mat]}/{len(vn)}")
                        else:
                            idx.append(f"{len(v)}/{vt[mat]}")
                    body.append("f "+" ".join(idx))
        out = [f"# {header}"]
        out += [f"v {x:.4f} {y:.4f} {z:.4f}" for x, y, z in v]
        out += ["vt %.5f %.5f" % self.atlas.uv(m) for m in self.atlas.cells]
        out += [f"vn {x:.4f} {y:.4f} {z:.4f}" for x, y, z in vn]
        out += body
        with open(path, "w") as f: f.write("\n".join(out)+"\n")

# ---------------------------------------------------------------- primitivas
def box(m, part, mat, x0, y0, z0, x1, y1, z1):
    x0, x1 = min(x0, x1), max(x0, x1); y0, y1 = min(y0, y1), max(y0, y1); z0, z1 = min(z0, z1), max(z0, z1)
    for f in ([(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)], [(x1,y0,z0),(x0,y0,z0),(x0,y1,z0),(x1,y1,z0)],
              [(x1,y0,z1),(x1,y0,z0),(x1,y1,z0),(x1,y1,z1)], [(x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)],
              [(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)], [(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)]):
        m.face(part, mat, f)

def cylinder(m, part, mat, axis, center, radius, length, seg=16, cap_mat=None, hollow=None):
    """Cilindro ao longo de 'x' ou 'z'. hollow = raio interno (tubo, sem tampas)."""
    cx, cy, cz = center
    def P(t, a, r):
        if axis == "x": return (cx+t, cy+r*math.cos(a), cz+r*math.sin(a))
        return (cx+r*math.cos(a), cy+r*math.sin(a), cz+t)
    h = length/2
    for k in range(seg):
        a, b = 2*math.pi*k/seg, 2*math.pi*(k+1)/seg
        m.face(part, mat, [P(-h, a, radius), P(-h, b, radius), P(h, b, radius), P(h, a, radius)])
        if hollow:
            m.face(part, cap_mat or mat, [P(h, a, hollow), P(h, b, hollow), P(-h, b, hollow), P(-h, a, hollow)])
            m.face(part, cap_mat or mat, [P(h, a, radius), P(h, b, radius), P(h, b, hollow), P(h, a, hollow)])
        else:
            m.face(part, cap_mat or mat, [P(h, 0, 0), P(h, a, radius), P(h, b, radius)])
            m.face(part, cap_mat or mat, [P(-h, 0, 0), P(-h, b, radius), P(-h, a, radius)])

# ---------------------------------------------------------------- loft
class Loft:
    """Superfície gerada por seções transversais (laços com o mesmo nº de pontos), em z crescente.
    Normais suaves ao longo de z; vincos nítidos entre segmentos do laço."""
    def __init__(self, sections):
        self.S = sections
        self.n = len(sections[0])
        self.fn = []
        for s in range(len(sections)-1):
            row = []
            for i in range(self.n):
                j = (i+1) % self.n
                a, b, c, d = sections[s][i], sections[s][j], sections[s+1][j], sections[s+1][i]
                row.append(norm(cross(sub(c, a), sub(d, b))))
            self.fn.append(row)
    def z(self, s): return self.S[s][0][2]
    def vnormal(self, s, i):
        acc = (0, 0, 0)
        for k in (s-1, s):
            if 0 <= k < len(self.fn): acc = add(acc, self.fn[k][i])
        return norm(acc)
    def emit(self, m, part, mat_fn, cap_mat=None):
        for s in range(len(self.S)-1):
            for i in range(self.n):
                mat = mat_fn(i, s, self.fn[s][i], (self.z(s)+self.z(s+1))/2)
                if mat is None: continue
                j = (i+1) % self.n
                p = [self.S[s][i], self.S[s][j], self.S[s+1][j], self.S[s+1][i]]
                nn = [self.vnormal(s, i), self.vnormal(s, i), self.vnormal(s+1, i), self.vnormal(s+1, i)]
                m.face(part, mat, [p[0], p[3], p[2], p[1]], [nn[0], nn[3], nn[2], nn[1]])
        if cap_mat:
            for s, flip in ((0, True), (len(self.S)-1, False)):
                ring = self.S[s]
                c = tuple(sum(p[k] for p in ring)/self.n for k in range(3))
                for i in range(self.n):
                    j = (i+1) % self.n
                    tri = [c, ring[j], ring[i]] if flip else [c, ring[i], ring[j]]
                    m.face(part, cap_mat, tri)
    def decal(self, m, part, mat, seg, z0, z1, t0=0.0, t1=1.0, offset=0.004):
        """Aplica uma faixa sobre o segmento 'seg' do laço entre z0..z1 (frações t0..t1 do segmento),
        deslocada 'offset' para fora. Usada para faróis, frisos, entradas de ar, maçanetas."""
        j = (seg+1) % self.n
        for s in range(len(self.S)-1):
            za, zb = self.z(s), self.z(s+1)
            if zb <= z0 or za >= z1: continue
            u0 = max(0.0, (z0-za)/(zb-za)); u1 = min(1.0, (z1-za)/(zb-za))   # recorte exato em z
            def pt(u, t):
                p = lerp3(lerp3(self.S[s][seg], self.S[s][j], t), lerp3(self.S[s+1][seg], self.S[s+1][j], t), u)
                nrm = norm(add(mul(self.vnormal(s, seg), 1-u), mul(self.vnormal(s+1, seg), u)))
                return add(p, mul(nrm, offset))
            m.face(part, mat, [pt(u0, t0), pt(u1, t0), pt(u1, t1), pt(u0, t1)])
