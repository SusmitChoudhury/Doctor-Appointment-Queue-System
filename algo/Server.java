import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Doctor Appointment Queue System - Backend HTTP Server
 * Built with Java's standard com.sun.net.httpserver.HttpServer (no external libraries).
 * Exposes RESTful JSON endpoints on port 8080.
 */
public class Server {

    private static final int PORT = 8080;
    private static final AppointmentSystem system = new AppointmentSystem();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Register API endpoints
        server.createContext("/api/book", new BookHandler());
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/wait", new WaitHandler());
        server.createContext("/api/emergency", new EmergencyHandler());
        server.createContext("/api/next", new NextHandler());
        server.createContext("/api/cancel", new CancelHandler());
        server.createContext("/api/settings", new SettingsHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("==================================================");
        System.out.println(" Doctor Appointment System - Java Backend Running ");
        System.out.println(" Port: " + PORT);
        System.out.println(" Base URL: http://localhost:" + PORT + "/api/");
        System.out.println("==================================================");
    }

    // =========================================================================
    // Handlers
    // =========================================================================

    /**
     * POST /api/book
     * Body: { "name": "...", "phone": "..." }
     * Returns: { "label": "1", "token": 1, "name": "...", "wait": 10 }
     */
    static class BookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}");
                return;
            }

            String body = readBody(exchange);
            String name = extractString(body, "name");
            String phone = extractString(body, "phone");

            if (name == null || name.trim().isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"Patient name cannot be empty.\"}");
                return;
            }
            if (phone == null) phone = "";

            Patient patient;
            int wait;
            synchronized (system) {
                patient = system.bookAppointment(name.trim(), phone.trim());
                wait = system.getEstimatedWait(patient.token);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"label\":\"").append(escapeJson(patient.label())).append("\",");
            sb.append("\"token\":").append(patient.token).append(",");
            sb.append("\"name\":\"").append(escapeJson(patient.name)).append("\",");
            sb.append("\"phone\":\"").append(escapeJson(patient.phone)).append("\",");
            sb.append("\"wait\":").append(wait);
            sb.append("}");

            sendResponse(exchange, 200, sb.toString());
        }
    }

    /**
     * GET /api/status
     * Returns: { current, emergencyMode, avgTime, emergencyTime, queue[], emergencies[] }
     */
    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed. Use GET.\"}");
                return;
            }

            Patient current;
            boolean emergencyMode;
            int avgTime;
            int emergencyTime;
            List<Patient> queue;
            List<Patient> emergencies;

            synchronized (system) {
                current = system.getCurrent();
                emergencyMode = system.isEmergencyMode();
                avgTime = system.getAvgTime();
                emergencyTime = system.getEmergencyTime();
                queue = system.getQueueList();
                emergencies = system.getEmergencyList();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"emergencyMode\":").append(emergencyMode).append(",");
            sb.append("\"avgTime\":").append(avgTime).append(",");
            sb.append("\"emergencyTime\":").append(emergencyTime).append(",");

            // current patient
            if (current == null) {
                sb.append("\"current\":null,");
            } else {
                sb.append("\"current\":{");
                sb.append("\"token\":").append(current.token).append(",");
                sb.append("\"label\":\"").append(escapeJson(current.label())).append("\",");
                sb.append("\"name\":\"").append(escapeJson(current.name)).append("\",");
                sb.append("\"emergency\":").append(current.emergency);
                sb.append("},");
            }

            // queue items
            sb.append("\"queue\":[");
            for (int i = 0; i < queue.size(); i++) {
                Patient p = queue.get(i);
                int wait;
                synchronized (system) {
                    wait = system.getEstimatedWait(p.token);
                }
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\"token\":").append(p.token).append(",");
                sb.append("\"label\":\"").append(escapeJson(p.label())).append("\",");
                sb.append("\"name\":\"").append(escapeJson(p.name)).append("\",");
                sb.append("\"phone\":\"").append(escapeJson(p.phone)).append("\",");
                sb.append("\"wait\":").append(wait);
                sb.append("}");
            }
            sb.append("],");

            // emergency items
            sb.append("\"emergencies\":[");
            for (int i = 0; i < emergencies.size(); i++) {
                Patient e = emergencies.get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\"token\":").append(e.token).append(",");
                sb.append("\"label\":\"").append(escapeJson(e.label())).append("\",");
                sb.append("\"name\":\"").append(escapeJson(e.name)).append("\"");
                sb.append("}");
            }
            sb.append("]");

            sb.append("}");

            sendResponse(exchange, 200, sb.toString());
        }
    }

    /**
     * GET /api/wait?token=N
     * Returns: { found, label, name, position, wait, status }
     */
    static class WaitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed. Use GET.\"}");
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String tokenParam = params.get("token");

            if (tokenParam == null || tokenParam.trim().isEmpty()) {
                sendResponse(exchange, 400, "{\"found\":false,\"error\":\"Missing 'token' query parameter.\"}");
                return;
            }

            int token;
            try {
                token = Integer.parseInt(tokenParam.trim());
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "{\"found\":false,\"error\":\"Token must be a number.\"}");
                return;
            }

            Patient current;
            List<Patient> queue;
            int wait = -1;

            synchronized (system) {
                current = system.getCurrent();
                queue = system.getQueueList();
                wait = system.getEstimatedWait(token);
            }

            // Check if this patient is currently inside the doctor's room
            if (current != null && !current.emergency && current.token == token) {
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                sb.append("\"found\":true,");
                sb.append("\"label\":\"").append(escapeJson(current.label())).append("\",");
                sb.append("\"name\":\"").append(escapeJson(current.name)).append("\",");
                sb.append("\"position\":0,");
                sb.append("\"wait\":0,");
                sb.append("\"status\":\"SERVING\"");
                sb.append("}");
                sendResponse(exchange, 200, sb.toString());
                return;
            }

            // Search in queue
            Patient target = null;
            int position = 0;
            for (int i = 0; i < queue.size(); i++) {
                if (queue.get(i).token == token) {
                    target = queue.get(i);
                    position = i + 1; // 1-based position in queue
                    break;
                }
            }

            if (target != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                sb.append("\"found\":true,");
                sb.append("\"label\":\"").append(escapeJson(target.label())).append("\",");
                sb.append("\"name\":\"").append(escapeJson(target.name)).append("\",");
                sb.append("\"position\":").append(position).append(",");
                sb.append("\"wait\":").append(wait).append(",");
                sb.append("\"status\":\"WAITING\"");
                sb.append("}");
                sendResponse(exchange, 200, sb.toString());
            } else {
                sendResponse(exchange, 200, "{\"found\":false,\"message\":\"Token not found in waiting queue.\"}");
            }
        }
    }

    /**
     * POST /api/emergency
     * Body: { "name": "..." }
     * Adds emergency patient to stack (LIFO).
     */
    static class EmergencyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}");
                return;
            }

            String body = readBody(exchange);
            String name = extractString(body, "name");

            if (name == null || name.trim().isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"Emergency patient name cannot be empty.\"}");
                return;
            }

            Patient p;
            synchronized (system) {
                p = system.addEmergency(name.trim());
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"success\":true,");
            sb.append("\"label\":\"").append(escapeJson(p.label())).append("\",");
            sb.append("\"token\":").append(p.token).append(",");
            sb.append("\"name\":\"").append(escapeJson(p.name)).append("\"");
            sb.append("}");

            sendResponse(exchange, 200, sb.toString());
        }
    }

    /**
     * POST /api/next
     * Doctor calls next patient (Emergency Stack first, then Normal Queue).
     */
    static class NextHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}");
                return;
            }

            Patient current;
            synchronized (system) {
                current = system.callNext();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"success\":true,");
            if (current == null) {
                sb.append("\"current\":null");
            } else {
                sb.append("\"current\":{");
                sb.append("\"token\":").append(current.token).append(",");
                sb.append("\"label\":\"").append(escapeJson(current.label())).append("\",");
                sb.append("\"name\":\"").append(escapeJson(current.name)).append("\",");
                sb.append("\"emergency\":").append(current.emergency);
                sb.append("}");
            }
            sb.append("}");

            sendResponse(exchange, 200, sb.toString());
        }
    }

    /**
     * POST /api/cancel
     * Body: { "token": N }
     * Cancels an appointment if in the waiting queue.
     */
    static class CancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}");
                return;
            }

            String body = readBody(exchange);
            Integer token = extractInt(body, "token");

            if (token == null) {
                sendResponse(exchange, 400, "{\"success\":false,\"error\":\"Valid token is required.\"}");
                return;
            }

            boolean ok;
            synchronized (system) {
                ok = system.cancelAppointment(token);
            }

            sendResponse(exchange, 200, "{\"success\":" + ok + "}");
        }
    }

    /**
     * POST /api/settings
     * Body: { "avgTime": N, "emergencyTime": M }
     * Updates doctor consultation times.
     */
    static class SettingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) return;
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed. Use POST.\"}");
                return;
            }

            String body = readBody(exchange);
            Integer avg = extractInt(body, "avgTime");
            Integer emg = extractInt(body, "emergencyTime");

            synchronized (system) {
                if (avg != null && avg > 0) system.setAvgTime(avg);
                if (emg != null && emg > 0) system.setEmergencyTime(emg);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"success\":true,");
            sb.append("\"avgTime\":").append(system.getAvgTime()).append(",");
            sb.append("\"emergencyTime\":").append(system.getEmergencyTime());
            sb.append("}");

            sendResponse(exchange, 200, sb.toString());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Handles preflight CORS OPTIONS requests.
     * Returns true if request was handled.
     */
    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    /**
     * Sends a JSON response with status code and CORS headers.
     */
    private static void sendResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Reads the entire request body as a UTF-8 String.
     */
    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        byte[] buf = is.readAllBytes();
        return new String(buf, StandardCharsets.UTF_8);
    }

    /**
     * Extracts a string property from simple JSON: "key": "value"
     */
    private static String extractString(String json, String key) {
        if (json == null) return null;
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts an integer property from simple JSON: "key": 123 or "key": "123"
     */
    private static Integer extractInt(String json, String key) {
        if (json == null) return null;
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?(-?\\d+)\"?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Simple JSON string escaping.
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Parses URL query parameters into a Map.
     */
    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(key, value);
            }
        }
        return map;
    }
}
