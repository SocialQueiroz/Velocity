#!/usr/bin/env python3
"""
Sintetiza todos os sons do VELOCITY (Velo 0.07). Sons 100 % gerados por código: sem gravações de
terceiros, sem questões de licença. Saída: Ogg Vorbis mono 22,05 kHz em assets/velocity/sounds/.
Uso (na raiz do repositório): python3 art/tools/gen_sounds.py      (requer numpy e ffmpeg com libvorbis)

Motor V6 4 tempos: 3 explosões por volta → frequência de ignição = rpm/60 × 3. Cada camada é um loop
perfeito (número inteiro de ciclos de 2 voltas; convolução e ruído circulares via FFT).
"""
import os, subprocess, tempfile, wave
import numpy as np

SR = 22050
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "src/main/resources/assets/velocity/sounds")
rng = np.random.default_rng(20261112)   # semente fixa: saída reprodutível

def save(name, x, loop=False):
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x)) or 1.0
    x = x / peak * 0.89
    if loop:   # emenda no trecho mais silencioso (janela de 4 ms): artefatos do Vorbis nas bordas ficam inaudíveis
        w = int(0.004 * SR)
        energy = np.convolve(np.abs(x), np.ones(w), mode="same")
        x = np.roll(x, -int(np.argmin(energy)))
    if not loop:   # rampas curtas nas pontas evitam estalos
        n = min(len(x) // 10, int(0.004 * SR))
        x[:n] *= np.linspace(0, 1, n); x[-n:] *= np.linspace(1, 0, n)
    pcm = (x * 32767).astype(np.int16)
    path = os.path.join(OUT, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "4", path], check=True)
        os.unlink(tmp.name)
    return path

def circ_filter(x, lo=None, hi=None, slope=None):
    """Filtro passa-faixa circular (periódico) no domínio da frequência."""
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    g = np.ones_like(f)
    if lo: g *= 1 / (1 + (lo / np.maximum(f, 1e-3)) ** 4)
    if hi: g *= 1 / (1 + (f / hi) ** 4)
    if slope: g *= (np.maximum(f, 20) / 200.0) ** slope
    return np.fft.irfft(X * g, len(x))

def circ_conv(x, h):
    n = len(x); H = np.fft.rfft(h, n)
    return np.fft.irfft(np.fft.rfft(x) * H, n)

def resonator_ir(freqs, decays, amps, length=0.06):
    t = np.arange(int(length * SR)) / SR
    return sum(a * np.exp(-t / d) * np.sin(2 * np.pi * f * t) for f, d, a in zip(freqs, decays, amps))

# ---------------------------------------------------------------- motor
def engine_layer(rpm, load, seconds=2.0):
    f_fire = rpm / 60.0 * 3.0
    cycles = max(6, int(round(seconds * f_fire / 6.0)) * 6)          # múltiplo de 6 ignições (2 voltas)
    n = int(round(cycles / f_fire * SR))
    f_fire = cycles / (n / SR)                                        # ajusta para loop exato
    x = np.zeros(n)
    # ordem de ignição alterna bancadas (escapes de comprimentos diferentes → timbres distintos)
    bank_gain = [1.0, 0.86, 0.97, 0.90, 1.03, 0.84]
    jitter = rng.normal(0, 0.0007, 6)
    for k in range(cycles):
        pos = (k / f_fire + jitter[k % 6] * (1 - rpm / 9000)) * SR
        i = int(pos) % n; frac = pos - int(pos)
        g = bank_gain[k % 6] * (1 + rng.normal(0, 0.03))
        x[i] += g * (1 - frac); x[(i + 1) % n] += g * frac
    bright = 0.35 + 0.65 * load
    ir = resonator_ir([88, 176, 260, 410, 1150], [0.012, 0.007, 0.005, 0.003, 0.0012],
                      [1.0, 0.55 * bright, 0.40 * bright, 0.30 * bright ** 2, 0.12 * bright ** 2])
    ir_b = resonator_ir([97, 190, 300], [0.010, 0.006, 0.004], [0.6, 0.35 * bright, 0.25 * bright])
    y = circ_conv(x, ir + 0.6 * ir_b)
    # componente grave na frequência de ignição e meia ordem (V6 "ronco")
    t = np.arange(n) / SR
    y += 0.10 * np.sin(2 * np.pi * f_fire * t) + 0.05 * np.sin(2 * np.pi * f_fire / 2 * t)
    # admissão/mecânico: ruído periódico modulado pela ignição
    noise = circ_filter(rng.normal(0, 1, n), lo=900, hi=4200)
    mod = 0.6 + 0.4 * np.sin(2 * np.pi * f_fire * t) ** 2
    y += noise * mod * (0.025 + 0.06 * load) * (rpm / 4000) ** 0.7
    if load < 0.5:   # desaceleração: mais abafado, leve borbulhar
        y = circ_filter(y, hi=1400)
        y += circ_filter(rng.normal(0, 1, n), lo=120, hi=600) * 0.03
    y = circ_filter(y, lo=35, hi=7000)
    return y / np.sqrt(np.mean(y ** 2)) * 0.25

