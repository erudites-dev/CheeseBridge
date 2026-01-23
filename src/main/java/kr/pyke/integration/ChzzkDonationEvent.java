package kr.pyke.integration;

public record ChzzkDonationEvent(String donor, String donationAmount, String donationMessage) {
    public int getAmount() {
        try { return Integer.parseInt(donationAmount); }
        catch (Exception e) { return 0; }
    }
}
