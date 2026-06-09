package com.platform.shared.math;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Production SymPy sidecar: shells out to python3 to verify mathematical expressions.
 * Extracts equation-like patterns from the candidate final answer, checks with sympy.simplify,
 * and returns 1.0 (valid), 0.0 (invalid), or 0.5 (not applicable / error).
 * Active outside the test profile.
 */
@Service
@Profile("!test")
public class ProductionMathVerifierAdapter implements MathVerifierPort {

    private static final Logger log = LoggerFactory.getLogger(ProductionMathVerifierAdapter.class);

    // Matches "expr1 = expr2" on a single line; excludes newlines to avoid breaking Python literals.
    private static final Pattern EQUATION_PATTERN = Pattern.compile(
            "([\\w +\\-*/^(){}\\[\\].,]+?)\\s*=\\s*([\\w +\\-*/^(){}\\[\\].,]+)");

    private static final int TIMEOUT_SECONDS = 5;

    @Override
    public double verify(String questionText, String candidateFinalAnswer) {
        String equation = extractEquation(candidateFinalAnswer);
        if (equation == null) {
            return 0.5; // no verifiable equation found
        }
        return runSymPyCheck(equation);
    }

    private String extractEquation(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = EQUATION_PATTERN.matcher(text);
        return m.find() ? m.group(0) : null;
    }

    private double runSymPyCheck(String equation) {
        // Build a minimal Python snippet that checks if LHS - RHS simplifies to 0.
        // If sympy is unavailable or the parse fails, returns 0.5 (neutral).
        String[] parts = equation.split("=", 2);
        if (parts.length != 2) return 0.5;
        String lhs = parts[0].trim();
        String rhs = parts[1].trim();

        String script = String.format(
                "from sympy import sympify, simplify%n"
                + "try:%n"
                + "    lhs = sympify('%s')%n"
                + "    rhs = sympify('%s')%n"
                + "    diff = simplify(lhs - rhs)%n"
                + "    print('1' if diff == 0 else '0')%n"
                + "except Exception:%n"
                + "    print('0.5')%n",
                lhs.replace("'", "\\'"), rhs.replace("'", "\\'"));

        try {
            Process process = new ProcessBuilder("python3", "-c", script)
                    .redirectErrorStream(true) // merge stderr so the pipe never blocks
                    .start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("SymPy check timed out for equation: {}", equation);
                return 0.5;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String output = reader.readLine();
                if ("1".equals(output)) return 1.0;
                if ("0".equals(output)) return 0.0;
                return 0.5;
            }
        } catch (Exception e) {
            log.warn("SymPy verification failed: {}", e.getMessage());
            return 0.5;
        }
    }
}
