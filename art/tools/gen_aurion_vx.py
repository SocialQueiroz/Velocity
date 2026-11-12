#!/usr/bin/env python3
"""
Aurion VX — malha externa (Velo 0.03). Ficha: docs/ARCHITECTURE.md §E.
Gera assets/velocity/vehicle_models/aurion_vx.{obj,json} e textures/vehicle/aurion_vx.png.
Uso (na raiz do repositório): python3 art/tools/gen_aurion_vx.py
"""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from meshlib import Atlas, Mesh, Loft, box, cylinder, smoothstep, gauss, mix, annulus_sector, beam, helix

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/velocity")

L = 4.40
FRONT_AXLE, REAR_AXLE = 1.28, -1.34
TRACK_F, TRACK_R = 0.81, 0.82          # meia-bitola (centro do pneu)
WHEEL_R = 0.34
ARCH_R = 0.41
GH_FRONT, GH_PEAK0, GH_PEAK1, GH_REAR = 0.62, -0.12, -0.50, -1.78   # para-brisa → teto → fastback

atlas = Atlas({
    "paint":     (236, 236, 236, 255),
    "glass":     (22, 30, 40, 150),
    "trim":      (18, 18, 20, 255),     # plástico preto texturizado
    "carbon":    (34, 36, 40, 255),
    "tire":      (24, 24, 26, 255),
    "rim":       (176, 180, 186, 255),
    "underbody": (44, 46, 50, 255),
    "headlight": (255, 250, 232, 255),
    "drl":       (225, 240, 255, 255),
    "taillight": (214, 18, 22, 255),
    "chrome":    (210, 213, 218, 255),
    "plate":     (232, 232, 226, 255),
    "mirror":    (150, 170, 190, 255),
    "rotor":     (120, 122, 126, 255),     # disco de freio (ferro fundido usinado)
    "rotor_hat": (70, 72, 76, 255),
    "caliper":   (226, 168, 24, 255),      # pinça amarela (assinatura Aurion)
    "pad":       (92, 80, 70, 255),        # material de atrito
    "pad_plate": (60, 62, 66, 255),
    "steel":     (88, 92, 98, 255),
    "spring":    (200, 40, 40, 255),
    "liner":     (14, 14, 15, 255),
    "leather":   (40, 34, 32, 255),        # couro sintético escuro
    "alcantara": (58, 58, 62, 255),
    "stitch":    (226, 168, 24, 255),      # costura amarela (combina com as pinças)
    "carpet":    (30, 30, 32, 255),
    "dash":      (26, 27, 30, 255),        # plástico macio do painel
    "display":   (36, 120, 200, 255),      # telas (emissivas)
    "alu":       (150, 152, 156, 255),     # alumínio escovado
    "amber_l":   (255, 150, 20, 255),      # setas (um material por lado = um canal de luz por lado)
    "amber_r":   (255, 150, 20, 255),
    "reverse":   (240, 242, 246, 255),
})

DOOR_Z0, DOOR_Z1 = -0.80, 0.84      # porta: da coluna B à dobradiça dianteira
HOOD_Z0, HOOD_Z1 = 0.70, 1.84       # capô: da base do para-brisa até antes dos faróis
TRUNK_Z0, TRUNK_Z1 = -2.14, -1.50   # tampa traseira

def region(i, z):
    """Parte móvel a que pertence uma face do loft (segmento i, z)."""
    if i == 0: return "body"
    left = i <= 7
    side = i if left else mirror(i)
    if side in (2, 3, 4, 5) and DOOR_Z0 < z < DOOR_Z1: return "door_l" if left else "door_r"
    if side == 6 and DOOR_Z0 < z < 0.34: return "door_l" if left else "door_r"   # vidro da porta
    if side in (6, 7) and HOOD_Z0 < z < HOOD_Z1: return "hood"
    if side in (6, 7) and TRUNK_Z0 < z < TRUNK_Z1: return "trunk"
    return "body"

def detail_region(i, z):
    r = region(i, z)
    return "body_details" if r == "body" else r

# ---------------------------------------------------------------- perfis (funções de z)
def half_width(z):
    hw = 0.96
    hw -= 0.15 * smoothstep(1.70, 2.20, z) ** 1.6       # nariz arredondado em planta
    hw -= 0.10 * smoothstep(-1.85, -2.20, z) ** 1.6     # traseira
    hw += 0.022 * gauss(z - FRONT_AXLE, 0.45)            # para-lama dianteiro
    hw += 0.040 * gauss(z - REAR_AXLE, 0.55)             # "ombros" traseiros
    hw -= 0.012 * gauss(z + 0.10, 0.45)                  # cintura nas portas
    return hw

