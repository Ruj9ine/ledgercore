package com.ledgercore.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternsTest {

    @Test
    void plainTextIsJustWrapped() {
        assertThat(LikePatterns.contains("alice")).isEqualTo("%alice%");
    }

    @Test
    void wildcardsAndTheEscapeCharacterAreEscaped() {
        // The input is the text:  50%_off\x
        assertThat(LikePatterns.contains("50%_off\\x")).isEqualTo("%50\\%\\_off\\\\x%");
    }
}
