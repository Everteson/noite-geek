package com.evert.noitegeek;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Busca e lê os dados das fileiras: animes em alta (AniList) e o feed do YouTube vindo do PC. */
final class Feeds {
    private Feeds() {}

    static final class Item {
        final String title;
        final String subtitle;
        final String image;
        final String key;    // anime: título pra busca na Crunchyroll; YouTube: id do vídeo
        final String badge;  // duração do vídeo, ou "Ep. 7"
        final float progress; // 0..1, quanto já assistiu (Crunchyroll)
        final boolean crunchy; // true: key é o id do episódio na Crunchyroll

        Item(String title, String subtitle, String image, String key, String badge) {
            this(title, subtitle, image, key, badge, 0f, false);
        }

        Item(String title, String subtitle, String image, String key, String badge, float progress, boolean crunchy) {
            this.title = title;
            this.subtitle = subtitle;
            this.image = image;
            this.key = key;
            this.badge = badge;
            this.progress = progress;
            this.crunchy = crunchy;
        }
    }

    static final String ANILIST = "https://graphql.anilist.co";

    /** Os animes mais em alta agora que têm página na Crunchyroll. */
    static final String ANIME_QUERY = "{\"query\":\"query{Page(perPage:50){media(type:ANIME,sort:TRENDING_DESC,"
            + "isAdult:false){id title{romaji english} coverImage{large} episodes "
            + "nextAiringEpisode{episode} externalLinks{site}}}}\"}";

    static List<Item> parseAnime(String json) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray media = new JSONObject(json).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
            for (int i = 0; i < media.length() && out.size() < 20; i++) {
                JSONObject m = media.getJSONObject(i);
                if (!onCrunchyroll(m.optJSONArray("externalLinks"))) continue;
                JSONObject t = m.getJSONObject("title");
                String title = t.optString("english", "");
                if (title.isEmpty() || "null".equals(title)) title = t.optString("romaji", "");
                String badge = "";
                JSONObject next = m.optJSONObject("nextAiringEpisode");
                if (next != null && next.optInt("episode") > 1) {
                    badge = "Ep. " + (next.optInt("episode") - 1);
                } else if (m.optInt("episodes") > 0) {
                    badge = m.optInt("episodes") + " eps";
                }
                String cover = m.getJSONObject("coverImage").optString("large", "");
                out.add(new Item(title, "", cover, title, badge));
            }
        } catch (Exception ignored) {
            // JSON quebrado: fica com o que deu pra ler
        }
        return out;
    }

    private static boolean onCrunchyroll(JSONArray links) {
        if (links == null) return false;
        for (int i = 0; i < links.length(); i++) {
            JSONObject l = links.optJSONObject(i);
            if (l != null && "Crunchyroll".equalsIgnoreCase(l.optString("site"))) return true;
        }
        return false;
    }

    /** Uma lista do feed do PC: "rec", "subs" ou "hist". */
    static List<Item> parseYoutube(String json, String key, int max) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(json).optJSONArray(key);
            if (arr == null) return out;
            for (int i = 0; i < arr.length() && out.size() < max; i++) {
                JSONObject v = arr.getJSONObject(i);
                String id = v.optString("id");
                if (id.length() != 11) continue;
                out.add(new Item(v.optString("title"), v.optString("channel"), v.optString("thumb"), id,
                        duration(v.optInt("duration"))));
            }
        } catch (Exception ignored) {
            // idem
        }
        return out;
    }

    /** Uma lista da Crunchyroll vinda do PC: "cr_cont" (continuar) ou "cr_list" (sua lista). */
    static List<Item> parseCrunchy(String json, String key, int max) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(json).optJSONArray(key);
            if (arr == null) return out;
            for (int i = 0; i < arr.length() && out.size() < max; i++) {
                JSONObject v = arr.getJSONObject(i);
                String id = v.optString("id");
                if (!id.matches("[A-Z0-9]{6,20}")) continue;
                out.add(new Item(v.optString("series"), v.optString("sub"), v.optString("thumb"), id,
                        v.optString("badge"), (float) v.optDouble("progress", 0), true));
            }
        } catch (Exception ignored) {
            // idem
        }
        return out;
    }

    static String duration(int s) {
        if (s <= 0) return "";
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(java.util.Locale.US, "%d:%02d", m, sec);
    }
}
