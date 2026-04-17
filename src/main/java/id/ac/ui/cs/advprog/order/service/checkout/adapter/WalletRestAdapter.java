package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class WalletRestAdapter implements WalletGateway {

    private final RestTemplate restTemplate;

    @Value("${order.wallet.url}")
    private String walletUrl;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Override
    public void debit(String userId, double amount) {
        String debitUrl = UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment(userId, "debit")
                .queryParam("amount", amount)
                .toUriString();

        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.put(debitUrl, null);
                return;
            } catch (HttpClientErrorException e) {
                throw new IllegalArgumentException("Saldo Wallet tidak mencukupi atau User tidak ditemukan!", e);
            } catch (ResourceAccessException e) {
                lastTransientError = e;
            }
        }
        throw new IllegalStateException("Gagal mengakses Wallet service saat debit.", lastTransientError);
    }

    @Override
    public void refund(String userId, double amount) {
        String refundUrl = UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment(userId, "credit")
                .queryParam("amount", amount)
                .toUriString();

        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.put(refundUrl, null);
                return;
            } catch (HttpClientErrorException e) {
                throw new IllegalStateException("Gagal melakukan refund ke wallet.", e);
            } catch (ResourceAccessException e) {
                lastTransientError = e;
            }
        }
        throw new IllegalStateException("Gagal mengakses Wallet service saat refund.", lastTransientError);
    }
}
