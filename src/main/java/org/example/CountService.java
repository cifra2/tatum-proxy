package org.example;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.BodyTransformer;
import io.vertx.httpproxy.BodyTransformers;
import io.vertx.httpproxy.MediaType;

import java.util.Collections;
import java.util.List;

/**
 * Service for tracking method calls in {@link SharedData} {@link io.vertx.core.shareddata.AsyncMap}.
 */
class CountService {
    public static final String MAP_COUNTS_NAME = "methodCounts";
    private static final Logger logger = LoggerFactory.getLogger(CountService.class);
    private static final String JSON_METHOD_KEY = "method";
    private static final String JSON_METHOD_DEFAULT_VALUE = "Unspecified";
    private final SharedData sharedData;

    CountService(SharedData sharedData) {
        this.sharedData = sharedData;
    }

    /**
     * Handler producing all actual method counts as JSON,
     *
     * @param context
     */
    public void statsHandler(RoutingContext context) {
        sharedData.getAsyncMap(MAP_COUNTS_NAME)
                .onSuccess(map -> map.entries()
                                          .onSuccess(context::json)
                                          .onFailure(cause -> context.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), cause)))
                .onFailure(cause -> context.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), cause));
    }

    /**
     * Method create body transformer, which parse body (single or batch requests) and will increment counters, based on method key.
     *
     * @return BodyTransformer accepting JSON media
     */
    public BodyTransformer createCountingTransformer() {
        return BodyTransformers.transform(MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, this::transformBody);
    }

    private Buffer transformBody(Buffer buffer) {

        Object value;
        try {
            value = Json.decodeValue(buffer);
        } catch (DecodeException e) {
            logger.warn("Invalid JSON in body of request.", e);
            return buffer;
        }

        getMethodList(value).forEach(this::incrementCounter);

        return buffer;
    }

    private List<String> getMethodList(Object value) {
        if (value instanceof JsonObject jsonObject) {
            return List.of(jsonObject.getString(JSON_METHOD_KEY, JSON_METHOD_DEFAULT_VALUE));
        }

        if (value instanceof JsonArray jsonArray) {
            return jsonArray.stream()
                           .filter(JsonObject.class::isInstance)
                           .map(JsonObject.class::cast)
                           .map(obj -> obj.getString(JSON_METHOD_KEY, JSON_METHOD_DEFAULT_VALUE))
                           .toList();
        }
        return Collections.emptyList();
    }

    /**
     * Method will try to initialize or increment counter for specified {@code counterName}.
     * <p>
     * It will not rise any error in case of failure. Errors are just logged.
     *
     * @param counterName name of counter
     */
    private void incrementCounter(String counterName) {
        sharedData.<String, Long>getAsyncMap(MAP_COUNTS_NAME)
                .onSuccess(map -> map.putIfAbsent(counterName, 1L).onSuccess(existingCounter -> {
                    // Key doesn't exist, so it has been initialized, no other action.
                    if (existingCounter == null) {
                        return;
                    }

                    // Counter exists, try to increment it.
                    map.get(counterName).onSuccess(currentCount -> {
                        Long count = currentCount != null ? currentCount + 1 : 1;
                        map.replace(counterName, count);
                    }).onFailure(cause -> logger.warn("Error reading counter from map.", cause));
                }).onFailure(cause -> logger.warn("Error initializing counter in a map.", cause)))
                .onFailure(cause -> logger.warn("Error getting counter map.", cause));
    }
}
