package main;

import com.google.gson.Gson;
import models.ConvertedHymn;
import models.Languages;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import repositories.DatabaseClient;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Main {

    /**
     * Perform a dry run without actually writing anything to the database.
     */
    public static final boolean DRY_RUN = false;

    public static final Logger LOGGER = Logger.getAnonymousLogger();

    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.INFO);
    }

    private static final String H4A_DB_NAME = "h4a-piano";
    private static final String HYMNAL_DB_NAME = "hymnaldb";

    public static void main(String[] args) throws SQLException, BadHanyuPinyinOutputFormatCombination, IOException {
        DatabaseClient hymnalDbClient = new DatabaseClient(HYMNAL_DB_NAME, 15);
        hymnalDbClient.getDb().execSql("PRAGMA user_version = 16");
        HymnalDbHandler hymnalDbHandler = HymnalDbHandler.create(hymnalDbClient);
        hymnalDbHandler.handle();

        DatabaseClient h4aClient = new DatabaseClient(H4A_DB_NAME, 111);
        H4AHandler h4AHandler = H4AHandler.create(h4aClient, hymnalDbHandler.allHymns, hymnalDbHandler.languagesHandler);
        h4AHandler.handle();

        runTests(hymnalDbClient);

        h4aClient.close();
        hymnalDbClient.close();
    }

    /**
     * Run through some basic tests to make sure databases have been migrated correctly.
     */
    private static void runTests(DatabaseClient hymnalDbClient) throws SQLException, IOException {
        ResultSet resultSet = hymnalDbClient.getDb().rawQuery(
                "SELECT * FROM song_data WHERE (hymn_type = 'h' AND hymn_number = '43') OR (hymn_type = 'S' AND hymn_number = '28') OR (hymn_type = 'ch' AND hymn_number = '37')");

        if (resultSet == null) {
            throw new IllegalArgumentException("hymn 48 was not found in the database");
        }

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
    }
}
