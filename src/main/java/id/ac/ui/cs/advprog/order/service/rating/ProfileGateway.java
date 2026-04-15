package id.ac.ui.cs.advprog.order.service.rating;

public interface ProfileGateway {
    void submitRating(
            String orderId,
            String titiperId,
            String jastiperId,
            String productId,
            int jastiperRating,
            int productRating
    );
}
