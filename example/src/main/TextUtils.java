package main;

public class TextUtils {

    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Returns true if a and b are equal, including if they are both null.
     * <p>Note: In platform versions 1.1 and earlier, this method only worked well if
     * both the arguments were instances of String.</i>

     * @param a first CharSequence to check
     * @param b second CharSequence to check
     * @return true if a and b are equal
     */
    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        int length;
        if (a != null && b != null && (length = a.length()) == b.length()) {
            if (a instanceof String && b instanceof String) {
                return a.equals(b);
            } else {
                for (int i = 0; i < length; i++) {
                    if (a.charAt(i) != b.charAt(i)) return false;
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isNumeric(String str) {
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    public static String escapeSingleQuotes(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (c == '\'') {
                sb.append("'");
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * @return true if str2 is longer than str1.
     */
    public static boolean isLonger(String str1, String str2) {
        if (TextUtils.isEmpty(str1) && TextUtils.isEmpty(str2)) {
            return false;
        }

        if (!TextUtils.isEmpty(str1) && TextUtils.isEmpty(str2)) {
            return false;
        }

        if (TextUtils.isEmpty(str1) && !TextUtils.isEmpty(str2)) {
            return true;
        }

        return str2.trim().length() > str1.trim().length();
    }

    public static String getLongerString(String str1, String str2) {
        if (TextUtils.isEmpty(str1) && TextUtils.isEmpty(str2)) {
            return null;
        }

        if (!TextUtils.isEmpty(str1) && TextUtils.isEmpty(str2)) {
            return str1.trim();
        }

        if (TextUtils.isEmpty(str1) && !TextUtils.isEmpty(str2)) {
            return str2.trim();
        }

        return str1.trim().length() >= str2.trim().length() ? str1.trim() : str2.trim();
    }
}