def belt(z):
    """Altura da linha de cintura / bordas do capô e da tampa traseira."""
    y = mix(0.84, 0.92, smoothstep(0.6, -1.5, z))        # sobe para trás
    y = mix(y, 0.58, smoothstep(1.80, 2.20, z))          # bico desce
    y -= 0.04 * smoothstep(-1.95, -2.20, z)              # cauda
    y += 0.025 * gauss(z - FRONT_AXLE, 0.32)             # pico do para-lama
    return y

def center_top(z):
    """Altura do capô/tampa no eixo central (sem estufa)."""
    y = belt(z)
    y -= 0.035 * smoothstep(0.9, 1.5, z) * (1 - smoothstep(1.95, 2.15, z))   # capô entre para-lamas
    y += 0.015 * smoothstep(-1.6, -2.0, z)                # ducktail
    return y

def roof(z):
    """Altura do teto (estufa). Fora da estufa devolve None."""
    if z >= GH_FRONT or z <= GH_REAR: return None
    peak = 1.235
    if z > GH_PEAK0:   # para-brisa
        t = (GH_FRONT - z) / (GH_FRONT - GH_PEAK0)
        return mix(center_top(z), peak, math.sin(t * math.pi / 2) ** 1.15)
    if z > GH_PEAK1:
        return peak + 0.008 * math.sin((z - GH_PEAK1) / (GH_PEAK0 - GH_PEAK1) * math.pi)
    t = (z - GH_REAR) / (GH_PEAK1 - GH_REAR)               # fastback
    return mix(center_top(z), peak, 1 - (1 - t) ** 2.2)

def bottom(z):
    y = 0.135
    for zc in (FRONT_AXLE, REAR_AXLE):
        d = abs(z - zc)
        if d < ARCH_R: y = max(y, WHEEL_R + math.sqrt(ARCH_R ** 2 - d * d))
    y = mix(y, 0.20, smoothstep(2.05, 2.20, z))          # bico sobe (ângulo de ataque)
    y = mix(y, 0.24, smoothstep(-2.0, -2.20, z))         # saída do difusor
    return y

# ---------------------------------------------------------------- seções
def sections():
    zs, z = [], -L / 2
    while z < L / 2 - 1e-9:
        zs.append(round(z, 4)); z += 0.04
    zs.append(L / 2)
    out = []
    for z in zs:
        hw, yb, bt = half_width(z), bottom(z), belt(z)
        ct = center_top(z)
        r = roof(z)
        gh = 0.0 if r is None else max(0.0, r - ct)
        roof_y = ct if r is None else max(r, ct)
        roof_w = mix(hw * 0.80, 0.56, smoothstep(0.0, 0.12, gh))
        win_w = hw * 0.86
        rocker = max(yb + 0.03, 0.26)
        mid = max(rocker + 0.03, 0.60)                     # vinco lateral (linha de caráter)
        shoulder = max(mid + 0.02, bt - 0.075)
        top_edge = max(shoulder + 0.01, bt)
        winbase = top_edge + 0.006
        L_ = [(hw * 0.90, yb), (hw * 0.965, rocker), (hw, mid), (hw * 0.988, shoulder),
              (hw * 0.95, top_edge), (win_w, winbase), (roof_w, roof_y)]
        R_ = [(-x, y) for x, y in reversed(L_)]
        ring = [(-hw * 0.90, yb, z)] + [(x, y, z) for x, y in L_] + [(x, y, z) for x, y in R_[:-1]]
        out.append(ring)
    return out
# índices do laço: 0 baixo-dir, 1 baixo-esq, 2 rocker, 3 vinco, 4 ombro, 5 cintura, 6 base janela, 7 teto-esq,
#                  8 teto-dir, 9 base janela-dir, 10 cintura-dir, 11 ombro-dir, 12 vinco-dir, 13 rocker-dir
# segmentos esquerdos 1..6 espelham em 13..8 (espelho de i = 14 - i)
N = 14
def mirror(i): return (N - i) % N

def material(i, s, normal, z):
    if i == 0: return "underbody"
    side = i if i <= 7 else mirror(i)
    r = roof(z)
    gh = 0.0 if r is None else r - center_top(z)
    if side == 6:      # janela lateral (entre colunas A e C)
        return "glass" if (gh > 0.10 and DOOR_Z0 < z < 0.34) else "paint"
    if side == 7:      # teto: para-brisa e vidro traseiro
        if gh > 0.02 and z > GH_PEAK0 + 0.02: return "glass"
        if gh > 0.02 and -1.48 < z < GH_PEAK1 - 0.04: return "glass"
        return "paint"
    return "paint"

