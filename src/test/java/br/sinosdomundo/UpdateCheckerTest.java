package br.sinosdomundo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    @Test
    void newerPatchIsNewer() {
        assertTrue(UpdateChecker.compare("1.0.1", "1.0.0") > 0);
    }

    @Test
    void comparesNumbersNotText() {
        assertTrue(UpdateChecker.compare("1.10.0", "1.9.3") > 0);
    }

    @Test
    void missingPartsCountAsZero() {
        assertEquals(0, UpdateChecker.compare("1.0", "1.0.0"));
    }

    @Test
    void suffixDoesNotBreak() {
        assertTrue(UpdateChecker.compare("1.2.0", "1.1.0-SNAPSHOT") > 0);
    }
}
