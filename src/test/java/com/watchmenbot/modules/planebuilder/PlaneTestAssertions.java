package com.watchmenbot.modules.planebuilder;

import java.util.Objects;

final class PlaneTestAssertions {
    private PlaneTestAssertions() {
    }

    static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("%s: expected <%s> but got <%s>".formatted(message, expected, actual));
        }
    }

    static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
}
