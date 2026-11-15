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
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
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
    private static final float MOTOR_BASE_SPEED = 120f; // deg/detik gear bawah
    private static final float TOWER_CENTER_Y = 3.7f;

    private PerspectiveCamera cam;
    private OrthographicCamera hudCam;
    private ModelBatch modelBatch;
    private SpriteBatch spriteBatch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Environment env;

    private Model gearModel, pinionModel, baseModel, motorModel;
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

    // --- HUD (koordinat layar, origin kiri-atas) ---
    private float pad = 24f, btnGap = 16f, btnH = 110f, btnY, btnW;
    private float sliderY;
    private final Rectangle btnMotor = new Rectangle();
    private final Rectangle btnDir = new Rectangle();
    private final Rectangle btnReset = new Rectangle();
    private final Rectangle sliderRect = new Rectangle();

    private static final Color C_GEAR = new Color(0.16f, 0.16f, 0.18f, 1f);
    private static final Color C_PINION = new Color(0.38f, 0.38f, 0.42f, 1f);
    private static final Color C_BASE = new Color(0.12f, 0.12f, 0.14f, 1f);
    private static final Color C_MOTOR = new Color(0.22f, 0.22f, 0.26f, 1f);
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
        font.setColor(Color.WHITE);

        env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.45f, 0.45f, 0.45f, 1f));
        env.add(new DirectionalLight().set(0.9f, 0.9f, 0.9f, -0.6f, -1f, -0.8f));

        gearModel = buildGear(GEAR_R, GEAR_THICK, 28, C_GEAR, true);
        pinionModel = buildGear(0.26f, GEAR_THICK, 10, C_PINION, false);
        baseModel = buildBox(3.6f, 0.5f, 3.6f, C_BASE);
        motorModel = buildBox(1.1f, 0.9f, 1.1f, C_MOTOR);

        ModelInstance base = new ModelInstance(baseModel);
        base.transform.setToTranslation(0, -0.25f, 0);
        instances.add(base);
        ModelInstance motor = new ModelInstance(motorModel);
        motor.transform.setToTranslation(2.1f, 0.45f, 0);
        instances.add(motor);
        for (int i = 0; i < STAGES; i++) {
            gearInst[i] = new ModelInstance(gearModel);
            pinionInst[i] = new ModelInstance(pinionModel);
            instances.add(gearInst[i]);
            instances.add(pinionInst[i]);
        }

        layoutHud(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                ptrX[pointer] = x;
                ptrY[pointer] = y;
                if (pointer == 0) {
                    if (btnMotor.contains(x, y)) { motorOn = !motorOn; return true; }
                    if (btnDir.contains(x, y)) { motorDir = -motorDir; return true; }
                    if (btnReset.contains(x, y)) { reset(); return true; }
                    if (sliderRect.contains(x, y)) { sliderDrag = true; setSpeedFromX(x); return true; }
                }
                if (pinchA == -1) {
                    pinchA = pointer;
                } else if (pinchB == -1 && pointer != pinchA) {
                    pinchB = pointer;
                    pinching = true;
                    cranking = false;
                    pinchDist = dist(ptrX[pinchA], ptrY[pinchA], ptrX[pointer], ptrY[pointer]);
                }
                if (!pinching) {
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
                if (pointer == 0 && cranking) {
                    float vel = MathUtils.clamp(dx * 240f, -720f, 720f);
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
    }

    private void setSpeedFromX(float x) {
        float frac = MathUtils.clamp((x - sliderRect.x) / sliderRect.width, 0f, 1f);
        speedMult = 0.25f + frac * (4f - 0.25f);
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

        if (cranking) {
            baseVel = dragVel;
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

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.04f, 0.04f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);
        for (ModelInstance inst : instances) modelBatch.render(inst, env);
        modelBatch.end();

        drawHud();
    }

    private void drawHud() {
        hudCam.update();
        shapes.setProjectionMatrix(hudCam.combined);
        spriteBatch.setProjectionMatrix(hudCam.combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // slider: track + knob
        shapes.setColor(0.25f, 0.25f, 0.30f, 1f);
        shapes.rect(sliderRect.x, sliderY + 24f, sliderRect.width, 8f);
        float frac = (speedMult - 0.25f) / (4f - 0.25f);
        shapes.setColor(Color.WHITE);
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
        font.draw(spriteBatch, "RPM gear bawah: " + fmt1(baseVel / 6f), tx, ty); ty += lh;
        font.draw(spriteBatch, "Putaran bawah: " + fmt2(turnsBottom) + "x", tx, ty); ty += lh;
        font.draw(spriteBatch, "Putaran atas: " + fmt9(turnsTop) + "x", tx, ty); ty += lh;
        font.draw(spriteBatch, "Stage: " + STAGES + "  |  Arah: " + (motorDir > 0 ? "Maju" : "Mundur"), tx, ty); ty += lh;
        font.draw(spriteBatch, "Motor: " + (motorOn ? "ON" : "OFF") + "  |  Kecepatan: " + fmt2(speedMult) + "x", tx, ty);
        font.draw(spriteBatch, "Kecepatan: " + fmt2(speedMult) + "x  (0.25x - 4x)", pad, sliderY - 8f);
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
        font.draw(spriteBatch, text, r.x, r.y + r.height / 2f + 16f, r.width, Align.center, false);
    }

    private String fmt1(float v) { return String.format(Locale.US, "%.1f", v); }
    private String fmt2(float v) { return String.format(Locale.US, "%.2f", v); }
    private String fmt9(float v) { return String.format(Locale.US, "%.9f", v); }

    /** Gear: disc cylinder + gigi box di sekeliling rim + garis penanda putih di atas. */
    private Model buildGear(float radius, float thick, int teeth, Color color, boolean withMarker) {
        ModelBuilder mb = new ModelBuilder();
        com.badlogic.gdx.graphics.g3d.Material mat =
                new com.badlogic.gdx.graphics.g3d.Material(ColorAttribute.createDiffuse(color));
        mb.begin();
        mb.node().id = "disc";
        mb.part("disc", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, mat)
                .cylinder(radius * 2f, thick, radius * 2f, 32);
        float toothTang = radius * 0.10f;
        float toothRad = radius * 0.18f;
        float toothR = radius + toothRad * 0.28f;
        for (int k = 0; k < teeth; k++) {
            float a = 360f * k / teeth;
            float rad = a * MathUtils.degreesToRadians;
            Node n = mb.node();
            n.id = "tooth" + k;
            n.translation.set(MathUtils.cos(rad) * toothR, 0, MathUtils.sin(rad) * toothR);
            n.rotation.set(Vector3.Y, 90f - a);
            mb.part("tooth" + k, GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, mat)
                    .box(toothTang, thick, toothRad);
        }
        if (withMarker) {
            com.badlogic.gdx.graphics.g3d.Material white =
                    new com.badlogic.gdx.graphics.g3d.Material(ColorAttribute.createDiffuse(Color.WHITE));
            Node m = mb.node();
            m.id = "marker";
            m.translation.set(radius * 0.55f, thick / 2f + 0.012f, 0);
            mb.part("marker", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, white)
                    .box(radius * 0.9f, 0.024f, 0.08f);
        }
        return mb.end();
    }

    private Model buildBox(float w, float h, float d, Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.part("box", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
                new com.badlogic.gdx.graphics.g3d.Material(ColorAttribute.createDiffuse(color)))
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
        pinionModel.dispose();
        baseModel.dispose();
        motorModel.dispose();
    }
}
