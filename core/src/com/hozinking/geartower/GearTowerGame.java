package com.hozinking.geartower;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

import java.util.Locale;

/**
 * Simulasi menara gear 1000 (ala video viral): 16 stage gear bertumpuk,
 * tiap stage 4x lebih lambat dari stage di bawahnya, arah berselang-seling.
 * Gear paling atas nyaris diam — itu inti demonya.
 *
 * Interaksi utama: drag horizontal = engkol menara (bolak-balik),
 * drag vertikal = orbit kamera, pinch = zoom.
 */
public class GearTowerGame extends ApplicationAdapter {
    private static final int STAGES = 16;
    private static final float GEAR_R = 1.0f;
    private static final float GEAR_THICK = 0.26f;
    private static final float PITCH = 0.42f;
    private static final float PINION_X = 1.26f;
    private static final float MOTOR_BASE_SPEED = 720f; // deg/detik gear bawah = 120 RPM di 1x
    private static final float SPEED_MIN = 0.25f;
    private static final float SPEED_MAX = 20f;
    private static final float TOWER_CENTER_Y = 3.7f;

    private PerspectiveCamera cam;
    private OrthographicCamera hudCam;
    private ModelBatch modelBatch;
    private SpriteBatch spriteBatch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Environment env;

    private Model gearModel, gearModelTop, pinionModel, baseModel, motorModel;
    private Model groundModel, gridModel, shadowModel, ringModel;
    private ModelInstance topRingInst;
    private final Array<ModelInstance> instances = new Array<>();
    private final ModelInstance[] gearInst = new ModelInstance[STAGES];
    private final ModelInstance[] pinionInst = new ModelInstance[STAGES];

    private final float[] angle = new float[STAGES];
    private final float[] pinionAngle = new float[STAGES];
    private float baseVel; // deg/detik gear bawah (bertanda)
    private float turnsBottom, turnsTop;
    private boolean motorOn = true;
    private float motorDir = 1f;
    private float speedMult = 1f;

    private float camElev = 14f; // derajat
    private float camDist = 14f;

    // --- input ---
    private boolean cranking;
    private float dragVel;
    private float lastX, lastY;
    private boolean pinching;
    private float pinchDist;
    private int pinchA = -1, pinchB = -1;
    private final float[] ptrX = new float[20];
    private final float[] ptrY = new float[20];
    private boolean sliderDrag;
    private boolean grabbingTop, spinDown;
    private float topVel;
    private float topProjX = -1f, topProjY = -1f;
    private float grabRadiusPx = 300f;
    private final Vector3 tmpV = new Vector3();

    // --- HUD: teks di-cache, di-refresh 10x/detik (hindari alokasi per-frame) ---
    private float hudTimer;
    private String hudRpm = "", hudBottom = "", hudTop = "", hudInfo = "", hudMotor = "", hudSpeed = "";

    // --- HUD (koordinat layar, origin kiri-atas) ---
    private float pad = 24f, btnGap = 16f, btnH = 110f, btnY, btnW;
    private float sliderY;
    private final Rectangle btnMotor = new Rectangle();
    private final Rectangle btnDir = new Rectangle();
    private final Rectangle btnReset = new Rectangle();
    private final Rectangle sliderRect = new Rectangle();

    private static final Color C_GEAR = new Color(0.25f, 0.25f, 0.28f, 1f);
    private static final Color C_GEAR_TOP = new Color(0.50f, 0.50f, 0.55f, 1f);
    private static final Color C_PINION = new Color(0.45f, 0.45f, 0.50f, 1f);
    private static final Color C_BASE = new Color(0.55f, 0.57f, 0.62f, 1f);
    private static final Color C_MOTOR = new Color(0.30f, 0.30f, 0.34f, 1f);
    private static final Color C_GROUND = new Color(0.90f, 0.91f, 0.93f, 1f);
    private static final Color C_GRID = new Color(0.78f, 0.80f, 0.84f, 1f);
    private static final Color C_RING = new Color(1.0f, 0.62f, 0.10f, 1f);
    private static final Color C_TEXT = new Color(0.10f, 0.10f, 0.12f, 1f);
    private static final Color C_TEXT_HELD = new Color(0.75f, 0.42f, 0.0f, 1f);
    private static final Color C_BTN_ON = new Color(0.13f, 0.45f, 0.20f, 1f);
    private static final Color C_BTN_OFF = new Color(0.32f, 0.32f, 0.36f, 1f);
    private static final Color C_BTN = new Color(0.20f, 0.22f, 0.30f, 1f);

