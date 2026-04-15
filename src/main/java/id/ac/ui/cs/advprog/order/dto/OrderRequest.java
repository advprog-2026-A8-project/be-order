package id.ac.ui.cs.advprog.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private String productId;
    private String userId;
    private String jastiperId;
    private Integer jumlah;
    private String alamatPengiriman;
}
