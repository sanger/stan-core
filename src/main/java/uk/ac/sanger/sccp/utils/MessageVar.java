/*
 * Copyright (c) 2019 Genome Research Ltd. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.sanger.sccp.utils;

/**
 * Message processor.
 * Given a template like:
 * <tt>"The following item{s} {is|are} here"</tt>,
 * the {@link #process process} method can produce either
 * <tt>"The following item is here"</tt>
 * or
 * <tt>"The following items are here"</tt>
 * based on its {@code number} argument.
 * <p>
 * The code <tt>#</tt>, either as one side or as a whole substitution, means "insert the number here".
 * E.g.
 * <tt>"There {is|are} {#} item{s}"</tt>
 * could be
 * <tt>"There is 1 item"</tt>
 * or
 * <tt>"There are 5 items"</tt>
 * <p>
 * Whereas
 * <tt>"There {is|are} {an|#} item{s}"</tt>
 * could be
 * <tt>"There is an item"</tt>
 * or
 * <tt>"There are 2 items"</tt>
 * @author dr6
 */
public class MessageVar {
    private MessageVar() {}

    public static final int SINGULAR = 1, PLURAL = 2;

    /**
     * Process a message template selecting either plural or singular parts
     * based on the {@code number} argument.
     * @param template the template to use to generate the string
     * @param number indicate whether to pluralise or singularise the message
     * @return the processed string
     * @see #SINGULAR
     * @see #PLURAL
     */
    public static String process(String template, int number) {
        int i = template.indexOf('{');
        if (i < 0) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template);
        do {
            int j = sb.indexOf("|", i);
            int k = sb.indexOf("}", i);
            if (j < i || j > k) {
                j = i;
            }
            int s,e;
            if (k==i+2 && sb.charAt(i+1)=='#') {
                // Special case: {#} means insert the number, regardless of pluralisation
                s = i+1;
                e = k;
            } else if (number==SINGULAR) {
                s = i + 1;
                e = j;
            } else {
                s = j + 1;
                e = k;
            }
            if (s < e) {
                String replacement = sb.substring(s, e);
                if (replacement.equals("#")) {
                    replacement = Integer.toString(number);
                }
                sb.replace(i, k+1, replacement);
            } else {
                sb.delete(i, k+1);
            }
            i = sb.indexOf("{", i);
        } while (i >= 0);

        return sb.toString();
    }
}
