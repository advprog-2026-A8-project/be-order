package id.ac.ui.cs.advprog.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingRequest {
    @NotBlank(message = "userId wajib diisi")
    private String userId;
    @NotNull(message = "jastiperRating wajib diisi")
    @Min(value = 1, message = "jastiperRating minimal 1")
    @Max(value = 5, message = "jastiperRating maksimal 5")
    private Integer jastiperRating;
    @NotNull(message = "productRating wajib diisi")
    @Min(value = 1, message = "productRating minimal 1")
    @Max(value = 5, message = "productRating maksimal 5")
    private Integer productRating;
}
