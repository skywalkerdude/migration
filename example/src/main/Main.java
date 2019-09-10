package main;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tylersuehr.sql.ContentValues;
import models.*;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import repositories.DatabaseClient;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Tyler Suehr
 */
public final class Main {

    /**
     * Perform a dry run without actually writing anything to the database.
     */
    private static final boolean DRY_RUN = true;

    private static final boolean DEBUG_LOG = true;

    /**
     * Use a custom escape sequence, since Gson will auto-escape strings and screw everything up. Right before we save
     * the value, we will undo the custom escape character and replace it with the standard \".
     */
    private static final String CUSTOM_ESCAPE = "$CUSESP$";

    private static void LOG(String logStatement) {
        if (DEBUG_LOG) {
            System.out.println(logStatement);
        }
    }

    private static final String H4A_DB_NAME = "h4a-piano";
    private static final String HYMNAL_DB_NAME = "hymnaldb";

    private static Map<HymnalDbKey, ConvertedHymn> hymnalDbHymns = new HashMap<>();
    private static Map<H4aKey, ConvertedHymn> h4aHymns = new HashMap<>();

    private static Set<Set<HymnalDbKey>> hymnalDbExceptions = new HashSet<>();
    static {
        // This mapping, even though it has two classic hymns, is correct.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CLASSIC_HYMN, "528", null),
                                      new HymnalDbKey(HymnType.CLASSIC_HYMN, "8444", null)));

        // These two songs are related and thus can have different Chinese songs.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CLASSIC_HYMN, "1358", null),
                                      new HymnalDbKey(HymnType.CLASSIC_HYMN, "921", null)));

        // This mapping, even though it has two classic hymns, is correct.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CLASSIC_HYMN, "631", null),
                                      new HymnalDbKey(HymnType.CLASSIC_HYMN, "481", null)));

        // This mapping, even though it has two classic hymns, is correct.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CLASSIC_HYMN, "720", null),
                                      new HymnalDbKey(HymnType.CLASSIC_HYMN, "8526", null)));

        // This mapping, even though it has two new songs, is correct.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.NEW_SONG, "19", null),
                                      new HymnalDbKey(HymnType.NEW_SONG, "474", null)));

        // This mapping, even though it has two German songs, is correct.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.GERMAN, "786", null),
                                      new HymnalDbKey(HymnType.GERMAN, "786b", null)));

        // This mapping, even though it has three Howard Higashi songs, is correct.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.HOWARD_HIGASHI, "12", null),
                                      new HymnalDbKey(HymnType.HOWARD_HIGASHI, "12f", null),
                                      new HymnalDbKey(HymnType.HOWARD_HIGASHI, "12s", null)));

        // Both these songs actually map to the same English song.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CHINESE, "641", null),
                                      new HymnalDbKey(HymnType.CHINESE_SUPPLEMENT, "917", null)));
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CHINESE, "641", "?gb=1"),
                                      new HymnalDbKey(HymnType.CHINESE_SUPPLEMENT, "917", "?gb=1")));


    }

    private static String toJsonString(Object src) {
        return
            new GsonBuilder()
                .create().toJson(src)
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\"[", "[")
                .replace("]\"", "]")
                .replace("\"{", "{")
                .replace("}\"", "}");
    }

    public static void main(String[] args) throws SQLException, BadHanyuPinyinOutputFormatCombination {
        DatabaseClient hymnalClient = new DatabaseClient(HYMNAL_DB_NAME, 11);
        DatabaseClient h4aClient = new DatabaseClient(H4A_DB_NAME, 111);

        populateHymnalDbHymns(hymnalClient.getDb().rawQuery("SELECT * FROM song_data"));
        for (HymnalDbKey hymnalDbKey : hymnalDbHymns.keySet()) {
            populateHymnalDbLanguages(hymnalDbKey);
        }

        // Need to fetch in this particular order so we can populate the "related" and "languages" fields properly.
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='E'"));
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='NS'"));
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='CH'"));
        // populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='BF'"));
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='C'"));
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='CS'"));
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='CB'"));
        populateH4aHymns(
            h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='T' and parent_hymn is not null"));
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='FR'"));
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='S'"));
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='K'"));
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='G'"));
        populateH4aHymns(h4aClient.getDb().rawQuery("SELECT * FROM hymns WHERE hymn_group='J'"));
        h4aClient.close();

        /*
        Obsolete deletions from before the 3.6 database upgrade. Delete after some time.

        h4aHymns.get(new H4aKey("C31")).languages.remove(new H4aKey("T34"));
        h4aHymns.get(new H4aKey("C31")).languages.add(new H4aKey("T33"));
        h4aHymns.get(new H4aKey("C74")).languages.clear();

        // This song has messed up mapping in the h4a db: [E945,E956,CB956,C750]. So we need to remove the incorrect
        mappings.
        h4aHymns.get(new H4aKey("C755")).languages.remove(new H4aKey("E945"));
        h4aHymns.get(new H4aKey("C755")).languages.remove(new H4aKey("C750"));
        */

        // The category for CS747 is (incorrectly) its first line instead.
        if (h4aHymns.get(new H4aKey("CS747")).category.equals("哦主！我願花費自己")) {
            h4aHymns.get(new H4aKey("CS747")).category = "教會的生活";
        } else {
            throw new IllegalArgumentException("CS747 was fixed somehow. Remove this check.");
        }

        // E1248 maps to both K1014 and K1001. K1014 is wrong and should map to E1295 instead.
        h4aHymns.get(new H4aKey("E1248")).languages.remove(new H4aKey("K1014"));
        h4aHymns.get(new H4aKey("K1014")).languages.remove(new H4aKey("E1248"));
        h4aHymns.get(new H4aKey("E1295")).languages.add(new H4aKey("K1014"));
        h4aHymns.get(new H4aKey("K1014")).languages.add(new H4aKey("E1295"));

        // E51 maps to both K57 and K46. K57 is wrong and should be map to E61 instead.
        h4aHymns.get(new H4aKey("E51")).languages.remove(new H4aKey("K57"));
        h4aHymns.get(new H4aKey("K57")).languages.remove(new H4aKey("E51"));
        h4aHymns.get(new H4aKey("E61")).languages.add(new H4aKey("K57"));
        h4aHymns.get(new H4aKey("K57")).languages.add(new H4aKey("E61"));

        // C644 maps to E1358 (but not vice versa) when it should map to E890 instead.
        h4aHymns.get(new H4aKey("C644")).languages.remove(new H4aKey("E1358"));
        h4aHymns.get(new H4aKey("C644")).languages.add(new H4aKey("E890"));

        // K644 maps to E1358 (but not vice versa) when it should map to E890 instead.
        h4aHymns.get(new H4aKey("K644")).languages.remove(new H4aKey("E1358"));
        h4aHymns.get(new H4aKey("K644")).languages.add(new H4aKey("E890"));

        // C664 should not map to E921 (and vice versa). Rather, the Chinese song for E921 should be CS853
        h4aHymns.get(new H4aKey("E921")).languages.remove(new H4aKey("C664"));
        h4aHymns.get(new H4aKey("C664")).languages.remove(new H4aKey("E921"));
        h4aHymns.get(new H4aKey("C664")).languages.clear();
        h4aHymns.get(new H4aKey("E921")).languages.add(new H4aKey("CS853"));
        // Instead, C664 should actually map to E1358 (already mapped)
        h4aHymns.get(new H4aKey("C664")).languages.add(new H4aKey("E1358"));

        // This song has messed up mapping in the h4a db: it's parent hymn should be E1358, not E921.
        h4aHymns.get(new H4aKey("K664")).languages.remove(new H4aKey("E921"));
        h4aHymns.get(new H4aKey("K664")).languages.add(new H4aKey("E1358"));

        // These songs have messed up mappings in the h4a db: K217 and C217  should map to E1360, not E267.
        // C217 and K217's parent hymn already points to E1360 and E1360 is already mapped to K217 and C217. So just a
        // simple deletion is needed.
        h4aHymns.get(new H4aKey("E267")).languages.remove(new H4aKey("K217"));
        h4aHymns.get(new H4aKey("E267")).languages.remove(new H4aKey("C217"));

        // FR46 is the french version of E1360, not E267
        h4aHymns.get(new H4aKey("FR46")).languages.remove(new H4aKey("E267"));
        h4aHymns.get(new H4aKey("E267")).languages.remove(new H4aKey("FR46"));
        h4aHymns.get(new H4aKey("FR46")).languages.add(new H4aKey("E1360"));
        h4aHymns.get(new H4aKey("E1360")).languages.add(new H4aKey("FR46"));

        // This song has messed up mapping in the h4a db: E419's Korean song is K210 (already mapped), not K319.
        h4aHymns.get(new H4aKey("E419")).languages.remove(new H4aKey("K319"));
        h4aHymns.get(new H4aKey("K319")).languages.remove(new H4aKey("E419"));

        // This song has messed up mapping in the h4a db: E494's Korean song is K373 (already mapped), not K372.
        h4aHymns.get(new H4aKey("E494")).languages.remove(new H4aKey("K372"));
        h4aHymns.get(new H4aKey("K372")).languages.remove(new H4aKey("E494"));

        // This song has messed up mapping in the h4a db: it has two Japanese songs (J530 and J539). J539 is wrong.
        h4aHymns.get(new H4aKey("E734")).languages.remove(new H4aKey("J539"));
        h4aHymns.get(new H4aKey("J539")).languages.remove(new H4aKey("E734"));

        // This song has messed up mapping in the h4a db: it has two Japanese songs (J693 and J643). J643 is wrong.
        h4aHymns.get(new H4aKey("E1017")).languages.remove(new H4aKey("J643"));
        h4aHymns.get(new H4aKey("J643")).languages.remove(new H4aKey("E1017"));

        // This song has messed up mapping in the h4a db: it also maps to E29 and its languages songs, which it
        // shouldn't.
        h4aHymns.get(new H4aKey("E505")).languages.remove(new H4aKey("E29"));
        h4aHymns.get(new H4aKey("E505")).languages.remove(new H4aKey("C27"));
        h4aHymns.get(new H4aKey("E505")).languages.remove(new H4aKey("T29"));

        // C383 and E505 should not be linked
        h4aHymns.get(new H4aKey("E505")).languages.remove(new H4aKey("C383"));
        h4aHymns.get(new H4aKey("C383")).languages.remove(new H4aKey("E505"));

        // This song has messed up mapping in the h4a db: it has two Korean songs (K460 and K446). K640 is wrong.
        h4aHymns.get(new H4aKey("E605")).languages.remove(new H4aKey("K460"));
        h4aHymns.get(new H4aKey("K460")).languages.remove(new H4aKey("E605"));

        // T songs with number > 1360, but since it has a parent_hymn in the db, it fulfilled the
        // "WHERE hymn_group='T' and parent_hymn is not null" clause. Nevertheless, these are mostly just duplicates
        // of the english songs and thus should be removed.
        h4aHymns.remove(new H4aKey("T10122"));
        h4aHymns.get(new H4aKey("E1134")).languages.remove(new H4aKey("T10122"));
        h4aHymns.remove(new H4aKey("T10810"));
        h4aHymns.get(new H4aKey("E877")).languages.remove(new H4aKey("T10810"));
        h4aHymns.remove(new H4aKey("T10735"));
        h4aHymns.get(new H4aKey("E469")).languages.remove(new H4aKey("T10735"));
        h4aHymns.remove(new H4aKey("T10218"));
        h4aHymns.get(new H4aKey("E814")).languages.remove(new H4aKey("T10218"));

        // C390 should map to E517 (which it is), but it's somehow in the "languages" filed for E527. So it needs to be
        // removed.
        h4aHymns.get(new H4aKey("E527")).languages.remove(new H4aKey("C390"));

        // C643 shouldn't be mapped to any song.
        h4aHymns.get(new H4aKey("C643")).languages.clear();

        // C485 shouldn't be mapped to any song.
        h4aHymns.get(new H4aKey("C485")).languages.clear();

        // CS401 shouldn't be mapped to any song.
        h4aHymns.get(new H4aKey("CS401")).languages.clear();

        // CS16 looks like it has the same tune as E1222, but the translation seems a bit different
        h4aHymns.get(new H4aKey("E1222")).languages.remove(new H4aKey("CS16"));
        h4aHymns.get(new H4aKey("CS16")).languages.remove(new H4aKey("E1222"));

        // C68 and T79 are actually translations of the song h/8079 in hymnal db, while CB79 is the translation for E79.
        // Therefore, they should have distinct mappings that should contain no overlap.
        h4aHymns.get(new H4aKey("E79")).languages.remove(new H4aKey("C68"));
        h4aHymns.get(new H4aKey("E79")).languages.remove(new H4aKey("T79"));
        h4aHymns.get(new H4aKey("CB79")).languages.remove(new H4aKey("C68"));
        h4aHymns.get(new H4aKey("CB79")).languages.remove(new H4aKey("T79"));

        // E445 contains songs that actually should map to E1359. These songs are very similar, but distinct. Thus, we
        // need to remove the duplicate mappings.
        h4aHymns.get(new H4aKey("E445")).languages.remove(new H4aKey("C339"));
        h4aHymns.get(new H4aKey("E445")).languages.remove(new H4aKey("K339"));
        h4aHymns.get(new H4aKey("CB445")).languages.clear(); // nothing in here that isn't covered by hymnal db

        // E480 maps to C357, but C357 is actually the translation to h/8357 (covered in hymnal db). So we can safely
        // just remove the mapping here.
        h4aHymns.get(new H4aKey("E480")).languages.remove(new H4aKey("C357"));

        // These two songs are the Chinese and German translations of "God's eternal economy." These two songs error
        // out because CS1004's related song is G10001, which cannot be caught by fixGermanSongs. These both are covered
        // in hymnal db, so G10001 is safe to remove.
        h4aHymns.get(new H4aKey("CS1004")).languages.clear();
        h4aHymns.remove(new H4aKey("G10001"));

        fixGermanSongs();

        for (H4aKey h4aKey : new HashSet<>(h4aHymns.keySet())) {
            populateH4aLanguages(h4aKey);
        }

        for (H4aKey h4aKey : h4aHymns.keySet()) {
            HymnalDbKey hymnalDbKey = h4aKey.toHymnalDbKey();
            if (hymnalDbHymns.containsKey(hymnalDbKey)) {
                ContentValues contentValues = getDiff(h4aKey);
                if (contentValues.size() > 0) {
                    LOG("Updating " + h4aKey.toHymnalDbKey());
                    LOG(contentValues.toString());
                    if (!DRY_RUN) {
                        hymnalClient.getDb().update("song_data", contentValues,
                                                    "HYMN_TYPE = \"" + hymnalDbKey.hymnType.hymnalDb
                                                        + "\" AND HYMN_NUMBER = \"" + hymnalDbKey.hymnNumber
                                                        + "\" AND QUERY_PARAMS = \"" + hymnalDbKey.queryParams + "\"");
                    }
                }
            } else {
                ContentValues contentValues = writeSong(h4aKey);
                LOG("Inserting " + h4aKey.toHymnalDbKey());
                LOG(contentValues.toString());
                if (!DRY_RUN) {
                    hymnalClient.getDb().insert("song_data", contentValues);
                }
            }
        }

        for (HymnalDbKey hymnalDbKey : hymnalDbHymns.keySet()) {
            ContentValues contentValues = populateRelevantForSongsNotTouched(hymnalDbKey);
            if (contentValues != null) {
                LOG("Updating " + hymnalDbKey);
                LOG(contentValues.toString());
                if (!DRY_RUN) {
                    hymnalClient.getDb().update("song_data", contentValues,
                                                "HYMN_TYPE = \"" + hymnalDbKey.hymnType.hymnalDb
                                                    + "\" AND HYMN_NUMBER = \"" + hymnalDbKey.hymnNumber
                                                    + "\" AND QUERY_PARAMS = \"" + hymnalDbKey.queryParams + "\"");
                }
            }
        }

        if (!DRY_RUN) {
            runTests(hymnalClient);
        }

        hymnalClient.close();
    }

    private static void populateHymnalDbHymns(ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            throw new IllegalArgumentException("hymnalDb query returned null");
        }

        while (resultSet.next()) {
            HymnType hymnType = HymnType.fromHymnalDb(resultSet.getString(2));
            String hymnNumber = resultSet.getString(3);
            String queryParams = resultSet.getString(4);

            hymnalDbHymns.put(new HymnalDbKey(hymnType, hymnNumber, queryParams),
                              new ConvertedHymn(resultSet.getString(5),
                                                resultSet.getString(6),
                                                resultSet.getString(7),
                                                resultSet.getString(8),
                                                resultSet.getString(9),
                                                resultSet.getString(10),
                                                resultSet.getString(11),
                                                resultSet.getString(12),
                                                resultSet.getString(13),
                                                resultSet.getString(14),
                                                resultSet.getString(15),
                                                resultSet.getString(16),
                                                resultSet.getString(17),
                                                resultSet.getString(18),
                                                resultSet.getString(19),
                                                resultSet.getString(20)));
        }
    }

    // TODO Some song mappings are wrong in database. We can't really fix it in code, so just remember to manually
    //  fix the db file, until we figure out a more automate way...
