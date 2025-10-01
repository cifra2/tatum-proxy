package org.example;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * Factory methods for creating HTTP proxy.
 */
class ProxyFactory {
    public static HttpProxy createHttpProxy(Vertx vertx, JsonObject config, CountService countService) {
        PoolOptions poolOptions = new PoolOptions().setHttp1MaxSize(10)
                                          .setMaxLifetime(30)
                                          .setMaxLifetimeUnit(TimeUnit.SECONDS)
                                          .setMaxWaitQueueSize(20);
        HttpClientOptions clientOptions = new HttpClientOptions().setSsl(true)
                                                  .setTrustAll(true)
                                                  .setKeepAlive(true)
                                                  .setIdleTimeout(30);

        JsonObject tatumConfig = config.getJsonObject("tatumNode");
        HttpProxy proxy = HttpProxy.reverseProxy(vertx.createHttpClient(clientOptions, poolOptions))
                                  .origin(tatumConfig.getInteger("port"), tatumConfig.getString("host"));

        JsonObject proxyConfig = config.getJsonObject("proxy");
        proxy.addInterceptor(ProxyInterceptor.builder()
                                     .removingPathPrefix(proxyConfig.getString("pathPrefix"))
                                     .transformingRequestHeaders(headers -> headers.add("x-api-key", proxyConfig.getString("apiKey")))
                                     // Count method calls
                                     .transformingRequestBody(countService.createCountingTransformer())
                                     .build());

        return proxy;
    }

}
