package com.example.kingdomsiege;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new Game(this));
    }
}

final class Audio {
    private final Context ctx;
    private final SoundPool pool;
    private final Map<String, Integer> sfx = new HashMap<>();
    private MediaPlayer music;
    private String musicName = "";
    boolean sfxOn = true, musicOn = true;

    Audio(Context ctx) {
        this.ctx = ctx;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        pool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build();
        String[] names = {"sfx_hit", "sfx_castle", "sfx_summon", "sfx_spell", "sfx_die",
                          "sfx_heal", "sfx_win", "sfx_lose", "sfx_click", "sfx_boss"};
        for (String n : names) {
            try (AssetFileDescriptor afd = ctx.getAssets().openFd(n + ".wav")) {
                sfx.put(n, pool.load(afd, 1));
            } catch (IOException ignored) { }
        }
    }

    void play(String n) {
        Integer id = sfx.get(n);
        if (sfxOn && id != null) pool.play(id, 0.8f, 0.8f, 1, 0, 1f);
    }

    void music(String name) {
        if (name.equals(musicName)) return;
        stopMusic();
        musicName = name;
        if (!musicOn) return;
        try (AssetFileDescriptor afd = ctx.getAssets().openFd(name + ".wav")) {
            music = new MediaPlayer();
            music.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            music.setLooping(true);
            music.setVolume(0.35f, 0.35f);
            music.prepare();
            music.start();
        } catch (IOException e) {
            music = null;
        }
    }

    void stopMusic() {
        if (music != null) {
            try { music.stop(); } catch (IllegalStateException ignored) { }
            music.release();
            music = null;
        }
        musicName = "";
    }

    void release() {
        stopMusic();
        pool.release();
    }
}

class Game extends View {
    // сцены
    static final int MENU = 0, MAP = 1, KINGDOM = 2, COLLECTION = 3, SETTINGS = 4, BATTLE = 5, RESULT = 6, REWARDS = 7;

    static final int LANES = 5, COLS = 14, MAX_MANA = 15, START_MANA = 5, STAGES = 10, KINGDOMS = 50, MAX_STARS_LEVEL = 2000;
    static final int DRAGON = 4;

    static final float LOG_Y = 0.72f, STATUS_Y = 0.77f, HAND_Y = 0.80f;

    // ---- данные из концепт-картинки ----
    static final String[] THEME_NAMES = {
        "Зелёные поля", "Пустыня", "Снежные горы", "Тёмный лес", "Ледяной замок",
        "Вулкан", "Разрушенный город", "Болото теней", "Крепость врага", "Последняя битва"
    };
    static final int[] THEME_PIC = {0, 1, 2, 3, 4, 5, 5, 3, 4, 5};   // номер картинки lvl_N - 1
    static final String[] PRE = {"Элар", "Драк", "Мор", "Велин", "Торн", "Ата", "Сал", "Ирон", "Лир", "Кор", "Вейл", "Рат"};
    static final String[] SUF = {"дия", "гар", "ония", "мир", "тан", "вен", "холь", "рия", "дан", "ланд"};
    static final String[] TITLES = {"Король", "Королева", "Император", "Тиран", "Регент", "Лорд"};

    // карты: индекс = тип карты
    static final String[] CARD_NAME = {"Пехотинец", "Лучник", "Кавалерия", "Маг", "Осадная машина", "Огненный шар", "Защита", "Лечение"};
    static final String[] CARD_ART = {"card_pehot", "card_luch", "card_kav", "card_mag", "card_osad", "card_fire", "card_shield", "card_heal"};
    static final int[] CARD_COST = {1, 2, 3, 4, 5, 3, 2, 2};

    // армия игрока: 0..2 призываются картами, 3 и 4 — это урон заклинаний Маг и Осадная машина
    static final String[] UNIT_NAME = {"Пехотинец", "Лучник", "Кавалерия", "Маг", "Осадная машина"};
    static final String[] UNIT_ART = {"unit_pehot", "unit_luch", "unit_kav", "unit_mag", "unit_osad"};
    static final int[] UNIT_HP = {100, 70, 150, 60, 200};
    static final int[] UNIT_DMG = {15, 20, 30, 40, 50};
    static final int[] UNIT_RANGE = {1, 4, 1, 3, 1};
    static final int[] UNIT_SPEED = {1, 1, 2, 1, 1};

    // враги
    static final String[] EN_NAME = {"Гоблин-лучник", "Орк", "Тролль", "Тёмный маг", "Дракон", "Ядовитый убийца", "Каменный голем", "Адский рыцарь"};
    static final String[] EN_ART = {"enemy_gob", "enemy_orc", "enemy_troll", "enemy_dark", "enemy_dragon", "enemy_assassin", "enemy_golem", "enemy_hellknight"};
    static final int[] EN_HP = {60, 90, 180, 70, 300, 85, 260, 210};
    static final int[] EN_DMG = {18, 12, 35, 45, 70, 32, 28, 42};
    static final int[] EN_RANGE = {4, 1, 1, 3, 2, 1, 1, 1};
    static final int[] EN_SPEED = {1, 1, 1, 1, 2, 2, 1, 1};
    static final int[] EN_TIER = {0, 1, 2, 2, 3, 1, 2, 2};

    // боссы: 0 дракон (огонь по линии), 1 тролль-вождь (регенерация), 2 тёмный лорд (призыв), 3 орк-вождь (ярость)
    static final String[] BOSS_NAME = {"Дракон", "Тролль-вождь", "Тёмный лорд", "Орк-вождь"};
    static final String[] BOSS_ART = {"enemy_dragon", "enemy_troll", "enemy_dark", "enemy_orc"};
    static final int[] BOSS_HP = {300, 500, 350, 400};
    static final int[] BOSS_DMG = {70, 50, 45, 60};
    static final int[] BOSS_RANGE = {2, 1, 3, 1};
    static final int[] BOSS_SPEED = {2, 1, 1, 1};

    // эффекты
    static final class Particle {
        float x, y, vx, vy; int life; int color;
    }
    final List<Particle> particles = new ArrayList<>();
    int shake = 0, flash = 0;
    Audio audio;

    static final class FloatText {
        float x, y; String s; int color; int life;
        FloatText(float x, float y, String s, int color) { this.x = x; this.y = y; this.s = s; this.color = color; this.life = 40; }
    }
    final List<FloatText> floats = new ArrayList<>();

    // ---- авто-режим: бой идёт сам ----
    boolean auto = true;
    final Runnable autoTick = new Runnable() {
        @Override
        public void run() { autoTurn(); }
    };

    void scheduleAuto(long delay) {
        removeCallbacks(autoTick);
        if (auto && scene == BATTLE) postDelayed(autoTick, delay);
    }

    void autoTurn() {
        if (scene != BATTLE || !auto) return;
        // Автобой обновляет и игровую логику, и экран сам, без ручных нажатий.
        int guard = 0;
        while (guard++ < 6 && scene == BATTLE && autoPlayOne()) { }
        if (scene == BATTLE) endTurn();
        postInvalidate();
        if (scene == BATTLE && auto) scheduleAuto(900);
    }

