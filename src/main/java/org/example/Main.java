package org.example;

import io.vertx.core.Vertx;

/**
 * Just for testing purposes.
 */
public class Main {
    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new TatumProxy());
    }
}
