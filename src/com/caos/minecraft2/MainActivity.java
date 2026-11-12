package com.caos.minecraft2;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {
    GLSurfaceView glv;
    Game game;
    Hud hud;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new Game();
        glv = new GLSurfaceView(this);
        glv.setRenderer(game);
        hud = new Hud(this, game);
        FrameLayout root = new FrameLayout(this);
        root.addView(glv, new FrameLayout.LayoutParams(-1, -1));
        root.addView(hud, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    protected void onPause() { super.onPause(); glv.onPause(); }
    @Override
    protected void onResume() { super.onResume(); glv.onResume(); }

    // =====================================================================
    static class Game implements GLSurfaceView.Renderer {
        static final int SX = 64, SY = 32, SZ = 64, SEA = 8;
        static final int MAXF = 80000, MP = 400;
        static final float HW = 0.3f, PH = 1.8f, EYE = 1.62f, SPEED = 5.2f;
        // {topo, lado}
        static final int NB = 13;
        static final int[][] COR = {
            {0, 0},
            {0x3FAE46, 0x7A5230}, // grama
            {0x7A5230, 0x6A4526}, // terra
            {0x8C8C8C, 0x777777}, // pedra
            {0xE6D58A, 0xD4C377}, // areia
            {0xB48850, 0x9A6B35}, // madeira
            {0x2E8B3A, 0x277A32}, // folha
            {0xE63CDC, 0xA02AA0}, // caos
            {0xF2F7FF, 0xDDE8F2}, // neve
            {0x3A3A3A, 0x303030}, // carvao
            {0xC9A58C, 0xB8937A}, // ferro
            {0xF2D13A, 0xDDBB2A}, // ouro
            {0x3EE0E0, 0x2CC4C4}, // diamante
            {0x6FA83A, 0x5C8F2E}, // cacto
            {0x2F6FD6, 0x2A62C9}  // agua
        };
        static final String[] NOMES = {"GRAMA", "TERRA", "PEDRA", "AREIA", "MADEIRA", "FOLHA", "CAOS", "NEVE", "CARVAO", "FERRO", "OURO", "DIAMANTE", "CACTO"};
        static final float[][] CORN = {
            {1,0,0, 1,1,0, 1,1,1, 1,0,1},
            {0,0,1, 0,1,1, 0,1,0, 0,0,0},
            {0,1,0, 0,1,1, 1,1,1, 1,1,0},
            {0,0,0, 1,0,0, 1,0,1, 0,0,1},
            {0,0,1, 1,0,1, 1,1,1, 0,1,1},
            {1,0,0, 0,0,0, 0,1,0, 1,1,0}
        };
        static final float[] SH = {0.8f, 0.7f, 1.0f, 0.5f, 0.9f, 0.75f};
        static final int[] ORDER = {0, 1, 2, 0, 2, 3};

        final byte[] blocks = new byte[SX * SY * SZ];
        final FloatBuffer vbuf = ByteBuffer.allocateDirect(MAXF * 6 * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        final ByteBuffer cbuf = ByteBuffer.allocateDirect(MAXF * 6 * 4).order(ByteOrder.nativeOrder());
        final FloatBuffer pvb = ByteBuffer.allocateDirect(MP * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        final ByteBuffer pcb = ByteBuffer.allocateDirect(MP * 4).order(ByteOrder.nativeOrder());
        int nv = 0;
        boolean dirty = true, inited = false;

        final float[] ppos = new float[MP * 3], pvel = new float[MP * 3], plife = new float[MP];
        final int[] pcol = new int[MP];
        int pnext = 0;

        // entrada (escrita pela UI, lida pela GL)
        volatile float joyX, joyY, yaw = 0f, pitch = 10f;
        volatile boolean jumpHeld;
        volatile int sel = 0;
        final ConcurrentLinkedQueue<Integer> acts = new ConcurrentLinkedQueue<Integer>();

        float px, py, pz, vy;
        boolean onGround;
        long last;
        final Random rnd = new Random();
        final int[] hit = new int[6];

        boolean fall;
        int fx, fy, fz;
        float fallT, caosT;

        static int idx(int x, int y, int z) { return (y * SZ + z) * SX + x; }
        static boolean inb(int x, int y, int z) { return x >= 0 && x < SX && y >= 0 && y < SY && z >= 0 && z < SZ; }
        static int clamp(int v, int a, int b) { return v < a ? a : (v > b ? b : v); }

        void look(float dx, float dy) {
            float ny = yaw + dx;
            if (ny > 360f) ny -= 360f;
            if (ny < -360f) ny += 360f;
            yaw = ny;
            float np = pitch + dy;
            if (np > 89f) np = 89f;
            if (np < -89f) np = -89f;
            pitch = np;
        }

        int topY(int x, int z) {
            for (int y = SY - 1; y >= 0; y--) if (blocks[idx(x, y, z)] != 0) return y;
            return -1;
        }

        boolean lastUw;

        static float hash2(int x, int z, int s) {
            int h = x * 374761393 + z * 668265263 + s * 1274126177;
            h = (h ^ (h >>> 13)) * 1274126177;
            h ^= (h >>> 16);
            return (h & 0xFFFF) / 65535f;
        }

        static float vnoise(float x, float z, int s) {
            int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
            float fx = x - x0, fz = z - z0;
            fx = fx * fx * (3f - 2f * fx);
            fz = fz * fz * (3f - 2f * fz);
            float a = hash2(x0, z0, s), b = hash2(x0 + 1, z0, s);
            float c = hash2(x0, z0 + 1, s), d = hash2(x0 + 1, z0 + 1, s);
            float top = a + (b - a) * fx, bot = c + (d - c) * fx;
            return top + (bot - top) * fz;
        }

        static float fbm(float x, float z, int s, int oct) {
            float sum = 0f, amp = 1f, f = 1f, tot = 0f;
            for (int i = 0; i < oct; i++) {
                sum += vnoise(x * f, z * f, s + i * 31) * amp;
                tot += amp;
                amp *= 0.5f;
                f *= 2f;
            }
            return sum / tot;
        }

        boolean water(float x, float y, float z) {
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            return inb(bx, by, bz) && blocks[idx(bx, by, bz)] == 14;
        }

        void carve(int cx, int cy, int cz, float r) {
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dy = -ri; dy <= ri; dy++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        if (dx * dx + dy * dy + dz * dz > r * r) continue;
                        int x = cx + dx, y = cy + dy, z = cz + dz;
                        if (!inb(x, y, z) || y < 2) continue;
                        int t = blocks[idx(x, y, z)];
                        if (t == 0 || t == 14) continue;
                        blocks[idx(x, y, z)] = 0;
                    }
                }
            }
        }

        void ore(int type, int veins, int maxY, int size) {
            for (int v = 0; v < veins; v++) {
                int x = rnd.nextInt(SX), y = 2 + rnd.nextInt(maxY - 1), z = rnd.nextInt(SZ);
                int n = size / 2 + rnd.nextInt(size);
                for (int i = 0; i < n; i++) {
                    if (inb(x, y, z) && blocks[idx(x, y, z)] == 3) blocks[idx(x, y, z)] = (byte) type;
                    x += rnd.nextInt(3) - 1;
                    y += rnd.nextInt(3) - 1;
                    z += rnd.nextInt(3) - 1;
                }
            }
        }

        void tree(int x, int h, int z) {
            int th = 4 + rnd.nextInt(3);
            if (h + th + 2 >= SY) return;
            for (int k = 1; k <= th; k++) blocks[idx(x, h + k, z)] = 5;
            for (int dy = th - 2; dy <= th + 1; dy++) {
                int r = (dy >= th) ? 1 : 2;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (r == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                        int ax = x + dx, ay = h + dy, az = z + dz;
                        if (inb(ax, ay, az) && blocks[idx(ax, ay, az)] == 0) blocks[idx(ax, ay, az)] = 6;
                    }
                }
            }
        }

        void generate() {
            Arrays.fill(blocks, (byte) 0);
            int seed = rnd.nextInt(100000);
            int[] hmap = new int[SX * SZ];
            byte[] topT = new byte[SX * SZ];
            byte[] bio = new byte[SX * SZ];

            // terreno, biomas, rios e agua
            for (int x = 0; x < SX; x++) {
                for (int z = 0; z < SZ; z++) {
                    float e = (fbm(x * 0.028f, z * 0.028f, seed, 4) - 0.5f) * 2.6f;
                    float m = (fbm(x * 0.018f, z * 0.018f, seed + 99, 3) - 0.5f) * 2.6f;
                    float h = SEA + 3 + e * 7f + Math.max(0f, m - 0.15f) * 22f;
                    float rv = Math.abs(fbm(x * 0.022f, z * 0.022f, seed + 7, 2) - 0.5f) * 2.6f;
                    if (rv < 0.07f) {
                        float k = 1f - rv / 0.07f;
                        h += ((SEA - 2) - h) * k;
                    }
                    float tp = (fbm(x * 0.02f, z * 0.02f, seed + 3, 2) - 0.5f) * 2.6f;
                    float hm = (fbm(x * 0.02f, z * 0.02f, seed + 5, 2) - 0.5f) * 2.6f;
                    int hi = clamp(Math.round(h), 2, SY - 7);
                    int top, sub, b;
                    if (hi <= SEA + 1) { top = 4; sub = 4; b = 5; }
                    else if (hi >= SEA + 10) { top = (hi >= SEA + 14) ? 8 : 3; sub = 3; b = 4; }
                    else if (tp < -0.35f) { top = 8; sub = 2; b = 2; }
                    else if (tp > 0.35f && hm < 0.15f) { top = 4; sub = 4; b = 3; }
                    else { top = 1; sub = 2; b = (hm > 0.2f) ? 1 : 0; }
                    for (int y = 0; y <= hi; y++) {
                        int t;
                        if (y == hi) t = top;
                        else if (y >= hi - 3) t = sub;
                        else t = 3;
                        blocks[idx(x, y, z)] = (byte) t;
                    }
                    for (int y = hi + 1; y <= SEA; y++) blocks[idx(x, y, z)] = 14;
                    hmap[x * SZ + z] = hi;
                    topT[x * SZ + z] = (byte) top;
                    bio[x * SZ + z] = (byte) b;
                }
            }

            // cavernas (minhocas que furam o mundo)
            for (int w = 0; w < 16; w++) {
                float wx = 4 + rnd.nextFloat() * (SX - 8);
                float wz = 4 + rnd.nextFloat() * (SZ - 8);
                int hx = hmap[(int) wx * SZ + (int) wz];
                if (hx < 11) continue;
                float wy = 4 + rnd.nextFloat() * (hx - 8);
                float ang = rnd.nextFloat() * 6.2832f;
                float pit = (rnd.nextFloat() - 0.5f) * 0.5f;
                float rad = 1.3f + rnd.nextFloat();
                int len = 70 + rnd.nextInt(90);
                for (int st = 0; st < len; st++) {
                    float cp = (float) Math.cos(pit);
                    wx += (float) Math.cos(ang) * cp;
                    wz += (float) Math.sin(ang) * cp;
                    wy += (float) Math.sin(pit);
                    ang += (rnd.nextFloat() - 0.5f) * 0.6f;
                    pit = pit * 0.9f + (rnd.nextFloat() - 0.5f) * 0.35f;
                    if (wx < 2 || wx > SX - 3 || wz < 2 || wz > SZ - 3 || wy < 3) break;
                    carve((int) wx, (int) wy, (int) wz, rad);
                }
            }

            // minerios
            ore(9, 70, 20, 7);
            ore(10, 45, 14, 5);
            ore(11, 18, 8, 4);
            ore(12, 9, 5, 3);

            // arvores e cactos
            for (int x = 2; x < SX - 2; x++) {
                for (int z = 2; z < SZ - 2; z++) {
                    int hi = hmap[x * SZ + z];
                    if (hi <= SEA + 1 || blocks[idx(x, hi, z)] != topT[x * SZ + z]) continue;
                    if (blocks[idx(x, hi + 1, z)] != 0) continue;
                    int bb = bio[x * SZ + z];
                    float r = rnd.nextFloat();
                    if (bb == 0 && r < 0.008f) tree(x, hi, z);
                    else if (bb == 1 && r < 0.06f) tree(x, hi, z);
                    else if (bb == 2 && r < 0.02f) tree(x, hi, z);
                    else if (bb == 3 && r < 0.015f) {
                        int ch = 2 + rnd.nextInt(2);
                        for (int k = 1; k <= ch; k++) blocks[idx(x, hi + k, z)] = 13;
                    }
                }
            }

            // spawn em terra firme, o mais perto do centro
            int bx = SX / 2, bz = SZ / 2, fxp = -1, fzp = -1;
            for (int r = 0; r < 24 && fxp < 0; r++) {
                for (int dx = -r; dx <= r && fxp < 0; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        int ax = bx + dx, az = bz + dz;
                        if (ax < 1 || az < 1 || ax >= SX - 1 || az >= SZ - 1) continue;
                        int ty = topY(ax, az);
                        if (ty <= SEA) continue;
                        int tt = blocks[idx(ax, ty, az)];
                        if (tt == 14 || tt == 6 || tt == 5 || tt == 13) continue;
                        fxp = ax;
                        fzp = az;
                        break;
                    }
                }
            }
            if (fxp < 0) { fxp = bx; fzp = bz; }
            px = fxp + 0.5f;
            pz = fzp + 0.5f;
            py = topY(fxp, fzp) + 1f;
            vy = 0f;
            fall = false;
            caosT = 0f;
            Arrays.fill(plife, 0f);
            dirty = true;
        }

        // ---------- mesh ----------
        int g(int x, int y, int z) {
            if (y < 0) return 1;
            if (!inb(x, y, z)) return 0;
            return blocks[idx(x, y, z)];
        }

        boolean vis(int t, int n) {
            return n == 0 || (n == 14 && t != 14);
        }

        void face(int x, int y, int z, int d, int t) {
            if (nv + 6 > MAXF * 6) return;
            int c = (d == 2) ? COR[t][0] : COR[t][1];
            float hs = 0.92f + 0.08f * (((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 7) / 7f;
            float f = SH[d] * hs;
            byte r = (byte) (((c >> 16) & 255) * f);
            byte gg = (byte) (((c >> 8) & 255) * f);
            byte bb = (byte) ((c & 255) * f);
            float[] k = CORN[d];
            for (int i = 0; i < 6; i++) {
                int o = ORDER[i] * 3;
                vbuf.put(x + k[o]);
                vbuf.put(y + k[o + 1]);
                vbuf.put(z + k[o + 2]);
                cbuf.put(r);
                cbuf.put(gg);
                cbuf.put(bb);
                cbuf.put((byte) 255);
            }
            nv += 6;
        }

        void buildMesh() {
            vbuf.clear();
            cbuf.clear();
            nv = 0;
            for (int y = 0; y < SY; y++) {
                for (int z = 0; z < SZ; z++) {
                    for (int x = 0; x < SX; x++) {
                        int t = blocks[idx(x, y, z)];
                        if (t == 0) continue;
                        if (vis(t, g(x + 1, y, z))) face(x, y, z, 0, t);
                        if (vis(t, g(x - 1, y, z))) face(x, y, z, 1, t);
                        if (vis(t, g(x, y + 1, z))) face(x, y, z, 2, t);
                        if (vis(t, g(x, y - 1, z))) face(x, y, z, 3, t);
                        if (vis(t, g(x, y, z + 1))) face(x, y, z, 4, t);
                        if (vis(t, g(x, y, z - 1))) face(x, y, z, 5, t);
                    }
                }
            }
            vbuf.flip();
            cbuf.flip();
        }

        // ---------- fisica ----------
        boolean solid(int x, int y, int z) {
            if (y < 0) return true;
            if (y >= SY) return false;
            if (x < 0 || x >= SX || z < 0 || z >= SZ) return true;
            return blocks[idx(x, y, z)] != 0 && blocks[idx(x, y, z)] != 14;
        }

        boolean collides(float x, float y, float z) {
            int x0 = (int) Math.floor(x - HW), x1 = (int) Math.floor(x + HW - 0.0001f);
            int y0 = (int) Math.floor(y), y1 = (int) Math.floor(y + PH - 0.0001f);
            int z0 = (int) Math.floor(z - HW), z1 = (int) Math.floor(z + HW - 0.0001f);
            for (int cx = x0; cx <= x1; cx++)
                for (int cy = y0; cy <= y1; cy++)
                    for (int cz = z0; cz <= z1; cz++)
                        if (solid(cx, cy, cz)) return true;
            return false;
        }

        boolean overlapsPlayer(int cx, int cy, int cz) {
            return px - HW < cx + 1 && px + HW > cx && py < cy + 1 && py + PH > cy
                    && pz - HW < cz + 1 && pz + HW > cz;
        }

        void updatePlayer(float dt) {
            float jx = joyX, jy = joyY;
            double yr = Math.toRadians(yaw);
            float fxx = (float) Math.sin(yr), fzz = (float) -Math.cos(yr);
            float rx = (float) Math.cos(yr), rz = (float) Math.sin(yr);
            float fw = -jy, st = jx;
            boolean sw = water(px, py + 0.2f, pz);
            float sp = sw ? SPEED * 0.6f : SPEED;
            float mx = (fxx * fw + rx * st) * sp * dt;
            float mz = (fzz * fw + rz * st) * sp * dt;
            if (sw) {
                vy -= 7f * dt;
                if (vy < -3f) vy = -3f;
                if (jumpHeld) vy = water(px, py + 1.0f, pz) ? 4.2f : 8.4f;
            } else {
                vy -= 26f * dt;
                if (vy < -40f) vy = -40f;
                if (jumpHeld && onGround) vy = 8.6f;
            }
            float my = vy * dt;
            onGround = false;
            float big = Math.max(Math.abs(mx), Math.max(Math.abs(my), Math.abs(mz)));
            int steps = (int) Math.ceil(big / 0.35f);
            if (steps < 1) steps = 1;
            float sx = mx / steps, sy = my / steps, sz = mz / steps;
            for (int i = 0; i < steps; i++) {
                px += sx;
                if (collides(px, py, pz)) px -= sx;
                pz += sz;
                if (collides(px, py, pz)) pz -= sz;
                py += sy;
                if (collides(px, py, pz)) {
                    if (sy < 0) {
                        py = (float) Math.floor(py) + 1f;
                        onGround = true;
                    } else {
                        py = (float) Math.floor(py + PH) - PH - 0.001f;
                    }
                    vy = 0f;
                    sy = 0f;
                }
            }
        }

        // ---------- acoes ----------
        boolean raycast() {
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            float dx = (float) (Math.sin(yr) * Math.cos(pr));
            float dy = (float) (-Math.sin(pr));
            float dz = (float) (-Math.cos(yr) * Math.cos(pr));
            float ex = px, ey = py + EYE, ez = pz;
            int lx = (int) Math.floor(ex), ly = (int) Math.floor(ey), lz = (int) Math.floor(ez);
            for (float s = 0.05f; s < 6f; s += 0.04f) {
                int cx = (int) Math.floor(ex + dx * s);
                int cy = (int) Math.floor(ey + dy * s);
                int cz = (int) Math.floor(ez + dz * s);
                if (cx == lx && cy == ly && cz == lz) continue;
                if (inb(cx, cy, cz) && blocks[idx(cx, cy, cz)] != 0 && blocks[idx(cx, cy, cz)] != 14) {
                    hit[0] = cx; hit[1] = cy; hit[2] = cz;
                    hit[3] = lx; hit[4] = ly; hit[5] = lz;
                    return true;
                }
                lx = cx; ly = cy; lz = cz;
            }
            return false;
        }

        void doBreak() {
            if (!raycast()) return;
            int x = hit[0], y = hit[1], z = hit[2];
            if (y <= 0) return;
            int t = blocks[idx(x, y, z)];
            blocks[idx(x, y, z)] = 0;
            dirty = true;
            for (int i = 0; i < 12; i++) spawn(x + 0.5f, y + 0.5f, z + 0.5f, COR[t][0]);
        }

        void doPlace() {
            if (!raycast()) return;
            int x = hit[3], y = hit[4], z = hit[5];
            if (!inb(x, y, z) || (blocks[idx(x, y, z)] != 0 && blocks[idx(x, y, z)] != 14) || overlapsPlayer(x, y, z)) return;
            blocks[idx(x, y, z)] = (byte) (sel + 1);
            dirty = true;
        }

        void spawn(float x, float y, float z, int col) {
            int i = pnext;
            pnext = (pnext + 1) % MP;
            ppos[i * 3] = x + (rnd.nextFloat() - 0.5f) * 0.6f;
            ppos[i * 3 + 1] = y + (rnd.nextFloat() - 0.5f) * 0.6f;
            ppos[i * 3 + 2] = z + (rnd.nextFloat() - 0.5f) * 0.6f;
            pvel[i * 3] = (rnd.nextFloat() - 0.5f) * 6f;
            pvel[i * 3 + 1] = rnd.nextFloat() * 5f + 1f;
            pvel[i * 3 + 2] = (rnd.nextFloat() - 0.5f) * 6f;
            plife[i] = 0.5f + rnd.nextFloat() * 0.7f;
            float f = 0.7f + rnd.nextFloat() * 0.45f;
            int r = Math.min(255, (int) (((col >> 16) & 255) * f));
            int gg = Math.min(255, (int) (((col >> 8) & 255) * f));
            int bb = Math.min(255, (int) ((col & 255) * f));
            pcol[i] = (r << 16) | (gg << 8) | bb;
        }

        void explode(int cx, int cy, int cz) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx * dx + dy * dy + dz * dz > 5) continue;
                        int x = cx + dx, y = cy + dy, z = cz + dz;
                        if (!inb(x, y, z) || y <= 0) continue;
                        int t = blocks[idx(x, y, z)];
                        if (t == 0) continue;
                        blocks[idx(x, y, z)] = 0;
                        spawn(x + 0.5f, y + 0.5f, z + 0.5f, COR[t][0]);
                        spawn(x + 0.5f, y + 0.5f, z + 0.5f, COR[7][0]);
                    }
                }
            }
            dirty = true;
        }

        void updateChaos(float dt) {
            if (fall) {
                fallT += dt;
                while (fallT > 0.06f && fall) {
                    fallT -= 0.06f;
                    if (blocks[idx(fx, fy, fz)] != 7) { fall = false; break; }
                    int ny = fy - 1;
                    if (ny < 0 || blocks[idx(fx, ny, fz)] != 0 || overlapsPlayer(fx, ny, fz)) {
                        explode(fx, fy, fz);
                        fall = false;
                    } else {
                        blocks[idx(fx, fy, fz)] = 0;
                        blocks[idx(fx, ny, fz)] = 7;
                        fy = ny;
                        dirty = true;
                    }
                }
            } else {
                caosT += dt;
                if (caosT > 10f) {
                    caosT = 0f;
                    if (rnd.nextFloat() < 0.7f) {
                        int x = clamp((int) px + rnd.nextInt(17) - 8, 1, SX - 2);
                        int z = clamp((int) pz + rnd.nextInt(17) - 8, 1, SZ - 2);
                        fx = x; fy = SY - 2; fz = z;
                        if (blocks[idx(fx, fy, fz)] == 0) {
                            blocks[idx(fx, fy, fz)] = 7;
                            fall = true;
                            fallT = 0f;
                            dirty = true;
                        }
                    }
                }
            }
        }

        void drawParticles(GL10 gl, float dt) {
            int n = 0;
            pvb.clear();
            pcb.clear();
            for (int i = 0; i < MP; i++) {
                if (plife[i] <= 0f) continue;
                plife[i] -= dt;
                if (plife[i] <= 0f) continue;
                pvel[i * 3 + 1] -= 16f * dt;
                ppos[i * 3] += pvel[i * 3] * dt;
                ppos[i * 3 + 1] += pvel[i * 3 + 1] * dt;
                ppos[i * 3 + 2] += pvel[i * 3 + 2] * dt;
                pvb.put(ppos[i * 3]);
                pvb.put(ppos[i * 3 + 1]);
                pvb.put(ppos[i * 3 + 2]);
                int c = pcol[i];
                pcb.put((byte) ((c >> 16) & 255));
                pcb.put((byte) ((c >> 8) & 255));
                pcb.put((byte) (c & 255));
                pcb.put((byte) 255);
                n++;
            }
            if (n == 0) return;
            pvb.flip();
            pcb.flip();
            gl.glPointSize(10f);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, pvb);
            gl.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, 0, pcb);
            gl.glDrawArrays(GL10.GL_POINTS, 0, n);
        }

        // ---------- renderer ----------
        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig c) {
            gl.glClearColor(0.53f, 0.81f, 0.92f, 1f);
            gl.glEnable(GL10.GL_DEPTH_TEST);
            gl.glEnable(GL10.GL_FOG);
            gl.glFogf(GL10.GL_FOG_MODE, GL10.GL_LINEAR);
            gl.glFogf(GL10.GL_FOG_START, 26f);
            lastUw = false;
            gl.glFogf(GL10.GL_FOG_END, 62f);
            gl.glFogfv(GL10.GL_FOG_COLOR, new float[]{0.53f, 0.81f, 0.92f, 1f}, 0);
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
            gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);
            if (!inited) {
                generate();
                inited = true;
            }
            dirty = true;
            last = System.nanoTime();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int w, int h) {
            gl.glViewport(0, 0, w, h);
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            float near = 0.12f, far = 90f;
            float top = near * 0.7f;
            float right = top * ((float) w / (float) h);
            gl.glFrustumf(-right, right, -top, top, near, far);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            long now = System.nanoTime();
            float dt = (now - last) / 1000000000f;
            last = now;
            if (dt > 0.05f) dt = 0.05f;
            if (dt < 0f) dt = 0f;

            Integer a;
            while ((a = acts.poll()) != null) {
                if (a == 0) doBreak();
                else if (a == 1) doPlace();
                else if (a == 2) generate();
            }
            updatePlayer(dt);
            updateChaos(dt);
            if (dirty) {
                buildMesh();
                dirty = false;
            }

            boolean uw = water(px, py + EYE, pz);
            if (uw != lastUw) {
                lastUw = uw;
                float[] fc = uw ? new float[]{0.12f, 0.32f, 0.70f, 1f} : new float[]{0.53f, 0.81f, 0.92f, 1f};
                gl.glFogfv(GL10.GL_FOG_COLOR, fc, 0);
                gl.glFogf(GL10.GL_FOG_START, uw ? 0.5f : 26f);
                gl.glFogf(GL10.GL_FOG_END, uw ? 16f : 62f);
                gl.glClearColor(fc[0], fc[1], fc[2], 1f);
            }
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glLoadIdentity();
            gl.glRotatef(pitch, 1f, 0f, 0f);
            gl.glRotatef(yaw, 0f, 1f, 0f);
            gl.glTranslatef(-px, -(py + EYE), -pz);

            if (nv > 0) {
                gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vbuf);
                gl.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, 0, cbuf);
                gl.glDrawArrays(GL10.GL_TRIANGLES, 0, nv);
            }
            drawParticles(gl, dt);
        }
    }

    // =====================================================================
    static class Hud extends View {
        final Game g;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Handler h = new Handler();
        final int[] role = new int[16]; // 0 nada, 1 joystick, 2 olhar, 3 pulo, 4 ui
        final float[] lx = new float[16], ly = new float[16], ax = new float[16], ay = new float[16];
        final boolean[] moved = new boolean[16], done = new boolean[16];
        final Runnable[] lp = new Runnable[16];
        final RectF novo = new RectF(), rr = new RectF();
        boolean joyOn;
        float joyCx, joyCy, joyKx, joyKy;

        Hud(Context c, Game game) {
            super(c);
            g = game;
            for (int i = 0; i < 16; i++) {
                final int id = i;
                lp[i] = new Runnable() {
                    public void run() {
                        if (role[id] == 2 && !moved[id] && !done[id]) {
                            done[id] = true;
                            g.acts.add(0);
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        }
                    }
                };
            }
        }

        float joyR() { return getHeight() * 0.15f; }
        float jr() { return getHeight() * 0.11f; }
        float jcx() { return getWidth() - getHeight() * 0.2f; }
        float jcy() { return getHeight() - getHeight() * 0.26f; }
        float hs() { return getHeight() * 0.085f; }
        float hl() { float s = hs(); return (getWidth() - (Game.NB * s + (Game.NB - 1) * s * 0.15f)) / 2f; }
        float ht() { return getHeight() - hs() - getHeight() * 0.035f; }

        void layout() {
            float W = getWidth(), H = getHeight();
            novo.set(W - H * 0.38f, H * 0.02f, W - H * 0.02f, H * 0.12f);
        }

        int hotHit(float x, float y) {
            float s = hs(), l = hl(), t = ht();
            if (y < t || y > t + s) return -1;
            for (int i = 0; i < Game.NB; i++) {
                float x0 = l + i * s * 1.15f;
                if (x >= x0 && x <= x0 + s) return i;
            }
            return -1;
        }

        float dist(float a, float b, float c, float d) {
            return (float) Math.hypot(a - c, b - d);
        }

        void down(int id, float x, float y) {
            if (id >= 16) return;
            layout();
            moved[id] = false;
            done[id] = false;
            ax[id] = x; ay[id] = y; lx[id] = x; ly[id] = y;
            if (dist(x, y, jcx(), jcy()) < jr() * 1.25f) {
                role[id] = 3;
                g.jumpHeld = true;
                return;
            }
            if (novo.contains(x, y)) {
                role[id] = 4;
                done[id] = true;
                g.acts.add(2);
                return;
            }
            int hb = hotHit(x, y);
            if (hb >= 0) {
                role[id] = 4;
                done[id] = true;
                g.sel = hb;
                return;
            }
            if (x < getWidth() * 0.45f) {
                if (joyOn) { role[id] = 0; return; }
                role[id] = 1;
                joyOn = true;
                joyCx = x; joyCy = y; joyKx = x; joyKy = y;
                g.joyX = 0f; g.joyY = 0f;
                return;
            }
            role[id] = 2;
            h.postDelayed(lp[id], 380);
        }

        void move(int id, float x, float y) {
            if (id >= 16) return;
            if (role[id] == 1) {
                float R = joyR();
                float vx = (x - joyCx) / R, vy = (y - joyCy) / R;
                float mag = (float) Math.hypot(vx, vy);
                if (mag > 1f) { vx /= mag; vy /= mag; mag = 1f; }
                joyKx = joyCx + vx * R;
                joyKy = joyCy + vy * R;
                if (mag < 0.12f) { vx = 0f; vy = 0f; }
                g.joyX = vx;
                g.joyY = vy;
            } else if (role[id] == 2) {
                float ddx = x - lx[id], ddy = y - ly[id];
                if (!moved[id] && dist(x, y, ax[id], ay[id]) > 22f) {
                    moved[id] = true;
                    h.removeCallbacks(lp[id]);
                }
                if (moved[id] || done[id]) g.look(ddx * 0.22f, ddy * 0.22f);
                lx[id] = x;
                ly[id] = y;
            }
        }

        void up(int id) {
            if (id >= 16) return;
            if (role[id] == 1) {
                joyOn = false;
                g.joyX = 0f;
                g.joyY = 0f;
            } else if (role[id] == 2) {
                h.removeCallbacks(lp[id]);
                if (!moved[id] && !done[id]) g.acts.add(1);
            } else if (role[id] == 3) {
                g.jumpHeld = false;
            }
            role[id] = 0;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            int ai = e.getActionIndex();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                down(e.getPointerId(ai), e.getX(ai), e.getY(ai));
            } else if (a == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    move(e.getPointerId(i), e.getX(i), e.getY(i));
                }
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) {
                up(e.getPointerId(ai));
            } else if (a == MotionEvent.ACTION_CANCEL) {
                for (int i = 0; i < 16; i++) {
                    h.removeCallbacks(lp[i]);
                    role[i] = 0;
                }
                joyOn = false;
                g.joyX = 0f;
                g.joyY = 0f;
                g.jumpHeld = false;
            }
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            layout();
            int W = getWidth(), H = getHeight();
            // mira
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCCFFFFFF);
            float cx = W / 2f, cy = H / 2f, ar = H * 0.02f, t = H * 0.004f;
            c.drawRect(cx - ar, cy - t, cx + ar, cy + t, p);
            c.drawRect(cx - t, cy - ar, cx + t, cy + ar, p);
            // dica
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(H * 0.032f);
            c.drawText("toca = bota | segura = quebra", H * 0.03f, H * 0.06f, p);
            // joystick
            float R = joyR();
            p.setStrokeWidth(H * 0.006f);
            if (joyOn) {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xAAFFFFFF);
                c.drawCircle(joyCx, joyCy, R, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(joyKx, joyKy, R * 0.4f, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0x44FFFFFF);
                c.drawCircle(W * 0.14f, H * 0.7f, R, p);
            }
            // pulo
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66000000);
            c.drawCircle(jcx(), jcy(), jr(), p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xCCFFFFFF);
            c.drawCircle(jcx(), jcy(), jr(), p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(H * 0.04f);
            c.drawText("PULA", jcx(), jcy() + H * 0.014f, p);
            // novo mundo
            p.setColor(0x66000000);
            c.drawRoundRect(novo, H * 0.02f, H * 0.02f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xCCFFFFFF);
            c.drawRoundRect(novo, H * 0.02f, H * 0.02f, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(H * 0.04f);
            c.drawText("NOVO MUNDO", novo.centerX(), novo.centerY() + H * 0.014f, p);
            // hotbar
            float s = hs(), l = hl(), tp = ht();
            for (int i = 0; i < Game.NB; i++) {
                float x0 = l + i * s * 1.15f;
                rr.set(x0, tp, x0 + s, tp + s);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Game.COR[i + 1][0] | 0xFF000000);
                c.drawRoundRect(rr, s * 0.12f, s * 0.12f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(i == g.sel ? s * 0.1f : s * 0.04f);
                p.setColor(i == g.sel ? 0xFFFFFFFF : 0x99000000);
                c.drawRoundRect(rr, s * 0.12f, s * 0.12f, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(H * 0.035f);
            c.drawText(Game.NOMES[g.sel], W / 2f, tp - H * 0.015f, p);
        }
    }
}
