import tkinter as tk
import math, random, os, time, subprocess
from functools import lru_cache

DISPLAY = ":1"
ligamos_x11 = False


def _testa():
    try:
        t = tk.Tk()
        t.destroy()
        return True
    except tk.TclError:
        return False


def conectar_x11():
    global ligamos_x11
    os.environ["DISPLAY"] = DISPLAY
    if _testa():
        return True
    print("Ligando o Termux:X11, segura aí...")
    nul = subprocess.DEVNULL
    try:
        subprocess.Popen(["termux-x11", DISPLAY], stdout=nul, stderr=nul)
        ligamos_x11 = True
        time.sleep(2)
        subprocess.Popen(
            ["am", "start", "--user", "0", "-n",
             "com.termux.x11/com.termux.x11.MainActivity"],
            stdout=nul, stderr=nul)
    except FileNotFoundError:
        print("Não achei o comando termux-x11. Roda: pkg install termux-x11-nightly")
        return False
    for _ in range(40):
        time.sleep(0.5)
        if _testa():
            return True
    return False


if not conectar_x11():
    print("Não consegui conectar no display. Abre o app Termux:X11 na mão e roda de novo.")
    raise SystemExit(1)

# ---------- janela ----------
root = tk.Tk()
root.title("Mini Minecraft")
sw, sh = root.winfo_screenwidth(), root.winfo_screenheight()
root.geometry(f"{sw}x{sh}+0+0")
root.overrideredirect(True)
cv = tk.Canvas(root, bg="#87ceeb", highlightthickness=0)
cv.pack(fill="both", expand=True)
W, H = sw, sh


def on_cfg(e):
    global W, H
    W, H = e.width, e.height


cv.bind("<Configure>", on_cfg)

# ---------- cores dos blocos ----------
CORES = {
    "grama": ((60, 170, 70), (110, 75, 40)),
    "terra": ((120, 80, 45), (90, 60, 35)),
    "pedra": ((130, 130, 130), (100, 100, 100)),
    "areia": ((230, 210, 130), (200, 180, 110)),
    "madeira": ((150, 100, 50), (120, 80, 40)),
    "caos": ((230, 60, 220), (160, 40, 160)),
}
CORES_INV = ["grama", "terra", "pedra", "areia", "madeira", "caos"]
bloco_sel = 0

# ---------- config / estado ----------
SPEED = 6
DIST2 = 220
cfg = {"tam": 12, "seed": random.randint(1000, 9999), "sens": 1.0, "strafe": False}
estado = "menu"
blocks = {}      # (x,y,z) -> tipo
cubos = []
particulas = []  # [x,y,z,vx,vy,vz,vida,cor]
px, py, pz, yaw = 0.0, 5.0, 0.0, 0.0
FOV = 400
joy = [0.0, 0.0]
joy_active = False
joy_c = (0, 0)
look_last = None
keys = set()
botoes = []
last = time.time()
press_t = None
press_pos = None
pressed_block = None
caos_t = 0.0


@lru_cache(maxsize=None)
def shade(c, f):
    return "#%02x%02x%02x" % tuple(int(v * f) for v in c)


def cor_do(tipo, topo):
    return CORES[tipo][0] if topo else CORES[tipo][1]


def gerar_mundo():
    global cubos, px, py, pz, yaw, blocks
    rnd = random.Random(cfg["seed"])
    ox, oz = rnd.random() * 100, rnd.random() * 100
    n = cfg["tam"]
    blocks = {}
    topo = {}
    for x in range(-n, n):
        for z in range(-n, n):
            h = int(2 + 1.5 * math.sin((x + ox) * 0.3)
                    + 1.5 * math.cos((z + oz) * 0.27) + rnd.random() * 0.8)
            h = max(h, 0)
            topo[(x, z)] = h
            for y in range(h + 1):
                if y == h:
                    t = "areia" if h <= 1 else "grama"
                elif y > h - 3:
                    t = "terra"
                else:
                    t = "pedra"
                blocks[(x, y, z)] = t
            if rnd.random() < 0.03:
                for k in range(1, 4):
                    blocks[(x, h + k, z)] = "madeira"
    px, pz, yaw = 0.0, 0.0, 0.0
    py = topo[(0, 0)] + 3.0
    reconstruir()


