package com.example.order.controller;

import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.OrderStatus;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class OrderModelAssembler
        implements RepresentationModelAssembler<Order, EntityModel<OrderResponse>> {

    @Override
    @NonNull
    public EntityModel<OrderResponse> toModel(@NonNull Order order) {
        EntityModel<OrderResponse> model = EntityModel.of(
            OrderResponse.from(order),
            // Self link — always present
            linkTo(methodOn(OrderController.class).getById(order.getId())).withSelfRel(),
            // Collection link
            linkTo(methodOn(OrderController.class).list(null, null)).withRel("orders"));

        // Conditional action links based on current state
        OrderStatus status = order.getStatus();

        if (status == OrderStatus.PENDING) {
            model.add(linkTo(methodOn(OrderController.class)
                .confirm(order.getId())).withRel("confirm"));
            model.add(linkTo(methodOn(OrderController.class)
                .cancel(order.getId())).withRel("cancel"));
        }

        if (status == OrderStatus.CONFIRMED) {
            model.add(linkTo(methodOn(OrderController.class)
                .ship(order.getId())).withRel("ship"));
        }

        if (status == OrderStatus.SHIPPED) {
            model.add(linkTo(methodOn(OrderController.class)
                .deliver(order.getId())).withRel("deliver"));
        }

        return model;
    }
}
