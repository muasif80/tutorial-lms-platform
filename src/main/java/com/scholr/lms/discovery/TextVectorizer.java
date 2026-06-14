package com.scholr.lms.discovery;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Turns text into a sparse term-frequency vector for cosine-similarity (semantic) search. It is a
 * <strong>deterministic stand-in</strong> for a real embedding model: same text in, same vector out,
 * with no network call and no randomness, so the whole search stack is testable without a GPU or an
 * embedding API.
 *
 * <p>In production you replace this one bean with a call to an embedding model (Sentence-Transformers,
 * an embeddings API, etc.) that maps text into a dense semantic space where synonyms land near each
 * other. Nothing above this seam changes — {@link SearchService} just asks for "the vector of this
 * text" and computes cosine similarity, exactly as it does here. Isolating the embedding behind one
 * component is what lets you start with bag-of-words and upgrade to true semantics later without a
 * rewrite, the same way {@code InMemoryEventPublisher} stands in for Kafka in Part 5.
 */
@Component
public class TextVectorizer {

    /** A sparse vector: dimension (a hashed token) → weight (term frequency). */
    public Map<Integer, Double> vectorize(String text) {
        Map<Integer, Double> vec = new HashMap<>();
        if (text == null || text.isBlank()) {
            return vec;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() < 2) {
                continue; // drop noise tokens
            }
            // Use the full hash space as the dimension key. A small modulo would make unrelated tokens
            // collide and fabricate spurious similarity; the wide space keeps unrelated documents truly
            // orthogonal (cosine 0), which is what a real hashing vectorizer relies on.
            int dim = token.hashCode();
            vec.merge(dim, 1.0, Double::sum);
        }
        return vec;
    }

    /** Cosine similarity in [0,1] between two sparse vectors; 0 if either is empty. */
    public double cosine(Map<Integer, Double> a, Map<Integer, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        // iterate the smaller map for the dot product
        Map<Integer, Double> small = a.size() <= b.size() ? a : b;
        Map<Integer, Double> large = small == a ? b : a;
        double dot = 0.0;
        for (Map.Entry<Integer, Double> e : small.entrySet()) {
            Double other = large.get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }
        return dot / (norm(a) * norm(b));
    }

    private double norm(Map<Integer, Double> v) {
        double sum = 0.0;
        for (double x : v.values()) {
            sum += x * x;
        }
        return Math.sqrt(sum);
    }
}
