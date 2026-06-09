package com.platform.ai.app;

import com.platform.ai.domain.CorpusRepository;
import com.platform.shared.embedding.EmbeddingPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the AI retrieval corpus from a static set of open-licensed study material chunks.
 * Source: OpenStax Physics 3e / Algebra and Trigonometry (CC BY 4.0).
 * In production call {@code seed()} once at startup if {@code corpusSize() == 0}.
 */
@Service
public class CorpusIngestionService {

    public static final String SOURCE = "OpenStax Physics 3e";
    public static final String LICENSE = "CC BY 4.0";

    // Deterministic IDs so re-seeding is idempotent (ON CONFLICT DO UPDATE).
    public static final List<SeedChunk> SEED_CHUNKS = List.of(
        new SeedChunk(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Newton's second law states that the net force acting on an object equals the product " +
            "of its mass and acceleration: F = ma. When the net force is zero the object is in " +
            "equilibrium and acceleration is zero."
        ),
        new SeedChunk(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "Kinetic energy is the energy possessed by an object due to its motion. For an object " +
            "of mass m moving at speed v, kinetic energy KE = (1/2)mv^2. Work done on an object " +
            "equals the change in its kinetic energy (work-energy theorem)."
        ),
        new SeedChunk(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            "The quadratic formula gives the solutions to ax^2 + bx + c = 0: " +
            "x = (-b ± sqrt(b^2 - 4ac)) / (2a). The discriminant b^2 - 4ac determines the " +
            "nature of the roots: positive means two real roots, zero means one repeated root, " +
            "negative means two complex roots."
        ),
        new SeedChunk(
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            "Gravitational potential energy near Earth's surface is PE = mgh, where m is mass, " +
            "g ≈ 9.8 m/s^2 is gravitational acceleration, and h is height above a reference level. " +
            "Conservation of mechanical energy: KE + PE = constant (no non-conservative forces)."
        ),
        new SeedChunk(
            UUID.fromString("00000000-0000-0000-0000-000000000005"),
            "Ohm's law: the current I through a conductor is proportional to the voltage V across " +
            "it: V = IR, where R is resistance in ohms. Power dissipated: P = IV = I^2 R = V^2/R. " +
            "Resistors in series: R_total = R1 + R2 + ... Resistors in parallel: 1/R_total = 1/R1 + 1/R2."
        ),
        new SeedChunk(
            UUID.fromString("00000000-0000-0000-0000-000000000006"),
            "Trigonometric identities: sin^2(theta) + cos^2(theta) = 1. " +
            "In a right triangle with angle theta: sin(theta) = opposite/hypotenuse, " +
            "cos(theta) = adjacent/hypotenuse, tan(theta) = opposite/adjacent. " +
            "The law of sines: a/sin(A) = b/sin(B) = c/sin(C)."
        ),
        new SeedChunk(
            UUID.fromString("00000000-0000-0000-0000-000000000007"),
            "Momentum p = mv is conserved in an isolated system. For a collision: " +
            "m1*v1_i + m2*v2_i = m1*v1_f + m2*v2_f. Elastic collisions also conserve kinetic energy. " +
            "Impulse J = F*delta_t = delta_p changes an object's momentum."
        ),
        new SeedChunk(
            UUID.fromString("00000000-0000-0000-0000-000000000008"),
            "Ideal gas law: PV = nRT, where P is pressure, V is volume, n is moles, " +
            "R = 8.314 J/(mol*K) is the universal gas constant, and T is absolute temperature in kelvin. " +
            "At constant temperature Boyle's law gives P1V1 = P2V2."
        )
    );

    private final CorpusRepository repo;
    private final EmbeddingPort embeddingPort;

    public CorpusIngestionService(CorpusRepository repo, EmbeddingPort embeddingPort) {
        this.repo = repo;
        this.embeddingPort = embeddingPort;
    }

    /** Inserts all seed chunks; safe to call repeatedly (upsert by id). */
    public void seed() {
        // Embeddings computed before the transaction so a model failure does not roll back
        // already-written rows and the DB connection is not held during inference.
        List<float[]> embeddings = SEED_CHUNKS.stream()
                .map(sc -> embeddingPort.embed(sc.text()))
                .toList();
        persistSeed(embeddings);
    }

    @Transactional
    public void persistSeed(List<float[]> embeddings) {
        for (int i = 0; i < SEED_CHUNKS.size(); i++) {
            SeedChunk sc = SEED_CHUNKS.get(i);
            repo.upsertChunk(sc.id(), SOURCE, LICENSE, sc.text(), embeddings.get(i));
        }
    }

    public record SeedChunk(UUID id, String text) {}
}
