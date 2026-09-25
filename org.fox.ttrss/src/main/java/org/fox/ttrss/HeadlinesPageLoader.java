package org.fox.ttrss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Loads a page, recovering from the server's pagination invalidation response. */
final class HeadlinesPageLoader {
    private static final int MAX_RESTARTS = 2;

    interface Request {
        JsonElement perform(HashMap<String, String> params);
    }

    static final class Result {
        final JsonArray articles;
        final int firstId;
        final boolean exhausted;

        Result(JsonArray articles, int firstId, boolean exhausted) {
            this.articles = articles;
            this.firstId = firstId;
            this.exhausted = exhausted;
        }
    }

    // Adaptive is resolved once per fresh load, using the actual returned articles,
    // not the unread count of a batch the user may already have finished reading.
    static String resolveViewMode(String mode, boolean search, int feedId, boolean hasUnread) {
        if ("adaptive".equals(mode)) {
            // The server does not apply adaptive's unread filter to search or id -1
            // (Starred articles / Special category).
            return !search && feedId != -1 && hasUnread ? "unread" : "all_articles";
        }
        return mode;
    }

    static int getSkip(String mode, boolean fresh, boolean recentlyRead, int unread, int total) {
        return fresh || ("unread".equals(mode) && !recentlyRead) ? unread : total;
    }

    static Result load(HashMap<String, String> original, Set<Integer> existingIds,
                       Request request) throws IOException {
        HashMap<String, String> params = new HashMap<>(original);
        int limit = Integer.parseInt(params.get("limit"));
        if (limit <= 0)
            throw new IOException("Invalid headlines page size");
        int skip = Integer.parseInt(params.get("skip"));
        int target = skip + limit;
        int restarts = 0;
        boolean recovering = false;
        JsonArray collected = new JsonArray();
        Set<Integer> collectedIds = new HashSet<>();
        boolean addedNew = false;

        while (true) {
            params.put("skip", String.valueOf(skip));
            JsonElement response = request.perform(params);
            if (response == null)
                return null; // Preserve the transport's error and the existing list.

            JsonArray content = response.getAsJsonArray();
            JsonArray articles = content;
            int firstId = 0;
            if (params.containsKey("include_header")) {
                JsonObject header = content.get(0).getAsJsonObject();
                JsonElement id = header.get("first_id");
                if (id != null && !id.isJsonNull()) {
                    try {
                        firstId = id.getAsInt();
                    } catch (NumberFormatException ignored) {
                        // Older servers may return an empty first_id.
                    }
                }
                JsonElement changed = header.get("first_id_changed");
                if (changed != null && !changed.isJsonNull() && changed.getAsBoolean()) {
                    if (++restarts > MAX_RESTARTS)
                        throw new IOException("Articles changed repeatedly while loading. Please retry.");

                    // No article query ran: this is not a short/end-of-feed page.
                    // Rescan the prefix to avoid skipping entries after membership changes.
                    skip = 0;
                    recovering = true;
                    collected = new JsonArray();
                    collectedIds.clear();
                    addedNew = false;
                    params.remove("check_first_id");
                    continue;
                }
                articles = content.get(1).getAsJsonArray();
            }

            for (JsonElement article : articles) {
                int id = article.getAsJsonObject().get("id").getAsInt();
                if (collectedIds.add(id))
                    collected.add(article);
                addedNew |= !existingIds.contains(id);
            }

            skip += articles.size();
            boolean exhausted = articles.size() < limit;
            if (exhausted || (addedNew && (!recovering || skip >= target)))
                return new Result(collected, firstId, exhausted);

            // A full duplicate-only page still has a successor. Continue within
            // this request instead of waiting for a scroll event with no new rows.
            if (firstId > 0)
                params.put("check_first_id", String.valueOf(firstId));
            else
                params.remove("check_first_id");
            params.remove("force_update");
        }
    }

    private HeadlinesPageLoader() {}
}
