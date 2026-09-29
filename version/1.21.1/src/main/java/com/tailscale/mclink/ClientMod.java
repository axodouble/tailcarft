/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import com.mojang.blaze3d.platform.ClipboardManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientMod {
    private static final Logger LOG = LoggerFactory.getLogger("mclink");

    private static ScreenState state;

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

    public static void onScreenInit(Minecraft client, Screen screen, int width, int height) {
        if (screen instanceof JoinMultiplayerScreen) {
            addConnectButton(client, screen, width, height);
        }
    }

    public static boolean isGuiSmokeTest() {
        return System.getenv("TMC_GUI_SMOKE") != null;
    }

    public static void onTick(Minecraft client) {
        // Add the marker server entry on the tick hook (works on Forge, where
        // the late-loaded MultiplayerScreenMixin is skipped). Runs before the
        // smoke/normal branch so the smoke tests observe it.
        ensureMarkerEntry(client);
        if (System.getenv("TMC_SERVER_SMOKE") != null) {
            serverSmokeTick(client);
            return;
        }
        if (isGuiSmokeTest()) {
            smokeTick(client);
            return;
        }
        state.tick(client);
        String error = state.takeError();
        if (error != null) {
            SystemToast.add(client.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("Remote LAN connection failed"), Component.literal(error));
        }
    }

    public static void onClientStopping(Minecraft client) {
        state.close();
    }

    private static void addConnectButton(Minecraft client, Screen screen, int width, int height) {
        Button direct = findButton(screen, "selectServer.direct");
        if (direct == null) {
            return;
        }
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
        addWidget(screen, Button.builder(Component.translatable("mclink.join"),
                button -> client.setScreen(new JoinRemoteScreen(screen)))
                .bounds(width / 2 - 102, firstRowY, 204, 20).build());
    }

    private static <T extends net.minecraft.client.gui.components.events.GuiEventListener
            & net.minecraft.client.gui.components.Renderable
            & net.minecraft.client.gui.narration.NarratableEntry> T addWidget(Screen screen, T widget) {
        if (screen instanceof ScreenAccessor accessor) {
            return accessor.mclink$addRenderableWidget(widget);
        }
        try {
            var m = Screen.class.getDeclaredMethod("addRenderableWidget",
                    net.minecraft.client.gui.components.events.GuiEventListener.class);
            m.setAccessible(true);
            @SuppressWarnings("unchecked")
            T result = (T) m.invoke(screen, widget);
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to add widget", t);
        }
    }

    private static void removeWidget(Screen screen, net.minecraft.client.gui.components.events.GuiEventListener widget) {
        if (screen instanceof ScreenAccessor accessor) {
            accessor.mclink$removeWidget(widget);
            return;
        }
        try {
            var m = Screen.class.getDeclaredMethod("removeWidget",
                    net.minecraft.client.gui.components.events.GuiEventListener.class);
            m.setAccessible(true);
            m.invoke(screen, widget);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to remove widget", t);
        }
    }

    /**
     * Adds a "Copy Tailcarft Invite" button above the port field when a host
     * session has a ready invite, so the invite can be copied to the
     * clipboard without selecting it in the chat.
     */
    public static void addCopyInviteButton(Screen screen, Minecraft client) {
        String invite = state().currentInvite();
        if (invite == null) {
            return;
        }
        removeOurs(screen, "mclink.copy_invite");
        for (var child : screen.children()) {
            if (child instanceof EditBox edit) {
                addWidget(screen, Button.builder(
                        Component.translatable("mclink.copy_invite"),
                        button -> {
                            button.setMessage(Component.translatable("mclink.copied"));
                            new ClipboardManager().setClipboard(client.getWindow().getWindow(), invite);
                        })
                    .bounds(edit.getX() - 75, edit.getY() - 24, 300, 20).build());
                return;
            }
        }
    }

    private static void removeOurs(Screen screen, String translationKey) {
        Button ours;
        while ((ours = findButton(screen, translationKey)) != null) {
            removeWidget(screen, ours);
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

    // GUI smoke hook: when TMC_GUI_SMOKE is set, the client walks the game's
    // own multiplayer navigation (title "Multiplayer" button, then the safety
    // screen's "Proceed" button) to the JoinMultiplayerScreen, waits for it to
    // render, prints a verdict line (TMC_GUI_SMOKE joinButton=true|false) and
    // stays alive so scripts/gui-smoke.sh can capture the real X11 window.
    // Inert in normal play. See ADR-004.
    private static int smokePhase;
    private static int smokeFrame;

    private static void smokeTick(Minecraft client) {
        Screen cur = client.screen;
        // Phase 0: wait until the title screen is ready (its "Multiplayer"
        // button exists), then click it to open the multiplayer safety screen.
        // (The first-launch accessibility onboarding screen is dismissed by
        // AccessibilityOnboardingScreenMixin before the title screen appears.)
        if (smokePhase == 0) {
            Button multiplayer = cur == null ? null : findButton(cur, "menu.multiplayer");
            if (multiplayer != null) {
                multiplayer.onPress();
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
                proceed.onPress();
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
                Screen screen = client.screen;
                boolean hasJoin = findButton(screen, "mclink.join") != null;
                System.out.println("TMC_GUI_SMOKE joinButton=" + hasJoin
                        + " screen=" + (screen == null ? "null" : screen.getClass().getSimpleName()));
                smokePhase = 3;
            }
            return;
        }
        // Phase 3: keep the client alive on the multiplayer screen.
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

    // Marker server-list entry + click-to-join, driven from the per-tick hook on
    // ModLauncher loaders (Forge/NeoForge), where the late-loaded
    // MultiplayerScreenMixin is not woven (ADR-005). The entry is added
    // reflectively, edit/delete are disabled while it is selected, and when the
    // selected entry is the marker and the screen next becomes a ConnectScreen
    // the (hung) connect is aborted and re-routed to the Tailcarft join flow.
    // Inert on Fabric/Quilt, where the Mixin intercepts the click before the
    // connect is attempted.
    private static void ensureMarkerEntry(Minecraft client) {
        Screen cur = client.screen;
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
            client.setScreen(new JoinRemoteScreen(parent, config.invite()));
        } catch (Throwable t) {
            LOG.error("Failed to re-route Tailcarft marker join", t);
        }
    }

    // Server-config smoke hook: when TMC_SERVER_SMOKE is set, the client walks
    // the game's multiplayer navigation (title "Multiplayer", then the safety
    // screen's "Proceed") to the multiplayer screen, asserts the marker server
    // entry is present, selects it, invokes the vanilla join, and asserts the
    // join is re-routed to JoinRemoteScreen (the Tailcarft flow). Prints a
    // verdict line (TMC_SERVER_SMOKE entry=... join=...) and stays alive for
    // the host to capture. Inert in normal play. See ADR-005.
    private static int serverSmokePhase;
    private static boolean serverSmokeEntryPresent;

    private static void serverSmokeTick(Minecraft client) {
        Screen cur = client.screen;
        // Dismiss the first-launch accessibility onboarding screen, which would
        // otherwise block the title screen and stall the smoke test. On a
        // ModLauncher loader (Forge/NeoForge) the onboarding mixin is not woven
        // (ADR-005); on Fabric/Quilt the mixin dismisses it, so this is a no-op
        // there.
        if (cur instanceof AccessibilityOnboardingScreen) {
            client.setScreen(new TitleScreen());
            return;
        }
        switch (serverSmokePhase) {
            case 0: {
                Button multiplayer = cur == null ? null : findButton(cur, "menu.multiplayer");
                if (multiplayer != null) {
                    multiplayer.onPress();
                    serverSmokePhase = 1;
                }
                return;
            }
            case 1: {
                Button proceed = cur == null ? null : findButtonByLabel(cur, "Proceed");
                if (proceed != null) {
                    proceed.onPress();
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
                            mps.setSelected(marker);
                        } catch (Throwable ignored) {
                        }
                    }
                    serverSmokePhase = 3;
                }
                return;
            }
            case 3: {
                if (cur instanceof JoinMultiplayerScreen mps) {
                    mps.joinSelectedServer();
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
}
