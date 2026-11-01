package com.korabel.schedule;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateCheckerTest {

    @Test
    public void testCalVerComparison() {
        // CalVer month and patch progression
        assertTrue(UpdateChecker.isNewer("2026.11.1", "2026.11.0"));
        assertTrue(UpdateChecker.isNewer("2026.12.0", "2026.11.5"));
        assertTrue(UpdateChecker.isNewer("2027.1.0", "2026.12.0"));

        // Equality should not be newer
        assertFalse(UpdateChecker.isNewer("2026.11.0", "2026.11.0"));
        assertFalse(UpdateChecker.isNewer("2026.11.0", "2026.11.1"));

        // Backwards-compatibility: CalVer is always newer than older SemVer (1.x.y)
        assertTrue(UpdateChecker.isNewer("2026.11.0", "1.3.0"));
        assertTrue(UpdateChecker.isNewer("2026.11.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.3.0", "2026.11.0"));

        // Optional 'v' / 'V' prefix
        assertTrue(UpdateChecker.isNewer("v2026.11.1", "2026.11.0"));
        assertTrue(UpdateChecker.isNewer("V2026.11.1", "v2026.11.0"));
        assertFalse(UpdateChecker.isNewer("v2026.11.0", "2026.11.0"));

        // Up to 4 components (e.g. sub-patch / hotfix)
        assertTrue(UpdateChecker.isNewer("2026.11.0.1", "2026.11.0"));
        assertFalse(UpdateChecker.isNewer("2026.11.0", "2026.11.0.1"));
        assertTrue(UpdateChecker.isNewer("2026.11.0.2", "2026.11.0.1"));

        // Invalid versions
        assertFalse(UpdateChecker.isNewer("invalid", "2026.11.0"));
        assertFalse(UpdateChecker.isNewer("2026.11.0", "invalid"));
        assertFalse(UpdateChecker.isNewer(null, "2026.11.0"));
        assertFalse(UpdateChecker.isNewer("2026.11.0", null));
    }
}
