package com.polarbookshop.orderservice.order.domain;

import com.polarbookshop.orderservice.book.Book;
import com.polarbookshop.orderservice.book.BookClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class OrderService
{
    private final BookClient bookClient;
    private final OrderRepository orderRepository;

    public OrderService(final BookClient bookClient, final OrderRepository orderRepository)
    {
        this.bookClient = bookClient;
        this.orderRepository = orderRepository;
    }

    public Flux<Order> getAllOrders()
    {
        return orderRepository.findAll();
    }

    public Mono<Order> submitOrder(String isbn, int quantity)
    {
        return bookClient.getBookByIsbn(isbn)
                .map(book -> buildAccepteddOrder(book, quantity))
                .defaultIfEmpty(buildRejectedOrder(isbn, quantity))
                .flatMap(orderRepository::save)
                .timeout(Duration.ofSeconds(3), Mono.empty())
                .onErrorResume(WebClientResponseException.NotFound.class, exception -> Mono.empty())
                .retryWhen(Retry.backoff(3, Duration.ofMillis(300)))
                .onErrorResume(Exception.class, exception -> Mono.empty());
    }

    public static Order buildAccepteddOrder(Book book, int quantity)
    {
        return Order.of(book.isbn(), book.title() + "_" + book.author(), book.price(), quantity, OrderStatus.ACCEPTED);
    }

    public static Order buildRejectedOrder(String isbn, int quantity)
    {
        return Order.of(isbn, null, null, quantity, OrderStatus.REJECTED);
    }
}
