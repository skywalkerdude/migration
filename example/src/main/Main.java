package main;

import com.google.gson.GsonBuilder;
import models.ConvertedHymn;
import models.H4aKey;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import repositories.DatabaseClient;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class Main {

    /**
     * Perform a dry run without actually writing anything to the database.
     */
    public static final boolean DRY_RUN = false;

    private static final boolean DEBUG_LOG = false;

    /**
     * Use a custom escape sequence, since Gson will auto-escape strings and screw everything up. Right before we save
     * the value, we will undo the custom escape character and replace it with the standard \".
     */
    private static final String CUSTOM_ESCAPE = "$CUSESP$";

    public static void LOG(String logStatement) {
        if (DEBUG_LOG) {
            System.out.println(logStatement);
        }
    }

    private static final String H4A_DB_NAME = "h4a-piano";
    private static final String HYMNAL_DB_NAME = "hymnaldb";

    private static Map<H4aKey, ConvertedHymn> h4aHymns = new HashMap<>();

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
        DatabaseClient hymnalClient = new DatabaseClient(HYMNAL_DB_NAME, 15);
        DatabaseClient h4aClient = new DatabaseClient(H4A_DB_NAME, 111);

        HymnalDbHandler handler = HymnalDbHandler.create(hymnalClient);
        handler.handle();

//        ConvertedHymn hymn = hymnalDbHymns.get(hymnalDbKey);
//        for (H4aKey relevant : hymn.languages) {
//            // populate all languages songs' "languages" fields
//            addHymnalDbLanguages(relevant.toHymnalDbKey(), allLanguages);
//        }

/*
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

//        Obsolete deletions from before the 3.6 database upgrade. Delete after some time.
//
//        h4aHymns.get(new H4aKey("C31")).languages.remove(new H4aKey("T34"));
//        h4aHymns.get(new H4aKey("C31")).languages.add(new H4aKey("T33"));
//        h4aHymns.get(new H4aKey("C74")).languages.clear();
//
//        // This song has messed up mapping in the h4a db: [E945,E956,CB956,C750]. So we need to remove the incorrect
//        mappings.
//        h4aHymns.get(new H4aKey("C755")).languages.remove(new H4aKey("E945"));
//        h4aHymns.get(new H4aKey("C755")).languages.remove(new H4aKey("C750"));

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
*/
        hymnalClient.close();
    }
}
