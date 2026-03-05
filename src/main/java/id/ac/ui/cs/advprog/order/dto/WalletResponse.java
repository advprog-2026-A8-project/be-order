package id.ac.ui.cs.advprog.order.dto;
import lombok.Data;
import java.util.UUID;

@Data
public class WalletResponse {
    private UUID id;
    private UUID userId;
    private Double balance;
}