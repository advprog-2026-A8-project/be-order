package id.ac.ui.cs.advprog.order.service.rating;

public interface ProfileGateway {
    default void submitRating(
            String orderId,
            String titiperId,
            String jastiperId,
            String productId,
            int jastiperRating,
            int productRating
    ) {
        submitRating(orderId, titiperId, jastiperId, productId, jastiperRating, productRating, null);
    }

    void submitRating(
            String orderId,
            String titiperId,
            String jastiperId,
            String productId,
            int jastiperRating,
            int productRating,
            String authorizationHeader
    );
}
