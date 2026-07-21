package wfederico.pneumacare.shared.demo;

import java.time.LocalDate;
import java.util.List;

/**
 * Static, curated demo dataset. Pure data — no Spring, no DB. Each patient has a
 * fixed name/DOB and an ordered list of evaluations (oldest first); the last
 * evaluation is the latest and drives the demoed weaning verdict.
 *
 * <p>Parameter values are hand-tuned to land specific RSBI/PaFi/Cstat bands; the
 * mapping is guarded by {@code DemoScenariosTest}.
 */
public final class DemoScenarios {

    private DemoScenarios() {}

    /** One ventilator evaluation. dayOffset is relative to seed time (negative = past). */
    public record Reading(int dayOffset, double f, double vt, double pao2,
                          double fio2, double pplat, double peep) {}

    /** One demo patient with fixed PII and an ordered evaluation series. */
    public record Patient(String firstName, String lastName, LocalDate birthDate,
                          List<Reading> readings) {}

    public static List<Patient> patients() {
        return List.of(
            new Patient("Carlos", "Giménez", LocalDate.of(1958, 3, 12), List.of(
                new Reading(-2, 34, 230, 65, 1.00, 35, 14),
                new Reading(-1, 32, 240, 72, 1.00, 33, 13),
                new Reading( 0, 30, 250, 80, 1.00, 32, 12))),
            new Patient("Marta", "Rossi", LocalDate.of(1967, 7, 4), List.of(
                new Reading(-2, 39, 390, 220, 1.00, 23, 16),
                new Reading(-1, 37, 395, 235, 1.00, 22, 15),
                new Reading( 0, 36, 400, 250, 1.00, 22, 15))),
            new Patient("Jorge", "Delgado", LocalDate.of(1972, 11, 28), List.of(
                new Reading(-2, 25, 450, 240, 0.60, 19, 10),
                new Reading(-1, 22, 470, 200, 0.50, 18,  9),
                new Reading( 0, 18, 500, 170, 0.40, 17,  8))),
            new Patient("Lucía", "Fernández", LocalDate.of(1980, 1, 19), List.of(
                new Reading(-2, 26, 330, 305, 1.00, 37, 10),
                new Reading(-1, 24, 340, 310, 1.00, 36, 10),
                new Reading( 0, 22, 350, 320, 1.00, 35, 10))),
            new Patient("Roberto", "Ibáñez", LocalDate.of(1961, 9, 8), List.of(
                new Reading(-2, 24, 460, 130, 1.00, 17, 8),
                new Reading(-1, 22, 480, 140, 1.00, 17, 8),
                new Reading( 0, 20, 500, 150, 1.00, 18, 8))),
            new Patient("Elena", "Morales", LocalDate.of(1975, 5, 30), List.of(
                new Reading(-2, 20, 520, 220, 0.50, 13, 6),
                new Reading(-1, 18, 540, 210, 0.45, 12, 6),
                new Reading( 0, 16, 550, 200, 0.40, 12, 7)))
        );
    }
}
