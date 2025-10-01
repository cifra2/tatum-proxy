package org.example;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Loads configuration from yaml file config.yaml.
 * <p>
 * In case there exist same environment property, it will override yaml value.
 */
class ConfigLoader {
    public static Future<JsonObject> load(Vertx vertx) {
        ConfigRetrieverOptions configOptions = new ConfigRetrieverOptions()
                                   .setIncludeDefaultStores(false)
                                   .addStore(new ConfigStoreOptions()
                                                     .setType("file")
                                                     .setFormat("yaml")
                                                     .setConfig(new JsonObject().put("path", "config.yaml")))
                                   .addStore(new ConfigStoreOptions()
                                                     .setType("env"));

        ConfigRetriever retriever = ConfigRetriever.create(vertx, configOptions);
        return retriever.getConfig();
    }
}
