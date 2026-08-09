package net.mysterria.stuff.features.chat;

public interface ChatAliasIntegration extends AutoCloseable {

    void reload();

    @Override
    void close();
}
