package org.breedinginsight.utilities;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/*
If you have text(optional), followed by some digits, followed by more text(optional), this Comparator
will sort the digits in numeric order.  The text (and any subsequent digits) will be in alpha-numeric order.
*/
public class IntOrderComparator implements Comparator<String> {
    @Override
    public int compare(String str1, String str2) {
        //static finals to make the Matcher::group code more readable
        final int PREFIX = 1;
        final int NUMBERS = 2;
        final int SUFFIX = 3;

        // convert null strings to blank
        str1 = (str1 == null) ? "" : str1;
        str2 = (str2 == null) ? "" : str2;

        //The real work begins

        // NOTE: The last group includes all remaining text and digits
        Pattern p = Pattern.compile("^([^\\d]*)(\\d*)(.*)$");

        Matcher m1 = p.matcher(str1);
        Matcher m2 = p.matcher(str2);
        m1.find(); // needed to let m1.group() work
        m2.find(); // needed to let m2.group() work

        String prefix1 = m1.group(PREFIX);
        String prefix2 = m2.group(PREFIX);
        Integer number1 = m1.group(NUMBERS).length() > 0 ? Integer.parseInt(m1.group(NUMBERS)) : null;
        Integer number2 = m2.group(NUMBERS).length() > 0 ? Integer.parseInt(m2.group(NUMBERS)) : null;
        String suffix1 = m1.group(SUFFIX);
        String suffix2 = m2.group(SUFFIX);

        if (!prefix1.equals(prefix2)) {
            return prefix1.compareTo(prefix2);
        }
        /*if the prefixes are equal, sort by numbers and suffixes.*/

        // an empty number goes before a number (EX. 'a' before 'a1')
        if (number1 == null && number2 != null) {
            return -1;
        }
        if (number1 != null && number2 == null) {
            return 1;
        }
        if (number1 == null && number2 == null) {
            return 0;
        }

        if (!number1.equals(number2)) {
            return number1.compareTo(number2);
        }
        /*if the prefixes and numbers are equal, sort by suffixes.*/
        if (!suffix1.equals(suffix2)) {
            return suffix1.compareTo(suffix2);
        }

        /* if all else fails, compare the strings
        (I think this will always return 0)*/
        return str1.compareTo(str2);
    }
}
