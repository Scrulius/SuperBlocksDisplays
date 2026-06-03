package com.blockdisplay.plugin;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * A wrapper around ConsoleCommandSender that silently discards all sendMessage() output.
 * Used to prevent "Modified entity data of Block Display" spam when dispatching commands.
 */
public class SilentCommandSender implements ConsoleCommandSender {

    private final ConsoleCommandSender delegate;

    public SilentCommandSender() {
        this.delegate = Bukkit.getConsoleSender();
    }

    // ========== Suppress all message output ==========

    @Override
    public void sendMessage(@NotNull String message) {
        // Silenced
    }

    @Override
    public void sendMessage(@NotNull String... messages) {
        // Silenced
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String message) {
        // Silenced
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String... messages) {
        // Silenced
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        // Silenced
    }

    // ========== Delegate everything else ==========

    @Override
    public @NotNull Component name() {
        return delegate.name();
    }

    @Override
    public @NotNull Server getServer() {
        return delegate.getServer();
    }

    @Override
    public @NotNull String getName() {
        return delegate.getName();
    }

    @Override
    public @NotNull Spigot spigot() {
        return delegate.spigot();
    }

    @Override
    public boolean isConversing() {
        return delegate.isConversing();
    }

    @Override
    public void acceptConversationInput(@NotNull String input) {
        delegate.acceptConversationInput(input);
    }

    @Override
    public boolean beginConversation(@NotNull Conversation conversation) {
        return delegate.beginConversation(conversation);
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation) {
        delegate.abandonConversation(conversation);
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation, @NotNull ConversationAbandonedEvent details) {
        delegate.abandonConversation(conversation, details);
    }

    @Override
    public void sendRawMessage(@NotNull String message) {
        // Silenced
    }

    @Override
    public void sendRawMessage(@Nullable UUID sender, @NotNull String message) {
        // Silenced
    }

    @Override
    public boolean isPermissionSet(@NotNull String name) {
        return delegate.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission perm) {
        return delegate.isPermissionSet(perm);
    }

    @Override
    public boolean hasPermission(@NotNull String name) {
        return delegate.hasPermission(name);
    }

    @Override
    public boolean hasPermission(@NotNull Permission perm) {
        return delegate.hasPermission(perm);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
        return delegate.addAttachment(plugin, name, value);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        return delegate.addAttachment(plugin);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value, int ticks) {
        return delegate.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) {
        return delegate.addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment attachment) {
        delegate.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        delegate.recalculatePermissions();
    }

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return delegate.getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return delegate.isOp();
    }

    @Override
    public void setOp(boolean value) {
        delegate.setOp(value);
    }
}