def build():
    m = Mesh(atlas)
    secs = sections()
    body = Loft(secs)
    body.emit(m, region, material, cap_mat="paint")

    def both(seg, z0, z1, t0, t1, mat, part=detail_region, off=0.004):
        body.decal(m, part, mat, seg, z0, z1, t0, t1, off)
        body.decal(m, part, mat, mirror(seg), z0, z1, 1 - t1, 1 - t0, off)

    # faróis em "L": lente no ombro e na cintura do bico + DRL superior
    both(5, 1.86, 2.14, 0.05, 0.95, "headlight")
    both(4, 1.98, 2.14, 0.10, 0.95, "headlight")
    both(6, 1.90, 2.12, 0.00, 0.22, "drl", off=0.005)
    # moldura preta dos faróis
    both(5, 1.82, 1.86, 0.0, 1.0, "trim")
    # entrada de ar lateral atrás da porta, saia lateral em carbono, frisos de porta, maçaneta
    both(3, -1.10, -0.84, 0.12, 0.92, "trim")
    both(2, -1.06, -0.86, 0.45, 1.00, "trim")
    both(1, -0.92, 0.86, 0.00, 1.00, "carbon")
    for zc in (0.84, -0.80):
        for seg in (2, 3, 4):
            both(seg, zc - 0.007, zc + 0.007, 0.0, 1.0, "trim", off=0.003)
    both(4, -0.66, -0.52, 0.25, 0.65, "chrome")
    # moldura das janelas (borracha preta) e coluna B escurecida
    both(5, -0.86, 0.36, 0.55, 1.00, "trim", off=0.002)
    # forro de teto e colunas por dentro (faixas deslocadas 1 cm para dentro; invisíveis por fora)
    body.decal(m, "interior", "alcantara", 7, GH_PEAK1 - 0.04, GH_PEAK0 + 0.02, 0.0, 1.0, -0.012)
    body.decal(m, "interior", "alcantara", 7, -1.78, -1.48, 0.0, 1.0, -0.012)
    for seg in (6, mirror(6)):
        body.decal(m, "interior", "alcantara", seg, 0.30, GH_FRONT, 0.0, 1.0, -0.012)
        body.decal(m, "interior", "alcantara", seg, -1.78, -0.84, 0.0, 1.0, -0.012)
    # saídas de ar do capô
    both(6, 1.02, 1.32, 0.45, 0.85, "trim")
    # bocal de combustível (lado direito, traseira)
    body.decal(m, "body_details", "trim", mirror(4), -1.42, -1.30, 0.30, 0.80, 0.003)
    # setas: dianteiras abaixo dos faróis e repetidoras laterais nos para-lamas
    for seg, mat in ((3, "amber_l"), (mirror(3), "amber_r")):
        t0, t1 = (0.10, 0.90)
        body.decal(m, "body_details", mat, seg, 1.92, 2.10, t0, t1, 0.005)
    body.decal(m, "body_details", "amber_l", 4, 1.50, 1.58, 0.30, 0.75, 0.004)
    body.decal(m, "body_details", "amber_r", mirror(4), 1.50, 1.58, 0.25, 0.70, 0.004)

    # ------ frente: grade trapezoidal, entradas laterais, splitter, emblema
    zf = 2.2
    box(m, "front", "trim", -0.52, 0.22, zf - 0.01, 0.52, 0.42, zf + 0.012)
    for sx in (1, -1):
        box(m, "front", "trim", sx * 0.58, 0.24, zf - 0.03, sx * 0.74, 0.36, zf + 0.008)
    box(m, "front", "carbon", -0.80, 0.135, 1.90, 0.80, 0.16, zf + 0.06)
    box(m, "front", "chrome", -0.05, 0.47, zf - 0.005, 0.05, 0.52, zf + 0.012)
    # grelha: barras horizontais cromadas
    for k in range(3):
        y = 0.26 + k * 0.055
        box(m, "front", "chrome", -0.50, y, zf + 0.012, 0.50, y + 0.008, zf + 0.018)

    # ------ traseira: lanterna contínua com assinatura de 3 barras, difusor, 4 escapes, placa
    zr = -2.2
    box(m, "rear_lights", "taillight", -0.80, 0.80, zr - 0.012, 0.80, 0.835, zr + 0.01)
    for sx in (1, -1):
        for k in range(3):
            y = 0.71 + k * 0.032
            box(m, "rear_lights", "taillight", sx * 0.58, y, zr - 0.012, sx * 0.80, y + 0.016, zr + 0.01)
    box(m, "rear", "trim", -0.82, 0.78, zr - 0.006, 0.82, 0.85, zr + 0.004)
    box(m, "rear", "carbon", -0.66, 0.14, zr - 0.02, 0.66, 0.30, zr + 0.25)
    for x in (-0.38, -0.13, 0.13, 0.38):
        box(m, "rear", "carbon", x - 0.008, 0.12, zr - 0.04, x + 0.008, 0.30, zr + 0.25)
    for sx in (1, -1):
        for x in (0.47, 0.60):
            cylinder(m, "rear", "chrome", "z", (sx * x, 0.25, zr - 0.02), 0.042, 0.12, seg=14, cap_mat="trim", hollow=0.032)
    box(m, "rear", "plate", -0.26, 0.42, zr - 0.012, 0.26, 0.53, zr + 0.002)
    for sx, mat in ((1, "amber_l"), (-1, "amber_r")):
        box(m, "rear_lights", mat, sx * 0.60, 0.655, zr - 0.012, sx * 0.80, 0.69, zr + 0.01)
        box(m, "rear_lights", "reverse", sx * 0.30, 0.57, zr - 0.012, sx * 0.48, 0.61, zr + 0.01)
    box(m, "rear", "chrome", -0.06, 0.62, zr - 0.010, 0.06, 0.66, zr + 0.002)

    # ------ spoiler ativo (posição recolhida) — parte própria para animação na Velo 0.06
    box(m, "spoiler", "paint", -0.66, center_top(-2.05) + 0.004, -2.16, 0.66, center_top(-2.05) + 0.03, -1.94)
    box(m, "spoiler", "carbon", -0.66, center_top(-2.05) + 0.03, -2.16, 0.66, center_top(-2.05) + 0.036, -2.06)

    # ------ retrovisores: haste + carcaça pintada + espelho
    for sx in (1, -1):
        hw = half_width(0.42)
        dp = "door_l" if sx > 0 else "door_r"   # retrovisor fica na porta
        box(m, dp, "trim", sx * hw * 0.93, 0.90, 0.40, sx * (hw + 0.08), 0.93, 0.46)
        box(m, dp, "paint", sx * (hw + 0.05), 0.90, 0.36, sx * (hw + 0.21), 1.01, 0.48)
        box(m, dp, "mirror", sx * (hw + 0.06), 0.91, 0.355, sx * (hw + 0.20), 1.00, 0.362)

    # ------ estrutura: cofre do motor (frente), assoalho rebaixado na cabine, porta-malas (trás)
    box(m, "underbody", "underbody", -0.58, 0.14, 0.62, 0.58, 0.64, 2.0)
    box(m, "underbody", "underbody", -0.58, 0.14, -2.0, 0.58, 0.64, -0.98)
    box(m, "underbody", "underbody", -0.80, 0.12, -0.98, 0.80, 0.17, 0.62)

    interior(m)

    # ------ caixas de roda (para-barro), freios e suspensão
    for corner, (x, z, side) in {"fl": (TRACK_F, FRONT_AXLE, 1), "fr": (-TRACK_F, FRONT_AXLE, -1),
                                 "rl": (TRACK_R, REAR_AXLE, 1), "rr": (-TRACK_R, REAR_AXLE, -1)}.items():
        front = corner[0] == "f"
        hw = half_width(z)
        # para-barro: meia-cúpula interna da caixa de roda
        annulus_sector(m, "liners", "liner", True, WHEEL_R, z, ARCH_R - 0.005, ARCH_R + 0.01,
                       -math.pi / 2 - 0.15, math.pi / 2 + 0.15, side * 0.50, side * (hw * 0.97), seg=14)
        brakes(m, corner, x, z, side, front)
        suspension(m, corner, x, z, side, front)

    # ------ rodas
    wheels = {"wheel_fl": (TRACK_F, FRONT_AXLE, 1, 0.245, 0.241), "wheel_fr": (-TRACK_F, FRONT_AXLE, -1, 0.245, 0.241),
              "wheel_rl": (TRACK_R, REAR_AXLE, 1, 0.285, 0.254), "wheel_rr": (-TRACK_R, REAR_AXLE, -1, 0.285, 0.254)}
    for name, (x, z, side, w, rim_r) in wheels.items():
        wheel(m, name, x, WHEEL_R, z, side, w, rim_r)
    return m

