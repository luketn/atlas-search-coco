package com.mycodefu.service;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Simple pure JDK HTTP server.
 */
public class SimpleServer {
    private static final Logger log = LoggerFactory.getLogger(SimpleServer.class);
    private final HttpServer server;

    private SimpleServer(HttpServer server){
        this.server = server;
    }

    public static SimpleServerBuilder create(int port) {
        return new SimpleServerBuilder(port);
    }

    public void start() {
        server.start();
        log.info("----------------------------------------");
        log.info("Welcome to the Simple Atlas Search Server!");
        log.info("Server started on http://localhost:"+server.getAddress().getPort());
        log.info("Example search http://localhost:"+server.getAddress().getPort()+"/image/search?text=red");
        log.info("----------------------------------------");

    }

    public void stop() {
        log.info("Server shutting down...");
        server.stop(0);
        log.info("Server completed shutting down.");
    }

    private static Map<String, String> parseQueryString(String query) {
        Map<String, String> queryParameters = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            String key = idx > 0 ? pair.substring(0, idx) : pair;
            String value = idx > 0 && pair.length() > idx + 1 ? pair.substring(idx + 1) : null;
            queryParameters.put(key, value);
        }
        return queryParameters;
    }

    public static class SimpleServerBuilder {
        HttpServer server;
        private SimpleServerBuilder(int port) {
            try {
                server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            server.createContext("/favicon.ico", exchange -> {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            });

            server.setExecutor(Executors.newFixedThreadPool(10));
        }

        public SimpleServerBuilder addContext(String path, HttpHandler handler) {
            server.createContext(path, handler);
            return this;
        }

        public SimpleServerBuilder addGetHandler(String path, Function<Map<String,String>, String> handler) {
            server.createContext(path, exchange -> {
                try {
                    String query = exchange.getRequestURI().getQuery();
                    Map<String, String> queryParameters;
                    if (query != null) {
                        queryParameters = parseQueryString(query);
                    } else {
                        queryParameters = Map.of();
                    }

                    String requestPath = exchange.getRequestURI().getPath();
                    log.info("Received request with path {} and query parameters: {}", requestPath, queryParameters);

                    String result = handler.apply(queryParameters);

                    if (result.startsWith("[") || result.startsWith("{")) {
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                    } else {
                        exchange.getResponseHeaders().add("Content-Type", "text/html");
                    }

                    byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();

                } catch (IOException e) {
                    log.error("Error handling request", e);
                    throw new RuntimeException(e);
                }
            });
            return this;
        }

        public SimpleServer build() {
            return new SimpleServer(this.server);
        }
    }
}
