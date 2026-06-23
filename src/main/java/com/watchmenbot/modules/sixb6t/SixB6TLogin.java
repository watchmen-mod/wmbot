package com.watchmenbot.modules.sixb6t;

import com.watchmenbot.WMBot;
import com.watchmenbot.util.TickTimer;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.Locale;

public class SixB6TLogin extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> password = sgGeneral.add(new StringSetting.Builder()
        .name("password")
        .description("Password to send with /login after joining 6b6t.")
        .defaultValue("")
        .placeholder("password")
        .build()
    );

    private final Setting<Boolean> waitForPrompt = sgGeneral.add(new BoolSetting.Builder()
        .name("wait-for-prompt")
        .description("Waits until chat asks for /login before sending the command.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait before sending /login.")
        .defaultValue(40)
        .range(0, 200)
        .sliderRange(0, 100)
        .build()
    );

    public SixB6TLogin() {
        super(WMBot.CATEGORY, "6b6t-login", "Automatically sends /login with your configured password after joining 6b6t.");
    }

    @Override
    public void onActivate() {
        SixB6TSession.resetLogin();
    }

    @Override
    public void onDeactivate() {
        SixB6TSession.loginTicksLeft(SixB6TSession.INACTIVE_TICKS);
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        SixB6TSession.loginTicksLeft(SixB6TSession.INACTIVE_TICKS);

        if (!waitForPrompt.get()) scheduleLogin();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!waitForPrompt.get()) return;

        String message = event.getMessage().getString().toLowerCase(Locale.ROOT);
        if (isLoginPrompt(message)) {
            SixB6TSession.resetAll();
            scheduleLogin();
        }
        else if (isSuccessfulLoginMessage(message)) {
            SixB6TPortals.requestAutoStart(20);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (SixB6TSession.loginTicksLeft() < 0) return;

        if (TickTimer.tickDelay(SixB6TSession::loginTicksLeft, SixB6TSession::loginTicksLeft)) return;

        SixB6TSession.loginTicksLeft(SixB6TSession.INACTIVE_TICKS);
        sendLogin();
    }

    private void scheduleLogin() {
        if (SixB6TSession.loginSent()) return;

        if (password.get().isBlank()) {
            warning("Set a password before using 6b6t Login.");
            SixB6TSession.loginTicksLeft(SixB6TSession.INACTIVE_TICKS);
            return;
        }

        int nextDelay = delay.get();
        int currentDelay = SixB6TSession.loginTicksLeft();
        SixB6TSession.loginTicksLeft(currentDelay < 0 ? nextDelay : Math.min(currentDelay, nextDelay));
    }

    private void sendLogin() {
        if (mc.player == null || mc.player.networkHandler == null) {
            SixB6TSession.loginTicksLeft(1);
            return;
        }

        SixB6TSession.loginSent(true);
        ChatUtils.sendPlayerMsg("/login " + password.get().trim(), true);
        info("Sent login command.");

        SixB6TPortals.requestAutoStart(100);
    }

    private boolean isLoginPrompt(String message) {
        return message.contains("please login")
            && message.contains("/login")
            && message.contains("<password>");
    }

    private boolean isSuccessfulLoginMessage(String message) {
        return message.contains("successfully logged")
            || message.contains("successful login")
            || message.contains("logged in")
            || message.contains("login successful");
    }
}
