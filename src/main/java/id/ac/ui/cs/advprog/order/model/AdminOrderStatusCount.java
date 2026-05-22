package id.ac.ui.cs.advprog.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "admin_order_status_count")
public class AdminOrderStatusCount {
    @Id
    @Column(nullable = false, length = 50)
    private String status;

    @Column(nullable = false)
    private Long total;
}
