package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WalletRestAdapter implements WalletGateway {

    private final RestTemplate restTemplate;

    @Value("${order.wallet.url}")
    private String walletUrl;

    @Override
    public void debit(String userId, double amount) {
        String debitUrl = UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment("pay")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "internal-order-service");

        Map<String, Object> payload = Map.of(
                "userId", userId,
                "amount", amount,
                "description", "Order payment"
        );

        try {
            restTemplate.postForEntity(debitUrl, new HttpEntity<>(payload, headers), Void.class);
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("Saldo Wallet tidak mencukupi atau User tidak ditemukan!", e);
        }
    }

    @Override
    public void refund(String userId, double amount) {
        String refundUrl = UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment("refund")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
                "userId", userId,
                "amount", amount,
                "description", "Order refund"
        );

        try {
            restTemplate.postForEntity(refundUrl, new HttpEntity<>(payload, headers), Void.class);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Gagal melakukan refund ke wallet.", e);
        }
    }
}
