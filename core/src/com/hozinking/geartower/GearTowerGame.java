package com.hozinking.geartower;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.TimeUtils;
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
    private float camLookY = TOWER_CENTER_Y, camElevT = 14f, camDistT = 14f, camLookYT = TOWER_CENTER_Y;

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

    // --- suara ---
    private Preferences prefs;
    private boolean soundOn = true;
    private Sound humSound, clickSound, tickSound, tapSound;
    private long humId = -1;
    private int lastClickTooth;
    private long lastClickMs;

    // --- pendulum jam (sisi kanan menara, ayun di bidang XY menghadap kamera) ---
    private static final float PEND_X = 2.7f, PEND_Z = 0.8f, PEND_PIVOT_Y = 5.4f, PEND_L = 1.5f;
    private static final float PEND_G_L = 39.48f; // (2π/T)^2, T = 1 detik
    private static final float PEND_DAMP = 0.08f;
    private static final float PEND_KICK = 0.6f; // impuls escapement tiap ekstrem (motor ON)
    private Model pendFrameModel, pendRodModel, pendBobModel;
    private ModelInstance pendRodInst, pendBobInst;
    private float pendTheta = 0.35f, pendOmega;
    private float prevPendOmega;
    private boolean grabbingPend, tickHigh;
    private float pendProjX = -1f, pendProjY = -1f, pendPivotProjX = -1f, pendPivotProjY = -1f;
    private float pendGrabRadiusPx = 200f;

    // --- tema terang/gelap ---
    private boolean themeDark;
    private float clearR = 0.875f, clearG = 0.902f, clearB = 0.933f;
    private Color textColor = C_TEXT;
    private ColorAttribute ambientAttr;
    private ModelInstance groundInst, gridInst, shadowInst;

    // --- HUD: teks di-cache, di-refresh 10x/detik (hindari alokasi per-frame) ---
    private float hudTimer;
    private String hudRpm = "", hudBottom = "", hudTop = "", hudInfo = "", hudMotor = "", hudSpeed = "", hudPerf = "";

    // --- HUD (koordinat layar, origin kiri-atas) ---
    private float pad = 24f, btnGap = 16f, btnH = 110f, btnY, btnW;
    private float sliderY;
    private final Rectangle btnMotor = new Rectangle();
    private final Rectangle btnDir = new Rectangle();
    private final Rectangle btnReset = new Rectangle();
    private final Rectangle btnSound = new Rectangle();
    private final Rectangle btnTheme = new Rectangle();
    private final Rectangle btnCamTop = new Rectangle();
    private final Rectangle btnCamMotor = new Rectangle();
    private final Rectangle btnCamReset = new Rectangle();
    private float row2Y, row3Y;
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
        ambientAttr = new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.6f, 1f);
        env.set(ambientAttr);
        env.add(new DirectionalLight().set(1.0f, 0.95f, 0.85f, 0.5f, -1f, -0.7f)); // key hangat 1.0, depan-kiri-atas
        env.add(new DirectionalLight().set(0.30f, 0.34f, 0.40f, -0.7f, -0.4f, 0.6f)); // fill dingin 0.4, dari kanan

        prefs = Gdx.app.getPreferences("geartower");
        soundOn = prefs.getBoolean("sound", true);
        themeDark = prefs.getBoolean("theme_dark", false);
        SoundSynth.ensureSounds();
        humSound = loadSound("sounds/motor_hum.wav");
        clickSound = loadSound("sounds/click.wav");
        tickSound = loadSound("sounds/tick.wav");
        tapSound = loadSound("sounds/tap.wav");

        gearModel = buildGearMerged(GEAR_R, GEAR_THICK, 28, C_GEAR, true);
        gearModelTop = buildGearMerged(GEAR_R, GEAR_THICK, 28, C_GEAR_TOP, true);
        pinionModel = buildGearMerged(0.26f, GEAR_THICK, 10, C_PINION, false);
        baseModel = buildBox(3.6f, 0.5f, 3.6f, C_BASE);
        motorModel = buildBox(1.1f, 0.9f, 1.1f, C_MOTOR);
        groundModel = buildBox(30f, 0.1f, 30f, C_GROUND);
        gridModel = buildGrid();
        shadowModel = buildShadowDisc();
        ringModel = buildRing();

        groundInst = new ModelInstance(groundModel);
        groundInst.transform.setToTranslation(0, -0.55f, 0);
        instances.add(groundInst);
        gridInst = new ModelInstance(gridModel);
        instances.add(gridInst);
        shadowInst = new ModelInstance(shadowModel);
        shadowInst.transform.setToTranslation(0, -0.49f, 0);
        instances.add(shadowInst);
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

        // pendulum jam di sisi kanan menara (rangka statis + batang + bob)
        pendFrameModel = buildPendFrame();
        pendRodModel = buildPendRod();
        pendBobModel = buildPendBob();
        instances.add(new ModelInstance(pendFrameModel));
        pendRodInst = new ModelInstance(pendRodModel);
        pendBobInst = new ModelInstance(pendBobModel);
        instances.add(pendRodInst);
        instances.add(pendBobInst);

        grabRadiusPx = 120f * Gdx.graphics.getDensity();
        pendGrabRadiusPx = 100f * Gdx.graphics.getDensity();
        layoutHud(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        applyTheme();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                ptrX[pointer] = x;
                ptrY[pointer] = y;
                // PERBAIKAN v1.0.1: sentuhan origin kiri-atas, HUD digambar origin kiri-bawah —
                // hit-test tombol/slider harus pakai Y yang sudah dibalik, kalau tidak tombol tak bisa ditekan.
                float hy = Gdx.graphics.getHeight() - y;
                if (pointer == 0) {
                    if (btnMotor.contains(x, hy)) { motorOn = !motorOn; playTap(); return true; }
                    if (btnDir.contains(x, hy)) { motorDir = -motorDir; playTap(); return true; }
                    if (btnReset.contains(x, hy)) { reset(); playTap(); return true; }
                    if (btnSound.contains(x, hy)) { setSound(!soundOn); playTap(); return true; }
                    if (btnTheme.contains(x, hy)) { setTheme(!themeDark); playTap(); return true; }
                    if (btnCamTop.contains(x, hy)) { camLookYT = gearY(STAGES - 1); camElevT = 22f; camDistT = 12f; playTap(); return true; }
                    if (btnCamMotor.contains(x, hy)) { camLookYT = 0.8f; camElevT = 8f; camDistT = 12f; playTap(); return true; }
                    if (btnCamReset.contains(x, hy)) { camLookYT = TOWER_CENTER_Y; camElevT = 14f; camDistT = 14f; playTap(); return true; }
                    if (sliderRect.contains(x, hy)) { sliderDrag = true; setSpeedFromX(x); return true; }
                    // pegang bob pendulum? (posisi layarnya dihitung tiap frame di render)
                    if (pendProjX >= 0 && dist(x, y, pendProjX, pendProjY) <= pendGrabRadiusPx) {
                        grabbingPend = true;
                        pendOmega = 0;
                        return true;
                    }
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
                    grabbingPend = false;
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
                        camDistT = camDist;
                    }
                    pinchDist = d;
                    return true;
                }
                float dx = x - lastX;
                float dy = y - lastY;
                lastX = x;
                lastY = y;
                if (pointer == 0 && grabbingPend) {
                    // geser bob: sudut dari pivot (layar, y ke bawah): θ = atan2(dx, dy)
                    pendTheta = MathUtils.clamp((float) Math.atan2(x - pendPivotProjX, y - pendPivotProjY), -0.6f, 0.6f);
                    pendOmega = 0;
                    return true;
                }
                if (pointer == 0 && grabbingTop) {
                    float vel = MathUtils.clamp(dx * 16f, -720f, 720f);
                    topVel = topVel * 0.6f + vel * 0.4f;
                    return true;
                }
                if (pointer == 0 && cranking) {
                    float vel = MathUtils.clamp(dx * 960f, -2880f, 2880f);
                    dragVel = dragVel * 0.6f + vel * 0.4f;
                    camElev = MathUtils.clamp(camElev - dy * 0.06f, 4f, 50f);
                    camElevT = camElev;
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
                    if (grabbingPend) { grabbingPend = false; prevPendOmega = 0; }
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
        hudPerf = "FPS: " + Gdx.graphics.getFramesPerSecond() + "  |  Draw: " + drawCalls();
    }

    /** Jumlah draw call ≈ jumlah model yang di-render (tiap model 1 mesh part). */
    private int drawCalls() {
        return instances.size + (grabbingTop ? 1 : 0);
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
        row2Y = sliderY - 100f - btnH;
        btnSound.set(pad, row2Y, btnW, btnH);
        btnTheme.set(pad + btnW + btnGap, row2Y, btnW, btnH);
        btnCamTop.set(pad + (btnW + btnGap) * 2, row2Y, btnW, btnH);
        row3Y = row2Y - btnGap - btnH;
        btnCamMotor.set(pad, row3Y, btnW, btnH);
        btnCamReset.set(pad + btnW + btnGap, row3Y, btnW, btnH);
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

        // --- pendulum: θ'' = -(g/L)·sin θ - c·ω; escapement saat motor ON ---
        if (!grabbingPend) {
            float acc = -PEND_G_L * MathUtils.sin(pendTheta) - PEND_DAMP * pendOmega;
            pendOmega += acc * delta;
            pendTheta += pendOmega * delta;
            if (pendOmega * prevPendOmega < 0f) { // sampai ekstrem
                if (motorOn && Math.abs(pendTheta) > 0.12f) {
                    pendOmega -= Math.signum(pendTheta) * PEND_KICK; // dorong ke arah gerak berikutnya
                }
                onPendulumExtreme();
            }
            prevPendOmega = pendOmega;
            pendOmega = MathUtils.clamp(pendOmega, -3.5f, 3.5f);
        }
        float pd = pendTheta * MathUtils.radiansToDegrees;
        pendRodInst.transform.idt().translate(PEND_X, PEND_PIVOT_Y, PEND_Z).rotate(Vector3.Z, pd);
        pendBobInst.transform.idt().translate(PEND_X, PEND_PIVOT_Y, PEND_Z).rotate(Vector3.Z, pd).translate(0, -PEND_L, 0);

        // --- kamera: lerp halus ke target (tombol / drag / pinch) ---
        float lk = Math.min(1f, delta * 3f);
        camElev += (camElevT - camElev) * lk;
        camDist += (camDistT - camDist) * lk;
        camLookY += (camLookYT - camLookY) * lk;
        float e = camElev * MathUtils.degreesToRadians;
        cam.position.set(0, camLookY + camDist * MathUtils.sin(e), camDist * MathUtils.cos(e));
        cam.lookAt(0, camLookY, 0);
        cam.up.set(0, 1, 0);
        cam.update();

        // posisi layar gear atas (origin kiri-atas) — untuk tes pegang di touchDown
        tmpV.set(0, gearY(STAGES - 1), 0);
        cam.project(tmpV);
        topProjX = tmpV.x;
        topProjY = Gdx.graphics.getHeight() - tmpV.y;

        // posisi layar bob + pivot pendulum (origin kiri-atas)
        float bs = MathUtils.sin(pendTheta) * PEND_L, bc = MathUtils.cos(pendTheta) * PEND_L;
        tmpV.set(PEND_X + bs, PEND_PIVOT_Y - bc, PEND_Z);
        cam.project(tmpV);
        pendProjX = tmpV.x;
        pendProjY = Gdx.graphics.getHeight() - tmpV.y;
        tmpV.set(PEND_X, PEND_PIVOT_Y, PEND_Z);
        cam.project(tmpV);
        pendPivotProjX = tmpV.x;
        pendPivotProjY = Gdx.graphics.getHeight() - tmpV.y;

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(clearR, clearG, clearB, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);
        for (ModelInstance inst : instances) modelBatch.render(inst, env);
        if (grabbingTop) modelBatch.render(topRingInst, env);
        modelBatch.end();

        updateSound();
        updateGearClicks();

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
        drawButton(btnSound, soundOn ? C_BTN_ON : C_BTN_OFF);
        drawButton(btnTheme, C_BTN);
        drawButton(btnCamTop, C_BTN);
        drawButton(btnCamMotor, C_BTN);
        drawButton(btnCamReset, C_BTN);
        shapes.end();

        spriteBatch.begin();
        float tx = pad;
        float ty = pad + 44f;
        float lh = 48f;
        font.setColor(textColor);
        font.draw(spriteBatch, hudRpm, tx, ty); ty += lh;
        font.draw(spriteBatch, hudBottom, tx, ty); ty += lh;
        font.draw(spriteBatch, hudTop, tx, ty); ty += lh;
        font.draw(spriteBatch, hudInfo, tx, ty); ty += lh;
        font.draw(spriteBatch, hudMotor, tx, ty); ty += lh;
        font.draw(spriteBatch, hudPerf, tx, ty); ty += lh;
        if (grabbingTop) {
            font.setColor(C_TEXT_HELD);
            font.draw(spriteBatch, "Gear atas dipegang — geser buat muter", tx, ty); ty += lh;
            font.setColor(textColor);
        }
        if (grabbingPend) {
            font.setColor(C_TEXT_HELD);
            font.draw(spriteBatch, "Pendulum dipegang — geser buat ayun", tx, ty); ty += lh;
            font.setColor(textColor);
        }
        font.draw(spriteBatch, hudSpeed, pad, sliderY - 8f);
        drawButtonLabel(btnMotor, motorOn ? "MOTOR: ON" : "MOTOR: OFF");
        drawButtonLabel(btnDir, motorDir > 0 ? "ARAH: MAJU" : "ARAH: MUNDUR");
        drawButtonLabel(btnReset, "RESET");
        drawButtonLabel(btnSound, soundOn ? "SUARA: ON" : "SUARA: OFF");
        drawButtonLabel(btnTheme, themeDark ? "TEMA: GELAP" : "TEMA: TERANG");
        drawButtonLabel(btnCamTop, "LIHAT ATAS");
        drawButtonLabel(btnCamMotor, "LIHAT MOTOR");
        drawButtonLabel(btnCamReset, "RESET KAMERA");
        spriteBatch.end();
    }

    private void drawButton(Rectangle r, Color c) {
        shapes.setColor(c);
        shapes.rect(r.x, r.y, r.width, r.height);
    }

    private void drawButtonLabel(Rectangle r, String text) {
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, text, r.x, r.y + r.height / 2f + 16f, r.width, Align.center, false);
        font.setColor(textColor);
    }

    // --- suara ---
    private Sound loadSound(String path) {
        try {
            return Gdx.audio.newSound(Gdx.files.local(path));
        } catch (Exception e) {
            return null;
        }
    }

    private void playTap() {
        if (soundOn && tapSound != null) tapSound.play(0.5f);
    }

    private void setSound(boolean on) {
        soundOn = on;
        prefs.putBoolean("sound", on);
        prefs.flush();
        if (!on && humId != -1 && humSound != null) {
            humSound.stop(humId);
            humId = -1;
        }
    }

    /** Hum motor: loop, volume + pitch mengikuti kecepatan; 0 saat motor mati. */
    private void updateSound() {
        float spd = Math.abs(baseVel);
        if (soundOn && humSound != null) {
            if (humId == -1) humId = humSound.loop(0f);
            float vol = motorOn ? MathUtils.clamp(spd / 720f, 0f, 1f) * 0.45f : 0f;
            float pitch = 0.7f + MathUtils.clamp(spd / 1440f, 0f, 1f) * 0.8f;
            humSound.setVolume(humId, vol);
            humSound.setPitch(humId, pitch);
        } else if (humId != -1 && humSound != null) {
            humSound.stop(humId);
            humId = -1;
        }
    }

    /** Klik tiap gigi gear bawah lewat satu titik, throttle maks ~20/detik. */
    private void updateGearClicks() {
        float spd = Math.abs(baseVel);
        if (!soundOn || clickSound == null || spd < 30f) return;
        int tooth = (int) Math.floor(angle[0] / (360f / 28f));
        if (tooth != lastClickTooth) {
            lastClickTooth = tooth;
            long now = TimeUtils.millis();
            if (now - lastClickMs >= 50) {
                lastClickMs = now;
                clickSound.play(MathUtils.clamp(spd / 1440f, 0.15f, 0.8f), 1f, 0f);
            }
        }
    }

    /** Tick-tock di tiap ekstrem pendulum; berhenti bila ayunan nyaris diam. */
    private void onPendulumExtreme() {
        if (!soundOn || tickSound == null || Math.abs(pendTheta) < 0.05f) return;
        tickHigh = !tickHigh;
        tickSound.play(0.5f, tickHigh ? 1.15f : 0.8f, 0f);
    }

    // --- tema ---
    private void setTheme(boolean dark) {
        themeDark = dark;
        prefs.putBoolean("theme_dark", dark);
        prefs.flush();
        applyTheme();
    }

    private void applyTheme() {
        if (themeDark) {
            clearR = 0.08f; clearG = 0.08f; clearB = 0.10f;
            textColor = new Color(0.92f, 0.92f, 0.95f, 1f);
            ambientAttr.color.set(0.7f, 0.7f, 0.7f, 1f);
            setDiffuse(groundInst, 0.16f, 0.16f, 0.19f, 1f);
            setDiffuse(gridInst, 0.35f, 0.35f, 0.38f, 1f); // tint gelap (vertex color dikali diffuse)
            setDiffuse(shadowInst, 0f, 0f, 0f, 0.10f);
        } else {
            clearR = 0.875f; clearG = 0.902f; clearB = 0.933f;
            textColor = C_TEXT;
            ambientAttr.color.set(0.6f, 0.6f, 0.6f, 1f);
            setDiffuse(groundInst, 0.90f, 0.91f, 0.93f, 1f);
            setDiffuse(gridInst, 1f, 1f, 1f, 1f);
            setDiffuse(shadowInst, 0f, 0f, 0f, 0.28f);
        }
    }

    private void setDiffuse(ModelInstance inst, float r, float g, float b, float a) {
        if (inst == null) return;
        inst.materials.get(0).set(ColorAttribute.createDiffuse(r, g, b, a));
    }

    /** Rangka pendulum: 2 tiang + palang atas/tengah + kaki + pivot — 1 mesh. */
    private Model buildPendFrame() {
        ModelBuilder mb = new ModelBuilder();
        Material mat = new Material(
                ColorAttribute.createDiffuse(0.20f, 0.20f, 0.23f, 1f),
                ColorAttribute.createSpecular(0.4f, 0.4f, 0.4f, 1f),
                FloatAttribute.createShininess(24f));
        mb.begin();
        mb.node().id = "pendframe";
        MeshPartBuilder b = mb.part("pendframe", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal, mat);
        Matrix4 t = new Matrix4();
        float postH = PEND_PIVOT_Y + 0.5f;
        for (float px : new float[]{PEND_X - 0.32f, PEND_X + 0.32f}) {
            t.idt().translate(px, -0.5f + postH / 2f, PEND_Z);
            b.setVertexTransform(t);
            b.box(0.12f, postH, 0.12f);
        }
        t.idt().translate(PEND_X, PEND_PIVOT_Y, PEND_Z);
        b.setVertexTransform(t);
        b.box(0.76f, 0.12f, 0.12f);
        t.idt().translate(PEND_X, 2.0f, PEND_Z);
        b.setVertexTransform(t);
        b.box(0.76f, 0.08f, 0.08f);
        for (float px : new float[]{PEND_X - 0.32f, PEND_X + 0.32f}) {
            t.idt().translate(px, -0.46f, PEND_Z);
            b.setVertexTransform(t);
            b.box(0.42f, 0.08f, 0.34f);
        }
        t.idt().translate(PEND_X, PEND_PIVOT_Y, PEND_Z).rotate(Vector3.X, 90f);
        b.setVertexTransform(t);
        b.cylinder(0.18f, 0.2f, 0.18f, 12);
        t.idt();
        b.setVertexTransform(t);
        return mb.end();
    }

    /** Batang pendulum: box tipis, origin di pivot (menggantung ke -Y). */
    private Model buildPendRod() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.node().id = "pendrod";
        MeshPartBuilder b = mb.part("pendrod", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
                new Material(
                        ColorAttribute.createDiffuse(0.55f, 0.42f, 0.18f, 1f),
                        ColorAttribute.createSpecular(0.5f, 0.5f, 0.5f, 1f),
                        FloatAttribute.createShininess(32f)));
        Matrix4 t = new Matrix4();
        t.idt().translate(0, -PEND_L / 2f, 0);
        b.setVertexTransform(t);
        b.box(0.07f, PEND_L, 0.07f);
        t.idt();
        b.setVertexTransform(t);
        return mb.end();
    }

    /** Bob kuningan: bola mengkilap. */
    private Model buildPendBob() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.part("pendbob", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
                new Material(
                        ColorAttribute.createDiffuse(0.85f, 0.65f, 0.25f, 1f),
                        ColorAttribute.createSpecular(0.9f, 0.9f, 0.9f, 1f),
                        FloatAttribute.createShininess(64f)))
                .sphere(0.56f, 0.56f, 0.56f, 16, 12);
        return mb.end();
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
        if (humId != -1 && humSound != null) humSound.stop(humId);
        if (humSound != null) humSound.dispose();
        if (clickSound != null) clickSound.dispose();
        if (tickSound != null) tickSound.dispose();
        if (tapSound != null) tapSound.dispose();
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
        pendFrameModel.dispose();
        pendRodModel.dispose();
        pendBobModel.dispose();
    }
}
