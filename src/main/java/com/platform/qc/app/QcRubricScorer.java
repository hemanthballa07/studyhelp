package com.platform.qc.app;

import com.platform.qc.domain.QcStatus;
import com.platform.qc.domain.RubricScore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Pure rubric scorer, registered as a Spring component so QcService can inject it.
 * Implements 8 dimensions (master-design §7) totalling 100 pts.
 * Bands: ≥75 → PASS, 60–74 → REVISION_REQUESTED, <60 → FAIL.
 */
@Component
public class QcRubricScorer {

    private static final Set<String> UNIT_REQUIRED_SUBJECTS = Set.of("PHYSICS", "CHEMISTRY");

    private static final Pattern UNIT_TOKENS = Pattern.compile(
            "\\b(m/s|m/s²|m/s\\^2|kg|mol|\\bN\\b|\\bJ\\b|Pa|\\bK\\b|°C|\\bL\\b|Newtons?|Joules?|Pascals?|Kelvin|Celsius)");

    private static final Pattern LATEX_PATTERN = Pattern.compile(
            "\\$[^$]+\\$|\\$\\$|\\\\\\[|\\\\frac");

    private static final Pattern STEP_PATTERN = Pattern.compile(
            "(?m)^(\\d+\\.\\s|Step\\s+\\d+|[-*]\\s).{14,}");

    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile(
            "(?i)(therefore|answer:|thus|√|=\\s)");

    private static final Pattern POLICY_VIOLATION_PATTERN = Pattern.compile(
            "(?i)(do your homework|cheat|answer key)");

    public RubricScore score(String body, String subjectCode) {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        List<String> violations = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        dimensions.put("STEP_STRUCTURE", scoreStepStructure(body, suggestions));
        dimensions.put("FINAL_ANSWER", scoreFinalAnswer(body, suggestions));
        dimensions.put("EQUATION_FORMATTING", scoreEquationFormatting(body, suggestions));
        dimensions.put("UNITS", scoreUnits(body, subjectCode, suggestions));
        dimensions.put("EXPLANATION_DEPTH", scoreExplanationDepth(body, suggestions));
        dimensions.put("READABILITY", scoreReadability(body, suggestions));
        dimensions.put("POLICY_COMPLIANCE", scorePolicyCompliance(body, violations, suggestions));
        dimensions.put("SIMILARITY_RISK", 15); // placeholder until Slice 9 corpus

        int total = dimensions.values().stream().mapToInt(Integer::intValue).sum();
        QcStatus status = total >= 75 ? QcStatus.PASS
                : total >= 60 ? QcStatus.REVISION_REQUESTED
                : QcStatus.FAIL;

        return new RubricScore(total, Map.copyOf(dimensions), List.copyOf(violations), List.copyOf(suggestions), status);
    }

    private int scoreStepStructure(String body, List<String> suggestions) {
        long count = STEP_PATTERN.matcher(body).results().count();
        if (count >= 2) return 20;
        if (count == 1) return 10;
        suggestions.add("Add numbered or bulleted steps to structure your solution");
        return 0;
    }

    private int scoreFinalAnswer(String body, List<String> suggestions) {
        if (body.length() < 5) return 0;
        int cutoff = body.length() - Math.max(1, body.length() / 5);
        String tail = body.substring(cutoff);
        if (FINAL_ANSWER_PATTERN.matcher(tail).find()) return 10;
        suggestions.add("Add a clear final answer (e.g. 'Therefore...' or 'Answer:') at the end");
        return 0;
    }

    private int scoreEquationFormatting(String body, List<String> suggestions) {
        if (LATEX_PATTERN.matcher(body).find()) return 10;
        suggestions.add("Use LaTeX formatting ($...$) for mathematical expressions");
        return 0;
    }

    private int scoreUnits(String body, String subjectCode, List<String> suggestions) {
        if (!UNIT_REQUIRED_SUBJECTS.contains(subjectCode)) return 10;
        if (UNIT_TOKENS.matcher(body).find()) return 10;
        suggestions.add("Include physical units (e.g. kg, m/s, N, J) in your answer");
        return 0;
    }

    private int scoreExplanationDepth(String body, List<String> suggestions) {
        String[] words = body.trim().split("\\s+");
        int totalWords = words.length;
        if (totalWords == 0) return 0;

        String[] lines = body.split("\n");
        String lastLine = "";
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].trim().isEmpty()) {
                lastLine = lines[i].trim();
                break;
            }
        }
        int lastLineWords = lastLine.isEmpty() ? 1 : lastLine.split("\\s+").length;
        double ratio = (double) (totalWords - lastLineWords) / Math.max(lastLineWords, 1);

        if (ratio >= 3) return 15;
        if (ratio >= 2) return 10;
        if (ratio >= 1) return 5;
        suggestions.add("Expand the explanation — add more steps before stating the final answer");
        return 0;
    }

    private int scoreReadability(String body, List<String> suggestions) {
        if (body.length() < 100) {
            suggestions.add("Expand the answer to at least 100 characters");
            return 5;
        }
        boolean wallOfText = Arrays.stream(body.split("\n"))
                .anyMatch(line -> line.length() > 200);
        if (wallOfText) {
            suggestions.add("Break long paragraphs into shorter lines for readability");
            return 5;
        }
        return 10;
    }

    private int scorePolicyCompliance(String body, List<String> violations, List<String> suggestions) {
        var matcher = POLICY_VIOLATION_PATTERN.matcher(body);
        if (matcher.find()) {
            violations.add("policy violation detected: '" + matcher.group() + "'");
            suggestions.add("Remove prohibited content (answer keys, cheating references)");
            return 0;
        }
        return 10;
    }
}
