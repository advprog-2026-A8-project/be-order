package id.ac.ui.cs.advprog.order.model;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    @Test
    void testOrderEntity() {
        Order order = new Order();
        order.setId("123");
        order.setProductId("prod-1");
        order.setUserId("user-1");
        order.setJastiperId("jastiper-1");
        order.setJumlah(5);
        order.setAlamatPengiriman("UI");
        order.setTotalAmount(25000.0);
        order.setJastiperRating(5);
        order.setProductRating(4);
        order.setRatingSubmitted(true);
        order.setStatus(OrderStatus.PENDING);

        assertEquals("123", order.getId());
        assertEquals("prod-1", order.getProductId());
        assertEquals("user-1", order.getUserId());
        assertEquals("jastiper-1", order.getJastiperId());
        assertEquals(5, order.getJumlah());
        assertEquals("UI", order.getAlamatPengiriman());
        assertEquals(25000.0, order.getTotalAmount());
        assertEquals(5, order.getJastiperRating());
        assertEquals(4, order.getProductRating());
        assertTrue(order.getRatingSubmitted());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void markNotNewShouldFlipEntityState() {
        Order order = new Order();
        assertTrue(order.isNew());

        order.markNotNew();

        assertFalse(order.isNew());
    }
}
