package main;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

import static main.H4ADbFixer.fix;
import static main.Main.LOGGER;

/**
 * Populates, audits, and makes a dense graph of all the songs in the hymnal db
 */
public class H4AHandler {

    private static final String H4A_DB_NAME = "h4a-piano";

    /**
     * Use a custom escape sequence, since Gson will auto-escape strings and screw everything up. Right before we save
     * the value, we will undo the custom escape character and replace it with the standard double-quote (").
     */
    private static final String CUSTOM_ESCAPE = "$CUSESP$";

    private final DatabaseClient h4aDbClient;
    private final Map<HymnalDbKey, ConvertedHymn> hymnalDbHymns;
    private final HymnalDbLanguagesHandler hymnalDbLanguagesHandler;
    private final Map<H4aKey, ConvertedHymn> h4aHymns;

    public static H4AHandler create(DatabaseClient h4aDbClient, Map<HymnalDbKey, ConvertedHymn> hymnalDbHymns,
                                    HymnalDbLanguagesHandler hymnalDbLanguagesHandler) throws SQLException, BadHanyuPinyinOutputFormatCombination {
        return new H4AHandler(h4aDbClient, hymnalDbHymns, hymnalDbLanguagesHandler);
    }

    private static String toJsonString(Object src) {
        return new GsonBuilder()
                .create().toJson(src)
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\"[", "[")
                .replace("]\"", "]")
                .replace("\"{", "{")
                .replace("}\"", "}");
    }

    public H4AHandler(DatabaseClient h4aDbClient, Map<HymnalDbKey, ConvertedHymn> hymnalDbHymns,
                      HymnalDbLanguagesHandler hymnalDbLanguagesHandler) {
        this.h4aDbClient = h4aDbClient;
        this.hymnalDbHymns = hymnalDbHymns;
        this.hymnalDbLanguagesHandler = hymnalDbLanguagesHandler;
        this.h4aHymns = new LinkedHashMap<>();
    }

    public void handle() throws BadHanyuPinyinOutputFormatCombination, SQLException {
        populate();
        fix(h4aHymns);
        fixGermanSongs();

        h4aHymns.forEach((h4aKey, h4aHymn) -> {
            // We're only interested in adding new songs
            if (hymnalDbHymns.containsKey(h4aKey.toHymnalDbKey())) {
                return;
            }
            switch (h4aKey.type()) {
                case CLASSIC_HYMN:
                case NEW_SONG:
                case CHINESE:
                case CHINESE_SUPPLEMENT:
                case CHINESE_SIMPLIFIED:
                case CHINESE_SIMPLIFIED_SUPPLEMENT:
                case FRENCH:
                case CEBUANO:
                    LOGGER.warning(h4aKey.type() + " songs should all be fully included in hymnal db. Warrants investigation.");
                case TAGALOG:
                case GERMAN:
                case KOREAN:
                case INDONESIAN:
                case JAPANESE:
                case SPANISH:
                case FARSI:
                    Optional<H4aKey> bestGuessParent = guessParent(h4aKey, h4aHymn);
                    bestGuessParent.ifPresent(parent -> {
                        ConvertedHymn hymnalDbParentHymn = hymnalDbHymns.get(parent.toHymnalDbKey());
                        if (hymnalDbParentHymn == null) {
                            throw new IllegalArgumentException("hymnalDbParentHymn should not be null");
                        }
                        LOGGER.fine("Adding " + h4aKey + " to " + parent.toHymnalDbKey());
                        hymnalDbParentHymn.languageReferences.add(createReference(h4aKey));
                        h4aHymn.languageReferences.add(createReference(parent));
                    });
                    // Regardless, we are going to import the hymn
                    hymnalDbHymns.put(h4aKey.toHymnalDbKey(), h4aHymn);
            }
        });

        for (HymnalDbKey hymnalDbKey : hymnalDbHymns.keySet()) {
            hymnalDbLanguagesHandler.handle(hymnalDbKey);
        }

        hymnalDbLanguagesHandler.auditGlobalLanguagesSet();
        hymnalDbLanguagesHandler.writeLanguageReferences();
    }

    private void populate() throws BadHanyuPinyinOutputFormatCombination, SQLException {
        ResultSet resultSet = h4aDbClient.getDb().rawQuery("SELECT * FROM hymns");
        if (resultSet == null) {
            throw new IllegalArgumentException("h4a query returned null");
        }

        while (resultSet.next()) {
            String id = resultSet.getString(1);
            H4aKey key = new H4aKey(id);

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

            List<Verse> lyrics = new ArrayList<>();
            ResultSet stanzas = new DatabaseClient(H4A_DB_NAME, 111).getDb().rawQuery(
                    "SELECT * FROM stanza WHERE parent_hymn='" + id + "' ORDER BY n_order");
            if (stanzas == null) {
                throw new IllegalArgumentException("h4a stanzas query returned null");
            }
            while (stanzas.next()) {
                String stanzaNumber = stanzas.getString(2);
                String text = stanzas.getString(3);

                // creates a verse object with the stanza num and content
                Verse verse = new Verse();

                if ("chorus".equals(stanzaNumber)) {
                    verse.setVerseType(Constants.CHORUS);
                } else {
                    verse.setVerseType(Constants.VERSE);
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

                verse.setVerseContent(verseContent);

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
                    verse.setTransliteration(transliteratedLines);
                }
                lyrics.add(verse);
            }

            String lyricsJson = new Gson().toJson(lyrics);
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
                        H4aKey relatedSongKey = new H4aKey(relatedSong);
                        if (relatedSongKey.type() == HymnType.SPANISH_MISTYPED) {
                            relatedSongKey = new H4aKey(HymnType.SPANISH, relatedSongKey.number());
                        }
                        related.add(relatedSongKey);
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

    /**
     * The numbering system for German songs in H4A is different from the numbering system in hymnal db. This results in
     * situations like E15, which is linked to de/15 in hymnal db, but is linked to G10 in H4a. If we were to continue
     * processing without rectifying this, there will be collisions down the road. In our example, G15 is linked to E21,
     * but we can't add G15 into the database because it's already taken by de/15.
     * <p>
     * This code basically goes through each German song and rekeys it to match its parent. So for instance, G10 will
     * be changed to G15 since its parent is E15.
     */
    private void fixGermanSongs() {
        // Need to do it this way to avoid ConcurrentModificationExceptions
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

                // number is already correct
                if (parentType == HymnType.NEW_SONG && h4aNumber.equals(parentNumber + "de")) {
                    continue;
                }

                final H4aKey newId;
                if (parentType == HymnType.CLASSIC_HYMN) {
                    newId = new H4aKey("G" + parentNumber);
                } else {
                    throw new IllegalArgumentException(h4aKey + "'s parent is not a classic hymn: " + h4aHymn.parentHymn);
                }

                if (h4aHymns.containsKey(newId)) {
                    H4aKey tempId = new H4aKey(h4aKey + "temp"); // These will get reassigned in future iterations.
                    h4aHymns.get(h4aHymn.parentHymn).languages.remove(h4aKey);
                    h4aHymns.get(h4aHymn.parentHymn).languages.add(tempId);
                    h4aHymns.remove(h4aKey);
                    h4aHymns.put(tempId, h4aHymn);
                } else {
                    h4aHymns.get(h4aHymn.parentHymn).languages.remove(h4aKey);
                    h4aHymns.get(h4aHymn.parentHymn).languages.add(newId);
                    h4aHymns.remove(h4aKey);
                    h4aHymns.put(newId, h4aHymn);
                }
                somethingChanged = true;
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

    /**
     * Try to get a song's parent explicitly if possible. If that's not possible, try to infer it.
     */
    private Optional<H4aKey> guessParent(H4aKey h4aKey, ConvertedHymn h4aHymn) {
        if (h4aHymn.parentHymn != null) {
            ConvertedHymn parentHymn = h4aHymns.get(h4aHymn.parentHymn);
            if (parentHymn.languages.contains(h4aKey)) {
                // parentKey points to the parent hymn, which in turn points back to this hymn. Since
                // references match, we have higher confidence that this is accurate.
                LOGGER.finer("PARENT FOUND: " + h4aKey + "'s parent: " + h4aHymn.parentHymn + " refers back to it");
                return Optional.of(h4aHymn.parentHymn);
            }
            // parentKey points to the parent hymn, but it doesn't point back. As such, we are less
            // confident that this is accurate, so we should perform some additional checks.
            List<Verse> h4aLyrics = h4aHymn.getLyrics();
            List<Verse> parentLyrics = parentHymn.getLyrics();
            if (h4aLyrics.size() != parentLyrics.size()) {
                // If the lyrics don't match, definitely not the right song
                LOGGER.finer("PARENT NOT FOUND: " + h4aKey + "'s parent was " + h4aHymn.parentHymn + " but it didn't link back");
                return Optional.empty();
            }
            for (int i = 0; i < h4aHymn.getLyrics().size(); i++) {
                Verse h4aVerse = h4aLyrics.get(i);
                Verse parentVerse = parentLyrics.get(i);
                if (!parentVerse.verseType().equals(h4aVerse.verseType())) {
                    LOGGER.finer("PARENT NOT FOUND: " + h4aKey + "'s parent was " + h4aHymn.parentHymn + " but it didn't link back");
                    return Optional.empty();
                }
            }
            // Same number of lyrics and the lyric type matches, so we are going to assume that they
            // are the same song
            LOGGER.finer("PARENT FOUND: " + h4aKey + "'s parent was " + h4aHymn.parentHymn + " didn't link back, but lyrics matched");
            return Optional.of(h4aHymn.parentHymn);
        }

        // parentKey is null, so we try to infer it from the type
        Set<H4aKey> inferredParentKeys = inferParent(h4aKey);
        if (inferredParentKeys.isEmpty()) {
            // Couldn't infer any parents, so just give up at this point.
            LOGGER.finer("PARENT NOT FOUND: Unable to infer parent from " + h4aKey);
            return Optional.empty();
        }
        outerLoop:
        for (H4aKey inferredParentKey : inferredParentKeys) {
            ConvertedHymn inferredParentHymn = h4aHymns.get(inferredParentKey);
            if (inferredParentHymn == null) {
                // If the inferredParentHymn doesn't exist, it most definitely is not the correct parent.
                LOGGER.finer("PARENT NOT FOUND: " + h4aKey + "'s parent was inferred to be " + inferredParentKey + ", but wasn't found in H4a");
                continue;
            }
            if (h4aHymn.languages.contains(inferredParentKey) && inferredParentHymn.languages.contains(h4aKey)) {
                LOGGER.finer("PARENT FOUND: " + h4aKey + "'s inferred parent " + inferredParentKey + " points back to it. Huzzah!");
                return Optional.of(inferredParentKey);
            }
            // Inferred parent doesn't point back to this song. As such, we are less confident that
            // this is accurate, so we should perform some additional checks.
            List<Verse> h4aLyrics = h4aHymn.getLyrics();
            List<Verse> parentLyrics = inferredParentHymn.getLyrics();
            if (h4aLyrics.size() != parentLyrics.size()) {
                // If the lyrics don't match, definitely not the right song
                LOGGER.finer("PARENT NOT FOUND: " + h4aKey + "'s parent was inferred to be " + inferredParentKey + " but it wasn't right");
                continue;
            }
            for (int i = 0; i < h4aHymn.getLyrics().size(); i++) {
                Verse h4aVerse = h4aLyrics.get(i);
                Verse parentVerse = parentLyrics.get(i);
                if (!parentVerse.verseType().equals(h4aVerse.verseType())) {
                    LOGGER.finer("PARENT NOT FOUND: " + h4aKey + "'s parent was inferred to be " + inferredParentKey + " but it wasn't right");
                    continue outerLoop;
                }
            }
            // Same number of lyrics and the lyric type matches, so we are going to assume that they
            // are the same song
            LOGGER.finer("PARENT FOUND: " + h4aKey + "'s parent was inferred to be " + inferredParentKey);
            return Optional.of(inferredParentKey);
        }
        LOGGER.finer("PARENT NOT FOUND: " + h4aKey + "'s parent was unable to be inferred");
        return Optional.empty();
    }

    /**
     * @return a list of {@link H4aKey}s that could be the parent of the passed-in key.
     */
    private Set<H4aKey> inferParent(H4aKey key) {
        Set<H4aKey> inferredParents = new LinkedHashSet<>();
        switch (key.type()) {
            case INDONESIAN:
            case JAPANESE:
            case KOREAN:
                inferredParents.add(new H4aKey(HymnType.CHINESE.h4a + key.number()));
                break;
            case FRENCH:
                inferredParents.add(new H4aKey(HymnType.CLASSIC_HYMN + key.number()));
                break;
            default:
        }
        return inferredParents;
    }

    /**
     * Converts an {@link H4aKey} to a {@link Reference} by inferring the text from the hymn type.
     */
    private Reference createReference(H4aKey h4aKey) {
        final String text;
        switch (h4aKey.type()) {
            case TAGALOG:
                text = "Tagalog";
                break;
            case CEBUANO:
                text = "Cebuano";
                break;
            case GERMAN:
                text = "German";
                break;
            case CHINESE:
                // fall through
            case CHINESE_SUPPLEMENT:
                text = "詩歌(繁)";
                break;
            case CHINESE_SIMPLIFIED:
                // fall through
            case CHINESE_SIMPLIFIED_SUPPLEMENT:
                text = "诗歌(简)";
                break;
            case KOREAN:
                text = "Korean";
                break;
            case INDONESIAN:
                text = "Indonesian";
                break;
            case JAPANESE:
                text = "Japanese";
                break;
            case SPANISH:
                text = "Spanish";
                break;
            case FRENCH:
                text = "French";
                break;
            case FARSI:
                text = "Farsi";
                break;
            case CLASSIC_HYMN:
                // fall through
            case NEW_SONG:
                // fall through
            case CHILDREN_SONG:
                text = "English";
                break;
            default:
                throw new IllegalArgumentException("Unexpected type encountered: " + h4aKey);
        }
        return Reference.create(text, h4aKey.toHymnalDbKey());
    }
}