SEAT_X, SEAT_HIP_Y, SEAT_Z = 0.40, 0.27, -0.30          # quadril do motorista (igual ao VehicleEntity)
SW_CENTER = (SEAT_X, 0.86, 0.14)                         # centro do volante
SW_TILT = math.radians(22)                               # inclinação da coluna em relação à vertical

def steering_axis():
    # eixo da coluna aponta do volante para o painel: para frente (+Z) e para baixo
    return (0.0, -math.sin(SW_TILT), math.cos(SW_TILT))

def interior(m):
    I = "interior"
    hw_cab = half_width(-0.2) * 0.86
    # assoalho com carpete, túnel central, firewall e anteparo traseiro
    box(m, I, "carpet", -0.78, 0.17, -0.96, 0.78, 0.19, 0.60)
    box(m, I, "carpet", -0.14, 0.17, -0.96, 0.14, 0.34, 0.60)
    box(m, I, "carpet", -0.78, 0.19, 0.50, 0.78, 0.62, 0.60)
    box(m, I, "alcantara", -0.80, 0.19, -1.00, 0.80, 0.98, -0.94)
    # painel: corpo principal, parte superior inclinada sob o para-brisa
    beam(m, I, "dash", (-0.80, 0.74, 0.42), (0.80, 0.74, 0.42), 0.34, 0.16)
    beam(m, I, "alcantara", (-0.80, 0.84, 0.50), (0.80, 0.84, 0.50), 0.22, 0.05)
    box(m, I, "stitch", -0.80, 0.818, 0.247, 0.80, 0.822, 0.252)
    # capuz do quadro de instrumentos + tela digital 12,3" (emissiva)
    box(m, I, "dash", SEAT_X - 0.20, 0.82, 0.24, SEAT_X + 0.20, 0.95, 0.40)
    box(m, I, "display", SEAT_X - 0.17, 0.835, 0.236, SEAT_X + 0.17, 0.925, 0.242)
    # central multimídia e saídas de ar (4)
    box(m, I, "dash", -0.14, 0.76, 0.25, 0.14, 0.92, 0.30)
    box(m, I, "display", -0.12, 0.775, 0.246, 0.12, 0.905, 0.252)
    for x in (-0.66, -0.22, 0.22 - 0.0, 0.66):
        if abs(x - SEAT_X) < 0.15: continue
        box(m, I, "trim", x - 0.07, 0.70, 0.244, x + 0.07, 0.76, 0.252)
        for k in range(3):
            box(m, I, "alu", x - 0.065, 0.712 + k * 0.016, 0.240, x + 0.065, 0.716 + k * 0.016, 0.244)
    # console central alto com seletor de câmbio curto
    box(m, I, "leather", -0.13, 0.34, -0.70, 0.13, 0.50, 0.30)
    box(m, I, "stitch", -0.131, 0.495, -0.70, 0.131, 0.499, 0.30)
    box(m, I, "trim", -0.10, 0.50, -0.30, 0.10, 0.52, 0.05)
    box(m, I, "alu", -0.012, 0.52, -0.13, 0.012, 0.58, -0.11)
    box(m, I, "leather", -0.035, 0.58, -0.155, 0.035, 0.62, -0.085)
    # bancos concha (motorista à esquerda, +X)
    for sx in (1, -1):
        x = sx * SEAT_X
        beam(m, I, "alcantara", (x, SEAT_HIP_Y - 0.04, -0.40), (x, SEAT_HIP_Y - 0.01, 0.06), 0.40, 0.08)    # almofada
        beam(m, I, "alcantara", (x, SEAT_HIP_Y + 0.02, -0.46), (x, 0.98, -0.66), 0.40, 0.08)              # encosto
        for bx in (-0.22, 0.22):                                                                           # abas laterais
            beam(m, I, "leather", (x + bx, SEAT_HIP_Y + 0.01, -0.40), (x + bx, SEAT_HIP_Y + 0.05, 0.04), 0.07, 0.12)
            beam(m, I, "leather", (x + bx, SEAT_HIP_Y + 0.06, -0.48), (x + bx, 0.80, -0.60), 0.07, 0.14)
        beam(m, I, "leather", (x, 0.94, -0.645), (x, 1.12, -0.69), 0.26, 0.08)                            # apoio de cabeça
        box(m, I, "stitch", x - 0.005, SEAT_HIP_Y + 0.12, -0.62, x + 0.005, 0.93, -0.51)
        box(m, I, "steel", x - 0.24, 0.17, -0.45, x + 0.24, SEAT_HIP_Y - 0.08, 0.05)                     # base/trilhos
        # forro de porta com apoio de braço e puxador
        xd = sx * (hw_cab + 0.01)
        D = "door_l" if sx > 0 else "door_r"
        box(m, D, "leather", xd - sx * 0.03, 0.30, -0.78, xd, 0.86, 0.40)
        box(m, D, "alcantara", xd - sx * 0.09, 0.52, -0.55, xd - sx * 0.03, 0.57, 0.15)
        box(m, D, "alu", xd - sx * 0.05, 0.62, -0.05, xd - sx * 0.032, 0.66, 0.10)
    # pedais (motorista): acelerador e freio
    box(m, I, "alu", SEAT_X - 0.16, 0.24, 0.47, SEAT_X - 0.04, 0.34, 0.49)
    box(m, I, "alu", SEAT_X + 0.04, 0.22, 0.48, SEAT_X + 0.10, 0.36, 0.50)
    # retrovisor interno
    box(m, I, "trim", -0.01, 1.14, -0.10, 0.01, 1.22, -0.09)
    box(m, I, "trim", -0.12, 1.09, -0.12, 0.12, 1.15, -0.09)
    box(m, I, "mirror", -0.11, 1.095, -0.124, 0.11, 1.145, -0.120)
    # coluna de direção (fixa)
    cx, cy, cz = SW_CENTER
    ax = steering_axis()
    beam(m, I, "dash", (cx, cy + ax[1] * 0.05, cz + ax[2] * 0.05), (cx, cy + ax[1] * 0.30, cz + ax[2] * 0.30), 0.09, 0.09)
    steering_wheel(m)

