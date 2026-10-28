package com.flightbooking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.security.SecureRandom;

/** Small file-backed repository so the demo runs without an external database. */
public final class DataStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path dataDir;
    private final Path flightsFile;
    private final Path passengersFile;
    private final Path bookingsFile;
    private final Path paymentsFile;
    private final List<Flight> flights = new ArrayList<>();
    private final List<Passenger> passengers = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();

    public DataStore(Path dataDir) throws IOException {
        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
        flightsFile = dataDir.resolve("flights.tsv");
        passengersFile = dataDir.resolve("passengers.tsv");
        bookingsFile = dataDir.resolve("bookings.tsv");
        paymentsFile = dataDir.resolve("payments.tsv");
        loadAll();
        if (flights.isEmpty()) {
            seedFlights();
            saveFlights();
        }
    }

    private void loadAll() throws IOException {
        readLines(flightsFile).forEach(line -> { try { String[] a = decode(line); if (a.length == 10) flights.add(new Flight(a[0],a[1],a[2],a[3],a[4],LocalDateTime.parse(a[5]),LocalDateTime.parse(a[6]),Double.parseDouble(a[7]),Integer.parseInt(a[8]),a[9])); } catch (RuntimeException ignored) { } });
        readLines(passengersFile).forEach(line -> { try { String[] a = decode(line); if (a.length == 5) passengers.add(new Passenger(a[0],a[1],a[2],a[3],a[4])); } catch (RuntimeException ignored) { } });
        readLines(bookingsFile).forEach(line -> { try { String[] a = decode(line); if (a.length == 9) bookings.add(new Booking(a[0],a[1],a[2],a[3],LocalDateTime.parse(a[4]),a[5],Double.parseDouble(a[6]),Boolean.parseBoolean(a[7]),a[8])); } catch (RuntimeException ignored) { } });
        readLines(paymentsFile).forEach(line -> { try { String[] a = decode(line); if (a.length == 8) payments.add(new Payment(a[0],a[1],a[2],a[3],a[4],a[5],LocalDateTime.parse(a[6]),Double.parseDouble(a[7]))); } catch (RuntimeException ignored) { } });
    }

    private List<String> readLines(Path file) throws IOException { return Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of(); }
    private String encode(String... fields) { Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding(); List<String> encoded = new ArrayList<>(); for (String field : fields) encoded.add(encoder.encodeToString((field == null ? "" : field).getBytes(StandardCharsets.UTF_8))); return String.join("|", encoded); }
    private String[] decode(String line) { Base64.Decoder decoder = Base64.getUrlDecoder(); String[] values = line.split("\\|", -1); for (int i=0;i<values.length;i++) values[i] = new String(decoder.decode(values[i]), StandardCharsets.UTF_8); return values; }
    private void write(Path file, List<String> lines) throws IOException { Files.write(file, lines, StandardCharsets.UTF_8); }
    private void saveFlights() throws IOException { List<String> lines = new ArrayList<>(); for (Flight f : flights) lines.add(encode(f.id,f.airline,f.flightNo,f.origin,f.destination,f.departure.toString(),f.arrival.toString(),Double.toString(f.price),Integer.toString(f.seatsAvailable),f.fareClass)); write(flightsFile,lines); }
    private void savePassengers() throws IOException { List<String> lines = new ArrayList<>(); for (Passenger p : passengers) lines.add(encode(p.id,p.fullName,p.email,p.phone,p.passport)); write(passengersFile,lines); }
    private void saveBookings() throws IOException { List<String> lines = new ArrayList<>(); for (Booking b : bookings) lines.add(encode(b.id,b.pnr,b.flightId,b.passengerId,b.createdAt.toString(),b.status,Double.toString(b.amount),Boolean.toString(b.seatReleased),b.note)); write(bookingsFile,lines); }
    private void savePayments() throws IOException { List<String> lines = new ArrayList<>(); for (Payment p : payments) lines.add(encode(p.id,p.pnr,p.kind,p.method,p.status,p.reference,p.timestamp.toString(),Double.toString(p.amount))); write(paymentsFile,lines); }

    private void seedFlights() {
        String[][] routes = {
            {"Ahmedabad","Mumbai","Skyline Air","SL"}, {"Ahmedabad","Delhi","Aero India","AI"},
            {"Ahmedabad","Bengaluru","Western Wings","WW"}, {"Mumbai","Ahmedabad","Skyline Air","SL"},
            {"Mumbai","Delhi","Aero India","AI"}, {"Mumbai","Goa","Coastal Air","CA"},
            {"Delhi","Mumbai","Aero India","AI"}, {"Delhi","Jaipur","NorthStar","NS"},
            {"Bengaluru","Chennai","Southern Air","SA"}, {"Hyderabad","Mumbai","Deccan Connect","DC"},
            {"Chennai","Bengaluru","Southern Air","SA"}, {"Pune","Ahmedabad","Western Wings","WW"}
        };
        int id = 1001;
        for (int day=0; day<12; day++) {
            for (int r=0; r<routes.length; r++) {
                if ((day + r) % 3 == 2) continue;
                LocalDate date = LocalDate.now().plusDays(day);
                int hour = 6 + ((day * 3 + r * 2) % 14);
                int minute = ((day + r) % 2) * 30;
                LocalDateTime departure = date.atTime(LocalTime.of(hour, minute));
                int duration = 65 + ((r * 19 + day * 7) % 155);
                LocalDateTime arrival = departure.plusMinutes(duration);
                double price = 3200 + ((r * 437 + day * 173) % 5400);
                int seats = 5 + ((r + day) % 18);
                String fare = price > 6500 ? "Business" : "Economy";
                flights.add(new Flight("FL-" + id, routes[r][2], routes[r][3] + (100 + (id % 800)), routes[r][0], routes[r][1], departure, arrival, price, seats, fare));
                id++;
            }
        }
    }

    public synchronized List<Flight> searchFlights(String origin, String destination, String date, double maxPrice) {
        String o = clean(origin).toLowerCase(Locale.ROOT), d = clean(destination).toLowerCase(Locale.ROOT);
        LocalDate targetDate = null; try { if (!clean(date).isBlank()) targetDate = LocalDate.parse(date); } catch (RuntimeException ignored) { }
        LocalDate finalTargetDate = targetDate;
        return flights.stream()
            .filter(f -> o.isBlank() || f.origin.toLowerCase(Locale.ROOT).contains(o))
            .filter(f -> d.isBlank() || f.destination.toLowerCase(Locale.ROOT).contains(d))
            .filter(f -> finalTargetDate == null || f.departure.toLocalDate().equals(finalTargetDate))
            .filter(f -> maxPrice <= 0 || f.price <= maxPrice)
            .filter(f -> f.departure.isAfter(LocalDateTime.now().minusMinutes(1)))
            .sorted(Comparator.comparing((Flight f) -> f.departure).thenComparingDouble(f -> f.price)).toList();
    }
    public synchronized List<Flight> getFlights() { return flights.stream().sorted(Comparator.comparing(f -> f.departure)).toList(); }
    public synchronized Optional<Flight> flightById(String id) { return flights.stream().filter(f -> f.id.equals(id)).findFirst(); }
    public synchronized List<Passenger> getPassengers() { return passengers.stream().sorted(Comparator.comparing(p -> p.fullName.toLowerCase(Locale.ROOT))).toList(); }
    public synchronized Optional<Passenger> passengerById(String id) { return passengers.stream().filter(p -> p.id.equals(id)).findFirst(); }

    public synchronized Passenger savePassenger(String id, String name, String email, String phone, String passport) throws IOException {
        name = clean(name); email = clean(email); phone = clean(phone); passport = clean(passport);
        if (name.isBlank()) throw new IllegalArgumentException("Full name is required.");
        if (email.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Enter a valid email address.");
        if (phone.isBlank()) throw new IllegalArgumentException("Phone number is required.");
        final String passengerId = clean(id);
        for (Passenger existing : passengers) {
            if (existing.email.equalsIgnoreCase(email) && !existing.id.equals(passengerId)) {
                throw new IllegalArgumentException("A passenger with this email already exists.");
            }
        }
        Passenger result;
        if (!passengerId.isBlank()) {
            int index = indexOfPassenger(passengerId);
            if (index < 0) throw new IllegalArgumentException("Passenger record was not found.");
            result = new Passenger(passengerId,name,email,phone,passport); passengers.set(index,result);
        } else {
            result = new Passenger("PAX-" + UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),name,email,phone,passport); passengers.add(result);
        }
        savePassengers(); return result;
    }
    private int indexOfPassenger(String id) { for (int i=0;i<passengers.size();i++) if(passengers.get(i).id.equals(id)) return i; return -1; }
    public synchronized void deletePassenger(String id) throws IOException {
        if (bookings.stream().anyMatch(b -> b.passengerId.equals(id))) throw new IllegalArgumentException("This passenger has booking history. Keep this record to preserve ticket and audit details.");
        boolean removed = passengers.removeIf(p -> p.id.equals(id)); if (!removed) throw new IllegalArgumentException("Passenger record was not found."); savePassengers();
    }

    public synchronized Booking createBooking(String flightId, String passengerId) throws IOException {
        Flight flight = flightById(flightId).orElseThrow(() -> new IllegalArgumentException("Please choose a valid flight."));
        passengerById(passengerId).orElseThrow(() -> new IllegalArgumentException("Please choose a passenger."));
        if (flight.departure.isBefore(LocalDateTime.now())) throw new IllegalArgumentException("This flight has already departed.");
        if (flight.seatsAvailable < 1) throw new IllegalArgumentException("No seats are available on this flight.");
        int fi = flights.indexOf(flight);
        flights.set(fi, flight.withSeats(flight.seatsAvailable - 1));
        String pnr = generatePnr();
        Booking booking = new Booking("BKG-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),pnr,flightId,passengerId,LocalDateTime.now(),"PENDING_PAYMENT",flight.price,false,"Created; awaiting payment");
        bookings.add(booking);
        try { saveFlights(); saveBookings(); } catch (IOException e) { flights.set(fi,flight); bookings.remove(booking); throw e; }
        return booking;
    }
    private String generatePnr() {
        final char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
        while (true) {
            StringBuilder value = new StringBuilder(6);
            for (int i = 0; i < 6; i++) value.append(alphabet[RANDOM.nextInt(alphabet.length)]);
            String candidate = value.toString();
            boolean exists = false;
            for (Booking booking : bookings) if (booking.pnr.equals(candidate)) { exists = true; break; }
            if (!exists) return candidate;
        }
    }
    public synchronized List<Booking> getBookings(String search) {
        String q = clean(search).toLowerCase(Locale.ROOT);
        return bookings.stream().filter(b -> q.isBlank() || b.pnr.toLowerCase(Locale.ROOT).contains(q) || b.status.toLowerCase(Locale.ROOT).contains(q) || b.passengerId.toLowerCase(Locale.ROOT).contains(q))
            .sorted(Comparator.comparing((Booking b) -> b.createdAt).reversed()).toList();
    }
    public synchronized Optional<Booking> bookingByPnr(String pnr) { return bookings.stream().filter(b -> b.pnr.equalsIgnoreCase(clean(pnr))).findFirst(); }
    public synchronized Booking recordPayment(String pnr, String method, String outcome) throws IOException {
        Booking booking = bookingByPnr(pnr).orElseThrow(() -> new IllegalArgumentException("Booking reference not found."));
        if (booking.status.equals("CANCELLED")) throw new IllegalArgumentException("This booking has been cancelled.");
        if (booking.status.equals("CONFIRMED")) throw new IllegalArgumentException("This booking is already paid and confirmed.");
        String selectedOutcome = "FAILED".equalsIgnoreCase(outcome) ? "FAILED" : "SUCCESS";
        String reference = (selectedOutcome.equals("SUCCESS") ? "TXN-" : "FAIL-") + UUID.randomUUID().toString().substring(0,10).toUpperCase(Locale.ROOT);
        payments.add(new Payment("PAY-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),booking.pnr,"PAYMENT",clean(method).isBlank()?"Demo payment":clean(method),selectedOutcome.equals("SUCCESS")?"PAID":"FAILED",reference,LocalDateTime.now(),booking.amount));
        Booking updated = booking;
        if (selectedOutcome.equals("SUCCESS")) updated = booking.withStatus("CONFIRMED","Payment recorded by demo simulator");
        replaceBooking(updated);
        try { savePayments(); saveBookings(); } catch (IOException e) { payments.remove(payments.size()-1); replaceBooking(booking); throw e; }
        return updated;
    }
    public synchronized Booking cancelBooking(String pnr) throws IOException {
        Booking booking = bookingByPnr(pnr).orElseThrow(() -> new IllegalArgumentException("Booking reference not found."));
        if (booking.status.equals("CANCELLED")) throw new IllegalArgumentException("This booking is already cancelled.");
        Flight flight = flightById(booking.flightId).orElseThrow(() -> new IllegalArgumentException("Flight record is missing."));
        double refund = 0;
        boolean wasPaid = booking.status.equals("CONFIRMED") && payments.stream().anyMatch(p -> p.pnr.equals(booking.pnr) && p.kind.equals("PAYMENT") && p.status.equals("PAID"));
        if (wasPaid) {
            long hours = java.time.Duration.between(LocalDateTime.now(), flight.departure).toHours();
            double percent = hours >= 24 ? 0.80 : hours >= 6 ? 0.50 : 0.0;
            refund = Math.round(booking.amount * percent * 100.0) / 100.0;
            payments.add(new Payment("REF-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),booking.pnr,"REFUND","Cancellation refund",refund>0?"SIMULATED_REFUND":"NO_REFUND", "RFD-"+UUID.randomUUID().toString().substring(0,10).toUpperCase(Locale.ROOT),LocalDateTime.now(),refund));
        }
        Booking updated = booking.withStatus("CANCELLED", "Cancelled; simulated refund ₹" + String.format(Locale.ROOT,"%.2f",refund));
        replaceBooking(updated);
        Flight changed = flight;
        if (!booking.seatReleased) {
            int fi = flights.indexOf(flight); changed = flight.withSeats(flight.seatsAvailable + 1); flights.set(fi,changed); updated = updated.withSeatReleased(true); replaceBooking(updated);
        }
        try { saveFlights(); saveBookings(); savePayments(); } catch (IOException e) { replaceBooking(booking); if (changed != flight) { int fi=flights.indexOf(changed); if(fi>=0)flights.set(fi,flight); } throw e; }
        return updated;
    }
    private void replaceBooking(Booking booking) { for (int i=0;i<bookings.size();i++) if(bookings.get(i).id.equals(booking.id)){bookings.set(i,booking);return;} }
    public synchronized List<Payment> getPayments() { return payments.stream().sorted(Comparator.comparing((Payment p)->p.timestamp).reversed()).toList(); }
    public synchronized List<Payment> getPaymentsFor(String pnr) { return payments.stream().filter(p->p.pnr.equalsIgnoreCase(pnr)).sorted(Comparator.comparing((Payment p)->p.timestamp).reversed()).toList(); }
    public synchronized Map<String,Object> dashboard() {
        long confirmed = bookings.stream().filter(b->b.status.equals("CONFIRMED")).count();
        long pending = bookings.stream().filter(b->b.status.equals("PENDING_PAYMENT")).count();
        long cancelled = bookings.stream().filter(b->b.status.equals("CANCELLED")).count();
        double revenue = payments.stream().filter(p->p.kind.equals("PAYMENT")&&p.status.equals("PAID")).mapToDouble(p->p.amount).sum();
        Map<String,Object> result = new LinkedHashMap<>(); result.put("flightCount",flights.size()); result.put("passengerCount",passengers.size()); result.put("bookingCount",bookings.size()); result.put("confirmedCount",confirmed); result.put("pendingCount",pending); result.put("cancelledCount",cancelled); result.put("revenue",Math.round(revenue*100.0)/100.0); result.put("recentBookings",getBookings("").stream().limit(5).map(this::bookingView).toList()); return result;
    }
    public synchronized Map<String,Object> bookingView(Booking b) {
        Map<String,Object> m = new LinkedHashMap<>(); m.put("id",b.id);m.put("pnr",b.pnr);m.put("flightId",b.flightId);m.put("passengerId",b.passengerId);m.put("createdAt",b.createdAt.toString());m.put("status",b.status);m.put("amount",b.amount);m.put("seatReleased",b.seatReleased);m.put("note",b.note);
        flightById(b.flightId).ifPresent(f->m.put("flight",f.toMap())); passengerById(b.passengerId).ifPresent(p->m.put("passenger",p.toMap())); m.put("payments",getPaymentsFor(b.pnr).stream().map(Payment::toMap).toList()); return m;
    }
    public synchronized List<Map<String,Object>> bookingViews(String search) { return getBookings(search).stream().map(this::bookingView).toList(); }
    public synchronized Map<String,Object> bookingViewByPnr(String pnr) { return bookingByPnr(pnr).map(this::bookingView).orElse(null); }
    public synchronized long bookingCount() { return bookings.size(); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public record Flight(String id,String airline,String flightNo,String origin,String destination,LocalDateTime departure,LocalDateTime arrival,double price,int seatsAvailable,String fareClass) {
        Flight withSeats(int seats) { return new Flight(id,airline,flightNo,origin,destination,departure,arrival,price,seats,fareClass); }
        Map<String,Object> toMap() { Map<String,Object> m=new LinkedHashMap<>();m.put("id",id);m.put("airline",airline);m.put("flightNo",flightNo);m.put("origin",origin);m.put("destination",destination);m.put("departure",departure.toString());m.put("arrival",arrival.toString());m.put("price",price);m.put("seatsAvailable",seatsAvailable);m.put("fareClass",fareClass);return m; }
    }
    public record Passenger(String id,String fullName,String email,String phone,String passport) { Map<String,Object> toMap(){Map<String,Object>m=new LinkedHashMap<>();m.put("id",id);m.put("fullName",fullName);m.put("email",email);m.put("phone",phone);m.put("passport",passport);return m;} }
    public record Booking(String id,String pnr,String flightId,String passengerId,LocalDateTime createdAt,String status,double amount,boolean seatReleased,String note) {
        Booking withStatus(String status,String note) { return new Booking(id,pnr,flightId,passengerId,createdAt,status,amount,seatReleased,note); }
        Booking withSeatReleased(boolean value) { return new Booking(id,pnr,flightId,passengerId,createdAt,status,amount,value,note); }
    }
    public record Payment(String id,String pnr,String kind,String method,String status,String reference,LocalDateTime timestamp,double amount) { Map<String,Object> toMap(){Map<String,Object>m=new LinkedHashMap<>();m.put("id",id);m.put("pnr",pnr);m.put("kind",kind);m.put("method",method);m.put("status",status);m.put("reference",reference);m.put("timestamp",timestamp.toString());m.put("amount",amount);return m;} }
}
