package dev.envoy;

import dev.envoy.gui.EnvoyGuiController;
import dev.envoy.module.beam.BeamModule;
import dev.envoy.module.border.BorderModule;
import dev.envoy.module.coloredfire.ColoredFireModule;
import dev.envoy.module.combat.CombatModule;
import dev.envoy.module.combat.CombatState;
import dev.envoy.module.cooldown.CooldownModule;
import dev.envoy.module.events.EnvoyEvents;
import dev.envoy.module.glow.GlowModule;
import dev.envoy.module.hologram.HologramModule;
import dev.envoy.module.icon.IconRegistry;
import dev.envoy.module.limb.LimbModule;
import dev.envoy.module.marker.MarkerModule;
import dev.envoy.module.model.ModelRegistry;
import dev.envoy.module.nametag.NametagModule;
import dev.envoy.module.notification.NotificationModule;
import dev.envoy.module.team.TeamModule;
import dev.envoy.module.title.TitleModule;
import dev.envoy.module.tntcountdown.TntCountdownModule;
import dev.envoy.module.tntcountdown.TntCountdownState;
import dev.envoy.module.vignette.VignetteModule;
import dev.envoy.module.waypoint.LocalWaypoints;
import dev.envoy.module.waypoint.WaypointController;
import dev.envoy.module.waypoint.WaypointModule;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.platform.RenderGameOverlayEvent;
import dev.envoy.platform.RenderLivingEvent;
import dev.envoy.platform.RenderWorldLastEvent;
import dev.envoy.platform.TickEvent;
import dev.envoy.render.BeamRenderer;
import dev.envoy.render.BorderRenderer;
import dev.envoy.render.BossBarRenderer;
import dev.envoy.render.CooldownRenderer;
import dev.envoy.render.CustomModelRenderer;
import dev.envoy.render.GlowOverlayListener;
import dev.envoy.render.HologramRenderer;
import dev.envoy.render.LightmapGuard;
import dev.envoy.render.LightningRenderer;
import dev.envoy.render.MarkerRenderer;
import dev.envoy.render.NametagRenderer;
import dev.envoy.render.NotificationRenderer;
import dev.envoy.render.Projection;
import dev.envoy.render.TeamRenderer;
import dev.envoy.render.TitleRenderer;
import dev.envoy.render.TntCountdownRenderer;
import dev.envoy.render.VignetteRenderer;
import dev.envoy.render.WaypointRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.NetworkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Client entrypoint and the fail-open boundary around game callbacks. */
public final class Envoy implements ClientModInitializer {
    public static final String MOD_ID = "envoy";
    public static final String MOD_NAME = "Envoy";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    private interface Task { void run(); }
    private interface Toggle { boolean enabled(); }

    private static final Map<String, Integer> FAILURE_COUNTS = new HashMap<String, Integer>();
    private static final EnvoyGuiController GUI_CONTROLLER = new EnvoyGuiController();
    private static final EnvoyEvents GUI_LISTENER = new EnvoyEvents();
    private static final CustomModelRenderer MODEL_RENDERER = new CustomModelRenderer();
    private static final BeamRenderer BEAM_RENDERER = new BeamRenderer();
    private static final BorderRenderer BORDER_RENDERER = new BorderRenderer();
    private static final BossBarRenderer BOSS_RENDERER = new BossBarRenderer();
    private static final CooldownRenderer COOLDOWN_RENDERER = new CooldownRenderer();
    private static final GlowOverlayListener GLOW_RENDERER = new GlowOverlayListener();
    private static final HologramRenderer HOLOGRAM_RENDERER = new HologramRenderer();
    private static final TntCountdownRenderer TNT_RENDERER = new TntCountdownRenderer();
    private static final LightningRenderer LIGHTNING_RENDERER = new LightningRenderer();
    private static final MarkerRenderer MARKER_RENDERER = new MarkerRenderer();
    private static final NametagRenderer NAMETAG_RENDERER = new NametagRenderer();
    private static final NotificationRenderer NOTIFICATION_RENDERER = new NotificationRenderer();
    private static final TeamRenderer TEAM_RENDERER = new TeamRenderer();
    private static final TitleRenderer TITLE_RENDERER = new TitleRenderer();
    private static final VignetteRenderer VIGNETTE_RENDERER = new VignetteRenderer();
    private static final WaypointRenderer WAYPOINT_RENDERER = new WaypointRenderer();