def steering_wheel(m):
    """Volante de base reta com 3 raios, aro em couro e aletas de troca (parte animada)."""
    P = "steering_wheel"
    cx, cy, cz = SW_CENTER
    ax = steering_axis()
    up = norm3((0.0, math.cos(SW_TILT), math.sin(SW_TILT)))   # "cima" do volante
    right = (-1.0, 0.0, 0.0)                                    # direita do motorista (-X)
    def at(u, v, w=0.0):   # u = direita, v = cima, w = ao longo do eixo
        return (cx + right[0] * u + up[0] * v + ax[0] * w, cy + right[1] * u + up[1] * v + ax[1] * w,
                cz + right[2] * u + up[2] * v + ax[2] * w)
    R, seg = 0.175, 28
    ring = []
    for k in range(seg + 1):
        a = 2 * math.pi * k / seg
        u, v = R * math.sin(a), R * math.cos(a)
        if v < -0.13: v = -0.13                                   # base reta
        ring.append((u, v))
    for k in range(seg):
        (u0, v0), (u1, v1) = ring[k], ring[k + 1]
        beam(m, P, "leather", at(u0, v0), at(u1, v1), 0.03, 0.03)
    beam(m, P, "stitch", at(-0.012, R - 0.006, -0.016), at(0.012, R - 0.006, -0.016), 0.012, 0.006)   # marca 12h
    for u, v in ((-0.16, 0.0), (0.16, 0.0), (0.0, -0.13)):        # 3 raios
        beam(m, P, "alu", at(0, 0, 0.01), at(u, v, 0.0), 0.04, 0.012)
    beam(m, P, "dash", at(0, 0.03, 0.02), at(0, -0.05, 0.02), 0.11, 0.05)   # cubo (airbag)
    box(m, P, "chrome", *[a - 0.012 for a in at(0, 0.0, -0.004)], *[a + 0.012 for a in at(0, 0.0, -0.004)])
    for side in (-1, 1):                                          # aletas
        beam(m, P, "alu", at(side * 0.12, 0.04, 0.05), at(side * 0.22, 0.06, 0.05), 0.012, 0.03)

