package com.scholr.lms.discovery;

/**
 * How a query is matched and ranked. The article's core trade-off lives in this enum.
 */
public enum SearchMode {

    /** Lexical: requires the query terms to actually appear. Precise, literal, fast — but blind to synonyms. */
    KEYWORD,

    /** Vector: cosine similarity over embeddings. Finds conceptually related results even with no shared words. */
    SEMANTIC,

    /** Both: combine a lexical score and a semantic score, then rerank. The pragmatic production default. */
    HYBRID
}
