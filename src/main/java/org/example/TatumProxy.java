package org.example;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;

/**
 * Reverse proxy which resends requests to configured Tatum Ethereum blockchain node.
 * <p>
 * It's using http pool for increased throughput and efficiency.
 * <p>
 * It exposes two endpoints for metrics (counts for method calls) and for health check.
 * <p>
 * Endpoint urls can be configured, defaults are {@code /stats} and {@code /health}
 */
public class TatumProxy extends VerticleBase {
    private static final Logger logger = LoggerFactory.getLogger(TatumProxy.class);

    @Override
    public Future<?> start() throws Exception {
        SharedData sharedData = vertx.sharedData();
        CountService countService = new CountService(sharedData);

        return ConfigLoader.load(vertx).compose(config -> {

            HttpProxy proxy = ProxyFactory.createHttpProxy(vertx, config, countService);

            Router proxyRouter = Router.router(vertx);
            JsonObject proxyConfig = config.getJsonObject("proxy");

            // Ethereum proxying
            proxyRouter.route(proxyConfig.getString("pathPrefix", "") + "/*")
                    // Log request
                    .handler(context -> {
                        logger.info(
                                "Received request from: %s, url: %s, method: %s"
                                        .formatted(
                                                context.request()
                                                        .remoteAddress()
                                                        .hostAddress(),
                                                context.request().uri(),
                                                context.request().method())
                        );
                        context.next();
                    })
                    .handler(ProxyHandler.create(proxy));

            // Health check
            proxyRouter.get(proxyConfig.getString("healthPath", "/health"))
                    .handler(context -> context.response().setStatusCode(200).end("OK"));

            // Method call count list
            proxyRouter.get(proxyConfig.getString("metricsPath", "/stats")).handler(countService::statsHandler);

            HttpServerOptions serverOptions = new HttpServerOptions()
                                                  .setSsl(true)
                                                  .setKeyCertOptions(
                                                          new PemKeyCertOptions()
                                                                  .setCertPath(proxyConfig.getString("certPath"))
                                                                  .setKeyPath(proxyConfig.getString("keyPath")
                                                                  )
                                                  );
            // Proxy server
            return vertx.createHttpServer(serverOptions)
                           .requestHandler(proxyRouter)
                           .listen(proxyConfig.getInteger("port"))
                           .onSuccess(res -> logger.info("HTTPS server is running on port 8443"))
                           .onFailure(cause -> logger.error("Could not start HTTPS server:", cause));
        }).onFailure(cause -> logger.error("Unable to load configuration.", cause));
    }
}
