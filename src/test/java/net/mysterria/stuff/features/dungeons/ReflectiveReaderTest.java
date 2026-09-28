package net.mysterria.stuff.features.dungeons;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReflectiveReaderTest {

    private static final UUID INSTANCE = UUID.fromString("5d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    /** Stands in for a MythicDungeons event: the getter lives on an abstract parent. */
    public abstract static class FakeDungeonEvent {
        private final Object instance;

        protected FakeDungeonEvent(Object instance) {
            this.instance = instance;
        }

        public Object getInstance() {
            return instance;
        }
    }

    public static final class FakeLootEvent extends FakeDungeonEvent {
        private final String table;

        public FakeLootEvent(Object instance, String table) {
            super(instance);
            this.table = table;
        }

        public String getLootTableNamespace() {
            return table;
        }

        public boolean isCancelled() {
            return false;
        }

        public Object getBroken() {
            throw new IllegalStateException("boom");
        }
    }

    public static final class FakeInstance {
        public UUID getUuid() {
            return INSTANCE;
        }
    }

    private final ReflectiveReader reader = new ReflectiveReader();

    @Test
    void readsInheritedAndDeclaredGetters() throws ReflectiveOperationException {
        FakeInstance instance = new FakeInstance();
        FakeLootEvent event = new FakeLootEvent(instance, "boss_chest");

        assertSame(instance, reader.read(event, "getInstance"));
        assertEquals("boss_chest", reader.read(event, "getLootTableNamespace", String.class));
        assertEquals(INSTANCE, reader.read(reader.read(event, "getInstance"), "getUuid", UUID.class));
        assertEquals(Boolean.FALSE, reader.read(event, "isCancelled", Boolean.class));
    }

    @Test
    void missingRequiredGetterIsAReflectiveFailure() {
        FakeLootEvent event = new FakeLootEvent(null, "t");
        assertThrows(NoSuchMethodException.class, () -> reader.read(event, "getParty"));
    }

    @Test
    void missingOptionalGetterIsNull() throws ReflectiveOperationException {
        FakeLootEvent event = new FakeLootEvent(null, "t");
        assertNull(reader.readOptional(event, "getWhoKicked"));
        assertNull(reader.readOptional(event, "getWhoKicked", UUID.class));
    }

    @Test
    void wrongTypeAndNullTargetYieldNull() throws ReflectiveOperationException {
        FakeLootEvent event = new FakeLootEvent(null, "t");
        assertNull(reader.read(event, "getLootTableNamespace", UUID.class));
        assertNull(reader.read(null, "getAnything"));
        assertNull(reader.readOptional(null, "getAnything"));
        assertNull(reader.read(event, "getInstance"));
    }

    @Test
    void throwingGetterSurfacesAsReflectiveFailure() {
        FakeLootEvent event = new FakeLootEvent(null, "t");
        assertThrows(InvocationTargetException.class, () -> reader.read(event, "getBroken"));
    }

    @Test
    void lookupsAreCachedOncePerClassAndGetterIncludingMisses() throws ReflectiveOperationException {
        FakeLootEvent first = new FakeLootEvent(null, "a");
        FakeLootEvent second = new FakeLootEvent(null, "b");
        reader.read(first, "getLootTableNamespace");
        reader.read(second, "getLootTableNamespace");
        reader.readOptional(first, "getMissing");
        reader.readOptional(second, "getMissing");
        assertEquals(2, reader.cachedLookups());
        assertEquals("b", reader.read(second, "getLootTableNamespace"));
    }
}
