package it.pino.zelchat.api.module.annotation;

import it.pino.zelchat.api.module.priority.ModulePriority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChatModuleSettings {
    String pluginOwner();
    ModulePriority priority();
}
