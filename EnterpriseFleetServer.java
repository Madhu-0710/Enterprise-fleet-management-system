package com.enterprise.fleet;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise Fleet Management System - Core Backend Server
 * Written in Pure Java SE (Compatible with Java 17/21/25)
 */
public class EnterpriseFleetServer {

    private static int port = 8000;
    private static File publicDir;

    // In-memory thread-safe data stores
    private static final List<Vehicle> vehicles = new ArrayList<>();
    private static final Map<String, Booking> bookings = new ConcurrentHashMap<>();
    private static final AtomicInteger bookingSequence = new AtomicInteger(1001);

    public static void main(String[] args) throws IOException {
        // Resolve static public directory
        String userDir = System.getProperty("user.dir");
        File candidate1 = new File(userDir, "src/main/resources/public");
        File candidate2 = new File("C:/Users/WindowsReport/.gemini/antigravity/scratch/enterprise-fleet-system/src/main/resources/public");
        if (candidate1.exists()) {
            publicDir = candidate1;
        } else if (candidate2.exists()) {
            publicDir = candidate2;
        } else {
            publicDir = candidate1;
            publicDir.mkdirs();
        }

        initFleetData();
        initSampleBookings();

        // Check ports: if specified in args or try 8000, 8088, 9000, 8080
        HttpServer server = null;
        int[] tryPorts = { 8000, 8088, 9000, 8080 };
        if (args.length > 0) {
            try {
                tryPorts = new int[] { Integer.parseInt(args[0]) };
            } catch (Exception ignored) {}
        }

        for (int p : tryPorts) {
            try {
                server = HttpServer.create(new InetSocketAddress("0.0.0.0", p), 0);
                port = p;
                break;
            } catch (IOException e) {
                // try next port
            }
        }

        if (server == null) {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
            port = server.getAddress().getPort();
        }

        // API Endpoints
        server.createContext("/api/fleet", new FleetHandler());
        server.createContext("/api/calculate", new CalculateHandler());
        server.createContext("/api/bookings", new BookingsHandler());
        server.createContext("/api/admin/approve", new ApproveHandler());
        server.createContext("/api/admin/reject", new RejectHandler());
        server.createContext("/api/health", new HealthHandler());

        // Static files handler (serves HTML, CSS, JS)
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor() != null ? 
                Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("================================================================================");
        System.out.println("🚀 ENTERPRISE FLEET MANAGEMENT SYSTEM - BACKEND ONLINE");
        System.out.println("================================================================================");
        System.out.println("Local Access URL   : http://localhost:" + port);
        List<String> ips = getLocalIpAddresses();
        for (String ip : ips) {
            System.out.println("Network Access URL : http://" + ip + ":" + port);
        }
        System.out.println("Static Assets Dir  : " + publicDir.getAbsolutePath());
        System.out.println("Vehicles Loaded    : " + vehicles.size() + " categories");
        System.out.println("Server listening on port " + port + " (All interfaces 0.0.0.0)");
        System.out.println("================================================================================");
    }