def norm3(v):
    l = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / l for c in v)

DISC_X_IN = 0.085   # deslocamento do disco para dentro do centro do pneu

def brakes(m, corner, cx, cz, outward, front):
    """Disco (gira com a roda), pinça fixa na manga (esterça), 2 pastilhas removíveis por roda."""
    r_disc = 0.180 if front else 0.170
    thick = 0.032 if front else 0.026
    xd = cx - outward * DISC_X_IN
    cy = WHEEL_R
    # disco ventilado: duas pistas + aletas internas visíveis pela borda, chapéu central
    cylinder(m, f"disc_{corner}", "rotor", "x", (xd, cy, cz), r_disc, thick, seg=28, cap_mat="rotor", hollow=0.105)
    cylinder(m, f"disc_{corner}", "rotor_hat", "x", (xd + outward * 0.02, cy, cz), 0.105, 0.045, seg=16, cap_mat="rotor_hat")
    for k in range(12):  # furos de ventilação (marcas escuras nas duas faces)
        a = 2 * math.pi * k / 12
        r = 0.140
        for face in (1, -1):
            x = xd + face * (thick / 2 + 0.001)
            y0, z0 = cy + r * math.cos(a), cz + r * math.sin(a)
            q = [(x, y0 - 0.008, z0 - 0.008), (x, y0 - 0.008, z0 + 0.008), (x, y0 + 0.008, z0 + 0.008), (x, y0 + 0.008, z0 - 0.008)]
            m.face(f"disc_{corner}", "trim", q if face * outward > 0 else q[::-1])
    # pinça: abraça o disco atrás do eixo, em cima (ângulo medido de +Y para +Z)
    a0, a1 = -math.pi * 0.62, -math.pi * 0.18
    span = 0.105 if front else 0.085
    part = f"caliper_{corner}"
    annulus_sector(m, part, "caliper", True, cy, cz, r_disc - 0.045, r_disc + 0.022, a0, a1,
                   xd - outward * (thick / 2 + 0.035), xd - outward * (thick / 2 + 0.002), seg=6)   # corpo interno
    annulus_sector(m, part, "caliper", True, cy, cz, r_disc - 0.045, r_disc + 0.022, a0, a1,
                   xd + outward * (thick / 2 + 0.002), xd + outward * (thick / 2 + 0.030), seg=6)   # corpo externo
    annulus_sector(m, part, "caliper", True, cy, cz, r_disc + 0.004, r_disc + 0.022, a0, a1,
                   xd - outward * (thick / 2 + 0.035), xd + outward * (thick / 2 + 0.030), seg=6)   # ponte
    # pastilhas (placa + material de atrito), uma de cada lado do disco
    for side_name, sgn in (("inner", -1), ("outer", 1)):
        p = f"pad_{corner}_{side_name}"
        xf = xd + sgn * outward * (thick / 2 + 0.0005)
        annulus_sector(m, p, "pad", True, cy, cz, r_disc - 0.040, r_disc - 0.004, a0 + 0.08, a1 - 0.08,
                       xf, xf + sgn * outward * 0.010, seg=5)
        annulus_sector(m, p, "pad_plate", True, cy, cz, r_disc - 0.042, r_disc - 0.002, a0 + 0.06, a1 - 0.06,
                       xf + sgn * outward * 0.010, xf + sgn * outward * 0.016, seg=5)