    @Override
    public void onInitializeClient() {
        ApolloNetwork network = bootstrap();
        if (network == null) return;
        registerModules(network);
        attempt("mixin audit", MixinAudit::report);
        LOGGER.info("[Envoy] Ornithe client initialized");
    }

    public static void problem(String hook, Throwable failure) {
        int occurrence;
        synchronized (FAILURE_COUNTS) {
            Integer previous = FAILURE_COUNTS.get(hook);
            occurrence = previous == null ? 1 : previous.intValue() + 1;
            FAILURE_COUNTS.put(hook, Integer.valueOf(occurrence));
        }
        if (occurrence <= 3) {
            LOGGER.error("[Envoy] error in " + hook + " (occurrence " + occurrence + "), carrying on", failure);
        }
    }

    public static void onConnect(final NetworkManager manager) {
        attempt("connect", () -> ApolloNetwork.getInstance().onConnect(manager));
    }

    public static void onJoinGame(final NetworkManager manager) {
        attempt("join game", () -> ApolloNetwork.getInstance().onJoinGame(manager));
    }

    public static void onDisconnect() {
        attempt("disconnect", () -> ApolloNetwork.getInstance().onDisconnect());
        attempt("tnt countdown clear", TntCountdownState::clear);
    }

    public static void onClientTickEnd() {
        attempt("client tick", () -> {
            TickEvent.ClientTickEvent tick = new TickEvent.ClientTickEvent(TickEvent.Phase.END);
            MODEL_RENDERER.onClientTick(tick);
            GUI_CONTROLLER.onClientTick(tick);
            WaypointController.tick();
            TITLE_RENDERER.tick();
        });
    }