def reconstruir():
    global cubos
    cubos = []
    for (x, y, z), t in blocks.items():
        fl = ((x + 1, y, z) not in blocks, (x - 1, y, z) not in blocks,
              (x, y, z + 1) not in blocks, (x, y, z - 1) not in blocks,
              (x, y + 1, z) not in blocks)
        if any(fl):
            cubos.append((x, y, z, t, fl))


def project(x, y, z, cs, sn):
    dx, dy, dz = x - px, y - py, z - pz
    rx = dx * cs - dz * sn
    rz = dx * sn + dz * cs
    if rz < 0.15:
        return None
    return (W / 2 + rx * FOV / rz, H / 2 - dy * FOV / rz)


def face(corners, col, cs, sn):
    pts = [project(*c, cs, sn) for c in corners]
    if None in pts:
        return
    cv.create_polygon([v for p in pts for v in p], fill=col, outline="")


def desenhar_mundo(cs, sn):
    cv.create_rectangle(0, 0, W, H, fill="#87ceeb", outline="")
    vis = [c for c in cubos if (c[0] - px) ** 2 + (c[2] - pz) ** 2 < DIST2]
    vis.sort(key=lambda c: -((c[0] - px) ** 2 + (c[2] - pz) ** 2))
    for x, y, z, t, fl in vis:
        tp, bt = y + 0.5, y - 0.5
        l, r = x - 0.5, x + 0.5
        n, f = z - 0.5, z + 0.5
        if fl[0] and px > r:
            face([(r, bt, n), (r, bt, f), (r, tp, f), (r, tp, n)], shade(cor_do(t, False), 0.7), cs, sn)
        if fl[1] and px < l:
            face([(l, bt, n), (l, bt, f), (l, tp, f), (l, tp, n)], shade(cor_do(t, False), 0.6), cs, sn)
        if fl[2] and pz > f:
            face([(l, bt, f), (r, bt, f), (r, tp, f), (l, tp, f)], shade(cor_do(t, False), 0.8), cs, sn)
        if fl[3] and pz < n:
            face([(l, bt, n), (r, bt, n), (r, tp, n), (l, tp, n)], shade(cor_do(t, False), 0.5), cs, sn)
        if fl[4] and py > tp:
            face([(l, tp, n), (r, tp, n), (r, tp, f), (l, tp, f)], shade(cor_do(t, True), 1.0), cs, sn)
    desenhar_particulas(cs, sn)


def desenhar_particulas(cs, sn):
    for p in particulas:
        pr = project(p[0], p[1], p[2], cs, sn)
        if pr:
            s = max(2, int(p[6] * 12))
            cv.create_rectangle(pr[0] - s, pr[1] - s, pr[0] + s, pr[1] + s,
                                fill=p[7], outline="")


def explosao(x, y, z, t):
    cor = CORES[t][0]
    for _ in range(14):
        particulas.append([x, y, z,
                           random.uniform(-3, 3), random.uniform(1, 5), random.uniform(-3, 3),
                           1.0, shade(cor, random.uniform(0.6, 1.2))])


def atualizar_particulas(dt):
    for p in particulas:
        p[0] += p[3] * dt
        p[1] += p[4] * dt
        p[2] += p[5] * dt
        p[4] -= 9 * dt
        p[6] -= dt * 1.5
    particulas[:] = [p for p in particulas if p[6] > 0]


def caos_aleatorio():
    # de vez em quando um bloco "caos" aparece do nada perto do jogador
    ang = random.uniform(0, math.tau)
    d = random.uniform(4, 9)
    x = round(px + math.cos(ang) * d)
    z = round(pz + math.sin(ang) * d)
    y = int(py) + 4
    blocks[(x, y, z)] = "caos"
    reconstruir()


