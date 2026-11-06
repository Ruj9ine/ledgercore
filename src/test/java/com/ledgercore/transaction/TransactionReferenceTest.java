package com.ledgercore.transaction;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionReferenceTest {

    @Test
    void referencesHaveTheExpectedFormat() {
        assertThat(TransactionReference.generate()).matches("TXN-[2-9A-HJKMNP-Z]{10}");
    }

    @Test
    void referencesDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(TransactionReference.generate());
        }
        assertThat(seen).hasSize(10_000);
    }
}