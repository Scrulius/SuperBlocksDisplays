package com.blockdisplay.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public class SilentCommandSender implements RemoteConsoleCommandSender {

    private static final SilentCommandSender INSTANCE = new SilentCommandSender();

    public static SilentCommandSender getInstance() {
        return INSTANCE;
    }

    private final CommandSender delegate;

    private SilentCommandSender() {
        this.delegate = Bukkit.getConsoleSender();
    }

    // Ignore all messages
    @Override public void sendMessage(@NotNull String message) {}
    @Override public void sendMessage(@NotNull String... messages) {}
    @Override public void sendMessage(@Nullable UUID sender, @NotNull String message) {}
    @Override public void sendMessage(@Nullable UUID sender, @NotNull String... messages) {}
    @Override public void sendPlainMessage(@NotNull String message) {}

    @Override public @NotNull Server getServer() { return delegate.getServer(); }
    @Override public @NotNull String getName() { return delegate.getName(); }
    @Override public @NotNull net.kyori.adventure.text.Component name() { return delegate.name(); }

    @Override public boolean isPermissionSet(@NotNull String name) { return delegate.isPermissionSet(name); }
    @Override public boolean isPermissionSet(@NotNull Permission perm) { return delegate.isPermissionSet(perm); }
    @Override public boolean hasPermission(@NotNull String name) { return delegate.hasPermission(name); }
    @Override public boolean hasPermission(@NotNull Permission perm) { return delegate.hasPermission(perm); }
    @Override public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) { return delegate.addAttachment(plugin, name, value); }
    @Override public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) { return delegate.addAttachment(plugin); }
    @Override public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value, int ticks) { return delegate.addAttachment(plugin, name, value, ticks); }
    @Override public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) { return delegate.addAttachment(plugin, ticks); }
    @Override public void removeAttachment(@NotNull PermissionAttachment attachment) { delegate.removeAttachment(attachment); }
    @Override public void recalculatePermissions() { delegate.recalculatePermissions(); }
    @Override public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() { return delegate.getEffectivePermissions(); }
    @Override public boolean isOp() { return delegate.isOp(); }
    @Override public void setOp(boolean value) { delegate.setOp(value); }

    // RemoteConsoleCommandSender specific
    @Override
    public java.net.SocketAddress getAddress() {
        return new java.net.InetSocketAddress("127.0.0.1", 0);
    }

    @Override
    public CommandSender.Spigot spigot() {
        return delegate.spigot();
    }
}