/*
-- ch/383 and ch/383?gb=1 -> should only have each other as languages. The language mapping on hymnal.net is wrong. They don't actually match to h/505 and its related songs
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ch/383?gb=1"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="383" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"詩歌(繁)", "path":"/en/hymn/ch/383"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="383" and QUERY_PARAMS = "?gb=1";
-- ch/643 and ch/643?gb=1 -> should only have each other as languages. The language mapping on hymnal.net is wrong. They don't actually match to h/1017 and its related songs
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)","path":"/en/hymn/ch/643?gb=1"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="643" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"詩歌(繁)", "path":"/en/hymn/ch/643"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="643" and QUERY_PARAMS = "?gb=1";
-- ts/142 and ts/142?gb=1 -> mapped to h/1193 for some reason, but it actually is the chinese version of h/1198. So it should map to h/1198 and its related songs
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ts/142?gb=1"}, {"value":"English", "path":"/en/hymn/h/1198"}, {"value":"Cebuano", "path":"/en/hymn/cb/1198"}]}' WHERE HYMN_TYPE="ts" AND HYMN_NUMBER="142" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"詩歌(繁)", "path":"/en/hymn/ts/142"}, {"value":"English", "path":"/en/hymn/h/1198"}, {"value":"Cebuano", "path":"/en/hymn/cb/1198"}]}' WHERE HYMN_TYPE="ts" AND HYMN_NUMBER="142" and QUERY_PARAMS = "?gb=1";
-- ts/253 and ts/253?gb=1 -> mapped to h/754 for some reason, but it actually is the chinese version of h/1164. So it should map to h/1164 and its related songs
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ts/253?gb=1"}, {"value":"English", "path":"/en/hymn/h/1164"}, {"value":"Cebuano", "path":"/en/hymn/cb/1164"}]}' WHERE HYMN_TYPE="ts" AND HYMN_NUMBER="253" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"詩歌(繁)", "path":"/en/hymn/ts/253"}, {"value":"English", "path":"/en/hymn/h/1164"}, {"value":"Cebuano", "path":"/en/hymn/cb/1164"}]}' WHERE HYMN_TYPE="ts" AND HYMN_NUMBER="253" and QUERY_PARAMS = "?gb=1";
-- h/8438 -> set languages json to null. The language mapping on hymnal.net is wrong. They don't actually match to h/754 and its related songs ch/439 and its related songs
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL WHERE HYMN_TYPE="h" AND HYMN_NUMBER="8438";
-- h/379 -> set languages json to null. The language mapping on hymnal.net is wrong. They don't actually match to ch/385 and its related songs ch/439 and its related songs
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL WHERE HYMN_TYPE="h" AND HYMN_NUMBER="379";
-- h/1111 -> languages should link to ch/1111 instead of ch/8111. The language mapping on hymnal.net is wrong.
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ch/1111?gb=1"}, {"value":"詩歌(繁)","path":"/en/hymn/ch/1111"}]}' WHERE HYMN_TYPE="h" AND HYMN_NUMBER="1111";
-- Delete ch/8111 and ch/8111?gb=1 for above reason ^^
DELETE FROM SONG_DATA WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="8111";
-- hf/15 is mapped to h/473 for some reason, but it actually is the french version of h/1084. So we need to update the languages json and copy over Author(null), Composer(null), Key(F Major), Time(3/4), Meter(8.8.8.8), Hymn Code(51712165172321), Scriptures (Song of Songs) from h/1084
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ts/302?gb=1"}, {"value":"詩歌(繁)", "path":"/en/hymn/ts/302"}, {"value":"Dutch", "path":"/en/hymn/hd/19"}, {"value":"English", "path":"/en/hymn/h/1084"}]}', SONG_META_DATA_AUTHOR = NULL, SONG_META_DATA_COMPOSER = NULL, SONG_META_DATA_KEY = "F Major", SONG_META_DATA_TIME = "3/4", SONG_META_DATA_METER = "8.8.8.8", SONG_META_DATA_HYMN_CODE = "51712165172321", SONG_META_DATA_SCRIPTURES = "Song of Songs" WHERE HYMN_TYPE="hf" AND HYMN_NUMBER="15";
-- h/8079 and h/79 are related. However, the language mappings for each is all messed up.
-- Here is the current mapping: h/79->cb/79,ch/68,ht/79; cb/79->h/79,ch/68,ht/79; h/8079->cb/79,ch/68,ht/79; ht/79->h/79,ch/68,cb/79; ch/68->h/8079,cb/79,ht/79
-- Here is the correct mapping: h/79->cb/79; cb/79->h/79; h/8079->ch/68,ht/79; ht/79->h/8079,ch/68; ch/68->h/8079,ht/79
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"Cebuano", "path":"/en/hymn/cb/79"}]}' WHERE HYMN_TYPE="h" AND HYMN_NUMBER="79" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/79"}]}' WHERE HYMN_TYPE="cb" AND HYMN_NUMBER="79" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ch/68?gb=1"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/68"}, {"value":"Tagalog", "path":"/en/hymn/ht/79"}]}' WHERE HYMN_TYPE="h" AND HYMN_NUMBER="8079" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/8079"}, {"value":"诗歌(简)", "path":"/en/hymn/ch/68?gb=1"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/68"}]}' WHERE HYMN_TYPE="ht" AND HYMN_NUMBER="79" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/8079"}, {"value":"Tagalog", "path":"/en/hymn/ht/79"}, {"value":"诗歌(简)", "path":"/en/hymn/ch/68?gb=1"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="68" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/8079"}, {"value":"Tagalog", "path":"/en/hymn/ht/79"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/68"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="68" and QUERY_PARAMS = "?gb=1";
-- ch/632 is mapped to h/870b, which is an alternate tune of h/870. So we need to fix that and make it map to h/870 instead
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"詩歌(繁)","path":"/en/hymn/ch/632"}, {"value":"Cebuano","path":"/en/hymn/cb/870"}, {"value":"English","path":"/en/hymn/h/870"}, {"value":"Tagalog","path":"/en/hymn/ht/870"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="632" and QUERY_PARAMS = "?gb=1";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)","path":"/en/hymn/ch/632?gb=1"}, {"value":"Cebuano","path":"/en/hymn/cb/870"}, {"value":"English","path":"/en/hymn/h/870"}, {"value":"Tagalog","path":"/en/hymn/ht/870"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="632" and QUERY_PARAMS = "";
-- ts/248 is mapped to h/300b, which is an alternate tune of h/300. So we need to fix that and make it map to h/300 instead
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"詩歌(繁)","path":"/en/hymn/ts/248"}, {"value":"Cebuano","path":"/en/hymn/cb/300"}, {"value":"English","path":"/en/hymn/h/300"}, {"value":"Tagalog","path":"/en/hymn/ht/300"}]}' WHERE HYMN_TYPE="ts" AND HYMN_NUMBER="248" and QUERY_PARAMS = "?gb=1";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)","path":"/en/hymn/ts/248?gb=1"}, {"value":"Cebuano","path":"/en/hymn/cb/300"}, {"value":"English","path":"/en/hymn/h/300"}, {"value":"Tagalog","path":"/en/hymn/ht/300"}]}' WHERE HYMN_TYPE="ts" AND HYMN_NUMBER="248" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL WHERE HYMN_TYPE="h" AND HYMN_NUMBER="300b" and QUERY_PARAMS = "";
-- fr/129 and ch/476 are mapped to h/8476, which is an alternate tune of h/1353. So we need to fix that and make it map to h/1353 instead
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)","path":"/en/hymn/ch/476?gb=1"}, {"value":"詩歌(繁)","path":"/en/hymn/ch/476"}, {"value":"English","path":"/en/hymn/h/1353"}, {"value":"Tagalog","path":"/en/hymn/ht/1353"}]}' WHERE HYMN_TYPE="hf" AND HYMN_NUMBER="129" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"詩歌(繁)","path":"/en/hymn/ch/476"}, {"value":"French","path":"/en/hymn/hf/129"}, {"value":"English","path":"/en/hymn/h/1353"}, {"value":"Tagalog","path":"/en/hymn/ht/1353"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="476" and QUERY_PARAMS = "?gb=1";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)","path":"/en/hymn/ch/476?gb=1"}, {"value":"French","path":"/en/hymn/hf/129"}, {"value":"English","path":"/en/hymn/h/1353"}, {"value":"Tagalog","path":"/en/hymn/ht/1353"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="476" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL WHERE HYMN_TYPE="h" AND HYMN_NUMBER="8476" and QUERY_PARAMS = "";
-- h/267 and h/1360 are related. However, the language mappings for each is all messed up.
-- Here is the current mapping: h/267->cb/267,ch/217,ht/267; cb/267->h/267,ch/217,ht/267; h/1360->cb/267,ch/217,ht/1360; ht/267->h/267,cb/267,ch/217; ch/217->h/1360,cb/267,ht/1360; ht/1360->cb/267,ch/217,h/1360; hf/46->cb/267,ch/217,h/267,de/267,ht/267; de267->cb/267,ch/217,h/267,hf/46,ht/267
-- Here is the correct mapping: h/267->cb/267,ht/267; cb/267->h/267,ht/267; ht/267->h/267,cb/267; h/1360->ch/217,ht/1360; ch/217->h/1360,ht/1360; ht/1360->ch/217,h/1360; hf/46->ch/217,h/1360,ht/1360; de/267->cb/267,h/267,ht/267
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"Cebuano", "path":"/en/hymn/cb/267"}, {"value":"Tagalog", "path":"/en/hymn/ht/267"}]}' WHERE HYMN_TYPE="h" AND HYMN_NUMBER="267" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/267"}, {"value":"Tagalog", "path":"/en/hymn/ht/267"}]}' WHERE HYMN_TYPE="cb" AND HYMN_NUMBER="267" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/267"}, {"value":"Cebuano", "path":"/en/hymn/cb/267"}]}' WHERE HYMN_TYPE="ht" AND HYMN_NUMBER="267" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ch/217?gb=1"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/217"}, {"value":"Tagalog", "path":"/en/hymn/ht/1360"}]}' WHERE HYMN_TYPE="h" AND HYMN_NUMBER="1360" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ch/217?gb=1"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/217"}, {"value":"Tagalog", "path":"/en/hymn/ht/1360"}]}' WHERE HYMN_TYPE="h" AND HYMN_NUMBER="1360" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/1360"}, {"value":"Tagalog", "path":"/en/hymn/ht/1360"}, {"value":"诗歌(简)", "path":"/en/hymn/ch/217?gb=1"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="217" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/1360"}, {"value":"Tagalog", "path":"/en/hymn/ht/1360"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/217"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="217" and QUERY_PARAMS = "?gb=1";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/1360"}, {"value":"诗歌(简)", "path":"/en/hymn/ch/217?gb=1"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/217"}]}' WHERE HYMN_TYPE="ht" AND HYMN_NUMBER="1360" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ch/217?gb=1"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/217"}, {"value":"English", "path":"/en/hymn/h/1360"}, {"value":"Tagalog", "path":"/en/hymn/ht/1360"}]}' WHERE HYMN_TYPE="hf" AND HYMN_NUMBER="46" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"Cebuano", "path":"/en/hymn/cb/267"}, {"value":"English", "path":"/en/hymn/h/267"}, {"value":"Tagalog", "path":"/en/hymn/ht/267"}]}' WHERE HYMN_TYPE="de" AND HYMN_NUMBER="267" and QUERY_PARAMS = "";
-- h/445 and h/1359 are related. However, the language mappings for each is all messed up.
-- Here is the current mapping: h/445->cb/445,ch/339,ht/1359; cb/445->h/445,ch/339,ht/1359; ht/445->cb/445,ch/339,h/445; h/1359->cb/445,ch/339,ht/1359; ch/339->h/1359,cb/445,ht/1359; ht/1359->h/1359,cb/445,ch/339; de/445->cb/445,ch/339,h/445,hf/79,ht/1359; hf/79->h/445,cb/445,ch/339,de/445,ht/1359
-- Here is the correct mapping: h/445->cb/445,ht/445; cb/445->h/445,ht/445; ht/445->h/445,cb/445; h/1359->ch/339,ht/1359; ch/339->h/1359,ht/1359; ht/1359->h/1359,ch/339; de/445->h/445,cb/445,hf/79,ht/445; hf/79->h/445,cb/445,de/445,ht/445
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"Cebuano", "path":"/en/hymn/cb/445"}, {"value":"Tagalog", "path":"/en/hymn/ht/445"}]}' WHERE HYMN_TYPE="h" AND HYMN_NUMBER="445" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/445"}, {"value":"Tagalog", "path":"/en/hymn/ht/445"}]}' WHERE HYMN_TYPE="cb" AND HYMN_NUMBER="445" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/445"}, {"value":"Cebuano", "path":"/en/hymn/cb/445"}]}' WHERE HYMN_TYPE="ht" AND HYMN_NUMBER="445" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ch/339?gb=1"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/339"}, {"value":"Tagalog", "path":"/en/hymn/ht/1359"}]}' WHERE HYMN_TYPE="h" AND HYMN_NUMBER="1359" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/1359"}, {"value":"Tagalog", "path":"/en/hymn/ht/1359"}, {"value":"诗歌(简)", "path":"/en/hymn/ch/339?gb=1"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="339" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/1359"}, {"value":"Tagalog", "path":"/en/hymn/ht/1359"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/339"}]}' WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="339" and QUERY_PARAMS = "?gb=1";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/1359"}, {"value":"诗歌(简)", "path":"/en/hymn/ch/339?gb=1"}, {"value":"詩歌(繁)", "path":"/en/hymn/ch/339"}]}' WHERE HYMN_TYPE="ht" AND HYMN_NUMBER="1359" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/445"}, {"value":"Cebuano", "path":"/en/hymn/cb/445"}, {"value":"German", "path":"/en/hymn/de/445"}, {"value":"Tagalog", "path":"/en/hymn/ht/445"}]}' WHERE HYMN_TYPE="de" AND HYMN_NUMBER="445" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"English", "path":"/en/hymn/h/445"}, {"value":"Cebuano", "path":"/en/hymn/cb/445"}, {"value":"French", "path":"/en/hymn/hf/79"}, {"value":"Tagalog", "path":"/en/hymn/ht/445"}]}' WHERE HYMN_TYPE="hf" AND HYMN_NUMBER="79" and QUERY_PARAMS = "";
-- ch/357 is mapped to h/8357 and vice-versa, which is correct. However, h/480 also maps to ch/357, which is incorrect since ch/357 is the translation for h/8357, not h/480
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL WHERE HYMN_TYPE="h" AND HYMN_NUMBER="480" and QUERY_PARAMS = "";
*/
    private static void populateHymnalDbLanguages(HymnalDbKey hymnalDbKey) {
        Set<HymnalDbKey> allLanguages = new HashSet<>();
        populateHymnalDbLanguagesHelper(hymnalDbKey, allLanguages);

        /* Audit allLanguages to see if there are conflicting types */
        List<HymnType> relevantTypes = new ArrayList<>();
        for (HymnalDbKey key : allLanguages) {
            relevantTypes.add(key.hymnType);
        }

        // Verify that the same hymn type doesn't appear more than the allowed number of times the languages list.
        outerLoop:
        for (HymnType hymnType : HymnType.values()) {
            int timesAllowed = 1;
            if (hymnType == HymnType.CHINESE || hymnType == HymnType.CHINESE_SUPPLEMENT) {
                timesAllowed = 2;
            }
            if (hymnType == HymnType.CLASSIC_HYMN || hymnType == HymnType.NEW_SONG) {
                for (HymnalDbKey key : allLanguages) {
                    String number = key.hymnNumber;
                    if (number.matches("(\\D+\\d+\\D*)|(\\D*\\d+\\D+)")) {
                        continue outerLoop;
                    }
                }
            }

            for (Set<HymnalDbKey> exception : hymnalDbExceptions) {
                if (allLanguages.containsAll(exception)) {
                    continue outerLoop;
                }
            }

            if (Collections.frequency(relevantTypes, hymnType) > timesAllowed) {
                throw new IllegalArgumentException(
                    String.format("%s has too many instances of %s: %s", hymnalDbKey, hymnType, allLanguages));
            }
        }

        // Verify that conflicting hymn types don't appear more together the languages list.
        if ((relevantTypes.contains(HymnType.CLASSIC_HYMN) && relevantTypes.contains(HymnType.NEW_SONG))
            || (relevantTypes.contains(HymnType.CLASSIC_HYMN) && relevantTypes.contains(HymnType.CHILDREN_SONG))
            || relevantTypes.contains(HymnType.CHILDREN_SONG) && relevantTypes.contains(HymnType.NEW_SONG)
            || relevantTypes.contains(HymnType.CHINESE) && relevantTypes.contains(HymnType.CHINESE_SUPPLEMENT)) {

            boolean isHymnalDbException = false;
            for (Set<HymnalDbKey> exception : hymnalDbExceptions) {
                if (allLanguages.containsAll(exception)) {
                    isHymnalDbException = true;
                }
            }
            if (!isHymnalDbException) {
                throw new IllegalArgumentException(
                    String.format("%s has conflicting languages types: %s", hymnalDbKey, allLanguages.toString()));
            }
        }
        /* Audit complete */

        addHymnalDbLanguages(hymnalDbKey, allLanguages); // populate "languages" field
        ConvertedHymn hymn = hymnalDbHymns.get(hymnalDbKey);
        for (H4aKey relevant : hymn.languages) {
            // populate all languages songs' "languages" fields
            addHymnalDbLanguages(relevant.toHymnalDbKey(), allLanguages);
        }
    }

    private static void populateHymnalDbLanguagesHelper(HymnalDbKey hymnalDbKey, Set<HymnalDbKey> allLanguages) {
        if (allLanguages.contains(hymnalDbKey)) {
            return;
        }

        if (!hymnalDbHymns.containsKey(hymnalDbKey)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", hymnalDbKey.toString()));
        }

        allLanguages.add(hymnalDbKey);

        ConvertedHymn hymnalDbHymn = hymnalDbHymns.get(hymnalDbKey);
        Languages languages = new Gson().fromJson(hymnalDbHymn.languagesJson, Languages.class);
        if (languages != null) {
            List<HymnalDbKey> related = languages.getData().stream().map(datum -> {
                String path = datum.getPath();
                return HymnalDbKey.extractFromPath(path);
            }).collect(Collectors.toList());

            for (HymnalDbKey relevantId : new ArrayList<>(related)) {
                populateHymnalDbLanguagesHelper(relevantId, allLanguages);
            }
        }
    }

    private static void addHymnalDbLanguages(HymnalDbKey key, Set<HymnalDbKey> allLanguages) {
        if (!hymnalDbHymns.containsKey(key)) {
            return;
        }

        ConvertedHymn convertedHymn = hymnalDbHymns.get(key);
        for (HymnalDbKey relevant : allLanguages) {
            H4aKey h4aKey = relevant.toH4aKey();
            if (h4aKey != null && !relevant.equals(key) && !convertedHymn.languages.contains(h4aKey)) {
                convertedHymn.languages.add(h4aKey);
            }
        }
    }

    private static void populateH4aHymns(ResultSet resultSet)
        throws SQLException, BadHanyuPinyinOutputFormatCombination {
        if (resultSet == null) {
            throw new IllegalArgumentException("h4a query returned null");
        }

        while (resultSet.next()) {
            String id = resultSet.getString(1);
            H4aKey key = new H4aKey(id);

            // BF188 is a legitimate song, but its parent hymn is CH93, which doesn't exist in h4a. However, this
            // song is covered by the hymnal db, so we can safely ignore it.
            // TODO remove if we commit to skipping BF songs, keep if we don't skip BF songs.
            // if ("BF188".equals(id)) {
            //     continue;
            // }

            String author = resultSet.getString(2);
            if (!TextUtils.isEmpty(author) && author.equals("*")) {
                author = "LSM";
            }
            String composer = resultSet.getString(3);
            // Some composers (e.g. 1151) have "Arranged by" before the real composer. This filters out that part.
            if (!TextUtils.isEmpty(composer)) {
                composer = composer.replace("Arranged by ", "");
            }

            String firstStanzaLine = resultSet.getString(5);

            List<String> lyrics = new ArrayList<>();
            ResultSet stanzas = new DatabaseClient(H4A_DB_NAME, 111).getDb().rawQuery(
                "SELECT * FROM stanza WHERE parent_hymn='" + id + "' ORDER BY n_order");
            if (stanzas == null) {
                throw new IllegalArgumentException("h4a stanzas query returned null");
            }
            while (stanzas.next()) {
                String stanzaNumber = stanzas.getString(2);
                String text = stanzas.getString(3);
                String note = stanzas.getString(4);

                // creates a verse object with the stanza num and content
                Map<String, String> verse = new HashMap<>();

                if ("chorus".equals(stanzaNumber)) {
                    verse.put(Constants.VERSE_TYPE, Constants.CHORUS);
                } else {
                    verse.put(Constants.VERSE_TYPE, Constants.VERSE);
                }

                List<String> verseContent = new ArrayList<>();
                String[] lines = text.split("<br/>");
                for (String line : lines) {
                    if (TextUtils.isEmpty(line)) {
                        continue;
                    }
                    // Use a custom escape method, since GSON will auto-escape strings and screw everything up. Right
                    // before we save the value, we will undo the custom escape character and replace it with the
                    // standard \".
                    line = line.replace("\"", CUSTOM_ESCAPE + "\"");
                    verseContent.add(line);
                }

                verse.put(Constants.VERSE_CONTENT, toJsonString(verseContent));
                if (!TextUtils.isEmpty(note)) {
                    verse.put(Constants.NOTE, note);
                }

                if (key.isTransliterable()) {
                    List<String> transliteratedLines = new ArrayList<>();
                    for (String line : verseContent) {
                        StringBuilder transliteratedLine = new StringBuilder();
                        char[] transliterableChars = line.toCharArray();
                        for (char transliterableChar : transliterableChars) {
                            HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
                            format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
                            format.setToneType(HanyuPinyinToneType.WITH_TONE_MARK);
                            format.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);
                            String[] transliteratedArray
                                = PinyinHelper.toHanyuPinyinStringArray(transliterableChar, format);

                            if (transliteratedArray == null) {
                                transliteratedLine.append(transliterableChar);
                                continue;
                            }

                            String transliterated = transliteratedArray[0];
                            if (transliterated.contains("none")) {
                                throw new IllegalArgumentException(
                                    transliterableChar + " was not able to be transliterated");
                            }
                            transliteratedLine.append(transliterated);
                        }
                        transliteratedLines.add(transliteratedLine.toString());
                    }

                    verse.put(Constants.VERSE_TRANSLITERATION, toJsonString(transliteratedLines));
                }

                lyrics.add(toJsonString(verse));
            }
            String lyricsJson = toJsonString(lyrics).replace(CUSTOM_ESCAPE + "\"", "\\\"");
            if (TextUtils.isEmpty(lyricsJson)) {
                throw new IllegalArgumentException("lyrics empty for " + key);
            }

            String musicKey = resultSet.getString(7);
            String mainCategory = resultSet.getString(8);
            String meter = resultSet.getString(9);
            String subCategory = resultSet.getString(11);
            if (!TextUtils.isEmpty(mainCategory) && !TextUtils.isEmpty(subCategory) && mainCategory.toLowerCase().equals(subCategory.toLowerCase())) {
                subCategory = null;
            }
            String time = resultSet.getString(12);
            String tune = resultSet.getString(13);
            String parentHymnId = resultSet.getString(14);

            // Weird mapping in h4a where G10001 maps to the Chinese version of "God's eternal economy" instead of the English.
            if (id.equals("G10001")) {
                parentHymnId = "NS180";
            }

            String sheetMusicLink = resultSet.getString(15);
            String svgJson = null;
            if (!TextUtils.isEmpty(sheetMusicLink)) {
                Map<String, String> leadSheet = new HashMap<>();
                leadSheet.put(Constants.NAME, "svg");

                List<Map<String, String>> leadSheetData = new ArrayList<>();
                leadSheetData.add(Map.of("path", sheetMusicLink, "value", "Guitar"));
                leadSheetData.add(Map.of("path", sheetMusicLink.replace("p.svg", "g.svg"), "value", "Piano"));

                leadSheet.put(Constants.DATA, toJsonString(leadSheetData));
                svgJson = new GsonBuilder().disableHtmlEscaping().create().toJson(leadSheet)
                                           .replace("\\\"", "\"")
                                           .replace("\"[", "[")
                                           .replace("]\"", "]");
            }

            String verse = resultSet.getString(16);
            StringBuilder scriptures = new StringBuilder();
            if (!TextUtils.isEmpty(verse)) {
                String[] verseReferences = verse.split(",");
                for (String verseReference : verseReferences) {
                    if (!TextUtils.isEmpty(verseReference)) {
                        continue;
                    }
                    scriptures.append(verseReference).append(";");
                }
            }
            if (scriptures.length() == 0) {
                scriptures = null;
            }

            List<H4aKey> related = new ArrayList<>();
            if (!TextUtils.isEmpty(resultSet.getString(17))) {
                for (String relatedSong : resultSet.getString(17).split(",")) {
                    if (!TextUtils.isEmpty(relatedSong)) {
                        related.add(new H4aKey(relatedSong));
                    }
                }
            }

            H4aKey parentHymn = null;
            if (!TextUtils.isEmpty(parentHymnId)) {
                parentHymn = new H4aKey(parentHymnId);
                if (!related.contains(parentHymn)) {
                    related.add(parentHymn);
                }
            }

            h4aHymns.put(new H4aKey(id),
                         new ConvertedHymn(firstStanzaLine,
                                           lyricsJson,
                                           mainCategory,
                                           subCategory,
                                           author,
                                           composer,
                                           musicKey,
                                           time,
                                           meter,
                                           scriptures != null ? scriptures.toString() : null,
                                           tune,
                                           null,
                                           svgJson,
                                           null,
                                           related,
                                           parentHymn));
        }
    }

    private static void populateH4aLanguages(H4aKey h4aKey) {
        Set<H4aKey> allLanguages = new HashSet<>();
        populateH4aLanguagesHelper(h4aKey, allLanguages);
        verifyOnlyOneInstanceOfEachType(h4aKey, allLanguages);

        // Audit allLanguages to see if there are conflicting types
        List<HymnType> relevantTypes = new ArrayList<>();
        for (H4aKey key : allLanguages) {
            relevantTypes.add(key.type());
        }

        boolean isHymnalDbException = false;
        for (Set<HymnalDbKey> exception : hymnalDbExceptions) {
            Set<H4aKey> transformed = exception.stream().map(HymnalDbKey::toH4aKey).collect(Collectors.toSet());
            if (allLanguages.containsAll(transformed)) {
                isHymnalDbException = true;
            }
        }

        if (!isHymnalDbException) {
            if (relevantTypes.contains(HymnType.CLASSIC_HYMN) && relevantTypes.contains(HymnType.NEW_SONG)) {
                throw new IllegalArgumentException(
                    String.format("%s has both E and NS languages songs %s", h4aKey.id, allLanguages.toString()));
            }

            if (relevantTypes.contains(HymnType.CLASSIC_HYMN) && relevantTypes.contains(HymnType.CHILDREN_SONG)) {
                throw new IllegalArgumentException(
                    String.format("%s has both E and CH languages songs %s", h4aKey.id, allLanguages.toString()));
            }

            if (relevantTypes.contains(HymnType.CHILDREN_SONG) && relevantTypes.contains(HymnType.NEW_SONG)) {
                throw new IllegalArgumentException(
                    String.format("%s has both CH and NS languages songs %s", h4aKey.id, allLanguages.toString()));
            }

            if (relevantTypes.contains(HymnType.CHINESE) && relevantTypes.contains(HymnType.CHINESE_SUPPLEMENT)) {
                throw new IllegalArgumentException(
                    String.format("%s has both C and CS languages songs %s", h4aKey.id, allLanguages.toString()));
            }
        }

        addH4aRelevant(h4aKey, allLanguages); // populate "languages" field
        ConvertedHymn hymn = h4aHymns.get(h4aKey);
        for (H4aKey relevant : hymn.languages) {
            // populate all languages songs' "languages" fields
            addH4aRelevant(relevant, allLanguages);
        }
    }

    private static void populateH4aLanguagesHelper(H4aKey h4aKey, Set<H4aKey> allLanguages) {
        if (h4aKey.type() == HymnType.BE_FILLED) {
            return;
        }

        if (!h4aHymns.containsKey(h4aKey)) {
            throw new IllegalArgumentException(h4aKey + " not found in hymns");
        }

        ConvertedHymn h4aHymn = h4aHymns.get(h4aKey);
        List<H4aKey> relevant = h4aHymn.languages;

        // High-level: remove the stuff that shouldn't be there
        //
        // Look in hymnal db and remove all languages types that already exist in the hymnal db mapping. This is because
        // there are a lot of messed up mappings in h4a, and if they already exist in the hymnal db mapping, then we
        // can disregard it now and save some headache later.
        if (hymnalDbHymns.containsKey(h4aKey.toHymnalDbKey())) {
            ConvertedHymn hymnalDbHymn = hymnalDbHymns.get(h4aKey.toHymnalDbKey());
            for (H4aKey h4aRelevant : new ArrayList<>(relevant){{add(h4aKey);}}) {
                for (H4aKey hymnalDbRelevant : hymnalDbHymn.languages) {
                    if (hymnalDbRelevant.isSameType(h4aRelevant) && !hymnalDbHymn.languages.contains(h4aRelevant)) {
                        relevant.remove(h4aRelevant);
                    }
                }
            }
        }

        // Remove all BE_FILLED songs because those are too unpredictable (some are messed up, some are just duplicates,
        // and some have sketchy mappings)
        // Also remove all UNKNOWN songs
        for (H4aKey h4aRelevant : new ArrayList<>(relevant)) {
            if (h4aRelevant.type() == HymnType.BE_FILLED || h4aRelevant.type() == HymnType.UNKNOWN) {
                relevant.remove(h4aRelevant);
            }
        }

        outerLoop:
        for (H4aKey relevantId : new ArrayList<>(relevant)) {
            // songs that show up in "languages" column but don't actually exist in the h4a db. These should be
            // ignored since they map to nothing.
            List<String> ignore =
                Arrays.asList("R509", "C825", "C914", "ES437", "C912", "C389", "C834", "T898", "C815", "C486", "ES300",
                              "C806", "C905", "BF1040", "C856", "ES140", "C812", "C810", "C850", "C901", "C517c",
                              "C510c", "C513c", "CB57", "ES500", "ES422", "ES421", "ES261", "ES221", "ES164",
                              "ES163", "C925", "C917", "C840", "CS352", "CS158", "CB1360", "C506c", "CB381", "CS46",
                              "C481c", "CS9117", "CS400", "CS46");

            if (ignore.contains(relevantId.id)) {
                relevant.remove(relevantId);
                continue;
            } else if (relevantId.id.equals("C485") || relevantId.id.equals("NS6")) {
                // Legitimate songs, but aren't actually related to anything. Therefore, they should be removed from
                // other songs' "languages" mappings.
                relevant.remove(relevantId);
                continue;
            } else if (relevantId.id.equals("C426")) {
                // C426 is a legitimate song, but its mapping in NS577 is wrong. It should be CS426. However, this is
                // covered by the hymnal db, so we can safely ignore this.
                relevant.remove(relevantId);
                continue;
            } else if (relevantId.id.equals("C305")) {
                // C305 is a legitimate song, but its mapping in BF84 is wrong. It should be CS305. However, this is
                // covered by the hymnal db, so we can safely ignore this.
                relevant.remove(relevantId);
                continue;
            } else if (relevantId.type().equals(HymnType.TAGALOG) && Integer.parseInt(relevantId.number()) > 1360) {
                // songs > T1360 are often times just repeats of their English counterpart or with small
                // insignificant changes. Even when they're not repeats, they're not very useful songs. Therefore, we
                // just ignore them.
                relevant.remove(relevantId);
                continue;
            } else if (relevantId.type().equals(HymnType.SPANISH_MISTYPED)) {
                relevant.remove(relevantId);
                relevantId = new H4aKey(HymnType.SPANISH, relevantId.number());
                relevant.add(relevantId);
                allLanguages.add(relevantId);
                // do not continue since this is a correction and we should add the correct languages hymn.
            }

            if (relevantId.type().equals(HymnType.CHINESE) || relevantId.type().equals(HymnType.CHINESE_SUPPLEMENT)) {
                if (hymnalDbHymns.containsKey(h4aKey.toHymnalDbKey())) {
                    ConvertedHymn hymnalDbHymn = hymnalDbHymns.get(h4aKey.toHymnalDbKey());
                    Languages languages = new Gson().fromJson(hymnalDbHymn.languagesJson, Languages.class);
                    if (languages != null) {
                        List<String> paths = languages.getPaths();
                        for (String path : paths) {
                            if ((relevantId.type().equals(HymnType.CHINESE) && path.contains("ts")) ||
                                (relevantId.type().equals(HymnType.CHINESE_SUPPLEMENT) && path.contains("ch")) ||
                                (path.matches(".*\\d+c.*"))) {
                                // Type in h4a is incorrect since it directly conflicts with the type found in the hymnal db
                                // song.
                                relevant.remove(relevantId);
                                continue outerLoop;
                            }
                        }
                    }

                }

                if (h4aHymn.parentHymn != null && hymnalDbHymns.containsKey(h4aHymn.parentHymn.toHymnalDbKey())) {
                    ConvertedHymn hymnalDbHymn = hymnalDbHymns.get(h4aHymn.parentHymn.toHymnalDbKey());
                    Languages languages = new Gson().fromJson(hymnalDbHymn.languagesJson, Languages.class);
                    if (languages != null) {
                        List<String> paths = languages.getPaths();
                        for (String path : paths) {
                            if ((relevantId.type().equals(HymnType.CHINESE) && path.contains("ts")) ||
                                (relevantId.type().equals(HymnType.CHINESE_SUPPLEMENT) && path.contains("ch"))||
                                (path.matches(".*\\d+c.*"))) {
                                // Type in h4a is incorrect since it directly conflicts with the type found in the hymnal db
                                // classic song.
                                relevant.remove(relevantId);
                                continue outerLoop;
                            }
                        }
                    }
                }
            }
        }
        // End of removal

        if (allLanguages.contains(h4aKey)) {
            return;
        }

        allLanguages.add(h4aKey);
        for (H4aKey relevantId : relevant) {
            populateH4aLanguagesHelper(relevantId, allLanguages);
        }
    }

    private static void verifyOnlyOneInstanceOfEachType(H4aKey h4aKey, Set<H4aKey> allLanguages) {
        List<HymnType> relevantTypes = new ArrayList<>();
        for (H4aKey key : allLanguages) {
            relevantTypes.add(key.type());
        }

        outerLoop:
        for (HymnType hymnType : HymnType.values()) {
            if (Collections.frequency(relevantTypes, hymnType) > 1) {
                for (Set<HymnalDbKey> exception : hymnalDbExceptions) {
                    Set<H4aKey> transformed = exception.stream().map(HymnalDbKey::toH4aKey).collect(Collectors.toSet());
                    if (allLanguages.containsAll(transformed)) {
                        continue outerLoop;
                    }
                }
                throw new IllegalArgumentException(
                    String.format("%s contains two %s songs %s", h4aKey.id, hymnType, allLanguages.toString()));
            }
        }
    }

    private static void addH4aRelevant(H4aKey key, Set<H4aKey> allLanguages) {
        if (!h4aHymns.containsKey(key)) {
            return;
        }

        ConvertedHymn convertedHymn = h4aHymns.get(key);
        for (H4aKey relevant : allLanguages) {
            if (!relevant.equals(key) && !convertedHymn.languages.contains(relevant)) {
                convertedHymn.languages.add(relevant);
            }
        }
    }

    /**
     * German songs in H4A has incorrect numbering compared to the German songs in hymnal db. Therefore, we need some
     * way to reconcile them.
     */
    private static void fixGermanSongs() {
        boolean somethingChanged;
        do {
            somethingChanged = false;
            for (H4aKey h4aKey : new HashSet<>(h4aHymns.keySet())) {
                // if not a german song, continue
                if (h4aKey.type() != HymnType.GERMAN) {
                    continue;
                }

                ConvertedHymn h4aHymn = h4aHymns.get(h4aKey);
                // if it doesn't have a parent hymn, then there's no way to reconcile the numbering
                if (h4aHymn.parentHymn == null) {
                    continue;
                }

                String h4aNumber = h4aKey.number();
                HymnType parentType = h4aHymn.parentHymn.type();
                String parentNumber = h4aHymn.parentHymn.number();

                // number is already correct
                if (parentType == HymnType.CLASSIC_HYMN && h4aNumber.equals(parentNumber)) {
                    continue;
                }

                if (parentType == HymnType.NEW_SONG && h4aNumber.equals(parentNumber + "de")) {
                    continue;
                }

                final H4aKey newId;
                if (parentType == HymnType.CLASSIC_HYMN) {
                    newId = new H4aKey("G" + parentNumber);
                } else if (parentType == HymnType.NEW_SONG) {
                    newId = new H4aKey("G" + parentNumber + "de");
                } else {
                    throw new IllegalArgumentException("Parent hymn is not classic or new song");
                }

                if (h4aHymns.containsKey(newId)) {
                    H4aKey tempId = new H4aKey(h4aKey + "temp");
                    h4aHymns.get(h4aHymn.parentHymn).languages.remove(h4aKey);
                    h4aHymns.get(h4aHymn.parentHymn).languages.add(tempId);
                    h4aHymns.remove(h4aKey);
                    h4aHymns.put(tempId, h4aHymn);
                    somethingChanged = true;
                } else {
                    h4aHymns.get(h4aHymn.parentHymn).languages.remove(h4aKey);
                    h4aHymns.get(h4aHymn.parentHymn).languages.add(newId);
                    h4aHymns.remove(h4aKey);
                    h4aHymns.put(newId, h4aHymn);
                    somethingChanged = true;
                }
            }
        } while (somethingChanged);

        // audit top make sure all the temporary keys have been reassigned
        for (H4aKey h4aKey : new HashSet<>(h4aHymns.keySet())) {
            if (h4aKey.type() != HymnType.GERMAN) {
                continue;
            }

            if (h4aKey.id.contains("temp")) {
                throw new IllegalArgumentException("Temporary remained after key reconciliation");
            }
        }
    }

    private static ContentValues getDiff(H4aKey h4aKey) {
        HymnalDbKey hymnalDbKey = h4aKey.toHymnalDbKey();
        ConvertedHymn hymnalDbHymn = hymnalDbHymns.get(hymnalDbKey);
        ConvertedHymn h4aHymn = h4aHymns.get(h4aKey);

        ContentValues contentValues = new ContentValues();

        if (TextUtils.isEmpty(hymnalDbHymn.lyricsJson)) {
            throw new IllegalArgumentException(
                String.format("%s has empty lyrics json", hymnalDbKey));
        }

        if (TextUtils.isEmpty(hymnalDbHymn.category) && !TextUtils.isEmpty(h4aHymn.category)) {
            contentValues.put("SONG_META_DATA_CATEGORY", TextUtils.escapeSingleQuotes(h4aHymn.category));
        }

        if (TextUtils.isEmpty(hymnalDbHymn.subCategory) && !TextUtils.isEmpty(h4aHymn.subCategory)) {
            contentValues.put("SONG_META_DATA_SUBCATEGORY", TextUtils.escapeSingleQuotes(h4aHymn.subCategory));
        }

        if ((TextUtils.isEmpty(hymnalDbHymn.author) || hymnalDbHymn.author.matches("[A-Z]\\. [A-Z]\\.")) && !TextUtils.isEmpty(h4aHymn.author)) {
            if (!TextUtils.isEmpty(hymnalDbHymn.author) && TextUtils.isLonger(hymnalDbHymn.author, h4aHymn.author)) {
                LOG("Updating author for " + hymnalDbKey + ". Old author: " + hymnalDbHymn.author + " New author: " + h4aHymn.author);
                contentValues.put("SONG_META_DATA_AUTHOR", TextUtils.escapeSingleQuotes(h4aHymn.author));
            }
        }
        if ((TextUtils.isEmpty(hymnalDbHymn.composer) || hymnalDbHymn.composer.matches("[A-Z]\\. [A-Z]\\.")) && !TextUtils.isEmpty(h4aHymn.composer)) {
            if (!TextUtils.isEmpty(hymnalDbHymn.composer) && TextUtils.isLonger(hymnalDbHymn.composer, h4aHymn.composer)) {
                LOG("Updating composer for " + hymnalDbKey + ". Old composer: " + hymnalDbHymn.composer + " New composer: " + h4aHymn.composer);
                contentValues.put("SONG_META_DATA_COMPOSER", TextUtils.escapeSingleQuotes(h4aHymn.composer));
            }
        }
        if (TextUtils.isEmpty(hymnalDbHymn.key) && !TextUtils.isEmpty(h4aHymn.key)) {
            LOG("Updating key for " + hymnalDbKey + ". Old key: " + hymnalDbHymn.key + " New key: " + h4aHymn.key);
            contentValues.put("SONG_META_DATA_KEY", TextUtils.escapeSingleQuotes(h4aHymn.key));
        }
        if (TextUtils.isLonger(hymnalDbHymn.time, h4aHymn.time)) {
            LOG("Updating time for " + hymnalDbKey + ". Old time: " + hymnalDbHymn.time + " New time: " + h4aHymn.time);
            contentValues.put("SONG_META_DATA_TIME", TextUtils.escapeSingleQuotes(h4aHymn.time));
        }
        if (TextUtils.isLonger(hymnalDbHymn.meter, h4aHymn.meter)) {
            LOG("Updating meter for " + hymnalDbKey + ". Old meter: " + hymnalDbHymn.meter + " New meter: " + h4aHymn.meter);
            contentValues.put("SONG_META_DATA_METER", TextUtils.escapeSingleQuotes(h4aHymn.meter));
        }
        if (TextUtils.isLonger(hymnalDbHymn.scriptures, h4aHymn.scriptures)) {
            LOG("Updating scriptures for " + hymnalDbKey + ". Old scriptures: " + hymnalDbHymn.scriptures + " New scriptures: " + h4aHymn.scriptures);
            contentValues.put("SONG_META_DATA_SCRIPTURES", TextUtils.escapeSingleQuotes(h4aHymn.scriptures));
        }
        if (TextUtils.isEmpty(hymnalDbHymn.hymnCode) && !TextUtils.isEmpty(h4aHymn.hymnCode)) {
            LOG("Updating hymnCode for " + hymnalDbKey + ". Old hymnCode: " + hymnalDbHymn.hymnCode + " New hymnCode: " + h4aHymn.hymnCode);
            contentValues.put("SONG_META_DATA_HYMN_CODE", TextUtils.escapeSingleQuotes(h4aHymn.hymnCode));
        }
        if (TextUtils.isEmpty(hymnalDbHymn.svgJson) && !TextUtils.isEmpty(h4aHymn.svgJson)) {
            contentValues.put("SONG_META_DATA_SVG_SHEET_MUSIC", TextUtils.escapeSingleQuotes(h4aHymn.svgJson));
        } else if (TextUtils.isLonger(hymnalDbHymn.svgJson, h4aHymn.svgJson)) {
            throw new IllegalArgumentException("svgJson should not need replacing unless the hymnal db version is missing");
        }

        // Remove existing languages from h4aHymn's languages list (so we only add the new ones).
        Languages hymnalDbLanguagesData = new Gson().fromJson(hymnalDbHymn.languagesJson, Languages.class);
        if (hymnalDbLanguagesData == null) {
            hymnalDbLanguagesData = new Languages();
            hymnalDbLanguagesData.setName("Languages");
            hymnalDbLanguagesData.setData(new ArrayList<>());
        }
        for (Datum datum : hymnalDbLanguagesData.getData()) {
            HymnalDbKey datumKey = HymnalDbKey.extractFromPath(datum.getPath());
            h4aHymn.languages.remove(datumKey.toH4aKey());
        }

        // Remove existing languages from h4aHymn's languages list (so we only add the new ones).
        // for (H4aKey languages : hymnalDbHymn.languages) {
        //     h4aHymn.languages.remove(languages);
        // }

        // Add new language mappings from H4A
        for (H4aKey h4aRelevant : h4aHymn.languages) {
            HymnType type = h4aRelevant.type();
            Datum datum = new Datum();
            final String value;
            switch (type) {
                case SPANISH:
                case SPANISH_MISTYPED:
                    value = "Spanish";
                    break;
                case KOREAN:
                    value = "Korean";
                    break;
                case JAPANESE:
                    value = "Japanese";
                    break;
                case GERMAN:
                    value = "German";
                    break;
                case FRENCH:
                    value = "French";
                    break;
                case TAGALOG:
                    value = "Tagalog";
                    break;
                case CHINESE_SUPPLEMENT:
                case CHINESE:
                    if (h4aRelevant.id.equals("CS917") && (h4aKey.id.equals("CB893") || h4aKey.id.equals("E893") || h4aKey.id.equals("C641") || h4aKey.id.equals("G893"))) {
                        // C641 and CS917 are both chinese translations to E893. So we need to distinguish them somehow
                        value = "補充詩歌(繁)";
                        break;
                    }
                    if (h4aRelevant.id.equals("C641") && h4aKey.id.equals("CS917")) {
                        // C641 and CS917 are both chinese translations to E893. So this mapping is legitimate.
                        value = "詩歌(繁)";
                        break;
                    }
                    if (h4aKey.id.equals("NS154") && h4aRelevant.id.equals("C330")) {
                        // NS154 is closely related to h/8330 so this chinese song (C330) should map to both E8330
                        // (already mapped) and NS154.
                        value = "詩歌(繁)";
                        break;
                    }
                case NEW_SONG:
                    if (h4aKey.id.equals("C330") && h4aRelevant.id.equals("NS154")) {
                        // NS154 is closely related to h/8330 and thus should be also mapped to C330 as well as
                        // E8330 (already mapped)
                        value = "New Song";
                        break;
                    }
                    if (h4aKey.id.equals("CS228") && h4aRelevant.id.equals("NS195")) {
                        // NS195 is mapped to CS228 in hymnal db, but not vice versa, so it is appropriate to add it
                        // here.
                        value = "English";
                        break;
                    }
                case CLASSIC_HYMN:
                    if (h4aKey.id.equals("T1351")) {
                        // Legitimate case where we should be adding an English song mapping because T1351 is not mapped
                        // to its English song on hymnal.net for some reason.
                        value = "English";
                        break;
                    }
                    if (h4aKey.id.equals("C549")) {
                        // Legitimate case where we should be adding an English song mapping because C549 is not mapped
                        // to its English song on hymnal.net for some reason, but the English song (E755) is mapped to
                        // C549.
                        value = "English";
                        break;
                    }
                    if (h4aKey.id.equals("C526") && h4aRelevant.id.equals("E720")) {
                        // There is a three-way mapping between NT720, E720, and E8526. They are mostly just new tunes
                        // and alternate tunes of each other. Since they are just alternate tunes of each other, C526 is
                        // a valid translation for all 3 of them.
                        value = "English";
                        break;
                    }
                default:
                    throw new IllegalArgumentException(
                        "found unexpected type: " + type + "(" + h4aRelevant + ") for " + h4aKey);
            }
            datum.setPath("/en/hymn/" + type.hymnalDb + "/" + h4aRelevant.number());
            datum.setValue(value);
            hymnalDbLanguagesData.getData().add(datum);
        }

        // Populate the languages map again, this time taking into account both the hymnal db version and the H4a
        // version of the song
        Set<Datum> allLanguagesDatum = new HashSet<>();
        for (Datum language : hymnalDbLanguagesData.getData()) {
            populateHymnalDbAndH4aLanguages(language, allLanguagesDatum);
        }

        Set<HymnalDbKey> allLanguages = allLanguagesDatum.stream()
                                                         .map(datum -> HymnalDbKey.extractFromPath(datum.getPath()))
                                                         .collect(Collectors.toSet());
        auditPopulateHymnalDbAndH4aLanguages(hymnalDbKey, allLanguages);

        for (Datum language : allLanguagesDatum) {
            if (hymnalDbKey.equals(HymnalDbKey.extractFromPath(language.getPath()))) {
                continue;
            }
            if (!hymnalDbLanguagesData.getData().contains(language)) {
                hymnalDbLanguagesData.getData().add(language);
            }
        }

        final String languagesJson;
        if (hymnalDbLanguagesData.getData().isEmpty()) {
            languagesJson= null;
        } else {
            languagesJson = toJsonString(hymnalDbLanguagesData);
        }
        if (!TextUtils.equals(hymnalDbHymn.languagesJson, languagesJson)) {
            hymnalDbHymn.languagesJson = languagesJson; // need to write this so populateRelevantForSongsNotTouched will work.
            contentValues.put("SONG_META_DATA_LANGUAGES", TextUtils.escapeSingleQuotes(languagesJson));
        }

        // No need to add relevantJson because Hymnal.net has all the new tunes/languages populated and H4A
        // doesn't offer anything new in that regard.

        return contentValues;
    }

    /**
     * Go through and populate the languages map again, this time taking into account both the hymnal db version and the
     * H4a version of the song
     */
    private static void populateHymnalDbAndH4aLanguages(Datum datum, Set<Datum> allLanguages) {
        if (allLanguages.contains(datum)) {
            return;
        }

        allLanguages.add(datum);

        ConvertedHymn hymn = hymnalDbHymns.get(HymnalDbKey.extractFromPath(datum.getPath()));
        if (hymn == null) {
            return;
        }
        Languages hymnalDbLanguagesData = new Gson().fromJson(hymn.languagesJson, Languages.class);
        if (hymnalDbLanguagesData == null) {
            return;
        }

        for (Datum language : hymnalDbLanguagesData.getData()) {
            populateHymnalDbAndH4aLanguages(language, allLanguages);
        }
    }

    private static void auditPopulateHymnalDbAndH4aLanguages(HymnalDbKey hymnalDbKey, Set<HymnalDbKey> allLanguages) {
        List<HymnType> relevantTypes = new ArrayList<>();
        for (HymnalDbKey key : allLanguages) {
            relevantTypes.add(key.hymnType);
        }

        // Verify that the same hymn type doesn't appear more than the allowed number of times the languages list.
        outerLoop:
        for (HymnType hymnType : HymnType.values()) {
            int timesAllowed = 1;
            if (hymnType == HymnType.CHINESE || hymnType == HymnType.CHINESE_SUPPLEMENT) {
                timesAllowed = 2;
            }
            if (hymnType == HymnType.CLASSIC_HYMN || hymnType == HymnType.NEW_SONG) {
                for (HymnalDbKey key : allLanguages) {
                    String number = key.hymnNumber;
                    if (number.matches("(\\D+\\d+\\D*)|(\\D*\\d+\\D+)")) {
                        continue outerLoop;
                    }
                }
            }

            for (Set<HymnalDbKey> exception : hymnalDbExceptions) {
                if (allLanguages.containsAll(exception)) {
                    continue outerLoop;
                }
            }

            if (Collections.frequency(relevantTypes, hymnType) > timesAllowed) {
                if (allLanguages.contains(new HymnalDbKey(HymnType.TAGALOG, "437", null))
                    && allLanguages.contains(new HymnalDbKey(HymnType.TAGALOG, "c333", null))) {
                    continue;
                }
                throw new IllegalArgumentException(
                    String.format("%s has too many instances of %s: %s", hymnalDbKey, hymnType, allLanguages));
            }
        }

        // Verify that conflicting hymn types don't appear more together the languages list.
        if ((relevantTypes.contains(HymnType.CLASSIC_HYMN) && relevantTypes.contains(HymnType.NEW_SONG))
            || (relevantTypes.contains(HymnType.CLASSIC_HYMN) && relevantTypes.contains(HymnType.CHILDREN_SONG))
            || relevantTypes.contains(HymnType.CHILDREN_SONG) && relevantTypes.contains(HymnType.NEW_SONG)
            || relevantTypes.contains(HymnType.CHINESE) && relevantTypes.contains(HymnType.CHINESE_SUPPLEMENT)) {

            boolean isExceptionCase = false;
            for (Set<HymnalDbKey> exception : hymnalDbExceptions) {
                if (allLanguages.containsAll(exception)) {
                    isExceptionCase = true;
                }
            }

            // NS154 is closely related to h/8330 so it is okay that ch/330 maps to both of them.
            if (allLanguages.contains(new HymnalDbKey(HymnType.CLASSIC_HYMN, "8330", null))
                && allLanguages.contains(new HymnalDbKey(HymnType.NEW_SONG, "154", null))) {
                isExceptionCase = true;
            }

            if (!isExceptionCase) {
                throw new IllegalArgumentException(
                    String.format("%s has conflicting languages types: %s", hymnalDbKey, allLanguages.toString()));
            }
        }
    }

    private static ContentValues writeSong(H4aKey key) {
        ConvertedHymn hymn = h4aHymns.get(key);

        ContentValues contentValues = new ContentValues();
        contentValues.put("HYMN_TYPE", key.type().hymnalDb);
        contentValues.put("HYMN_NUMBER", key.number());
        contentValues.put("QUERY_PARAMS", "");
        contentValues.put("SONG_TITLE", TextUtils.escapeSingleQuotes(hymn.title));
        contentValues.put("SONG_LYRICS", TextUtils.escapeSingleQuotes(hymn.lyricsJson));
        contentValues.put("SONG_META_DATA_CATEGORY", TextUtils.escapeSingleQuotes(hymn.category));
        contentValues.put("SONG_META_DATA_SUBCATEGORY", TextUtils.escapeSingleQuotes(hymn.subCategory));
        contentValues.put("SONG_META_DATA_AUTHOR", TextUtils.escapeSingleQuotes(hymn.author));
        contentValues.put("SONG_META_DATA_COMPOSER", TextUtils.escapeSingleQuotes(hymn.composer));
        contentValues.put("SONG_META_DATA_KEY", TextUtils.escapeSingleQuotes(hymn.key));
        contentValues.put("SONG_META_DATA_TIME", TextUtils.escapeSingleQuotes(hymn.time));
        contentValues.put("SONG_META_DATA_METER", TextUtils.escapeSingleQuotes(hymn.meter));
        contentValues.put("SONG_META_DATA_SCRIPTURES", TextUtils.escapeSingleQuotes(hymn.scriptures));
        contentValues.put("SONG_META_DATA_HYMN_CODE", TextUtils.escapeSingleQuotes(hymn.hymnCode));
        contentValues.put("SONG_META_DATA_MUSIC", TextUtils.escapeSingleQuotes(hymn.musicJson));
        contentValues.put("SONG_META_DATA_SVG_SHEET_MUSIC", TextUtils.escapeSingleQuotes(hymn.svgJson));
        contentValues.put("SONG_META_DATA_PDF_SHEET_MUSIC", TextUtils.escapeSingleQuotes(hymn.pdfJson));

        // Add new language mappings from H4A
        Languages languages = new Languages();
        languages.setName("Languages");
        languages.setData(new ArrayList<>());
        for (H4aKey language : new ArrayList<>(hymn.languages)) {
            HymnType type = language.type();
            if (type == key.type()) {
                throw new IllegalArgumentException(key + " has a language mapping that is the same type as itself " + language);
            }
            Datum datum = new Datum();
            final String value;
            switch (type) {
                case SPANISH:
                case SPANISH_MISTYPED:
                    value = "Spanish";
                    break;
                case KOREAN:
                    value = "Korean";
                    break;
                case JAPANESE:
                    value = "Japanese";
                    break;
                case GERMAN:
                    value = "German";
                    break;
                case FRENCH:
                    value = "French";
                    break;
                case TAGALOG:
                    value = "Tagalog";
                    break;
                case CHINESE_SUPPLEMENT:
                case CHINESE:
                    value = "詩歌(繁)";
                    break;
                case NEW_SONG:
                case CLASSIC_HYMN:
                    value = "English";
                    break;
                case CEBUANO:
                    value = "Cebuano";
                    break;
                default:
                    throw new IllegalArgumentException(
                        "found unexpected type: " + type + "(" + language + ") for " + key);
            }
            datum.setPath("/en/hymn/" + type.hymnalDb + "/" + language.number());
            datum.setValue(value);
            languages.getData().add(datum);

            // Add the ?gb=1 version if it exists.
            if (type == HymnType.CHINESE || type == HymnType.CHINESE_SUPPLEMENT) {
                HymnalDbKey withQueryParams = new HymnalDbKey(language.type(), language.number(), "?gb=1");
                if (hymnalDbHymns.containsKey(withQueryParams)) {
                    Datum queryParamsDatum = new Datum();
                    queryParamsDatum.setPath("/en/hymn/" + type.hymnalDb + "/" + language.number() + "?gb=1");
                    queryParamsDatum.setValue("诗歌(简)");
                    languages.getData().add(queryParamsDatum);
                }
            }

            // Remove this song from the languages list so we can add the rest to the relevantJson
            hymn.languages.remove(language);
        }
        if (!languages.getData().isEmpty()) {
            String languagesJson = toJsonString(languages);
            contentValues.put("SONG_META_DATA_LANGUAGES", TextUtils.escapeSingleQuotes(languagesJson));
        }

        Relevant relevant = new Relevant();
        relevant.setName("Relevant");
        relevant.setData(new ArrayList<>());
        for (H4aKey relevantHymn : new ArrayList<>(hymn.languages)) {
            HymnType type = relevantHymn.type();
            Datum datum = new Datum();
            datum.setPath("/en/hymn/" + type.hymnalDb + "/" + relevantHymn.number());
            datum.setValue("New Tune");
            relevant.getData().add(datum);
        }
        if (!relevant.getData().isEmpty()) {
            String relevantJson = toJsonString(relevant);
            contentValues.put("SONG_META_DATA_RELEVANT", TextUtils.escapeSingleQuotes(relevantJson));
        }
        return contentValues;
    }

    /**
     * There are songs that exist in the hymnal db but not in the H4A database (namely, ?gb=1 songs). For those, we need
     * to go through and populate the relevant songs according to whatever was populated already
     */
    private static ContentValues populateRelevantForSongsNotTouched(HymnalDbKey hymnalDbKey) {
        ConvertedHymn hymn = hymnalDbHymns.get(hymnalDbKey);

        Languages hymnalDbLanguagesData = new Gson().fromJson(hymn.languagesJson, Languages.class);
        if (hymnalDbLanguagesData == null) {
            hymnalDbLanguagesData = new Languages();
            hymnalDbLanguagesData.setName("Languages");
            hymnalDbLanguagesData.setData(new ArrayList<>());
        }

        // Populate the languages map again, this time taking into account both the hymnal db version and the H4a
        // version of the song
        Set<Datum> allLanguagesDatum = new HashSet<>();
        for (Datum language : hymnalDbLanguagesData.getData()) {
            populateHymnalDbAndH4aLanguages(language, allLanguagesDatum);
        }

        Set<HymnalDbKey> allLanguages = allLanguagesDatum.stream()
                                                         .map(datum -> HymnalDbKey.extractFromPath(datum.getPath()))
                                                         .collect(Collectors.toSet());
        auditPopulateHymnalDbAndH4aLanguages(hymnalDbKey, allLanguages);

        boolean foundNew = false;
        for (Datum language : allLanguagesDatum) {
            if (hymnalDbKey.equals(HymnalDbKey.extractFromPath(language.getPath()))) {
                continue;
            }
            if (!hymnalDbLanguagesData.getData().contains(language)) {
                foundNew = true;
                hymnalDbLanguagesData.getData().add(language);
            }
        }

        if (!foundNew) {
            return null;
        }

        final String languagesJson;
        if (hymnalDbLanguagesData.getData().isEmpty()) {
            languagesJson = null;
        } else {
            languagesJson = toJsonString(hymnalDbLanguagesData);
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put("SONG_META_DATA_LANGUAGES", TextUtils.escapeSingleQuotes(languagesJson));
        return contentValues;
    }

    /**
     * Run through some basic tests to make sure databases have been migrated correctly.
     */
    private static void runTests(DatabaseClient hymnalClient) {
        ResultSet resultSet = hymnalClient.getDb().rawQuery(
            "SELECT * FROM song_data WHERE (hymn_type = 'h' AND hymn_number = '43') OR (hymn_type = 'S' AND hymn_number = '28') OR (hymn_type = 'ch' AND hymn_number = '37')");

        if (resultSet == null) {
            throw new IllegalArgumentException("hymn 48 was not found in the database");
        }

        try {
            resultSet.next();
            ConvertedHymn h43 = new ConvertedHymn(resultSet.getString(5),
                                                  resultSet.getString(6),
                                                  resultSet.getString(7),
                                                  resultSet.getString(8),
                                                  resultSet.getString(9),
                                                  resultSet.getString(10),
                                                  resultSet.getString(11),
                                                  resultSet.getString(12),
                                                  resultSet.getString(13),
                                                  resultSet.getString(14),
                                                  resultSet.getString(15),
                                                  resultSet.getString(16),
                                                  resultSet.getString(17),
                                                  resultSet.getString(18),
                                                  resultSet.getString(19),
                                                  resultSet.getString(20));

            if (!TextUtils.isJsonValid(h43.lyricsJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(h43.musicJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(h43.svgJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(h43.pdfJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(h43.languagesJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(h43.relevantJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            Languages h43Languages = new Gson().fromJson(h43.languagesJson, Languages.class);

            resultSet.next();
            ConvertedHymn s28 = new ConvertedHymn(resultSet.getString(5),
                                                  resultSet.getString(6),
                                                  resultSet.getString(7),
                                                  resultSet.getString(8),
                                                  resultSet.getString(9),
                                                  resultSet.getString(10),
                                                  resultSet.getString(11),
                                                  resultSet.getString(12),
                                                  resultSet.getString(13),
                                                  resultSet.getString(14),
                                                  resultSet.getString(15),
                                                  resultSet.getString(16),
                                                  resultSet.getString(17),
                                                  resultSet.getString(18),
                                                  resultSet.getString(19),
                                                  resultSet.getString(20));

            if (!TextUtils.isJsonValid(s28.lyricsJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(s28.musicJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(s28.svgJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(s28.pdfJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(s28.languagesJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(s28.relevantJson)) {
                throw new IllegalArgumentException("invalid json");
            }

            Languages s28Languages = new Gson().fromJson(s28.languagesJson, Languages.class);

            resultSet.next();
            ConvertedHymn ch37 = new ConvertedHymn(resultSet.getString(5),
                                                   resultSet.getString(6),
                                                   resultSet.getString(7),
                                                   resultSet.getString(8),
                                                   resultSet.getString(9),
                                                   resultSet.getString(10),
                                                   resultSet.getString(11),
                                                   resultSet.getString(12),
                                                   resultSet.getString(13),
                                                   resultSet.getString(14),
                                                   resultSet.getString(15),
                                                   resultSet.getString(16),
                                                   resultSet.getString(17),
                                                   resultSet.getString(18),
                                                   resultSet.getString(19),
                                                   resultSet.getString(20));

            if (!TextUtils.isJsonValid(ch37.lyricsJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(ch37.musicJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(ch37.svgJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(ch37.pdfJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(ch37.languagesJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(ch37.relevantJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            Languages ch37Languages = new Gson().fromJson(ch37.languagesJson, Languages.class);

            resultSet.next();
            ConvertedHymn ch37gb1 = new ConvertedHymn(resultSet.getString(5),
                                                   resultSet.getString(6),
                                                   resultSet.getString(7),
                                                   resultSet.getString(8),
                                                   resultSet.getString(9),
                                                   resultSet.getString(10),
                                                   resultSet.getString(11),
                                                   resultSet.getString(12),
                                                   resultSet.getString(13),
                                                   resultSet.getString(14),
                                                   resultSet.getString(15),
                                                   resultSet.getString(16),
                                                   resultSet.getString(17),
                                                   resultSet.getString(18),
                                                   resultSet.getString(19),
                                                   resultSet.getString(20));

            if (!TextUtils.isJsonValid(ch37gb1.lyricsJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(ch37gb1.musicJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(ch37gb1.svgJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(ch37gb1.pdfJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(ch37gb1.languagesJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            if (!TextUtils.isJsonValid(ch37gb1.relevantJson)) {
                throw new IllegalArgumentException("invalid json");
            }
            Languages ch37gb1Languages = new Gson().fromJson(ch37gb1.languagesJson, Languages.class);

            if (h43Languages.getData().size() != s28Languages.getData().size()) {
                throw new IllegalArgumentException("h43 and s28 has unequal languages");
            }

            if (h43Languages.getData().size() != ch37Languages.getData().size()) {
                throw new IllegalArgumentException("h43 and ch37 has unequal languages");
            }

            if (h43Languages.getData().size() != ch37gb1Languages.getData().size()) {
                throw new IllegalArgumentException("h43 and ch37?gb=1 has unequal languages");
            }


        } catch (SQLException | IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
