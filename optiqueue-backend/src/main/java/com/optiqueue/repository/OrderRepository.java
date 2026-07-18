package com.optiqueue.repository;

import com.optiqueue.entity.Order;
import com.optiqueue.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Page<Order> findByUserUsername(String username, Pageable pageable);

    /** Loads order + items + products + user in one query (avoids N+1 on detail view). */
    @Query("select distinct o from Order o " +
           "join fetch o.user " +
           "left join fetch o.items i " +
           "left join fetch i.product " +
           "where o.id = :id")
    Optional<Order> findDetailById(Long id);
}
