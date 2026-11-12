package com.caos.minecraft2;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

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
    @Override
    protected void onDestroy() { game.shutdown(); super.onDestroy(); }

    // =====================================================================
    static class Game implements GLSurfaceView.Renderer {
        // ---------- config do mundo ----------
        static final int CS = 16;               // tamanho do chunk (x e z)
        static final int SY = 80;               // altura do mundo
        static final int SEA = 24;              // nivel do mar
        static final int SNOW_Y = 58;           // acima disso tem neve
        static final int RENDER_R = 4;          // raio (em chunks) que desenha
        static final int LOAD_R = RENDER_R + 1; // raio (em chunks) que gera
        static final int MAX_GEN = 6;           // chunks sendo gerados ao mesmo tempo
        static final int MAX_MESH = 4;          // malhas sendo montadas ao mesmo tempo
        static final float FOG_S = 28f, FOG_E = 60f, FAR = 110f;

        static final int MP = 600;
        static final int HB = 9;                // slots da hotbar
        static final float HW = 0.3f, PH = 1.8f, EYE = 1.62f, SPEED = 5.2f;

        // ids dos blocos
        static final int AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, SAND = 4, WOOD = 5,
                LEAF = 6, CAOS = 7, WATER = 8, SNOW = 9, CACTUS = 10, IRON = 11,
                PLANKS = 12, COBBLE = 13, BRICK = 14, GLASS = 15, GRAVEL = 16, SANDSTONE = 17,
                WOOL_W = 18, WOOL_R = 19, WOOL_B = 20, WOOL_G = 21, WOOL_Y = 22, WOOL_K = 23,
                OBSIDIAN = 24, ICE = 25, COAL = 26, GOLD = 27, DIAMOND = 28, LAMP = 29, BEDROCK = 30;
        static final int IT_BOMB = 31, IT_TORCH = 32, IT_CLOCK = 33, IT_FEATHER = 34, IT_POTION = 35, IT_HAMMER = 36;
        static final int NALL = 37;             // blocos + itens (ids 0..36)
        static final int NB = 31;               // quantidade de ids (0..30)

        // tiles {topo, lado, baixo} de cada bloco (indices no atlas)
        static final int[][] TILE = {
            {0, 0, 0},                                                         // 0 ar
            {Tex.T_GRASS_TOP, Tex.T_GRASS_SIDE, Tex.T_DIRT},                   // 1 grama
            {Tex.T_DIRT, Tex.T_DIRT, Tex.T_DIRT},                              // 2 terra
            {Tex.T_STONE, Tex.T_STONE, Tex.T_STONE},                           // 3 pedra
            {Tex.T_SAND, Tex.T_SAND, Tex.T_SAND},                              // 4 areia
            {Tex.T_LOG_TOP, Tex.T_LOG_SIDE, Tex.T_LOG_TOP},                    // 5 madeira
            {Tex.T_LEAF, Tex.T_LEAF, Tex.T_LEAF},                              // 6 folha
            {Tex.T_CAOS, Tex.T_CAOS, Tex.T_CAOS},                              // 7 caos
            {Tex.T_WATER, Tex.T_WATER, Tex.T_WATER},                           // 8 agua
            {Tex.T_SNOW, Tex.T_SNOW, Tex.T_SNOW},                              // 9 neve
            {Tex.T_CACTUS_TOP, Tex.T_CACTUS_SIDE, Tex.T_CACTUS_TOP},           // 10 cacto
            {Tex.T_IRON, Tex.T_IRON, Tex.T_IRON},                              // 11 ferro
            {Tex.T_PLANKS, Tex.T_PLANKS, Tex.T_PLANKS},                        // 12 tabuas
            {Tex.T_COBBLE, Tex.T_COBBLE, Tex.T_COBBLE},                        // 13 pedregulho
            {Tex.T_BRICK, Tex.T_BRICK, Tex.T_BRICK},                           // 14 tijolo
            {Tex.T_GLASS, Tex.T_GLASS, Tex.T_GLASS},                           // 15 vidro
            {Tex.T_GRAVEL, Tex.T_GRAVEL, Tex.T_GRAVEL},                        // 16 cascalho
            {Tex.T_SS_TOP, Tex.T_SS_SIDE, Tex.T_SS_TOP},                       // 17 arenito
            {Tex.T_WOOL_W, Tex.T_WOOL_W, Tex.T_WOOL_W},                        // 18 la branca
            {Tex.T_WOOL_R, Tex.T_WOOL_R, Tex.T_WOOL_R},                        // 19 la vermelha
            {Tex.T_WOOL_B, Tex.T_WOOL_B, Tex.T_WOOL_B},                        // 20 la azul
            {Tex.T_WOOL_G, Tex.T_WOOL_G, Tex.T_WOOL_G},                        // 21 la verde
            {Tex.T_WOOL_Y, Tex.T_WOOL_Y, Tex.T_WOOL_Y},                        // 22 la amarela
            {Tex.T_WOOL_K, Tex.T_WOOL_K, Tex.T_WOOL_K},                        // 23 la preta
            {Tex.T_OBSIDIAN, Tex.T_OBSIDIAN, Tex.T_OBSIDIAN},                  // 24 obsidiana
            {Tex.T_ICE, Tex.T_ICE, Tex.T_ICE},                                 // 25 gelo
            {Tex.T_COAL, Tex.T_COAL, Tex.T_COAL},                              // 26 carvao
            {Tex.T_GOLD, Tex.T_GOLD, Tex.T_GOLD},                              // 27 ouro
            {Tex.T_DIAMOND, Tex.T_DIAMOND, Tex.T_DIAMOND},                     // 28 diamante
            {Tex.T_LAMP, Tex.T_LAMP, Tex.T_LAMP},                              // 29 lampada
            {Tex.T_BEDROCK, Tex.T_BEDROCK, Tex.T_BEDROCK}                      // 30 bedrock
        };
        static final String[] NOMES = {"VAZIO", "GRAMA", "TERRA", "PEDRA", "AREIA", "MADEIRA",
                "FOLHA", "CAOS", "AGUA", "NEVE", "CACTO", "FERRO", "TABUAS", "PEDREGULHO",
                "TIJOLO", "VIDRO", "CASCALHO", "ARENITO", "LA BRANCA", "LA VERMELHA", "LA AZUL",
                "LA VERDE", "LA AMARELA", "LA PRETA", "OBSIDIANA", "GELO", "CARVAO", "OURO",
                "DIAMANTE", "LAMPADA", "BEDROCK",
                "BOMBA DO CAOS", "TOCHA", "RELOGIO", "PENA", "POCAO", "MARRETA"};

        // blocos que escondem a face do vizinho (os outros deixam ver atraves)
        static final boolean[] OPAQUE = new boolean[NB];
        static {
            Arrays.fill(OPAQUE, true);
            OPAQUE[AIR] = false;
            OPAQUE[LEAF] = false;
            OPAQUE[WATER] = false;
            OPAQUE[GLASS] = false;
        }

        static final float[][] CORN = {
            {1,0,0, 1,1,0, 1,1,1, 1,0,1},
            {0,0,1, 0,1,1, 0,1,0, 0,0,0},
            {0,1,0, 0,1,1, 1,1,1, 1,1,0},
            {0,0,0, 1,0,0, 1,0,1, 0,0,1},
            {0,0,1, 1,0,1, 1,1,1, 0,1,1},
            {1,0,0, 0,0,0, 0,1,0, 1,1,0}
        };
        // coordenada (u,v) da textura em cada canto de cada face
        static final float[][] CUV = {
            {1,1, 1,0, 0,0, 0,1},
            {1,1, 1,0, 0,0, 0,1},
            {0,0, 0,1, 1,1, 1,0},
            {0,0, 1,0, 1,1, 0,1},
            {0,1, 1,1, 1,0, 0,0},
            {0,1, 1,1, 1,0, 0,0}
        };
        static final float TEPS = 0.004f;      // folguinha pra nao pegar pixel do tile do lado
        static final int[] DX = {1, -1, 0, 0, 0, 0};
        static final int[] DY = {0, 0, 1, -1, 0, 0};
        static final int[] DZ = {0, 0, 0, 0, 1, -1};
        static final float[] SH = {0.8f, 0.7f, 1.0f, 0.5f, 0.9f, 0.75f};
        static final int[] ORDER = {0, 1, 2, 0, 2, 3};
        static final float[] FOG_AIR = {0.53f, 0.81f, 0.92f, 1f};
        static final float[] FOG_WATER = {0.08f, 0.22f, 0.45f, 1f};

        // sprite de cada item (mesma ordem dos ids 31..36)
        static final int[] ITEM_TILE = {Tex.T_BOMB, Tex.T_TORCH, Tex.T_CLOCK, Tex.T_FEATHER, Tex.T_POTION, Tex.T_HAMMER};

        // ---- ceu / dia e noite ----
        static final float DAY_LEN = 360f;      // segundos de um dia inteiro
        static final float SKY_D = 100f;        // distancia do sol, da lua e das estrelas
        static final float TILT = 25f;          // inclinacao da orbita (graus)
        static final float COS_T = (float) Math.cos(Math.toRadians(TILT));
        static final float SIN_T = (float) Math.sin(Math.toRadians(TILT));
        static final int NSTAR = 260;
        // cor do ceu / cor que multiplica o mundo todo (escurece de noite)
        static final float[] SKY_DAY = {0.53f, 0.81f, 0.92f}, SKY_SET = {0.96f, 0.58f, 0.38f}, SKY_NIGHT = {0.07f, 0.09f, 0.22f};
        static final float[] MUL_DAY = {1f, 1f, 1f}, MUL_SET = {1f, 0.82f, 0.72f}, MUL_NIGHT = {0.30f, 0.34f, 0.55f};
        // pulos do relogio: amanhecer, meio-dia, por do sol, meia-noite
        static final float[] PRESET = {0f, 0.275f, 0.55f, 0.775f};
        static final int MB = 8;                // bombas voando ao mesmo tempo

        // cor media do bloco (pras particulas)
        static int pcolor(int t) { return Tex.avg(TILE[t][1]); }

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

        // =================================================================
        // texturas procedurais: um atlas 256x256 com tiles de 16x16
        // =================================================================
        static final class Tex {
            static final int T_GRASS_TOP = 0, T_GRASS_SIDE = 1, T_DIRT = 2, T_STONE = 3, T_SAND = 4,
                    T_LOG_SIDE = 5, T_LOG_TOP = 6, T_LEAF = 7, T_CAOS = 8, T_WATER = 9, T_SNOW = 10,
                    T_CACTUS_SIDE = 11, T_CACTUS_TOP = 12, T_IRON = 13, T_PLANKS = 14, T_COBBLE = 15,
                    T_BRICK = 16, T_GLASS = 17, T_GRAVEL = 18, T_SS_SIDE = 19, T_SS_TOP = 20,
                    T_WOOL_W = 21, T_WOOL_R = 22, T_WOOL_B = 23, T_WOOL_G = 24, T_WOOL_Y = 25,
                    T_WOOL_K = 26, T_OBSIDIAN = 27, T_ICE = 28, T_COAL = 29, T_GOLD = 30,
                    T_DIAMOND = 31, T_LAMP = 32, T_BEDROCK = 33,
                    T_BOMB = 34, T_TORCH = 35, T_CLOCK = 36, T_FEATHER = 37, T_POTION = 38, T_HAMMER = 39;
            static final int NT = 40, TS = 16, AT = 256;
            static final int[] pix = new int[AT * AT];
            static final int[] AVG = new int[NT];
            static final Bitmap[] tiles = new Bitmap[NT];
            static final int[] CAOS_PAL = {0xE63CDC, 0x5A1470, 0x1EE6E6, 0xFF9CFF, 0x14061C};
            static Bitmap atlas;

            static synchronized Bitmap atlas() {
                if (atlas == null) {
                    build();
                    atlas = Bitmap.createBitmap(pix, AT, AT, Bitmap.Config.ARGB_8888);
                    for (int t = 0; t < NT; t++) {
                        long r = 0, g = 0, b = 0;
                        int n = 0;
                        for (int y = 0; y < TS; y++) {
                            for (int x = 0; x < TS; x++) {
                                int p = pix[((t >> 4) * TS + y) * AT + (t & 15) * TS + x];
                                if ((p >>> 24) == 0) continue;
                                r += (p >> 16) & 255;
                                g += (p >> 8) & 255;
                                b += p & 255;
                                n++;
                            }
                        }
                        AVG[t] = (n == 0) ? 0xFF808080 : rgb((int) (r / n), (int) (g / n), (int) (b / n));
                    }
                }
                return atlas;
            }

            static synchronized Bitmap tile(int t) {
                if (tiles[t] == null) {
                    tiles[t] = Bitmap.createBitmap(atlas(), (t & 15) * TS, (t >> 4) * TS, TS, TS);
                }
                return tiles[t];
            }

            static synchronized int avg(int t) {
                atlas();
                return AVG[t];
            }

            // ruido deterministico 0..1 por pixel
            static float n2(int x, int y, int s) {
                int h = x * 374761393 + y * 668265263 + s * 1274126177;
                h = (h ^ (h >>> 13)) * 1274126177;
                h ^= h >>> 16;
                return (h & 0xFFFF) / 65536f;
            }

            static int clampi(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

            static int rgb(int r, int g, int b) {
                return 0xFF000000 | (clampi(r) << 16) | (clampi(g) << 8) | clampi(b);
            }

            static int shade(int c, float f) {
                return rgb((int) (((c >> 16) & 255) * f), (int) (((c >> 8) & 255) * f), (int) ((c & 255) * f));
            }

            static void put(int t, int x, int y, int argb) {
                pix[((t >> 4) * TS + y) * AT + (t & 15) * TS + x] = argb;
            }

            static int dirt(int x, int y) {
                float f = 0.82f + n2(x, y, 6) * 0.28f;
                float p = n2(x, y, 7);
                if (p > 0.94f) f *= 0.65f;
                else if (p < 0.04f) f *= 1.25f;
                return shade(0x866043, f);
            }

            static int stone(int x, int y, int s, int base) {
                float f = 0.88f + n2(x, y, s) * 0.22f + (n2(x >> 2, y >> 2, s + 1) - 0.5f) * 0.14f;
                return shade(base, f);
            }

            static void ore(int t, int oc, int seed) {
                for (int y = 0; y < TS; y++)
                    for (int x = 0; x < TS; x++)
                        put(t, x, y, stone(x, y, 5, 0x7D7D7D));
                for (int k = 0; k < 6; k++) {
                    int cx = 2 + (int) (n2(k, 1, seed) * 12f);
                    int cy = 2 + (int) (n2(k, 2, seed) * 12f);
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (Math.abs(dx) + Math.abs(dy) > 1 && n2(cx + dx, cy + dy, seed + 9) < 0.5f) continue;
                            int x = cx + dx, y = cy + dy;
                            if (x < 0 || x > 15 || y < 0 || y > 15) continue;
                            put(t, x, y, shade(oc, 0.85f + n2(x, y, seed + 5) * 0.3f));
                        }
                    }
                }
            }

            static void bricks(int t, int bw, int bh, int mortar, int seed, boolean gray, int baseCol) {
                for (int y = 0; y < TS; y++) {
                    for (int x = 0; x < TS; x++) {
                        int row = y / bh, yy = y % bh;
                        int off = ((row & 1) == 0) ? 0 : bw / 2;
                        int xx = (x + off) % bw;
                        int idx = (x + off) / bw + row * 7;
                        boolean m = (yy == bh - 1) || (xx == bw - 1);
                        int col;
                        if (m) {
                            col = shade(mortar, 0.9f + n2(x, y, seed) * 0.2f);
                        } else if (gray) {
                            float g = 0.6f + n2(idx, 0, seed + 1) * 0.55f;
                            col = shade(0xA0A0A0, g * (0.9f + n2(x, y, seed + 2) * 0.2f));
                        } else {
                            col = shade(baseCol, 0.82f + n2(idx, 0, seed + 1) * 0.3f + n2(x, y, seed + 2) * 0.1f);
                        }
                        put(t, x, y, col);
                    }
                }
            }

            static void wool(int t, int color, int seed) {
                for (int y = 0; y < TS; y++) {
                    for (int x = 0; x < TS; x++) {
                        float f = 0.9f + n2(x, y, seed) * 0.16f;
                        if (((x + y) & 1) == 0) f *= 0.96f;
                        put(t, x, y, shade(color, f));
                    }
                }
            }

            static void box(int t, int x0, int y0, int x1, int y1, int col, int seed) {
                for (int y = y0; y <= y1; y++)
                    for (int x = x0; x <= x1; x++)
                        put(t, x, y, shade(col, 0.88f + n2(x, y, seed) * 0.24f));
            }

            static void ball(int t, float cx, float cy, float r, int col, int seed) {
                for (int y = 0; y < TS; y++) {
                    for (int x = 0; x < TS; x++) {
                        float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                        float d = (float) Math.sqrt(dx * dx + dy * dy);
                        if (d > r) continue;
                        float f = 0.85f + n2(x, y, seed) * 0.2f;
                        if (d > r - 1.2f) f *= 0.7f;                         // borda mais escura
                        if (dx < 0 && dy < 0 && d < r * 0.7f) f *= 1.25f;    // brilhinho
                        put(t, x, y, shade(col, f));
                    }
                }
            }

            static void spr(int t, int x, int y, int col) {
                if (x >= 0 && x < TS && y >= 0 && y < TS) put(t, x, y, 0xFF000000 | col);
            }

            // sprites 16x16 dos itens (o resto do tile fica transparente)
            static void itemSprites() {
                // bomba do caos: bolinha preta com manchas de caos e pavio
                ball(T_BOMB, 7.5f, 9.5f, 5.6f, 0x2A2A33, 100);
                spr(T_BOMB, 5, 10, CAOS_PAL[0]);
                spr(T_BOMB, 9, 8, CAOS_PAL[2]);
                spr(T_BOMB, 8, 12, CAOS_PAL[0]);
                spr(T_BOMB, 6, 12, CAOS_PAL[2]);
                spr(T_BOMB, 10, 11, CAOS_PAL[3]);
                box(T_BOMB, 6, 3, 9, 4, 0x55555F, 101);
                spr(T_BOMB, 8, 2, 0x8B6B3A);
                spr(T_BOMB, 9, 1, 0x8B6B3A);
                spr(T_BOMB, 10, 1, 0x8B6B3A);
                spr(T_BOMB, 11, 0, 0xFFE24A);
                spr(T_BOMB, 12, 0, 0xFF8A20);
                spr(T_BOMB, 12, 1, 0xFFE24A);

                // tocha: chama em cima, palito embaixo
                for (int y = 1; y <= 6; y++) {
                    int x0 = (y == 1 || y == 6) ? 7 : 6, x1 = (y == 1 || y == 6) ? 8 : 9;
                    int fc = y <= 2 ? 0xFFF29A : (y <= 4 ? 0xFFB02E : 0xFF6A1A);
                    for (int x = x0; x <= x1; x++) put(T_TORCH, x, y, shade(fc, 0.9f + n2(x, y, 111) * 0.2f));
                }
                box(T_TORCH, 7, 7, 8, 14, 0x7A5630, 112);

                // relogio: aro dourado, mostrador claro e ponteiros
                ball(T_CLOCK, 7.5f, 7.5f, 6.8f, 0xD9A82A, 120);
                for (int y = 0; y < TS; y++) {
                    for (int x = 0; x < TS; x++) {
                        float dx = x + 0.5f - 7.5f, dy = y + 0.5f - 7.5f;
                        if (Math.sqrt(dx * dx + dy * dy) < 5.2) put(T_CLOCK, x, y, shade(0xF2EEDC, 0.95f + n2(x, y, 121) * 0.08f));
                    }
                }
                spr(T_CLOCK, 7, 3, 0x333333);
                spr(T_CLOCK, 12, 7, 0x333333);
                spr(T_CLOCK, 7, 12, 0x333333);
                spr(T_CLOCK, 3, 7, 0x333333);
                spr(T_CLOCK, 7, 7, 0x222222);
                spr(T_CLOCK, 7, 6, 0x222222);
                spr(T_CLOCK, 7, 5, 0x222222);
                spr(T_CLOCK, 7, 4, 0x222222);
                spr(T_CLOCK, 8, 7, 0xC03030);
                spr(T_CLOCK, 9, 7, 0xC03030);
                spr(T_CLOCK, 10, 7, 0xC03030);

                // pena: diagonal clarinha com o cabo
                for (int k = 0; k <= 13; k++) {
                    int x = 1 + k, y = 14 - k;
                    int w = Math.min(k, 13 - k) / 3;
                    for (int d = -w; d <= w; d++) {
                        int xx = x + d, yy = y + d;
                        if (xx < 0 || xx > 15 || yy < 0 || yy > 15) continue;
                        put(T_FEATHER, xx, yy, d == 0 ? shade(0x9A9AA8, 1f) : shade(0xF0F2FA, 0.88f + n2(xx, yy, 130) * 0.16f));
                    }
                }
                spr(T_FEATHER, 0, 15, 0x9A9AA8);

                // pocao de velocidade: frasco ciano com rolha
                ball(T_POTION, 7.5f, 10f, 5f, 0x30D8E0, 140);
                box(T_POTION, 6, 2, 9, 5, 0xCFE8F0, 141);
                box(T_POTION, 6, 0, 9, 1, 0x8A5A2A, 142);
                spr(T_POTION, 5, 8, 0xFFFFFF);
                spr(T_POTION, 5, 9, 0xFFFFFF);

                // marreta: cabo de madeira e cabecao de ferro
                box(T_HAMMER, 7, 6, 8, 15, 0x7A5630, 150);
                box(T_HAMMER, 2, 1, 13, 6, 0x8A8A92, 151);
                box(T_HAMMER, 2, 1, 3, 6, 0x5A5A62, 152);
                box(T_HAMMER, 12, 1, 13, 6, 0x5A5A62, 153);
                for (int x = 4; x <= 11; x++) spr(T_HAMMER, x, 1, 0xD0D0DA);
            }

            static void build() {
                for (int y = 0; y < TS; y++) {
                    for (int x = 0; x < TS; x++) {
                        // grama (topo)
                        float gf = 0.82f + n2(x, y, 1) * 0.28f + (n2(x >> 1, y >> 1, 2) - 0.5f) * 0.12f;
                        put(T_GRASS_TOP, x, y, shade(0x5BAA3E, gf));
                        // terra
                        put(T_DIRT, x, y, dirt(x, y));
                        // grama (lado): terra com franja verde irregular em cima
                        int d = 3 + (int) (n2(x, 0, 3) * 3f);
                        put(T_GRASS_SIDE, x, y, y < d ? shade(0x5BAA3E, 0.78f + n2(x, y, 4) * 0.28f) : dirt(x, y));
                        // pedra
                        put(T_STONE, x, y, stone(x, y, 5, 0x7D7D7D));
                        // areia
                        float sf = 0.92f + n2(x, y, 10) * 0.12f;
                        if (n2(x, y, 11) > 0.95f) sf *= 0.85f;
                        put(T_SAND, x, y, shade(0xDCCF8E, sf));
                        // tronco (lado): casca com listras verticais
                        float lf = 0.7f + n2(x, 0, 12) * 0.35f + n2(x, y, 13) * 0.12f;
                        put(T_LOG_SIDE, x, y, shade(0x6B5230, lf));
                        // tronco (topo): aneis
                        float dx = x - 7.5f, dy = y - 7.5f;
                        float dd = Math.max(Math.abs(dx), Math.abs(dy));
                        int lt;
                        if (dd > 5.5f) {
                            lt = shade(0x5A4328, 0.85f + n2(x, y, 14) * 0.25f);
                        } else {
                            int ring = (int) Math.sqrt(dx * dx + dy * dy);
                            lt = shade((ring & 1) == 0 ? 0xB8935A : 0x9C7A46, 0.9f + n2(x, y, 15) * 0.18f);
                        }
                        put(T_LOG_TOP, x, y, lt);
                        // folha: com uns buracos (transparentes)
                        float ln = n2(x, y, 20);
                        if (ln > 0.9f) put(T_LEAF, x, y, 0x002F8A33);
                        else put(T_LEAF, x, y, shade(0x2F8A33, 0.62f + n2(x, y, 21) * 0.6f));
                        // caos: pixels bagunçados em blocos 2x2
                        int ci = (int) (n2(x >> 1, y >> 1, 30) * 5f);
                        if (ci > 4) ci = 4;
                        put(T_CAOS, x, y, shade(CAOS_PAL[ci], 0.9f + n2(x, y, 31) * 0.2f));
                        // agua
                        float wf = 0.85f + n2(x >> 1, y >> 1, 32) * 0.2f + (float) Math.sin((x + y) * 0.9) * 0.05f;
                        put(T_WATER, x, y, shade(0x2F6FE0, wf));
                        // neve
                        put(T_SNOW, x, y, shade(0xF2F6FA, 0.94f + n2(x, y, 33) * 0.08f));
                        // cacto (lado)
                        float cf = 0.85f + n2(x, y, 34) * 0.2f;
                        if (x == 0 || x == 15) cf *= 0.7f;
                        else if (x % 5 == 2) cf *= 0.85f;
                        int cc = shade(0x2E8B3F, cf);
                        if (n2(x, y, 35) > 0.94f) cc = 0xFFD8F0C8;
                        put(T_CACTUS_SIDE, x, y, cc);
                        // cacto (topo)
                        float tf = 0.9f + n2(x, y, 36) * 0.15f;
                        if (dd > 6.5f) tf *= 0.7f;
                        else if (Math.abs(dx) < 1.1f || Math.abs(dy) < 1.1f) tf *= 0.88f;
                        put(T_CACTUS_TOP, x, y, shade(0x3A9A4A, tf));
                        // tabuas: 4 tabuas horizontais com emenda
                        int board = y >> 2, yy = y & 3;
                        float pf = 0.9f + n2(x >> 1, yy + board * 4, 60) * 0.18f + n2(x, y, 61) * 0.06f;
                        if (yy == 3) pf *= 0.75f;
                        int seam = ((board & 1) == 0) ? 5 : 12;
                        if (x == seam && yy < 3) pf *= 0.8f;
                        put(T_PLANKS, x, y, shade(0xB58F56, pf));
                        // vidro: moldura + brilhinho, resto transparente
                        boolean edge = (x == 0 || y == 0 || x == 15 || y == 15);
                        boolean glint = (x == y && x >= 3 && x <= 6) || (x == y - 1 && x >= 3 && x <= 5);
                        int gl;
                        if (edge) gl = shade(0xC8E8F0, 0.9f + n2(x, y, 62) * 0.15f);
                        else if (glint) gl = 0xFFFFFFFF;
                        else gl = 0x00C8E8F0;
                        put(T_GLASS, x, y, gl);
                        // cascalho
                        float gn = n2(x >> 1, y >> 1, 70);
                        int gbase = n2(x, y, 71) > 0.82f ? 0x8C7A66 : 0x9A9A9A;
                        put(T_GRAVEL, x, y, shade(gbase, (0.55f + gn * 0.6f) * (0.9f + n2(x, y, 72) * 0.2f)));
                        // arenito
                        float af = 0.93f + n2(x, y, 73) * 0.1f;
                        if (y == 2 || y == 3 || y == 12) af *= 0.86f;
                        put(T_SS_SIDE, x, y, shade(0xD9CC8C, af));
                        put(T_SS_TOP, x, y, shade(0xDDD196, 0.96f + n2(x, y, 74) * 0.08f));
                        // obsidiana
                        float on = n2(x, y, 80);
                        if (on > 0.92f) put(T_OBSIDIAN, x, y, shade(0x5B34A0, 0.8f + n2(x, y, 81) * 0.4f));
                        else put(T_OBSIDIAN, x, y, shade(0x18102C, 0.7f + on * 0.7f));
                        // gelo
                        float ifc = 0.94f + n2(x, y, 82) * 0.1f;
                        if (((x + y) % 8) == 0 || ((x + y + 1) % 8) == 0) ifc *= 1.08f;
                        put(T_ICE, x, y, shade(0xA4D4F0, ifc));
                        // lampada
                        boolean border = ((x & 7) == 0) || ((y & 7) == 0);
                        float lamf = 0.95f + n2(x, y, 83) * 0.1f;
                        int lc = border ? shade(0xC8963C, lamf) : shade(0xFAE79A, lamf);
                        if (!border && (x & 7) >= 3 && (x & 7) <= 4 && (y & 7) >= 3 && (y & 7) <= 4) lc = shade(0xFFF6C8, 1f);
                        put(T_LAMP, x, y, lc);
                        // bedrock
                        float bn = n2(x, y, 84);
                        float bf = 0.5f + bn * 0.9f;
                        if (n2(x >> 1, y >> 1, 85) > 0.6f) bf *= 0.7f;
                        put(T_BEDROCK, x, y, shade(0x555555, bf));
                    }
                }
                ore(T_IRON, 0xD8AF93, 40);
                ore(T_COAL, 0x1E1E1E, 41);
                ore(T_GOLD, 0xFADC3C, 42);
                ore(T_DIAMOND, 0x5AE6E1, 43);
                bricks(T_BRICK, 8, 4, 0xB4AFA5, 50, false, 0x964637);
                bricks(T_COBBLE, 6, 5, 0x555555, 53, true, 0);
                wool(T_WOOL_W, 0xEBEBEB, 90);
                wool(T_WOOL_R, 0xAF2D2D, 91);
                wool(T_WOOL_B, 0x3246AA, 92);
                wool(T_WOOL_G, 0x3C8232, 93);
                wool(T_WOOL_Y, 0xE6C832, 94);
                wool(T_WOOL_K, 0x1E1E23, 95);
                itemSprites();
            }
        }

        // ---------- classes auxiliares ----------
        static final class Mesh {
            final FloatBuffer v;   // posicoes
            final FloatBuffer t;   // coordenadas da textura
            final ByteBuffer c;    // cores (sombreamento)
            final int n;
            Mesh(float[] va, int nf, float[] ta, int nt, byte[] ca, int nc, int n) {
                this.n = n;
                v = ByteBuffer.allocateDirect(nf * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                v.put(va, 0, nf);
                v.position(0);
                t = ByteBuffer.allocateDirect(nt * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                t.put(ta, 0, nt);
                t.position(0);
                c = ByteBuffer.allocateDirect(nc).order(ByteOrder.nativeOrder());
                c.put(ca, 0, nc);
                c.position(0);
            }
        }

        // acumula vertices (cresce sozinho)
        static final class Sink {
            float[] v = new float[24576];
            float[] t = new float[16384];
            byte[] c = new byte[32768];
            int nf, nt, nc, n;
            void reset() { nf = 0; nt = 0; nc = 0; n = 0; }
            void vert(float x, float y, float z, float tu, float tv, byte r, byte g, byte b, byte a) {
                if (nf + 3 > v.length) v = Arrays.copyOf(v, v.length * 2);
                if (nt + 2 > t.length) t = Arrays.copyOf(t, t.length * 2);
                if (nc + 4 > c.length) c = Arrays.copyOf(c, c.length * 2);
                v[nf++] = x; v[nf++] = y; v[nf++] = z;
                t[nt++] = tu; t[nt++] = tv;
                c[nc++] = r; c[nc++] = g; c[nc++] = b; c[nc++] = a;
                n++;
            }
            Mesh build() { return n == 0 ? null : new Mesh(v, nf, t, nt, c, nc, n); }
        }

        static final class Chunk {
            final int cx, cz;
            final byte[] b;
            int maxY;
            int version = 1, meshVer = 0;     // precisa de malha nova quando meshVer != version
            boolean urgent, meshed, meshing, modified, loaded = true;
            Mesh mo, mw; // opaco e agua
            Chunk(int cx, int cz, byte[] b) { this.cx = cx; this.cz = cz; this.b = b; }
            boolean needsMesh() { return meshVer != version; }
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

        // trabalho pra thread / resultado dela
        static final class Job {
            boolean gen;
            int world, cx, cz, version;
            Noise noise;
            Chunk c, w, e, n, s;
        }

        static final class Res {
            boolean gen;
            int world, cx, cz, version;
            Chunk c;
            Mesh mo, mw;
        }

        // ruido de valor (value noise) com seed (sem estado, pode usar em qualquer thread)
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

        // ---------- gerador de terreno (cada thread tem o seu) ----------
        static final class Gen {
            Noise noise = new Noise(1);
            final Col col = new Col(), tcol = new Col();

            void column(int x, int z, Col o) {
                float cont = noise.fbm(x * 0.005f, z * 0.005f, 3);            // continentes/oceanos
                float hill = noise.fbm(x * 0.03f + 100f, z * 0.03f - 100f, 4); // colinas
                float mtn = noise.fbm(x * 0.011f - 300f, z * 0.011f + 300f, 4); // montanhas
                float temp = noise.fbm(x * 0.0035f + 700f, z * 0.0035f - 700f, 2);
                float moist = noise.fbm(x * 0.004f - 900f, z * 0.004f + 900f, 2);
                float gv = noise.v2(x * 0.07f + 50f, z * 0.07f - 50f);        // manchas de cascalho
                float m = (mtn - 0.5f) * 6f;
                if (m < 0f) m = 0f;
                if (m > 1f) m = 1f;
                float h = SEA + 3 + (cont - 0.5f) * 36f + (hill - 0.5f) * 14f + m * m * 36f;
                int hi = clamp((int) h, 2, SY - 14);
                int biome = 0;                          // 0 planicie, 1 floresta, 2 deserto, 3 tundra
                if (temp > 0.56f && moist < 0.5f) biome = 2;
                else if (temp < 0.42f) biome = 3;
                else if (moist > 0.52f) biome = 1;
                o.h = hi;
                o.m = m;
                o.biome = biome;
                if (hi <= SEA + 1) {
                    if (hi < SEA - 1 && gv > 0.6f) { o.top = GRAVEL; o.sub = GRAVEL; }
                    else { o.top = SAND; o.sub = SAND; }
                }
                else if (biome == 2 && m < 0.3f) { o.top = SAND; o.sub = SAND; }
                else if (hi >= SNOW_Y) { o.top = SNOW; o.sub = STONE; }
                else if (m > 0.35f && hi > SEA + 12) { o.top = (gv > 0.62f) ? GRAVEL : STONE; o.sub = STONE; }
                else if (biome == 3) { o.top = SNOW; o.sub = DIRT; }
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
                            if (y == 0) {
                                t = BEDROCK;
                            } else if (y == h) {
                                t = col.top;
                            } else if (y >= h - 3) {
                                t = col.sub;
                                if (t == SAND && col.biome == 2 && y <= h - 2) t = SANDSTONE;
                            } else {
                                t = STONE;
                                float o = noise.rand(x, y + 5000, z);
                                if (o < 0.012f) { if (y < 56) t = COAL; }
                                else if (o < 0.022f) { if (y < 40) t = IRON; }
                                else if (o < 0.026f) { if (y < 24) t = GOLD; }
                                else if (o < 0.028f) { if (y < 14) t = DIAMOND; }
                            }
                            if (y >= 2 && y <= h - 2 && carve(x, y, z)) continue;
                            b[(y * CS + lz) * CS + lx] = (byte) t;
                        }
                        for (int y = h + 1; y <= SEA; y++) {
                            // na tundra a superficie da agua congela
                            b[(y * CS + lz) * CS + lx] = (byte) ((col.biome == 3 && y == SEA) ? ICE : WATER);
                        }
                    }
                }
                decorate(c, ox, oz);
                c.calcMaxY();
                return c;
            }
        }

        // ---------- montador de malha (cada thread tem o seu) ----------
        static final class Mesher {
            final Sink so = new Sink(), sw = new Sink();
            Chunk cW, cE, cN, cS;
            Mesh mo, mw;

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
                int tile = TILE[t][d == 2 ? 0 : (d == 3 ? 2 : 1)];
                boolean w = (t == WATER);
                float f;
                if (t == LAMP) {
                    f = 1f; // lampada brilha: sem sombra
                } else {
                    float hs = w ? 1f : 0.94f + 0.06f * (((wx * 73856093) ^ (y * 19349663) ^ (wz * 83492791)) & 7) / 7f;
                    f = SH[d] * hs;
                }
                byte sh = (byte) (255 * f);
                byte a = (byte) (w ? 160 : 255);
                float u0 = (tile & 15) * 0.0625f, v0 = (tile >> 4) * 0.0625f;
                float[] k = CORN[d];
                float[] uv = CUV[d];
                for (int i = 0; i < 6; i++) {
                    int ci = ORDER[i];
                    int o = ci * 3;
                    float vy2 = k[o + 1];
                    if (w && vy2 > 0.5f) vy2 -= 0.1f; // superficie da agua um pouco mais baixa
                    float uu = u0 + (TEPS + uv[ci * 2] * (1f - 2f * TEPS)) * 0.0625f;
                    float vv = v0 + (TEPS + uv[ci * 2 + 1] * (1f - 2f * TEPS)) * 0.0625f;
                    s.vert(x + k[o], y + vy2, z + k[o + 2], uu, vv, sh, sh, sh, a);
                }
            }

            void build(Chunk c, Chunk w, Chunk e, Chunk n, Chunk s) {
                cW = w; cE = e; cN = n; cS = s;
                so.reset();
                sw.reset();
                final byte[] b = c.b;
                int ox = c.cx * CS, oz = c.cz * CS;
                int top = c.maxY;
                for (int y = 0; y <= top; y++) {
                    for (int z = 0; z < CS; z++) {
                        for (int x = 0; x < CS; x++) {
                            int t = b[(y * CS + z) * CS + x];
                            if (t == 0) continue;
                            boolean wt = (t == WATER);
                            for (int d = 0; d < 6; d++) {
                                int q = nb(c, x + DX[d], y + DY[d], z + DZ[d]);
                                boolean show;
                                if (wt) show = (q == AIR) || (q != WATER && !OPAQUE[q]);
                                else show = !OPAQUE[q] && q != t;
                                if (show) face(wt ? sw : so, x, y, z, ox + x, oz + z, d, t);
                            }
                        }
                    }
                }
                mo = so.build();
                mw = sw.build();
            }
        }

        // ---------- threads ----------
        final class Worker extends Thread {
            final Gen g = new Gen();
            final Mesher m = new Mesher();
            Worker(int i) { setName("chunk-" + i); setDaemon(true); }
            public void run() {
                while (!stop) {
                    Job j;
                    try {
                        j = jobs.take();
                    } catch (InterruptedException ex) {
                        return;
                    }
                    if (j.world != world) continue; // mundo velho, ignora
                    Res r = new Res();
                    r.gen = j.gen;
                    r.world = j.world;
                    r.cx = j.cx;
                    r.cz = j.cz;
                    r.c = j.c;
                    r.version = -1;
                    try {
                        if (j.gen) {
                            g.noise = j.noise;
                            r.c = g.generate(j.cx, j.cz);
                        } else {
                            m.build(j.c, j.w, j.e, j.n, j.s);
                            r.mo = m.mo;
                            r.mw = m.mw;
                            r.version = j.version;
                        }
                    } catch (Throwable t) {
                        if (j.gen) r.c = null;
                        r.mo = null;
                        r.mw = null;
                        r.version = -1;
                    }
                    results.add(r);
                }
            }
        }

        // ---------- estado do mundo ----------
        final HashMap<Long, Chunk> chunks = new HashMap<Long, Chunk>();
        final HashMap<Long, byte[]> saved = new HashMap<Long, byte[]>(); // chunks editados que foram descarregados
        final ArrayList<Chunk> urgentList = new ArrayList<Chunk>();
        final HashSet<Long> pendingGen = new HashSet<Long>();
        final LinkedBlockingQueue<Job> jobs = new LinkedBlockingQueue<Job>();
        final ConcurrentLinkedQueue<Res> results = new ConcurrentLinkedQueue<Res>();
        Worker[] workers;
        volatile boolean stop;
        volatile int world;
        int meshInFlight;
        Noise noise = new Noise(1);
        final Gen gen = new Gen();            // usado so na thread do GL (spawn)
        final Mesher mesher = new Mesher();   // usado so na thread do GL (edicoes)
        Chunk lchunk;                         // cache do ultimo chunk acessado
        int lcx, lcz;
        int lastPcx, lastPcz;
        boolean loadDone, wasUnder;
        volatile int chunkCount;
        boolean inited = false;
        int texId;

        // culling
        float aspect = 1.78f;
        final float[] fnx = new float[5], fny = new float[5], fnz = new float[5];
        final Chunk[] vis = new Chunk[(2 * RENDER_R + 1) * (2 * RENDER_R + 1)];

        final FloatBuffer pvb = ByteBuffer.allocateDirect(MP * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        final ByteBuffer pcb = ByteBuffer.allocateDirect(MP * 4).order(ByteOrder.nativeOrder());
        final float[] ppos = new float[MP * 3], pvel = new float[MP * 3], plife = new float[MP];
        final int[] pcol = new int[MP];
        int pnext = 0;

        // entrada (escrita pela UI, lida pela GL)
        volatile float joyX, joyY, yaw = 0f, pitch = 10f;
        volatile boolean jumpHeld;
        volatile int sel = 0;
        final int[] slots = new int[HB];      // hotbar: 0 = vazio, senao id do bloco
        final ConcurrentLinkedQueue<Integer> acts = new ConcurrentLinkedQueue<Integer>();

        float px, py, pz, vy;
        boolean onGround;
        long last;
        final Random rnd = new Random();
        final int[] hit = new int[6];

        boolean fall;
        int fx, fy, fz;
        float fallT, caosT;

        // ---- dia e noite ----
        volatile float dayT = 0.08f;            // 0..1: hora do dia (0 = amanhecer)
        volatile float skyAng = 0f;             // angulo do sol em radianos (o Hud usa pra mostrar a hora)
        volatile float speedT;                  // tempo que sobra da pocao de velocidade
        float sinA, starA;
        float skyR = 0.53f, skyG = 0.81f, skyB = 0.92f, mulR = 1f, mulG = 1f, mulB = 1f;
        final float[] star = new float[NSTAR * 3];
        final FloatBuffer svb = ByteBuffer.allocateDirect(NSTAR * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        final FloatBuffer qb = ByteBuffer.allocateDirect(12 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        final FloatBuffer fsq = ByteBuffer.allocateDirect(12 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        {
            fsq.put(new float[]{-1f, -1f, 0f, 1f, -1f, 0f, -1f, 1f, 0f, 1f, 1f, 0f});
            fsq.position(0);
            Random sr = new Random(77);
            for (int i = 0; i < NSTAR; i++) {
                float x, y, z, l;
                do {
                    x = (float) sr.nextGaussian();
                    y = (float) sr.nextGaussian();
                    z = (float) sr.nextGaussian();
                    l = (float) Math.sqrt(x * x + y * y + z * z);
                } while (l < 0.001f);
                star[i * 3] = x / l;
                star[i * 3 + 1] = y / l;
                star[i * 3 + 2] = z / l;
            }
        }

        // ---- bombas do caos ----
        final float[] bpos = new float[MB * 3], bvel = new float[MB * 3], blife = new float[MB];
        final FloatBuffer bvb = ByteBuffer.allocateDirect(MB * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        final ByteBuffer bcb = ByteBuffer.allocateDirect(MB * 4).order(ByteOrder.nativeOrder());
        int bnext;

        static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }
        static int clamp(int v, int a, int b) { return v < a ? a : (v > b ? b : v); }

        void startWorkers() {
            if (workers != null) return;
            int n = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 1));
            workers = new Worker[n];
            for (int i = 0; i < n; i++) {
                workers[i] = new Worker(i);
                workers[i].start();
            }
        }

        void shutdown() {
            stop = true;
            if (workers != null) {
                for (int i = 0; i < workers.length; i++) workers[i].interrupt();
            }
        }

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

        // ---------- acesso aos chunks / blocos (so thread do GL) ----------
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
            c.version++;
            if (c.meshed && !c.urgent) { c.urgent = true; urgentList.add(c); }
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

        // coloca um chunk pronto no mundo e avisa os vizinhos que ja tinham malha
        void addChunk(Chunk c) {
            chunks.put(key(c.cx, c.cz), c);
            Chunk n;
            if ((n = chunk(c.cx - 1, c.cz)) != null && n.meshed) n.version++;
            if ((n = chunk(c.cx + 1, c.cz)) != null && n.meshed) n.version++;
            if ((n = chunk(c.cx, c.cz - 1)) != null && n.meshed) n.version++;
            if ((n = chunk(c.cx, c.cz + 1)) != null && n.meshed) n.version++;
        }

        void meshNow(Chunk c) {
            mesher.build(c, chunk(c.cx - 1, c.cz), chunk(c.cx + 1, c.cz),
                    chunk(c.cx, c.cz - 1), chunk(c.cx, c.cz + 1));
            c.mo = mesher.mo;
            c.mw = mesher.mw;
            c.meshed = true;
            c.meshVer = c.version;
        }

        void newWorld() {
            world++; // invalida tudo que as threads ainda estao fazendo
            jobs.clear();
            results.clear();
            pendingGen.clear();
            meshInFlight = 0;
            noise = new Noise(rnd.nextInt());
            gen.noise = noise;
            chunks.clear();
            saved.clear();
            urgentList.clear();
            lchunk = null;
            // acha um lugar em terra firme espiralando a partir do 0,0
            int sx = 0, sz = 0;
            for (int i = 0; i < 3000; i++) {
                int x = (int) (Math.cos(i * 0.7) * i * 0.6), z = (int) (Math.sin(i * 0.7) * i * 0.6);
                gen.column(x, z, gen.col);
                if (gen.col.h > SEA + 2 && gen.col.top != STONE && gen.col.top != SNOW) { sx = x; sz = z; break; }
            }
            int pcx = sx >> 4, pcz = sz >> 4;
            // so o comecinho e' gerado na hora, o resto vem pelas threads
            for (int dz = -2; dz <= 2; dz++)
                for (int dx = -2; dx <= 2; dx++)
                    addChunk(gen.generate(pcx + dx, pcz + dz));
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++)
                    meshNow(chunk(pcx + dx, pcz + dz));
            gen.column(sx, sz, gen.col);
            px = sx + 0.5f;
            pz = sz + 0.5f;
            py = Math.max(gen.col.h, SEA) + 1f;
            while (collides(px, py, pz) && py < SY - 3) py += 1f; // caso tenha arvore no spawn
            vy = 0f;
            fall = false;
            caosT = 0f;
            Arrays.fill(plife, 0f);
            Arrays.fill(blife, 0f);
            dayT = 0.08f;
            speedT = 0f;
            lastPcx = pcx;
            lastPcz = pcz;
            loadDone = false;
        }

        // ---------- carrega / descarrega / manda pras threads ----------
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

            // 1) resultados que as threads terminaram
            Res r;
            while ((r = results.poll()) != null) {
                if (r.world != world) continue;
                if (r.gen) {
                    pendingGen.remove(key(r.cx, r.cz));
                    if (r.c == null) continue;
                    if (Math.max(Math.abs(r.cx - pcx), Math.abs(r.cz - pcz)) > LOAD_R + 1) continue;
                    if (chunk(r.cx, r.cz) != null) continue;
                    addChunk(r.c);
                } else {
                    meshInFlight--;
                    Chunk c = r.c;
                    c.meshing = false;
                    // se o chunk mudou enquanto montava, joga fora (ele ja vai ser refeito)
                    if (c.loaded && r.version == c.version) {
                        c.mo = r.mo;
                        c.mw = r.mw;
                        c.meshed = true;
                        c.meshVer = r.version;
                    }
                }
            }

            // 2) pede os chunks que faltam, do mais perto pro mais longe
            if (!loadDone) {
                boolean missing = false;
                for (int i = 0; i < OFFS.length; i++) {
                    int cx = pcx + OFFS[i][0], cz = pcz + OFFS[i][1];
                    if (chunk(cx, cz) != null) continue;
                    long k = key(cx, cz);
                    byte[] s = saved.remove(k);
                    if (s != null) { // editado antes: volta direto da memoria
                        Chunk c = new Chunk(cx, cz, s);
                        c.modified = true;
                        c.calcMaxY();
                        addChunk(c);
                        continue;
                    }
                    missing = true;
                    if (pendingGen.contains(k)) continue;
                    if (pendingGen.size() >= MAX_GEN) break;
                    Job j = new Job();
                    j.gen = true;
                    j.world = world;
                    j.noise = noise;
                    j.cx = cx;
                    j.cz = cz;
                    pendingGen.add(k);
                    jobs.add(j);
                }
                if (!missing) loadDone = true;
            }

            // 3) edicoes: remonta na hora, aqui mesmo (pra nao ter delay ao quebrar/por bloco)
            for (int i = 0; i < urgentList.size(); i++) {
                Chunk c = urgentList.get(i);
                c.urgent = false;
                if (c.loaded && c.meshed) meshNow(c);
            }
            urgentList.clear();

            // 4) manda montar malha dos chunks que precisam, os mais perto primeiro
            while (meshInFlight < MAX_MESH) {
                Chunk best = null;
                int bd = Integer.MAX_VALUE;
                for (Chunk c : chunks.values()) {
                    if (!c.needsMesh() || c.meshing) continue;
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
                Job j = new Job();
                j.gen = false;
                j.world = world;
                j.c = best;
                j.w = chunk(best.cx - 1, best.cz);
                j.e = chunk(best.cx + 1, best.cz);
                j.n = chunk(best.cx, best.cz - 1);
                j.s = chunk(best.cx, best.cz + 1);
                j.version = best.version;
                best.meshing = true;
                meshInFlight++;
                jobs.add(j);
            }
            chunkCount = chunks.size();
        }

        // ---------- culling ----------
        // planos do frustum (esq, dir, cima, baixo, perto), todos passando pelo olho
        void setupFrustum() {
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            float sy = (float) Math.sin(yr), cy = (float) Math.cos(yr);
            float sp = (float) Math.sin(pr), cp = (float) Math.cos(pr);
            float ffx = sy * cp, ffy = -sp, ffz = -cy * cp;   // frente
            float rrx = cy, rrz = sy;                         // direita
            float uux = sy * sp, uuy = cp, uuz = -cy * sp;    // cima
            float tv = 0.7f * 1.1f;                           // tan(fov/2) vertical + folga
            float th = tv * aspect;
            fnx[0] = ffx * th - rrx; fny[0] = ffy * th;        fnz[0] = ffz * th - rrz;
            fnx[1] = ffx * th + rrx; fny[1] = ffy * th;        fnz[1] = ffz * th + rrz;
            fnx[2] = ffx * tv - uux; fny[2] = ffy * tv - uuy;  fnz[2] = ffz * tv - uuz;
            fnx[3] = ffx * tv + uux; fny[3] = ffy * tv + uuy;  fnz[3] = ffz * tv + uuz;
            fnx[4] = ffx;            fny[4] = ffy;             fnz[4] = ffz;
        }

        // caixa em coordenadas relativas ao olho
        boolean boxVisible(float x0, float y0, float z0, float x1, float y1, float z1) {
            for (int i = 0; i < 5; i++) {
                float nx = fnx[i], ny = fny[i], nz = fnz[i];
                float x = nx >= 0f ? x1 : x0;
                float y = ny >= 0f ? y1 : y0;
                float z = nz >= 0f ? z1 : z0;
                if (nx * x + ny * y + nz * z < 0f) return false;
            }
            return true;
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
            float spd = (wet ? SPEED * 0.6f : SPEED) * (speedT > 0f ? 1.8f : 1f);
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
            if (slots[sel] == IT_HAMMER) { doHammer(); return; }
            if (!raycast()) return;
            int x = hit[0], y = hit[1], z = hit[2];
            if (y <= 0) return; // bedrock la embaixo nao quebra
            int t = get(x, y, z);
            if (t <= 0) return;
            set(x, y, z, AIR);
            for (int i = 0; i < 12; i++) spawn(x + 0.5f, y + 0.5f, z + 0.5f, pcolor(t));
        }

        void doPlace() {
            int blk = slots[sel];
            if (blk <= 0) return; // slot vazio
            if (blk >= NB) { useItem(blk); return; } // item: usa em vez de por
            if (!raycast()) return;
            int x = hit[3], y = hit[4], z = hit[5];
            int t = get(x, y, z);
            if (t != AIR && t != WATER) return; // inclui chunk nao carregado
            if (overlapsPlayer(x, y, z)) return;
            set(x, y, z, blk);
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

        void explode(int cx, int cy, int cz) { explode(cx, cy, cz, 2, 5); }

        void explode(int cx, int cy, int cz, int rng, int r2) {
            for (int dx = -rng; dx <= rng; dx++) {
                for (int dy = -rng; dy <= rng; dy++) {
                    for (int dz = -rng; dz <= rng; dz++) {
                        if (dx * dx + dy * dy + dz * dz > r2) continue;
                        int x = cx + dx, y = cy + dy, z = cz + dz;
                        if (y <= 0) continue;
                        int t = get(x, y, z);
                        if (t <= 0 || t == WATER) continue;
                        set(x, y, z, AIR);
                        spawn(x + 0.5f, y + 0.5f, z + 0.5f, pcolor(t));
                        spawn(x + 0.5f, y + 0.5f, z + 0.5f, pcolor(CAOS));
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

        // ---------- dia e noite ----------
        static float smooth(float a, float b, float x) {
            float t = (x - a) / (b - a);
            if (t < 0f) t = 0f;
            if (t > 1f) t = 1f;
            return t * t * (3f - 2f * t);
        }

        // o dia (55%) passa mais devagar que a noite (45%) pra noite nao enjoar
        static float angleOf(float t) {
            final float pi = 3.14159265f;
            return t < 0.55f ? (t / 0.55f) * pi : pi + ((t - 0.55f) / 0.45f) * pi;
        }

        void updateTime(float dt) {
            dayT += dt / DAY_LEN;
            if (dayT >= 1f) dayT -= 1f;
            if (speedT > 0f) speedT -= dt;
        }

        void nextTime() {
            float n = PRESET[0] + 1f;
            for (int i = 0; i < PRESET.length; i++) {
                if (PRESET[i] > dayT + 0.01f) { n = PRESET[i]; break; }
            }
            dayT = n >= 1f ? n - 1f : n;
        }

        // calcula cor do ceu / escuridao do mundo pela altura do sol
        void computeSky() {
            float a = angleOf(dayT);
            skyAng = a;
            float s = (float) Math.sin(a);
            sinA = s;
            float k = s >= 0f ? smooth(0f, 0.35f, s) : smooth(0f, 0.3f, -s);
            float[] ts = s >= 0f ? SKY_DAY : SKY_NIGHT;
            float[] tm = s >= 0f ? MUL_DAY : MUL_NIGHT;
            skyR = SKY_SET[0] + (ts[0] - SKY_SET[0]) * k;
            skyG = SKY_SET[1] + (ts[1] - SKY_SET[1]) * k;
            skyB = SKY_SET[2] + (ts[2] - SKY_SET[2]) * k;
            mulR = MUL_SET[0] + (tm[0] - MUL_SET[0]) * k;
            mulG = MUL_SET[1] + (tm[1] - MUL_SET[1]) * k;
            mulB = MUL_SET[2] + (tm[2] - MUL_SET[2]) * k;
            starA = smooth(0f, 0.3f, -s);
            if (slots[sel] == IT_TORCH) { // tocha na mao clareia a noite (e deixa quentinho)
                mulR = Math.max(mulR, mulR + (1f - mulR) * 0.6f);
                mulG = Math.max(mulG, mulG + (0.88f - mulG) * 0.6f);
                mulB = Math.max(mulB, mulB + (0.7f - mulB) * 0.6f);
            }
        }

        void resetState(GL10 gl) {
            gl.glEnable(GL10.GL_DEPTH_TEST);
            gl.glDepthMask(true);
            gl.glEnable(GL10.GL_CULL_FACE);
            gl.glEnable(GL10.GL_ALPHA_TEST);
            gl.glEnable(GL10.GL_FOG);
            gl.glDisable(GL10.GL_BLEND);
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        }

        // multiplica a tela inteira pela cor da noite (dia = 1,1,1 = nao desenha nada)
        void drawNightOverlay(GL10 gl) {
            if (mulR > 0.995f && mulG > 0.995f && mulB > 0.995f) return;
            gl.glDisable(GL10.GL_DEPTH_TEST);
            gl.glDisable(GL10.GL_FOG);
            gl.glDisable(GL10.GL_CULL_FACE);
            gl.glDisable(GL10.GL_ALPHA_TEST);
            gl.glDisable(GL10.GL_TEXTURE_2D);
            gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
            gl.glEnable(GL10.GL_BLEND);
            gl.glBlendFunc(GL10.GL_ZERO, GL10.GL_SRC_COLOR);
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            gl.glColor4f(mulR, mulG, mulB, 1f);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, fsq);
            gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
            gl.glPopMatrix();
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glPopMatrix();
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glDisable(GL10.GL_BLEND);
        }

        // quadrado de lado 2h no plano x = x, centrado em (cy, cz)
        void quadX(GL10 gl, float x, float cy, float cz, float h) {
            qb.clear();
            qb.put(x); qb.put(cy - h); qb.put(cz - h);
            qb.put(x); qb.put(cy + h); qb.put(cz - h);
            qb.put(x); qb.put(cy - h); qb.put(cz + h);
            qb.put(x); qb.put(cy + h); qb.put(cz + h);
            qb.flip();
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, qb);
            gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
        }

        void starRange(GL10 gl, int from, int to, float size, float sa, float ca) {
            svb.clear();
            int n = 0;
            for (int i = from; i < to; i++) {
                float x = star[i * 3], y = star[i * 3 + 1], z = star[i * 3 + 2];
                float yy = (x * sa + y * ca) * COS_T - z * SIN_T; // altura da estrela depois de girar
                if (yy < 0.02f) continue;                          // abaixo do horizonte
                svb.put(x * SKY_D); svb.put(y * SKY_D); svb.put(z * SKY_D);
                n++;
            }
            if (n == 0) return;
            svb.flip();
            gl.glPointSize(size);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, svb);
            gl.glDrawArrays(GL10.GL_POINTS, 0, n);
        }

        // sol, lua e estrelas: desenhados por cima da escuridao da noite, mas atras do terreno (teste de profundidade)
        void drawSky(GL10 gl, boolean under) {
            if (under) return;
            float a = skyAng;
            float sa = sinA, ca = (float) Math.cos(a);
            float elev = sa * COS_T;                       // altura do sol no ceu
            float sunA = smooth(-0.03f, 0.07f, elev);
            float moonA = smooth(-0.03f, 0.07f, -elev);
            gl.glDisable(GL10.GL_FOG);
            gl.glDisable(GL10.GL_CULL_FACE);
            gl.glDisable(GL10.GL_ALPHA_TEST);
            gl.glDisable(GL10.GL_TEXTURE_2D);
            gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
            gl.glEnable(GL10.GL_DEPTH_TEST);
            gl.glDepthMask(false);
            gl.glEnable(GL10.GL_BLEND);
            gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            gl.glRotatef(pitch, 1f, 0f, 0f);
            gl.glRotatef(yaw, 0f, 1f, 0f);
            gl.glRotatef(TILT, 1f, 0f, 0f);
            gl.glRotatef((float) Math.toDegrees(a), 0f, 0f, 1f);
            if (starA > 0.01f) {
                gl.glColor4f(1f, 1f, 1f, starA);
                starRange(gl, 0, NSTAR - 40, 3f, sa, ca);
                gl.glColor4f(0.85f, 0.92f, 1f, starA);
                starRange(gl, NSTAR - 40, NSTAR, 6f, sa, ca);
            }
            if (sunA > 0.01f) {
                float w = smooth(0f, 0.3f, sa);            // perto do horizonte o sol fica laranja
                float cg = 0.55f + 0.43f * w, cb = 0.25f + 0.55f * w;
                gl.glColor4f(1f, cg, cb, 0.22f * sunA);
                quadX(gl, SKY_D, 0f, 0f, 17f);
                gl.glColor4f(1f, cg, cb, 0.35f * sunA);
                quadX(gl, SKY_D, 0f, 0f, 11f);
                gl.glColor4f(1f, cg, cb, sunA);
                quadX(gl, SKY_D, 0f, 0f, 6.5f);
                gl.glColor4f(1f, 1f, 0.92f, sunA);
                quadX(gl, SKY_D, 0f, 0f, 4.2f);
            }
            if (moonA > 0.01f) {
                gl.glColor4f(0.86f, 0.9f, 1f, moonA);
                quadX(gl, -SKY_D, 0f, 0f, 5.5f);
                gl.glColor4f(0.66f, 0.7f, 0.84f, moonA);
                quadX(gl, -SKY_D, 1.8f, 1.5f, 1.4f);
                quadX(gl, -SKY_D, -2f, -1.2f, 1.1f);
                quadX(gl, -SKY_D, -0.8f, 2.6f, 0.9f);
            }
            gl.glPopMatrix();
            gl.glDepthMask(true);
            gl.glDisable(GL10.GL_BLEND);
        }

        // ---------- itens ----------
        void useItem(int it) {
            if (it == IT_BOMB) {
                throwBomb();
            } else if (it == IT_CLOCK) {
                nextTime();
            } else if (it == IT_FEATHER) {
                vy = 15f;
                onGround = false;
                for (int i = 0; i < 10; i++) spawn(px, py + 0.1f, pz, 0xF4F4FF);
            } else if (it == IT_POTION) {
                speedT = 25f;
                for (int i = 0; i < 14; i++) spawn(px, py + 1f, pz, 0x30D8E0);
            }
            // a tocha nao faz nada ao tocar: so clareia a noite enquanto esta na mao
        }

        void doHammer() {
            if (!raycast()) return;
            int x = hit[0], y = hit[1], z = hit[2];
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++)
                        breakOne(x + dx, y + dy, z + dz, 4);
        }

        void breakOne(int x, int y, int z, int parts) {
            if (y <= 0) return; // bedrock nao quebra
            int t = get(x, y, z);
            if (t <= 0 || t == WATER) return;
            set(x, y, z, AIR);
            for (int i = 0; i < parts; i++) spawn(x + 0.5f, y + 0.5f, z + 0.5f, pcolor(t));
        }

        void throwBomb() {
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            float dx = (float) (Math.sin(yr) * Math.cos(pr));
            float dy = (float) (-Math.sin(pr));
            float dz = (float) (-Math.cos(yr) * Math.cos(pr));
            int i = bnext;
            bnext = (bnext + 1) % MB;
            bpos[i * 3] = px + dx * 0.6f;
            bpos[i * 3 + 1] = py + EYE + dy * 0.6f - 0.1f;
            bpos[i * 3 + 2] = pz + dz * 0.6f;
            bvel[i * 3] = dx * 17f;
            bvel[i * 3 + 1] = dy * 17f + 3f;
            bvel[i * 3 + 2] = dz * 17f;
            blife[i] = 2.6f;
        }

        void blast(int cx, int cy, int cz) {
            explode(cx, cy, cz, 3, 10);
            // a explosao joga o jogador pra cima (rocket jump)
            float dx = px - (cx + 0.5f), dy = py + 0.9f - (cy + 0.5f), dz = pz - (cz + 0.5f);
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 7f) {
                vy = Math.max(vy, 14f * (1f - d / 7f));
                onGround = false;
            }
        }

        void updateBombs(float dt) {
            for (int i = 0; i < MB; i++) {
                if (blife[i] <= 0f) continue;
                blife[i] -= dt;
                bvel[i * 3 + 1] -= 18f * dt;
                float mx = bvel[i * 3] * dt, my = bvel[i * 3 + 1] * dt, mz = bvel[i * 3 + 2] * dt;
                int steps = (int) Math.ceil(Math.max(Math.abs(mx), Math.max(Math.abs(my), Math.abs(mz))) / 0.3f);
                if (steps < 1) steps = 1;
                int res = 0; // 0 voando, 1 explode, 2 some (caiu em chunk que nao existe)
                for (int s = 0; s < steps; s++) {
                    float nx = bpos[i * 3] + mx / steps;
                    float ny = bpos[i * 3 + 1] + my / steps;
                    float nz = bpos[i * 3 + 2] + mz / steps;
                    int t = get((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz));
                    if (t == -1) { res = 2; break; }
                    if (t != AIR && t != WATER) { res = 1; break; }
                    bpos[i * 3] = nx;
                    bpos[i * 3 + 1] = ny;
                    bpos[i * 3 + 2] = nz;
                }
                if (res == 0 && blife[i] <= 0f) res = 1; // pavio acabou
                if (res != 0) {
                    blife[i] = 0f;
                    if (res == 1) {
                        blast((int) Math.floor(bpos[i * 3]), (int) Math.floor(bpos[i * 3 + 1]), (int) Math.floor(bpos[i * 3 + 2]));
                    }
                } else {
                    // rastro de faiscas
                    spawn(bpos[i * 3], bpos[i * 3 + 1], bpos[i * 3 + 2], rnd.nextFloat() < 0.5f ? pcolor(CAOS) : 0xFFB040);
                }
            }
        }

        void drawBombs(GL10 gl) {
            int n = 0;
            bvb.clear();
            bcb.clear();
            for (int i = 0; i < MB; i++) {
                if (blife[i] <= 0f) continue;
                bvb.put(bpos[i * 3]);
                bvb.put(bpos[i * 3 + 1]);
                bvb.put(bpos[i * 3 + 2]);
                boolean flash = blife[i] < 1f && (((int) (blife[i] * 10f)) & 1) == 0; // pisca quando vai explodir
                if (flash) {
                    bcb.put((byte) 255); bcb.put((byte) 60); bcb.put((byte) 220); bcb.put((byte) 255);
                } else {
                    bcb.put((byte) 42); bcb.put((byte) 42); bcb.put((byte) 51); bcb.put((byte) 255);
                }
                n++;
            }
            if (n == 0) return;
            bvb.flip();
            bcb.flip();
            gl.glPointSize(22f);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, bvb);
            gl.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, 0, bcb);
            gl.glDrawArrays(GL10.GL_POINTS, 0, n);
        }

        // ---------- renderer ----------
        void applyFog(GL10 gl, boolean under) {
            float[] fc = under ? FOG_WATER : new float[]{skyR, skyG, skyB, 1f};
            gl.glClearColor(fc[0], fc[1], fc[2], 1f);
            gl.glFogfv(GL10.GL_FOG_COLOR, fc, 0);
            gl.glFogf(GL10.GL_FOG_START, under ? 1f : FOG_S);
            gl.glFogf(GL10.GL_FOG_END, under ? 22f : FOG_E);
        }

        // sobe o atlas de texturas pra GPU (pixelado de perto, suave de longe)
        void uploadTexture(GL10 gl) {
            int[] ids = new int[1];
            gl.glGenTextures(1, ids, 0);
            texId = ids[0];
            gl.glBindTexture(GL10.GL_TEXTURE_2D, texId);
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST_MIPMAP_LINEAR);
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL11.GL_GENERATE_MIPMAP, (float) GL10.GL_TRUE);
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, Tex.atlas(), 0);
            gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig c) {
            gl.glEnable(GL10.GL_DEPTH_TEST);
            gl.glEnable(GL10.GL_CULL_FACE);   // nao desenha o lado de tras das faces
            gl.glCullFace(GL10.GL_BACK);
            gl.glFrontFace(GL10.GL_CCW);
            gl.glEnable(GL10.GL_ALPHA_TEST);  // buracos da folha e do vidro
            gl.glAlphaFunc(GL10.GL_GREATER, 0.5f);
            gl.glEnable(GL10.GL_FOG);
            gl.glFogf(GL10.GL_FOG_MODE, GL10.GL_LINEAR);
            wasUnder = false;
            applyFog(gl, false);
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
            gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);
            uploadTexture(gl);
            startWorkers();
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
            aspect = (float) w / (float) h;
            float right = top * aspect;
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
            updateBombs(dt);
            updateTime(dt);
            updateChunks();

            boolean under = isWater(px, py + EYE, pz);
            computeSky();
            wasUnder = under;
            applyFog(gl, under); // a cor do ceu muda o tempo todo

            resetState(gl);
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glLoadIdentity();
            gl.glRotatef(pitch, 1f, 0f, 0f);
            gl.glRotatef(yaw, 0f, 1f, 0f);

            // quais chunks aparecem na tela (frustum culling)
            setupFrustum();
            float ey = py + EYE;
            int pcx = ((int) Math.floor(px)) >> 4, pcz = ((int) Math.floor(pz)) >> 4;
            int nvis = 0;
            for (Chunk c : chunks.values()) {
                if (c.mo == null && c.mw == null) continue;
                if (Math.abs(c.cx - pcx) > RENDER_R || Math.abs(c.cz - pcz) > RENDER_R) continue;
                float x0 = c.cx * CS - px, z0 = c.cz * CS - pz;
                if (!boxVisible(x0, -ey, z0, x0 + CS, c.maxY + 1 - ey, z0 + CS)) continue;
                if (nvis < vis.length) vis[nvis++] = c;
            }

            gl.glEnable(GL10.GL_TEXTURE_2D);
            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            gl.glBindTexture(GL10.GL_TEXTURE_2D, texId);
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 1) { // agua: translucida, depois de tudo opaco
                    gl.glEnable(GL10.GL_BLEND);
                    gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
                    gl.glDepthMask(false);
                }
                for (int i = 0; i < nvis; i++) {
                    Chunk c = vis[i];
                    Mesh m = (pass == 0) ? c.mo : c.mw;
                    if (m == null) continue;
                    gl.glPushMatrix();
                    gl.glTranslatef(c.cx * CS - px, -ey, c.cz * CS - pz);
                    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, m.v);
                    gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, m.t);
                    gl.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, 0, m.c);
                    gl.glDrawArrays(GL10.GL_TRIANGLES, 0, m.n);
                    gl.glPopMatrix();
                }
            }
            gl.glDepthMask(true);
            gl.glDisable(GL10.GL_BLEND);
            gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            gl.glDisable(GL10.GL_TEXTURE_2D);

            gl.glPushMatrix();
            gl.glTranslatef(-px, -ey, -pz);
            drawParticles(gl, dt);
            drawBombs(gl);
            drawNightOverlay(gl);
            drawSky(gl, under);
            gl.glPopMatrix();
        }
    }

    // =====================================================================
    static class Hud extends View {
        // itens do inventario criativo: blocos 1..30 + limpar (0)
        static final int[] INV = new int[Game.NALL];
        static {
            for (int i = 1; i < Game.NALL; i++) INV[i - 1] = i;
            INV[Game.NALL - 1] = 0;
        }
        static final int COLS = 8, ROWS = 5;
        static final float[] SRC = {0f, 0f, 16f, 0f, 0f, 16f};

        final Game g;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ipT = new Paint(), ipL = new Paint(), ipR = new Paint(); // icones (sem suavizar)
        final Matrix mx = new Matrix();
        final float[] dst = new float[6];
        final Handler h = new Handler();
        final int[] role = new int[16]; // 0 nada, 1 joystick, 2 olhar, 3 pulo, 4 ui
        final float[] lx = new float[16], ly = new float[16], ax = new float[16], ay = new float[16];
        final boolean[] moved = new boolean[16], done = new boolean[16];
        final Runnable[] lp = new Runnable[16];
        final RectF novo = new RectF(), inv = new RectF(), panel = new RectF(), rr = new RectF(), cell = new RectF(), ir = new RectF();
        boolean joyOn, invOpen;
        float joyCx, joyCy, joyKx, joyKy;

        Hud(Context c, Game game) {
            super(c);
            g = game;
            ipT.setFilterBitmap(false);
            ipL.setFilterBitmap(false);
            ipR.setFilterBitmap(false);
            ipL.setColorFilter(new LightingColorFilter(0xFFD0D0D0, 0));
            ipR.setColorFilter(new LightingColorFilter(0xFF9A9A9A, 0));
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
        float hl() { float s = hs(); return (getWidth() - (Game.HB * s + (Game.HB - 1) * s * 0.15f)) / 2f; }
        float ht() { return getHeight() - hs() - getHeight() * 0.035f; }

        void layout() {
            float W = getWidth(), H = getHeight();
            novo.set(W - H * 0.38f, H * 0.02f, W - H * 0.02f, H * 0.12f);
            inv.set(W - H * 0.60f, H * 0.02f, W - H * 0.40f, H * 0.12f);
            float pw = Math.min(W * 0.86f, H * 1.75f), ph = H * 0.72f;
            panel.set((W - pw) / 2f, H * 0.13f, (W + pw) / 2f, H * 0.13f + ph);
        }

        int hotHit(float x, float y) {
            float s = hs(), l = hl(), t = ht();
            if (y < t || y > t + s) return -1;
            for (int i = 0; i < Game.HB; i++) {
                float x0 = l + i * s * 1.15f;
                if (x >= x0 && x <= x0 + s) return i;
            }
            return -1;
        }

        void cellRect(int i, RectF out) {
            float H = getHeight();
            float pad = H * 0.018f, title = H * 0.065f;
            float cw = (panel.width() - pad * (COLS + 1)) / COLS;
            float ch = (panel.height() - title - pad * (ROWS + 1)) / ROWS;
            int cx = i % COLS, cy = i / COLS;
            float x0 = panel.left + pad + cx * (cw + pad);
            float y0 = panel.top + title + pad + cy * (ch + pad);
            out.set(x0, y0, x0 + cw, y0 + ch);
        }

        int invHit(float x, float y) {
            for (int i = 0; i < INV.length; i++) {
                cellRect(i, cell);
                if (cell.contains(x, y)) return i;
            }
            return -1;
        }

        String clockText() {
            float hr = (6f + g.skyAng / 6.2831855f * 24f) % 24f;
            if (hr < 0f) hr += 24f;
            int hh = (int) hr, mm = (int) ((hr - hh) * 60f);
            return (hh < 10 ? "0" : "") + hh + ":" + (mm < 10 ? "0" : "") + mm;
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
            if (inv.contains(x, y)) {
                role[id] = 4;
                done[id] = true;
                invOpen = !invOpen;
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
            if (invOpen) { // com o inventario aberto so mexe nele
                role[id] = 4;
                done[id] = true;
                int ci = invHit(x, y);
                if (ci >= 0) g.slots[g.sel] = INV[ci];
                else if (!panel.contains(x, y)) invOpen = false;
                return;
            }
            if (dist(x, y, jcx(), jcy()) < jr() * 1.25f) {
                role[id] = 3;
                g.jumpHeld = true;
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

        // desenha um tile (16x16) esticado num paralelogramo: origem, ponta do u, ponta do v
        void isoFace(Canvas c, Bitmap bmp, float ox, float oy, float ux, float uy, float vx, float vy, Paint pt) {
            dst[0] = ox; dst[1] = oy; dst[2] = ux; dst[3] = uy; dst[4] = vx; dst[5] = vy;
            mx.setPolyToPoly(SRC, 0, dst, 0, 3);
            c.drawBitmap(bmp, mx, pt);
        }

        // icone do bloco em cubo isometrico, com as texturas de verdade
        void drawIcon(Canvas c, int b, float x, float y, float s) {
            if (b >= Game.NB) { // item: sprite 2D em vez de cubo
                Bitmap bm = Game.Tex.tile(Game.ITEM_TILE[b - Game.NB]);
                ir.set(x + s * 0.04f, y + s * 0.04f, x + s * 0.96f, y + s * 0.96f);
                c.drawBitmap(bm, null, ir, ipT);
                return;
            }
            Bitmap top = Game.Tex.tile(Game.TILE[b][0]);
            Bitmap side = Game.Tex.tile(Game.TILE[b][1]);
            float cx = x + s * 0.5f;
            float rY = y + s * 0.25f, cY = y + s * 0.5f, blY = y + s * 0.75f, bY = y + s;
            isoFace(c, top, cx, y, x + s, rY, x, rY, ipT);          // topo: T -> R, T -> L
            isoFace(c, side, x, rY, cx, cY, x, blY, ipL);           // esquerda: L -> C, L -> BL
            isoFace(c, side, cx, cY, x + s, rY, cx, bY, ipR);       // direita: C -> R, C -> B
        }

        void fitText(Canvas c, String s, float cx, float base, float size, float maxW) {
            p.setTextSize(size);
            float w = p.measureText(s);
            if (w > maxW) p.setTextSize(size * maxW / w);
            c.drawText(s, cx, base, p);
        }

        void button(Canvas c, RectF r, String label, float H, boolean on) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? 0xAA3A7A3A : 0x66000000);
            c.drawRoundRect(r, H * 0.02f, H * 0.02f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xCCFFFFFF);
            c.drawRoundRect(r, H * 0.02f, H * 0.02f, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(H * 0.04f);
            c.drawText(label, r.centerX(), r.centerY() + H * 0.014f, p);
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
            c.drawText("toca = bota/usa | segura = quebra", H * 0.03f, H * 0.06f, p);
            // debug: posicao e chunks carregados
            p.setTextSize(H * 0.026f);
            c.drawText("XYZ " + (int) Math.floor(g.px) + " " + (int) Math.floor(g.py) + " "
                    + (int) Math.floor(g.pz) + "  chunks " + g.chunkCount + "  " + clockText(), H * 0.03f, H * 0.1f, p);
            if (g.speedT > 0f) c.drawText("VELOCIDADE " + (int) Math.ceil(g.speedT) + "s", H * 0.03f, H * 0.14f, p);
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
            // botoes de cima
            button(c, novo, "NOVO MUNDO", H, false);
            button(c, inv, "INV", H, invOpen);

            // inventario criativo
            if (invOpen) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x66000000);
                c.drawRect(0, 0, W, H, p);
                p.setColor(0xEE222222);
                c.drawRoundRect(panel, H * 0.03f, H * 0.03f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(H * 0.006f);
                p.setColor(0xCCFFFFFF);
                c.drawRoundRect(panel, H * 0.03f, H * 0.03f, p);
                p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(0xFFFFFFFF);
                fitText(c, "CRIATIVO - escolhe um slot la embaixo e toca no bloco", panel.centerX(),
                        panel.top + H * 0.048f, H * 0.032f, panel.width() * 0.94f);
                int cur = g.slots[g.sel];
                for (int i = 0; i < INV.length; i++) {
                    cellRect(i, cell);
                    int b = INV[i];
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFF3C3C3C);
                    c.drawRoundRect(cell, H * 0.012f, H * 0.012f, p);
                    p.setColor(0xFFFFFFFF);
                    p.setTextAlign(Paint.Align.CENTER);
                    if (b > 0) {
                        float sz = Math.min(cell.width(), cell.height()) * 0.62f;
                        drawIcon(c, b, cell.centerX() - sz / 2f, cell.top + cell.height() * 0.05f, sz);
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(0xFFFFFFFF);
                        p.setTextAlign(Paint.Align.CENTER);
                        fitText(c, Game.NOMES[b], cell.centerX(), cell.bottom - H * 0.012f, H * 0.024f,
                                cell.width() * 0.94f);
                    } else {
                        fitText(c, "LIMPAR", cell.centerX(), cell.centerY() + H * 0.01f, H * 0.03f,
                                cell.width() * 0.9f);
                    }
                    if (b > 0 && b == cur) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(H * 0.008f);
                        p.setColor(0xFFFFFFFF);
                        c.drawRoundRect(cell, H * 0.012f, H * 0.012f, p);
                    }
                }
            }

            // hotbar (comeca vazia)
            float s = hs(), l = hl(), tp = ht();
            for (int i = 0; i < Game.HB; i++) {
                float x0 = l + i * s * 1.15f;
                rr.set(x0, tp, x0 + s, tp + s);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x99000000);
                c.drawRoundRect(rr, s * 0.12f, s * 0.12f, p);
                int b = g.slots[i];
                if (b > 0) drawIcon(c, b, x0 + s * 0.15f, tp + s * 0.12f, s * 0.7f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(i == g.sel ? s * 0.1f : s * 0.04f);
                p.setColor(i == g.sel ? 0xFFFFFFFF : 0x99000000);
                c.drawRoundRect(rr, s * 0.12f, s * 0.12f, p);
            }
            int held = g.slots[g.sel];
            if (held > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(H * 0.035f);
                c.drawText(Game.NOMES[held], W / 2f, tp - H * 0.015f, p);
            }
        }
    }
}
