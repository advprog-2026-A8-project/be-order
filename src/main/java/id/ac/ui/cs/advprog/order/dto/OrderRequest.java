package id.ac.ui.cs.advprog.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    @NotBlank(message = "productId wajib diisi")
    private String productId;
    @NotBlank(message = "userId wajib diisi")
    private String userId;
    @NotBlank(message = "jastiperId wajib diisi")
    private String jastiperId;
    @NotNull(message = "jumlah wajib diisi")
    @Min(value = 1, message = "jumlah minimal 1")
    private Integer jumlah;
    @NotBlank(message = "alamatPengiriman wajib diisi")
    private String alamatPengiriman;
    private String voucherCode;
}
