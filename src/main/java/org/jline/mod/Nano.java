/*
 * Copyright (c) 2002-2017, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package org.jline.mod;

import org.jline.builtins.Options;
import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.terminal.Attributes.ControlChar;
import org.jline.terminal.Attributes.InputFlag;
import org.jline.terminal.Attributes.LocalFlag;
import org.jline.terminal.MouseEvent;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.Terminal.Signal;
import org.jline.terminal.Terminal.SignalHandler;
import org.jline.utils.*;
import org.jline.utils.InfoCmp.Capability;

import java.io.InputStreamReader;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jline.keymap.KeyMap.*;

public class Nano {

    // Final fields
    private final Terminal terminal;
    protected final Display display;
    private final BindingReader bindingReader;
    protected final Size size;
    private final Path root;
    private final boolean restricted;
    private final int vsusp;

    // Keys
    private KeyMap<Operation> keys;

    // Configuration
    public String title = "JLine Nano 3.0.0";
    private boolean printLineNumbers = true;
    private boolean wrapping = true;
    private boolean smoothScrolling = true;
    private boolean mouseSupport = false;
    private boolean oneMoreLine = true;
    private boolean constantCursor;
    private int tabs = 4;
    private String matchBrackets = "(<[{)>]}";

    // Input
    private final List<Buffer> buffers = new ArrayList<>();
    private int bufferIndex;
    private Buffer buffer;

    private String message;
    private int nbBindings = 0;

    private LinkedHashMap<String, String> shortcuts;

    private String editMessage;
    private final StringBuilder editBuffer = new StringBuilder();

    private boolean searchCaseSensitive;
    private boolean searchRegexp;
    private boolean searchBackwards;
    private String searchTerm;
    private List<String> searchTerms = new ArrayList<>();
    private int searchTermId = -1;
    private WriteMode writeMode = WriteMode.WRITE;
    private boolean writeBackup;

    private boolean readNewBuffer = true;

    protected enum WriteMode {
        WRITE,
        APPEND,
        PREPEND
    }

    protected enum WriteFormat {
        UNIX,
        DOS,
        MAC
    }

    @SuppressWarnings("Duplicates")
    protected class Buffer {
        String file;
        Charset charset;
        WriteFormat format = WriteFormat.UNIX;
        List<String> lines;

        int firstLineToDisplay;
        int firstColumnToDisplay;
        int offsetInLineToDisplay;

        int line;
        List<LinkedList<Integer>> offsets = new ArrayList<>();
        int offsetInLine;
        int column;
        int wantedColumn;

        boolean dirty;

        Buffer(String file) {
            this.file = file;
        }

        public void clear() {
            lines.clear();
            lines.add("");
            computeAllOffsets();
           firstLine();
        }

        void open() throws IOException {
            if (lines != null) {
                return;
            }

            lines = new ArrayList<>();
            lines.add("");
            charset = Charset.defaultCharset();
            computeAllOffsets();

            if (file == null) {
                return;
            }

            Path path = root.resolve(file);
            if (Files.isDirectory(path)) {
                setMessage("\"" + file + "\" is a directory");
                return;
            }

            try (InputStream fis = Files.newInputStream(path))
            {
                read(fis);
            } catch (IOException e) {
                setMessage("Error reading " + file + ": " + e.getMessage());
            }
        }

        void open(InputStream is) throws IOException {
            if (lines != null) {
                return;
            }

            lines = new ArrayList<>();
            lines.add("");
            charset = Charset.defaultCharset();
            computeAllOffsets();

            read(is);
        }

        void read(InputStream fis) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int remaining;
            while ((remaining = fis.read(buffer)) > 0) {
                bos.write(buffer, 0, remaining);
            }
            byte[] bytes = bos.toByteArray();

            charset = Charset.forName("UTF-8");

            // TODO: detect format, do not eat last newline
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(bytes), charset))) {
                String line;
                lines.clear();
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
            computeAllOffsets();
            moveToChar(0);
        }

        void insert(String insert) {
            String text = lines.get(line);
            int pos = offsetInLine + column;
            insert = insert.replaceAll("\r\n", "\n");
            insert = insert.replaceAll("\r", "\n");
            String mod;
            if (pos == text.length()) {
                mod = text + insert;
            } else {
                mod = text.substring(0, pos) + insert + text.substring(pos);
            }
            List<String> ins = new ArrayList<>();
            int last = 0;
            int idx = mod.indexOf('\n', last);
            while (idx >= 0) {
                ins.add(mod.substring(last, idx));
                last = idx + 1;
                idx = mod.indexOf('\n', last);
            }
            ins.add(mod.substring(last));
            lines.set(line, ins.get(0));
            offsets.set(line, computeOffsets(ins.get(0)));
            for (int i = 1; i < ins.size(); i++) {
                ++line;
                lines.add(line, ins.get(i));
                offsets.add(line, computeOffsets(ins.get(i)));
            }
            moveToChar(ins.get(ins.size() - 1).length() - (text.length() - pos));
            dirty = true;
        }

        void computeAllOffsets() {
            offsets.clear();
            for (String text : lines) {
                offsets.add(computeOffsets(text));
            }
        }

        LinkedList<Integer> computeOffsets(String text) {
            int width = size.getColumns() - (printLineNumbers ? 8 : 0);
            LinkedList<Integer> offsets = new LinkedList<>();
            offsets.add(0);
            int last = 0;
            int prevword = 0;
            boolean inspace = false;
            for (int i = 0; i < text.length(); i++) {
                if (isBreakable(text.charAt(i))) {
                    inspace = true;
                } else if (inspace) {
                    prevword = i;
                    inspace = false;
                }
                if (i == last + width - 1) {
                    if (prevword == last) {
                        prevword = i;
                    }
                    offsets.add(prevword);
                    last = prevword;
                }
            }
            return offsets;
        }

        boolean isBreakable(char ch) {
            return ch == ' ';
        }

        void moveToChar(int pos) {
            offsetInLine = prevLineOffset(line, pos + 1).get();
            column = pos - offsetInLine;
        }

        void delete(int count) {
            while (--count >= 0 && moveRight(1) && backspace(1));
        }

        boolean backspace(int count) {
            while (count > 0) {
                String text = lines.get(line);
                int pos = offsetInLine + column;
                if (pos == 0) {
                    if (line == 0) {
                        bof();
                        return false;
                    }
                    String prev = lines.get(--line);
                    lines.set(line, prev + text);
                    offsets.set(line, computeOffsets(prev + text));
                    moveToChar(length(prev, tabs));
                    lines.remove(line + 1);
                    offsets.remove(line + 1);
                    count--;
                    dirty = true;
                } else {
                    int nb = Math.min(pos, count);
                    text = text.substring(0, pos - nb) + text.substring(pos);
                    lines.set(line, text);
                    offsets.set(line, computeOffsets(text));
                    moveToChar(offsetInLine + column - nb);
                    count -= nb;
                    dirty = true;
                }
            }
            return true;
        }

        boolean moveLeft(int chars) {
            boolean ret = true;
            while (--chars >= 0) {
                if (offsetInLine + column > 0) {
                    moveToChar(offsetInLine + column - 1);
                } else if (line > 0) {
                    line--;
                    moveToChar(length(getLine(line), tabs));
                } else {
                    bof();
                    ret = false;
                    break;
                }
            }
            wantedColumn = column;
            ensureCursorVisible();
            return ret;
        }

        boolean moveRight(int chars) {
            boolean ret = true;
            while (--chars >= 0) {
                int len = length(getLine(line), tabs);
                if (offsetInLine + column + 1 <= len) {
                    moveToChar(offsetInLine + column + 1);
                } else if (getLine(line + 1) != null) {
                    line++;
                    offsetInLine = 0;
                    column = 0;
                } else {
                    eof();
                    ret = false;
                    break;
                }
            }
            wantedColumn = column;
            ensureCursorVisible();
            return ret;
        }

        void moveDown(int lines) {
            cursorDown(lines);
            ensureCursorVisible();
        }

        void moveUp(int lines) {
            cursorUp(lines);
            ensureCursorVisible();
        }

        private Optional<Integer> prevLineOffset(int line, int offsetInLine) {
            if (line >= offsets.size()) {
                return Optional.empty();
            }
            Iterator<Integer> it = offsets.get(line).descendingIterator();
            while (it.hasNext()) {
                int off = it.next();
                if (off < offsetInLine) {
                    return Optional.of(off);
                }
            }
            return Optional.empty();
        }

        private Optional<Integer> nextLineOffset(int line, int offsetInLine) {
            if (line >= offsets.size()) {
                return Optional.empty();
            }
            return offsets.get(line).stream()
                    .filter(o -> o > offsetInLine)
                    .findFirst();
        }

        void moveDisplayDown(int lines) {
            int height = size.getRows() - computeHeader().size() - computeFooter().size();
            // Adjust cursor
            while (--lines >= 0) {
                int lastLineToDisplay = firstLineToDisplay;
                if (firstColumnToDisplay > 0 || !wrapping) {
                    lastLineToDisplay += height - 1;
                } else {
                    int off = offsetInLineToDisplay;
                    for (int l = 0; l < height - 1; l++) {
                        Optional<Integer> next = nextLineOffset(lastLineToDisplay, off);
                        if (next.isPresent()) {
                            off = next.get();
                        } else {
                            off = 0;
                            lastLineToDisplay++;
                        }
                    }
                }
                if (getLine(lastLineToDisplay) == null) {
                    eof();
                    return;
                }
                Optional<Integer> next = nextLineOffset(firstLineToDisplay, offsetInLineToDisplay);
                if (next.isPresent()) {
                    offsetInLineToDisplay = next.get();
                } else {
                    offsetInLineToDisplay = 0;
                    firstLineToDisplay++;
                }
            }
        }

        void moveDisplayUp(int lines) {
            int width = size.getColumns() - (printLineNumbers ? 8 : 0);
            while (--lines >= 0) {
                if (offsetInLineToDisplay > 0) {
                    offsetInLineToDisplay = Math.max(0, offsetInLineToDisplay - (width - 1));
                } else if (firstLineToDisplay > 0) {
                    firstLineToDisplay--;
                    offsetInLineToDisplay = prevLineOffset(firstLineToDisplay, Integer.MAX_VALUE).get();
                } else {
                    bof();
                    return;
                }
            }
        }

        private void cursorDown(int lines) {
            // Adjust cursor
            while (--lines >= 0) {
                if (firstColumnToDisplay > 0 || !wrapping) {
                    if (getLine(line + 1) == null) {
                        buffer.lines.add("");
                        computeAllOffsets();
                    }
                    line++;
                    offsetInLine = 0;
                    column = Math.min(getLine(line).length(), wantedColumn);
                } else {
                    String txt = getLine(line);
                    Optional<Integer> off = nextLineOffset(line, offsetInLine);
                    if (off.isPresent()) {
                        offsetInLine = off.get();
                    } else {
                        if (getLine(line + 1) == null) {
                            buffer.lines.add("");
                            computeAllOffsets();
                        }
                        line++;
                        offsetInLine = 0;
                        txt = getLine(line);
                    }
                    String curLine = txt;
                    int next = nextLineOffset(line, offsetInLine).orElseGet(curLine::length);
                    column = Math.min(wantedColumn, next - offsetInLine);
                }
            }
        }

        private void cursorUp(int lines) {
            while (--lines >= 0) {
                if (firstColumnToDisplay > 0 || !wrapping) {
                    if (line > 0) {
                        line--;
                        column = Math.min(length(getLine(line), tabs) - offsetInLine, wantedColumn);
                    } else {
                        bof();
                        break;
                    }
                } else {
                    Optional<Integer> prev = prevLineOffset(line, offsetInLine);
                    if (prev.isPresent()) {
                        offsetInLine = prev.get();
                    } else if (line > 0) {
                        line--;
                        offsetInLine = prevLineOffset(line, Integer.MAX_VALUE).get();
                        int next = nextLineOffset(line, offsetInLine).orElse(getLine(line).length());
                        column = Math.min(wantedColumn, next - offsetInLine);
                    } else {
                        bof();
                        break;
                    }
                }
            }
        }

        void ensureCursorVisible() {
            List<AttributedString> header = computeHeader();
            int rwidth = size.getColumns();
            int height = size.getRows() - header.size() - computeFooter().size();

            while (line < firstLineToDisplay
                    || line == firstLineToDisplay && offsetInLine < offsetInLineToDisplay) {
                moveDisplayUp(smoothScrolling ? 1 : height / 2);
            }

            while (true) {
                int cursor = header.size() * size.getColumns() + (printLineNumbers ? 8 : 0);
                int cur = firstLineToDisplay;
                int off = offsetInLineToDisplay;
                while (true) {
                    if (cur < line || off < offsetInLine) {
                        if (firstColumnToDisplay > 0 || !wrapping) {
                            cursor += rwidth;
                            cur++;
                        } else {
                            cursor += rwidth;
                            Optional<Integer> next = nextLineOffset(cur, off);
                            if (next.isPresent()) {
                                off = next.get();
                            } else {
                                cur++;
                                off = 0;
                            }
                        }
                    } else if (cur == line) {
                        cursor += column;
                        break;
                    } else {
                        throw new IllegalStateException();
                    }
                }
                if (cursor >= (height + header.size()) * rwidth) {
                    moveDisplayDown(smoothScrolling ? 1 : height / 2);
                } else {
                    break;
                }
            }
        }

        void eof() {
        }

        void bof() {
        }

        void resetDisplay() {
            int width = size.getColumns() - (printLineNumbers ? 8 : 0);
            column = offsetInLine + column;
            offsetInLine = (column / width) * (width - 1);
            column = column - offsetInLine;
        }

        String getLine(int line) {
            return line < lines.size() ? lines.get(line) : null;
        }

        String getTitle() {
            return file != null ? "File: " + file : "New Buffer";
        }

        List<AttributedString> computeHeader() {
            String left = Nano.this.getTitle();
            String middle = null;
            String right = dirty ? "Modified" : "        ";

            int width = size.getColumns();
            int mstart = 2 + left.length() + 1;
            int mend = width - 2 - 8;

            if (file == null) {
                middle = "New Buffer";
            } else {
                int max = mend - mstart;
                String src = file;
                if ("File: ".length() + src.length() > max) {
                    int lastSep = src.lastIndexOf('/');
                    if (lastSep > 0) {
                        String p1 = src.substring(lastSep);
                        String p0 = src.substring(0, lastSep);
                        while (p0.startsWith(".")) {
                            p0 = p0.substring(1);
                        }
                        int nb = max - p1.length() - "File: ...".length();
                        int cut;
                        cut = Math.max(0, Math.min(p0.length(), p0.length() - nb));
                        middle = "File: ..." + p0.substring(cut) + p1;
                    }
                    if (middle == null || middle.length() > max) {
                        left = null;
                        max = mend - 2;
                        int nb = max - "File: ...".length();
                        int cut = Math.max(0, Math.min(src.length(), src.length() - nb));
                        middle = "File: ..." + src.substring(cut);
                        if (middle.length() > max) {
                            middle = middle.substring(0, max);
                        }
                    }
                } else {
                    middle = "File: " + src;
                }
            }

            int pos = 0;
            AttributedStringBuilder sb = new AttributedStringBuilder();
            sb.style(AttributedStyle.INVERSE);
            sb.append("  ");
            pos += 2;

            if (left != null) {
                sb.append(left);
                pos += left.length();
                sb.append(" ");
                pos += 1;
                for (int i = 1; i < (size.getColumns() - middle.length()) / 2 - left.length() - 1 - 2; i++) {
                    sb.append(" ");
                    pos++;
                }
            }
            sb.append(middle);
            pos += middle.length();
            while (pos < width - 8 - 2) {
                sb.append(" ");
                pos++;
            }
            sb.append(right);
            sb.append("  \n");
            if (oneMoreLine) {
                return Collections.singletonList(sb.toAttributedString());
            } else {
                return Arrays.asList(sb.toAttributedString(), new AttributedString("\n"));
            }
        }

        List<AttributedString> getDisplayedLines(int nbLines) {
            AttributedStyle s = AttributedStyle.DEFAULT.foreground(AttributedStyle.BLACK + AttributedStyle.BRIGHT);
            AttributedString cut = new AttributedString("…", s);
            AttributedString ret = new AttributedString("↩", s);

            List<AttributedString> newLines = new ArrayList<>();
            int rwidth = size.getColumns();
            int width = rwidth - (printLineNumbers ? 8 : 0);
            int curLine = firstLineToDisplay;
            int curOffset = offsetInLineToDisplay;
            int prevLine = -1;
            for (int terminalLine = 0; terminalLine < nbLines; terminalLine++) {
                AttributedStringBuilder line = new AttributedStringBuilder().tabs(tabs);
                if (printLineNumbers && curLine < lines.size()) {
                    line.style(s);
                    if (curLine != prevLine) {
                        line.append(String.format("%7d ", curLine + 1));
                    } else {
                        line.append("      ‧ ");
                    }
                    line.style(AttributedStyle.DEFAULT);
                    prevLine = curLine;
                }
                if (curLine >= lines.size()) {
                    // Nothing to do
                } else if (firstColumnToDisplay > 0 || !wrapping) {
                    AttributedString disp = new AttributedString(getLine(curLine));
                    disp = disp.columnSubSequence(firstColumnToDisplay, Integer.MAX_VALUE);
                    if (disp.columnLength() >= width) {
                        line.append(disp.columnSubSequence(0, width - cut.columnLength()));
                        line.append(cut);
                    } else {
                        line.append(disp);
                    }
                    curLine++;
                } else {
                    Optional<Integer> nextOffset = nextLineOffset(curLine, curOffset);
                    if (nextOffset.isPresent()) {
                        AttributedString disp = new AttributedString(getLine(curLine));
                        line.append(disp.columnSubSequence(curOffset, nextOffset.get()));
                        line.append(ret);
                        curOffset = nextOffset.get();
                    } else {
                        AttributedString disp = new AttributedString(getLine(curLine));
                        line.append(disp.columnSubSequence(curOffset, Integer.MAX_VALUE));
                        curLine++;
                        curOffset = 0;
                    }
                }
                line.append('\n');
                newLines.add(line.toAttributedString());
            }
            return newLines;
        }

        void moveTo(int x, int y) {
            if (printLineNumbers) {
                x = Math.max(x - 8, 0);
            }
            line = firstLineToDisplay;
            offsetInLine = offsetInLineToDisplay;
            wantedColumn = x;
            cursorDown(y);
        }

        int getDisplayedCursor() {
            int rwidth = size.getColumns() + 1;
            int cursor = (printLineNumbers ? 8 : 0);
            int cur = firstLineToDisplay;
            int off = offsetInLineToDisplay;
            while (true) {
                if (cur < line || off < offsetInLine) {
                    if (firstColumnToDisplay > 0 || !wrapping) {
                        cursor += rwidth;
                        cur++;
                    } else {
                        cursor += rwidth;
                        Optional<Integer> next = nextLineOffset(cur, off);
                        if (next.isPresent()) {
                            off = next.get();
                        } else {
                            cur++;
                            off = 0;
                        }
                    }
                } else if (cur == line) {
                    cursor += column;
                    break;
                } else {
                    throw new IllegalStateException();
                }
            }
            return cursor;
        }

        char getCurrentChar() {
            String str = lines.get(line);
            if (column + offsetInLine < str.length()) {
                return str.charAt(column + offsetInLine);
            } else if (line < lines.size() - 1) {
                return '\n';
            } else {
                return 0;
            }
        }

        @SuppressWarnings("StatementWithEmptyBody")
        void prevWord() {
            while (Character.isAlphabetic(getCurrentChar())
                    && moveLeft(1));
            while (!Character.isAlphabetic(getCurrentChar())
                    && moveLeft(1));
            while (Character.isAlphabetic(getCurrentChar())
                    && moveLeft(1));
            moveRight(1);
        }

        @SuppressWarnings("StatementWithEmptyBody")
        void nextWord() {
            while (Character.isAlphabetic(getCurrentChar())
                    && moveRight(1));
            while (!Character.isAlphabetic(getCurrentChar())
                    && moveRight(1));
        }

        void beginningOfLine() {
            column = offsetInLine = 0;
            wantedColumn = 0;
        }

        void endOfLine() {
            column = length(lines.get(line), tabs);
            int width = size.getColumns() - (printLineNumbers ? 8 : 0);
            offsetInLine = (column / width) * (width - 1);
            column = column - offsetInLine;
            wantedColumn = column;
        }

        void prevPage() {
            int height = size.getRows() - computeHeader().size() - computeFooter().size();
            scrollUp(height - 2);
        }

        void nextPage() {
            int height = size.getRows() - computeHeader().size() - computeFooter().size();
            scrollDown(height - 2);
        }

        void scrollUp(int lines) {
            cursorUp(lines);
            moveDisplayUp(lines);
        }

        void scrollDown(int lines) {
            cursorDown(lines);
            moveDisplayDown(lines);
        }

        void firstLine() {
            line = 0;
            offsetInLine = column = 0;
            ensureCursorVisible();
        }

        void lastLine() {
            line = lines.size() - 1;
            offsetInLine = column = 0;
            ensureCursorVisible();
        }

        void nextSearch() {
            if (searchTerm == null) {
                setMessage("No current search pattern");
                return;
            }
            setMessage(null);
            int cur = line;
            int dir = searchBackwards ? -1 : +1;
            int newPos = -1;
            int newLine = -1;
            // Search on current line
            List<Integer> curRes = doSearch(lines.get(line));
            if (searchBackwards) {
                Collections.reverse(curRes);
            }
            for (int r : curRes) {
                if (searchBackwards ? r < offsetInLine + column : r > offsetInLine + column) {
                    newPos = r;
                    newLine = line;
                    break;
                }
            }
            // Check other lines
            if (newPos < 0) {
                while (true) {
                    cur = (cur + dir + lines.size()) % lines.size();
                    if (cur == line) {
                        break;
                    }
                    List<Integer> res = doSearch(lines.get(cur));
                    if (!res.isEmpty()) {
                        newPos = searchBackwards ? res.get(res.size() - 1) : res.get(0);
                        newLine = cur;
                        break;
                    }
                }
            }
            if (newPos < 0) {
                if (!curRes.isEmpty()) {
                    newPos = curRes.get(0);
                    newLine = line;
                }
            }
            if (newPos >= 0) {
                if (newLine == line && newPos == offsetInLine + column) {
                    setMessage("This is the only occurence");
                    return;
                }
                if ((searchBackwards && (newLine > line || (newLine == line && newPos > offsetInLine + column)))
                    || (!searchBackwards && (newLine < line || (newLine == line && newPos < offsetInLine + column)))) {
                    setMessage("Search Wrapped");
                }
                int width = size.getColumns() - (printLineNumbers ? 8 : 0);
                line = newLine;
                column = newPos;
                offsetInLine = (column / width) * (width - 1);
                ensureCursorVisible();
            } else {
                setMessage("\"" + searchTerm + "\" not found");
            }
        }

        private List<Integer> doSearch(String text) {
            Pattern pat = Pattern.compile(searchTerm,
                    (searchCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                            | (searchRegexp ? 0 : Pattern.LITERAL));
            Matcher m = pat.matcher(text);
            List<Integer> res = new ArrayList<>();
            while (m.find()) {
                res.add(m.start());
            }
            return res;
        }

        void matching() {
            int opening = getCurrentChar();
            int idx = matchBrackets.indexOf(opening);
            if (idx >= 0) {
                int dir = (idx >= matchBrackets.length() / 2) ? -1 : +1;
                int closing = matchBrackets.charAt((idx + matchBrackets.length() / 2) % matchBrackets.length());

                int lvl = 1;
                int cur = line;
                int pos = offsetInLine + column;
                while (true) {
                    if ((pos + dir >= 0) && (pos + dir < getLine(cur).length())) {
                        pos += dir;
                    } else if ((cur + dir >= 0) && (cur + dir < lines.size())) {
                        cur += dir;
                        pos = dir > 0 ? 0 : lines.get(cur).length() - 1;
                        // Skip empty lines
                        if (pos < 0 || pos >= lines.get(cur).length()) {
                            continue;
                        }
                    } else {
                        setMessage("No matching bracket");
                        return;
                    }
                    int c = lines.get(cur).charAt(pos);
                    if (c == opening) {
                        lvl++;
                    } else if (c == closing) {
                        if (--lvl == 0) {
                            line = cur;
                            moveToChar(pos);
                            ensureCursorVisible();
                            return;
                        }
                    }
                }
            } else {
                setMessage("Not a bracket");
            }
        }

        private int length(String line, int tabs) {
            return new AttributedStringBuilder().tabs(tabs).append(line).columnLength();
        }
    }

    public Nano(Terminal terminal, File root) {
        this(terminal, root.toPath());
    }

    private Nano(Terminal terminal, Path root) {
        this(terminal, root, null);
    }

    private Nano(Terminal terminal, Path root, Options opts) {
        this.terminal = terminal;
        this.root = root;
        this.display = new Display(terminal, true);
        this.bindingReader = new BindingReader(terminal.reader());
        this.size = new Size();
        this.restricted = opts != null && opts.isSet("restricted");
        this.vsusp = terminal.getAttributes().getControlChar(ControlChar.VSUSP);
        bindKeys();
    }

    public void open(String... files) throws IOException {
        open(Arrays.asList(files));
    }

    private void open(List<String> files) throws IOException {
        for (String file : files) {
            buffers.add(new Buffer(file));
        }
    }

    public void run() throws IOException {
        if (buffers.isEmpty()) {
            buffers.add(new Buffer(null));
        }
        buffer = buffers.get(bufferIndex);

        Attributes attributes = terminal.getAttributes();
        Attributes newAttr = new Attributes(attributes);
        newAttr.setLocalFlags(EnumSet.of(LocalFlag.ICANON, LocalFlag.ECHO, LocalFlag.IEXTEN), false);
        newAttr.setInputFlags(EnumSet.of(InputFlag.IXON, InputFlag.ICRNL, InputFlag.INLCR), false);
        newAttr.setControlChar(ControlChar.VMIN, 1);
        newAttr.setControlChar(ControlChar.VTIME, 0);
        newAttr.setControlChar(ControlChar.VINTR, 0);
        if (restricted) {
            newAttr.setControlChar(ControlChar.VSUSP, 0);
        }
        terminal.setAttributes(newAttr);
        terminal.puts(Capability.enter_ca_mode);
        terminal.puts(Capability.keypad_xmit);
        if (mouseSupport) {
            terminal.trackMouse(Terminal.MouseTracking.Normal);
        }

        this.shortcuts = standardShortcuts();

        SignalHandler prevHandler = null;
        Status status = Status.getStatus(terminal, false);
        try {
            size.copy(terminal.getSize());
            if (status != null) {
                status.suspend();
            }
            buffer.open();
            if (buffer.file != null) {
                setMessage("Read " + buffer.lines.size() + " lines");
            }

            display.clear();
            display.reset();
            display.resize(size.getRows(), size.getColumns());
            prevHandler = terminal.handle(Signal.WINCH, this::handle);

            display();

            while (true) {
                Operation op;
                switch (op = readOperation(keys)) {
                    case QUIT:
                        if (buffer.lines.size() == 1 && buffer.lines.get(0).isEmpty()) {
                            buffer.dirty = false;
                            if (quit()) return;
                        } else execute();
                        break;
                    case WRITE:
                        write();
                        break;
                    case READ:
                        read();
                        break;
                    case UP:
                        buffer.moveUp(1);
                        break;
                    case DOWN:
                        buffer.moveDown(1);
                        break;
                    case LEFT:
                        buffer.moveLeft(1);
                        break;
                    case RIGHT:
                        buffer.moveRight(1);
                        break;
                    case INSERT:
                        buffer.insert(bindingReader.getLastBinding());
                        break;
                    case BACKSPACE:
                        buffer.backspace(1);
                        break;
                    case DELETE:
                        buffer.delete(1);
                        break;
                    case WRAP:
                        wrap();
                        break;
                    case NUMBERS:
                        numbers();
                        break;
                    case SMOOTH_SCROLLING:
                        smoothScrolling();
                        break;
                    case MOUSE_SUPPORT:
                        mouseSupport();
                        break;
                    case ONE_MORE_LINE:
                        oneMoreLine();
                        break;
                    case CLEAR_SCREEN:
                        clearScreen();
                        break;
                    case PREV_BUFFER:
                        prevBuffer();
                        break;
                    case NEXT_BUFFER:
                        nextBuffer();
                        break;
                    case CUR_POS:
                        curPos();
                        break;
                    case PREV_WORD:
                        buffer.prevWord();
                        break;
                    case NEXT_WORD:
                        buffer.nextWord();
                        break;
                    case BEGINNING_OF_LINE:
                        buffer.beginningOfLine();
                        break;
                    case END_OF_LINE:
                        buffer.endOfLine();
                        break;
                    case FIRST_LINE:
                        buffer.firstLine();
                        break;
                    case LAST_LINE:
                        buffer.lastLine();
                        break;
                    case PREV_PAGE:
                        buffer.prevPage();
                        break;
                    case NEXT_PAGE:
                        buffer.nextPage();
                        break;
                    case SCROLL_UP:
                        buffer.scrollUp(1);
                        break;
                    case SCROLL_DOWN:
                        buffer.scrollDown(1);
                        break;
                    case SEARCH:
                        search();
                        break;
                    case NEXT_SEARCH:
                        buffer.nextSearch();
                        break;
                    case HELP:
                        help("nano-main-help.txt");
                        break;
                    case CONSTANT_CURSOR:
                        constantCursor();
                        break;
                    case VERBATIM:
                        buffer.insert(new String(Character.toChars(bindingReader.readCharacter())));
                        break;
                    case MATCHING:
                        buffer.matching();
                        break;
                    case MOUSE_EVENT:
                        mouseEvent();
                        break;
                    case TOGGLE_SUSPENSION:
                        toggleSuspension();
                        break;
                    default:
                        setMessage("Unsupported " + op.name().toLowerCase().replace('_', '-'));
                        break;
                }
                display();
            }
        } finally {
            if (mouseSupport) {
                terminal.trackMouse(Terminal.MouseTracking.Off);
            }
            terminal.puts(Capability.exit_ca_mode);
            terminal.puts(Capability.keypad_local);
            terminal.flush();
            terminal.setAttributes(attributes);
            terminal.handle(Signal.WINCH, prevHandler);
            if (status != null) {
                status.restore();
            }
       }
    }

    private boolean write() throws IOException {
        KeyMap<Operation> writeKeyMap = new KeyMap<>();
        if (!restricted) {
            writeKeyMap.setUnicode(Operation.INSERT);
            for (char i = 32; i < 256; i++) {
                writeKeyMap.bind(Operation.INSERT, Character.toString(i));
            }
            for (char i = 'A'; i <= 'Z'; i++) {
                writeKeyMap.bind(Operation.DO_LOWER_CASE, alt(i));
            }
            writeKeyMap.bind(Operation.BACKSPACE, del());
            writeKeyMap.bind(Operation.APPEND_MODE, alt('a'));
            writeKeyMap.bind(Operation.PREPEND_MODE, alt('p'));
            writeKeyMap.bind(Operation.BACKUP, alt('b'));
            writeKeyMap.bind(Operation.TO_FILES, ctrl('T'));
        }
        writeKeyMap.bind(Operation.MAC_FORMAT, alt('m'));
        writeKeyMap.bind(Operation.DOS_FORMAT, alt('d'));
        writeKeyMap.bind(Operation.ACCEPT, "\r");
        writeKeyMap.bind(Operation.CANCEL, ctrl('C'));
        writeKeyMap.bind(Operation.HELP, ctrl('G'), key(terminal, Capability.key_f1));
        writeKeyMap.bind(Operation.MOUSE_EVENT, key(terminal, Capability.key_mouse));
        writeKeyMap.bind(Operation.TOGGLE_SUSPENSION, alt('z'));
        writeKeyMap.bind(Operation.RIGHT, key(terminal, Capability.key_right));
        writeKeyMap.bind(Operation.LEFT, key(terminal, Capability.key_left));
      
        editMessage = getWriteMessage();
        editBuffer.setLength(0);
        editBuffer.append(buffer.file == null ? "" : buffer.file);
        int curPos = editBuffer.length();
        this.shortcuts = writeShortcuts();
        display(curPos);
        while (true) {
            switch (readOperation(writeKeyMap)) {
                case INSERT:
                    editBuffer.insert(curPos++, bindingReader.getLastBinding());
                    break;
                case BACKSPACE:
                    if (curPos > 0) {
                        editBuffer.deleteCharAt(--curPos);
                    }
                    break;
                case LEFT:
                    if (curPos > 0) {
                        curPos--;
                    }
                    break;
                case RIGHT:
                    if (curPos < editBuffer.length()) {
                        curPos++;
                    }
                    break;
                case CANCEL:
                    editMessage = null;
                    this.shortcuts = standardShortcuts();
                    return false;
                case ACCEPT:
                    editMessage = null;
                    if (save(editBuffer.toString())) {
                        this.shortcuts = standardShortcuts();
                        return true;
                    }
                    return false;
                case HELP:
                    help("nano-write-help.txt");
                    break;
                case MAC_FORMAT:
                    buffer.format = (buffer.format == WriteFormat.MAC) ? WriteFormat.UNIX : WriteFormat.MAC;
                    break;
                case DOS_FORMAT:
                    buffer.format = (buffer.format == WriteFormat.DOS) ? WriteFormat.UNIX : WriteFormat.DOS;
                    break;
                case APPEND_MODE:
                    writeMode = (writeMode == WriteMode.APPEND) ? WriteMode.WRITE : WriteMode.APPEND;
                    break;
                case PREPEND_MODE:
                    writeMode = (writeMode == WriteMode.PREPEND) ? WriteMode.WRITE : WriteMode.PREPEND;
                    break;
                case BACKUP:
                    writeBackup = !writeBackup;
                    break;
                case MOUSE_EVENT:
                    mouseEvent();
                    break;
                case TOGGLE_SUSPENSION:
                    toggleSuspension();
                    break;
            }
            editMessage = getWriteMessage();
            display(curPos);
        }
    }

    private Operation readOperation(KeyMap<Operation> keymap) {
        while (true) {
            Operation op = bindingReader.readBinding(keymap);
            if (op == Operation.DO_LOWER_CASE) {
                bindingReader.runMacro(bindingReader.getLastBinding().toLowerCase());
            } else {
                return op;
            }
        }
    }

    private boolean save(String name) throws IOException {
        Path orgPath = buffer.file != null ? root.resolve(new File(buffer.file).getCanonicalPath()) : null;
        Path newPath = root.resolve(new File(name).getCanonicalPath());
        boolean isSame = orgPath != null && Files.exists(orgPath) && Files.exists(newPath) && Files.isSameFile(orgPath, newPath);
        if (!isSame && Files.exists(Paths.get(name)) && writeMode == WriteMode.WRITE) {
            Operation op = getYNC("File exists, OVERWRITE ? ");
            if (op != Operation.YES) {
                return false;
            }
        } else if (!Files.exists(newPath)) {
            newPath.toFile().createNewFile();
        }
        Path t = Files.createTempFile(newPath.getParent(), "jline-", ".temp");
        try (OutputStream os = Files.newOutputStream(t, StandardOpenOption.WRITE,
                                                        StandardOpenOption.TRUNCATE_EXISTING,
                                                        StandardOpenOption.CREATE)) {
            if (writeMode == WriteMode.APPEND) {
                if (Files.isReadable(newPath)) {
                    Files.copy(newPath, os);
                }
            }
            Writer w = new OutputStreamWriter(os, buffer.charset);
            for (int i = 0; i < buffer.lines.size(); i++) {
                w.write(buffer.lines.get(i));
                switch (buffer.format) {
                    case UNIX:
                        w.write("\n");
                        break;
                    case DOS:
                        w.write("\r\n");
                        break;
                    case MAC:
                        w.write("\r");
                        break;
                }
            }
            w.flush();
            if (writeMode == WriteMode.PREPEND) {
                if (Files.isReadable(newPath)) {
                    Files.copy(newPath, os);
                }
            }
            if (writeBackup) {
                Files.move(newPath, newPath.resolveSibling(newPath.getFileName().toString() + "~"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(t, newPath, StandardCopyOption.REPLACE_EXISTING);
            if (writeMode == WriteMode.WRITE) {
                buffer.file = name;
                buffer.dirty = false;
            }
            setMessage("Wrote " + buffer.lines.size() + " lines");
            return true;
        } catch (IOException e) {
            setMessage("Error writing " + name + ": " + e.toString());
            return false;
        } finally {
            Files.deleteIfExists(t);
            writeMode = WriteMode.WRITE;
        }
    }

    private Operation getYNC(String message) {
        String oldEditMessage = editMessage;
        String oldEditBuffer = editBuffer.toString();
        LinkedHashMap<String, String> oldShortcuts = shortcuts;
        try {
            editMessage = message;
            editBuffer.setLength(0);
            KeyMap<Operation> yncKeyMap = new KeyMap<>();
            yncKeyMap.bind(Operation.YES, "y", "Y");
            yncKeyMap.bind(Operation.NO, "n", "N");
            yncKeyMap.bind(Operation.CANCEL, ctrl('C'));
            shortcuts = new LinkedHashMap<>();
            shortcuts.put(" Y", "Yes");
            shortcuts.put(" N", "No");
            shortcuts.put("^C", "Cancel");
            display();
            return readOperation(yncKeyMap);
        } finally {
            editMessage = oldEditMessage;
            editBuffer.append(oldEditBuffer);
            shortcuts = oldShortcuts;
        }
    }

    private String getWriteMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("File Name to ");
        switch (writeMode) {
            case WRITE:
                sb.append("Write");
                break;
            case APPEND:
                sb.append("Append");
                break;
            case PREPEND:
                sb.append("Prepend");
                break;
        }
        switch (buffer.format) {
            case UNIX:
                break;
            case DOS:
                sb.append(" [DOS Format]");
                break;
            case MAC:
                sb.append(" [Mac Format]");
                break;
        }
        if (writeBackup) {
            sb.append(" [Backup]");
        }
        sb.append(": ");
        return sb.toString();
    }

    private void read() {
        KeyMap<Operation> readKeyMap = new KeyMap<>();
        readKeyMap.setUnicode(Operation.INSERT);
        for (char i = 32; i < 256; i++) {
            readKeyMap.bind(Operation.INSERT, Character.toString(i));
        }
        for (char i = 'A'; i <= 'Z'; i++) {
            readKeyMap.bind(Operation.DO_LOWER_CASE, alt(i));
        }
        readKeyMap.bind(Operation.BACKSPACE, del());
        readKeyMap.bind(Operation.NEW_BUFFER, alt('f'));
        readKeyMap.bind(Operation.TO_FILES, ctrl('T'));
        readKeyMap.bind(Operation.EXECUTE, ctrl('X'));
        readKeyMap.bind(Operation.ACCEPT, "\r");
        readKeyMap.bind(Operation.CANCEL, ctrl('C'));
        readKeyMap.bind(Operation.HELP, ctrl('G'), key(terminal, Capability.key_f1));
        readKeyMap.bind(Operation.MOUSE_EVENT, key(terminal, Capability.key_mouse));
        readKeyMap.bind(Operation.RIGHT, key(terminal, Capability.key_right));
        readKeyMap.bind(Operation.LEFT, key(terminal, Capability.key_left));

        editMessage = getReadMessage();
        editBuffer.setLength(0);
        int curPos = editBuffer.length();
        this.shortcuts = readShortcuts();
        display(curPos);
        while (true) {
            switch (readOperation(readKeyMap)) {
                case INSERT:
                    editBuffer.insert(curPos++, bindingReader.getLastBinding());
                    break;
                case BACKSPACE:
                    if (curPos > 0) {
                        editBuffer.deleteCharAt(--curPos);
                    }
                    break;
                case LEFT:
                    if (curPos > 0) {
                        curPos--;
                    }
                    break;
                case RIGHT:
                    if (curPos < editBuffer.length()) {
                        curPos++;
                    }
                    break;            
                case CANCEL:
                    editMessage = null;
                    this.shortcuts = standardShortcuts();
                    return;
                case ACCEPT:
                    editMessage = null;
                    String file = editBuffer.toString();
                    boolean empty = file.isEmpty();
                    Path p = empty ? null : root.resolve(file);
                    if (!readNewBuffer && !empty && !Files.exists(p)) {
                        setMessage("\"" + file + "\" not found");
                    } else if (!empty && Files.isDirectory(p)) {
                        setMessage("\"" + file + "\" is a directory");
                    } else if (!empty && !Files.isRegularFile(p)) {
                        setMessage("\"" + file + "\" is not a regular file");
                    } else {
                        Buffer buf = new Buffer(empty ? null : file);
                        try {
                            buf.open();
                            if (readNewBuffer) {
                                buffers.add(++bufferIndex, buf);
                                buffer = buf;
                            } else {
                                buffer.insert(String.join("\n", buf.lines));
                            }
                            setMessage(null);
                        } catch (IOException e) {
                            setMessage("Error reading " + file + ": " + e.getMessage());
                        }
                    }
                    this.shortcuts = standardShortcuts();
                    return;
                case HELP:
                    help("nano-read-help.txt");
                    break;
                case NEW_BUFFER:
                    readNewBuffer = !readNewBuffer;
                    break;
                case MOUSE_EVENT:
                    mouseEvent();
                    break;
                case TOGGLE_SUSPENSION:
                    toggleSuspension();
                    break;
            }
            editMessage = getReadMessage();
            display(curPos);
        }
    }

    private String getReadMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("File to insert");
        if (readNewBuffer) {
            sb.append(" into new buffer");
        }
        sb.append(" [from ./]: ");
        return sb.toString();
    }

    private LinkedHashMap<String, String> readShortcuts() {
        LinkedHashMap<String, String> shortcuts = new LinkedHashMap<>();
        shortcuts.put("^G", "Get Help");
        shortcuts.put("^T", "To Files");
        shortcuts.put("M-F", "New Buffer");
        shortcuts.put("^C", "Cancel");
        shortcuts.put("^X", "Execute Command");
        return shortcuts;
    }

    private LinkedHashMap<String, String> writeShortcuts() {
        LinkedHashMap<String, String> s = new LinkedHashMap<>();
        s.put("^G", "Get Help");
        s.put("M-M", "Mac Format");
        s.put("^C", "Cancel");
        s.put("M-D", "DOS Format");
        if (!restricted) {
            s.put("^T", "To Files");
            s.put("M-P", "Prepend");
            s.put("M-A", "Append");
            s.put("M-B", "Backup File");
        }
        return s;
    }

    private LinkedHashMap<String, String> helpShortcuts() {
        LinkedHashMap<String, String> s = new LinkedHashMap<>();
        s.put("^L", "Refresh");
        s.put("^Y", "Prev Page");
        s.put("^P", "Prev Line");
        s.put("M-\\", "First Line");
        s.put("^X", "Exit");
        s.put("^V", "Next Page");
        s.put("^N", "Next Line");
        s.put("M-/", "Last Line");
        return s;
    }

    private LinkedHashMap<String, String> searchShortcuts() {
        LinkedHashMap<String, String> s = new LinkedHashMap<>();
        s.put("^G", "Get Help");
        s.put("^Y", "First Line");
        s.put("^R", "Replace");
        s.put("^W", "Beg of Par");
        s.put("M-C", "Case Sens");
        s.put("M-R", "Regexp");
        s.put("^C", "Cancel");
        s.put("^V", "Last Line");
        s.put("^T", "Go To Line");
        s.put("^O", "End of Par");
        s.put("M-B", "Backwards");
        s.put("^P", "PrevHstory");
        return s;
    }

    private LinkedHashMap<String, String> standardShortcuts() {
        LinkedHashMap<String, String> s = new LinkedHashMap<>();
        s.put("^G", "Get Help");
        s.put("^O", "WriteOut");
        s.put("^R", "Read File");
        s.put("^Y", "Prev Page");
        s.put("^K", "Cut Text");
        s.put("^C", "Cur Pos");
        s.put("^X", "Execute");
        s.put("^J", "Justify");
        s.put("^W", "Where Is");
        s.put("^V", "Next Page");
        s.put("^U", "UnCut Text");
        s.put("^T", "To Spell");
        return s;
    }

    private void help(String help) {
        Buffer org = this.buffer;
        Buffer newBuf = new Buffer(null);
        try (InputStream is = getClass().getResourceAsStream(help)) {
            newBuf.open(is);
        } catch (IOException e) {
            setMessage("Unable to read help");
            return;
        }
        LinkedHashMap<String, String> oldShortcuts = this.shortcuts;
        this.shortcuts = helpShortcuts();
        boolean oldWrapping = this.wrapping;
        boolean oldPrintLineNumbers = this.printLineNumbers;
        boolean oldConstantCursor = this.constantCursor;
        this.wrapping = true;
        this.printLineNumbers = false;
        this.constantCursor = false;
        this.buffer = newBuf;
        try {
            this.message = null;
            terminal.puts(Capability.cursor_invisible);
            display();
            while (true) {
                switch (readOperation(keys)) {
                    case QUIT:
                        return;
                    case FIRST_LINE:
                        buffer.firstLine();
                        break;
                    case LAST_LINE:
                        buffer.lastLine();
                        break;
                    case PREV_PAGE:
                        buffer.prevPage();
                        break;
                    case NEXT_PAGE:
                        buffer.nextPage();
                        break;
                    case UP:
                        buffer.scrollUp(1);
                        break;
                    case DOWN:
                        buffer.scrollDown(1);
                        break;
                    case CLEAR_SCREEN:
                        clearScreen();
                        break;
                    case MOUSE_EVENT:
                        mouseEvent();
                        break;
                    case TOGGLE_SUSPENSION:
                        toggleSuspension();
                        break;
               }
                display();
            }
        } finally {
            this.buffer = org;
            this.wrapping = oldWrapping;
            this.printLineNumbers = oldPrintLineNumbers;
            this.constantCursor = oldConstantCursor;
            this.shortcuts = oldShortcuts;
            terminal.puts(Capability.cursor_visible);
        }
    }

    private void search() throws IOException {
        KeyMap<Operation> searchKeyMap = new KeyMap<>();
        searchKeyMap.setUnicode(Operation.INSERT);
//        searchKeyMap.setNomatch(Operation.INSERT);
        for (char i = 32; i < 256; i++) {
            searchKeyMap.bind(Operation.INSERT, Character.toString(i));
        }
        for (char i = 'A'; i <= 'Z'; i++) {
            searchKeyMap.bind(Operation.DO_LOWER_CASE, alt(i));
        }
        searchKeyMap.bind(Operation.BACKSPACE, del());
        searchKeyMap.bind(Operation.CASE_SENSITIVE, alt('c'));
        searchKeyMap.bind(Operation.BACKWARDS, alt('b'));
        searchKeyMap.bind(Operation.REGEXP, alt('r'));
        searchKeyMap.bind(Operation.ACCEPT, "\r");
        searchKeyMap.bind(Operation.CANCEL, ctrl('C'));
        searchKeyMap.bind(Operation.FIRST_LINE, ctrl('Y'));
        searchKeyMap.bind(Operation.LAST_LINE, ctrl('V'));
        searchKeyMap.bind(Operation.MOUSE_EVENT, key(terminal, Capability.key_mouse));
        searchKeyMap.bind(Operation.RIGHT, key(terminal, Capability.key_right));
        searchKeyMap.bind(Operation.LEFT, key(terminal, Capability.key_left));
        searchKeyMap.bind(Operation.UP, key(terminal, Capability.key_up));
        searchKeyMap.bind(Operation.DOWN, key(terminal, Capability.key_down));

        editMessage = getSearchMessage();
        editBuffer.setLength(0);
        String currentBuffer = "";
        int curPos = editBuffer.length();
        this.shortcuts = searchShortcuts();
        display(curPos);
        try {
            while (true) {
                switch (readOperation(searchKeyMap)) {
                    case INSERT:
                        editBuffer.insert(curPos++, bindingReader.getLastBinding());
                        break;
                    case BACKSPACE:
                        if (curPos > 0) {
                            editBuffer.deleteCharAt(--curPos);
                        }
                        break;
                    case LEFT:
                        if (curPos > 0) {
                            curPos--;
                        }
                        break;
                    case RIGHT:
                        if (curPos < editBuffer.length()) {
                            curPos++;
                        }
                        break;
                    case UP:
                        searchTermId++;
                        if (searchTermId >= 0 && searchTermId < searchTerms.size()) {
                            if (searchTermId == 0) {
                                currentBuffer = editBuffer.toString();
                            }
                            editBuffer.setLength(0);
                            editBuffer.append(searchTerms.get(searchTermId));
                            curPos = editBuffer.length();
                        } else if (searchTermId >= searchTerms.size()) {
                            searchTermId = searchTerms.size() - 1;
                        }
                        break;
                    case DOWN:
                        if (searchTerms.size() > 0) {
                            searchTermId--;
                            editBuffer.setLength(0);
                            if (searchTermId < 0) {
                                searchTermId = -1;
                                editBuffer.append(currentBuffer);                                    
                            } else {
                                editBuffer.append(searchTerms.get(searchTermId));
                            }
                            curPos = editBuffer.length();
                        }
                        break;
                    case CASE_SENSITIVE:
                        searchCaseSensitive = !searchCaseSensitive;
                        break;
                    case BACKWARDS:
                        searchBackwards = !searchBackwards;
                        break;
                    case REGEXP:
                        searchRegexp = !searchRegexp;
                        break;
                    case CANCEL:
                        return;
                    case ACCEPT:
                        if (editBuffer.length() > 0) {
                            searchTerm = editBuffer.toString();
                        }
                        if (searchTerm == null || searchTerm.isEmpty()) {
                            setMessage("Cancelled");
                        } else {
                            if (!searchTerms.contains(searchTerm)) {
                                searchTerms.add(searchTerm);
                            }
                            searchTermId = -1;
                            buffer.nextSearch();
                        }
                        return;
                    case HELP:
                        help("nano-search-help.txt");
                        break;
                    case FIRST_LINE:
                        buffer.firstLine();
                        return;
                    case LAST_LINE:
                        buffer.lastLine();
                        return;
                    case MOUSE_EVENT:
                        mouseEvent();
                        break;
                    case TOGGLE_SUSPENSION:
                        toggleSuspension();
                        break;
                }
                editMessage = getSearchMessage();
                display(curPos);
            }
        } finally {
            this.shortcuts = standardShortcuts();
            editMessage = null;
        }
    }

    private String getSearchMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Search");
        if (searchCaseSensitive) {
            sb.append(" [Case Sensitive]");
        }
        if (searchRegexp) {
            sb.append(" [Regexp]");
        }
        if (searchBackwards) {
            sb.append(" [Backwards]");
        }
        if (searchTerm != null) {
            sb.append(" [");
            sb.append(searchTerm);
            sb.append("]");
        }
        sb.append(": ");
        return sb.toString();
    }

    private String computeCurPos() {
        int chari = 0;
        int chart = 0;
        for (int i = 0; i < buffer.lines.size(); i++) {
            int l = buffer.lines.get(i).length() + 1;
            if (i < buffer.line) {
                chari += l;
            } else if (i == buffer.line) {
                chari += buffer.offsetInLine + buffer.column;
            }
            chart += l;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("line ");
        sb.append(buffer.line + 1);
        sb.append("/");
        sb.append(buffer.lines.size());
        sb.append(" (");
        sb.append(Math.round((100.0 * buffer.line) / buffer.lines.size()));
        sb.append("%), ");
        sb.append("col ");
        sb.append(buffer.offsetInLine + buffer.column + 1);
        sb.append("/");
        sb.append(buffer.lines.get(buffer.line).length() + 1);
        sb.append(" (");
        if (buffer.lines.get(buffer.line).length() > 0) {
            sb.append(Math.round((100.0 * (buffer.offsetInLine + buffer.column))
                    / (buffer.lines.get(buffer.line).length())));
        } else {
            sb.append("100");
        }
        sb.append("%), ");
        sb.append("char ");
        sb.append(chari + 1);
        sb.append("/");
        sb.append(chart);
        sb.append(" (");
        sb.append(Math.round((100.0 * chari) / chart));
        sb.append("%)");
        return sb.toString();
    }

    private void curPos() {
        setMessage(computeCurPos());
    }

    private void prevBuffer() throws IOException {
        if (buffers.size() > 1) {
            bufferIndex = (bufferIndex + buffers.size() - 1) % buffers.size();
            buffer = buffers.get(bufferIndex);
            setMessage("Switched to " + buffer.getTitle());
            buffer.open();
            display.clear();
        } else {
            setMessage("No more open file buffers");
        }
    }

    private void nextBuffer() throws IOException {
        if (buffers.size() > 1) {
            bufferIndex = (bufferIndex + 1) % buffers.size();
            buffer = buffers.get(bufferIndex);
            setMessage("Switched to " + buffer.getTitle());
            buffer.open();
            display.clear();
        } else {
            setMessage("No more open file buffers");
        }
    }

    public void setMessageInstant(String message) {
        setMessage(message);
        try {
            display();
        } catch (Exception e) {
            // Ignore
        }
    }

    protected void setMessage(String message) {
        if (message != null) {
            int v = size.getColumns() - 5;
            message = message.replaceAll("[\\r\\n\\t]", " ");
            if (message.length() > v) message = message.substring(0, v);
        }

        this.message = message;
        this.nbBindings = 25;
    }

    protected boolean doExecute(List<String> lines) {
        return false;
    }

    protected void execute() throws IOException {
        if (doExecute(buffer.lines))
            buffer.clear();
    }

    private boolean quit() throws IOException {
        if (buffer.dirty) {
            Operation op = getYNC("Save modified buffer (ANSWERING \"No\" WILL DESTROY CHANGES) ? ");
            switch (op) {
                case CANCEL:
                    return false;
                case NO:
                    break;
                case YES:
                    if (!write()) {
                        return false;
                    }
            }
        }
        return dropBuffer();
    }

    protected boolean dropBuffer() throws IOException {
        buffers.remove(bufferIndex);
        if (bufferIndex == buffers.size() && bufferIndex > 0) {
            bufferIndex = buffers.size() - 1;
        }
        if (buffers.isEmpty()) {
            buffer = null;
            return true;
        } else {
            buffer = buffers.get(bufferIndex);
            buffer.open();
            display.clear();
            setMessage("Switched to " + buffer.getTitle());
            return false;
        }
    }

    private void numbers() {
        printLineNumbers = !printLineNumbers;
        resetDisplay();
        setMessage("Lines numbering " + (printLineNumbers ? "enabled" : "disabled"));
    }

    private void smoothScrolling() {
        smoothScrolling = !smoothScrolling;
        setMessage("Smooth scrolling " + (smoothScrolling ? "enabled" : "disabled"));
    }

    private void mouseSupport() throws IOException {
        mouseSupport = !mouseSupport;
        setMessage("Mouse support " + (mouseSupport ? "enabled" : "disabled"));
        terminal.trackMouse(mouseSupport ? Terminal.MouseTracking.Normal : Terminal.MouseTracking.Off);
    }

    private void constantCursor() {
        constantCursor = !constantCursor;
        setMessage("Constant cursor position display " + (constantCursor ? "enabled" : "disabled"));
    }

    private void oneMoreLine() {
        oneMoreLine = !oneMoreLine;
        setMessage("Use of one more line for editing " + (oneMoreLine ? "enabled" : "disabled"));
    }

    private void wrap() {
        wrapping = !wrapping;
        resetDisplay();
        setMessage("Lines wrapping " + (wrapping ? "enabled" : "disabled"));
    }

    private void clearScreen() {
        resetDisplay();
    }

    private void mouseEvent() {
        MouseEvent event = terminal.readMouseEvent();
        if (event.getModifiers().isEmpty() && event.getType() == MouseEvent.Type.Released
                && event.getButton() == MouseEvent.Button.Button1) {
            int x = event.getX();
            int y = event.getY();
            int hdr = buffer.computeHeader().size();
            int ftr = computeFooter().size();
            if (y < hdr) {
                // nothing
            } else if (y < size.getRows() - ftr) {
                buffer.moveTo(x, y - hdr);
            } else {
                int cols = (shortcuts.size() + 1) / 2;
                int cw = size.getColumns() / cols;
                int l = y - (size.getRows() - ftr) - 1;
                int si = l * cols +  x / cw;
                String shortcut = null;
                Iterator<String> it = shortcuts.keySet().iterator();
                while (si-- >= 0 && it.hasNext()) { shortcut = it.next(); }
                if (shortcut != null) {
                    shortcut = shortcut.replaceAll("M-", "\\\\E");
                    String seq = KeyMap.translate(shortcut);
                    bindingReader.runMacro(seq);
                }
            }
        }
        else if (event.getType() == MouseEvent.Type.Wheel) {
            if (event.getButton() == MouseEvent.Button.WheelDown) {
                buffer.moveDown(1);
            } else if (event.getButton() == MouseEvent.Button.WheelUp) {
                buffer.moveUp(1);
            }
        }
    }
    
    private void toggleSuspension(){
        if (restricted) {
            setMessage("This function is disabled in restricted mode");
        } else if (vsusp < 0) {
            setMessage("This function is disabled");
        } else {
            Attributes attrs = terminal.getAttributes();
            int toggle = vsusp;
            String message = "enabled";
            if (attrs.getControlChar(ControlChar.VSUSP) > 0) {
                toggle = 0;
                message = "disabled";
            }
            attrs.setControlChar(ControlChar.VSUSP, toggle);
            terminal.setAttributes(attrs);
            setMessage("Suspension " + message);
        }
    }

    public String getTitle() {
        return title;
    }

    private void resetDisplay() {
        display.clear();
        display.resize(size.getRows(), size.getColumns());
        for (Buffer buffer : buffers) {
            buffer.resetDisplay();
        }
    }

    private synchronized void display() {
        display(null);
    }
    
    private synchronized void display(final Integer editCursor) {
        if (nbBindings > 0) {
            if (--nbBindings == 0) {
                message = null;
            }
        }

        List<AttributedString> header = buffer.computeHeader();
        List<AttributedString> footer = computeFooter();

        int nbLines = size.getRows() - header.size() - footer.size();
        List<AttributedString> newLines = buffer.getDisplayedLines(nbLines);
        newLines.addAll(0, header);
        newLines.addAll(footer);

        // Compute cursor position
        int cursor;
        if (editMessage != null) {
            int crsr = editCursor != null ? editCursor : editBuffer.length();
            cursor = editMessage.length() + crsr;
            cursor = size.cursorPos(size.getRows() - footer.size(), cursor);
        } else {
            cursor = size.cursorPos(header.size(),
                                    buffer.getDisplayedCursor());
        }
        display.update(newLines, cursor);
    }

    private List<AttributedString> computeFooter() {
        List<AttributedString> footer = new ArrayList<>();

        if (editMessage != null) {
            AttributedStringBuilder sb = new AttributedStringBuilder();
            sb.style(AttributedStyle.INVERSE);
            sb.append(editMessage);
            sb.append(editBuffer);
            for (int i = editMessage.length() + editBuffer.length(); i < size.getColumns(); i++) {
                sb.append(' ');
            }
            sb.append('\n');
            footer.add(sb.toAttributedString());
        } else if (message != null || constantCursor) {
            int rwidth = size.getColumns();
            String text = "[ " + (message == null ? computeCurPos() : message) + " ]";
            int len = text.length();
            AttributedStringBuilder sb = new AttributedStringBuilder();
            for (int i = 0; i < (rwidth - len) / 2; i++) {
                sb.append(' ');
            }
            sb.style(AttributedStyle.INVERSE);
            sb.append(text);
            sb.append('\n');
            footer.add(sb.toAttributedString());
        } else {
            footer.add(new AttributedString("\n"));
        }

        Iterator<Entry<String, String>> sit = shortcuts.entrySet().iterator();
        int cols = (shortcuts.size() + 1) / 2;
        int cw = (size.getColumns() - 1) / cols;
        int rem = (size.getColumns() - 1) % cols;
        for (int l = 0; l < 2; l++) {
            AttributedStringBuilder sb = new AttributedStringBuilder();
            for (int c = 0; c < cols; c++) {
                Map.Entry<String, String> entry = sit.hasNext() ? sit.next() : null;
                String key = entry != null ? entry.getKey() : "";
                String val = entry != null ? entry.getValue() : "";
                sb.style(AttributedStyle.INVERSE);
                sb.append(key);
                sb.style(AttributedStyle.DEFAULT);
                sb.append(" ");
                int nb = cw - key.length() - 1 + (c < rem ? 1 : 0);
                if (val.length() > nb) {
                    sb.append(val.substring(0, nb));
                } else {
                    sb.append(val);
                    if (c < cols - 1) {
                        for (int i = 0; i < nb - val.length(); i++) {
                            sb.append(" ");
                        }
                    }
                }
            }
            sb.append('\n');
            footer.add(sb.toAttributedString());
        }

        return footer;
    }

    private void handle(Signal signal) {
        if (buffer != null) {
            size.copy(terminal.getSize());
            buffer.computeAllOffsets();
            buffer.moveToChar(buffer.offsetInLine + buffer.column);
            resetDisplay();
            display();
        }
    }

    private void bindKeys() {
        keys = new KeyMap<>();
        keys.setUnicode(Operation.INSERT);

        for (char i = 32; i < KEYMAP_LENGTH; i++) {
            keys.bind(Operation.INSERT, Character.toString(i));
        }
        keys.bind(Operation.BACKSPACE, del());
        for (char i = 'A'; i <= 'Z'; i++) {
            keys.bind(Operation.DO_LOWER_CASE, alt(i));
        }

        keys.bind(Operation.HELP, ctrl('G'), key(terminal, Capability.key_f1));
        keys.bind(Operation.QUIT, ctrl('X'), key(terminal, Capability.key_f2));
        keys.bind(Operation.WRITE, ctrl('O'), key(terminal, Capability.key_f3));
        keys.bind(Operation.JUSTIFY_PARAGRAPH, ctrl('J'), key(terminal, Capability.key_f4));

        keys.bind(Operation.READ, ctrl('R'), key(terminal, Capability.key_f5));
        keys.bind(Operation.SEARCH, ctrl('W'), key(terminal, Capability.key_f6));
        keys.bind(Operation.PREV_PAGE, ctrl('Y'), key(terminal, Capability.key_f7));
        keys.bind(Operation.NEXT_PAGE, ctrl('V'), key(terminal, Capability.key_f8));

        keys.bind(Operation.CUT, ctrl('K'), key(terminal, Capability.key_f9));
        keys.bind(Operation.UNCUT, ctrl('U'), key(terminal, Capability.key_f10));
        keys.bind(Operation.CUR_POS, ctrl('C'), key(terminal, Capability.key_f11));
        keys.bind(Operation.TO_SPELL, ctrl('T'), key(terminal, Capability.key_f11));

        keys.bind(Operation.GOTO, ctrl('_'), key(terminal, Capability.key_f13), alt('g'));
        keys.bind(Operation.REPLACE, ctrl('\\'), key(terminal, Capability.key_f14), alt('r'));
        keys.bind(Operation.MARK, ctrl('^'), key(terminal, Capability.key_f15), alt('a'));
        keys.bind(Operation.NEXT_SEARCH, key(terminal, Capability.key_f16), alt('w'));

        keys.bind(Operation.COPY, alt('^'));
        keys.bind(Operation.INDENT, alt('}'));
        keys.bind(Operation.UNINDENT, alt('{'));

        keys.bind(Operation.RIGHT, ctrl('F'));
        keys.bind(Operation.LEFT, ctrl('B'));
        keys.bind(Operation.NEXT_WORD, ctrl(' '));
        keys.bind(Operation.PREV_WORD, alt(' '));
        keys.bind(Operation.UP, ctrl('P'));
        keys.bind(Operation.DOWN, ctrl('N'));

        keys.bind(Operation.BEGINNING_OF_LINE, ctrl('A'));
        keys.bind(Operation.END_OF_LINE, ctrl('E'));
        keys.bind(Operation.BEGINNING_OF_PARAGRAPH, alt('('), alt('9'));
        keys.bind(Operation.END_OF_PARAGRAPH, alt(')'), alt('0'));
        keys.bind(Operation.FIRST_LINE, alt('\\'), alt('|'));
        keys.bind(Operation.LAST_LINE, alt('/'), alt('?'));

        keys.bind(Operation.MATCHING, alt(']'));
        keys.bind(Operation.SCROLL_UP, alt('-'), alt('_'));
        keys.bind(Operation.SCROLL_DOWN, alt('+'), alt('='));

        keys.bind(Operation.PREV_BUFFER, alt('<'));
        keys.bind(Operation.NEXT_BUFFER, alt('>'));
        keys.bind(Operation.PREV_BUFFER, alt(','));
        keys.bind(Operation.NEXT_BUFFER, alt('.'));

        keys.bind(Operation.VERBATIM, alt('v'));
        keys.bind(Operation.INSERT, ctrl('I'), ctrl('M'));
        keys.bind(Operation.DELETE, ctrl('D'));
        keys.bind(Operation.BACKSPACE, ctrl('H'));
        keys.bind(Operation.CUT_TO_END, alt('t'));

        keys.bind(Operation.JUSTIFY_FILE, alt('j'));
        keys.bind(Operation.COUNT, alt('d'));
        keys.bind(Operation.CLEAR_SCREEN, ctrl('L'));

        keys.bind(Operation.HELP, alt('x'));
        keys.bind(Operation.CONSTANT_CURSOR, alt('c'));
        keys.bind(Operation.ONE_MORE_LINE, alt('o'));
        keys.bind(Operation.SMOOTH_SCROLLING, alt('s'));
        keys.bind(Operation.MOUSE_SUPPORT, alt('m'));
        keys.bind(Operation.WHITESPACE, alt('p'));
        keys.bind(Operation.HIGHLIGHT, alt('y'));

        keys.bind(Operation.SMART_HOME_KEY, alt('h'));
        keys.bind(Operation.AUTO_INDENT, alt('i'));
        keys.bind(Operation.CUT_TO_END_TOGGLE, alt('k'));
        // TODO: reenable wrapping after fixing #120
        // keys.bind(Operation.WRAP, alt('l'));
        keys.bind(Operation.TABS_TO_SPACE, alt('q'));

        keys.bind(Operation.BACKUP, alt('b'));

        keys.bind(Operation.NUMBERS, alt('n'));

        // TODO: map other keys
        keys.bind(Operation.UP, key(terminal, Capability.key_up));
        keys.bind(Operation.DOWN, key(terminal, Capability.key_down));
        keys.bind(Operation.RIGHT, key(terminal, Capability.key_right));
        keys.bind(Operation.LEFT, key(terminal, Capability.key_left));

        keys.bind(Operation.MOUSE_EVENT, key(terminal, Capability.key_mouse));
        
        keys.bind(Operation.TOGGLE_SUSPENSION, alt('z'));
    }

    protected enum Operation {
        DO_LOWER_CASE,

        QUIT,
        WRITE,
        READ,
        GOTO,
        FIND,

        WRAP,
        NUMBERS,
        SMOOTH_SCROLLING,
        MOUSE_SUPPORT,
        ONE_MORE_LINE,
        CLEAR_SCREEN,

        UP,
        DOWN,
        LEFT,
        RIGHT,

        INSERT,
        BACKSPACE,

        NEXT_BUFFER,
        PREV_BUFFER,

        HELP,
        NEXT_PAGE,
        PREV_PAGE,
        SCROLL_UP,
        SCROLL_DOWN,
        NEXT_WORD,
        PREV_WORD,
        BEGINNING_OF_LINE,
        END_OF_LINE,
        FIRST_LINE,
        LAST_LINE,

        CUR_POS,

        CASE_SENSITIVE,
        BACKWARDS,
        REGEXP,
        ACCEPT,
        CANCEL,
        SEARCH,
        MAC_FORMAT,
        DOS_FORMAT,
        APPEND_MODE,
        PREPEND_MODE,
        BACKUP,
        TO_FILES,
        YES,
        NO,
        NEW_BUFFER,
        EXECUTE,
        NEXT_SEARCH,
        MATCHING,
        VERBATIM,
        DELETE,

        JUSTIFY_PARAGRAPH,
        TO_SPELL,
        CUT,
        REPLACE,
        MARK,
        COPY,
        INDENT,
        UNINDENT,
        BEGINNING_OF_PARAGRAPH,
        END_OF_PARAGRAPH,
        CUT_TO_END,
        JUSTIFY_FILE,
        COUNT,
        CONSTANT_CURSOR,
        WHITESPACE,
        HIGHLIGHT,
        SMART_HOME_KEY,
        AUTO_INDENT,
        CUT_TO_END_TOGGLE,
        TABS_TO_SPACE,
        UNCUT,

        MOUSE_EVENT,
 
        TOGGLE_SUSPENSION
    }

}
