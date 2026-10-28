# FlightDesk — Flight Booking System Demo

A small, integrated flight-booking web application built with **Java 17+**, HTML, CSS, and vanilla JavaScript. It uses Java's built-in HTTP server and a lightweight local file store, so it does not require Spring, Maven, a database server, or any paid APIs to run the demo.

## 1. Requirements

- JDK 17 or newer (install a JDK, not only a JRE). Verify with `java -version` and `javac -version`.
- A modern browser such as Chrome, Edge, or Firefox.
- No internet connection is required for application functionality. The UI uses system fonts.

## 2. Run on Windows

1. Extract the ZIP to a normal folder, for example `C:\Projects\FlightBookingDemo`.
2. Open the extracted folder.
3. Double-click **`run.bat`**. Keep the terminal window open while using the application.
4. Open **http://localhost:8080** in your browser.
5. To stop the server, return to the terminal and press **Ctrl+C**.

If Windows says `java` or `javac` is not recognised, install a JDK 17+ and add its `bin` folder to PATH. Reopen the terminal after changing PATH.

## 3. Run on Linux / macOS

From the extracted project directory:

```sh
chmod +x run.sh
./run.sh
```

Then open http://localhost:8080. The script compiles the source and launches the server.

## 4. The four integrated modules

### Module 1 — Flight Search & Fare Selection
- Search sample flight schedules by origin, destination, date, and maximum fare.
- View airline, flight number, departure/arrival, fare class, price, and available seats.
- Select a flight to start a booking.

### Module 2 — Customer & Passenger Management
- Add, list, search, edit, and delete passenger records.
- Required-field and email-format validation.
- Duplicate passenger email addresses are rejected.
- Passenger records linked to booking history cannot be deleted, preserving historical ticket and audit details.

### Module 3 — Booking & PNR Generation
- Select a saved passenger and an available flight.
- Create a reservation with a unique six-character PNR.
- A seat is reserved and the booking starts in `PENDING_PAYMENT` status.

### Module 4 — Payment, Ticket & Booking Management
- Simulate a successful or failed payment; no real money is processed.
- A successful simulated payment changes the booking to `CONFIRMED`.
- Failed payments leave the booking pending so a retry can be demonstrated.
- Search the booking register, inspect ticket details, and print the confirmation/e-ticket view.
- Cancel a booking and record the assumed demonstration refund. The sample refund policy is **80% at least 24 hours before departure, 50% from 6 to under 24 hours, and 0% under 6 hours**. This is only an academic/demo assumption, not an airline policy or financial transaction.

## 5. Suggested live demonstration

1. Open **Passengers** and add a passenger.
2. Open **Flight search**, search a route such as Ahmedabad → Mumbai, and select a flight.
3. Pick the passenger and create the booking. Note the generated PNR.
4. Open **Payments & refunds**, select the PNR, choose a demo method, and click **Simulate success**.
5. Open **Bookings & tickets**, view the booking and print the ticket.
6. Cancel the booking to demonstrate the updated status and a simulated refund record.
7. Repeat with **Simulate failure** to show that a failed payment does not confirm a booking.

## 6. Data storage and reset

Application records are saved in the local `data/` folder (`flights.tsv`, `passengers.tsv`, `bookings.tsv`, and `payments.tsv`) using URL-safe Base64-encoded fields. This avoids requiring a database installation and keeps demo data between runs.

To reset the demo: stop the server, delete the `.tsv` files inside `data/`, then start the app again. The sample flight schedule will be generated for the current date and upcoming days. Passenger, booking, and payment records will be empty after reset.

You can change the listening port with the `PORT` environment variable, or select another local data folder with `FLIGHT_BOOKING_DATA_DIR`.

Windows PowerShell example for a different port:

```powershell
$env:PORT = "8081"
.\run.bat
```

## 7. API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Health check |
| GET | `/api/dashboard` | Dashboard statistics and recent bookings |
| GET | `/api/flights?origin=&destination=&date=&maxPrice=` | Search flights |
| GET / POST / PUT / DELETE | `/api/passengers` | Passenger CRUD |
| GET / POST | `/api/bookings` | List or create bookings |
| GET | `/api/booking?pnr=ABC123` | Look up one booking |
| GET / POST | `/api/payments` | List transactions or simulate a payment |
| POST | `/api/cancellations` | Cancel a booking and record a simulated refund |

POST and PUT requests accept `application/x-www-form-urlencoded` form fields. API responses are JSON.

## 8. Project structure

```text
FlightBookingDemo/
├── run.bat
├── run.sh
├── pom.xml
├── README.md
├── data/                         # Local demo records (created/updated at runtime)
└── src/main/
    ├── java/com/flightbooking/
    │   ├── Main.java             # Local HTTP server and API routes
    │   ├── DataStore.java        # File-backed records and business rules
    │   └── Json.java             # Minimal JSON response writer
    └── resources/web/
        ├── index.html
        └── assets/
            ├── styles.css
            └── app.js
```

## 9. Important scope and security notes

- This is an educational local demo, not a production booking platform.
- It intentionally has no account login or role-based security layer; keep it on the local machine and do not expose the port publicly.
- Flight schedules are simulated; there is no live airline/GDS integration.
- Payments and refunds are simulated status records; do not enter real card or bank details.
- The file-backed store is designed for a single-machine classroom demonstration, not concurrent production traffic or multi-user deployment.
