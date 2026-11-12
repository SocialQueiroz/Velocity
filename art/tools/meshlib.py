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
    """Atlas 256x256: materiais de cor sólida em células 16x16 (linhas de cima) e imagens
    (telas, mostradores) empacotadas em prateleiras na parte de baixo."""
    SIZE = 256
    IMG_Y0 = 112
    def __init__(self, colors):
        self.colors = dict(colors)       # nome -> (r,g,b,a)
        self.cells = {}
        for i, name in enumerate(colors):
            self.cells[name] = (i % 16, i // 16)
        if (len(colors) + 15) // 16 * 16 > self.IMG_Y0: raise ValueError("materiais demais para o atlas")
        self.images = {}                 # nome -> (x, y, w, h, PIL.Image)
        self._shelf = [0, self.IMG_Y0, 0]   # x, y, altura da prateleira
    def add_image(self, name, img):
        """Registra uma imagem (PIL, RGBA) como material mapeável por UV (0..1 dentro da imagem)."""
        w, h = img.size
        x, y, sh = self._shelf
        if x + w + 2 > self.SIZE: x, y, sh = 0, y + sh + 2, 0
        if y + h + 2 > self.SIZE: raise ValueError("atlas cheio: " + name)
        self.images[name] = (x + 1, y + 1, w, h, img.convert("RGBA"))
        self._shelf = [x + w + 2, y, max(sh, h)]
        self.cells[name] = None
    def uv(self, mat, local=None):
        if mat in self.images:
            x, y, w, h, _ = self.images[mat]
            u, v = local if local is not None else (0.5, 0.5)
            # meio texel para dentro evita vazamento do vizinho; OBJ usa V para cima
            px = x + 0.5 + u * (w - 1); py = y + 0.5 + (1 - v) * (h - 1)
            return (px / self.SIZE, 1 - py / self.SIZE)
        cx, cy = self.cells[mat]
        return ((cx*16+8)/self.SIZE, 1-(cy*16+8)/self.SIZE)
    def write_png(self, path):
        from PIL import Image
        S = self.SIZE
        out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        for name, cell in self.cells.items():
            if cell is None: continue
            cx, cy = cell
            out.paste(Image.new("RGBA", (16, 16), tuple(self.colors[name])), (cx*16, cy*16))
        for name, (x, y, w, h, img) in self.images.items():
            # borda de 1 px repetida (evita costura com filtragem/mip)
            out.paste(img.resize((w + 2, h + 2)), (x - 1, y - 1))
            out.paste(img, (x, y))
        out.save(path, optimize=True)

# ---------------------------------------------------------------- malha
class Mesh:
    def __init__(self, atlas):
        self.atlas = atlas
        self.parts = {}
    def face(self, part, mat, pts, normals=None, uvs=None):
        """uvs: lista de (u,v) em 0..1 dentro da imagem do material (só para materiais de imagem)."""
        if mat not in self.atlas.cells: raise KeyError("material desconhecido: " + mat)
        if not isinstance(part, str): raise TypeError("nome de parte inválido: %r" % (part,))
        self.parts.setdefault(part, {}).setdefault(mat, []).append((list(pts), normals, uvs))
    def triangles(self):
        return sum(len(f[0])-2 for mats in self.parts.values() for faces in mats.values() for f in faces)
    def write_obj(self, path, header):
        v, vn, vt, body = [], [], [], []
        vt_solid = {}
        def solid(mat):
            if mat not in vt_solid:
                vt.append(self.atlas.uv(mat)); vt_solid[mat] = len(vt)
            return vt_solid[mat]
        for part, mats in self.parts.items():
            body.append(f"o {part}")
            for mat, faces in mats.items():
                body.append(f"usemtl {mat}")
                for pts, ns, uvs in faces:
                    idx = []
                    for i, p in enumerate(pts):
                        v.append(p)
                        if uvs is not None:
                            vt.append(self.atlas.uv(mat, uvs[i])); ti = len(vt)
                        else:
                            ti = solid(mat)
                        if ns is not None:
                            vn.append(ns[i]); idx.append(f"{len(v)}/{ti}/{len(vn)}")
                        else:
                            idx.append(f"{len(v)}/{ti}")
                    body.append("f "+" ".join(idx))
        out = [f"# {header}"]
        out += [f"v {x:.4f} {y:.4f} {z:.4f}" for x, y, z in v]
        out += ["vt %.5f %.5f" % t for t in vt]
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
    def __init__(self, sections, axis=2, coords=None):
        """axis: eixo ao longo do qual as seções avançam (2 = z, 0 = x); coords: parâmetro escalar
        por seção (substitui o eixo) — usado por decal() e passado ao mat_fn."""
        self.S = sections
        self.coord = list(coords) if coords is not None else [sec[0][axis] for sec in sections]
        self.n = len(sections[0])
        self.fn = []
        for s in range(len(sections)-1):
            row = []
            for i in range(self.n):
                j = (i+1) % self.n
                a, b, c, d = sections[s][i], sections[s][j], sections[s+1][j], sections[s+1][i]
                row.append(norm(cross(sub(c, a), sub(d, b))))
            self.fn.append(row)
    def z(self, s): return self.coord[s]
    def vnormal(self, s, i):
        acc = (0, 0, 0)
        for k in (s-1, s):
            if 0 <= k < len(self.fn): acc = add(acc, self.fn[k][i])
        return norm(acc)
    def emit(self, m, part, mat_fn, cap_mat=None, flip=False):
        """flip=True inverte a orientação (normais para o outro lado) — útil para lofts espelhados."""
        sgn = -1.0 if flip else 1.0
        for s in range(len(self.S)-1):
            for i in range(self.n):
                zm = (self.z(s)+self.z(s+1))/2
                mat = mat_fn(i, s, self.fn[s][i], zm)
                if mat is None: continue
                pname = part(i, zm) if callable(part) else part
                j = (i+1) % self.n
                p = [self.S[s][i], self.S[s][j], self.S[s+1][j], self.S[s+1][i]]
                nn = [self.vnormal(s, i), self.vnormal(s, i), self.vnormal(s+1, i), self.vnormal(s+1, i)]
                nn = [mul(q, sgn) for q in nn]
                quad, qn = [p[0], p[3], p[2], p[1]], [nn[0], nn[3], nn[2], nn[1]]
                if flip: quad, qn = quad[::-1], qn[::-1]
                m.face(pname, mat, quad, qn)
        if cap_mat:
            for s, flip_cap in ((0, not flip), (len(self.S)-1, flip)):
                ring = self.S[s]
                c = tuple(sum(p[k] for p in ring)/self.n for k in range(3))
                for i in range(self.n):
                    j = (i+1) % self.n
                    tri = [c, ring[j], ring[i]] if flip_cap else [c, ring[i], ring[j]]
                    m.face(part(0, self.z(s)) if callable(part) else part, cap_mat, tri)
    def decal(self, m, part, mat, seg, z0, z1, t0=0.0, t1=1.0, offset=0.004):
        """Aplica uma faixa sobre o segmento 'seg' do laço entre z0..z1 (frações t0..t1 do segmento),
        deslocada 'offset' para fora. Usada para faróis, frisos, entradas de ar, maçanetas.
        'part' pode ser uma função (seg, z) -> nome da parte."""
        j = (seg+1) % self.n
        for s in range(len(self.S)-1):
            za, zb = self.z(s), self.z(s+1)
            if zb <= z0 or za >= z1: continue
            u0 = max(0.0, (z0-za)/(zb-za)); u1 = min(1.0, (z1-za)/(zb-za))   # recorte exato em z
            def pt(u, t):
                p = lerp3(lerp3(self.S[s][seg], self.S[s][j], t), lerp3(self.S[s+1][seg], self.S[s+1][j], t), u)
                nrm = norm(add(mul(self.vnormal(s, seg), 1-u), mul(self.vnormal(s+1, seg), u)))
                return add(p, mul(nrm, offset))
            zm = za + (zb-za)*(u0+u1)/2
            pname = part(seg, zm) if callable(part) else part
            m.face(pname, mat, [pt(u0, t0), pt(u1, t0), pt(u1, t1), pt(u0, t1)])

def annulus_sector(m, part, mat, axis_x, cy, cz, r0, r1, a0, a1, x0, x1, seg=8):
    """Prisma em forma de setor de coroa circular (eixo X): pinças, pastilhas, para-barros."""
    P = lambda x, r, a: (x, cy + r*math.cos(a), cz + r*math.sin(a))
    xa, xb = min(x0, x1), max(x0, x1)
    for k in range(seg):
        a, b = a0 + (a1-a0)*k/seg, a0 + (a1-a0)*(k+1)/seg
        m.face(part, mat, [P(xa, r1, a), P(xa, r1, b), P(xb, r1, b), P(xb, r1, a)])     # externo
        m.face(part, mat, [P(xb, r0, a), P(xb, r0, b), P(xa, r0, b), P(xa, r0, a)])     # interno
        m.face(part, mat, [P(xb, r0, a), P(xb, r1, a), P(xb, r1, b), P(xb, r0, b)])     # face +x
        m.face(part, mat, [P(xa, r0, b), P(xa, r1, b), P(xa, r1, a), P(xa, r0, a)])     # face -x
    for a in (a0, a1):
        m.face(part, mat, [P(xa, r0, a), P(xb, r0, a), P(xb, r1, a), P(xa, r1, a)])

def beam(m, part, mat, p0, p1, w, h):
    """Barra de seção retangular entre dois pontos (braços de suspensão)."""
    d = norm(sub(p1, p0))
    up = (0, 1, 0) if abs(d[1]) < 0.9 else (1, 0, 0)
    sx = norm(cross(d, up)); sy = norm(cross(sx, d))
    c = [add(add(p, mul(sx, ox*w/2)), mul(sy, oy*h/2)) for p in (p0, p1) for ox, oy in ((-1,-1),(1,-1),(1,1),(-1,1))]
    a, b = c[:4], c[4:]
    for i in range(4):
        j = (i+1) % 4
        m.face(part, mat, [a[i], a[j], b[j], b[i]])
    m.face(part, mat, a[::-1]); m.face(part, mat, b)

def helix(m, part, mat, base, radius, height, turns, wire, seg_per_turn=12):
    """Mola helicoidal vertical (faixa de quads) com centro em base=(x,y,z)."""
    n = int(turns*seg_per_turn)
    pts = []
    for k in range(n+1):
        a = 2*math.pi*k/seg_per_turn
        pts.append((base[0] + radius*math.cos(a), base[1] + height*k/n, base[2] + radius*math.sin(a)))
    for k in range(n):
        p, q = pts[k], pts[k+1]
        o = (0, wire, 0)
        rp = norm((p[0]-base[0], 0, p[2]-base[2])); rq = norm((q[0]-base[0], 0, q[2]-base[2]))
        m.face(part, mat, [sub(p, o), sub(q, o), add(q, o), add(p, o)])
        m.face(part, mat, [add(p, mul(rp, wire)), add(q, mul(rq, wire)), sub(q, mul(rq, wire)), sub(p, mul(rp, wire))])

def tube(m, part, mat, path, radius, seg=8, closed=False, caps=True, mat_fn=None):
    """Tubo de seção circular ao longo de uma polilinha (aros de volante, barras, tubulações).
    radius pode ser número ou função(i) -> raio. mat_fn(i) -> material do trecho i (opcional).
    Usa transporte paralelo do referencial para não torcer."""
    n = len(path)
    tang = []
    for i in range(n):
        a = path[(i-1) % n] if (closed or i > 0) else path[i]
        b = path[(i+1) % n] if (closed or i < n-1) else path[i]
        tang.append(norm(sub(b, a)))
    ref = (0, 1, 0) if abs(tang[0][1]) < 0.9 else (1, 0, 0)
    u = norm(cross(tang[0], ref))
    rings = []
    for i in range(n):
        t = tang[i]
        u = norm(sub(u, mul(t, u[0]*t[0]+u[1]*t[1]+u[2]*t[2])))      # reprojeta no plano normal
        w = cross(t, u)
        r = radius(i) if callable(radius) else radius
        rings.append([(add(path[i], add(mul(u, r*math.cos(2*math.pi*k/seg)), mul(w, r*math.sin(2*math.pi*k/seg)))),
                       norm(add(mul(u, math.cos(2*math.pi*k/seg)), mul(w, math.sin(2*math.pi*k/seg))))) for k in range(seg)])
    count = n if closed else n-1
    for i in range(count):
        a, b = rings[i], rings[(i+1) % n]
        mm = mat_fn(i) if mat_fn else mat
        for k in range(seg):
            k2 = (k+1) % seg
            m.face(part, mm, [a[k][0], a[k2][0], b[k2][0], b[k][0]], [a[k][1], a[k2][1], b[k2][1], b[k][1]])
    if caps and not closed:
        m.face(part, mat, [p for p, _ in rings[0]][::-1]) if seg <= 4 else [m.face(part, mat, [path[0], rings[0][(k+1) % seg][0], rings[0][k][0]]) for k in range(seg)]
        [m.face(part, mat, [path[-1], rings[-1][k][0], rings[-1][(k+1) % seg][0]]) for k in range(seg)]

def quad_img(m, part, mat, center, right, up, w, h, normal_offset=0.0):
    """Quad com imagem (material de imagem do atlas): centro, vetores 'direita' e 'cima' (como o
    observador vê), largura e altura em metros."""
    r, u = norm(right), norm(up)
    nrm = cross(u, r)   # aponta para o observador quando right × up aponta para longe dele
    c = add(center, mul(nrm, normal_offset))
    p = lambda a, b: add(c, add(mul(r, a*w/2), mul(u, b*h/2)))
    m.face(part, mat, [p(-1, -1), p(1, -1), p(1, 1), p(-1, 1)], None, [(0, 0), (1, 0), (1, 1), (0, 1)])

def obox(m, part, mat, center, right, up, w, h, d):
    """Caixa orientada: centro, eixos 'right' e 'up' (ortogonais), dimensões w (right) × h (up) × d (right×up)."""
    r, u = norm(right), norm(up); n = cross(r, u)
    def P(a, b, c): return add(center, add(mul(r, a*w/2), add(mul(u, b*h/2), mul(n, c*d/2))))
    for f in ([P(-1,-1,1),P(1,-1,1),P(1,1,1),P(-1,1,1)], [P(1,-1,-1),P(-1,-1,-1),P(-1,1,-1),P(1,1,-1)],
              [P(1,-1,1),P(1,-1,-1),P(1,1,-1),P(1,1,1)], [P(-1,-1,-1),P(-1,-1,1),P(-1,1,1),P(-1,1,-1)],
              [P(-1,1,1),P(1,1,1),P(1,1,-1),P(-1,1,-1)], [P(-1,-1,-1),P(1,-1,-1),P(1,-1,1),P(-1,-1,1)]):
        m.face(part, mat, f)
