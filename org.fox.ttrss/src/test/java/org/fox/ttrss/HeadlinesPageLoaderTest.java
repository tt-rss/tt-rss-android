package org.fox.ttrss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class HeadlinesPageLoaderTest {
    private static HashMap<String, String> params(int skip, int limit) {
        HashMap<String, String> params = new HashMap<>();
        params.put("skip", String.valueOf(skip));
        params.put("limit", String.valueOf(limit));
        params.put("include_header", "true");
        params.put("check_first_id", "91090");
        return params;
    }

    private static JsonArray page(int firstId, int... ids) {
        JsonObject header = new JsonObject();
        header.addProperty("first_id", firstId);
        JsonArray articles = new JsonArray();
        for (int id : ids) {
            JsonObject article = new JsonObject();
            article.addProperty("id", id);
            articles.add(article);
        }
        JsonArray response = new JsonArray();
        response.add(header);
        response.add(articles);
        return response;
    }

    @Test
    public void loggedInvalidationRescansPrefixAndLoadsUnseenArticles() throws Exception {
        Set<Integer> existing = new HashSet<>();
        for (int i = 1; i <= 45; i++) existing.add(i);
        List<Integer> skips = new ArrayList<>();
        HeadlinesPageLoader.Result result = HeadlinesPageLoader.load(params(10, 15), existing, p -> {
            int skip = Integer.parseInt(p.get("skip"));
            skips.add(skip);
            if (skips.size() == 1) {
                // Exact invalidation response from ttrss-004.log:588.
                return JsonParser.parseString("[{\"id\":\"-3\",\"first_id\":91092,"
                        + "\"is_cat\":false,\"first_id_changed\":true},[]]");
            }
            if (skip == 0) {
                assertFalse(p.containsKey("check_first_id"));
                // A new article was inserted ahead of the ten remaining local unread articles.
                return page(91092, 100, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49);
            }
            assertEquals("91092", p.get("check_first_id"));
            return page(91092, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64);
        });
        assertEquals(List.of(10, 0, 15), skips);
        assertFalse(result.exhausted);
        assertEquals(91092, result.firstId);
        Set<Integer> merged = new HashSet<>(existing);
        for (JsonElement article : result.articles)
            merged.add(article.getAsJsonObject().get("id").getAsInt());
        for (int i = 1; i <= 64; i++) assertTrue("Missing article " + i, merged.contains(i));
        assertTrue(merged.contains(100));
        assertEquals(65, merged.size());
    }

    @Test
    public void duplicateOnlyPageContinuesWithoutAnotherScroll() throws Exception {
        List<Integer> skips = new ArrayList<>();
        HeadlinesPageLoader.Result result = HeadlinesPageLoader.load(params(0, 2), Set.of(1, 2), p -> {
            int skip = Integer.parseInt(p.get("skip"));
            skips.add(skip);
            return skip == 0 ? page(4, 1, 2) : page(4, 3, 4);
        });
        assertEquals(List.of(0, 2), skips);
        assertEquals(4, result.articles.size());
        assertFalse(result.exhausted);
    }

    @Test
    public void genuineEmptyPageDisablesPagination() throws Exception {
        HeadlinesPageLoader.Result result = HeadlinesPageLoader.load(params(10, 15), Set.of(), p -> page(91090));
        assertTrue(result.exhausted);
        assertEquals(0, result.articles.size());
    }

    @Test
    public void falseInvalidationFlagIsAnOrdinaryResponse() throws Exception {
        HeadlinesPageLoader.Result result = HeadlinesPageLoader.load(params(0, 2), Set.of(), p -> {
            JsonArray response = page(1, 1);
            response.get(0).getAsJsonObject().addProperty("first_id_changed", false);
            return response;
        });
        assertTrue(result.exhausted);
        assertEquals(1, result.articles.size());
    }

    @Test
    public void transportFailureDuringRecoveryDoesNotPublishPartialScan() throws Exception {
        int[] calls = {0};
        HeadlinesPageLoader.Result result = HeadlinesPageLoader.load(params(4, 2), Set.of(), p -> {
            calls[0]++;
            if (calls[0] == 1)
                return JsonParser.parseString("[{\"first_id_changed\":true},[]]");
            if (calls[0] == 2) return page(5, 1, 2);
            return null;
        });
        assertNull(result);
        assertEquals(3, calls[0]);
    }

    @Test
    public void repeatedInvalidationIsBounded() {
        int[] calls = {0};
        assertThrows(IOException.class, () -> HeadlinesPageLoader.load(params(10, 15), Set.of(), p -> {
            calls[0]++;
            return JsonParser.parseString("[{\"first_id_changed\":true},[]]");
        }));
        assertEquals(3, calls[0]);
    }

    @Test
    public void secondInvalidationDiscardsPartialScanAndStartsAgain() throws Exception {
        List<Integer> skips = new ArrayList<>();
        HeadlinesPageLoader.Result result = HeadlinesPageLoader.load(params(2, 2), Set.of(1, 2), p -> {
            skips.add(Integer.parseInt(p.get("skip")));
            return switch (skips.size()) {
                case 1, 3 -> JsonParser.parseString("[{\"first_id_changed\":true},[]]");
                case 2 -> page(10, 10, 1);
                case 4 -> page(11, 11, 1);
                default -> page(11, 2, 3);
            };
        });
        assertEquals(List.of(2, 0, 2, 0, 2), skips);
        Set<Integer> ids = new HashSet<>();
        for (JsonElement article : result.articles)
            ids.add(article.getAsJsonObject().get("id").getAsInt());
        assertEquals(Set.of(11, 1, 2, 3), ids);
        assertFalse(result.exhausted);
    }

    @Test
    public void recoveryCanReachTheRealEndOfFeed() throws Exception {
        int[] calls = {0};
        HeadlinesPageLoader.Result result = HeadlinesPageLoader.load(params(2, 2), Set.of(1, 2), p -> {
            calls[0]++;
            return switch (calls[0]) {
                case 1 -> JsonParser.parseString("[{\"first_id_changed\":true},[]]");
                case 2 -> page(3, 1, 2);
                default -> page(3, 3);
            };
        });
        assertEquals(3, calls[0]);
        assertEquals(3, result.articles.size());
        assertTrue(result.exhausted);
    }

    @Test
    public void olderApiWithoutHeadersStillLoads() throws Exception {
        HashMap<String, String> params = params(0, 2);
        params.remove("include_header");
        HeadlinesPageLoader.Result result = HeadlinesPageLoader.load(params, Set.of(), p -> page(0, 1).get(1));
        assertEquals(1, result.articles.size());
        assertTrue(result.exhausted);
    }

    @Test
    public void finishingLocalAdaptiveBatchDoesNotSkipRemoteUnread() {
        String mode = HeadlinesPageLoader.resolveViewMode("adaptive", false, 12, true);
        assertEquals("unread", mode);
        assertEquals(8, HeadlinesPageLoader.getSkip(mode, false, false, 8, 15));
        // Same session, all fifteen fetched articles have now been read.
        assertEquals(0, HeadlinesPageLoader.getSkip(mode, false, false, 0, 15));
    }

    @Test
    public void searchStarredAndReadOnlyAdaptiveUseStableOffsets() {
        assertEquals("all_articles", HeadlinesPageLoader.resolveViewMode("adaptive", true, 12, true));
        assertEquals("all_articles", HeadlinesPageLoader.resolveViewMode("adaptive", false, -1, true));
        assertEquals("all_articles", HeadlinesPageLoader.resolveViewMode("adaptive", false, 12, false));
        for (String mode : List.of("all_articles", "marked", "published")) {
            assertEquals(mode, HeadlinesPageLoader.resolveViewMode(mode, false, 12, true));
            assertEquals(15, HeadlinesPageLoader.getSkip(mode, false, false, 8, 15));
        }
    }

    @Test
    public void freshIsAlwaysUnreadOnlyAndRecentlyReadIgnoresUnreadMode() {
        assertEquals(8, HeadlinesPageLoader.getSkip("all_articles", true, false, 8, 15));
        assertEquals(0, HeadlinesPageLoader.getSkip("unread", true, false, 0, 15));
        assertEquals(15, HeadlinesPageLoader.getSkip("unread", false, true, 0, 15));
    }
}
