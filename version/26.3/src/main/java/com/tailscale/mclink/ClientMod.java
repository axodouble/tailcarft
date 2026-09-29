/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import com.mojang.blaze3d.platform.ClipboardManager;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientMod {
    private static final Logger LOG = LoggerFactory.getLogger("mclink");

    private static ScreenState state;

    // Connect-button state, driven from the proven per-tick Minecraft hook.
    // Forge/NeoForge do not weave Mixins into the late-loaded screen classes
    // (JoinMultiplayerScreen, Screen), so the button is added on the tick hook
    // instead, which works on every loader. See ADR-005.
    private static JoinMultiplayerScreen lastMps;
    private static int lastMpsW;
    private static int lastMpsH;

    // Cached reflective handles for the protected Screen widget methods, used
    // when the ScreenAccessorMixin (target Screen) has not been applied.
    private static Method addWidgetMethod;
    private static Method removeWidgetMethod;

    // Marker server-list entry + click-to-join, driven from the per-tick hook on
    // ModLauncher loaders (Forge/NeoForge), where the late-loaded
    // MultiplayerScreenMixin is not woven (ADR-005). The entry is added
    // reflectively; the join is re-routed when the marker is the selected entry
    // and the screen next becomes a ConnectScreen (the sentinel ip hangs the
    // connect, giving the hook a window). Inert on Fabric/Quilt: the Mixin
    // intercepts the click before the connect is attempted.
    private static boolean markerSelected;
    private static java.lang.reflect.Field fMpsServers;
    private static java.lang.reflect.Field fMpsSelection;
    private static java.lang.reflect.Field fMpsEdit;
    private static java.lang.reflect.Field fMpsDelete;
    private static java.lang.reflect.Field fServerListInternal;
    private static java.lang.reflect.Field fConnectAborted;
    private static java.lang.reflect.Field fConnectParent;

    private ClientMod() {}

    public static void onInitializeClient() {
        state = new ScreenState();
    }

    public static ScreenState state() {
        return state;
    }

    // Kept for the shared loader screen-init hook. 26.3 wires the connect
    // button through the per-tick ensureConnectButton hook instead (works on
    // Forge, where the late-loaded MultiplayerScreenMixin is skipped).
    public static void onScreenInit(Minecraft client, Screen screen, int width, int height) {
    }

    // Adds the "Connect with Tailcarft" button to the multiplayer screen.
    // Idempotent: removes any prior instance first, so re-adding after a
    // resize (which re-runs vanilla repositionElements) does not stack.
    private static void addConnectButton(Minecraft client, Screen screen, int width, int height) {
        Button direct = findButton(screen, "selectServer.direct");
        if (direct == null) {
            return;
        }
        removeOurs(screen, "mclink.join");
        int firstRowY = direct.getY();
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getY() == firstRowY)
                .forEach(button -> button.setY(button.getY() - 24));
        for (var child : screen.children()) {
            if (child instanceof ServerSelectionList list) {
                list.setRectangle(width, height - 120, 0, 32);
                break;
            }
        }
        mclinkAddWidget(screen, Button.builder(Component.translatable("mclink.join"),
                button -> client.gui.setScreen(new JoinRemoteScreen(screen)))
                .bounds(width / 2 - 102, firstRowY, 204, 20).build());
    }

    /**
     * Driven from {@link #onTick}. Adds the connect button on the first tick
     * after the multiplayer screen is shown and again after a resize, which is
     * when vanilla re-lays-out the widget row. Guarded by instance + size so
     * the row reposition does not compound on every tick.
     */
    private static void ensureConnectButton(Minecraft client) {
        Screen cur = client.gui.screen();
        if (!(cur instanceof JoinMultiplayerScreen mps)) {
            lastMps = null;
            return;
        }
        if (mps != lastMps || mps.width != lastMpsW || mps.height != lastMpsH) {
            lastMps = mps;
            lastMpsW = mps.width;
            lastMpsH = mps.height;
            addConnectButton(client, mps, mps.width, mps.height);
        }
    }

    /**
     * Driven from {@link #onTick}. On ModLauncher loaders (Forge/NeoForge) the
     * late-loaded {@code MultiplayerScreenMixin} is not woven (ADR-005), so the
     * marker server-list entry and its click-to-join are delivered here instead:
     * the entry is added reflectively, edit/delete are disabled while it is
     * selected, and when the selected entry is the marker and the screen next
     * becomes a {@link ConnectScreen} the (hung) connect is aborted and re-routed
     * to the Tailcarft join flow. Inert on Fabric/Quilt, where the Mixin
     * intercepts the click before the connect is attempted.
     */
    private static void ensureMarkerEntry(Minecraft client) {
        Screen cur = client.gui.screen();
        if (cur instanceof JoinMultiplayerScreen mps) {
            addMarkerIfMissing(mps);
            markerSelected = markerIsSelected(mps);
            if (markerSelected) {
                disableMarkerButtons(mps);
            }
        } else if (cur instanceof ConnectScreen connect) {
            if (markerSelected) {
                markerSelected = false;
                reRouteMarkerJoin(client, connect);
            }
        } else {
            markerSelected = false;
        }
    }

    private static void addMarkerIfMissing(JoinMultiplayerScreen mps) {
        TailcarftConfig config = TailcarftConfig.load();
        if (config == null) {
            return;
        }
        try {
            if (fServerListInternal == null) {
                fServerListInternal = ServerList.class.getDeclaredField("serverList");
                fServerListInternal.setAccessible(true);
            }
            ServerList list = mpsServers(mps);
            if (list.get(TailcarftServerEntry.MARKER) != null) {
                return;
            }
            @SuppressWarnings("unchecked")
            java.util.List<ServerData> internal = (java.util.List<ServerData>) fServerListInternal.get(list);
            internal.add(0, TailcarftServerEntry.create(config));
            mpsSelectionList(mps).updateOnlineServers(list);
        } catch (Throwable t) {
            LOG.error("Failed to add Tailcarft server entry", t);
        }
    }

    private static ServerList mpsServers(JoinMultiplayerScreen mps) throws Exception {
        if (fMpsServers == null) {
            fMpsServers = JoinMultiplayerScreen.class.getDeclaredField("servers");
            fMpsServers.setAccessible(true);
        }
        return (ServerList) fMpsServers.get(mps);
    }

    private static ServerSelectionList mpsSelectionList(JoinMultiplayerScreen mps) throws Exception {
        if (fMpsSelection == null) {
            fMpsSelection = JoinMultiplayerScreen.class.getDeclaredField("serverSelectionList");
            fMpsSelection.setAccessible(true);
        }
        return (ServerSelectionList) fMpsSelection.get(mps);
    }

    private static boolean markerIsSelected(JoinMultiplayerScreen mps) {
        try {
            ServerSelectionList.Entry selected = mpsSelectionList(mps).getSelected();
            if (selected instanceof ServerSelectionList.OnlineServerEntry entry) {
                return TailcarftServerEntry.MARKER.equals(entry.getServerData().ip);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static ServerSelectionList.Entry findMarkerEntry(JoinMultiplayerScreen mps) {
        try {
            for (ServerSelectionList.Entry entry : mpsSelectionList(mps).children()) {
                if (entry instanceof ServerSelectionList.OnlineServerEntry online
                        && TailcarftServerEntry.MARKER.equals(online.getServerData().ip)) {
                    return entry;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void disableMarkerButtons(JoinMultiplayerScreen mps) {
        try {
            if (fMpsEdit == null) {
                fMpsEdit = JoinMultiplayerScreen.class.getDeclaredField("editButton");
                fMpsEdit.setAccessible(true);
            }
            if (fMpsDelete == null) {
                fMpsDelete = JoinMultiplayerScreen.class.getDeclaredField("deleteButton");
                fMpsDelete.setAccessible(true);
            }
            Button edit = (Button) fMpsEdit.get(mps);
            Button delete = (Button) fMpsDelete.get(mps);
            if (edit != null) {
                edit.active = false;
            }
            if (delete != null) {
                delete.active = false;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void reRouteMarkerJoin(Minecraft client, ConnectScreen cur) {
        try {
            if (fConnectAborted == null) {
                fConnectAborted = ConnectScreen.class.getDeclaredField("aborted");
                fConnectAborted.setAccessible(true);
            }
            if (fConnectParent == null) {
                fConnectParent = ConnectScreen.class.getDeclaredField("parent");
                fConnectParent.setAccessible(true);
            }
            fConnectAborted.set(cur, true);
            Screen parent = (Screen) fConnectParent.get(cur);
            TailcarftConfig config = TailcarftConfig.load();
            if (config == null) {
                LOG.warn("Tailcarft server entry clicked but config is missing; ignoring");
                return;
            }
            client.gui.setScreen(new JoinRemoteScreen(parent, config.invite()));
        } catch (Throwable t) {
            LOG.error("Failed to re-route Tailcarft marker join", t);
        }
    }

    /**
     * Adds a widget to a screen via the ScreenAccessorMixin when it has been
     * applied, otherwise reflectively against the protected
     * {@code Screen.addRenderableWidget}. The reflective path is needed because
     * Forge does not apply Mixin to some late-loaded classes, and
     * {@code addRenderableWidget} is protected so it cannot be called directly.
     */
    @SuppressWarnings("unchecked")
    private static <T extends GuiEventListener & Renderable & NarratableEntry> T mclinkAddWidget(
            Screen screen, T widget) {
        if (screen instanceof ScreenAccessor accessor) {
            return accessor.mclink$addRenderableWidget(widget);
        }
        try {
            if (addWidgetMethod == null) {
                addWidgetMethod = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
                addWidgetMethod.setAccessible(true);
            }
            return (T) addWidgetMethod.invoke(screen, widget);
        } catch (Throwable t) {
            LOG.error("Failed to add Tailcarft connect button (Screen.addRenderableWidget unreachable)", t);
            return widget;
        }
    }

    public static void onTick(Minecraft client) {
        // Add the "Connect with Tailcarft" button and the marker server entry on
        // the tick hook (works on Forge, where the late-loaded
        // MultiplayerScreenMixin is skipped). Runs before the smoke/normal branch
        // so the smoke tests observe both.
        ensureConnectButton(client);
        ensureMarkerEntry(client);
        if (System.getenv("TMC_SERVER_SMOKE") != null) {
            serverSmokeTick(client);
            return;
        }
        if (System.getenv("TMC_GUI_SMOKE") != null) {
            smokeTick(client);
            return;
        }
        state.tick(client);
        String error = state.takeError();
        if (error != null) {
            SystemToast.add(client.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("Remote LAN connection failed"), Component.literal(error));
        }
    }

    public static void onClientStopping(Minecraft client) {
        state.close();
    }

    public static boolean isGuiSmokeTest() {
        return System.getenv("TMC_GUI_SMOKE") != null;
    }

    /**
     * Adds a "Copy Tailcarft Invite" button as its own section in the
     * scrollable world-options content, right under the multiplayer options,
     * when a host session has a ready invite. Adding it to the content (rather
     * than as a top-level screen widget) keeps it with the LAN section and
     * makes it scroll with the rest of the options.
     */
    public static void addCopyInviteButton(Screen screen, LinearLayout content) {
        String invite = state().currentInvite();
        if (invite == null) {
            return;
        }
        removeOurs(screen, "mclink.copy_invite");
        content.addChild(Button.builder(
                Component.translatable("mclink.copy_invite"),
                button -> {
                    button.setMessage(Component.translatable("mclink.copied"));
                    new ClipboardManager().setClipboard(invite);
                })
            .bounds(0, 0, 308, 20).build());
    }

    private static void removeOurs(Screen screen, String translationKey) {
        Button ours;
        while ((ours = findButton(screen, translationKey)) != null) {
            mclinkRemoveWidget(screen, ours);
        }
    }

    /**
     * Removes a widget from a screen via the ScreenAccessorMixin when it has
     * been applied, otherwise reflectively against the protected
     * {@code Screen.removeWidget}. See {@link #mclinkAddWidget}.
     */
    private static void mclinkRemoveWidget(Screen screen, GuiEventListener widget) {
        if (screen instanceof ScreenAccessor accessor) {
            accessor.mclink$removeWidget(widget);
            return;
        }
        try {
            if (removeWidgetMethod == null) {
                removeWidgetMethod = Screen.class.getDeclaredMethod("removeWidget", GuiEventListener.class);
                removeWidgetMethod.setAccessible(true);
            }
            removeWidgetMethod.invoke(screen, widget);
        } catch (Throwable t) {
            LOG.error("Failed to remove Tailcarft connect button (Screen.removeWidget unreachable)", t);
        }
    }

    private static Button findButton(Screen screen, String translationKey) {
        String label = Component.translatable(translationKey).getString();
        return screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label))
                .findFirst().orElse(null);
    }

    // GUI smoke hook: when TMC_GUI_SMOKE is set, the client waits for the
    // startup resource load to finish (the game settles on a stable screen),
    // opens the Multiplayer screen, waits for it to render, then prints a
    // verdict line (TMC_GUI_SMOKE joinButton=true|false) and stays alive so
    // scripts/gui-smoke.sh can capture the real X11 window. Inert in normal
    // play. See ADR-004.
    private static int smokePhase;
    private static int smokeFrame;

    // A minimal "no modifier keys" input, used to click the title screen's
    // Multiplayer button programmatically during the GUI smoke test.
    private static final InputWithModifiers NO_MODS = new InputWithModifiers() {
        @Override
        public int input() {
            return 0;
        }

        @Override
        public int modifiers() {
            return 0;
        }
    };

    private static void smokeTick(Minecraft client) {
        Screen cur = client.gui.screen();
        // Dismiss the first-launch accessibility onboarding screen, which would
        // otherwise block the title screen and stall the smoke test. Handled here
        // on the proven per-tick hook (not only in the one-shot screen-init
        // mixin) so it's robust to startup timing; once we switch screens the
        // guard stops matching and this runs exactly once.
        if (cur instanceof AccessibilityOnboardingScreen) {
            client.gui.setScreen(new TitleScreen());
            return;
        }
        // Phase 0: wait until the title screen is ready (its "Multiplayer"
        // button exists), then click it to open the multiplayer safety screen.
        // (The first-launch accessibility onboarding screen is dismissed by
        // AccessibilityOnboardingScreenMixin before the title screen appears.)
        if (smokePhase == 0) {
            Button multiplayer = cur == null ? null : findButton(cur, "menu.multiplayer");
            if (multiplayer != null) {
                multiplayer.onPress(NO_MODS);
                smokePhase = 1;
                smokeFrame = 0;
            }
            return;
        }
        // Phase 1: wait until the multiplayer safety screen is ready (its
        // "Proceed" button exists), then click it to open the multiplayer
        // server-list screen (JoinMultiplayerScreen).
        if (smokePhase == 1) {
            Button proceed = cur == null ? null : findButtonByLabel(cur, "Proceed");
            if (proceed != null) {
                proceed.onPress(NO_MODS);
                smokePhase = 2;
                smokeFrame = 0;
            }
            return;
        }
        // Phase 2: let the multiplayer screen render, then report the verdict.
        // The client stays alive on this screen afterwards (no in-game
        // screenshot, no exit) so the host can capture the real X11 window.
        if (smokePhase == 2) {
            if (++smokeFrame >= 60) {
                Screen screen = client.gui.screen();
                boolean hasJoin = findButton(screen, "mclink.join") != null;
                System.out.println("TMC_GUI_SMOKE joinButton=" + hasJoin
                        + " screen=" + (screen == null ? "null" : screen.getClass().getName()));
                smokePhase = 3;
            }
            return;
        }
        // Phase 3: keep the client alive on the multiplayer screen.
    }

    // Server-config smoke hook: when TMC_SERVER_SMOKE is set, the client opens
    // the multiplayer screen (same navigation as the GUI smoke test), asserts the
    // marker server entry is present, selects it, invokes the vanilla join, and
    // asserts the join is re-routed to JoinRemoteScreen (the Tailcarft flow).
    // Prints a verdict line (TMC_SERVER_SMOKE entry=... join=...) and stays
    // alive for the host to capture. Inert in normal play. See ADR-005.
    private static int serverSmokePhase;
    private static boolean serverSmokeEntryPresent;

    private static void serverSmokeTick(Minecraft client) {
        Screen cur = client.gui.screen();
        if (cur instanceof AccessibilityOnboardingScreen) {
            client.gui.setScreen(new TitleScreen());
            return;
        }
        switch (serverSmokePhase) {
            case 0: {
                Button multiplayer = cur == null ? null : findButton(cur, "menu.multiplayer");
                if (multiplayer != null) {
                    multiplayer.onPress(NO_MODS);
                    serverSmokePhase = 1;
                }
                return;
            }
            case 1: {
                Button proceed = cur == null ? null : findButtonByLabel(cur, "Proceed");
                if (proceed != null) {
                    proceed.onPress(NO_MODS);
                    serverSmokePhase = 2;
                }
                return;
            }
            case 2: {
                if (cur instanceof JoinMultiplayerScreen mps) {
                    try {
                        serverSmokeEntryPresent =
                                mpsServers(mps).get(TailcarftServerEntry.MARKER) != null;
                    } catch (Throwable ignored) {
                    }
                    ServerSelectionList.Entry marker = findMarkerEntry(mps);
                    if (marker != null) {
                        try {
                            mpsSelectionList(mps).setSelected(marker);
                        } catch (Throwable ignored) {
                        }
                    }
                    serverSmokePhase = 3;
                }
                return;
            }
            case 3: {
                if (cur instanceof JoinMultiplayerScreen mps) {
                    ServerData marker;
                    try {
                        marker = mpsServers(mps).get(TailcarftServerEntry.MARKER);
                    } catch (Throwable ignored) {
                        marker = null;
                    }
                    if (marker != null) {
                        mps.join(marker);
                    }
                    serverSmokePhase = 4;
                }
                return;
            }
            case 4: {
                boolean joined = cur instanceof JoinRemoteScreen;
                System.out.println("TMC_SERVER_SMOKE entry=" + serverSmokeEntryPresent
                        + " join=" + joined
                        + " screen=" + (cur == null ? "null" : cur.getClass().getName()));
                serverSmokePhase = 5;
                return;
            }
            default:
                // Phase 5: keep the client alive on the join screen.
                return;
        }
    }

    // Finds a button by its literal (already-translated) message text, used to
    // click the multiplayer safety screen's "Proceed" button during the smoke
    // test. Distinct from findButton, which matches a translation key.
    private static Button findButtonByLabel(Screen screen, String label) {
        return screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label))
                .findFirst().orElse(null);
    }
}