    int enemyCountInLane(int lane) {
        int n = 0;
        for (Unit u : units) if (u.alive() && !u.player && u.lane == lane) n++;
        return n;
    }

    int nearestEnemyCol(int lane) {
        int best = COLS;
        for (Unit u : units) if (u.alive() && !u.player && u.lane == lane && u.col < best) best = u.col;
        return best;
    }

    int freeLaneForSummon() {
        int bestLane = -1, bestOur = Integer.MAX_VALUE;
        for (int lane = 0; lane < LANES; lane++) {
            if (occupied(lane, 0)) continue;
            int our = 0;
            for (Unit u : units) if (u.alive() && u.player && u.lane == lane) our++;
            if (our < bestOur) { bestOur = our; bestLane = lane; }
        }
        return bestLane;
    }

    // одно разумное действие ИИ; возвращает true, если карта разыграна
    boolean autoPlayOne() {
        int enemies = 0, lanesEnemy = 0;
        for (Unit u : units) if (u.alive() && !u.player) enemies++;
        boolean nearCastle = false;
        for (Unit u : units) if (u.alive() && !u.player && u.col <= 3) nearCastle = true;

        if (pHp < pMax / 2 && mana >= CARD_COST[7]) { playCard(7, 0); return true; }
        if (pShield == 0 && nearCastle && mana >= CARD_COST[6]) { playCard(6, 0); return true; }

        int bestLane = -1, bestCount = 0;
        for (int lane = 0; lane < LANES; lane++) {
            int n = enemyCountInLane(lane);
            if (n > bestCount) { bestCount = n; bestLane = lane; }
        }
        if (bestLane >= 0) {
            if (bestCount >= 2 && mana >= CARD_COST[5]) { playCard(5, bestLane); return true; }
            if (nearestEnemyCol(bestLane) <= 6 && mana >= CARD_COST[3]) { playCard(3, bestLane); return true; }
        }
        if (enemies >= 4 && mana >= CARD_COST[4]) { playCard(4, 0); return true; }

        int lane = freeLaneForSummon();
        if (lane >= 0) {
            if (mana >= CARD_COST[2]) { playCard(2, lane); return true; }
            if (mana >= CARD_COST[1]) { playCard(1, lane); return true; }
            if (mana >= CARD_COST[0]) { playCard(0, lane); return true; }
        }
        return false;
    }

    static final class Unit {
        boolean player;
        int type;
        String name, art;
        int lane, col, hp, maxHp, dmg, range, speed;
        boolean boss; int bt;
        boolean alive() { return hp > 0; }
    }

    final SharedPreferences prefs;
    final Random rnd = new Random();
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Map<String, Bitmap> cache = new HashMap<>();

    int scene = MENU;
    int unlocked = 1, gold = 0, totalWins = 0, dailyStreak = 0;
    String lastDaily = "";
    boolean[] claimedAchievements = new boolean[4];
    int[] upg = new int[6];
    int[] stars = new int[MAX_STARS_LEVEL + 1];
    int curK = 0;
    float scrollY = 0, startScroll = 0, downY = 0;
    boolean moved = false;

    // бой
    int bLevel, pMax, pHp, eMax, eHp, pShield, shieldTurns, mana, turn;
    boolean bBoss;
    double bMult;
    int selCard = -1;
    String status = "";
    final List<Unit> units = new ArrayList<>();
    final List<String> log = new ArrayList<>();
    boolean resWin;
    int resStars, resReward;

    Game(Context ctx) {
        super(ctx);
        prefs = ctx.getSharedPreferences("kingdom", Context.MODE_PRIVATE);
        audio = new Audio(ctx);
        load();
    }

    // ================= сохранение =================
    void load() {
        try {
            unlocked = prefs.getInt("unlocked", 1);
            gold = prefs.getInt("gold", 0);
            audio.sfxOn = prefs.getBoolean("sfx", true);
            audio.musicOn = prefs.getBoolean("music", true);
            totalWins = prefs.getInt("totalWins", 0);
            dailyStreak = prefs.getInt("dailyStreak", 0);
            lastDaily = prefs.getString("lastDaily", "");
            for (int i = 0; i < claimedAchievements.length; i++) claimedAchievements[i] = prefs.getBoolean("achievement" + i, false);
            String[] up = prefs.getString("upg", "0,0,0,0,0,0").split(",");
            for (int i = 0; i < 6 && i < up.length; i++) upg[i] = Integer.parseInt(up[i]);
            String s = prefs.getString("stars", "");
            for (String p : s.split(",")) {
                if (p.isEmpty()) continue;
                String[] q = p.split(":");
                int lv = Integer.parseInt(q[0]);
                if (lv <= MAX_STARS_LEVEL) stars[lv] = Integer.parseInt(q[1]);
            }
        } catch (Exception ignored) {
            // повреждённое сохранение — начинаем заново
        }
    }

    void save() {
        StringBuilder up = new StringBuilder();
        for (int i = 0; i < 6; i++) { if (i > 0) up.append(','); up.append(upg[i]); }
        StringBuilder st = new StringBuilder();
        for (int i = 1; i < stars.length; i++)
            if (stars[i] > 0) st.append(i).append(':').append(stars[i]).append(',');
        SharedPreferences.Editor editor = prefs.edit().putInt("unlocked", unlocked).putInt("gold", gold)
             .putString("upg", up.toString()).putString("stars", st.toString())
             .putBoolean("sfx", audio.sfxOn).putBoolean("music", audio.musicOn)
             .putInt("totalWins", totalWins).putInt("dailyStreak", dailyStreak).putString("lastDaily", lastDaily);
        for (int i = 0; i < claimedAchievements.length; i++) editor.putBoolean("achievement" + i, claimedAchievements[i]);
        editor.apply();
    }

    // ================= мир =================
    static String kingdomName(int k) {
        Random r = new Random(k * 7919L + 17);
        return PRE[r.nextInt(PRE.length)] + SUF[r.nextInt(SUF.length)];
    }

    static String ruler(int k) {
        Random r = new Random(k * 104729L + 3);
        return TITLES[r.nextInt(TITLES.length)] + " " + PRE[r.nextInt(PRE.length)];
    }

    static int upgCost(int i, int lvl) { return i < 5 ? 40 + 30 * lvl : 80 + 60 * lvl; }

    int kingdomStars(int k) {
        int s = 0;
        for (int i = 1; i <= STAGES; i++) {
            int lv = k * STAGES + i;
            if (lv <= MAX_STARS_LEVEL) s += stars[lv];
        }
        return s;
    }

    int totalStars() {
        int s = 0;
        for (int v : stars) s += v;
        return s;
    }

    // ================= ресурсы и рисование =================
    Bitmap bmp(String name) {
        Bitmap b = cache.get(name);
        if (b == null) {
            try (InputStream in = getContext().getAssets().open(name + ".png")) {
                b = BitmapFactory.decodeStream(in);
            } catch (IOException e) {
                b = null;
            }
            if (b != null) cache.put(name, b);
        }
        return b;
    }

    RectF fr(float x1, float y1, float x2, float y2) {
        float w = getWidth(), h = getHeight();
        return new RectF(x1 * w, y1 * h, x2 * w, y2 * h);
    }

