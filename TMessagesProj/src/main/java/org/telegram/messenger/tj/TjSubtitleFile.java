package org.telegram.messenger.tj;

import android.text.TextUtils;

import com.google.android.exoplayer2.text.Cue;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A subtitles file the viewer picked from the device - SRT, WebVTT or ASS/SSA - read into timed
 * lines the player screen shows itself, next to whatever subtitles the video carries.
 *
 * Hebrew subtitles still go around in the old Windows code page as often as in UTF-8, so a file
 * that is not valid UTF-8 is read as Windows-1255.
 */
public final class TjSubtitleFile {

    private static final class Line {
        final long start, end;
        final String text;

        Line(long start, long end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    private static final Pattern TIMING = Pattern.compile(
            "((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3})\\s*-->\\s*((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3})");
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern ASS_TAGS = Pattern.compile("\\{[^}]*\\}");

    public final String name;
    private final ArrayList<Line> lines;
    private int lastIndex;
    private Line lastShown;

    private TjSubtitleFile(String name, ArrayList<Line> lines) {
        this.name = name;
        this.lines = lines;
    }

    /** Null when nothing in the file could be read as subtitles. */
    public static TjSubtitleFile parse(String name, byte[] data) {
        if (data == null || data.length == 0) return null;
        String text = decode(data);
        ArrayList<Line> lines = text.contains("[Events]") || text.contains("Dialogue:") ? parseAss(text) : parseSrt(text);
        if (lines.isEmpty()) return null;
        Collections.sort(lines, (a, b) -> Long.compare(a.start, b.start));
        return new TjSubtitleFile(name, lines);
    }

    /** The lines on screen at {@code position}, or null when they are the same as last time. */
    public List<Cue> cuesAt(long position, boolean force) {
        Line found = null;
        int from = lastIndex < lines.size() && lines.get(lastIndex).start <= position ? lastIndex : 0;
        for (int i = from; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.start > position) break;
            if (position < line.end) {
                found = line;
                lastIndex = i;
                break;
            }
        }
        if (!force && found == lastShown) return null;
        lastShown = found;
        if (found == null) return Collections.emptyList();
        return Collections.singletonList(new Cue.Builder().setText(found.text).build());
    }

    private static String decode(byte[] data) {
        int offset = 0;
        Charset charset = null;
        if (data.length >= 3 && (data[0] & 0xff) == 0xef && (data[1] & 0xff) == 0xbb && (data[2] & 0xff) == 0xbf) {
            offset = 3;
            charset = StandardCharsets.UTF_8;
        } else if (data.length >= 2 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xfe) {
            offset = 2;
            charset = StandardCharsets.UTF_16LE;
        } else if (data.length >= 2 && (data[0] & 0xff) == 0xfe && (data[1] & 0xff) == 0xff) {
            offset = 2;
            charset = StandardCharsets.UTF_16BE;
        }
        if (charset != null) {
            return new String(data, offset, data.length - offset, charset);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException notUtf8) {
            try {
                return new String(data, Charset.forName("windows-1255"));
            } catch (Exception e) {
                return new String(data, StandardCharsets.ISO_8859_1);
            }
        }
    }

    /** SRT and WebVTT: a timing line, then the text up to the next empty line. */
    private static ArrayList<Line> parseSrt(String text) {
        ArrayList<Line> result = new ArrayList<>();
        String[] rows = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < rows.length; i++) {
            Matcher m = TIMING.matcher(rows[i]);
            if (!m.find()) continue;
            long start = time(m.group(1));
            long end = time(m.group(2));
            StringBuilder body = new StringBuilder();
            while (i + 1 < rows.length && !rows[i + 1].trim().isEmpty()) {
                if (TIMING.matcher(rows[i + 1]).find()) break;
                i++;
                if (body.length() > 0) body.append('\n');
                body.append(rows[i].trim());
            }
            String clean = TAGS.matcher(ASS_TAGS.matcher(body).replaceAll("")).replaceAll("").trim();
            if (start >= 0 && end > start && !clean.isEmpty()) {
                result.add(new Line(start, end, unescape(clean)));
            }
        }
        return result;
    }

    /** ASS/SSA: the Dialogue lines, in the column order the Format line gives. */
    private static ArrayList<Line> parseAss(String text) {
        ArrayList<Line> result = new ArrayList<>();
        int startCol = 1, endCol = 2, textCol = 9, columns = 10;
        boolean inEvents = false;
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String row = raw.trim();
            if (row.startsWith("[")) {
                inEvents = row.equalsIgnoreCase("[Events]");
                continue;
            }
            if (inEvents && row.startsWith("Format:")) {
                String[] names = row.substring(7).split(",");
                columns = names.length;
                for (int i = 0; i < names.length; i++) {
                    String n = names[i].trim();
                    if (n.equalsIgnoreCase("Start")) startCol = i;
                    else if (n.equalsIgnoreCase("End")) endCol = i;
                    else if (n.equalsIgnoreCase("Text")) textCol = i;
                }
                continue;
            }
            if (!row.startsWith("Dialogue:")) continue;
            String[] parts = row.substring(9).split(",", columns);
            if (parts.length <= Math.max(textCol, Math.max(startCol, endCol))) continue;
            long start = time(parts[startCol].trim());
            long end = time(parts[endCol].trim());
            String body = ASS_TAGS.matcher(parts[textCol]).replaceAll("")
                    .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ").trim();
            if (start >= 0 && end > start && !body.isEmpty()) {
                result.add(new Line(start, end, body));
            }
        }
        return result;
    }

    private static String unescape(String text) {
        return text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
    }

    /** "01:02:03,456", "02:03.456" or ASS's "1:02:03.45" in milliseconds; -1 when it is none of them. */
    private static long time(String value) {
        if (TextUtils.isEmpty(value)) return -1;
        try {
            String[] clock = value.replace(',', '.').split(":");
            long ms = 0;
            for (int i = 0; i < clock.length - 1; i++) {
                ms = ms * 60 + Long.parseLong(clock[i].trim());
            }
            ms *= 60_000;
            String last = clock[clock.length - 1].trim();
            int dot = last.indexOf('.');
            if (dot < 0) {
                return ms + Long.parseLong(last) * 1000;
            }
            String fraction = (last.substring(dot + 1) + "000").substring(0, 3);
            return ms + Long.parseLong(last.substring(0, dot)) * 1000 + Long.parseLong(fraction);
        } catch (Exception e) {
            return -1;
        }
    }
}
