package kr.pyke.integration;

public record DonationEvent(String donor, String donationAmount, String donationMessage, String platform) {
    public int getAmount() {
        try { return Integer.parseInt(donationAmount); }
        catch (Exception e) { return 0; }
    }
}