def suspension(m, corner, cx, cz, outward, front):
    """Duplo A (braços superior e inferior), manga de eixo, cubo e conjunto mola/amortecedor."""
    cy = WHEEL_R
    k = f"knuckle_{corner}"
    x_hub = cx - outward * 0.14
    cylinder(m, k, "steel", "x", (x_hub, cy, cz), 0.055, 0.07, seg=12)               # cubo/rolamento
    box(m, k, "steel", x_hub - 0.025, cy - 0.17, cz - 0.03, x_hub + 0.025, cy + 0.17, cz + 0.03)   # manga
    s = f"suspension_{corner}"
    x_in = outward * 0.50
    for y_out, y_in, w in ((cy - 0.15, 0.20, 0.035), (cy + 0.15, 0.52, 0.03)):
        for dz in (-0.16, 0.16):
            beam(m, s, "steel", (x_hub, y_out, cz), (x_in, y_in, cz + dz), w, w)
    base = (cx - outward * 0.26, cy - 0.12, cz - (0.05 if front else -0.05))
    # amortecedor (tubo) + mola helicoidal
    box(m, s, "steel", base[0] - 0.022, base[1], base[2] - 0.022, base[0] + 0.022, base[1] + 0.42, base[2] + 0.022)
    helix(m, s, "spring", (base[0], base[1] + 0.12, base[2]), 0.055, 0.24, 5, 0.008)

def wheel(m, part, cx, cy, cz, outward, tire_w, rim_r, seg=32):
    xi, xo = cx - outward * tire_w / 2, cx + outward * tire_w / 2
    P = lambda x, r, a: (x, cy + r * math.cos(a), cz + r * math.sin(a))
    sh = WHEEL_R - 0.028
    xs_o, xs_i = xo - outward * 0.022, xi + outward * 0.022
    flip = lambda q: q if outward > 0 else q[::-1]
    for k in range(seg):
        a, b = 2 * math.pi * k / seg, 2 * math.pi * (k + 1) / seg
        na, nb = (0, math.cos(a), math.sin(a)), (0, math.cos(b), math.sin(b))
        q = [P(xs_i, WHEEL_R, a), P(xs_o, WHEEL_R, a), P(xs_o, WHEEL_R, b), P(xs_i, WHEEL_R, b)]
        ns = [na, na, nb, nb]
        if outward < 0: q, ns = q[::-1], ns[::-1]
        m.face(part, "tire", q, ns)
        for xe, xs, sgn in ((xo, xs_o, 1), (xi, xs_i, -1)):
            q1 = [P(xs, WHEEL_R, a), P(xe, sh, a), P(xe, sh, b), P(xs, WHEEL_R, b)]
            q2 = [P(xe, sh, a), P(xe, rim_r, a), P(xe, rim_r, b), P(xe, sh, b)]
            if sgn * outward < 0: q1, q2 = q1[::-1], q2[::-1]
            m.face(part, "tire", q1); m.face(part, "tire", q2)
        xr = xo - outward * 0.06
        m.face(part, "rim", flip([P(xo, rim_r, b), P(xr, rim_r * 0.95, b), P(xr, rim_r * 0.95, a), P(xo, rim_r, a)]))
        m.face(part, "rotor_hat", flip([P(xr, rim_r * 0.95, b), P(xi, rim_r * 0.95, b), P(xi, rim_r * 0.95, a), P(xr, rim_r * 0.95, a)]))
    # 5 raios duplos
    xr = xo - outward * 0.06
    x_out = xo - outward * 0.010
    for k in range(5):
        a = 2 * math.pi * k / 5
        for da in (-0.12, 0.12):
            c0, c1 = P(0, 0.065, a), P(0, rim_r * 0.93, a + da)
            dy, dz = c1[1] - c0[1], c1[2] - c0[2]
            Ln = math.hypot(dy, dz); py, pz = -dz / Ln * 0.020, dy / Ln * 0.020
            pts = lambda x, dip=0.0: [(x, c0[1] + py, c0[2] + pz), (x - outward * dip, c1[1] + py, c1[2] + pz),
                                      (x - outward * dip, c1[1] - py, c1[2] - pz), (x, c0[1] - py, c0[2] - pz)]
            top, bot = pts(x_out, 0.012), pts(xr)
            m.face(part, "rim", flip(top))
            for i in range(4):
                j = (i + 1) % 4
                m.face(part, "rim", flip([bot[i], bot[j], top[j], top[i]]))
    cylinder(m, part, "rim", "x", (xo - outward * 0.03, cy, cz), 0.075, 0.05, seg=10, cap_mat="chrome")
    for k in range(5):  # porcas
        a = 2 * math.pi * k / 5 + math.pi / 5
        cylinder(m, part, "chrome", "x", (xo - outward * 0.003, cy + 0.045 * math.cos(a), cz + 0.045 * math.sin(a)), 0.009, 0.012, seg=6)

