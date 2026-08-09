package it.pino.zelchat.api;

import it.pino.zelchat.api.module.ModuleManager;

public interface ZelChatAPI {
    static ZelChatAPI get() {
        throw new UnsupportedOperationException("compile-only ZelChat API contract");
    }

    ModuleManager getModuleManager();
}
