package com.watchmenbot.modules.stash;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Deque;

final class StashKitbotMessenger {
    private final MinecraftClient mc;
    private final Deque<PendingWhisper> whisperQueue = new ArrayDeque<>();

    private PendingCommand pendingCommand = PendingCommand.NONE;
    private int pendingCommandTicks;
    private int pendingCommandSequence;
    private PendingWhisper pendingWhisper;
    private int pendingWhisperTicks;
    private int pendingWhisperSequence;
    private int whisperCooldownTicks;
    private int sendSequence;

    StashKitbotMessenger(MinecraftClient mc) {
        this.mc = mc;
    }

    void clearCommand() {
        pendingCommand = PendingCommand.NONE;
        pendingCommandTicks = 0;
        pendingCommandSequence = 0;
    }

    PendingCommand pendingCommand() {
        return pendingCommand;
    }

    boolean hasPendingCommandWindow() {
        return pendingCommand != PendingCommand.NONE && pendingCommandTicks > 0;
    }

    PendingWhisper pendingWhisper() {
        return pendingWhisper;
    }

    boolean hasPendingWhisperWindow() {
        return pendingWhisper != null && pendingWhisperTicks > 0;
    }

    boolean pendingCommandSentAfterWhisper() {
        return pendingCommandSequence > pendingWhisperSequence;
    }

    void applyWhisperCooldown(int cooldownTicks) {
        whisperCooldownTicks = cooldownTicks;
        whisperQueue.addFirst(pendingWhisper);
        pendingWhisper = null;
        pendingWhisperTicks = 0;
        pendingWhisperSequence = 0;
    }

    void clearPendingCommand() {
        pendingCommand = PendingCommand.NONE;
        pendingCommandTicks = 0;
        pendingCommandSequence = 0;
    }

    void tickPendingCommandWindow() {
        if (pendingCommandTicks > 0) {
            pendingCommandTicks--;
            if (pendingCommandTicks == 0) {
                pendingCommand = PendingCommand.NONE;
                pendingCommandSequence = 0;
            }
        }
    }

    void tickWhisperQueue(String replyCommand) {
        if (mc.player == null) return;

        if (pendingWhisperTicks > 0) {
            pendingWhisperTicks--;
            if (pendingWhisperTicks == 0) {
                pendingWhisper = null;
                pendingWhisperSequence = 0;
            }
        }

        if (whisperCooldownTicks > 0) {
            whisperCooldownTicks--;
            return;
        }

        if (pendingWhisper != null || whisperQueue.isEmpty()) return;

        PendingWhisper next = whisperQueue.removeFirst();
        String command = replyCommand.trim();
        if (command.isEmpty()) command = "/w";

        ChatUtils.sendPlayerMsg(command + " " + next.player() + " " + next.message(), true);
        pendingWhisper = next;
        pendingWhisperTicks = 100;
        pendingWhisperSequence = ++sendSequence;
    }

    void reply(String player, String message, String replyCommand) {
        whisperQueue.addLast(new PendingWhisper(player, message));
        tickWhisperQueue(replyCommand);
    }

    void queueReply(String player, String message) {
        whisperQueue.addLast(new PendingWhisper(player, message));
    }

    boolean sendHomeCommand(String homeCommand) {
        String command = homeCommand.trim();
        if (command.isEmpty()) command = "/home stash";
        ChatUtils.sendPlayerMsg(command, true);
        pendingCommand = PendingCommand.HOME;
        pendingCommandTicks = 100;
        pendingCommandSequence = ++sendSequence;
        return true;
    }

    boolean sendCommand(String command, String argument, PendingCommand pending) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return false;

        ChatUtils.sendPlayerMsg(trimmed + " " + argument, true);
        pendingCommand = pending;
        pendingCommandTicks = 100;
        pendingCommandSequence = ++sendSequence;
        return true;
    }
}

enum PendingCommand {
    NONE,
    TPA,
    HOME
}

record PendingWhisper(String player, String message) {
}