    private static List<String> getLocalIpAddresses() {
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String ip = addr.getHostAddress();
                    if (!addr.isLoopbackAddress() && !ip.contains(":") && !ip.startsWith("169.254")) {
                        result.add(ip);
                    }
                }
            }
        } catch (Exception e) {
            result.add("127.0.0.1");
        }
        return result;
    }

    // Initialize fleet vehicle categories
    private static void initFleetData() {
        vehicles.add(new Vehicle(
                "v1",
                "Tata Ace Gold (Chhota Hathi)",
                "Light Commercial Vehicle (LCV)",
                "750 kg (0.75 Ton)",
                22.0,
                500.0,
                14,
                "Intra-city logistics, FMCG, quick parcel delivery & narrow roads",
                "Best for City Delivery",
                "truck-mini"
        ));

        vehicles.add(new Vehicle(
                "v2",
                "Mahindra Bolero Maxi Truck Plus",
                "Medium Cargo Pickup",
                "1,500 kg (1.5 Tons)",
                32.0,
                800.0,
                18,
                "Suburban & regional distribution, retail inventory, farm produce",
                "High Popularity",
                "truck-pickup"
        ));

        vehicles.add(new Vehicle(
                "v3",
                "Eicher Pro 2049 (14-Foot Container)",
                "Intermediate Commercial Vehicle (ICV)",
                "5,000 kg (5.0 Tons)",
                48.0,
                1500.0,
                12,
                "Covered weatherproof container, e-commerce parcels, electronics",
                "Weatherproof Container",
                "truck-box"
        ));

        vehicles.add(new Vehicle(
                "v4",
                "Ashok Leyland 1615 HE (20-Foot Cargo)",
                "Medium & Heavy Commercial Vehicle (MHCV)",
                "12,000 kg (12.0 Tons)",
                75.0,
                2800.0,
                8,
                "Interstate logistics, bulk industrial components & hardware",
                "Heavy Hauler",
                "truck-heavy"
        ));

        vehicles.add(new Vehicle(
                "v5",
                "BharatBenz 2823R (Multi-Axle Heavy)",
                "Heavy Duty Multi-Axle Freight",
                "22,000 kg (22.0 Tons)",
                110.0,
                4500.0,
                6,
                "Long-haul heavy industrial freight, raw materials, nationwide transport",
                "Industrial Grade",
                "truck-multi"
        ));

        vehicles.add(new Vehicle(
                "v6",
                "Volvo FM420 Reefer (Cold-Chain)",
                "Refrigerated Temperature-Controlled",
                "18,000 kg (18.0 Tons)",
                135.0,
                5500.0,
                5,
                "-25°C to +15°C certified cold storage, pharmaceuticals, perishables & seafood",
                "Cold-Chain Certified",
                "truck-reefer"
        ));

        vehicles.add(new Vehicle(
                "v7",
                "Tata Signa 4825.TK High-Deck Flatbed",
                "Oversized Freight & Flatbed Trailer",
                "35,000 kg (35.0 Tons)",
                165.0,
                7000.0,
                4,
                "Heavy construction machinery, structural beams, wind turbines & containers",
                "Mega Freight",
                "truck-flatbed"
        ));
    }

    private static void initSampleBookings() {
        Booking b1 = new Booking(
                "EFM-2026-1001",
                "Apex Industrial Logistics",
                "Vikram Malhotra",
                "+91 98201 44521",
                "v4",
                "Ashok Leyland 1615 HE (20-Foot Cargo)",
                "Mumbai Port Gateway",
                "Pune MIDC Bhosari",
                148.0,
                "one-way",
                148.0,
                75.0,
                2800.0,
                11100.0,
                222.0,
                706.1,
                14828.1,
                "Automotive Spare Parts",
                "APPROVED",
                "KA-04-E-8834",
                "Ramesh Chand",
                "+91 98450 11223",
                "2026-10-07 14:30"
        );
        bookings.put(b1.id, b1);

        Booking b2 = new Booking(
                "EFM-2026-1002",
                "FreshHarvest Organics",
                "Priya Sharma",
                "+91 98711 55677",
                "v6",
                "Volvo FM420 Reefer (Cold-Chain)",
                "Nashik Agri Cold Storage",
                "Bandra Kurla Complex Mumbai",
                165.0,
                "round-trip",
                330.0,
                135.0,
                8250.0,
                40986.0,
                495.0,
                2486.5,
                52217.5,
                "Organic Produce & Exotic Fruits (4°C)",
                "PROCESSING",
                "",
                "",
                "",
                ""
        );
        bookings.put(b2.id, b2);
    }

    // Models
    public static class Vehicle {
        public String id;
        public String name;
        public String category;
        public String loadCapacity;
        public double ratePerKm;
        public double baseFare;
        public int availableCount;
        public String description;
        public String badge;
        public String icon;

        public Vehicle(String id, String name, String category, String loadCapacity, double ratePerKm, 
                       double baseFare, int availableCount, String description, String badge, String icon) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.loadCapacity = loadCapacity;
            this.ratePerKm = ratePerKm;
            this.baseFare = baseFare;
            this.availableCount = availableCount;
            this.description = description;
            this.badge = badge;
            this.icon = icon;
        }

        public String toJson() {
            return "{" +
                    "\"id\":\"" + escape(id) + "\"," +
                    "\"name\":\"" + escape(name) + "\"," +
                    "\"category\":\"" + escape(category) + "\"," +
                    "\"loadCapacity\":\"" + escape(loadCapacity) + "\"," +
                    "\"ratePerKm\":" + ratePerKm + "," +
                    "\"baseFare\":" + baseFare + "," +
                    "\"availableCount\":" + availableCount + "," +
                    "\"description\":\"" + escape(description) + "\"," +
                    "\"badge\":\"" + escape(badge) + "\"," +
                    "\"icon\":\"" + escape(icon) + "\"" +
                    "}";
        }
    }

    public static class Booking {
        public String id;
        public String companyName;
        public String contactName;
        public String contactPhone;
        public String vehicleId;
        public String vehicleName;
        public String source;
        public String destination;
        public double inputKms;
        public String tripType; // "one-way" or "round-trip"
        public double totalBillableKms;
        public double ratePerKm;
        public double baseFare;
        public double distanceCost;
        public double tollTaxes;
        public double gst;
        public double grandTotal;
        public String cargoDescription;
        public String status; // "PROCESSING", "APPROVED", "REJECTED"
        public String allocatedVehicleNumber;
        public String allocatedDriver;
        public String driverPhone;
        public String approvalTime;
        public String createdAt;

        public Booking(String id, String companyName, String contactName, String contactPhone,
                       String vehicleId, String vehicleName, String source, String destination,
                       double inputKms, String tripType, double totalBillableKms, double ratePerKm,
                       double baseFare, double distanceCost, double tollTaxes, double gst,
                       double grandTotal, String cargoDescription, String status,
                       String allocatedVehicleNumber, String allocatedDriver, String driverPhone,
                       String approvalTime) {
            this.id = id;
            this.companyName = companyName;
            this.contactName = contactName;
            this.contactPhone = contactPhone;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.source = source;
            this.destination = destination;
            this.inputKms = inputKms;
            this.tripType = tripType;
            this.totalBillableKms = totalBillableKms;
            this.ratePerKm = ratePerKm;
            this.baseFare = baseFare;
            this.distanceCost = distanceCost;
            this.tollTaxes = tollTaxes;
            this.gst = gst;
            this.grandTotal = grandTotal;
            this.cargoDescription = cargoDescription;
            this.status = status;
            this.allocatedVehicleNumber = allocatedVehicleNumber;
            this.allocatedDriver = allocatedDriver;
            this.driverPhone = driverPhone;
            this.approvalTime = approvalTime;
            this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        public String toJson() {
            return "{" +
                    "\"id\":\"" + escape(id) + "\"," +
                    "\"companyName\":\"" + escape(companyName) + "\"," +
                    "\"contactName\":\"" + escape(contactName) + "\"," +
                    "\"contactPhone\":\"" + escape(contactPhone) + "\"," +
                    "\"vehicleId\":\"" + escape(vehicleId) + "\"," +
                    "\"vehicleName\":\"" + escape(vehicleName) + "\"," +
                    "\"source\":\"" + escape(source) + "\"," +
                    "\"destination\":\"" + escape(destination) + "\"," +
                    "\"inputKms\":" + inputKms + "," +
                    "\"tripType\":\"" + escape(tripType) + "\"," +
                    "\"totalBillableKms\":" + totalBillableKms + "," +
                    "\"ratePerKm\":" + ratePerKm + "," +
                    "\"baseFare\":" + baseFare + "," +
                    "\"distanceCost\":" + distanceCost + "," +
                    "\"tollTaxes\":" + tollTaxes + "," +
                    "\"gst\":" + gst + "," +
                    "\"grandTotal\":" + grandTotal + "," +
                    "\"cargoDescription\":\"" + escape(cargoDescription) + "\"," +
                    "\"status\":\"" + escape(status) + "\"," +
                    "\"allocatedVehicleNumber\":\"" + escape(allocatedVehicleNumber) + "\"," +
                    "\"allocatedDriver\":\"" + escape(allocatedDriver) + "\"," +
                    "\"driverPhone\":\"" + escape(driverPhone) + "\"," +
                    "\"approvalTime\":\"" + escape(approvalTime) + "\"," +
                    "\"createdAt\":\"" + escape(createdAt) + "\"" +
                    "}";
        }
    }

    public static class CalculationResult {
        public double kms;
        public String tripType;
        public double billableKms;
        public double ratePerKm;
        public double baseFare;
        public double distanceCost;
        public double tollTaxes;
        public double gst;
        public double total;

        public String toJson(Vehicle v) {
            return "{" +
                    "\"vehicleId\":\"" + escape(v.id) + "\"," +
                    "\"vehicleName\":\"" + escape(v.name) + "\"," +
                    "\"loadCapacity\":\"" + escape(v.loadCapacity) + "\"," +
                    "\"kms\":" + kms + "," +
                    "\"tripType\":\"" + escape(tripType) + "\"," +
                    "\"billableKms\":" + billableKms + "," +
                    "\"ratePerKm\":" + ratePerKm + "," +
                    "\"baseFare\":" + baseFare + "," +
                    "\"distanceCost\":" + distanceCost + "," +
                    "\"tollTaxes\":" + tollTaxes + "," +
                    "\"gst\":" + gst + "," +
                    "\"total\":" + total +
                    "}";
        }
    }

    public static CalculationResult calculateFare(Vehicle v, double kms, String tripType) {
        CalculationResult r = new CalculationResult();
        r.kms = kms;
        r.tripType = tripType;
        r.ratePerKm = v.ratePerKm;

        if ("round-trip".equalsIgnoreCase(tripType)) {
            r.billableKms = kms * 2.0;
            r.baseFare = Math.round(v.baseFare * 1.5 * 100.0) / 100.0;
            r.distanceCost = Math.round((r.billableKms * v.ratePerKm * 0.92) * 100.0) / 100.0;
        } else {
            r.billableKms = kms;
            r.baseFare = v.baseFare;
            r.distanceCost = Math.round((kms * v.ratePerKm) * 100.0) / 100.0;
        }

        r.tollTaxes = r.billableKms > 40 ? Math.round((r.billableKms * 1.5) * 100.0) / 100.0 : 0.0;
        double subtotal = r.baseFare + r.distanceCost + r.tollTaxes;
        r.gst = Math.round((subtotal * 0.05) * 100.0) / 100.0;
        r.total = Math.round((subtotal + r.gst) * 100.0) / 100.0;
        return r;
    }

    // HTTP Handlers
    static class FleetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            int totalCount = 0;
            StringBuilder vJson = new StringBuilder("[");
            for (int i = 0; i < vehicles.size(); i++) {
                Vehicle v = vehicles.get(i);
                totalCount += v.availableCount;
                vJson.append(v.toJson());
                if (i < vehicles.size() - 1) vJson.append(",");
            }
            vJson.append("]");

            String response = "{" +
                    "\"systemName\":\"Enterprise Fleet Management System\"," +
                    "\"companyName\":\"TitanFleet Global Logistics Enterprise\"," +
                    "\"headquarters\":\"Bengaluru & Gurugram, India\"," +
                    "\"activeFleetCount\":" + totalCount + "," +
                    "\"vehicleCategoriesCount\":" + vehicles.size() + "," +
                    "\"supportPhone\":\"+91 1800-419-8800\"," +
                    "\"supportEmail\":\"dispatch@enterprisefleet.com\"," +
                    "\"vehicles\":" + vJson.toString() +
                    "}";

            sendJsonResponse(exchange, 200, response);
        }
    }

    static class CalculateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = readBody(exchange);
            Map<String, String> params = parseSimpleJson(body);

            String vehicleId = params.getOrDefault("vehicleId", "v1");
            double kms = parseDouble(params.get("kms"), 10.0);
            String tripType = params.getOrDefault("tripType", "one-way");

            Vehicle vehicle = findVehicle(vehicleId);
            if (vehicle == null) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Vehicle category not found\"}");
                return;
            }

            CalculationResult calc = calculateFare(vehicle, kms, tripType);
            sendJsonResponse(exchange, 200, calc.toJson(vehicle));
        }
    }

    static class BookingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("id=")) {
                    String reqId = query.split("id=")[1].split("&")[0];
                    Booking b = bookings.get(reqId);
                    if (b != null) {
                        sendJsonResponse(exchange, 200, b.toJson());
                    } else {
                        sendJsonResponse(exchange, 404, "{\"error\":\"Booking not found\"}");
                    }
                    return;
                }

                List<Booking> list = new ArrayList<>(bookings.values());
                list.sort((a, b) -> b.id.compareTo(a.id));

                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    sb.append(list.get(i).toJson());
                    if (i < list.size() - 1) sb.append(",");
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());

            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                Map<String, String> p = parseSimpleJson(body);

                String vehicleId = p.getOrDefault("vehicleId", "v1");
                Vehicle v = findVehicle(vehicleId);
                if (v == null) v = vehicles.get(0);

                String companyName = p.getOrDefault("companyName", "Enterprise Partner");
                String contactName = p.getOrDefault("contactName", "Logistics Officer");
                String contactPhone = p.getOrDefault("contactPhone", "+91 99999 00000");
                String source = p.getOrDefault("source", "City Warehouse Hub A");
                String destination = p.getOrDefault("destination", "Distribution Centre B");
                double kms = parseDouble(p.get("kms"), 25.0);
                String tripType = p.getOrDefault("tripType", "one-way");
                String cargo = p.getOrDefault("cargoDescription", "Commercial Goods");

                CalculationResult calc = calculateFare(v, kms, tripType);
                String newId = "EFM-" + LocalDateTime.now().getYear() + "-" + bookingSequence.incrementAndGet();

                // Initial state is PROCESSING
                Booking booking = new Booking(
                        newId,
                        companyName,
                        contactName,
                        contactPhone,
                        v.id,
                        v.name,
                        source,
                        destination,
                        kms,
                        tripType,
                        calc.billableKms,
                        calc.ratePerKm,
                        calc.baseFare,
                        calc.distanceCost,
                        calc.tollTaxes,
                        calc.gst,
                        calc.total,
                        cargo,
                        "PROCESSING",
                        "",
                        "",
                        "",
                        ""
                );

                bookings.put(newId, booking);
                sendJsonResponse(exchange, 201, booking.toJson());
            } else {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        }
    }

    static class ApproveHandler implements HttpHandler {
        private static final String[] DRIVERS = {
                "Rajesh Kumar (+91 98450 12345)",
                "Sunil Rao (+91 97410 88921)",
                "Amitabh Sen (+91 99881 23411)",
                "Balwinder Singh (+91 98140 76543)",
                "Mohan Das (+91 94480 34567)"
        };
        private static final String[] PLATES = {
                "KA-01-EF-4821", "MH-12-TX-9022", "DL-01-AG-3419", "TN-07-LK-7712", "HR-26-BR-6290"
        };
        private static int driverIdx = 0;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = readBody(exchange);
            Map<String, String> p = parseSimpleJson(body);
            String bookingId = p.get("bookingId");

            if (bookingId == null || !bookings.containsKey(bookingId)) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Booking not found with ID: " + bookingId + "\"}");
                return;
            }

            Booking b = bookings.get(bookingId);
            b.status = "APPROVED";
            
            String plate = p.getOrDefault("vehicleNumber", PLATES[Math.abs(driverIdx) % PLATES.length]);
            String fullDriver = DRIVERS[Math.abs(driverIdx) % DRIVERS.length];
            driverIdx++;

            String dName = fullDriver.split(" \\(")[0];
            String dPhone = fullDriver.contains("(") ? fullDriver.substring(fullDriver.indexOf("(") + 1, fullDriver.indexOf(")")) : "+91 98000 11111";

            b.allocatedVehicleNumber = plate;
            b.allocatedDriver = p.getOrDefault("driverName", dName);
            b.driverPhone = p.getOrDefault("driverPhone", dPhone);
            b.approvalTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            sendJsonResponse(exchange, 200, b.toJson());
        }
    }

    static class RejectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String body = readBody(exchange);
            Map<String, String> p = parseSimpleJson(body);
            String bookingId = p.get("bookingId");

            if (bookingId == null || !bookings.containsKey(bookingId)) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Booking not found\"}");
                return;
            }

            Booking b = bookings.get(bookingId);
            b.status = "REJECTED";
            b.approvalTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            sendJsonResponse(exchange, 200, b.toJson());
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String resp = "{\"status\":\"UP\",\"time\":\"" + LocalDateTime.now() + "\"}";
            sendJsonResponse(exchange, 200, resp);
        }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            while (path.startsWith("/")) {
                path = path.substring(1);
            }

            File targetFile = new File(publicDir, path);
            if (!targetFile.exists() || targetFile.isDirectory()) {
                targetFile = new File(publicDir, "index.html");
            }

            if (!targetFile.exists()) {
                String notFound = "<h1>404 File Not Found</h1>";
                exchange.sendResponseHeaders(404, notFound.length());
                OutputStream os = exchange.getResponseBody();
                os.write(notFound.getBytes());
                os.close();
                return;
            }

            String mime = getMimeType(targetFile.getName());
            exchange.getResponseHeaders().set("Content-Type", mime + "; charset=UTF-8");
            addCorsHeaders(exchange);

            byte[] bytes = java.nio.file.Files.readAllBytes(targetFile.toPath());
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // Utility methods
    private static Vehicle findVehicle(String id) {
        for (Vehicle v : vehicles) {
            if (v.id.equalsIgnoreCase(id)) return v;
        }
        return null;
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static double parseDouble(String val, double def) {
        if (val == null) return def;
        try {
            return Double.parseDouble(val.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        String content = json.trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            content = content.substring(1, content.length() - 1).trim();
        }

        boolean inQuote = false;
        StringBuilder curKey = new StringBuilder();
        StringBuilder curVal = new StringBuilder();
        boolean parsingKey = true;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote) {
                if (c == ':') {
                    parsingKey = false;
                    continue;
                }
                if (c == ',') {
                    if (curKey.length() > 0) {
                        map.put(curKey.toString().trim(), curVal.toString().trim());
                    }
                    curKey = new StringBuilder();
                    curVal = new StringBuilder();
                    parsingKey = true;
                    continue;
                }
            }
            if (parsingKey) {
                curKey.append(c);
            } else {
                curVal.append(c);
            }
        }
        if (curKey.length() > 0) {
            map.put(curKey.toString().trim(), curVal.toString().trim());
        }
        return map;
    }

    private static String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "text/plain";
    }
}