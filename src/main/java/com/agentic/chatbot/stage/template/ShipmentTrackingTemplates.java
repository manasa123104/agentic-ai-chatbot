package com.agentic.chatbot.stage.template;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Offline logistics sample: shipment tracking (plain Java 17) and its JUnit 5 tests.
 */
final class ShipmentTrackingTemplates {

    static final String PKG = "com.logistics.tracking";
    private static final String MAIN = "src/main/java/com/logistics/tracking/";
    private static final String TEST = "src/test/java/com/logistics/tracking/";

    private ShipmentTrackingTemplates() {
    }

    static Map<String, String> code() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(MAIN + "ShipmentStatus.java", SHIPMENT_STATUS);
        files.put(MAIN + "TrackingEvent.java", TRACKING_EVENT);
        files.put(MAIN + "Shipment.java", SHIPMENT);
        files.put(MAIN + "TrackingException.java", TRACKING_EXCEPTION);
        files.put(MAIN + "ShipmentRepository.java", SHIPMENT_REPOSITORY);
        files.put(MAIN + "ShipmentTrackingService.java", SHIPMENT_TRACKING_SERVICE);
        return files;
    }

    static Map<String, String> tests() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(TEST + "ShipmentStatusTest.java", SHIPMENT_STATUS_TEST);
        files.put(TEST + "ShipmentTrackingServiceTest.java", SHIPMENT_TRACKING_SERVICE_TEST);
        return files;
    }

    private static final String SHIPMENT_STATUS = """
            package com.logistics.tracking;

            import java.util.EnumSet;
            import java.util.Set;

            /**
             * Where a shipment is in its journey. Each status lists the statuses it may move to next,
             * so scans that arrive out of order are refused.
             */
            public enum ShipmentStatus {
                CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, EXCEPTION, RETURNED, CANCELLED;

                /**
                 * Business rule: the allowed lifecycle is CREATED, PICKED_UP, IN_TRANSIT (repeatable hub scans),
                 * OUT_FOR_DELIVERY, DELIVERED. Problems go to EXCEPTION, which resumes transit or returns to sender.
                 *
                 * @return statuses this status may move to
                 */
                public Set<ShipmentStatus> allowedNext() {
                    return switch (this) {
                        case CREATED -> EnumSet.of(PICKED_UP, CANCELLED);
                        case PICKED_UP -> EnumSet.of(IN_TRANSIT, EXCEPTION);
                        case IN_TRANSIT -> EnumSet.of(IN_TRANSIT, OUT_FOR_DELIVERY, EXCEPTION);
                        case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED, EXCEPTION);
                        case EXCEPTION -> EnumSet.of(IN_TRANSIT, RETURNED);
                        case DELIVERED, RETURNED, CANCELLED -> EnumSet.noneOf(ShipmentStatus.class);
                    };
                }

                /**
                 * @param next proposed next status
                 * @return true when the move is allowed
                 */
                public boolean canMoveTo(ShipmentStatus next) {
                    return allowedNext().contains(next);
                }

                /** @return true when no further scans are accepted */
                public boolean isFinal() {
                    return allowedNext().isEmpty();
                }
            }
            """;

    private static final String TRACKING_EVENT = """
            package com.logistics.tracking;

            import java.time.Instant;

            /**
             * One scan in a shipment's history.
             *
             * @param status   status after the scan
             * @param location where the scan happened, e.g. a hub or city
             * @param at       when it happened (UTC)
             * @param note     optional note; required for exceptions
             */
            public record TrackingEvent(ShipmentStatus status, String location, Instant at, String note) {
            }
            """;

    private static final String SHIPMENT = """
            package com.logistics.tracking;

            import java.time.LocalDate;
            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.List;

            /**
             * A parcel or consignment being moved from sender to recipient, with its full scan history.
             */
            public class Shipment {

                private final String trackingNumber;
                private final String sender;
                private final String recipient;
                private final String origin;
                private final String destination;
                private final List<TrackingEvent> events = new ArrayList<>();
                private LocalDate estimatedDelivery;

                /**
                 * @param trackingNumber    unique tracking number
                 * @param sender            who sends the shipment
                 * @param recipient         who receives it
                 * @param origin            pickup city or hub
                 * @param destination       delivery city or address
                 * @param estimatedDelivery promised delivery date
                 * @param created           first event, normally CREATED at the origin
                 */
                public Shipment(String trackingNumber, String sender, String recipient, String origin, String destination,
                                LocalDate estimatedDelivery, TrackingEvent created) {
                    this.trackingNumber = trackingNumber;
                    this.sender = sender;
                    this.recipient = recipient;
                    this.origin = origin;
                    this.destination = destination;
                    this.estimatedDelivery = estimatedDelivery;
                    this.events.add(created);
                }

                /** @return the tracking number */
                public String getTrackingNumber() {
                    return trackingNumber;
                }

                /** @return the sender */
                public String getSender() {
                    return sender;
                }

                /** @return the recipient */
                public String getRecipient() {
                    return recipient;
                }

                /** @return pickup location */
                public String getOrigin() {
                    return origin;
                }

                /** @return delivery location */
                public String getDestination() {
                    return destination;
                }

                /** @return promised delivery date */
                public LocalDate getEstimatedDelivery() {
                    return estimatedDelivery;
                }

                /** @return all scans, oldest first (read-only) */
                public List<TrackingEvent> getEvents() {
                    return Collections.unmodifiableList(events);
                }

                /** @return status of the latest scan */
                public ShipmentStatus getCurrentStatus() {
                    return events.get(events.size() - 1).status();
                }

                /** @return location of the latest scan */
                public String getCurrentLocation() {
                    return events.get(events.size() - 1).location();
                }

                void addEvent(TrackingEvent event) {
                    events.add(event);
                }

                void setEstimatedDelivery(LocalDate estimatedDelivery) {
                    this.estimatedDelivery = estimatedDelivery;
                }
            }
            """;

    private static final String TRACKING_EXCEPTION = """
            package com.logistics.tracking;

            /**
             * Raised when a booking, scan or tracking lookup breaks a business rule.
             */
            public class TrackingException extends RuntimeException {

                /** Why the request was refused. */
                public enum Reason {
                    INVALID_INPUT,
                    INVALID_TRACKING_NUMBER,
                    NOT_FOUND,
                    INVALID_TRANSITION
                }

                private final Reason reason;

                /**
                 * @param reason  machine-readable reason
                 * @param message message safe to show to customers and dispatchers
                 */
                public TrackingException(Reason reason, String message) {
                    super(message);
                    this.reason = reason;
                }

                /** @return why the request was refused */
                public Reason getReason() {
                    return reason;
                }
            }
            """;

    private static final String SHIPMENT_REPOSITORY = """
            package com.logistics.tracking;

            import java.util.Map;
            import java.util.Optional;
            import java.util.concurrent.ConcurrentHashMap;

            /**
             * In-memory shipment store. Replace with a database repository in production.
             */
            public class ShipmentRepository {

                private final Map<String, Shipment> shipments = new ConcurrentHashMap<>();

                /**
                 * @param shipment shipment to store
                 * @return the stored shipment
                 */
                public Shipment save(Shipment shipment) {
                    shipments.put(shipment.getTrackingNumber(), shipment);
                    return shipment;
                }

                /**
                 * @param trackingNumber tracking number to look up
                 * @return the shipment, or empty when unknown
                 */
                public Optional<Shipment> find(String trackingNumber) {
                    return Optional.ofNullable(shipments.get(trackingNumber));
                }
            }
            """;

    private static final String SHIPMENT_TRACKING_SERVICE = """
            package com.logistics.tracking;

            import java.time.Clock;
            import java.time.Instant;
            import java.time.LocalDate;
            import java.util.regex.Pattern;

            /**
             * Books shipments, records scan events in the allowed order and answers tracking lookups.
             */
            public class ShipmentTrackingService {

                /** Business rule: tracking numbers are LG followed by exactly 10 digits. */
                private static final Pattern TRACKING_NUMBER = Pattern.compile("LG[0-9]{10}");
                /** Business rule: an exception delays the promised delivery date by this many days. */
                public static final int EXCEPTION_DELAY_DAYS = 2;

                private final ShipmentRepository repository;
                private final Clock clock;
                private long sequence;

                /**
                 * @param repository where shipments are stored
                 * @param clock      time source; inject a fixed clock in tests
                 */
                public ShipmentTrackingService(ShipmentRepository repository, Clock clock) {
                    this.repository = repository;
                    this.clock = clock;
                }

                /** @param repository where shipments are stored; uses the UTC system clock */
                public ShipmentTrackingService(ShipmentRepository repository) {
                    this(repository, Clock.systemUTC());
                }

                /**
                 * Books a shipment and issues its tracking number.
                 *
                 * @param sender      who sends it
                 * @param recipient   who receives it
                 * @param origin      pickup city or hub
                 * @param destination delivery city or address
                 * @param transitDays promised transit time in days (1 to 30)
                 * @return the new shipment in status CREATED
                 */
                public synchronized Shipment book(String sender, String recipient, String origin, String destination,
                                                  int transitDays) {
                    // Business rule: every party and both locations are mandatory for a valid waybill.
                    require(sender, "Sender is required");
                    require(recipient, "Recipient is required");
                    require(origin, "Origin is required");
                    require(destination, "Destination is required");
                    // Business rule: promised transit time must be between 1 and 30 days.
                    if (transitDays < 1 || transitDays > 30) {
                        throw new TrackingException(TrackingException.Reason.INVALID_INPUT, "Transit days must be between 1 and 30");
                    }
                    String trackingNumber = "LG" + String.format("%010d", ++sequence);
                    TrackingEvent created = new TrackingEvent(ShipmentStatus.CREATED, origin.trim(), Instant.now(clock), "Booked");
                    Shipment shipment = new Shipment(trackingNumber, sender.trim(), recipient.trim(), origin.trim(),
                            destination.trim(), LocalDate.now(clock).plusDays(transitDays), created);
                    return repository.save(shipment);
                }

                /**
                 * Records a scan and moves the shipment to a new status.
                 *
                 * @param trackingNumber shipment to update
                 * @param status         new status
                 * @param location       where the scan happened
                 * @param note           note; required for EXCEPTION
                 * @return the updated shipment
                 */
                public synchronized Shipment recordScan(String trackingNumber, ShipmentStatus status, String location, String note) {
                    Shipment shipment = track(trackingNumber);
                    ShipmentStatus current = shipment.getCurrentStatus();
                    // Business rule: statuses can only move along the allowed lifecycle; final statuses accept no scans.
                    if (status == null || !current.canMoveTo(status)) {
                        throw new TrackingException(TrackingException.Reason.INVALID_TRANSITION,
                                "Cannot move shipment from " + current + " to " + status);
                    }
                    require(location, "Location is required");
                    // Business rule: an exception must say what went wrong so customer service can act on it.
                    if (status == ShipmentStatus.EXCEPTION && (note == null || note.isBlank())) {
                        throw new TrackingException(TrackingException.Reason.INVALID_INPUT,
                                "An exception scan needs a note explaining the problem");
                    }
                    shipment.addEvent(new TrackingEvent(status, location.trim(), Instant.now(clock), note == null ? "" : note.trim()));
                    // Business rule: exceptions push the promised delivery date so customers get an honest ETA.
                    if (status == ShipmentStatus.EXCEPTION) {
                        shipment.setEstimatedDelivery(shipment.getEstimatedDelivery().plusDays(EXCEPTION_DELAY_DAYS));
                    }
                    return shipment;
                }

                /**
                 * Looks up a shipment by tracking number.
                 *
                 * @param trackingNumber tracking number entered by the customer
                 * @return the shipment with its history
                 */
                public Shipment track(String trackingNumber) {
                    String number = trackingNumber == null ? "" : trackingNumber.trim().toUpperCase();
                    if (!TRACKING_NUMBER.matcher(number).matches()) {
                        throw new TrackingException(TrackingException.Reason.INVALID_TRACKING_NUMBER,
                                "Tracking number must be LG followed by 10 digits");
                    }
                    return repository.find(number).orElseThrow(() -> new TrackingException(
                            TrackingException.Reason.NOT_FOUND, "Shipment not found: " + number));
                }

                /**
                 * Business rule: a shipment is delayed when it is not finished and its promised date has passed.
                 *
                 * @param trackingNumber shipment to check
                 * @param asOf           the date to compare with
                 * @return true when the shipment is late
                 */
                public boolean isDelayed(String trackingNumber, LocalDate asOf) {
                    Shipment shipment = track(trackingNumber);
                    return !shipment.getCurrentStatus().isFinal() && asOf.isAfter(shipment.getEstimatedDelivery());
                }

                private static void require(String value, String message) {
                    if (value == null || value.isBlank()) {
                        throw new TrackingException(TrackingException.Reason.INVALID_INPUT, message);
                    }
                }
            }
            """;

    private static final String SHIPMENT_STATUS_TEST = """
            package com.logistics.tracking;

            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.assertFalse;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            /**
             * Unit tests for the shipment lifecycle rules in {@link ShipmentStatus}.
             */
            class ShipmentStatusTest {

                @Test
                void normalJourneyIsAllowed() {
                    assertTrue(ShipmentStatus.CREATED.canMoveTo(ShipmentStatus.PICKED_UP));
                    assertTrue(ShipmentStatus.PICKED_UP.canMoveTo(ShipmentStatus.IN_TRANSIT));
                    assertTrue(ShipmentStatus.IN_TRANSIT.canMoveTo(ShipmentStatus.IN_TRANSIT));
                    assertTrue(ShipmentStatus.IN_TRANSIT.canMoveTo(ShipmentStatus.OUT_FOR_DELIVERY));
                    assertTrue(ShipmentStatus.OUT_FOR_DELIVERY.canMoveTo(ShipmentStatus.DELIVERED));
                }

                @Test
                void skippingStatusesIsRefused() {
                    assertFalse(ShipmentStatus.CREATED.canMoveTo(ShipmentStatus.DELIVERED));
                    assertFalse(ShipmentStatus.PICKED_UP.canMoveTo(ShipmentStatus.OUT_FOR_DELIVERY));
                }

                @Test
                void deliveredReturnedAndCancelledAreFinal() {
                    assertTrue(ShipmentStatus.DELIVERED.isFinal());
                    assertTrue(ShipmentStatus.RETURNED.isFinal());
                    assertTrue(ShipmentStatus.CANCELLED.isFinal());
                    assertFalse(ShipmentStatus.EXCEPTION.isFinal());
                }
            }
            """;

    private static final String SHIPMENT_TRACKING_SERVICE_TEST = """
            package com.logistics.tracking;

            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.DisplayName;
            import org.junit.jupiter.api.Test;

            import java.time.Clock;
            import java.time.Instant;
            import java.time.LocalDate;
            import java.time.ZoneOffset;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertFalse;
            import static org.junit.jupiter.api.Assertions.assertThrows;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            /**
             * Unit tests for {@link ShipmentTrackingService}, one per acceptance criterion of the tracking stories.
             */
            class ShipmentTrackingServiceTest {

                private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

                private ShipmentTrackingService service;

                @BeforeEach
                void setUp() {
                    Clock fixed = Clock.fixed(Instant.parse("2026-01-15T08:00:00Z"), ZoneOffset.UTC);
                    service = new ShipmentTrackingService(new ShipmentRepository(), fixed);
                }

                private Shipment booked() {
                    return service.book("Acme Ltd", "Jane Doe", "Mumbai", "Pune", 3);
                }

                private TrackingException.Reason refused(Runnable action) {
                    return assertThrows(TrackingException.class, action::run).getReason();
                }

                @Test
                @DisplayName("Booking issues an LG tracking number, a CREATED event and an ETA")
                void booksShipmentWithTrackingNumber() {
                    Shipment shipment = booked();

                    assertEquals("LG0000000001", shipment.getTrackingNumber());
                    assertEquals(ShipmentStatus.CREATED, shipment.getCurrentStatus());
                    assertEquals("Mumbai", shipment.getCurrentLocation());
                    assertEquals(TODAY.plusDays(3), shipment.getEstimatedDelivery());
                }

                @Test
                @DisplayName("Booking without a destination or with bad transit days is rejected")
                void rejectsIncompleteBooking() {
                    TrackingException e = assertThrows(TrackingException.class,
                            () -> service.book("Acme Ltd", "Jane Doe", "Mumbai", " ", 3));
                    assertEquals("Destination is required", e.getMessage());
                    assertEquals(TrackingException.Reason.INVALID_INPUT,
                            refused(() -> service.book("Acme Ltd", "Jane Doe", "Mumbai", "Pune", 0)));
                }

                @Test
                @DisplayName("Normal journey ends DELIVERED with 5 events")
                void followsTheNormalJourney() {
                    String id = booked().getTrackingNumber();
                    service.recordScan(id, ShipmentStatus.PICKED_UP, "Mumbai", "");
                    service.recordScan(id, ShipmentStatus.IN_TRANSIT, "Lonavala hub", "");
                    service.recordScan(id, ShipmentStatus.OUT_FOR_DELIVERY, "Pune", "");
                    Shipment delivered = service.recordScan(id, ShipmentStatus.DELIVERED, "Pune", "Signed by J. Doe");

                    assertEquals(ShipmentStatus.DELIVERED, delivered.getCurrentStatus());
                    assertEquals(5, delivered.getEvents().size());
                }

                @Test
                @DisplayName("Skipping a status is rejected and DELIVERED is final")
                void rejectsInvalidTransitions() {
                    String id = booked().getTrackingNumber();
                    assertEquals(TrackingException.Reason.INVALID_TRANSITION,
                            refused(() -> service.recordScan(id, ShipmentStatus.DELIVERED, "Pune", "")));

                    service.recordScan(id, ShipmentStatus.PICKED_UP, "Mumbai", "");
                    service.recordScan(id, ShipmentStatus.IN_TRANSIT, "Lonavala hub", "");
                    service.recordScan(id, ShipmentStatus.OUT_FOR_DELIVERY, "Pune", "");
                    service.recordScan(id, ShipmentStatus.DELIVERED, "Pune", "");
                    assertEquals(TrackingException.Reason.INVALID_TRANSITION,
                            refused(() -> service.recordScan(id, ShipmentStatus.IN_TRANSIT, "Pune", "")));
                }

                @Test
                @DisplayName("An exception needs a note and pushes the ETA by 2 days")
                void exceptionNeedsNoteAndDelaysEta() {
                    String id = booked().getTrackingNumber();
                    service.recordScan(id, ShipmentStatus.PICKED_UP, "Mumbai", "");
                    assertEquals(TrackingException.Reason.INVALID_INPUT,
                            refused(() -> service.recordScan(id, ShipmentStatus.EXCEPTION, "Mumbai", " ")));

                    Shipment shipment = service.recordScan(id, ShipmentStatus.EXCEPTION, "Mumbai", "Address not found");
                    assertEquals(TODAY.plusDays(3 + ShipmentTrackingService.EXCEPTION_DELAY_DAYS), shipment.getEstimatedDelivery());
                }

                @Test
                @DisplayName("Malformed and unknown tracking numbers are reported")
                void rejectsBadTrackingNumbers() {
                    assertEquals(TrackingException.Reason.INVALID_TRACKING_NUMBER, refused(() -> service.track("ABC123")));
                    TrackingException e = assertThrows(TrackingException.class, () -> service.track("LG9999999999"));
                    assertEquals(TrackingException.Reason.NOT_FOUND, e.getReason());
                    assertEquals("Shipment not found: LG9999999999", e.getMessage());
                }

                @Test
                @DisplayName("A shipment past its ETA and not delivered is delayed")
                void reportsDelayedShipments() {
                    String id = booked().getTrackingNumber();
                    assertFalse(service.isDelayed(id, TODAY.plusDays(3)));
                    assertTrue(service.isDelayed(id, TODAY.plusDays(4)));
                }
            }
            """;
}