    void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        c.drawText(s, x, y, paint);
    }

    void box(Canvas c, RectF r, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRoundRect(r, 16, 16, paint);
    }

    void button(Canvas c, RectF r, String label, int color) {
        box(c, r, color);
        text(c, label, r.centerX(), r.centerY() + r.height() * 0.15f, r.height() * 0.42f, Color.WHITE, Paint.Align.CENTER);
    }

    void backButton(Canvas c) {
        button(c, backBtn(), "Назад", Color.rgb(90, 90, 110));
    }

    RectF backBtn() { return fr(0.02f, 0.02f, 0.30f, 0.085f); }

    // рисует картинку, вписывая её в прямоугольник с сохранением пропорций
    void drawFit(Canvas c, String name, float cx, float cy, float maxW, float maxH) {
        Bitmap b = bmp(name);
        if (b == null) return;
        float ar = b.getWidth() / (float) b.getHeight();
        float w = maxW, h = maxW / ar;
        if (h > maxH) { h = maxH; w = maxH * ar; }
        paint.setAlpha(255);
        c.drawBitmap(b, null, new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), paint);
    }

    void drawCover(Canvas c, String name, int alpha) {
        Bitmap b = bmp(name);
        if (b == null) return;
        float w = getWidth(), h = getHeight();
        float s = Math.max(w / b.getWidth(), h / b.getHeight());
        float dw = b.getWidth() * s, dh = b.getHeight() * s;
        paint.setAlpha(alpha);
        c.drawBitmap(b, null, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), paint);
        paint.setAlpha(255);
    }

    void dim(Canvas c, int alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha, 10, 12, 22));
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.save();
        if (shake > 0) {
            c.translate((rnd.nextFloat() - 0.5f) * shake * 2, (rnd.nextFloat() - 0.5f) * shake * 2);
            shake--;
        }
        c.drawColor(Color.rgb(18, 20, 28));
        switch (scene) {
            case MENU: drawMenu(c); break;
            case MAP: drawMap(c); break;
            case KINGDOM: drawKingdom(c); break;
            case COLLECTION: drawCollection(c); break;
            case SETTINGS: drawSettings(c); break;
            case REWARDS: drawRewards(c); break;
            case BATTLE: drawBattle(c); break;
            default: drawResult(c); break;
        }
        if (scene == BATTLE) drawFx(c);
        c.restore();
        if (shake > 0 || flash > 0 || !particles.isEmpty()) postInvalidateDelayed(16);
    }

    void drawFx(Canvas c) {
        if (flash > 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.min(255, flash), 255, 255, 255));
            c.drawRect(0, 0, getWidth(), getHeight(), paint);
            flash -= 14;
        }
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.x += p.vx; p.y += p.vy; p.vy += 0.35f; p.life--;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.max(0, Math.min(255, p.life * 8)),
                    Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
            c.drawCircle(p.x, p.y, 3 + p.life * 0.08f, paint);
            if (p.life <= 0) particles.remove(i);
        }
    }

    void spawnBurst(int lane, int col, int color, int n) {
        float x = cellCX(col), y = cellCY(lane, col);
        for (int i = 0; i < n; i++) {
            Particle p = new Particle();
            p.x = x; p.y = y;
            p.vx = (rnd.nextFloat() - 0.5f) * 10;
            p.vy = (rnd.nextFloat() - 1.2f) * 6;
            p.life = 30 + rnd.nextInt(15);
            p.color = color;
            particles.add(p);
        }
    }

    void updateMusic() {
        if (audio != null) audio.music(scene == BATTLE ? "music_battle" : "music_menu");
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        audio.release();
    }

    // ---------- главное меню (как лобби на картинке) ----------
    static final String[] MENU_LABELS = {"Играть (карта мира)", "Продолжить: уровень ", "Коллекция карт", "Награды и задания", "Настройки"};

    RectF menuBtn(int i) {
        float top = 0.42f + i * 0.095f;
        return fr(0.10f, top, 0.90f, top + 0.075f);
    }

    void drawMenu(Canvas c) {
        float w = getWidth(), h = getHeight();
        drawCover(c, "lobby_bg", 255);
        dim(c, 110);
        text(c, "ЗАХВАТ КОРОЛЕВСТВ", w / 2f, h * 0.10f, h * 0.045f, Color.rgb(255, 215, 110), Paint.Align.CENTER);
        text(c, "Игрок — уровень " + unlocked + "    Монеты: " + gold, w / 2f, h * 0.155f, h * 0.026f, Color.WHITE, Paint.Align.CENTER);
        text(c, "Звёзд: " + totalStars(), w / 2f, h * 0.195f, h * 0.024f, Color.LTGRAY, Paint.Align.CENTER);
        for (int i = 0; i < MENU_LABELS.length; i++) {
            String label = i == 1 ? MENU_LABELS[1] + unlocked : MENU_LABELS[i];
            int color = i == 0 ? Color.rgb(180, 130, 30) : (i == 1 ? Color.rgb(60, 140, 80) : Color.rgb(60, 90, 160));
            button(c, menuBtn(i), label, color);
        }
    }

    // ---------- карта мира: 50 королевств ----------
    float rowH() { return getHeight() * 0.13f; }

    float maxScroll() { return Math.max(0, KINGDOMS * rowH() - getHeight() * 0.86f); }

    void drawMap(Canvas c) {
        float w = getWidth(), h = getHeight();
        drawCover(c, "lobby_bg", 120);
        dim(c, 200);
        text(c, "КАРТА МИРА", w * 0.62f, h * 0.06f, h * 0.034f, Color.rgb(255, 215, 110), Paint.Align.CENTER);
        backButton(c);
        float rh = rowH();
        for (int k = 0; k < KINGDOMS; k++) {
            float top = h * 0.12f + k * rh - scrollY;
            if (top + rh < 0 || top > h) continue;
            boolean open = k * STAGES + 1 <= unlocked;
            RectF row = new RectF(w * 0.03f, top + 4, w * 0.97f, top + rh - 4);
            box(c, row, open ? Color.rgb(35, 45, 70) : Color.rgb(28, 28, 36));
            RectF th = new RectF(row.left + 8, row.top + 8, row.left + w * 0.26f, row.bottom - 8);
            drawFit(c, "lvl_" + (THEME_PIC[k % 10] + 1), th.centerX(), th.centerY(), th.width(), th.height());
            if (open) {
                text(c, "Королевство " + (k + 1) + ": " + kingdomName(k), row.left + w * 0.29f, row.top + rh * 0.38f,
                        h * 0.026f, Color.WHITE, Paint.Align.LEFT);
                text(c, THEME_NAMES[k % 10], row.left + w * 0.29f, row.top + rh * 0.68f,
                        h * 0.02f, Color.LTGRAY, Paint.Align.LEFT);
                text(c, kingdomStars(k) + " / " + (STAGES * 3) + " ★", row.right - 14, row.top + rh * 0.55f,
                        h * 0.022f, Color.rgb(255, 210, 90), Paint.Align.RIGHT);
            } else {
                text(c, "Королевство " + (k + 1) + " — закрыто", row.left + w * 0.29f, row.centerY() + h * 0.008f,
                        h * 0.022f, Color.GRAY, Paint.Align.LEFT);
            }
        }
    }

    // ---------- королевство: 10 этапов ----------
    void drawKingdom(Canvas c) {
        float w = getWidth(), h = getHeight();
        drawCover(c, "lvl_" + (THEME_PIC[curK % 10] + 1), 110);
        dim(c, 190);
        text(c, kingdomName(curK) + " — " + ruler(curK), w / 2f, h * 0.07f, h * 0.03f, Color.rgb(255, 215, 110), Paint.Align.CENTER);
        text(c, THEME_NAMES[curK % 10], w / 2f, h * 0.115f, h * 0.024f, Color.LTGRAY, Paint.Align.CENTER);
        backButton(c);
        for (int s = 1; s <= STAGES; s++) {
            int lv = curK * STAGES + s;
            RectF r = stageRect(s);
            boolean open = lv <= unlocked;
            boolean boss = s == STAGES;
            box(c, r, !open ? Color.rgb(28, 28, 36) : (boss ? Color.rgb(110, 40, 40) : Color.rgb(40, 60, 95)));
            String label = "Этап " + s + (boss ? "  — БОСС (дракон)" : "");
            text(c, label, r.left + w * 0.04f, r.centerY() + r.height() * 0.12f, r.height() * 0.32f,
                    open ? Color.WHITE : Color.GRAY, Paint.Align.LEFT);
            String info;
            if (!open) info = "закрыто";
            else {
                int st = lv <= MAX_STARS_LEVEL ? stars[lv] : 0;
                info = st == 0 ? "играть" : (st == 3 ? "★★★" : st == 2 ? "★★☆" : "★☆☆");
            }
            text(c, info, r.right - w * 0.03f, r.centerY() + r.height() * 0.12f, r.height() * 0.32f,
                    Color.rgb(255, 210, 90), Paint.Align.RIGHT);
        }
    }

    RectF stageRect(int s) {
        float top = 0.15f + (s - 1) * 0.078f;
        return fr(0.04f, top, 0.96f, top + 0.07f);
    }

    // ---------- коллекция: армия, карты, враги, улучшения ----------
    RectF collUnit(int i) {
        float cw = getWidth() / 5f;
        return new RectF(i * cw + 4, getHeight() * 0.13f, (i + 1) * cw - 4, getHeight() * 0.30f);
    }

    RectF collCard(int i) {
        float cw = getWidth() / 4f;
        float top = getHeight() * (0.36f + (i / 4) * 0.17f);
        return new RectF((i % 4) * cw + 4, top, (i % 4 + 1) * cw - 4, top + getHeight() * 0.165f);
    }

    RectF collEnemy(int i) {
        float cw = getWidth() / 5f;
        return new RectF(i * cw + 4, getHeight() * 0.745f, (i + 1) * cw - 4, getHeight() * 0.87f);
    }

    RectF castleBtn() { return fr(0.10f, 0.885f, 0.90f, 0.955f); }

    void drawCollection(Canvas c) {
        float w = getWidth(), h = getHeight();
        drawCover(c, "lobby_bg", 90);
        dim(c, 200);
        backButton(c);
        text(c, "КОЛЛЕКЦИЯ", w * 0.62f, h * 0.06f, h * 0.034f, Color.rgb(255, 215, 110), Paint.Align.CENTER);
        text(c, "Армия (тап — улучшить), монеты: " + gold, w / 2f, h * 0.115f, h * 0.022f, Color.WHITE, Paint.Align.CENTER);
        for (int i = 0; i < 5; i++) {
            RectF r = collUnit(i);
            box(c, r, Color.rgb(30, 40, 62));
            drawFit(c, UNIT_ART[i], r.centerX(), r.centerY() - r.height() * 0.08f, r.width() * 0.9f, r.height() * 0.72f);
            text(c, UNIT_NAME[i].split(" ")[0], r.centerX(), r.bottom - h * 0.022f, h * 0.018f, Color.WHITE, Paint.Align.CENTER);
            text(c, "ур." + upg[i], r.centerX(), r.bottom - h * 0.005f, h * 0.018f, Color.rgb(255, 210, 90), Paint.Align.CENTER);
        }
        text(c, "Карты", w * 0.05f, h * 0.345f, h * 0.024f, Color.rgb(255, 215, 110), Paint.Align.LEFT);
        for (int i = 0; i < 8; i++) {
            RectF r = collCard(i);
            drawFit(c, CARD_ART[i], r.centerX(), r.centerY(), r.width(), r.height());
        }
        text(c, "Враги", w * 0.05f, h * 0.725f, h * 0.024f, Color.rgb(255, 120, 110), Paint.Align.LEFT);
        for (int i = 0; i < 5; i++) {
            RectF r = collEnemy(i);
            box(c, r, Color.rgb(50, 26, 30));
            drawFit(c, EN_ART[i], r.centerX(), r.centerY() - r.height() * 0.08f, r.width() * 0.9f, r.height() * 0.72f);
            text(c, EN_NAME[i].split(" ")[0], r.centerX(), r.bottom - h * 0.01f, h * 0.018f, Color.WHITE, Paint.Align.CENTER);
        }
        int cost = upgCost(5, upg[5]);
        button(c, castleBtn(), "Укрепление замка: ур." + upg[5] + " (+60 HP), цена " + cost, Color.rgb(60, 90, 160));
    }

    // ---------- ежедневные награды и достижения ----------
    RectF dailyBtn() { return fr(0.08f, 0.25f, 0.92f, 0.34f); }
    RectF achievementBtn(int i) { return fr(0.08f, 0.43f + i * 0.115f, 0.92f, 0.515f + i * 0.115f); }

    String todayStamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
    }

    boolean dailyAvailable() { return !todayStamp().equals(lastDaily); }

    void claimDailyReward() {
        if (!dailyAvailable()) return;
        String today = todayStamp();
        String yesterday = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date(System.currentTimeMillis() - 86400000L));
        dailyStreak = yesterday.equals(lastDaily) ? Math.min(7, dailyStreak + 1) : 1;
        int reward = 40 + dailyStreak * 10;
        gold += reward;
        lastDaily = today;
        status = "Ежедневная награда: +" + reward;
        save();
    }

    int achievementTarget(int i) { return new int[]{1, 5, 15, 50}[i]; }
    int achievementReward(int i) { return new int[]{50, 120, 300, 1000}[i]; }

    void claimAchievement(int i) {
        if (i < 0 || i >= claimedAchievements.length || claimedAchievements[i] || totalWins < achievementTarget(i)) return;
        claimedAchievements[i] = true;
        gold += achievementReward(i);
        status = "Достижение получено: +" + achievementReward(i) + " монет";
        save();
    }

    void drawRewards(Canvas c) {
        float w = getWidth(), h = getHeight();
        drawCover(c, "lobby_bg", 100); dim(c, 205); backButton(c);
        text(c, "НАГРАДЫ И ЗАДАНИЯ", w / 2f, h * 0.15f, h * 0.032f, Color.rgb(255, 215, 110), Paint.Align.CENTER);
        text(c, "Побед: " + totalWins + "     Монеты: " + gold, w / 2f, h * 0.205f, h * 0.024f, Color.WHITE, Paint.Align.CENTER);
        String dailyText = dailyAvailable() ? "Забрать ежедневный бонус (серия " + dailyStreak + ")" : "Ежедневный бонус уже получен";
        button(c, dailyBtn(), dailyText, dailyAvailable() ? Color.rgb(45, 125, 75) : Color.rgb(65, 65, 75));
        String[] names = {"Первая победа", "5 побед", "15 побед", "50 побед"};
        for (int i = 0; i < 4; i++) {
            String state = claimedAchievements[i] ? "Получено" : (totalWins >= achievementTarget(i) ? "Забрать" : totalWins + "/" + achievementTarget(i));
            button(c, achievementBtn(i), names[i] + " · " + state + " · +" + achievementReward(i),
                    claimedAchievements[i] ? Color.rgb(55, 65, 70) : (totalWins >= achievementTarget(i) ? Color.rgb(125, 90, 25) : Color.rgb(45, 55, 80)));
        }
        text(c, "Бонус доступен раз в календарный день", w / 2f, h * 0.93f, h * 0.02f, Color.LTGRAY, Paint.Align.CENTER);
    }

    // ---------- настройки ----------
    void drawSettings(Canvas c) {
        float w = getWidth(), h = getHeight();
        drawCover(c, "lobby_bg", 100);
        dim(c, 210);
        backButton(c);
        text(c, "НАСТРОЙКИ", w / 2f, h * 0.20f, h * 0.036f, Color.rgb(255, 215, 110), Paint.Align.CENTER);
        String[] lines = {
            "Версия 1.4 — Королевское обновление",
            "Игра работает офлайн, интернет не нужен",
            "Прогресс сохраняется на телефоне",
            "500 уровней: 50 королевств по 10 этапов",
            "Босс-дракон на каждом 10-м этапе",
            "Звёзды зависят от оставшегося HP вашего замка"
        };
        for (int i = 0; i < lines.length; i++)
            text(c, lines[i], w / 2f, h * (0.30f + i * 0.06f), h * 0.024f, Color.WHITE, Paint.Align.CENTER);
        button(c, sfxBtn(), "Звуки эффектов: " + (audio.sfxOn ? "ВКЛ" : "ВЫКЛ"), Color.rgb(60, 90, 160));
        button(c, musicBtn(), "Музыка: " + (audio.musicOn ? "ВКЛ" : "ВЫКЛ"), Color.rgb(60, 90, 160));
    }

    RectF sfxBtn() { return fr(0.10f, 0.66f, 0.90f, 0.735f); }
    RectF musicBtn() { return fr(0.10f, 0.76f, 0.90f, 0.835f); }

    // ---------- бой ----------
    // ---- перспектива: col 0 — близко к нашему замку, col COLS-1 — далеко у врага ----
    float depthT(int col) { return col / (float) (COLS - 1); }
    float horizonY() { return getHeight() * 0.10f; }
    float laneNearY(int lane) { return getHeight() * (0.34f + lane * 0.07f); }
    float laneFarY(int lane) { return horizonY() + (laneNearY(lane) - horizonY()) * 0.45f; }
    float cellCX(int col) { return getWidth() * (0.16f + 0.70f * depthT(col)); }
    float cellCY(int lane, int col) {
        float t = depthT(col);
        return laneNearY(lane) + (laneFarY(lane) - laneNearY(lane)) * t;
    }
    float scaleAt(int col) { return 1f - 0.5f * depthT(col); }
    float bandNear() { return getHeight() * 0.075f; }

    RectF laneRect(int i) {
        float y = laneFarY(i) - bandNear() * 0.6f, y2 = laneNearY(i) + bandNear() * 0.6f;
        return new RectF(0, y, getWidth(), y2);
    }

    RectF cardRect(int i) {
        float w = getWidth(), h = getHeight();
        float cw = w / 8f;
        float ch = Math.min(cw * 1.35f, 0.15f * h);
        return new RectF(i * cw + 2, HAND_Y * h, (i + 1) * cw - 2, HAND_Y * h + ch);
    }

    RectF endBtn() { return fr(0.03f, 0.905f, 0.68f, 0.975f); }
    RectF surBtn() { return fr(0.70f, 0.905f, 0.97f, 0.975f); }

    boolean needsLane(int k) { return k <= 3 || k == 5; }

    void drawBattle(Canvas c) {
        float w = getWidth(), h = getHeight();
        drawCover(c, "lvl_" + (THEME_PIC[((bLevel - 1) / STAGES) % 10] + 1), 150);
        dim(c, 130);

        int k = (bLevel - 1) / STAGES;
        int stage = (bLevel - 1) % STAGES + 1;
        text(c, "Ур." + bLevel + " " + kingdomName(k) + " — этап " + stage + (bBoss ? " БОСС" : ""),
                8, h * 0.035f, h * 0.024f, Color.WHITE, Paint.Align.LEFT);
        text(c, "Замок " + Math.max(0, pHp) + "/" + pMax + (pShield > 0 ? " (щит " + pShield + ")" : "")
                + "   Враг " + Math.max(0, eHp) + "/" + eMax + "   Ход " + turn,
                8, h * 0.075f, h * 0.022f, Color.LTGRAY, Paint.Align.LEFT);
        drawManaBar(c, w, h);

        // пол: трапеции лайнов, сужающиеся к горизонту
        float x0 = cellCX(0) - w * 0.04f, xF = cellCX(COLS - 1);
        for (int i = 0; i < LANES; i++) {
            float yN = laneNearY(i), yF = laneFarY(i);
            float bN = bandNear(), bF = bN * 0.45f;
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(x0, yN - bN);
            p.lineTo(xF, yF - bF);
            p.lineTo(xF, yF + bF);
            p.lineTo(x0, yN + bN);
            p.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(i % 2 == 0 ? Color.argb(150, 52, 70, 48) : Color.argb(150, 60, 56, 40));
            c.drawPath(p, paint);
        }

        // замки (близкий слева, дальний справа)
        float bN = bandNear();
        RectF lc = new RectF(0f, laneFarY(0) - bN * 0.5f, w * 0.10f, laneNearY(LANES - 1) + bN);
        box(c, lc, Color.rgb(40, 90, 180));
        text(c, "Я", lc.centerX(), lc.centerY(), h * 0.03f, Color.WHITE, Paint.Align.CENTER);
        RectF rc = new RectF(w * 0.90f, laneFarY(0) - bN * 0.5f, w, laneFarY(LANES - 1) + bN * 0.4f);
        box(c, rc, Color.rgb(170, 40, 40));
        text(c, "В", rc.centerX(), rc.centerY(), h * 0.03f, Color.WHITE, Paint.Align.CENTER);

        // юниты в глубину: дальние рисуются первыми
        for (int col = COLS - 1; col >= 0; col--) {
            float s = scaleAt(col);
            for (int i = 0; i < LANES; i++) {
                Unit u = unitAt(i, col);
                if (u == null) continue;
                float cx = cellCX(col), cy = cellCY(i, col);
                float sz = h * 0.075f * s;
                // тень
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(110, 0, 0, 0));
                c.drawOval(new RectF(cx - sz * 0.35f, cy - sz * 0.05f, cx + sz * 0.35f, cy + sz * 0.12f), paint);
                drawFit(c, u.art, cx, cy - sz * 0.5f, sz * 0.95f, sz * 1.25f);
                // полоска HP
                float barW = sz * 0.7f;
                float frac = Math.max(0, u.hp) / (float) u.maxHp;
                paint.setColor(Color.rgb(60, 30, 30));
                c.drawRect(cx - barW / 2, cy + sz * 0.16f, cx + barW / 2, cy + sz * 0.16f + 4, paint);
                paint.setColor(u.player ? Color.rgb(90, 220, 110) : Color.rgb(240, 80, 70));
                c.drawRect(cx - barW / 2, cy + sz * 0.16f, cx - barW / 2 + barW * frac, cy + sz * 0.16f + 4, paint);
            }
        }

        // всплывающий урон
        boolean anim = !floats.isEmpty();
        for (int i = floats.size() - 1; i >= 0; i--) {
            FloatText f = floats.get(i);
            text(c, f.s, f.x, f.y - (40 - f.life) * 1.6f, h * 0.026f, f.color, Paint.Align.CENTER);
            f.life--;
            if (f.life <= 0) floats.remove(i);
        }
        if (anim) postInvalidateDelayed(16);

        int from = Math.max(0, log.size() - 2);
        float ly = h * LOG_Y;
        for (int i = from; i < log.size(); i++) {
            text(c, log.get(i), 8, ly, h * 0.02f, Color.LTGRAY, Paint.Align.LEFT);
            ly += h * 0.022f;
        }
        if (status.length() > 0)
            text(c, status, 8, h * STATUS_Y, h * 0.022f, Color.rgb(255, 220, 120), Paint.Align.LEFT);

        // рука карт
        for (int i = 0; i < 8; i++) {
            RectF r = cardRect(i);
            drawFit(c, CARD_ART[i], r.centerX(), r.centerY(), r.width(), r.height());
            if (mana < CARD_COST[i]) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(140, 0, 0, 0));
                c.drawRoundRect(r, 12, 12, paint);
            }
            if (i == selCard) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(6);
                paint.setColor(Color.rgb(255, 215, 60));
                c.drawRoundRect(r, 12, 12, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        button(c, endBtn(), "Конец хода", Color.rgb(50, 130, 70));
        button(c, surBtn(), "Сдаться", Color.rgb(130, 50, 50));
        button(c, autoBtn(), auto ? "АВТО: ВКЛ" : "АВТО: ВЫКЛ", auto ? Color.rgb(40, 110, 170) : Color.rgb(90, 90, 100));
    }

    RectF autoBtn() { return fr(0.74f, 0.0f, 0.99f, 0.045f); }

    // полоска маны: кристаллы, до MAX_MANA штук
    void drawManaBar(Canvas c, float w, float h) {
        float top = h * 0.105f, slotW = w * 0.8f / MAX_MANA;
        for (int i = 0; i < MAX_MANA; i++) {
            RectF r = new RectF(8 + i * slotW + 2, top, 8 + (i + 1) * slotW - 2, top + h * 0.014f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(i < mana ? Color.rgb(90, 170, 255) : Color.rgb(40, 50, 70));
            c.drawRoundRect(r, 4, 4, paint);
        }
        text(c, "Мана " + mana + "/" + MAX_MANA, w * 0.84f, top + h * 0.012f, h * 0.02f, Color.rgb(160, 210, 255), Paint.Align.LEFT);
    }

    void drawResult(Canvas c) {
        float w = getWidth(), h = getHeight();
        drawCover(c, "lobby_bg", 255);
        dim(c, 200);
        text(c, resWin ? "ПОБЕДА!" : "ПОРАЖЕНИЕ", w / 2f, h * 0.40f, h * 0.06f,
                resWin ? Color.rgb(255, 210, 90) : Color.rgb(230, 90, 80), Paint.Align.CENTER);
        if (resWin) {
            String st = resStars == 3 ? "★★★" : resStars == 2 ? "★★☆" : "★☆☆";
            text(c, st, w / 2f, h * 0.50f, h * 0.05f, Color.rgb(255, 210, 90), Paint.Align.CENTER);
            text(c, "Награда: " + resReward + " монет", w / 2f, h * 0.57f, h * 0.03f, Color.WHITE, Paint.Align.CENTER);
        } else {
            text(c, "Укрепите армию в коллекции и попробуйте снова", w / 2f, h * 0.52f, h * 0.024f, Color.WHITE, Paint.Align.CENTER);
        }
        text(c, "Нажмите, чтобы вернуться в меню", w / 2f, h * 0.70f, h * 0.024f, Color.LTGRAY, Paint.Align.CENTER);
    }

    // ================= касания =================
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = y; startScroll = scrollY; moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(y - downY) > 14) moved = true;
                if (scene == MAP) scrollY = Math.max(0, Math.min(maxScroll(), startScroll + (downY - y)));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) { audio.play("sfx_click"); handleTap(x, y); }
                updateMusic();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    void handleTap(float x, float y) {
        float h = getHeight();
        switch (scene) {
            case MENU:
                for (int i = 0; i < MENU_LABELS.length; i++) {
                    if (menuBtn(i).contains(x, y)) {
                        if (i == 0) { scene = MAP; scrollY = 0; }
                        else if (i == 1) startBattle(unlocked);
                        else if (i == 2) scene = COLLECTION;
                        else if (i == 3) scene = REWARDS;
                        else scene = SETTINGS;
                        return;
                    }
                }
                break;
            case MAP:
                if (backBtn().contains(x, y)) { scene = MENU; return; }
                int k = (int) Math.floor((y - h * 0.12f + scrollY) / rowH());
                if (k >= 0 && k < KINGDOMS && k * STAGES + 1 <= unlocked) {
                    curK = k;
                    scene = KINGDOM;
                }
                break;
            case KINGDOM:
                if (backBtn().contains(x, y)) { scene = MAP; return; }
                for (int s = 1; s <= STAGES; s++) {
                    if (stageRect(s).contains(x, y)) {
                        int lv = curK * STAGES + s;
                        if (lv <= unlocked) startBattle(lv);
                        return;
                    }
                }
                break;
            case COLLECTION:
                if (backBtn().contains(x, y)) { scene = MENU; return; }
                for (int i = 0; i < 5; i++) if (collUnit(i).contains(x, y)) { buyUpgrade(i); return; }
                if (castleBtn().contains(x, y)) buyUpgrade(5);
                break;
            case SETTINGS:
                if (backBtn().contains(x, y)) { scene = MENU; return; }
                if (sfxBtn().contains(x, y)) { audio.sfxOn = !audio.sfxOn; save(); }
                else if (musicBtn().contains(x, y)) {
                    audio.musicOn = !audio.musicOn;
                    if (!audio.musicOn) audio.stopMusic();
                    save();
                }
                break;
            case REWARDS:
                if (backBtn().contains(x, y)) { scene = MENU; return; }
                if (dailyBtn().contains(x, y)) claimDailyReward();
                else {
                    for (int i = 0; i < 4; i++) {
                        if (achievementBtn(i).contains(x, y)) claimAchievement(i);
                    }
                }
                break;
            case BATTLE:
                battleTap(x, y);
                break;
            default:
                scene = MENU;
                break;
        }
    }

    void buyUpgrade(int i) {
        int cost = upgCost(i, upg[i]);
        if (gold >= cost) {
            gold -= cost;
            upg[i]++;
            save();
        }
    }

    void battleTap(float x, float y) {
        if (autoBtn().contains(x, y)) {
            auto = !auto;
            if (auto) scheduleAuto(300);
            else removeCallbacks(autoTick);
            return;
        }
        for (int i = 0; i < 8; i++) {
            if (cardRect(i).contains(x, y)) {
                if (needsLane(i)) selCard = (selCard == i) ? -1 : i;
                else { playCard(i, 0); selCard = -1; }
                return;
            }
        }
        if (endBtn().contains(x, y)) { endTurn(); return; }
        if (surBtn().contains(x, y)) { pHp = 0; checkEnd(); return; }
        for (int i = 0; i < LANES; i++) {
            if (laneRect(i).contains(x, y)) {
                if (selCard >= 0) playCard(selCard, i);
                return;
            }
        }
    }

    // ================= бой =================
    void startBattle(int level) {
        bLevel = level;
        bBoss = level % STAGES == 0;
        bMult = 1.0 + (level - 1) * 0.012;      // более заметный рост сложности
        pMax = 300 + 60 * upg[5];
        pHp = pMax;
        pShield = 0; shieldTurns = 0;
        // Вражеский замок крепче: победа требует осмысленного продвижения армии.
        eMax = 240 + level * 18 + (bBoss ? 320 : 0) + ((level % STAGES == 5) ? 120 : 0);
        eHp = eMax;
        mana = START_MANA; turn = 1; selCard = -1; status = ""; floats.clear();
        units.clear(); log.clear();
        int k = (level - 1) / STAGES;
        if (level % STAGES == 4) {
            status = "Событие: разведывательная засада! Готовьтесь к подкреплениям.";
            for (int i = 0; i < 2; i++) spawnEnemy(i, i, false);
        }
        if (level % STAGES == 5) {
            status = "Событие: осада крепости! Мини-босс и усиленный замок.";
            spawnBoss((k + 1) % 4, 1, 1.0);
        }
        if (level % STAGES == 7) {
            status = "Событие: вражеский конвой! Несколько линий атакованы.";
            for (int lane = 0; lane < LANES; lane += 2) spawnEnemy(1, lane, false);
        }
        if (bBoss) {
            status = "Событие: битва за столицу! Главный босс.";
            spawnBoss(k % 4, 2, 1.8);
            if (k % 5 == 4) spawnBoss((k + 2) % 4, 0, 1.8);
        }
        scene = BATTLE;
        updateMusic();
        postInvalidate();
        scheduleAuto(900);
    }

    Unit unitAt(int lane, int col) {
        for (Unit u : units) if (u.alive() && u.lane == lane && u.col == col) return u;
        return null;
    }

    boolean occupied(int lane, int col) { return unitAt(lane, col) != null; }

    boolean spawnPlayer(int t, int lane) {
        if (occupied(lane, 0)) return false;
        double m = 1 + 0.15 * upg[t];
        Unit u = new Unit();
        u.player = true; u.type = t; u.name = UNIT_NAME[t]; u.art = UNIT_ART[t];
        u.lane = lane; u.col = 0;
        u.maxHp = u.hp = (int) (UNIT_HP[t] * m);
        u.dmg = (int) (UNIT_DMG[t] * m);
        u.range = UNIT_RANGE[t]; u.speed = UNIT_SPEED[t];
        units.add(u);
        return true;
    }

    boolean spawnEnemy(int t, int lane, boolean boss) {
        if (occupied(lane, COLS - 1)) return false;
        double m = boss ? bMult * 1.5 : bMult;
        Unit u = new Unit();
        u.player = false; u.type = t; u.name = EN_NAME[t]; u.art = EN_ART[t];
        u.lane = lane; u.col = COLS - 1;
        u.maxHp = u.hp = (int) (EN_HP[t] * m);
        u.dmg = boss ? EN_DMG[t] : (int) (EN_DMG[t] * bMult);   // урон дракона фиксированный
        u.range = EN_RANGE[t]; u.speed = EN_SPEED[t];
        units.add(u);
        return true;
    }

    boolean spawnBoss(int bt, int lane, double factor) {
        if (occupied(lane, COLS - 1)) return false;
        Unit u = new Unit();
        u.player = false; u.boss = true; u.bt = bt;
        u.name = BOSS_NAME[bt]; u.art = BOSS_ART[bt];
        u.lane = lane; u.col = COLS - 1;
        u.maxHp = u.hp = (int) (BOSS_HP[bt] * bMult * factor);
        u.dmg = BOSS_DMG[bt]; u.range = BOSS_RANGE[bt]; u.speed = BOSS_SPEED[bt];
        units.add(u);
        status = "Босс: " + u.name + "!";
        flash = 150; shake = 14;
        audio.play("sfx_boss");
        return true;
    }

    int bossType() { return ((bLevel - 1) / STAGES) % 4; }

    // способности боссов в начале хода врага
    void bossAbilities() {
        for (Unit u : new ArrayList<>(units)) {
            if (!u.alive() || !u.boss) continue;
            if (u.bt == 1) {                                  // тролль: регенерация
                int before = u.hp;
                u.hp = Math.min(u.maxHp, u.hp + 25);
                if (u.hp > before) addFloat(u.lane, u.col, "+" + (u.hp - before), Color.rgb(120, 255, 120));
            }
            if (u.bt == 2 && enemyCount() < 15) {             // тёмный лорд: призыв гоблинов
                spawnEnemy(0, rnd.nextInt(LANES), false);
                log.add(u.name + " призывает гоблина");
            }
            if (u.bt == 0 && turn % 3 == 0) {                 // дракон: огонь по своей линии
                for (Unit p : units) {
                    if (p.alive() && p.player && p.lane == u.lane) {
                        p.hp -= 40;
                        addFloat(p.lane, p.col, "-40", Color.rgb(255, 140, 60));
                    }
                }
                log.add(u.name + " дышит огнём!");
                audio.play("sfx_spell");
                shake = 8;
            }
        }
        cleanup();
    }

    int enemyCount() {
        int n = 0;
        for (Unit u : units) if (u.alive() && !u.player) n++;
        return n;
    }

    Unit nearestEnemyInLane(int lane) {
        Unit best = null;
        for (Unit u : units)
            if (u.alive() && !u.player && u.lane == lane && (best == null || u.col < best.col)) best = u;
        return best;
    }

    void playCard(int k, int lane) {
        if (mana < CARD_COST[k]) { status = "Недостаточно маны."; return; }
        boolean ok = true;
        switch (k) {
            case 0:
            case 1:
            case 2:
                ok = spawnPlayer(k, lane);
                if (ok) audio.play("sfx_summon");
                status = ok ? "Призван: " + UNIT_NAME[k] : "Линия у вашего замка занята.";
                break;
            case 3: {   // Маг: 40 урона ближайшему врагу на линии
                Unit t = nearestEnemyInLane(lane);
                if (t == null) { status = "На линии нет врагов."; ok = false; }
                else {
                    t.hp -= (int) (40 * (1 + 0.15 * upg[3]));
                    status = "Маг бьёт: " + t.name + "!";
                    audio.play("sfx_spell");
                }
                break;
            }
            case 4: {   // Осадная машина: урон всем врагам
                int dmg = (int) (30 * (1 + 0.15 * upg[4]));
                for (Unit u : units) if (!u.player && u.alive()) u.hp -= dmg;
                status = "Осадная машина: " + dmg + " по всем врагам!";
                audio.play("sfx_spell"); shake = 10;
                break;
            }
            case 5:     // Огненный шар: урон по линии
                for (Unit u : units) if (!u.player && u.alive() && u.lane == lane) u.hp -= 50;
                status = "Огненный шар на линии " + (lane + 1) + "!";
                audio.play("sfx_spell"); shake = 6;
                break;
            case 6:     // Защита
                pShield = 50; shieldTurns = 5;
                status = "Щит 50 на 5 ходов.";
                audio.play("sfx_heal");
                break;
            default:    // Лечение
                pHp = Math.min(pMax, pHp + 40);
                status = "Замок исцелён (+40).";
                audio.play("sfx_heal");
                break;
        }
        if (ok) { mana -= CARD_COST[k]; selCard = -1; }
        cleanup();
        checkEnd();
    }

    void endTurn() {
        log.clear();
        for (Unit u : new ArrayList<>(units))
            if (u.player && u.alive()) act(u);
        cleanup();
        bossAbilities();
        for (Unit u : new ArrayList<>(units))
            if (!u.player && u.alive()) act(u);
        cleanup();
        if (pHp > 0 && eHp > 0) spawnWave();
        if (shieldTurns > 0) {
            shieldTurns--;
            if (shieldTurns == 0) pShield = 0;
        }
        mana = Math.min(MAX_MANA, mana + 1 + turn / 5);   // мана растёт быстрее с ходами
        turn++;
        status = "";
        checkEnd();
        postInvalidate();
        if (scene == BATTLE && auto) scheduleAuto(900);
    }

    void spawnWave() {
        // Больше врагов и постепенное открытие высоких тиров, включая драконов.
        int attempts = Math.min(9, 2 + bLevel / 35 + turn / 4);
        double chance = Math.min(0.96, 0.48 + bLevel * 0.001 + turn * 0.012);
        if (bBoss && turn % 4 == 0 && enemyCount() < 18)
            spawnBoss(bossType(), rnd.nextInt(LANES), 1.0 + turn * 0.01);
        int maxTier = Math.min(3, (bLevel - 1) / 25);
        List<Integer> pool = new ArrayList<>();
        for (int t = 0; t < EN_NAME.length; t++) if (EN_TIER[t] <= maxTier) pool.add(t);
        for (int i = 0; i < attempts && enemyCount() < 18; i++) {
            if (rnd.nextDouble() < chance)
                spawnEnemy(pool.get(rnd.nextInt(pool.size())), rnd.nextInt(LANES), false);
        }
        if (bLevel % STAGES == 4 && turn % 3 == 0 && enemyCount() < 18)
            spawnEnemy(0, rnd.nextInt(LANES), false);
    }

    void act(Unit u) {
        int moves = Math.max(1, u.speed);
        for (int i = 0; i < moves; i++) {
            if (attack(u)) return;
            if (!advance(u)) return;
        }
    }

    void damageCastle(int d) {
        int absorb = Math.min(pShield, d);
        pShield -= absorb;
        pHp -= (d - absorb);
    }

    boolean attack(Unit u) {
        Unit target = null;
        int best = Integer.MAX_VALUE;
        for (Unit e : units) {
            if (!e.alive() || e.player == u.player || e.lane != u.lane) continue;
            int d = Math.abs(e.col - u.col);
            if (d <= u.range && d < best) { best = d; target = e; }
        }
        int dmg = u.dmg;
        if (u.boss && u.bt == 3 && u.hp < u.maxHp / 2) dmg *= 2;    // орк-вождь в ярости
        if (target != null) {
            target.hp -= dmg;
            addFloat(target.lane, target.col, "-" + dmg, Color.rgb(255, 230, 120));
            spawnBurst(target.lane, target.col, Color.rgb(255, 240, 180), 4);
            audio.play("sfx_hit");
            log.add(u.name + " бьёт " + target.name + " (-" + dmg + ")");
            return true;
        }
        int castleDist = u.player ? COLS - u.col : u.col + 1;
        if (castleDist <= u.range) {
            if (u.player) {
                int d = u.dmg;
                eHp -= d;
                addFloat(u.lane, COLS - 1, "-" + d, Color.rgb(255, 230, 120));
                audio.play("sfx_hit");
                shake = Math.max(shake, 5);
                log.add(u.name + " бьёт вражеский замок (-" + d + ")");
            } else {
                int before = pHp + pShield;
                damageCastle(dmg);
                addFloat(u.lane, 0, "-" + (before - (pHp + pShield)), Color.rgb(255, 90, 80));
                audio.play("sfx_castle");
                shake = Math.max(shake, 10);
                log.add(u.name + " бьёт ваш замок (-" + dmg + ")");
            }
            return true;
        }
        return false;
    }

    boolean advance(Unit u) {
        int nc = u.col + (u.player ? 1 : -1);
        if (nc < 0 || nc >= COLS || occupied(u.lane, nc)) return false;
        u.col = nc;
        return true;
    }

    void addFloat(int lane, int col, String s, int color) {
        floats.add(new FloatText(cellCX(col), cellCY(lane, col), s, color));
    }

    void cleanup() {
        boolean died = false;
        for (int i = units.size() - 1; i >= 0; i--) {
            Unit u = units.get(i);
            if (!u.alive()) {
                spawnBurst(u.lane, u.col, u.player ? Color.rgb(120, 255, 140) : Color.rgb(255, 120, 90), u.boss ? 30 : 12);
                if (u.boss) { flash = 150; shake = 12; }
                died = true;
                units.remove(i);
            }
        }
        if (died) audio.play("sfx_die");
    }


    void checkEnd() {
        if (scene != BATTLE) return;
        if (pHp <= 0) finish(false);
        else if (eHp <= 0) finish(true);
    }

    void finish(boolean win) {
        scene = RESULT;
        resWin = win;
        removeCallbacks(autoTick);
        audio.stopMusic();
        audio.play(win ? "sfx_win" : "sfx_lose");
        if (win) {
            totalWins++;
            int p = pHp * 100 / pMax;
            resStars = p >= 70 ? 3 : (p >= 40 ? 2 : 1);
            resReward = 15 + bLevel / 3 + resStars * 5 + (bBoss ? 30 : 0);
            gold += resReward;
            if (bLevel <= MAX_STARS_LEVEL) stars[bLevel] = Math.max(stars[bLevel], resStars);
            if (bLevel >= unlocked) unlocked = bLevel + 1;
            save();
        }
    }
}