    @Override
    public void create() {
        cam = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.near = 0.1f;
        cam.far = 100f;
        hudCam = new OrthographicCamera();

        modelBatch = new ModelBatch();
        spriteBatch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getData().setScale(2f);
        font.setColor(C_TEXT);

        env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.6f, 1f));
        env.add(new DirectionalLight().set(1.0f, 0.95f, 0.85f, 0.5f, -1f, -0.7f)); // key hangat 1.0, depan-kiri-atas
        env.add(new DirectionalLight().set(0.30f, 0.34f, 0.40f, -0.7f, -0.4f, 0.6f)); // fill dingin 0.4, dari kanan

        gearModel = buildGearMerged(GEAR_R, GEAR_THICK, 28, C_GEAR, true);
        gearModelTop = buildGearMerged(GEAR_R, GEAR_THICK, 28, C_GEAR_TOP, true);
        pinionModel = buildGearMerged(0.26f, GEAR_THICK, 10, C_PINION, false);
        baseModel = buildBox(3.6f, 0.5f, 3.6f, C_BASE);
        motorModel = buildBox(1.1f, 0.9f, 1.1f, C_MOTOR);
        groundModel = buildBox(30f, 0.1f, 30f, C_GROUND);
        gridModel = buildGrid();
        shadowModel = buildShadowDisc();
        ringModel = buildRing();

        ModelInstance ground = new ModelInstance(groundModel);
        ground.transform.setToTranslation(0, -0.55f, 0);
        instances.add(ground);
        ModelInstance grid = new ModelInstance(gridModel);
        instances.add(grid);
        ModelInstance shadow = new ModelInstance(shadowModel);
        shadow.transform.setToTranslation(0, -0.49f, 0);
        instances.add(shadow);
        topRingInst = new ModelInstance(ringModel);
        topRingInst.transform.setToTranslation(0, gearY(STAGES - 1) + GEAR_THICK / 2f + 0.03f, 0);

        ModelInstance base = new ModelInstance(baseModel);
        base.transform.setToTranslation(0, -0.25f, 0);
        instances.add(base);
        ModelInstance motor = new ModelInstance(motorModel);
        motor.transform.setToTranslation(2.1f, 0.45f, 0);
        instances.add(motor);
        for (int i = 0; i < STAGES; i++) {
            gearInst[i] = new ModelInstance(i == STAGES - 1 ? gearModelTop : gearModel);
            pinionInst[i] = new ModelInstance(pinionModel);
            instances.add(gearInst[i]);
            instances.add(pinionInst[i]);
        }

        grabRadiusPx = 120f * Gdx.graphics.getDensity();
        layoutHud(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                ptrX[pointer] = x;
                ptrY[pointer] = y;
                // PERBAIKAN v1.0.1: sentuhan origin kiri-atas, HUD digambar origin kiri-bawah —
                // hit-test tombol/slider harus pakai Y yang sudah dibalik, kalau tidak tombol tak bisa ditekan.
                float hy = Gdx.graphics.getHeight() - y;
                if (pointer == 0) {
                    if (btnMotor.contains(x, hy)) { motorOn = !motorOn; return true; }
                    if (btnDir.contains(x, hy)) { motorDir = -motorDir; return true; }
                    if (btnReset.contains(x, hy)) { reset(); return true; }
                    if (sliderRect.contains(x, hy)) { sliderDrag = true; setSpeedFromX(x); return true; }
                    // pegang gear atas? (posisi layarnya dihitung tiap frame di render)
                    if (topProjX >= 0 && dist(x, y, topProjX, topProjY) <= grabRadiusPx) {
                        grabbingTop = true;
                        spinDown = false;
                        cranking = false;
                        topVel = 0;
                        lastX = x;
                        lastY = y;
                        return true;
                    }
                }
                if (pinchA == -1) {
                    pinchA = pointer;
                } else if (pinchB == -1 && pointer != pinchA) {
                    pinchB = pointer;
                    pinching = true;
                    cranking = false;
                    grabbingTop = false;
                    pinchDist = dist(ptrX[pinchA], ptrY[pinchA], ptrX[pointer], ptrY[pointer]);
                }
                if (!pinching && !grabbingTop) {
                    cranking = true;
                    dragVel = 0;
                }
                lastX = x;
                lastY = y;
                return true;
            }

            @Override
            public boolean touchDragged(int x, int y, int pointer) {
                ptrX[pointer] = x;
                ptrY[pointer] = y;
                if (sliderDrag && pointer == 0) { setSpeedFromX(x); return true; }
                if (pinching && pinchA >= 0 && pinchB >= 0) {
                    float d = dist(ptrX[pinchA], ptrY[pinchA], ptrX[pinchB], ptrY[pinchB]);
                    if (pinchDist > 0 && d > 0) {
                        camDist = MathUtils.clamp(camDist * pinchDist / d, 8f, 24f);
                    }
                    pinchDist = d;
                    return true;
                }
                float dx = x - lastX;
                float dy = y - lastY;
                lastX = x;
                lastY = y;
                if (pointer == 0 && grabbingTop) {
                    float vel = MathUtils.clamp(dx * 16f, -720f, 720f);
                    topVel = topVel * 0.6f + vel * 0.4f;
                    return true;
                }
                if (pointer == 0 && cranking) {
                    float vel = MathUtils.clamp(dx * 960f, -2880f, 2880f);
                    dragVel = dragVel * 0.6f + vel * 0.4f;
                    camElev = MathUtils.clamp(camElev - dy * 0.06f, 4f, 50f);
                }
                return true;
            }

            @Override
            public boolean touchUp(int x, int y, int pointer, int button) {
                if (pointer == pinchA) pinchA = -1;
                if (pointer == pinchB) pinchB = -1;
                if (pinchA == -1 || pinchB == -1) pinching = false;
                if (pointer == 0) {
                    if (grabbingTop) { grabbingTop = false; spinDown = true; }
                    cranking = false;
                    sliderDrag = false;
                }
                return true;
            }
        });
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Faktor kecepatan stage i terhadap gear bawah: 4x lebih lambat per stage, arah selang-seling. */
    private float factor(int i) {
        float f = 1f;
        for (int k = 0; k < i; k++) f /= 4f;
        return (i % 2 == 0) ? f : -f;
    }

    private float gearY(int i) {
        return 0.55f + i * PITCH;
    }

    private void reset() {
        for (int i = 0; i < STAGES; i++) {
            angle[i] = 0;
            pinionAngle[i] = 0;
        }
        turnsBottom = 0;
        turnsTop = 0;
        baseVel = 0;
        dragVel = 0;
        topVel = 0;
        grabbingTop = false;
        spinDown = false;
    }

    private void setSpeedFromX(float x) {
        float frac = MathUtils.clamp((x - sliderRect.x) / sliderRect.width, 0f, 1f);
        speedMult = SPEED_MIN + frac * (SPEED_MAX - SPEED_MIN);
    }

    /** Refresh teks HUD (dipanggil 10x/detik saja, bukan tiap frame). */
    private void refreshHudStrings() {
        hudRpm = "RPM gear bawah: " + fmt1(baseVel / 6f);
        hudBottom = "Putaran bawah: " + fmt2(turnsBottom) + "x";
        hudTop = "Putaran atas: " + fmt9(turnsTop) + "x";
        hudInfo = "Stage: " + STAGES + "  |  Arah: " + (motorDir > 0 ? "Maju" : "Mundur");
        hudMotor = "Motor: " + (motorOn ? "ON" : "OFF") + "  |  Kecepatan: " + fmt2(speedMult) + "x";
        hudSpeed = "Kecepatan: " + fmt2(speedMult) + "x  (0.25x - 20x)";
    }

    private void layoutHud(float w, float h) {
        hudCam.setToOrtho(false, w, h);
        btnW = (w - pad * 2 - btnGap * 2) / 3f;
        btnY = h - pad - btnH;
        btnMotor.set(pad, btnY, btnW, btnH);
        btnDir.set(pad + btnW + btnGap, btnY, btnW, btnH);
        btnReset.set(pad + (btnW + btnGap) * 2, btnY, btnW, btnH);
        sliderY = btnY - 78f;
        sliderRect.set(pad, sliderY, w - pad * 2, 56f);
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        layoutHud(width, height);
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);

        if (grabbingTop) {
            // putar dari gear atas: menara mengikuti lewat rasio terbalik
            baseVel = topVel / factor(STAGES - 1);
        } else if (cranking) {
            baseVel = dragVel;
        } else if (spinDown) {
            // habis lepas gear atas: putaran raksasa diluruhkan cepat sebelum motor ambil alih
            baseVel *= (float) Math.pow(1e-4f, delta);
            float target = motorDir * MOTOR_BASE_SPEED * speedMult;
            if (Math.abs(baseVel) <= Math.max(Math.abs(target), 720f)) spinDown = false;
        } else if (motorOn) {
            float target = motorDir * MOTOR_BASE_SPEED * speedMult;
            baseVel += (target - baseVel) * Math.min(1f, delta * 2.5f);
        } else {
            baseVel *= (float) Math.pow(0.2f, delta);
            if (Math.abs(baseVel) < 0.5f) baseVel = 0;
        }

        turnsBottom += baseVel * delta / 360f;
        turnsTop += baseVel * factor(STAGES - 1) * delta / 360f;
        for (int i = 0; i < STAGES; i++) {
            float f = factor(i);
            angle[i] += baseVel * f * delta;
            pinionAngle[i] += baseVel * (-4f) * f * delta;
            gearInst[i].transform.idt().translate(0, gearY(i), 0).rotate(Vector3.Y, angle[i]);
            pinionInst[i].transform.idt().translate(PINION_X, gearY(i), 0).rotate(Vector3.Y, pinionAngle[i]);
        }

        float e = camElev * MathUtils.degreesToRadians;
        cam.position.set(0, TOWER_CENTER_Y + camDist * MathUtils.sin(e), camDist * MathUtils.cos(e));
        cam.lookAt(0, TOWER_CENTER_Y, 0);
        cam.up.set(0, 1, 0);
        cam.update();

        // posisi layar gear atas (origin kiri-atas) — untuk tes pegang di touchDown
        tmpV.set(0, gearY(STAGES - 1), 0);
        cam.project(tmpV);
        topProjX = tmpV.x;
        topProjY = Gdx.graphics.getHeight() - tmpV.y;

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.875f, 0.902f, 0.933f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);
        for (ModelInstance inst : instances) modelBatch.render(inst, env);
        if (grabbingTop) modelBatch.render(topRingInst, env);
        modelBatch.end();

        hudTimer -= delta;
        if (hudTimer <= 0f) {
            hudTimer = 0.1f;
            refreshHudStrings();
        }

        drawHud();
    }

    private void drawHud() {
        hudCam.update();
        shapes.setProjectionMatrix(hudCam.combined);
        spriteBatch.setProjectionMatrix(hudCam.combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // slider: track + knob
        shapes.setColor(0.72f, 0.74f, 0.78f, 1f);
        shapes.rect(sliderRect.x, sliderY + 24f, sliderRect.width, 8f);
        float frac = (speedMult - SPEED_MIN) / (SPEED_MAX - SPEED_MIN);
        shapes.setColor(0.15f, 0.15f, 0.18f, 1f);
        shapes.rect(sliderRect.x + frac * sliderRect.width - 14f, sliderY + 8f, 28f, 40f);
        // tombol
        drawButton(btnMotor, motorOn ? C_BTN_ON : C_BTN_OFF);
        drawButton(btnDir, C_BTN);
        drawButton(btnReset, C_BTN);
        shapes.end();

        spriteBatch.begin();
        float tx = pad;
        float ty = pad + 44f;
        float lh = 48f;
        font.setColor(C_TEXT);
        font.draw(spriteBatch, hudRpm, tx, ty); ty += lh;
        font.draw(spriteBatch, hudBottom, tx, ty); ty += lh;
        font.draw(spriteBatch, hudTop, tx, ty); ty += lh;
        font.draw(spriteBatch, hudInfo, tx, ty); ty += lh;
        font.draw(spriteBatch, hudMotor, tx, ty); ty += lh;
        if (grabbingTop) {
            font.setColor(C_TEXT_HELD);
            font.draw(spriteBatch, "Gear atas dipegang — geser buat muter", tx, ty); ty += lh;
            font.setColor(C_TEXT);
        }
        font.draw(spriteBatch, hudSpeed, pad, sliderY - 8f);
        drawButtonLabel(btnMotor, motorOn ? "MOTOR: ON" : "MOTOR: OFF");
        drawButtonLabel(btnDir, motorDir > 0 ? "ARAH: MAJU" : "ARAH: MUNDUR");
        drawButtonLabel(btnReset, "RESET");
        spriteBatch.end();
    }

    private void drawButton(Rectangle r, Color c) {
        shapes.setColor(c);
        shapes.rect(r.x, r.y, r.width, r.height);
    }

    private void drawButtonLabel(Rectangle r, String text) {
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, text, r.x, r.y + r.height / 2f + 16f, r.width, Align.center, false);
        font.setColor(C_TEXT);
    }

    private String fmt1(float v) { return String.format(Locale.US, "%.1f", v); }
    private String fmt2(float v) { return String.format(Locale.US, "%.2f", v); }
    private String fmt9(float v) { return String.format(Locale.US, "%.9f", v); }

    /** Gear SATU mesh (1 draw call): disc + gigi + penanda dalam satu MeshPart, warna via vertex color. */
    private Model buildGearMerged(float radius, float thick, int teeth, Color color, boolean withMarker) {
        ModelBuilder mb = new ModelBuilder();
        Material mat = new Material(
                ColorAttribute.createDiffuse(Color.WHITE),
                ColorAttribute.createSpecular(0.6f, 0.6f, 0.6f, 1f),
                FloatAttribute.createShininess(48f));
        mb.begin();
        mb.node().id = "gear";
        MeshPartBuilder b = mb.part("gear", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.ColorPacked, mat);
        Matrix4 t = new Matrix4();
        b.setColor(color);
        b.cylinder(radius * 2f, thick, radius * 2f, 16);
        float toothTang = radius * 0.10f;
        float toothRad = radius * 0.18f;
        float toothR = radius + toothRad * 0.28f;
        for (int k = 0; k < teeth; k++) {
            float a = 360f * k / teeth;
            float rad = a * MathUtils.degreesToRadians;
            t.idt().translate(MathUtils.cos(rad) * toothR, 0, MathUtils.sin(rad) * toothR)
                    .rotate(Vector3.Y, 90f - a);
            b.setVertexTransform(t);
            b.box(toothTang, thick, toothRad);
        }
        t.idt();
        b.setVertexTransform(t);
        if (withMarker) {
            b.setColor(Color.WHITE);
            t.translate(radius * 0.55f, thick / 2f + 0.012f, 0);
            b.setVertexTransform(t);
            b.box(radius * 0.9f, 0.024f, 0.08f);
            t.idt();
            b.setVertexTransform(t);
        }
        return mb.end();
    }

    /** Grid halus di lantai: kotak-kotak tipis dalam satu mesh (1 draw call). */
    private Model buildGrid() {
        ModelBuilder mb = new ModelBuilder();
        Material mat = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        mb.begin();
        mb.node().id = "grid";
        MeshPartBuilder b = mb.part("grid", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.ColorPacked, mat);
        Matrix4 t = new Matrix4();
        b.setColor(C_GRID);
        for (int i = -10; i <= 10; i++) {
            t.idt().translate(0, -0.493f, i);
            b.setVertexTransform(t);
            b.box(20f, 0.012f, 0.05f);
            t.idt().translate(i, -0.493f, 0);
            b.setVertexTransform(t);
            b.box(0.05f, 0.012f, 20f);
        }
        t.idt();
        b.setVertexTransform(t);
        return mb.end();
    }

    /** Bayangan kontak palsu: disc hitam transparan di bawah alas. */
    private Model buildShadowDisc() {
        ModelBuilder mb = new ModelBuilder();
        Material mat = new Material(
                ColorAttribute.createDiffuse(0f, 0f, 0f, 0.28f),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA));
        mb.begin();
        mb.node().id = "shadow";
        mb.part("shadow", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, mat)
                .cylinder(2.6f * 2f, 0.02f, 2.6f * 2f, 32);
        return mb.end();
    }

    /** Cincin amber di sekeliling gear atas: kotak-kotak kecil melingkar, 1 mesh. */
    private Model buildRing() {
        ModelBuilder mb = new ModelBuilder();
        Material mat = new Material(
                ColorAttribute.createDiffuse(C_RING),
                ColorAttribute.createEmissive(0.9f, 0.5f, 0.05f, 1f));
        mb.begin();
        mb.node().id = "ring";
        MeshPartBuilder b = mb.part("ring", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.ColorPacked, mat);
        Matrix4 t = new Matrix4();
        b.setColor(Color.WHITE);
        float ringR = GEAR_R + 0.11f;
        int segs = 32;
        for (int k = 0; k < segs; k++) {
            float a = 360f * k / segs;
            float rad = a * MathUtils.degreesToRadians;
            t.idt().translate(MathUtils.cos(rad) * ringR, 0, MathUtils.sin(rad) * ringR)
                    .rotate(Vector3.Y, -a);
            b.setVertexTransform(t);
            b.box(0.05f, 0.03f, 0.24f);
        }
        t.idt();
        b.setVertexTransform(t);
        return mb.end();
    }

    private Model buildBox(float w, float h, float d, Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.part("box", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
                new Material(ColorAttribute.createDiffuse(color)))
                .box(w, h, d);
        return mb.end();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        spriteBatch.dispose();
        shapes.dispose();
        font.dispose();
        gearModel.dispose();
        gearModelTop.dispose();
        pinionModel.dispose();
        baseModel.dispose();
        motorModel.dispose();
        groundModel.dispose();
        gridModel.dispose();
        shadowModel.dispose();
        ringModel.dispose();
    }
}
