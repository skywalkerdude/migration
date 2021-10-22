package models;

import main.TextUtils;

public enum HymnType {
    CLASSIC_HYMN("h", "E"),
    NEW_TUNE("nt", null),
    NEW_SONG("ns", "NS"),
    CHILDREN_SONG("c", "CH"),
    SCRIPTURE("sc", null),
    HOWARD_HIGASHI("lb", "NS"),
    DUTCH("hd", null),
    GERMAN("de", "G"),
    CHINESE("ch", "C"),
    CHINESE_SUPPLEMENT("ts", "CS"),
    CEBUANO("cb", "CB"),
    TAGALOG("ht", "T"),
    FRENCH("hf", "FR"),
    BE_FILLED(null, "BF"),
    SPANISH("S", "S"),
    // "ES" songs should actually match "S" songs
    SPANISH_MISTYPED("S", "ES"),
    KOREAN("K", "K"),
    JAPANESE("J", "J"),
    // H4a db uses "Z" to indicate simplified Chinese, while hymnaldb uses query parameters (i.e. "gb=1")
    CHINESE_SIMPLIFIED(null, "Z"),
    CHINESE_SIMPLIFIED_SUPPLEMENT(null, "ZS"),
    FARSI("F", "F"),
    INDONESIAN("I", "I"),
    // Not sure what ths "R" category is, but it doesn't exist in the database
    UNKNOWN(null, "R");

    public final String hymnalDb;

    public final String h4a;

    public static HymnType fromH4a(String h4a) {
        for (HymnType hymnType : HymnType.values()) {
            if (h4a.equals(hymnType.h4a)) {
                return hymnType;
            }
        }
        return null;
    }

    public static HymnType fromHymnalDb(String hymnaldb) {
        for (HymnType hymnType : HymnType.values()) {
            if (hymnaldb.equals(hymnType.hymnalDb)) {
                return hymnType;
            }
        }
        return null;
    }

    HymnType(String hymnalDb, String h4a) {
        this.hymnalDb = hymnalDb;
        this.h4a = h4a;
    }

    public boolean hasH4aEquivalent() {
        return !TextUtils.isEmpty(h4a);
    }

    public boolean hasHymnalDbEquivalent() {
        return !TextUtils.isEmpty(hymnalDb);
    }

    @Override
    public String toString() {
        return hymnalDb + "/" + h4a;
    }
}