    public static void onRenderStart(final float partialTicks) {
        when(() -> EnvoySettings.customModels, "render start",
            () -> MODEL_RENDERER.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START, partialTicks)));
    }

    public static void onRenderWorld(float partialTicks) {
        final RenderWorldLastEvent event = new RenderWorldLastEvent(partialTicks);
        Projection.newFrame();
        // nothing to draw in the world this frame: skip the GL state snapshot (reading GL state makes the game wait for the driver)
        if (dev.envoy.module.beam.BeamState.isEmpty() && dev.envoy.module.border.BorderState.isEmpty()
                && dev.envoy.module.hologram.HologramState.isEmpty() && TntCountdownState.isEmpty()
                && dev.envoy.module.lightning.LightningState.isEmpty() && dev.envoy.module.marker.MarkerState.all().isEmpty()
                && dev.envoy.module.team.TeamState.members().isEmpty() && dev.envoy.module.waypoint.WaypointState.all().isEmpty()
                && !LocalWaypoints.hasCurrent() && !NAMETAG_RENDERER.hasWork()) return;
        LightmapGuard.begin();
        try {
            when(() -> EnvoySettings.beams, "beam render", () -> BEAM_RENDERER.onRenderWorld(event));
            when(() -> EnvoySettings.border, "border render", () -> BORDER_RENDERER.onRenderWorld(event));
            when(() -> EnvoySettings.holograms, "hologram render", () -> HOLOGRAM_RENDERER.onRenderWorld(event));
            when(() -> EnvoySettings.nametags, "nametag render", () -> NAMETAG_RENDERER.renderQueued(event.partialTicks));
            when(() -> EnvoySettings.tntCountdown, "tnt countdown render", () -> TNT_RENDERER.onRenderWorld(event));
            when(() -> EnvoySettings.lightning, "lightning render", () -> LIGHTNING_RENDERER.onRenderWorld(event));
            when(() -> EnvoySettings.markers, "marker render", () -> MARKER_RENDERER.onRenderWorld(event));
            when(() -> EnvoySettings.teamView, "team render", () -> TEAM_RENDERER.onRenderWorld(event));
            when(() -> EnvoySettings.waypoints, "waypoint render", () -> WAYPOINT_RENDERER.onRenderWorld(event));
        } finally {
            LightmapGuard.end();
        }
    }

    public static void onOverlay(final float partialTicks) {
        final RenderGameOverlayEvent.Post event = new RenderGameOverlayEvent.Post(RenderGameOverlayEvent.ElementType.ALL);
        when(() -> EnvoySettings.cooldowns, "cooldown overlay", () -> COOLDOWN_RENDERER.onRenderOverlay(event));
        when(() -> EnvoySettings.glow, "glow overlay", () -> GLOW_RENDERER.onRenderOverlayPost(event));
        when(() -> EnvoySettings.notifications, "notification overlay", () -> NOTIFICATION_RENDERER.onRenderOverlay(event));
        when(() -> EnvoySettings.vignette, "vignette overlay", () -> VIGNETTE_RENDERER.onRenderOverlay(event));
        when(() -> EnvoySettings.titles, "title overlay", () -> TITLE_RENDERER.render(partialTicks));
    }

    public static boolean onBossOverlay() {
        final RenderGameOverlayEvent.Pre event = new RenderGameOverlayEvent.Pre(RenderGameOverlayEvent.ElementType.BOSSHEALTH);
        when(() -> EnvoySettings.bossBars, "boss overlay", () -> BOSS_RENDERER.onRenderOverlay(event));
        return event.isCanceled();
    }

    public static boolean onRenderLiving(EntityLivingBase entity, double x, double y, double z) {
        final RenderLivingEvent.Pre<EntityLivingBase> event = new RenderLivingEvent.Pre<EntityLivingBase>(entity, x, y, z);
        when(() -> EnvoySettings.customModels, "custom model render", () -> MODEL_RENDERER.onRenderLiving(event));
        return event.isCanceled();
    }

    public static boolean onRenderName(EntityLivingBase entity, double x, double y, double z) {
        final RenderLivingEvent.Specials.Pre<EntityLivingBase> event = new RenderLivingEvent.Specials.Pre<EntityLivingBase>(entity, x, y, z);
        when(() -> EnvoySettings.nametags, "nametag render", () -> NAMETAG_RENDERER.onRenderName(event));
        return event.isCanceled();
    }

    public static void onGuiOpen(final GuiScreen screen) {
        when(() -> EnvoySettings.chat, "gui open", () -> GUI_LISTENER.onGuiOpen(screen));
    }

    private static ApolloNetwork bootstrap() {
        try {
            File directory = FabricLoader.getInstance().getConfigDir().toFile();
            EnvoyConfig.init(directory);
            LocalWaypoints.init(directory);
            ApolloNetwork network = ApolloNetwork.getInstance();
            CombatState.localSettingsChanged();
            return network;
        } catch (Throwable failure) {
            problem("initialize", failure);
            return null;
        }
    }

    private static void registerModules(final ApolloNetwork network) {
        attempt("glow module", () -> GlowModule.register(network));
        attempt("vignette module", () -> VignetteModule.register(network));
        attempt("combat module", () -> CombatModule.register(network));
        attempt("model registry", ModelRegistry::init);
        attempt("icon registry", IconRegistry::init);
        if (LunarHost.isLunar()) return;
        attempt("nametag module", () -> NametagModule.register(network));
        attempt("cooldown module", () -> CooldownModule.register(network));
        attempt("team module", () -> TeamModule.register(network));
        attempt("waypoint module", () -> WaypointModule.register(network));
        attempt("marker module", () -> MarkerModule.register(network));
        attempt("beam module", () -> BeamModule.register(network));
        attempt("notification module", () -> NotificationModule.register(network));
        attempt("border module", () -> BorderModule.register(network));
        attempt("hologram module", () -> HologramModule.register(network));
        attempt("colored fire module", () -> ColoredFireModule.register(network));
        attempt("limb module", () -> LimbModule.register(network));
        attempt("title module", () -> TitleModule.register(network));
        attempt("tnt countdown module", () -> TntCountdownModule.register(network));
        attempt("staff module", () -> dev.envoy.module.staff.StaffModule.register(network));
    }

    private static void when(Toggle toggle, String label, Task task) {
        if (toggle.enabled()) attempt(label, task);
    }

    private static void attempt(String label, Task task) {
        try {
            task.run();
        } catch (Throwable failure) {
            problem(label, failure);
        }
    }
}
