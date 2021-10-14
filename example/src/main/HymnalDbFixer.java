
package main;

import repositories.DatabaseClient;

/**
 * Class to fix errors in the current hymnal db mapping according to some manually predefined operations.
 *
 * NOTE: There is no need to use the db to create a fully dense graph, because that will be done in code later.
 */
public class HymnalDbFixer {

    public static void fix(DatabaseClient client) {
        fix_h1351(client);
        fix_h1111_ch8111(client);
        fix_ch1090(client);
        fix_h445_h1359(client);
        fix_hf15(client);
        fix_h79_h8079(client);
        fix_h267_h1360(client);
        fix_ts253(client);
        fix_ts142(client);
        fix_h720_h8526(client);
        fix_h31_ch29(client);
        fix_h379(client);
        fix_h8438(client);
        fix_ht1358(client);
        fix_ch643(client);
        fix_h528(client);
        fix_h480(client);

        fix_nt377(client);
        fix_ns98(client);
        fix_nt575_ns34(client);
        fix_h635_h481_h631(client);
        fix_ns59_ns110_ns111(client);
        fix_ns2(client);
        fix_ns4(client);
        fix_ns10_ns142(client);
        fix_h1033(client);
        fix_h1358(client);
        fix_h1162_h1163(client);
        fix_ns73(client);
        fix_ns53(client);
    }

