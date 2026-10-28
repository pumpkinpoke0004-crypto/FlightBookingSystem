package com.flightbooking;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** Entry point and dependency-free HTTP/API server for FlightDesk. */
public final class Main {
    private static DataStore store;
    private static Path webRoot;
    private Main() {}

    public static void main(String[] args) throws Exception {
        int port = integerEnv("PORT", 8080);
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path dataDir = Path.of(System.getenv().getOrDefault("FLIGHT_BOOKING_DATA_DIR", root.resolve("data").toString())).toAbsolutePath().normalize();
        webRoot = root.resolve("src/main/resources/web").normalize();
        store = new DataStore(dataDir);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", Main::handle);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println("FlightDesk is running at http://localhost:" + port);
        System.out.println("Local demo data: " + dataDir);
        System.out.println("Press Ctrl+C to stop the server.");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path, method);
                return;
            }
            if (!method.equals("GET") && !method.equals("HEAD")) {
                sendJson(exchange, 405, error("Method not allowed."));
                return;
            }
            serveStatic(exchange, path, method.equals("HEAD"));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, error(message(e, "Please check the submitted information.")));
        } catch (Exception e) {
            e.printStackTrace(System.err);
            try { sendJson(exchange, 500, error("The server could not complete that request. Check the terminal for details.")); }
            catch (IOException ignored) { exchange.close(); }
        }
    }

    private static void handleApi(HttpExchange exchange, String path, String method) throws IOException {
        Map<String,String> q = queryParams(exchange.getRequestURI().getRawQuery());
        switch (path) {
            case "/api/health" -> sendJson(exchange, 200, success(Map.of("status", "ok", "today", LocalDate.now().toString())));
            case "/api/dashboard" -> sendJson(exchange, 200, success(store.dashboard()));
            case "/api/flights" -> {
                if (!method.equals("GET")) { methodNotAllowed(exchange); return; }
                double maxPrice = number(q.get("maxPrice"), 0);
                sendJson(exchange, 200, success(store.searchFlights(q.get("origin"), q.get("destination"), q.get("date"), maxPrice).stream().map(DataStore.Flight::toMap).toList()));
            }
            case "/api/passengers" -> {
                switch (method) {
                    case "GET" -> sendJson(exchange, 200, success(store.getPassengers().stream().map(DataStore.Passenger::toMap).toList()));
                    case "POST", "PUT" -> {
                        Map<String,String> form = formParams(exchange);
                        DataStore.Passenger p = store.savePassenger(form.get("id"), form.get("fullName"), form.get("email"), form.get("phone"), form.get("passport"));
                        sendJson(exchange, method.equals("POST") ? 201 : 200, success(p.toMap(), method.equals("POST") ? "Passenger added successfully." : "Passenger updated successfully."));
                    }
                    case "DELETE" -> { store.deletePassenger(q.get("id")); sendJson(exchange, 200, success(null, "Passenger deleted.")); }
                    default -> methodNotAllowed(exchange);
                }
            }
            case "/api/bookings" -> {
                switch (method) {
                    case "GET" -> sendJson(exchange, 200, success(store.bookingViews(q.get("q"))));
                    case "POST" -> {
                        Map<String,String> form = formParams(exchange);
                        DataStore.Booking booking = store.createBooking(required(form,"flightId"), required(form,"passengerId"));
                        sendJson(exchange, 201, success(store.bookingView(booking), "Booking created. Record the simulated payment to confirm the ticket."));
                    }
                    default -> methodNotAllowed(exchange);
                }
            }
            case "/api/booking" -> {
                if (!method.equals("GET")) { methodNotAllowed(exchange); return; }
                Map<String,Object> booking = store.bookingViewByPnr(q.get("pnr"));
                if (booking == null) sendJson(exchange, 404, error("No booking was found for that PNR."));
                else sendJson(exchange, 200, success(booking));
            }
            case "/api/payments" -> {
                switch (method) {
                    case "GET" -> sendJson(exchange, 200, success(store.getPayments().stream().map(DataStore.Payment::toMap).toList()));
                    case "POST" -> {
                        Map<String,String> form = formParams(exchange);
                        DataStore.Booking booking = store.recordPayment(required(form,"pnr"), form.getOrDefault("method","Demo payment"), form.getOrDefault("outcome","SUCCESS"));
                        String msg = booking.status().equals("CONFIRMED") ? "Simulated payment successful. Booking confirmed." : "Simulated payment failed. The booking remains pending.";
                        sendJson(exchange, 200, success(store.bookingView(booking), msg));
                    }
                    default -> methodNotAllowed(exchange);
                }
            }
            case "/api/cancellations" -> {
                if (!method.equals("POST")) { methodNotAllowed(exchange); return; }
                Map<String,String> form = formParams(exchange);
                DataStore.Booking booking = store.cancelBooking(required(form,"pnr"));
                sendJson(exchange, 200, success(store.bookingView(booking), "Booking cancelled. Any refund shown is simulated and has not moved real money."));
            }
            default -> sendJson(exchange, 404, error("API endpoint not found."));
        }
    }

    private static void serveStatic(HttpExchange exchange, String requestPath, boolean headOnly) throws IOException {
        String route = requestPath.equals("/") ? "/index.html" : requestPath;
        Path normalized = webRoot.resolve(route.substring(1)).normalize();
        if (!normalized.startsWith(webRoot)) { sendText(exchange, 403, "Forbidden", "text/plain; charset=utf-8", headOnly); return; }
        if (!Files.isRegularFile(normalized)) { sendText(exchange, 404, "Page not found", "text/plain; charset=utf-8", headOnly); return; }
        String type = contentType(normalized.getFileName().toString());
        byte[] body = Files.readAllBytes(normalized);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Cache-Control", "no-cache");
        if (headOnly) { exchange.sendResponseHeaders(200, -1); exchange.close(); }
        else { exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close(); }
    }

    private static String contentType(String filename) {
        String name = filename.toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
    private static void methodNotAllowed(HttpExchange exchange) throws IOException { sendJson(exchange, 405, error("Method not allowed for this endpoint.")); }
    private static String required(Map<String,String> values, String key) { String v = values.get(key); if(v == null || v.isBlank()) throw new IllegalArgumentException("Missing required field: " + key + "."); return v.trim(); }
    private static double number(String value, double fallback) { try { if (value == null || value.isBlank()) return fallback; return Double.parseDouble(value); } catch (NumberFormatException e) { return fallback; } }
    private static int integerEnv(String key, int fallback) { try { return Integer.parseInt(System.getenv().getOrDefault(key, Integer.toString(fallback))); } catch (NumberFormatException e) { return fallback; } }
    private static String message(Exception e, String fallback) { return e.getMessage() == null || e.getMessage().isBlank() ? fallback : e.getMessage(); }
    private static Map<String,Object> success(Object data) { return success(data, ""); }
    private static Map<String,Object> success(Object data, String message) { Map<String,Object> m = new LinkedHashMap<>(); m.put("success", true); m.put("data", data); if(!message.isBlank())m.put("message", message); return m; }
    private static Map<String,Object> error(String message) { Map<String,Object> m = new LinkedHashMap<>(); m.put("success", false); m.put("message", message); return m; }

    private static Map<String,String> queryParams(String raw) { return parseParams(raw); }
    private static Map<String,String> formParams(HttpExchange exchange) throws IOException {
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseParams(raw);
    }
    private static Map<String,String> parseParams(String raw) {
        Map<String,String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return result;
        for (String item : raw.split("&")) {
            if (item.isEmpty()) continue;
            int split = item.indexOf('=');
            String key = split < 0 ? item : item.substring(0,split);
            String value = split < 0 ? "" : item.substring(split+1);
            result.put(decode(key), decode(value));
        }
        return result;
    }
    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }

    private static void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = Json.stringify(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
    private static void sendText(HttpExchange exchange, int status, String text, String type, boolean headOnly) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type",type);
        if(headOnly){exchange.sendResponseHeaders(status,-1);exchange.close();}else{exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();}
    }
}
