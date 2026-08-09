package it.pino.zelchat.api.module;

import org.bukkit.plugin.Plugin;

public interface ModuleManager {
    void register(Plugin plugin, ChatModule module);
    void unregister(Plugin plugin, ChatModule module);
}
