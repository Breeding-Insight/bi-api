package org.breedinginsight.utilities;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/*
If you have text(optional), followed by some digits(optional), followed by more text(optional), this Comparator
will sort the digits in numeric order.  The trailing text (and any subsequent digits) will be in
alpha-numeric order.

In other words; this comparator will assume that strings being compared are comprised of a text _prefix_, and an
_integer_, followed by a _suffix_.

This comparator will fist compare the prefix as an alpha-numeric, the integer as numeric and
then the the suffix as an alpha-numeric.
For Example:
1) if a string is 'big4team', then prefix='big', integer=4, suffix='team'
2) if a string is '12monkeys', then prefix='', integer=12, suffix='monkeys'
3) if a string is 'abcd', then prefix='abcd', integer=null, suffix=''
4) if a string is 'libnum14.3', then prefix='libnum', integer=14, suffix='.4'

*/
public class IntOrderComparator implements Comparator<String> {
    @Override
    public int compare(String str1, String str2) {
        //static finals to make the Matcher::group code more readable
        final int PREFIX = 1;
        final int INTEGER = 2;
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
        Integer integer1 = m1.group(INTEGER).length() > 0 ? Integer.parseInt(m1.group(INTEGER)) : null;
        Integer integer2 = m2.group(INTEGER).length() > 0 ? Integer.parseInt(m2.group(INTEGER)) : null;
        String suffix1 = m1.group(SUFFIX);
        String suffix2 = m2.group(SUFFIX);

        if (!prefix1.equals(prefix2)) {
            return prefix1.compareTo(prefix2);
        }
        /*if the prefixes are equal, sort by integers and suffixes.*/

        // an empty integer goes before an integer (EX. 'a' before 'a1')
        if (integer1 == null && integer2 != null) {
            return -1;
        }
        if (integer1 != null && integer2 == null) {
            return 1;
        }
        if (integer1 == null && integer2 == null) {
            return 0;
        }

        if (!integer1.equals(integer2)) {
            return integer1.compareTo(integer2);
        }
        /*if the prefixes and integers are equal, sort by suffixes.*/
        if (!suffix1.equals(suffix2)) {
            return suffix1.compareTo(suffix2);
        }

        /* if all else fails, compare the strings
        (I think this will always return 0)*/
        return str1.compareTo(str2);
    }
}