    /**
     * h/1351 should also map to ht/1351,so we need to update all songs in that graph.
     */
    private static void fix_h1351(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ts/835\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ts/835?gb=1\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1351\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"1351\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1351\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ts/835?gb=1\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1351\"}]}' WHERE HYMN_TYPE=\"ts\" AND HYMN_NUMBER=\"835\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1351\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ts/835\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1351\"}]}' WHERE HYMN_TYPE=\"ts\" AND HYMN_NUMBER=\"835\" and QUERY_PARAMS = \"?gb=1\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1351\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ts/835\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ts/835?gb=1\"}]}' WHERE HYMN_TYPE=\"ht\" AND HYMN_NUMBER=\"1351\" and QUERY_PARAMS = \"\";");
    }

    /**
     * h/1111 should map to ch/1111 instead of ch/8111.
     *
     * In fact, ch/8111 and ch/8111?gb=1 aren't even valid hymns
     */
    private static void fix_h1111_ch8111(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/1111?gb=1\"}, {\"value\":\"詩歌(繁)\",\"path\":\"/en/hymn/ch/1111\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"1111\"");
        client.getDb().execSql("DELETE FROM SONG_DATA WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"8111\"");
    }

    /**
     * ch/1090 and ch/1090?gb=1 should map to the english, and tagalog 1090, not 1089
     */
    private static void fix_ch1090(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1090\"},{\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/1090?gb=1\"},{\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1090\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"1090\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1090\"},{\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/1090\"},{\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1090\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"1090\" and QUERY_PARAMS = \"?gb=1\";");
    }

    /**
     *  h/445 and h/1359 are related. However, the language mappings for each is all messed up.
     *  Here is the current mapping:
     *    h/445->cb/445,ch/339,ht/1359;
     *    cb/445->h/445,ch/339,ht/1359;
     *    ht/445->cb/445,ch/339,h/445;
     *    de/445->cb/445,ch/339,h/445,hf/79,ht/1359;
     *    hf/79->h/445,cb/445,ch/339,de/445,ht/1359;
     *    h/1359->cb/445,ch/339,ht/1359;
     *    ch/339->h/1359,cb/445,ht/1359;
     *    ht/1359->h/1359,cb/445,ch/339;
     *  Here is the correct mapping:
     *    h/445->cb/445,ht/445,hf/79,de/445;
     *    cb/445->h/445,ht/445,hf/79,de/445;
     *    ht/445->h/445,cb/445,hf/79,de/445;
     *    hf/79->h/445,cb/445,ht/445,de/79;
     *    de/445->h/445,cb/445,ht/445,de/445;
     *    h/1359->ch/339,ht/1359;
     *    ch/339->h/1359,ht/1359;
     *    ht/1359->h/1359,ch/339;
     */
    private static void fix_h445_h1359(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/445\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/445\"},{\"value\":\"French\", \"path\":\"/en/hymn/hf/79\"},{\"value\":\"German\", \"path\":\"/en/hymn/de/445\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"445\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/445\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/445\"},{\"value\":\"French\", \"path\":\"/en/hymn/hf/79\"},{\"value\":\"German\", \"path\":\"/en/hymn/de/445\"}]}' WHERE HYMN_TYPE=\"cb\" AND HYMN_NUMBER=\"445\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/445\"}, {\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/445\"},{\"value\":\"French\", \"path\":\"/en/hymn/hf/79\"},{\"value\":\"German\", \"path\":\"/en/hymn/de/445\"}]}' WHERE HYMN_TYPE=\"ht\" AND HYMN_NUMBER=\"445\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/445\"}, {\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/445\"},{\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/445\"},{\"value\":\"German\", \"path\":\"/en/hymn/de/445\"}]}' WHERE HYMN_TYPE=\"hf\" AND HYMN_NUMBER=\"79\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/445\"}, {\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/445\"},{\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/445\"},{\"value\":\"French\", \"path\":\"/en/hymn/hf/79\"}]}' WHERE HYMN_TYPE=\"de\" AND HYMN_NUMBER=\"445\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/339\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/339?gb=1\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1359\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"1359\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1359\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/339?gb=1\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1359\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"339\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1359\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/339\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1359\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"339\" and QUERY_PARAMS = \"?gb=1\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1359\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/339\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/339?gb=1\"}]}' WHERE HYMN_TYPE=\"ht\" AND HYMN_NUMBER=\"1359\" and QUERY_PARAMS = \"\";");

    }

    /**
     * hf/15 is mapped to h/473 for some reason, but it actually is the French version of h/1084. So we need to update the languages json and copy over Author(null), Composer(null), Key(F Major), Time(3/4), Meter(8.8.8.8), Hymn Code(51712165172321), Scriptures (Song of Songs) from h/1084
     */
    private static void fix_hf15(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"German\", \"path\":\"/en/hymn/de/1084\"},{\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ts/302?gb=1\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ts/302\"}, {\"value\":\"Dutch\", \"path\":\"/en/hymn/hd/19\"}, {\"value\":\"English\", \"path\":\"/en/hymn/h/1084\"}]}', SONG_META_DATA_AUTHOR = NULL, SONG_META_DATA_COMPOSER = NULL, SONG_META_DATA_KEY = \"F Major\", SONG_META_DATA_TIME = \"3/4\", SONG_META_DATA_METER = \"8.8.8.8\", SONG_META_DATA_HYMN_CODE = \"51712165172321\", SONG_META_DATA_SCRIPTURES = \"Song of Songs\" WHERE HYMN_TYPE=\"hf\" AND HYMN_NUMBER=\"15\";");
    }

    /**
     * h/8079 and h/79 are related. However, the language mappings for each is all messed up.
     *
     *  Here is the current mapping:
     *    h/79->cb/79,ch/68,ht/79;
     *    cb/79->h/79,ch/68,ht/79;
     *    h/8079->cb/79,ch/68,ht/79;
     *    ht/79->h/79,ch/68,cb/79;
     *    ch/68->h/8079,cb/79,ht/79;
     *
     *  Here is the correct mapping:
     *    h/79->cb/79;
     *    cb/79->h/79;
     *    h/8079->ch/68,ch/68?gb=1,ht/79;
     *    ch/68->h/8079,ch/68?gb=1,ht/79;
     *    ch/68?gb=1->h/8079,ch/68,ht/79;
     *    ht/79->h/8079,ch/68,ch/68?gb=1;
     */
    private static void fix_h79_h8079(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/79\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"79\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/79\"}]}' WHERE HYMN_TYPE=\"cb\" AND HYMN_NUMBER=\"79\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/68\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/68?gb=1\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/79\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"8079\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/8079\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/68?gb=1\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/79\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"68\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/8079\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/68\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/79\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"68\" and QUERY_PARAMS = \"?gb=1\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/8079\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/68\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/68?gb=1\"}]}' WHERE HYMN_TYPE=\"ht\" AND HYMN_NUMBER=\"79\" and QUERY_PARAMS = \"\";");
    }

    /**
     * h/267 and h/1360 are related. However, the language mappings for each is all messed up.
     *
     *  Here is the current mapping:
     *    h/267->cb/267,ch/217,ht/267;
     *    cb/267->h/267,ch/217,ht/267;
     *    h/1360->cb/267,ch/217,ht/1360;
     *    ht/267->h/267,cb/267,ch/217;
     *    ch/217->h/1360,cb/267,ht/1360;
     *    ht/1360->cb/267,ch/217,h/1360;
     *    hf/46->cb/267,ch/217,h/267,de/267,ht/267;
     *    de267->cb/267,ch/217,h/267,hf/46,ht/267
     *
     *  Here is the correct mapping:
     *    h/267->cb/267,ht/267;
     *    cb/267->h/267,ht/267;
     *    de/267->cb/267,h/267,ht/267
     *    ht/267->h/267,cb/267;
     *    h/1360->ch/217,ht/1360;
     *    ch/217->h/1360,ht/1360;
     *    ht/1360->ch/217,h/1360;
     *    hf/46->ch/217,h/1360,ht/1360;
     */
    private static void fix_h267_h1360(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/267\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/267\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"267\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/267\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/267\"}]}' WHERE HYMN_TYPE=\"cb\" AND HYMN_NUMBER=\"267\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/267\"}, {\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/267\"}]}' WHERE HYMN_TYPE=\"ht\" AND HYMN_NUMBER=\"267\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/217?gb=1\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/217\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1360\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"1360\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/217?gb=1\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/217\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1360\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"1360\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1360\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1360\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/217?gb=1\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"217\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1360\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1360\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/217\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"217\" and QUERY_PARAMS = \"?gb=1\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\", \"path\":\"/en/hymn/h/1360\"}, {\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/217?gb=1\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/217\"}]}' WHERE HYMN_TYPE=\"ht\" AND HYMN_NUMBER=\"1360\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ch/217?gb=1\"}, {\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ch/217\"}, {\"value\":\"English\", \"path\":\"/en/hymn/h/1360\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/1360\"}]}' WHERE HYMN_TYPE=\"hf\" AND HYMN_NUMBER=\"46\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/267\"}, {\"value\":\"English\", \"path\":\"/en/hymn/h/267\"}, {\"value\":\"Tagalog\", \"path\":\"/en/hymn/ht/267\"}]}' WHERE HYMN_TYPE=\"de\" AND HYMN_NUMBER=\"267\" and QUERY_PARAMS = \"\";");
    }

    /**
     *  ts/253 and ts/253?gb=1 -> mapped to h/754 for some reason, but it actually is the chinese version of h/1164.
     *  So it should map to h/1164 and its related songs
     */
    private static void fix_ts253(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ts/253?gb=1\"}, {\"value\":\"English\", \"path\":\"/en/hymn/h/1164\"}, {\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/1164\"}]}' WHERE HYMN_TYPE=\"ts\" AND HYMN_NUMBER=\"253\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ts/253\"}, {\"value\":\"English\", \"path\":\"/en/hymn/h/1164\"}, {\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/1164\"}]}' WHERE HYMN_TYPE=\"ts\" AND HYMN_NUMBER=\"253\" and QUERY_PARAMS = \"?gb=1\";");
    }

    /**
     *  ts/142 and ts/142?gb=1 -> mapped to h/1193 for some reason, but it actually is the chinese version of h/1198.
     *  So it should map to h/1198 and its related songs
     */
    private static void fix_ts142(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"诗歌(简)\", \"path\":\"/en/hymn/ts/142?gb=1\"}, {\"value\":\"English\", \"path\":\"/en/hymn/h/1198\"}, {\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/1198\"}]}' WHERE HYMN_TYPE=\"ts\" AND HYMN_NUMBER=\"142\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"詩歌(繁)\", \"path\":\"/en/hymn/ts/142\"}, {\"value\":\"English\", \"path\":\"/en/hymn/h/1198\"}, {\"value\":\"Cebuano\", \"path\":\"/en/hymn/cb/1198\"}]}' WHERE HYMN_TYPE=\"ts\" AND HYMN_NUMBER=\"142\" and QUERY_PARAMS = \"?gb=1\";");
    }

    /**
     *  h/8526 is an alternate tune of h/720. In Hymnal.net, ch/526 and ch/526?gb=1 play the same tune as h/8526,
     *  while cb/720, ht/720, and de/720 play the same tune as h/720.
     *  So here is the correct mapping:
     *    h/720->cb/720,ht/720,de/720;
     *    cb/720->h/720,ht/720,de/720;
     *    ht/720->h/720,cb/720,de/720;
     *    de/720->h/720,cb/720,ht/720;
     *    h/8526-> ch/526,ch/526?gb=1;
     *    ch/526-> h/8526,ch/526?gb=1;
     *    ch/526?gb=1-> h/8526,ch/526;
     */
    private static void fix_h720_h8526(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"Cebuano\",\"path\":\"/en/hymn/cb/720\"},{\"value\":\"Tagalog\",\"path\":\"/en/hymn/ht/720\"}, {\"value\":\"German\",\"path\":\"/en/hymn/de/720\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"720\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\",\"path\":\"/en/hymn/h/720\"},{\"value\":\"Tagalog\",\"path\":\"/en/hymn/ht/720\"}, {\"value\":\"German\",\"path\":\"/en/hymn/de/720\"}]}' WHERE HYMN_TYPE=\"cb\" AND HYMN_NUMBER=\"720\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\",\"path\":\"/en/hymn/h/720\"},{\"value\":\"Cebuano\",\"path\":\"/en/hymn/cb/720\"}, {\"value\":\"German\",\"path\":\"/en/hymn/de/720\"}]}' WHERE HYMN_TYPE=\"ht\" AND HYMN_NUMBER=\"720\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\",\"path\":\"/en/hymn/h/720\"},{\"value\":\"Cebuano\",\"path\":\"/en/hymn/cb/720\"}, {\"value\":\"Tagalog\",\"path\":\"/en/hymn/ht/720\"}]}' WHERE HYMN_TYPE=\"de\" AND HYMN_NUMBER=\"720\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"詩歌(繁)\",\"path\":\"/en/hymn/ch/526\"},{\"value\":\"诗歌(简)\",\"path\":\"/en/hymn/ch/526?gb=1\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"8526\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\",\"path\":\"/en/hymn/h/8526\"},{\"value\":\"诗歌(简)\",\"path\":\"/en/hymn/ch/526?gb=1\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"526\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\",\"path\":\"/en/hymn/h/8526\"},{\"value\":\"詩歌(繁)\",\"path\":\"/en/hymn/ch/526\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"526\" and QUERY_PARAMS = \"?gb=1\";");

    }

    /**
     * hd/31 is the Dutch song for ns/79. h/31, ch/29, and ch/29?gb=1 should not reference it at all.
     */
    private static void fix_h31_ch29(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"诗歌(简)\",\"path\":\"/en/hymn/ch/29?gb=1\"},{\"value\":\"詩歌(繁)\",\"path\":\"/en/hymn/ch/29\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"31\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\",\"path\":\"/en/hymn/h/31\"},{\"value\":\"诗歌(简)\",\"path\":\"/en/hymn/ch/29?gb=1\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"29\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"English\",\"path\":\"/en/hymn/h/31\"},{\"value\":\"詩歌(繁)\",\"path\":\"/en/hymn/ch/29\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"29\" and QUERY_PARAMS = \"?gb=1\";");

    }

    /**
     * The mapping for h/379 is wrong. It shouldn't map to ch/439 and its related songs, but instead should match to
     * nothing
     */
    private static void fix_h379(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"379\" and QUERY_PARAMS = \"\";");
    }

    /**
     * h/8438 should map to ch/438, ch/438?gb=1, ht/c438, not its current mapping of ch/439 and its related songs.
     */
    private static void fix_h8438(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"诗歌(简)\",\"path\":\"/en/hymn/ch/438?gb=1\"},{\"value\":\"詩歌(繁)\",\"path\":\"/en/hymn/ch/438\"},{\"value\":\"Tagalog\",\"path\":\"/en/hymn/ht/c438\"}]}' WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"8438\" and QUERY_PARAMS = \"\";");
    }

    /**
     * ht/1358 should not link to cb/921, as they are different songs with the same tune (h/921 vs h/1358). To decouple
     * them, we need to fix the languages of ht/1358 to not point to a cebuano song (since cb/1358 doesn't exist)
     */
    private static void fix_ht1358(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"诗歌(简)\",\"path\":\"/en/hymn/ch/664?gb=1\"},{\"value\":\"詩歌(繁)\",\"path\":\"/en/hymn/ch/664\"},{\"value\":\"English\",\"path\":\"/en/hymn/h/1358\"}]}' WHERE HYMN_TYPE=\"ht\" AND HYMN_NUMBER=\"1358\" and QUERY_PARAMS = \"\";");
    }

    /**
     *  h/1017, ch/693, and ch/643 are all kind of related. h/1017 is the full English song, ch/693 is the full
     *  Chinese song, while ch/643 is just the verse repeated a few times. I'm making a judgement call here to say
     *  that just having the chorus does NOT constitute a translation of the song, and therefore, I am going to
     *  set ch/643 and ch/643?gb=1 to just have each other as languages
     */
    private static void fix_ch643(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"诗歌(简)\",\"path\":\"/en/hymn/ch/643?gb=1\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"643\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{\"name\":\"Languages\",\"data\":[{\"value\":\"詩歌(繁)\",\"path\":\"/en/hymn/ch/643\"}]}' WHERE HYMN_TYPE=\"ch\" AND HYMN_NUMBER=\"643\" and QUERY_PARAMS = \"?gb=1\";");
    }

    /**
     * h/528 should be by itself. The Chinese song it's linked to (ch/444) is actually translated by h/8444.
     */
    private static void fix_h528(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"528\" and QUERY_PARAMS = \"\";");
    }

    /**
     * h/480 should be by itself. The Chinese song it's linked to (ch/357) is actually translated by h/8357.
     */
    private static void fix_h480(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"480\" and QUERY_PARAMS = \"\";");
    }

    /**
     * nt/377 has a relevant song of nt/1079, but they are not really related. So remove it from the relevant list
     */
    private static void fix_nt377(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = '{\"name\":\"Relevant\",\"data\":[{\"value\":\"Original Tune\",\"path\":\"/en/hymn/h/377\"}]}' WHERE HYMN_TYPE=\"nt\" AND HYMN_NUMBER=\"377\" and QUERY_PARAMS = \"\";");
    }

    /**
     * ns/98 has a relevant song of ns/80, but they are not really related. So remove it from the relevant list
     */
    private static void fix_ns98(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"98\" and QUERY_PARAMS = \"\";");
    }

    /**
     * nt/575 has a bunch of relevant songs that are not really related.
     * ns/34 is also in that same bucket and references a bunch of songs that are not really related
     */
    private static void fix_nt575_ns34(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = '{\"name\":\"Relevant\",\"data\":[{\"value\":\"Original Tune\",\"path\":\"/en/hymn/h/575\"}]}' WHERE HYMN_TYPE=\"nt\" AND HYMN_NUMBER=\"575\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"34\" and QUERY_PARAMS = \"\";");
    }

    /**
     * h/635, h/481, h/631 reference each other, but they're not really related
     */
    private static void fix_h635_h481_h631(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"635\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"481\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"631\" and QUERY_PARAMS = \"\";");
    }

    /**
     * ns/59, ns/110, ns/111 reference each other, but they're not really related
     */
    private static void fix_ns59_ns110_ns111(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"59\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"110\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"111\" and QUERY_PARAMS = \"\";");
    }

    /**
     * ns/2 references ns/3, but they're not really related
     */
    private static void fix_ns2(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"2\" and QUERY_PARAMS = \"\";");
    }

    /**
     * ns/4 references ns/5 and h/36, but they're not really related
     */
    private static void fix_ns4(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"4\" and QUERY_PARAMS = \"\";");
    }

    /**
     * ns/10, ns/142 reference each other, but they're not really related
     */
    private static void fix_ns10_ns142(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"10\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"142\" and QUERY_PARAMS = \"\";");
    }

    /**
     * h/1033 references h/1007, but they're not really related
     */
    private static void fix_h1033(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"1033\" and QUERY_PARAMS = \"\";");
    }

    /**
     * h/1358 references h/921, but they're not really related
     */
    private static void fix_h1358(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"1358\" and QUERY_PARAMS = \"\";");
    }

    /**
     * h/1162, h/1163 reference each other, but they're not really related
     */
    private static void fix_h1162_h1163(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"1162\" and QUERY_PARAMS = \"\";");
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"h\" AND HYMN_NUMBER=\"1163\" and QUERY_PARAMS = \"\";");
    }

    /**
     * ns/73 references ns/24 and nt/711 but is not really related to those
     */
    private static void fix_ns73(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"73\" and QUERY_PARAMS = \"\";");
    }

    /**
     * ns/53 references h/34, but they're not really related
     */
    private static void fix_ns53(DatabaseClient client) {
        client.getDb().execSql("UPDATE SONG_DATA set SONG_META_DATA_RELEVANT = NULL WHERE HYMN_TYPE=\"ns\" AND HYMN_NUMBER=\"53\" and QUERY_PARAMS = \"\";");
    }
}
