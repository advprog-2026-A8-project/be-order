package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class WalletRestAdapter implements WalletGateway {

    private final RestTemplate restTemplate;

    @Value("${order.wallet.url}")
    private String walletUrl;

    @Override
    public void debit(String userId, double amount) {
        String debitUrl = UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment(userId, "debit")
                .queryParam("amount", amount)
                .toUriString();

        try {
            restTemplate.put(debitUrl, null);
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("Saldo Wallet tidak mencukupi atau User tidak ditemukan!", e);
        }
    }

    @Override
    public void refund(String userId, double amount) {
        String refundUrl = UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment(userId, "credit")
                .queryParam("amount", amount)
                .toUriString();

        try {
            restTemplate.put(refundUrl, null);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Gagal melakukan refund ke wallet.", e);
        }
    }
}