def mira_bloco(cs, sn):
    # raio simples pra achar o bloco na frente do jogador
    dx, dy, dz = -sn, 0, cs
    dx, dy, dz = -sn * 0 + math.sin(yaw) * 0, 0, 1
    fx, fz = math.sin(yaw), math.cos(yaw)
    fy = -0.4
    x, y, z = px, py, pz
    anterior = None
    for _ in range(60):
        x += fx * 0.1
        y += fy * 0.1
        z += fz * 0.1
        bx, by, bz = round(x), round(y), round(z)
        if (bx, by, bz) in blocks:
            return (bx, by, bz), anterior
        anterior = (bx, by, bz)
    return None, None


def quebrar(pos):
    global pressed_block
    t = blocks.pop(pos, None)
    if t:
        explosao(pos[0], pos[1], pos[2], t)
        reconstruir()


def colocar(pos):
    if pos and pos not in blocks:
        blocks[pos] = CORES_INV[bloco_sel]
        reconstruir()


def desenhar_hud_mira():
    cv.create_oval(W / 2 - 5, H / 2 - 5, W / 2 + 5, H / 2 + 5, outline="white", width=2)


def desenhar_hud_inv():
    x0 = W * 0.3
    y0 = H - H * 0.1 - 10
    bw = W * 0.06
    for i, nome in enumerate(CORES_INV):
        xi = x0 + i * (bw + 8)
        borda = "#ffff00" if i == bloco_sel else "#e6e6e6"
        cv.create_rectangle(xi, y0, xi + bw, y0 + H * 0.09,
                            fill=shade(CORES[nome][0], 1.0), outline=borda, width=3)


# ---------- ações ----------
def jogar():
    global estado
    estado = "jogo"


def abrir_menu():
    global estado
    estado = "menu"


def ciclo_tam():
    tams = [8, 12, 16, 24]
    cfg["tam"] = tams[(tams.index(cfg["tam"]) + 1) % len(tams)] if cfg["tam"] in tams else 12
    gerar_mundo()


def nova_seed():
    cfg["seed"] = random.randint(1000, 9999)
    gerar_mundo()


def ciclo_sens():
    s = [0.5, 1.0, 1.5, 2.0, 3.0]
    cfg["sens"] = s[(s.index(cfg["sens"]) + 1) % len(s)]


def troca_strafe():
    cfg["strafe"] = not cfg["strafe"]


def troca_bloco():
    global bloco_sel
    bloco_sel = (bloco_sel + 1) % len(CORES_INV)


def sair():
    root.destroy()
    if ligamos_x11:
        subprocess.run(["pkill", "-f", "termux-x11"])
    raise SystemExit


def botao(x0, y0, x1, y1, texto, acao, tam=0.045):
    cv.create_rectangle(x0, y0, x1, y1, fill="#2b2b2b", outline="#e6e6e6", width=3)
    cv.create_text((x0 + x1) / 2, (y0 + y1) / 2, text=texto, fill="white",
                   font=("Helvetica", max(10, int(H * tam)), "bold"))
    botoes.append((x0, y0, x1, y1, acao))


def desenhar_menu():
    cv.create_text(W / 2, H * 0.14, text="MINI MINECRAFT", fill="white",
                   font=("Helvetica", int(H * 0.09), "bold"))
    bw, bh, gap = min(W * 0.7, H * 1.2), H * 0.1, H * 0.025
    x0 = (W - bw) / 2
    y = H * 0.3
    itens = [
        ("JOGAR", jogar),
        (f"MUNDO: {cfg['tam'] * 2}x{cfg['tam'] * 2}", ciclo_tam),
        (f"NOVA SEED ({cfg['seed']})", nova_seed),
        (f"SENSIBILIDADE: {cfg['sens']}x", ciclo_sens),
        ("SAIR", sair),
    ]
    for texto, acao in itens:
        botao(x0, y, x0 + bw, y + bh, texto, acao)
        y += bh + gap


