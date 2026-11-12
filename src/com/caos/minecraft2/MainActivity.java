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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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
        // ---------- config do mundo ----------
        static final int CS = 16;               // tamanho do chunk (x e z)
        static final int SY = 80;               // altura do mundo
        static final int SEA = 24;              // nivel do mar
        static final int SNOW_Y = 58;           // acima disso tem neve
        static final int RENDER_R = 4;          // raio (em chunks) que desenha
        static final int LOAD_R = RENDER_R + 1; // raio (em chunks) que gera
        static final int GEN_BUDGET = 1;        // chunks gerados por frame
        static final int MESH_BUDGET = 2;       // chunks "montados" por frame
        static final float FOG_S = 28f, FOG_E = 60f, FAR = 110f;

        static final int MP = 400;
        static final float HW = 0.3f, PH = 1.8f, EYE = 1.62f, SPEED = 5.2f;

        // ids dos blocos
        static final int AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, SAND = 4, WOOD = 5,
                LEAF = 6, CAOS = 7, WATER = 8, SNOW = 9, CACTUS = 10, ORE = 11;

        // {topo, lado}
        static final int[][] COR = {
            {0, 0},
            {0x3FAE46, 0x7A5230}, // 1 grama
            {0x7A5230, 0x6A4526}, // 2 terra
            {0x8C8C8C, 0x777777}, // 3 pedra
            {0xE6D58A, 0xD4C377}, // 4 areia
            {0xB48850, 0x9A6B35}, // 5 madeira
            {0x2E8B3A, 0x277A32}, // 6 folha
            {0xE63CDC, 0xA02AA0}, // 7 caos
            {0x2F6FE0, 0x2F6FE0}, // 8 agua
            {0xF2F6FA, 0xDDE5EE}, // 9 neve
            {0x2F8F3F, 0x2A7A36}, // 10 cacto
            {0xC9A46A, 0x9A8466}  // 11 minerio
        };
        static final String[] NOMES = {"GRAMA", "TERRA", "PEDRA", "AREIA", "MADEIRA", "FOLHA", "CAOS"};
        static final float[][] CORN = {
            {1,0,0, 1,1,0, 1,1,1, 1,0,1},
            {0,0,1, 0,1,1, 0,1,0, 0,0,0},
            {0,1,0, 0,1,1, 1,1,1, 1,1,0},
            {0,0,0, 1,0,0, 1,0,1, 0,0,1},
            {0,0,1, 1,0,1, 1,1,1, 0,1,1},
            {1,0,0, 0,0,0, 0,1,0, 1,1,0}
        };
        static final int[] DX = {1, -1, 0, 0, 0, 0};
        static final int[] DY = {0, 0, 1, -1, 0, 0};
        static final int[] DZ = {0, 0, 0, 0, 1, -1};
        static final float[] SH = {0.8f, 0.7f, 1.0f, 0.5f, 0.9f, 0.75f};
        static final int[] ORDER = {0, 1, 2, 0, 2, 3};
        static final float[] FOG_AIR = {0.53f, 0.81f, 0.92f, 1f};
        static final float[] FOG_WATER = {0.08f, 0.22f, 0.45f, 1f};

        // offsets de chunks ordenados do mais perto pro mais longe
        static final int[][] OFFS;
        static {
            int n = 2 * LOAD_R + 1;
            int[][] o = new int[n * n][];
            int k = 0;
            for (int dz = -LOAD_R; dz <= LOAD_R; dz++)
                for (int dx = -LOAD_R; dx <= LOAD_R; dx++)
                    o[k++] = new int[]{dx, dz};
            Arrays.sort(o, new Comparator<int[]>() {
                public int compare(int[] a, int[] b) {
                    return (a[0] * a[0] + a[1] * a[1]) - (b[0] * b[0] + b[1] * b[1]);
                }
            });
            OFFS = o;
        }

        // ---------- classes auxiliares ----------
        static final class Mesh {
            final FloatBuffer v;
            final ByteBuffer c;
            final int n;
            Mesh(float[] va, int nf, byte[] ca, int nc, int n) {
                this.n = n;
                v = ByteBuffer.allocateDirect(nf * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                v.put(va, 0, nf);
                v.position(0);
                c = ByteBuffer.allocateDirect(nc).order(ByteOrder.nativeOrder());
                c.put(ca, 0, nc);
                c.position(0);
            }
        }

        // acumula vertices (cresce sozinho)
        static final class Sink {
            float[] v = new float[24576];
            byte[] c = new byte[32768];
            int nf, nc, n;
            void reset() { nf = 0; nc = 0; n = 0; }
            void vert(float x, float y, float z, byte r, byte g, byte b, byte a) {
                if (nf + 3 > v.length) v = Arrays.copyOf(v, v.length * 2);
                if (nc + 4 > c.length) c = Arrays.copyOf(c, c.length * 2);
                v[nf++] = x; v[nf++] = y; v[nf++] = z;
                c[nc++] = r; c[nc++] = g; c[nc++] = b; c[nc++] = a;
                n++;
            }
            Mesh build() { return n == 0 ? null : new Mesh(v, nf, c, nc, n); }
        }

        static final class Chunk {
            final int cx, cz;
            final byte[] b;
            int maxY;
            boolean dirty = true, urgent, meshed, modified, loaded = true;
            Mesh mo, mw; // opaco e agua
            Chunk(int cx, int cz, byte[] b) { this.cx = cx; this.cz = cz; this.b = b; }
            void calcMaxY() {
                maxY = 0;
                for (int y = SY - 1; y >= 0; y--) {
                    int base = y * CS * CS;
                    for (int i = 0; i < CS * CS; i++) {
                        if (b[base + i] != 0) { maxY = y; return; }
                    }
                }
            }
        }

        static final class Col { int h, top, sub, biome; float m; }

        // ruido de valor (value noise) com seed
        static final class Noise {
            final int seed;
            Noise(int s) { seed = s; }
            int hash(int x, int y, int z) {
                int h = seed + x * 374761393 + y * 668265263 + z * 1274126177;
                h = (h ^ (h >>> 15)) * 0x2c1b3c6d;
                h = (h ^ (h >>> 12)) * 0x297a2d39;
                h ^= h >>> 15;
                return h;
            }
            float rand(int x, int y, int z) { return (hash(x, y, z) & 0xFFFFFF) / 16777216f; }
            static float fade(float t) { return t * t * t * (t * (t * 6f - 15f) + 10f); }
            static float lerp(float a, float b, float t) { return a + (b - a) * t; }
            static int fl(float v) { int i = (int) v; return v < i ? i - 1 : i; }
            float v2(float x, float z) {
                int x0 = fl(x), z0 = fl(z);
                float u = fade(x - x0), w = fade(z - z0);
                float a = rand(x0, 0, z0), b = rand(x0 + 1, 0, z0);
                float c = rand(x0, 0, z0 + 1), d = rand(x0 + 1, 0, z0 + 1);
                return lerp(lerp(a, b, u), lerp(c, d, u), w);
            }
            float v3(float x, float y, float z) {
                int x0 = fl(x), y0 = fl(y), z0 = fl(z);
                float u = fade(x - x0), v = fade(y - y0), w = fade(z - z0);
                float c000 = rand(x0, y0, z0), c100 = rand(x0 + 1, y0, z0);
                float c010 = rand(x0, y0 + 1, z0), c110 = rand(x0 + 1, y0 + 1, z0);
                float c001 = rand(x0, y0, z0 + 1), c101 = rand(x0 + 1, y0, z0 + 1);
                float c011 = rand(x0, y0 + 1, z0 + 1), c111 = rand(x0 + 1, y0 + 1, z0 + 1);
                float a = lerp(lerp(c000, c100, u), lerp(c010, c110, u), v);
                float b = lerp(lerp(c001, c101, u), lerp(c011, c111, u), v);
                return lerp(a, b, w);
            }
            // varias camadas de ruido somadas (0..1)
            float fbm(float x, float z, int oct) {
                float s = 0f, a = 1f, f = 1f, n = 0f;
                for (int i = 0; i < oct; i++) {
                    s += v2(x * f + i * 37.1f, z * f - i * 23.7f) * a;
                    n += a;
                    a *= 0.5f;
                    f *= 2f;
                }
                return s / n;
            }
        }

        // ---------- estado do mundo ----------
        final HashMap<Long, Chunk> chunks = new HashMap<Long, Chunk>();
        final HashMap<Long, byte[]> saved = new HashMap<Long, byte[]>(); // chunks editados que foram descarregados
        final ArrayList<Chunk> urgentList = new ArrayList<Chunk>();
        Noise noise = new Noise(1);
        final Col col = new Col(), tcol = new Col();
        final Sink so = new Sink(), sw = new Sink();
        Chunk cW, cE, cN, cS;           // vizinhos do chunk sendo montado
        Chunk lchunk;                   // cache do ultimo chunk acessado
        int lcx, lcz;
        int lastPcx, lastPcz;
        boolean loadDone, wasUnder;
        volatile int chunkCount;
        boolean inited = false;

        final FloatBuffer pvb = ByteBuffer.allocateDirect(MP * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        final ByteBuffer pcb = ByteBuffer.allocateDirect(MP * 4).order(ByteOrder.nativeOrder());
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

        static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }
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

        // ---------- acesso aos chunks / blocos ----------
        Chunk chunk(int cx, int cz) {
            if (lchunk != null && lcx == cx && lcz == cz) return lchunk;
            Chunk c = chunks.get(key(cx, cz));
            if (c != null) { lchunk = c; lcx = cx; lcz = cz; }
            return c;
        }

        // -1 = chunk nao carregado
        int get(int x, int y, int z) {
            if (y < 0) return STONE;
            if (y >= SY) return AIR;
            Chunk c = chunk(x >> 4, z >> 4);
            if (c == null) return -1;
            return c.b[(y * CS + (z & 15)) * CS + (x & 15)];
        }

        void markDirty(Chunk c) {
            if (c == null) return;
            c.dirty = true;
            if (!c.urgent) { c.urgent = true; urgentList.add(c); }
        }

        boolean set(int x, int y, int z, int t) {
            if (y < 0 || y >= SY) return false;
            int cx = x >> 4, cz = z >> 4;
            Chunk c = chunk(cx, cz);
            if (c == null) return false;
            int lx = x & 15, lz = z & 15;
            c.b[(y * CS + lz) * CS + lx] = (byte) t;
            c.modified = true;
            if (t != AIR && y > c.maxY) c.maxY = y;
            markDirty(c);
            if (lx == 0) markDirty(chunk(cx - 1, cz));
            if (lx == 15) markDirty(chunk(cx + 1, cz));
            if (lz == 0) markDirty(chunk(cx, cz - 1));
            if (lz == 15) markDirty(chunk(cx, cz + 1));
            return true;
        }

        // ---------- geracao do terreno ----------
        void column(int x, int z, Col o) {
            float cont = noise.fbm(x * 0.005f, z * 0.005f, 3);            // continentes/oceanos
            float hill = noise.fbm(x * 0.03f + 100f, z * 0.03f - 100f, 4); // colinas
            float mtn = noise.fbm(x * 0.011f - 300f, z * 0.011f + 300f, 4); // montanhas
            float temp = noise.fbm(x * 0.0035f + 700f, z * 0.0035f - 700f, 2);
            float moist = noise.fbm(x * 0.004f - 900f, z * 0.004f + 900f, 2);
            float m = (mtn - 0.5f) * 6f;
            if (m < 0f) m = 0f;
            if (m > 1f) m = 1f;
            float h = SEA + 3 + (cont - 0.5f) * 36f + (hill - 0.5f) * 14f + m * m * 36f;
            int hi = clamp((int) h, 2, SY - 14);
            int biome = 0;                                    // 0 planicie, 1 floresta, 2 deserto
            if (temp > 0.56f && moist < 0.5f) biome = 2;
            else if (moist > 0.52f) biome = 1;
            o.h = hi;
            o.m = m;
            o.biome = biome;
            if (hi <= SEA + 1) { o.top = SAND; o.sub = SAND; }
            else if (biome == 2 && m < 0.3f) { o.top = SAND; o.sub = SAND; }
            else if (hi >= SNOW_Y) { o.top = SNOW; o.sub = STONE; }
            else if (m > 0.35f && hi > SEA + 12) { o.top = STONE; o.sub = STONE; }
            else { o.top = GRASS; o.sub = DIRT; }
        }

        // cavernas: onde dois campos de ruido ficam perto de 0.5 ao mesmo tempo (tuneis)
        boolean carve(int x, int y, int z) {
            float a = noise.v3(x * 0.055f, y * 0.08f, z * 0.055f);
            if (Math.abs(a - 0.5f) > 0.035f) return false;
            float b = noise.v3(x * 0.055f + 200f, y * 0.08f + 200f, z * 0.055f + 200f);
            return Math.abs(b - 0.5f) < 0.035f;
        }

        void put(Chunk c, int ox, int oz, int wx, int y, int wz, int t) {
            int lx = wx - ox, lz = wz - oz;
            if (lx < 0 || lx >= CS || lz < 0 || lz >= CS || y < 0 || y >= SY) return;
            int i = (y * CS + lz) * CS + lx;
            int cur = c.b[i];
            if (cur == AIR || (t == WOOD && cur == LEAF)) c.b[i] = (byte) t;
        }

        void tree(Chunk c, int ox, int oz, int tx, int tz, int h) {
            int th = 4 + (noise.hash(tx, 31, tz) & 1);
            for (int dy = th - 1; dy <= th + 1; dy++) {
                int r = (dy == th + 1) ? 1 : 2;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (r == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                        put(c, ox, oz, tx + dx, h + dy, tz + dz, LEAF);
                    }
                }
            }
            for (int k = 1; k <= th; k++) put(c, ox, oz, tx, h + k, tz, WOOD);
        }

        void cactus(Chunk c, int ox, int oz, int tx, int tz, int h) {
            int ch = 2 + (noise.hash(tx, 55, tz) & 1);
            for (int k = 1; k <= ch; k++) put(c, ox, oz, tx, h + k, tz, CACTUS);
        }

        // arvores/cactos sao decididos so pela posicao (seed), entao
        // cada chunk calcula as arvores dos vizinhos que invadem ele (sem emenda)
        void decorate(Chunk c, int ox, int oz) {
            for (int tz = oz - 2; tz < oz + CS + 2; tz++) {
                for (int tx = ox - 2; tx < ox + CS + 2; tx++) {
                    float r = noise.rand(tx, 9001, tz);
                    if (r > 0.05f) continue;
                    column(tx, tz, tcol);
                    if (tcol.h <= SEA + 1) continue;
                    if (tcol.top == SAND) {
                        if (tcol.biome == 2 && r < 0.008f) cactus(c, ox, oz, tx, tz, tcol.h);
                        continue;
                    }
                    if (tcol.top != GRASS) continue;
                    float dens = (tcol.biome == 1) ? 0.05f : 0.006f;
                    if (r < dens) tree(c, ox, oz, tx, tz, tcol.h);
                }
            }
        }

        Chunk generate(int cx, int cz) {
            byte[] b = new byte[CS * SY * CS];
            Chunk c = new Chunk(cx, cz, b);
            int ox = cx * CS, oz = cz * CS;
            for (int lz = 0; lz < CS; lz++) {
                for (int lx = 0; lx < CS; lx++) {
                    int x = ox + lx, z = oz + lz;
                    column(x, z, col);
                    int h = col.h;
                    for (int y = 0; y <= h; y++) {
                        int t;
                        if (y == 0) t = STONE;
                        else if (y == h) t = col.top;
                        else if (y >= h - 3) t = col.sub;
                        else {
                            t = STONE;
                            if (y < 40 && noise.rand(x, y + 5000, z) < 0.012f) t = ORE;
                        }
                        if (y >= 2 && y <= h - 2 && carve(x, y, z)) continue;
                        b[(y * CS + lz) * CS + lx] = (byte) t;
                    }
                    for (int y = h + 1; y <= SEA; y++) b[(y * CS + lz) * CS + lx] = (byte) WATER;
                }
            }
            decorate(c, ox, oz);
            c.calcMaxY();
            return c;
        }

        Chunk loadChunk(int cx, int cz) {
            long k = key(cx, cz);
            byte[] s = saved.remove(k);
            Chunk c;
            if (s != null) {
                c = new Chunk(cx, cz, s);
                c.modified = true;
                c.calcMaxY();
            } else {
                c = generate(cx, cz);
            }
            chunks.put(k, c);
            Chunk n;
            if ((n = chunk(cx - 1, cz)) != null && n.meshed) n.dirty = true;
            if ((n = chunk(cx + 1, cz)) != null && n.meshed) n.dirty = true;
            if ((n = chunk(cx, cz - 1)) != null && n.meshed) n.dirty = true;
            if ((n = chunk(cx, cz + 1)) != null && n.meshed) n.dirty = true;
            return c;
        }

        void newWorld() {
            noise = new Noise(rnd.nextInt());
            chunks.clear();
            saved.clear();
            urgentList.clear();
            lchunk = null;
            // acha um lugar em terra firme espiralando a partir do 0,0
            int sx = 0, sz = 0;
            for (int i = 0; i < 3000; i++) {
                int x = (int) (Math.cos(i * 0.7) * i * 0.6), z = (int) (Math.sin(i * 0.7) * i * 0.6);
                column(x, z, col);
                if (col.h > SEA + 2 && col.top != STONE && col.top != SNOW) { sx = x; sz = z; break; }
            }
            int pcx = sx >> 4, pcz = sz >> 4;
            for (int dz = -2; dz <= 2; dz++)
                for (int dx = -2; dx <= 2; dx++)
                    loadChunk(pcx + dx, pcz + dz);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    Chunk c = chunk(pcx + dx, pcz + dz);
                    buildMesh(c);
                    c.dirty = false;
                }
            }
            column(sx, sz, col);
            px = sx + 0.5f;
            pz = sz + 0.5f;
            py = Math.max(col.h, SEA) + 1f;
            while (collides(px, py, pz) && py < SY - 3) py += 1f; // caso tenha arvore no spawn
            vy = 0f;
            fall = false;
            caosT = 0f;
            Arrays.fill(plife, 0f);
            lastPcx = pcx;
            lastPcz = pcz;
            loadDone = false;
        }

        // ---------- carrega / descarrega / monta malhas ----------
        void updateChunks() {
            int pcx = ((int) Math.floor(px)) >> 4, pcz = ((int) Math.floor(pz)) >> 4;
            if (pcx != lastPcx || pcz != lastPcz) {
                lastPcx = pcx;
                lastPcz = pcz;
                loadDone = false;
                Iterator<Chunk> it = chunks.values().iterator();
                while (it.hasNext()) {
                    Chunk c = it.next();
                    if (Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > LOAD_R + 1) {
                        if (c.modified) saved.put(key(c.cx, c.cz), c.b);
                        c.loaded = false;
                        it.remove();
                        lchunk = null;
                    }
                }
            }
            if (!loadDone) {
                int budget = GEN_BUDGET;
                for (int i = 0; i < OFFS.length && budget > 0; i++) {
                    int cx = pcx + OFFS[i][0], cz = pcz + OFFS[i][1];
                    if (chunk(cx, cz) == null) {
                        loadChunk(cx, cz);
                        budget--;
                    }
                }
                if (budget > 0) loadDone = true;
            }
            // edicoes: remonta na hora
            for (int i = 0; i < urgentList.size(); i++) {
                Chunk c = urgentList.get(i);
                c.urgent = false;
                if (c.loaded && c.meshed) {
                    buildMesh(c);
                    c.dirty = false;
                }
            }
            urgentList.clear();
            // o resto: os mais perto primeiro, so se os 4 vizinhos ja existem
            for (int n = 0; n < MESH_BUDGET; n++) {
                Chunk best = null;
                int bd = Integer.MAX_VALUE;
                for (Chunk c : chunks.values()) {
                    if (!c.dirty) continue;
                    int dx = c.cx - pcx, dz = c.cz - pcz;
                    if (Math.max(Math.abs(dx), Math.abs(dz)) > RENDER_R) continue;
                    int d = dx * dx + dz * dz;
                    if (d >= bd) continue;
                    if (chunk(c.cx - 1, c.cz) == null || chunk(c.cx + 1, c.cz) == null
                            || chunk(c.cx, c.cz - 1) == null || chunk(c.cx, c.cz + 1) == null) continue;
                    best = c;
                    bd = d;
                }
                if (best == null) break;
                buildMesh(best);
                best.dirty = false;
            }
            chunkCount = chunks.size();
        }

        // ---------- mesh ----------
        int nb(Chunk c, int x, int y, int z) {
            if (y < 0) return STONE;
            if (y >= SY) return AIR;
            Chunk t = c;
            if (x < 0) { t = cW; x = 15; }
            else if (x > 15) { t = cE; x = 0; }
            if (z < 0) { t = cN; z = 15; }
            else if (z > 15) { t = cS; z = 0; }
            if (t == null) return AIR;
            return t.b[(y * CS + z) * CS + x];
        }

        // x e z locais (0..15): o chunk e' posicionado na hora de desenhar
        void face(Sink s, int x, int y, int z, int wx, int wz, int d, int t) {
            int c = (d == 2) ? COR[t][0] : COR[t][1];
            boolean w = (t == WATER);
            float hs = w ? 1f : 0.92f + 0.08f * (((wx * 73856093) ^ (y * 19349663) ^ (wz * 83492791)) & 7) / 7f;
            float f = SH[d] * hs;
            byte r = (byte) (((c >> 16) & 255) * f);
            byte gg = (byte) (((c >> 8) & 255) * f);
            byte bb = (byte) ((c & 255) * f);
            byte a = (byte) (w ? 160 : 255);
            float[] k = CORN[d];
            for (int i = 0; i < 6; i++) {
                int o = ORDER[i] * 3;
                float vy2 = k[o + 1];
                if (w && vy2 > 0.5f) vy2 -= 0.1f; // superficie da agua um pouco mais baixa
                s.vert(x + k[o], y + vy2, z + k[o + 2], r, gg, bb, a);
            }
        }

        void buildMesh(Chunk c) {
            cW = chunk(c.cx - 1, c.cz);
            cE = chunk(c.cx + 1, c.cz);
            cN = chunk(c.cx, c.cz - 1);
            cS = chunk(c.cx, c.cz + 1);
            so.reset();
            sw.reset();
            final byte[] b = c.b;
            int ox = c.cx * CS, oz = c.cz * CS;
            for (int y = 0; y <= c.maxY; y++) {
                for (int z = 0; z < CS; z++) {
                    for (int x = 0; x < CS; x++) {
                        int t = b[(y * CS + z) * CS + x];
                        if (t == 0) continue;
                        boolean w = (t == WATER);
                        for (int d = 0; d < 6; d++) {
                            int n = nb(c, x + DX[d], y + DY[d], z + DZ[d]);
                            boolean show = w ? (n == AIR) : (n == AIR || n == WATER);
                            if (show) face(w ? sw : so, x, y, z, ox + x, oz + z, d, t);
                        }
                    }
                }
            }
            c.mo = so.build();
            c.mw = sw.build();
            c.meshed = true;
        }

        // ---------- fisica ----------
        // chunk nao carregado conta como parede (-1 != 0)
        boolean solid(int x, int y, int z) {
            int t = get(x, y, z);
            return t != AIR && t != WATER;
        }

        boolean isWater(float x, float y, float z) {
            return get((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)) == WATER;
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
            boolean wet = isWater(px, py + 0.4f, pz);
            boolean head = isWater(px, py + EYE, pz);
            float spd = wet ? SPEED * 0.6f : SPEED;
            float mx = (fxx * fw + rx * st) * spd * dt;
            float mz = (fzz * fw + rz * st) * spd * dt;
            if (wet) {
                vy -= 9f * dt;
                if (vy < -3f) vy = -3f;
                if (jumpHeld) {
                    vy += 30f * dt;
                    float cap = head ? 3.5f : 6.5f; // na superficie da um pulinho pra sair
                    if (vy > cap) vy = cap;
                }
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
                int t = get(cx, cy, cz);
                if (t > 0 && t != WATER) {
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
            int t = get(x, y, z);
            if (t <= 0) return;
            set(x, y, z, AIR);
            for (int i = 0; i < 12; i++) spawn(x + 0.5f, y + 0.5f, z + 0.5f, COR[t][0]);
        }

        void doPlace() {
            if (!raycast()) return;
            int x = hit[3], y = hit[4], z = hit[5];
            int t = get(x, y, z);
            if (t != AIR && t != WATER) return; // inclui chunk nao carregado
            if (overlapsPlayer(x, y, z)) return;
            set(x, y, z, sel + 1);
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
                        if (y <= 0) continue;
                        int t = get(x, y, z);
                        if (t <= 0 || t == WATER) continue;
                        set(x, y, z, AIR);
                        spawn(x + 0.5f, y + 0.5f, z + 0.5f, COR[t][0]);
                        spawn(x + 0.5f, y + 0.5f, z + 0.5f, COR[CAOS][0]);
                    }
                }
            }
        }

        void updateChaos(float dt) {
            if (fall) {
                fallT += dt;
                while (fallT > 0.06f && fall) {
                    fallT -= 0.06f;
                    if (get(fx, fy, fz) != CAOS) { fall = false; break; }
                    int ny = fy - 1;
                    int below = get(fx, ny, fz);
                    if (below == -1) {
                        set(fx, fy, fz, AIR); // caiu num chunk que nao existe, some
                        fall = false;
                    } else if ((below != AIR && below != WATER) || overlapsPlayer(fx, ny, fz)) {
                        explode(fx, fy, fz);
                        fall = false;
                    } else {
                        set(fx, fy, fz, AIR);
                        set(fx, ny, fz, CAOS);
                        fy = ny;
                    }
                }
            } else {
                caosT += dt;
                if (caosT > 10f) {
                    caosT = 0f;
                    if (rnd.nextFloat() < 0.7f) {
                        int x = (int) Math.floor(px) + rnd.nextInt(17) - 8;
                        int z = (int) Math.floor(pz) + rnd.nextInt(17) - 8;
                        fx = x; fy = SY - 2; fz = z;
                        if (get(fx, fy, fz) == AIR) {
                            set(fx, fy, fz, CAOS);
                            fall = true;
                            fallT = 0f;
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
        void applyFog(GL10 gl, boolean under) {
            float[] fc = under ? FOG_WATER : FOG_AIR;
            gl.glClearColor(fc[0], fc[1], fc[2], 1f);
            gl.glFogfv(GL10.GL_FOG_COLOR, fc, 0);
            gl.glFogf(GL10.GL_FOG_START, under ? 1f : FOG_S);
            gl.glFogf(GL10.GL_FOG_END, under ? 22f : FOG_E);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig c) {
            gl.glEnable(GL10.GL_DEPTH_TEST);
            gl.glEnable(GL10.GL_FOG);
            gl.glFogf(GL10.GL_FOG_MODE, GL10.GL_LINEAR);
            wasUnder = false;
            applyFog(gl, false);
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
            gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);
            if (!inited) {
                newWorld();
                inited = true;
            }
            last = System.nanoTime();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int w, int h) {
            gl.glViewport(0, 0, w, h);
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            float near = 0.15f;
            float top = near * 0.7f;
            float right = top * ((float) w / (float) h);
            gl.glFrustumf(-right, right, -top, top, near, FAR);
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
                else if (a == 2) newWorld();
            }
            updatePlayer(dt);
            updateChaos(dt);
            updateChunks();

            boolean under = isWater(px, py + EYE, pz);
            if (under != wasUnder) {
                wasUnder = under;
                applyFog(gl, under);
            }

            gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glLoadIdentity();
            gl.glRotatef(pitch, 1f, 0f, 0f);
            gl.glRotatef(yaw, 0f, 1f, 0f);

            // direcao da camera (pra descartar chunks que estao atras)
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            float dx = (float) (Math.sin(yr) * Math.cos(pr));
            float dy = (float) (-Math.sin(pr));
            float dz = (float) (-Math.cos(yr) * Math.cos(pr));
            float ey = py + EYE;
            int pcx = ((int) Math.floor(px)) >> 4, pcz = ((int) Math.floor(pz)) >> 4;

            for (int pass = 0; pass < 2; pass++) {
                if (pass == 1) { // agua: translucida, depois de tudo opaco
                    gl.glEnable(GL10.GL_BLEND);
                    gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
                    gl.glDepthMask(false);
                }
                for (Chunk c : chunks.values()) {
                    Mesh m = (pass == 0) ? c.mo : c.mw;
                    if (m == null) continue;
                    if (Math.abs(c.cx - pcx) > RENDER_R || Math.abs(c.cz - pcz) > RENDER_R) continue;
                    float hy = (c.maxY + 1) * 0.5f;
                    float rx = c.cx * CS + 8f - px, ry = hy - ey, rz = c.cz * CS + 8f - pz;
                    float rad = (float) Math.sqrt(128f + hy * hy);
                    if (rx * dx + ry * dy + rz * dz < -rad) continue;
                    gl.glPushMatrix();
                    gl.glTranslatef(c.cx * CS - px, -ey, c.cz * CS - pz);
                    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, m.v);
                    gl.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, 0, m.c);
                    gl.glDrawArrays(GL10.GL_TRIANGLES, 0, m.n);
                    gl.glPopMatrix();
                }
            }
            gl.glDepthMask(true);
            gl.glDisable(GL10.GL_BLEND);

            gl.glPushMatrix();
            gl.glTranslatef(-px, -ey, -pz);
            drawParticles(gl, dt);
            gl.glPopMatrix();
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
        float hs() { return getHeight() * 0.105f; }
        float hl() { float s = hs(); return (getWidth() - (7 * s + 6 * s * 0.15f)) / 2f; }
        float ht() { return getHeight() - hs() - getHeight() * 0.035f; }

        void layout() {
            float W = getWidth(), H = getHeight();
            novo.set(W - H * 0.38f, H * 0.02f, W - H * 0.02f, H * 0.12f);
        }

        int hotHit(float x, float y) {
            float s = hs(), l = hl(), t = ht();
            if (y < t || y > t + s) return -1;
            for (int i = 0; i < 7; i++) {
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
            // debug: posicao e chunks carregados
            p.setTextSize(H * 0.026f);
            c.drawText("XYZ " + (int) Math.floor(g.px) + " " + (int) Math.floor(g.py) + " "
                    + (int) Math.floor(g.pz) + "  chunks " + g.chunkCount, H * 0.03f, H * 0.1f, p);
            postInvalidateDelayed(250);
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
            for (int i = 0; i < 7; i++) {
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
