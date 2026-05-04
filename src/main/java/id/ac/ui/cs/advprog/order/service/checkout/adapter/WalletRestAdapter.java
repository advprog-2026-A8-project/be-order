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
    private static final String DESCRIPTION_PAYMENT = "Order payment";
    private static final String DESCRIPTION_REFUND = "Order refund";
    private static final String INTERNAL_AUTHORIZATION = "internal-order-service";

    private final RestTemplate restTemplate;

    @Value("${order.wallet.url}")
    private String walletUrl;

    @Override
    public void debit(String userId, double amount) {
        String debitUrl = UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment("pay")
                .toUriString();

        try {
            restTemplate.postForEntity(
                    debitUrl,
                    buildWalletRequest(userId, amount, DESCRIPTION_PAYMENT, true),
                    Void.class
            );
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("Saldo Wallet tidak mencukupi atau User tidak ditemukan!", e);
        }
    }

    @Override
    public void refund(String userId, double amount) {
        String refundUrl = UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment("refund")
                .toUriString();

        try {
            restTemplate.postForEntity(
                    refundUrl,
                    buildWalletRequest(userId, amount, DESCRIPTION_REFUND, false),
                    Void.class
            );
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Gagal melakukan refund ke wallet.", e);
        }
    }

    private HttpEntity<Map<String, Object>> buildWalletRequest(
            String userId,
            double amount,
            String description,
            boolean withAuthorization
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (withAuthorization) {
            headers.set("Authorization", INTERNAL_AUTHORIZATION);
        }

        Map<String, Object> payload = Map.of(
                "userId", userId,
                "amount", amount,
                "description", description
        );

        return new HttpEntity<>(payload, headers);
    }
}