def desenhar_hud():
    bw, bh = W * 0.16, H * 0.1
    botao(W - bw - 10, 10, W - 10, 10 + bh, "MENU", abrir_menu, 0.04)
    txt = "LADO: ON" if cfg["strafe"] else "LADO: OFF"
    botao(W - bw * 1.3 - 10, H - bh - 10, W - 10, H - 10, txt, troca_strafe, 0.04)
    botao(10, 10, 10 + bw, 10 + bh, f"BLOCO: {CORES_INV[bloco_sel]}", troca_bloco, 0.035)
    jr = H * 0.13
    cx, cy = joy_c if joy_active else (W * 0.15, H * 0.75)
    cv.create_oval(cx - jr, cy - jr, cx + jr, cy + jr, outline="#202020", width=3)
    kx, ky = cx + joy[0] * jr, cy + joy[1] * jr
    cv.create_oval(kx - jr * 0.4, ky - jr * 0.4, kx + jr * 0.4, ky + jr * 0.4,
                   fill="#e6e6e6", outline="")
    desenhar_hud_mira()
    desenhar_hud_inv()


# ---------- input ----------
def on_press(e):
    global joy_active, joy_c, look_last, press_t, press_pos
    for x0, y0, x1, y1, acao in botoes:
        if x0 <= e.x <= x1 and y0 <= e.y <= y1:
            acao()
            return
    if estado != "jogo":
        return
    if e.x < W * 0.45:
        joy_active = True
        joy_c = (e.x, e.y)
    else:
        look_last = (e.x, e.y)
        press_t = time.time()
        press_pos = (e.x, e.y)


def on_drag(e):
    global joy, look_last, yaw
    if estado != "jogo":
        return
    if joy_active:
        jr = H * 0.13
        vx, vy = (e.x - joy_c[0]) / jr, (e.y - joy_c[1]) / jr
        mag = math.hypot(vx, vy)
        if mag > 1:
            vx, vy = vx / mag, vy / mag
        if mag < 0.15:
            vx = vy = 0.0
        joy = [vx, vy]
    elif look_last:
        yaw += (e.x - look_last[0]) * 0.006 * cfg["sens"]
        look_last = (e.x, e.y)


def on_release(e):
    global joy_active, joy, look_last, press_t
    if estado == "jogo" and press_t and not joy_active:
        dur = time.time() - press_t
        if press_pos and math.hypot(e.x - press_pos[0], e.y - press_pos[1]) < 20:
            alvo, vizinho = mira_bloco(0, 0)
            if dur > 0.45:
                quebrar(alvo)
            else:
                colocar(vizinho)
    press_t = None
    joy_active = False
    joy = [0.0, 0.0]
    look_last = None


def key_down(e):
    global estado
    k = e.keysym.lower()
    if k == "escape":
        estado = "menu" if estado == "jogo" else "jogo"
    if k == "q":
        alvo, _ = mira_bloco(0, 0)
        quebrar(alvo)
    if k == "e":
        _, viz = mira_bloco(0, 0)
        colocar(viz)
    keys.add(k)


def key_up(e):
    keys.discard(e.keysym.lower())


cv.bind("<ButtonPress-1>", on_press)
cv.bind("<B1-Motion>", on_drag)
cv.bind("<ButtonRelease-1>", on_release)
root.bind("<KeyPress>", key_down)
root.bind("<KeyRelease>", key_up)
root.focus_force()


# ---------- loop ----------
def tick():
    global px, pz, yaw, last, FOV, caos_t
    agora = time.time()
    dt = min(agora - last, 0.1)
    last = agora
    FOV = H * 0.7
    botoes.clear()
    cv.delete("all")

    if estado == "menu":
        yaw += 0.25 * dt
        desenhar_mundo(math.cos(yaw), math.sin(yaw))
        desenhar_menu()
    else:
        fwd = -joy[1] + (("w" in keys) - ("s" in keys))
        lado = ("d" in keys) - ("a" in keys)
        giro = ("right" in keys) - ("left" in keys)
        if cfg["strafe"]:
            lado += joy[0]
        else:
            giro += joy[0]
        yaw += giro * 2.0 * cfg["sens"] * dt
        cs, sn = math.cos(yaw), math.sin(yaw)
        px += (sn * fwd + cs * lado) * SPEED * dt
        pz += (cs * fwd - sn * lado) * SPEED * dt

        caos_t += dt
        if caos_t > 8:
            caos_t = 0
            if random.random() < 0.5:
                caos_aleatorio()

        atualizar_particulas(dt)
        desenhar_mundo(cs, sn)
        desenhar_hud()

    root.after(16, tick)


gerar_mundo()
tick()
root.mainloop()
