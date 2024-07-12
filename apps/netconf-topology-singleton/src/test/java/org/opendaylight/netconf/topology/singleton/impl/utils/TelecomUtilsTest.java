package org.opendaylight.netconf.topology.singleton.impl.utils;

import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.topology.singleton.messages.utils.TelecomUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;


class TelecomUtilsTest {

    private final TelecomUtils telecomUtils = new TelecomUtils();

    @Test
    void testCalculateCallCharges() {
        assertEquals(1.0, telecomUtils.calculateCallCharges(10));
        assertEquals(0.0, telecomUtils.calculateCallCharges(0));
        assertEquals(0.5, telecomUtils.calculateCallCharges(5));
    }

    @Test
    void testCalculateSmsCharges() {
        assertEquals(0.5, telecomUtils.calculateSmsCharges(10));
        assertEquals(0.0, telecomUtils.calculateSmsCharges(0));
        assertEquals(0.25, telecomUtils.calculateSmsCharges(5));
    }

    @Test
    void testCalculateDataCharges() {
        assertEquals(0.2, telecomUtils.calculateDataCharges(10));
        assertEquals(0.0, telecomUtils.calculateDataCharges(0));
        assertEquals(0.1, telecomUtils.calculateDataCharges(5));
    }

    @Test
    void testSendSms() {
        assertEquals("SMS sent to 1234567890: Hello", telecomUtils.sendSms("1234567890", "Hello"));

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            telecomUtils.sendSms("", "Hello");
        });
        assertEquals("Phone number or message cannot be empty", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class, () -> {
            telecomUtils.sendSms("1234567890", "");
        });
        assertEquals("Phone number or message cannot be empty", exception.getMessage());
    }

    @Test
    void testIsDataUsageExceedingLimit() {
        assertTrue(telecomUtils.isDataUsageExceedingLimit(15, 10));
        assertFalse(telecomUtils.isDataUsageExceedingLimit(5, 10));
        assertFalse(telecomUtils.isDataUsageExceedingLimit(10, 10));
    }

    @Test
    void testCalculateTotalCharges() {
        assertEquals(1.7, telecomUtils.calculateTotalCharges(10, 10, 10));
        assertEquals(0.0, telecomUtils.calculateTotalCharges(0, 0, 0));
        assertEquals(0.85, telecomUtils.calculateTotalCharges(5, 5, 5));
    }

    @Test
    void testCalculateRemainingData() {
        assertEquals(5, telecomUtils.calculateRemainingData(5, 10));

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            telecomUtils.calculateRemainingData(15, 10);
        });
        assertEquals("Data used cannot exceed data limit", exception.getMessage());
    }

    @Test
    void testCalculateRoamingCharges() {
        assertEquals(2.4, telecomUtils.calculateRoamingCharges(10, 10));
        assertEquals(0.0, telecomUtils.calculateRoamingCharges(0, 0));
        assertEquals(1.2, telecomUtils.calculateRoamingCharges(5, 5));
    }

    @Test
    void testIsSmsLimitExceeded() {
        assertTrue(telecomUtils.isSmsLimitExceeded(15, 10));
        assertFalse(telecomUtils.isSmsLimitExceeded(5, 10));
        assertFalse(telecomUtils.isSmsLimitExceeded(10, 10));
    }

    // Test cases for isValidPhoneNumber method

    /**
     * Test that a valid 10-digit phone number returns true.
     */
    @Test
    void testIsValidPhoneNumber_Valid() {
        assertTrue(telecomUtils.isValidPhoneNumber("1234567890"));
    }

    /**
     * Test that a null phone number returns false.
     */
    @Test
    void testIsValidPhoneNumber_Null() {
        assertFalse(telecomUtils.isValidPhoneNumber(null));
    }

    /**
     * Test that a phone number with less than 10 digits returns false.
     */
    @Test
    void testIsValidPhoneNumber_LessThan10Digits() {
        assertFalse(telecomUtils.isValidPhoneNumber("12345"));
    }

    /**
     * Test that a phone number with more than 10 digits returns false.
     */
    @Test
    void testIsValidPhoneNumber_MoreThan10Digits() {
        assertFalse(telecomUtils.isValidPhoneNumber("123456789012"));
    }

    /**
     * Test that a phone number with non-digit characters returns false.
     */
    @Test
    void testIsValidPhoneNumber_NonDigitCharacters() {
        assertFalse(telecomUtils.isValidPhoneNumber("12345abcde"));
    }
}