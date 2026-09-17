package org.wanshu.reader.core.source;

import java.util.HashMap;
import java.util.Map;

public class ChineseNumberParser {
    private static final Map<Character, Integer> CN_DIGITS = new HashMap<Character, Integer>();
    private static final Map<Character, Integer> CN_UNITS = new HashMap<Character, Integer>();

    static {
        CN_DIGITS.put('零', 0);
        CN_DIGITS.put('〇', 0);
        CN_DIGITS.put('一', 1);
        CN_DIGITS.put('二', 2);
        CN_DIGITS.put('两', 2);
        CN_DIGITS.put('三', 3);
        CN_DIGITS.put('四', 4);
        CN_DIGITS.put('五', 5);
        CN_DIGITS.put('六', 6);
        CN_DIGITS.put('七', 7);
        CN_DIGITS.put('八', 8);
        CN_DIGITS.put('九', 9);

        CN_UNITS.put('十', 10);
        CN_UNITS.put('百', 100);
        CN_UNITS.put('千', 1000);
        CN_UNITS.put('万', 10000);
    }

    public static Integer parse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        if (input.matches("^\\d+$")) {
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        int total = 0;
        int section = 0;
        int number = 0;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (CN_DIGITS.containsKey(ch)) {
                number = CN_DIGITS.get(ch);
            } else if (CN_UNITS.containsKey(ch)) {
                int unit = CN_UNITS.get(ch);
                if (unit == 10000) {
                    section = (section + number) * unit;
                    total += section;
                    section = 0;
                } else {
                    section += (number != 0 ? number : 1) * unit;
                }
                number = 0;
            } else {
                return null;
            }
        }

        return total + section + number;
    }
}
