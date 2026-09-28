package net.mysterria.stuff.features.dungeons;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PartyTrackerTest {

    private static final UUID LEADER = UUID.fromString("0b4e7c1a-3f5d-4a8e-9c2b-1d6f0e7a8b9c");

    @Test
    void deriveIsDeterministicNameUuidOfLeaderAndCreationMillis() {
        UUID expected = UUID.nameUUIDFromBytes(
                ("mythicdungeons-party:" + LEADER + ":1700000000000").getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, PartyTracker.derive(LEADER, 1_700_000_000_000L));
        assertEquals(PartyTracker.derive(LEADER, 42L), PartyTracker.derive(LEADER, 42L));
        assertEquals(3, PartyTracker.derive(LEADER, 42L).version());
    }

    @Test
    void deriveDiffersByLeaderAndByCreationTime() {
        UUID other = UUID.fromString("7f000000-0000-4000-8000-000000000001");
        assertNotEquals(PartyTracker.derive(LEADER, 42L), PartyTracker.derive(LEADER, 43L));
        assertNotEquals(PartyTracker.derive(LEADER, 42L), PartyTracker.derive(other, 42L));
    }

    @Test
    void sameObjectKeepsItsFirstIdAndEqualObjectsDoNot() {
        PartyTracker tracker = new PartyTracker(8);
        Object party = new EqualEverything();
        Object lookalike = new EqualEverything();
        UUID first = PartyTracker.derive(LEADER, 1L);

        PartyTracker.PartyState state = tracker.track(party, first, PartyTracker.SOURCE_CREATED);
        assertSame(state, tracker.track(party, PartyTracker.derive(LEADER, 2L), PartyTracker.SOURCE_FIRST_SEEN));
        assertEquals(first, tracker.lookup(party).id());
        assertEquals(PartyTracker.SOURCE_CREATED, tracker.lookup(party).source());
        assertNull(tracker.lookup(lookalike), "tracking is by identity, not equals()");
    }

    @Test
    void trackerIsBoundedAndEvictsOldest() {
        PartyTracker tracker = new PartyTracker(2);
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();
        tracker.track(a, PartyTracker.derive(LEADER, 1L), PartyTracker.SOURCE_CREATED);
        tracker.track(b, PartyTracker.derive(LEADER, 2L), PartyTracker.SOURCE_CREATED);
        tracker.track(c, PartyTracker.derive(LEADER, 3L), PartyTracker.SOURCE_CREATED);
        assertEquals(2, tracker.size());
        assertNull(tracker.lookup(a));
        assertNotNull(tracker.lookup(b));
        assertNotNull(tracker.lookup(c));
    }

    @Test
    void memberCountStartsUnknown() {
        PartyTracker tracker = new PartyTracker(4);
        PartyTracker.PartyState state = tracker.track(new Object(), PartyTracker.derive(LEADER, 1L),
                PartyTracker.SOURCE_ANONYMOUS);
        assertEquals(-1, state.memberCount());
        state.memberCount(3);
        assertEquals(3, state.memberCount());
    }

    @Test
    void nullPartyIsNeverTracked() {
        PartyTracker tracker = new PartyTracker(4);
        assertNull(tracker.track(null, PartyTracker.derive(LEADER, 1L), PartyTracker.SOURCE_CREATED));
        assertNull(tracker.lookup(null));
    }

    private static final class EqualEverything {
        @Override
        public boolean equals(Object other) {
            return other instanceof EqualEverything;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