def starter():
    t = np.arange(int(1.35 * SR)) / SR
    whine = sum(np.sin(2 * np.pi * h * (460 + 30 * np.sin(2 * np.pi * 3 * t)) * t) / h for h in (1, 2, 3, 4))
    crank_rate = 3.5 + 3.0 * t
    phase = np.cumsum(crank_rate) / SR
    chug = (np.sin(2 * np.pi * phase) > 0.75).astype(float)
    chug = np.convolve(chug, resonator_ir([80, 160], [0.02, 0.01], [1, 0.4], 0.05), mode="same")
    env_s = np.clip(1.15 - t, 0, 1)
    y = 0.18 * whine * env_s + 0.5 * chug * env_s
    idle = np.tile(engine_layer(1100, 0.8, 0.5), 4)[:len(t)]
    catch = np.clip((t - 0.9) / 0.15, 0, 1) * np.clip(1 - (t - 1.2) / 0.15, 0.4, 1)
    return y + 1.2 * idle * catch

def shutdown():
    dur = 1.1; n = int(dur * SR); t = np.arange(n) / SR
    rpm = 850 * np.exp(-t / 0.35)
    phase = np.cumsum(rpm / 60 * 3) / SR
    pulses = np.diff(np.floor(phase), prepend=0) > 0
    y = np.convolve(pulses.astype(float) * (rpm / 850), resonator_ir([88, 176, 260], [0.012, 0.007, 0.005], [1, .5, .3]), mode="same")
    y[int(0.8 * SR):] *= np.linspace(1, 0, n - int(0.8 * SR))
    return y

def horn():
    t = np.arange(int(0.75 * SR)) / SR
    def tone(f):
        p = (np.sin(2 * np.pi * f * t) > -0.2).astype(float) - 0.4
        return circ_filter(p, lo=300, hi=3000)
    env = np.clip(t / 0.015, 0, 1) * np.clip((0.75 - t) / 0.05, 0, 1)
    return (tone(410) + 0.9 * tone(515)) * env

def thunk(low=90, mid=380, latch=True, length=0.35, weight=1.0):
    t = np.arange(int(length * SR)) / SR
    body = np.exp(-t / (0.05 * weight)) * np.sin(2 * np.pi * low * t)
    knock = 0.5 * np.exp(-t / 0.02) * np.sin(2 * np.pi * mid * t)
    y = body + knock
    if latch:
        click = circ_filter(rng.normal(0, 1, len(t)), lo=2000, hi=6000) * np.exp(-t / 0.004) * 0.5
        y += np.roll(click, int(0.012 * SR))
    return y

def door_open():
    t = np.arange(int(0.45 * SR)) / SR
    click = circ_filter(rng.normal(0, 1, len(t)), lo=1800, hi=5000) * np.exp(-t / 0.005)
    swoosh = circ_filter(rng.normal(0, 1, len(t)), lo=200, hi=1200) * np.exp(-((t - 0.18) / 0.09) ** 2) * 0.25
    return click + swoosh + 0.15 * thunk(140, 600, False, 0.45, 0.4)

def relay(freq):
    t = np.arange(int(0.06 * SR)) / SR
    return circ_filter(rng.normal(0, 1, len(t)), lo=1500, hi=6000) * np.exp(-t / 0.003) + \
        0.6 * np.exp(-t / 0.008) * np.sin(2 * np.pi * freq * t)

