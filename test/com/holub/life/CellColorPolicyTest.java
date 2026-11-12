package com.holub.life;

import java.awt.Color;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellColorPolicyTest {
    // The colors that the game cycles through.
    private static final Color RED = Color.RED;
    private static final Color BLUE = new Color(0x0072B2);
    private static final Color GREEN = new Color(0x009E73);
    private static final Map<String, Color> COLORS = Map.of("RED", RED, "BLUE", BLUE, "GREEN", GREEN);

    @ParameterizedTest(name = "age {0}")
    @ValueSource(ints = {0, 9, 10, 30, 1000})
    void fixedPolicyReturnsTheSameColorForEveryAge(int age) {
        CellColorPolicy policy = new FixedColorPolicy(RED);

        assertEquals(RED, policy.colorOf(age));
    }

    @ParameterizedTest(name = "age {0}: {1}")
    @CsvSource({
        "0, RED", "9, RED",
        "10, BLUE", "19, BLUE",
        "20, GREEN", "29, GREEN",
        "30, RED", "1000, BLUE"
    })
    void cyclingPolicyStepsEveryTenGenerationsAndWrapsAround(int age, String expected) {
        CellColorPolicy policy = new CyclingColorPolicy(10, RED, BLUE, GREEN);

        assertEquals(COLORS.get(expected), policy.colorOf(age));
    }

    @Test
    void onlyTheCyclingPolicyChangesWithAge() {
        assertFalse(new FixedColorPolicy(RED).changesWithAge());
        assertTrue(new CyclingColorPolicy(10, RED, BLUE, GREEN).changesWithAge());
    }

    @Test
    void cyclingPolicyKeepsItsOwnCopyOfTheColors() {
        Color[] colors = {RED, BLUE, GREEN};
        CellColorPolicy policy = new CyclingColorPolicy(10, colors);

        colors[1] = Color.BLACK;

        assertEquals(BLUE, policy.colorOf(10), "Changing the caller's array must not change the policy");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPolicies")
    void policiesRejectInvalidArguments(String invalidCase, Executable creation) {
        assertThrows(IllegalArgumentException.class, creation);
    }

    private static Stream<Arguments> invalidPolicies() {
        return Stream.of(
            Arguments.of("fixed color is null", (Executable) () -> new FixedColorPolicy(null)),
            Arguments.of("period is 0", (Executable) () -> new CyclingColorPolicy(0, RED, BLUE)),
            Arguments.of("period is -10", (Executable) () -> new CyclingColorPolicy(-10, RED, BLUE)),
            Arguments.of("no colors", (Executable) () -> new CyclingColorPolicy(10)),
            Arguments.of("only one color", (Executable) () -> new CyclingColorPolicy(10, RED)),
            Arguments.of("colors array is null", (Executable) () -> new CyclingColorPolicy(10, (Color[]) null)),
            Arguments.of("a color is null", (Executable) () -> new CyclingColorPolicy(10, RED, null))
        );
    }
}
