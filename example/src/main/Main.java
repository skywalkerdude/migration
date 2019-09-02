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
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CLASSIC_HYMN, "1353", null),
                                      new HymnalDbKey(HymnType.CLASSIC_HYMN, "8476", null)));

        // This mapping is weird, but it's fine.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CLASSIC_HYMN, "1360", null),
                                      new HymnalDbKey(HymnType.CLASSIC_HYMN, "267", null)));

        // This mapping, even though it has two classic hymns, is correct. They are basically the same song, only
        // that E1359 has a chorus.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CLASSIC_HYMN, "1359", null),
                                      new HymnalDbKey(HymnType.CLASSIC_HYMN, "445", null)));

        // This mapping, even though it has two classic hymns, is correct.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CLASSIC_HYMN, "79", null),
                                      new HymnalDbKey(HymnType.CLASSIC_HYMN, "8079", null)));

        // This mapping, even though it has two classic hymns, is correct.
        hymnalDbExceptions.add(Set.of(new HymnalDbKey(HymnType.CLASSIC_HYMN, "528", null),
                                      new HymnalDbKey(HymnType.CLASSIC_HYMN, "8444", null)));

        // This mapping, even though it has two classic hymns, is correct.
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
    }

    private static String toJsonString(Object src) {
        return
            new GsonBuilder()
                .disableHtmlEscaping().create().toJson(src)
                .replace("\\\"", "\"")
                .replace("\"[", "[")
                .replace("]\"", "]")
                .replace("\"{", "{")
                .replace("}\"", "}");
    }

    public static void main(String[] args) throws SQLException, BadHanyuPinyinOutputFormatCombination {
        DatabaseClient hymnalClient = new DatabaseClient(HYMNAL_DB_NAME, 10);
        DatabaseClient h4aClient = new DatabaseClient(H4A_DB_NAME, 111);

        populateHymnalDbHymns(hymnalClient.getDb().rawQuery("SELECT * FROM song_data"));
        for (HymnalDbKey hymnalDbKey : hymnalDbHymns.keySet()) {
            populateHymnalDbRelevant(hymnalDbKey);
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

        // This song has messed up mapping in the h4a db: K217 should map to E1360, not E267.
        // K217's parent hymn already points to E1360 and E1360 is already mapped to K217. So just a simple deletion is
        // needed.
        h4aHymns.get(new H4aKey("E267")).languages.remove(new H4aKey("K217"));

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

        // This song is a T song with number > 1360, but since it has a parent_hymn in the db, it fulfilled the
        // "WHERE hymn_group='T' and parent_hymn is not null" clause. Nevertheless, it is just a two-verse version of
        // E1134 and thus should be removed.
        h4aHymns.remove(new H4aKey("T10122"));
        h4aHymns.get(new H4aKey("E1134")).languages.remove(new H4aKey("T10122"));

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

        for (H4aKey h4aKey : new HashSet<>(h4aHymns.keySet())) {
            populateH4aRelevant(h4aKey);
        }

        fixGermanSongs();

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
                ContentValues contentValues = toContentValues(h4aKey);
                LOG("Inserting " + h4aKey.toHymnalDbKey());
                LOG(contentValues.toString());
                if (!DRY_RUN) {
                    hymnalClient.getDb().insert("song_data", contentValues);
                }
            }
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
-- ts/142 and ts/142?gb=1 ->  should only have each other as languages. The language mapping on hymnal.net is wrong. They don't actually match to h/1193 and its related songs
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ts/142?gb=1"}]}' WHERE HYMN_TYPE="ts" AND HYMN_NUMBER="142" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"詩歌(繁)", "path":"/en/hymn/ts/142"}]}' WHERE HYMN_TYPE="ts" AND HYMN_NUMBER="142" and QUERY_PARAMS = "?gb=1";
-- ts/253 and ts/253?gb=1 ->  should only have each other as languages. The language mapping on hymnal.net is wrong. They don't actually match to h/754 and its related songs
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ts/253?gb=1"}]}' WHERE HYMN_TYPE="ts" AND HYMN_NUMBER="253" and QUERY_PARAMS = "";
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"詩歌(繁)", "path":"/en/hymn/ts/253"}]}' WHERE HYMN_TYPE="ts" AND HYMN_NUMBER="253" and QUERY_PARAMS = "?gb=1";
-- h/8438 -> set languages json to null. The language mapping on hymnal.net is wrong. They don't actually match to h/754 and its related songs ch/439 and its related songs
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL WHERE HYMN_TYPE="h" AND HYMN_NUMBER="8438";
-- h/379 -> set languages json to null. The language mapping on hymnal.net is wrong. They don't actually match to ch/385 and its related songs ch/439 and its related songs
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL WHERE HYMN_TYPE="h" AND HYMN_NUMBER="379";
-- h/1111 -> languages should link to ch/1111 instead of ch/8111. The language mapping on hymnal.net is wrong.
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = '{"name":"Languages","data":[{"value":"诗歌(简)", "path":"/en/hymn/ch/1111?gb=1"},{"value":"詩歌(繁)","path":"/en/hymn/ch/1111"}]}' WHERE HYMN_TYPE="h" AND HYMN_NUMBER="1111";
-- Delete ch/8111 and ch/8111?gb=1 for above reason ^^
DELETE FROM SONG_DATA WHERE HYMN_TYPE="ch" AND HYMN_NUMBER="8111";
-- hf/15 -> set languages json to null and copy over Author(null), Composer(null), Key(F Major), Time(3/4), Meter(8.8.8.8), Hymn Code(51712165172321), Scriptures (Song of Songs) from h/1084
UPDATE SONG_DATA set SONG_META_DATA_LANGUAGES = NULL, SONG_META_DATA_AUTHOR = NULL, SONG_META_DATA_COMPOSER = NULL, SONG_META_DATA_KEY = "F Major", SONG_META_DATA_TIME = "3/4", SONG_META_DATA_METER = "8.8.8.8", SONG_META_DATA_HYMN_CODE = "51712165172321", SONG_META_DATA_SCRIPTURES = "Song of Songs" WHERE HYMN_TYPE="hf" AND HYMN_NUMBER="15";
*/
    private static void populateHymnalDbRelevant(HymnalDbKey hymnalDbKey) {
        Set<HymnalDbKey> allRelevant = new HashSet<>();
        populateHymnalDbRelevantHelper(hymnalDbKey, allRelevant);

        /* Audit allRelevant to see if there are conflicting types */
        List<HymnType> relevantTypes = new ArrayList<>();
        for (HymnalDbKey key : allRelevant) {
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
                for (HymnalDbKey key : allRelevant) {
                    String number = key.hymnNumber;
                    if (number.matches("(\\D+\\d+\\D*)|(\\D*\\d+\\D+)")) {
                        continue outerLoop;
                    }
                }
            }

            for (Set<HymnalDbKey> exception : hymnalDbExceptions) {
                if (allRelevant.containsAll(exception)) {
                    continue outerLoop;
                }
            }

            if (Collections.frequency(relevantTypes, hymnType) > timesAllowed) {
                throw new IllegalArgumentException(
                    String.format("%s has too many instances of %s: %s", hymnalDbKey, hymnType, allRelevant));
            }
        }

        // Verify that conflicting hymn types don't appear more together the languages list.
        if ((relevantTypes.contains(HymnType.CLASSIC_HYMN) && relevantTypes.contains(HymnType.NEW_SONG))
            || (relevantTypes.contains(HymnType.CLASSIC_HYMN) && relevantTypes.contains(HymnType.CHILDREN_SONG))
            || relevantTypes.contains(HymnType.CHILDREN_SONG) && relevantTypes.contains(HymnType.NEW_SONG)
            || relevantTypes.contains(HymnType.CHINESE) && relevantTypes.contains(HymnType.CHINESE_SUPPLEMENT)) {

            //noinspection StatementWithEmptyBody
            if (allRelevant.contains(new HymnalDbKey(HymnType.CHINESE, "641", null))
                && allRelevant.contains(new HymnalDbKey(HymnType.CHINESE_SUPPLEMENT, "917", null))) {
                // do not throw exception because both these songs actually map to the same English song.
            } else if (allRelevant.contains(new HymnalDbKey(HymnType.CLASSIC_HYMN, "1358", null))
                && allRelevant.contains(new HymnalDbKey(HymnType.CLASSIC_HYMN, "921", null))) {
                // do not throw exception because these songs are related and thus can have different Chinese songs.
            } else {
                throw new IllegalArgumentException(
                    String.format("%s has conflicting languages types: %s", hymnalDbKey, allRelevant.toString()));
            }
        }
        /* Audit complete */

        addHymnalDbRelevant(hymnalDbKey, allRelevant); // populate "languages" field
        ConvertedHymn hymn = hymnalDbHymns.get(hymnalDbKey);
        for (H4aKey relevant : hymn.languages) {
            // populate all languages songs' "languages" fields
            addHymnalDbRelevant(relevant.toHymnalDbKey(), allRelevant);
        }
    }

    private static void populateHymnalDbRelevantHelper(HymnalDbKey hymnalDbKey, Set<HymnalDbKey> allRelevant) {
        if (allRelevant.contains(hymnalDbKey)) {
            return;
        }

        if (!hymnalDbHymns.containsKey(hymnalDbKey)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", hymnalDbKey.toString()));
        }

        allRelevant.add(hymnalDbKey);

        ConvertedHymn hymnalDbHymn = hymnalDbHymns.get(hymnalDbKey);
        Languages languages = new Gson().fromJson(hymnalDbHymn.languagesJson, Languages.class);
        if (languages != null) {
            List<HymnalDbKey> related = languages.getData().stream().map(datum -> {
                String path = datum.getPath();
                return HymnalDbKey.extractFromPath(path);
            }).collect(Collectors.toList());

            for (HymnalDbKey relevantId : new ArrayList<>(related)) {
                populateHymnalDbRelevantHelper(relevantId, allRelevant);
            }
        }
    }

    private static void addHymnalDbRelevant(HymnalDbKey key, Set<HymnalDbKey> allRelevant) {
        if (!hymnalDbHymns.containsKey(key)) {
            return;
        }

        ConvertedHymn convertedHymn = hymnalDbHymns.get(key);
        for (HymnalDbKey relevant : allRelevant) {
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
                    verseContent.add(line);
                }

                verse.put(Constants.VERSE_CONTENT, toJsonString(lines));
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
            String lyricsJson = toJsonString(lyrics);
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

    private static void verifyOnlyOneInstanceOfEachType(H4aKey h4aKey, Set<H4aKey> allRelevant) {
        List<HymnType> relevantTypes = new ArrayList<>();
        for (H4aKey key : allRelevant) {
            relevantTypes.add(key.type());
        }

        for (HymnType hymnType : HymnType.values()) {
            if (Collections.frequency(relevantTypes, hymnType) > 1) {
                // It's okay if a song maps to both E445 and E1359, since they are basically the same song, only that
                // E1359 has a chorus.
                if (allRelevant.contains(new H4aKey("E445")) && allRelevant.contains(new H4aKey("E1359"))) {
                    continue;
                }
                throw new IllegalArgumentException(
                    String.format("%s contains two %s songs %s", h4aKey.id, hymnType, allRelevant.toString()));
            }
        }
    }

    private static void populateH4aRelevant(H4aKey h4aKey) {
        if (h4aKey.id.equals("K584")) {
            System.out.println("found!!");
        }
        Set<H4aKey> allRelevant = new HashSet<>();

        populateH4aRelevantHelper(h4aKey, allRelevant);

        verifyOnlyOneInstanceOfEachType(h4aKey, allRelevant);

        // Audit allRelevant to see if there are conflicting types
        List<HymnType> relevantTypes = new ArrayList<>();
        for (H4aKey key : allRelevant) {
            relevantTypes.add(key.type());
        }

        if (relevantTypes.contains(HymnType.CLASSIC_HYMN) && relevantTypes.contains(HymnType.NEW_SONG)) {
            throw new IllegalArgumentException(
                String.format("%s has both E and NS languages songs %s", h4aKey.id, allRelevant.toString()));
        }

        if (relevantTypes.contains(HymnType.CLASSIC_HYMN) && relevantTypes.contains(HymnType.CHILDREN_SONG)) {
            throw new IllegalArgumentException(
                String.format("%s has both E and CH languages songs %s", h4aKey.id, allRelevant.toString()));
        }

        if (relevantTypes.contains(HymnType.CHILDREN_SONG) && relevantTypes.contains(HymnType.NEW_SONG)) {
            throw new IllegalArgumentException(
                String.format("%s has both CH and NS languages songs %s", h4aKey.id, allRelevant.toString()));
        }

        if (relevantTypes.contains(HymnType.CHINESE) && relevantTypes.contains(HymnType.CHINESE_SUPPLEMENT)) {
            throw new IllegalArgumentException(
                String.format("%s has both C and CS languages songs %s", h4aKey.id, allRelevant.toString()));
        }

        addH4aRelevant(h4aKey, allRelevant); // populate "languages" field
        ConvertedHymn hymn = h4aHymns.get(h4aKey);
        for (H4aKey relevant : hymn.languages) {
            // populate all languages songs' "languages" fields
            addH4aRelevant(relevant, allRelevant);
        }
    }

    private static void populateH4aRelevantHelper(H4aKey h4aKey, Set<H4aKey> allRelevant) {
        if (h4aKey.type() == HymnType.BE_FILLED) {
            return;
        }

        if (!h4aHymns.containsKey(h4aKey)) {
            throw new IllegalArgumentException(h4aKey + " not found  in hymns");
        }

        ConvertedHymn h4aHymn = h4aHymns.get(h4aKey);
        List<H4aKey> relevant = h4aHymn.languages;

        // Look in hymnal db and remove all languages types that already exist in the hymnal db mapping. This is because
        // there are a lot of messed up mappings in h4a, and if they already exist in the hymnal db mapping, then we
        // can disregard it now and save some headache later.
        if (hymnalDbHymns.containsKey(h4aKey.toHymnalDbKey())) {
            ConvertedHymn hymnalDbHymn = hymnalDbHymns.get(h4aKey.toHymnalDbKey());
            for (H4aKey h4aRelevant : new ArrayList<>(relevant)) {
                for (H4aKey hymnalDbRelevant : hymnalDbHymn.languages) {
                    if (hymnalDbRelevant.isSameType(h4aRelevant)) {
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

        // High-level: remove the stuff that shouldn't be there
        outerLoop:
        for (H4aKey relevantId : new ArrayList<>(relevant)) {
            // songs that show up in "languages" column but don't actually exist in the h4a db. These should be
            // ignored since they map to nothing.
            List<String> ignore =
                Arrays.asList("CS158", "CS400", "C481c", "CS352", "CB381", "C513c", "C517c", "C510c", "C506c");
            // Arrays.asList("R509", "C825", "C914", "ES437", "C912", "C389", "C834", "T898", "C815", "C486", "ES300",
            //               "C806", "C905", "BF1040", "C856", "ES140", "C812", "C810", "C850", "C901", "C517c",
            //               "C510c", "C513c", "CB57", "ES500", "ES422", "ES421", "ES261", "ES221", "ES164",
            //               "ES163", "C925", "C917", "C840", "CS352", "CS158", "CB1360", "C506c", "CB381", "CS46",
            //               "C481c", "CS9117", "CS400", "CS46");

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
                allRelevant.add(relevantId);
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

        if (allRelevant.contains(h4aKey)) {
            return;
        }

        allRelevant.add(h4aKey);
        for (H4aKey relevantId : relevant) {
            populateH4aRelevantHelper(relevantId, allRelevant);
        }
    }

    private static void addH4aRelevant(H4aKey key, Set<H4aKey> allRelevant) {
        if (!h4aHymns.containsKey(key)) {
            return;
        }

        ConvertedHymn convertedHymn = h4aHymns.get(key);
        for (H4aKey relevant : allRelevant) {
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
                    if (h4aKey.id.equals("NS154")) {
                        // ns/154 is closely related to h/8330 so this chinese song (ch/330) should map to both
                        // h/8330 (already mapped) and ns154.
                        value = "詩歌(繁)";
                        break;
                    }
                case NEW_SONG:
                    if (h4aKey.id.equals("C330")) {
                        // ns/154 is closely related to h/8330 and thus should be also mapped to ch/330 as well as
                        // h/8330 (already mapped)
                        value = "New Song";
                        break;
                    }
                case CLASSIC_HYMN:
                    if (h4aKey.id.equals("T1351")) {
                        // Legitimate case where we should be adding an English song mapping because T1351 is not mapped
                        // To its English song on hymnal.net for some reason.
                        value = "English";
                        break;
                    }
                    if (h4aRelevant.id.equals("E1359") || h4aRelevant.id.equals("E445")) {
                        // These two songs are basically the same song, just one with a chorus and one without.
                        // Therefore, it's appropriate it'd be the English version of multiple language songs.
                        value = "English";
                        break;
                    }
                default:
                    throw new IllegalArgumentException("found unexpected type: " + type);
            }
            datum.setPath("/en/hymn/" + type.hymnalDb + "/" + h4aRelevant.number());
            datum.setValue(value);
            hymnalDbLanguagesData.getData().add(datum);
        }
        final String languagesJson;
        if (hymnalDbLanguagesData.getData().isEmpty()) {
            languagesJson= null;
        } else {
            languagesJson = toJsonString(hymnalDbLanguagesData);
        }
        if (!TextUtils.equals(hymnalDbHymn.languagesJson, languagesJson)) {
            contentValues.put("SONG_META_DATA_LANGUAGES", TextUtils.escapeSingleQuotes(languagesJson));
        }

        // No need to add relevantJson because Hymnal.net has all the new tunes/languages populated and H4A
        // doesn't offer anything new in that regard.

        return contentValues;
    }

    private static ContentValues toContentValues(H4aKey key) {
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
                default:
                    continue;
            }
            datum.setPath("/en/hymn/" + type.hymnalDb + "/" + language.number());
            datum.setValue(value);
            languages.getData().add(datum);

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
}
