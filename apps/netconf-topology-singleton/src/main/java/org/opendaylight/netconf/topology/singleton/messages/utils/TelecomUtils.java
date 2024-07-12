package org.opendaylight.netconf.topology.singleton.messages.utils;


public class TelecomUtils {

    private static final double CALL_RATE_PER_MINUTE = 0.10; // $0.10 per minute
    private static final double SMS_RATE = 0.05; // $0.05 per SMS
    private static final double DATA_RATE_PER_MB = 0.02; // $0.02 per MB

    public double calculateCallCharges(int minutes) {
        return minutes * CALL_RATE_PER_MINUTE;
    }

    public double calculateSmsCharges(int numberOfSms) {
        return numberOfSms * SMS_RATE;
    }

    public double calculateDataCharges(int dataInMB) {
        return dataInMB * DATA_RATE_PER_MB;
    }

    public String sendSms(String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.isEmpty() || message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Phone number or message cannot be empty");
        }
        // Simulate sending SMS
        return "SMS sent to " + phoneNumber + ": " + message;
    }

    public boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("\\d{10}");
    }

    public boolean isDataUsageExceedingLimit(int dataInMB, int limitInMB) {
        return dataInMB > limitInMB;
    }

    public double calculateTotalCharges(int minutes, int numberOfSms, int dataInMB) {
        return calculateCallCharges(minutes) + calculateSmsCharges(numberOfSms) + calculateDataCharges(dataInMB);
    }

    public int calculateRemainingData(int dataUsed, int dataLimit) {
        if (dataUsed > dataLimit) {
            throw new IllegalArgumentException("Data used cannot exceed data limit");
        }
        return dataLimit - dataUsed;
    }

    public double calculateRoamingCharges(int minutes, int dataInMB) {
        double roamingCallRate = CALL_RATE_PER_MINUTE * 2; // Double the rate for roaming
        double roamingDataRate = DATA_RATE_PER_MB * 2; // Double the rate for roaming
        return (minutes * roamingCallRate) + (dataInMB * roamingDataRate);
    }

    public boolean isSmsLimitExceeded(int numberOfSms, int smsLimit) {
        return numberOfSms > smsLimit;
    }
}