def shift():
    t = np.arange(int(0.18 * SR)) / SR
    return thunk(110, 520, False, 0.18, 0.5) * 0.8 + circ_filter(rng.normal(0, 1, len(t)), lo=600, hi=2500) * np.exp(-t / 0.02) * 0.3

def tire_squeal():
    n = int(1.5 * SR); t = np.arange(n) / SR
    vib = 1 + 0.03 * np.sin(2 * np.pi * 6 * t)               # 9 ciclos em 1,5 s: periódico
    phase = np.cumsum(1650 * vib) / SR
    phase *= np.round(phase[-1]) / phase[-1]
    tone = np.sin(2 * np.pi * phase) + 0.4 * np.sin(4 * np.pi * phase)
    hiss = circ_filter(rng.normal(0, 1, n), lo=1000, hi=3000)
    return 0.6 * tone + 0.8 * hiss / np.std(hiss) * 0.3

def crash(big):
    """Metal amassando: estalo inicial + ressonâncias metálicas inarmônicas + chiado de estilhaços."""
    dur = 1.1 if big else 0.55
    n = int(dur * SR); t = np.arange(n) / SR
    y = np.zeros(n)
    y += circ_filter(rng.normal(0, 1, n), lo=80, hi=5000) * np.exp(-t / (0.05 if big else 0.03)) * 1.2          # impacto
    for f, dcy, a in ((137, .18, 1.0), (311, .12, .7), (523, .09, .6), (911, .07, .45), (1490, .05, .35), (2330, .04, .25)):
        y += a * np.exp(-t / (dcy * (1.6 if big else 1.0))) * np.sin(2 * np.pi * f * (1 + 0.01 * rng.normal()) * t + rng.uniform(0, 6.28))
    y += 0.9 * np.exp(-t / (0.09 if big else 0.05)) * np.sin(2 * np.pi * (55 if big else 85) * t)                  # pancada grave
    for _ in range(14 if big else 5):                                                                                # rangidos/estalos tardios
        t0 = rng.uniform(0.05, dur * 0.8); i0 = int(t0 * SR); L = min(n - i0, int(0.05 * SR))
        tt = np.arange(L) / SR
        y[i0:i0 + L] += rng.uniform(0.15, 0.5) * np.exp(-tt / 0.008) * circ_filter(rng.normal(0, 1, L), lo=1500, hi=7000)
    return y

def glass_break():
    n = int(0.7 * SR); t = np.arange(n) / SR
    y = circ_filter(rng.normal(0, 1, n), lo=2500, hi=9000) * np.exp(-t / 0.04)
    for _ in range(40):
        t0 = rng.uniform(0.0, 0.5); i0 = int(t0 * SR); L = min(n - i0, int(0.03 * SR)); tt = np.arange(L) / SR
        y[i0:i0 + L] += rng.uniform(0.1, 0.5) * np.exp(-tt / 0.006) * np.sin(2 * np.pi * rng.uniform(3000, 8500) * tt)
    return y

def wind():
    n = int(2.0 * SR)
    return circ_filter(rng.normal(0, 1, n), lo=60, hi=900, slope=-0.5)

def main():
    for name, rpm in (("idle", 850), ("low", 2500), ("mid", 4500), ("high", 7000)):
        save(f"engine/v6_{name}_on", engine_layer(rpm, 1.0), loop=True)
        if name != "idle":
            save(f"engine/v6_{name}_off", engine_layer(rpm, 0.15), loop=True)
    save("engine/v6_start", starter()); save("engine/v6_stop", shutdown())
    save("vehicle/horn", horn())
    save("vehicle/door_open", door_open()); save("vehicle/door_close", thunk())
    save("vehicle/hood_open", thunk(70, 300, True, 0.5, 1.4) * 0.7); save("vehicle/hood_close", thunk(60, 260, True, 0.5, 1.8))
    save("vehicle/turn_tick", relay(2400)); save("vehicle/turn_tock", relay(1700))
    save("vehicle/gear_shift", shift())
    save("vehicle/tire_squeal", tire_squeal(), loop=True); save("vehicle/wind", wind(), loop=True)
    save("vehicle/crash_small", crash(False)); save("vehicle/crash_big", crash(True)); save("vehicle/glass_break", glass_break())
    total = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(OUT) for f in fs)
    print(f"OK: sons gerados em {OUT} ({total // 1024} KiB)")

if __name__ == "__main__":
    main()
