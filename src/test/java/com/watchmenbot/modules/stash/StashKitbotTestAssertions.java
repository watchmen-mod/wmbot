package com.watchmenbot.modules.stash;

final class StashKitbotTestAssertions {
    private StashKitbotTestAssertions() {
    }

    static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError("%s expected <%s> but got <%s>".formatted(label, expected, actual));
        }
    }

    static void assertTrue(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    static void assertFalse(boolean value, String label) {
        if (value) throw new AssertionError(label);
    }

    static void assertNull(Object value, String label) {
        if (value != null) throw new AssertionError("%s expected null but got <%s>".formatted(label, value));
    }
}
