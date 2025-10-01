package org.example;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.Body;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class CountServiceTest {

    @Mock
    private SharedData sharedData;

    @Mock
    private AsyncMap<String, Long> map;

    @Mock
    private RoutingContext context;

    private CountService countService;

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        countService = new CountService(sharedData);
        when(sharedData.<String, Long>getAsyncMap(eq(CountService.MAP_COUNTS_NAME))).thenReturn(Future.succeededFuture(map));
        testContext.completeNow();
    }

    @Test
    void testTransformBody_SingleRequest(VertxTestContext testContext) {
        Buffer body = Buffer.buffer("{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\",\"params\":[],\"id\":83}");
        when(map.putIfAbsent(eq("eth_blockNumber"), eq(1L))).thenReturn(Future.succeededFuture(null));

        countService.createCountingTransformer().transform(Body.body(body));

        testContext.verify(() -> verify(map).putIfAbsent(eq("eth_blockNumber"), eq(1L)));
        testContext.completeNow();
    }

    @Test
    void testTransformBody_BatchRequest(VertxTestContext testContext) {
        Buffer body = Buffer.buffer("[{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBlockByNumber\",\"params\":[],\"id\":1},{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":2}]");
        when(map.putIfAbsent(eq("eth_getBlockByNumber"), eq(1L))).thenReturn(Future.succeededFuture(null));
        when(map.putIfAbsent(eq("eth_chainId"), eq(1L))).thenReturn(Future.succeededFuture(null));

        countService.createCountingTransformer().transform(Body.body(body));

        testContext.verify(() -> {
            verify(map).putIfAbsent(eq("eth_getBlockByNumber"), eq(1L));
            verify(map).putIfAbsent(eq("eth_chainId"), eq(1L));
        });
        testContext.completeNow();
    }

    @Test
    void testStatsHandler(VertxTestContext testContext) {
        Map<String, Long> counts = Map.of("eth_blockNumber", 5L);
        when(map.entries()).thenReturn(Future.succeededFuture(counts));

        countService.statsHandler(context);

        testContext.verify(() -> verify(context).json(eq(counts)));
        testContext.completeNow();
    }
}
