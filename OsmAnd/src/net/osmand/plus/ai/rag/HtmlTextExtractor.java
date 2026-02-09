package net.osmand.plus.ai.rag;

import android.text.Html;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

/**
 * LAMPP Phase 7: Extracts plain text from Wikipedia HTML content.
 *
 * Removes navigation elements, infoboxes, scripts, styles, and other
 * non-content elements to produce clean text for LLM context.
 */
public class HtmlTextExtractor {

    // Patterns for removing unwanted HTML elements with their content
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE_PATTERN = Pattern.compile(
            "<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NAV_PATTERN = Pattern.compile(
            "<nav[^>]*>.*?</nav>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FOOTER_PATTERN = Pattern.compile(
            "<footer[^>]*>.*?</footer>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "<header[^>]*>.*?</header>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Wikipedia-specific elements to remove
    private static final Pattern INFOBOX_PATTERN = Pattern.compile(
            "<table[^>]*class=\"[^\"]*infobox[^\"]*\"[^>]*>.*?</table>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NAVBOX_PATTERN = Pattern.compile(
            "<div[^>]*class=\"[^\"]*navbox[^\"]*\"[^>]*>.*?</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TOC_PATTERN = Pattern.compile(
            "<div[^>]*id=\"toc\"[^>]*>.*?</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SIDEBAR_PATTERN = Pattern.compile(
            "<div[^>]*class=\"[^\"]*sidebar[^\"]*\"[^>]*>.*?</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HATNOTE_PATTERN = Pattern.compile(
            "<div[^>]*class=\"[^\"]*hatnote[^\"]*\"[^>]*>.*?</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Tables are often data tables, remove them
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "<table[^>]*>.*?</table>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Reference markers like [1], [2], etc.
    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "\\[\\d+\\]|\\[citation needed\\]|\\[clarification needed\\]",
            Pattern.CASE_INSENSITIVE);

    // Multiple whitespace normalization
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("[ \\t]+");
    private static final Pattern MULTI_NEWLINE_PATTERN = Pattern.compile("\\n{3,}");

    // Characters per token estimate
    private static final int CHARS_PER_TOKEN = 4;

    /**
     * Extract plain text from HTML content.
     *
     * @param html The HTML content from Wikipedia
     * @return Clean plain text suitable for LLM context
     */
    @NonNull
    public String extractText(@NonNull String html) {
        if (html.isEmpty()) {
            return "";
        }

        String text = html;

        // Remove unwanted elements with content
        text = SCRIPT_PATTERN.matcher(text).replaceAll("");
        text = STYLE_PATTERN.matcher(text).replaceAll("");
        text = NAV_PATTERN.matcher(text).replaceAll("");
        text = FOOTER_PATTERN.matcher(text).replaceAll("");
        text = HEADER_PATTERN.matcher(text).replaceAll("");

        // Remove Wikipedia-specific elements
        text = INFOBOX_PATTERN.matcher(text).replaceAll("");
        text = NAVBOX_PATTERN.matcher(text).replaceAll("");
        text = TOC_PATTERN.matcher(text).replaceAll("");
        text = SIDEBAR_PATTERN.matcher(text).replaceAll("");
        text = HATNOTE_PATTERN.matcher(text).replaceAll("");
        text = TABLE_PATTERN.matcher(text).replaceAll("");

        // Convert HTML to plain text using Android's Html utility
        text = Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString();

        // Remove reference markers
        text = REFERENCE_PATTERN.matcher(text).replaceAll("");

        // Normalize whitespace
        text = MULTI_SPACE_PATTERN.matcher(text).replaceAll(" ");
        text = MULTI_NEWLINE_PATTERN.matcher(text).replaceAll("\n\n");

        // Trim leading/trailing whitespace
        text = text.trim();

        return text;
    }

    /**
     * Extract text and truncate to a maximum number of tokens.
     * Truncates at sentence boundaries when possible.
     *
     * @param html The HTML content
     * @param maxTokens Maximum number of tokens (estimated)
     * @return Truncated plain text
     */
    @NonNull
    public String extractText(@NonNull String html, int maxTokens) {
        String text = extractText(html);
        return truncateToTokens(text, maxTokens);
    }

    /**
     * Truncate text to approximately the specified number of tokens.
     * Attempts to truncate at sentence boundaries.
     *
     * @param text The text to truncate
     * @param maxTokens Maximum number of tokens
     * @return Truncated text
     */
    @NonNull
    public String truncateToTokens(@NonNull String text, int maxTokens) {
        int maxChars = maxTokens * CHARS_PER_TOKEN;

        if (text.length() <= maxChars) {
            return text;
        }

        // Find a sentence boundary before the limit
        int lastSentenceEnd = findLastSentenceEnd(text, maxChars);

        if (lastSentenceEnd > maxChars / 2) {
            // Found a good sentence boundary
            return text.substring(0, lastSentenceEnd + 1).trim();
        }

        // No good sentence boundary, try paragraph boundary
        int lastParagraph = text.lastIndexOf("\n\n", maxChars);
        if (lastParagraph > maxChars / 2) {
            return text.substring(0, lastParagraph).trim();
        }

        // Fallback: truncate at word boundary
        int lastSpace = text.lastIndexOf(' ', maxChars);
        if (lastSpace > maxChars / 2) {
            return text.substring(0, lastSpace).trim() + "...";
        }

        // Last resort: hard truncate
        return text.substring(0, maxChars).trim() + "...";
    }

    /**
     * Estimate the number of tokens in text.
     *
     * @param text The text to estimate
     * @return Estimated token count
     */
    public int estimateTokens(@NonNull String text) {
        return text.length() / CHARS_PER_TOKEN;
    }

    /**
     * Find the last sentence ending before the given position.
     */
    private int findLastSentenceEnd(@NonNull String text, int maxPos) {
        // Look for sentence-ending punctuation followed by space or end
        int lastEnd = -1;

        for (int i = 0; i < maxPos && i < text.length() - 1; i++) {
            char c = text.charAt(i);
            char next = text.charAt(i + 1);

            // Sentence ends with . ! ? followed by space or newline
            if ((c == '.' || c == '!' || c == '?') &&
                    (next == ' ' || next == '\n')) {
                // Make sure it's not an abbreviation (check for uppercase after)
                if (i + 2 < text.length() && c == '.') {
                    char afterSpace = text.charAt(i + 2);
                    // If lowercase follows, probably abbreviation
                    if (Character.isLowerCase(afterSpace)) {
                        continue;
                    }
                }
                lastEnd = i;
            }
        }

        return lastEnd;
    }

    /**
     * Extract the first paragraph from HTML content.
     * Useful for generating short snippets.
     *
     * @param html The HTML content
     * @return First paragraph text
     */
    @NonNull
    public String extractFirstParagraph(@NonNull String html) {
        String text = extractText(html);

        if (text.isEmpty()) {
            return "";
        }

        // Find first paragraph break
        int paragraphEnd = text.indexOf("\n\n");
        if (paragraphEnd > 0 && paragraphEnd < 500) {
            return text.substring(0, paragraphEnd).trim();
        }

        // No paragraph break found, take first 300 chars at sentence boundary
        return truncateToTokens(text, 75); // ~300 chars
    }
}
