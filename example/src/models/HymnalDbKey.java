package models;

import main.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HymnalDbKey {

    private static final Pattern PATH_PATTERN = Pattern.compile("(\\w+)/(c?\\d+[a-z]*)(\\?gb=1)?");

    public final HymnType hymnType;
    public final String hymnNumber;
    public final String queryParams;

    public HymnalDbKey(HymnType hymnType, String hymnNumber, String queryParams) {
        assert hymnType != null;
        assert !TextUtils.isEmpty(hymnNumber);

        this.hymnType = hymnType;
        this.hymnNumber = hymnNumber;
        this.queryParams = queryParams != null ? queryParams : "";
    }

    public H4aKey toH4aKey() {
        if (!hymnType.hasH4aEquivalent()) {
            return null;
        }

        // NS 10XX songs are Howard Higashi songs in the H4A db
        if (hymnType == HymnType.HOWARD_HIGASHI) {
            // There are 3 special translations of Howard Higashi songs in the hymnal db that require special treatment
            if (hymnNumber.equals("12f")) {
                return new H4aKey(HymnType.NEW_SONG.h4a + "1012f");
            }
            if (hymnNumber.equals("12s")) {
                return new H4aKey(HymnType.NEW_SONG.h4a + "1012s");
            }
            if (hymnNumber.equals("41c")) {
                return new H4aKey(HymnType.NEW_SONG.h4a + "1041c");
            }
            return new H4aKey(HymnType.NEW_SONG.h4a + (Integer.parseInt(hymnNumber) + 1000));
        }
        if ("?gb=1".equals(queryParams)) {
            if (hymnType == HymnType.CHINESE) {
                return new H4aKey(HymnType.CHINESE_SIMPLIFIED.h4a + hymnNumber);
            } else if (hymnType == HymnType.CHINESE_SUPPLEMENT) {
                return new H4aKey(HymnType.CHINESE_SIMPLIFIED_SUPPLEMENT.h4a + hymnNumber);
            } else if (hymnType == HymnType.CLASSIC_HYMN || hymnType == HymnType.NEW_SONG) { // ns/102c or h/1313c
                return new H4aKey(hymnType.h4a + hymnNumber);
            } else {
                throw new IllegalArgumentException("Non-chinese song has query params of \"?gb=1\": " + this);
            }
        }
        return new H4aKey(hymnType.h4a + hymnNumber);
    }

    @Override
    public int hashCode() {
        return hymnType.hashCode() + hymnNumber.hashCode() + queryParams.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HymnalDbKey) {
            HymnalDbKey key = (HymnalDbKey) obj;
            return hymnType == key.hymnType && hymnNumber.equals(key.hymnNumber) && queryParams.equals(key.queryParams);
        }
        return false;
    }

    @Override
    public String toString() {
        return hymnType.hymnalDb + "/" + hymnNumber + "/" + queryParams;
    }

    public static HymnalDbKey extractFromPath(String path) {
        HymnType hymnType = extractTypeFromPath(path);
        String hymnNumber = extractNumberFromPath(path);
        String queryParam = extractQueryParamFromPath(path);
        return new HymnalDbKey(hymnType, hymnNumber, queryParam);
    }

    public static HymnType extractTypeFromPath(String path) {
        Matcher matcher = PATH_PATTERN.matcher(path);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unable to extract hymn type from " + path);
        }

        return HymnType.fromHymnalDb(matcher.group(1));
    }

    public static String extractNumberFromPath(String path) {
        Matcher matcher = PATH_PATTERN.matcher(path);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unable to extract hymn number from " + path);
        }

        return matcher.group(2);
    }

    public static String extractQueryParamFromPath(String path) {
        Matcher matcher = PATH_PATTERN.matcher(path);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unable to extract query param from " + path);
        }

        return matcher.group(3);
    }
}