def main():
    m = build()
    os.makedirs(os.path.join(ASSETS, "vehicle_models"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "textures/vehicle"), exist_ok=True)
    m.write_obj(os.path.join(ASSETS, "vehicle_models/aurion_vx.obj"), "Aurion VX — gerado por art/tools/gen_aurion_vx.py")
    atlas.write_png(os.path.join(ASSETS, "textures/vehicle/aurion_vx.png"))
    piv = lambda x, z: [x, WHEEL_R, z]
    desc = {
        "_comment": "Gerado por art/tools/gen_aurion_vx.py — não editar à mão.",
        "mesh": "velocity:vehicle_models/aurion_vx.obj",
        "texture": "velocity:textures/vehicle/aurion_vx.png",
        "default_paint": "#A3131C",
        "parts": {
            "steer_fl": {"pivot": piv(TRACK_F, FRONT_AXLE), "animation": "steer"},
            "steer_fr": {"pivot": piv(-TRACK_F, FRONT_AXLE), "animation": "steer"},
            "wheel_fl": {"parent": "steer_fl", "pivot": piv(TRACK_F, FRONT_AXLE), "animation": "wheel_spin"},
            "wheel_fr": {"parent": "steer_fr", "pivot": piv(-TRACK_F, FRONT_AXLE), "animation": "wheel_spin"},
            "wheel_rl": {"pivot": piv(TRACK_R, REAR_AXLE), "animation": "wheel_spin"},
            "wheel_rr": {"pivot": piv(-TRACK_R, REAR_AXLE), "animation": "wheel_spin"},
            # discos giram com a roda; pinças, pastilhas e mangas acompanham o esterço (dianteiras)
            **{f"disc_{c}": {"parent": f"wheel_{c}"} for c in ("fl", "fr", "rl", "rr")},
            **{f"{p}_{c}": {"parent": f"steer_{c}"} for p in ("caliper", "knuckle") for c in ("fl", "fr")},
            **{f"pad_{c}_{s}": {"parent": f"steer_{c}"} for c in ("fl", "fr") for s in ("inner", "outer")},
            "steering_wheel": {"pivot": list(SW_CENTER), "animation": "steering_wheel",
                               "axis": list(steering_axis()), "factor": 12.0},
            # dobradiças: portas na frente (eixo vertical), capô na base do para-brisa, tampa à frente dela
            "door_l": {"pivot": [half_width(DOOR_Z1) * 0.99, 0.6, DOOR_Z1], "animation": "door_l", "axis": [0, 1, 0],
                       "factor": -math.radians(62)},
            "door_r": {"pivot": [-half_width(DOOR_Z1) * 0.99, 0.6, DOOR_Z1], "animation": "door_r", "axis": [0, 1, 0],
                       "factor": math.radians(62)},
            "hood": {"pivot": [0, center_top(HOOD_Z0), HOOD_Z0], "animation": "hood", "axis": [1, 0, 0],
                     "factor": -math.radians(55)},
            "trunk": {"pivot": [0, center_top(TRUNK_Z1), TRUNK_Z1], "animation": "trunk", "axis": [1, 0, 0],
                      "factor": math.radians(48)},
            "spoiler": {"parent": "trunk", "pivot": [0, center_top(-2.05), -1.94], "animation": "spoiler",
                        "axis": [1, 0, 0], "factor": math.radians(14)},
        },
        "materials": {
            "paint": {"role": "paint"},
            "glass": {"role": "translucent"},
            "headlight": {"role": "emissive", "light": "headlights"},
            "drl": {"role": "emissive", "light": "always"},
            "display": {"role": "emissive", "light": "engine"},
            "amber_l": {"role": "emissive", "light": "turn_l"},
            "amber_r": {"role": "emissive", "light": "turn_r"},
            "reverse": {"role": "emissive", "light": "reverse"},
            "taillight": {"role": "emissive", "light": "tail"},
        },
    }
    with open(os.path.join(ASSETS, "vehicle_models/aurion_vx.json"), "w") as f:
        json.dump(desc, f, indent=2, ensure_ascii=False); f.write("\n")
    print(f"Aurion VX: {m.triangles()} triângulos, {len(m.parts)} partes")

if __name__ == "__main__":
    main()
