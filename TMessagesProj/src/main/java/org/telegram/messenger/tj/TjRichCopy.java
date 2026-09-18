package org.telegram.messenger.tj;

import android.text.TextUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Turns a message back into the little HTML dialect the app already understands when pasting
 * (see CopyUtilities.fromHTML), so that copying a message and pasting it into the message field
 * keeps the bold, the links, the quote and the rest of it instead of flattening to bare text.
 *
 * The HTML rides along with the plain text on the clipboard, so anything else the text is pasted
 * into still receives the plain text it always received.
 */
public final class TjRichCopy {

    private TjRichCopy() { }

    /** The message as HTML, or null when it carries no formatting worth keeping. */
    public static String html(MessageObject message, CharSequence plain) {
        if (message == null || message.messageOwner == null || plain == null) {
            return null;
        }
        ArrayList<TLRPC.MessageEntity> entities = message.messageOwner.entities;
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        // The offsets belong to the message text. If what is being copied is not exactly that
        // text - a caption cut short, a name glued in front - they would land in the wrong place.
        if (!TextUtils.equals(plain.toString(), message.messageOwner.message)) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        if (!append(out, plain, entities)) {
            return null;
        }
        return out.toString();
    }

    /** Appends one message; falls back to escaped plain text when there is nothing to keep. */
    public static boolean append(StringBuilder out, CharSequence text, List<TLRPC.MessageEntity> entities) {
        ArrayList<Mark> marks = new ArrayList<>();
        final int length = text.length();
        if (entities != null) {
            for (int a = 0; a < entities.size(); a++) {
                Mark mark = Mark.of(entities.get(a), length);
                if (mark != null) {
                    marks.add(mark);
                }
            }
        }
        if (marks.isEmpty()) {
            escape(out, text, 0, length);
            return false;
        }
        Collections.sort(marks);

        TreeSet<Integer> boundaries = new TreeSet<>();
        boundaries.add(0);
        boundaries.add(length);
        for (int a = 0; a < marks.size(); a++) {
            boundaries.add(marks.get(a).start);
            boundaries.add(marks.get(a).end);
        }

        ArrayList<Mark> open = new ArrayList<>();
        Integer previous = null;
        for (Integer boundary : boundaries) {
            if (previous != null) {
                emit(out, text, previous, boundary, marks, open);
            }
            previous = boundary;
        }
        for (int a = open.size() - 1; a >= 0; a--) {
            out.append(open.get(a).close);
        }
        return true;
    }

    private static void emit(StringBuilder out, CharSequence text, int from, int to, ArrayList<Mark> marks, ArrayList<Mark> open) {
        ArrayList<Mark> wanted = new ArrayList<>();
        for (int a = 0; a < marks.size(); a++) {
            Mark mark = marks.get(a);
            if (mark.start <= from && mark.end >= to) {
                wanted.add(mark);
            }
        }
        // Close whatever is open and no longer wanted, innermost first, then open what is missing.
        int common = 0;
        while (common < open.size() && common < wanted.size() && open.get(common) == wanted.get(common)) {
            common++;
        }
        for (int a = open.size() - 1; a >= common; a--) {
            out.append(open.get(a).close);
            open.remove(a);
        }
        for (int a = common; a < wanted.size(); a++) {
            out.append(wanted.get(a).open);
            open.add(wanted.get(a));
        }
        escape(out, text, from, to);
    }

    private static void escape(StringBuilder out, CharSequence text, int start, int end) {
        for (int a = start; a < end; a++) {
            char c = text.charAt(a);
            if (c == '\n') {
                out.append("<br>");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == '&') {
                out.append("&amp;");
            } else {
                out.append(c);
            }
        }
    }

    private static String attr(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** One opened-and-closed pair of tags over a stretch of the text. */
    private static final class Mark implements Comparable<Mark> {

        final int start;
        final int end;
        final int depth;
        final String open;
        final String close;

        private Mark(int start, int end, int depth, String open, String close) {
            this.start = start;
            this.end = end;
            this.depth = depth;
            this.open = open;
            this.close = close;
        }

        static Mark of(TLRPC.MessageEntity entity, int length) {
            if (entity == null || entity.length <= 0 || entity.offset < 0 || entity.offset + entity.length > length) {
                return null;
            }
            final int start = entity.offset;
            final int end = entity.offset + entity.length;
            if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
                return new Mark(start, end, 0, entity.collapsed
                        ? "<blockquote data-collapsed=\"1\">" : "<blockquote>", "</blockquote>");
            }
            if (entity instanceof TLRPC.TL_messageEntityPre) {
                return new Mark(start, end, 1, TextUtils.isEmpty(entity.language)
                        ? "<pre>" : "<pre language=\"" + attr(entity.language) + "\">", "</pre>");
            }
            if (entity instanceof TLRPC.TL_messageEntityCode) {
                return new Mark(start, end, 1, "<pre>", "</pre>");
            }
            if (entity instanceof TLRPC.TL_messageEntitySpoiler) {
                return new Mark(start, end, 2, "<spoiler>", "</spoiler>");
            }
            if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                if (TextUtils.isEmpty(entity.url)) {
                    return null;
                }
                return new Mark(start, end, 3, "<a href=\"" + attr(entity.url) + "\">", "</a>");
            }
            if (entity instanceof TLRPC.TL_messageEntityBold) {
                return new Mark(start, end, 4, "<b>", "</b>");
            }
            if (entity instanceof TLRPC.TL_messageEntityItalic) {
                return new Mark(start, end, 5, "<i>", "</i>");
            }
            if (entity instanceof TLRPC.TL_messageEntityUnderline) {
                return new Mark(start, end, 6, "<u>", "</u>");
            }
            if (entity instanceof TLRPC.TL_messageEntityStrike) {
                return new Mark(start, end, 7, "<s>", "</s>");
            }
            if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                long id = ((TLRPC.TL_messageEntityCustomEmoji) entity).document_id;
                return new Mark(start, end, 8,
                        "<animated-emoji data-document-id=\"" + id + "\">", "</animated-emoji>");
            }
            // Links, mentions, hashtags and commands are found again by whoever reads the text.
            return null;
        }

        @Override
        public int compareTo(Mark other) {
            if (depth != other.depth) {
                return depth < other.depth ? -1 : 1;
            }
            if (start != other.start) {
                return start < other.start ? -1 : 1;
            }
            return Integer.compare(other.end, end);
        }
    }
}
