// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import android.support.annotation.UiThread;

import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class Lines {
    final private @Root Kitty kitty = Kitty.make();


    public final static int HEADER_POINTER = -123, MARKER_POINTER = -456;
    public enum LINES {FETCHING, CAN_FETCH_MORE, EVERYTHING_FETCHED}
    public LINES status = null;

    private final static Line HEADER = new Line(HEADER_POINTER, null, null, null, false, false, null);
    private final static Line MARKER = new Line(MARKER_POINTER, null, null, null, false, false, null);

    private final ArrayDeque<Line> filtered = new ArrayDeque<>();
    private final ArrayDeque<Line> unfiltered = new ArrayDeque<>();

    private int skipUnfiltered = -1;
    private int skipFiltered = -1;

    private int skipUnfilteredOffset = -1;
    private int skipFilteredOffset = -1;

    private int maxUnfilteredSize = 0;

    Lines(String name) {
        maxUnfilteredSize = P.lineIncrement;
        kitty.setPrefix(name);
    }

    @Cat ArrayList<Line> getCopy() {
        int skip = P.filterLines ? skipFiltered : skipUnfiltered;
        ArrayList<Line> lines = new ArrayList<>(P.filterLines ? filtered : unfiltered);
        int marker = skip >= 0 && lines.size() > 0 ? lines.size() - skip : -1;
        if (marker > 0) lines.add(marker, MARKER);
        lines.add(0, HEADER);
        return lines;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    void addFirst(Line line) {
        assertEquals(status, LINES.FETCHING);
        ensureSizeBeforeAddition();
        unfiltered.addFirst(line);
        if (line.visible) filtered.addFirst(line);
    }

    void addLast(Line line) {
        if (status == LINES.FETCHING) {
            kitty.warn("addLast() while lines are being fetched");
            return;
        }
        ensureSizeBeforeAddition();
        unfiltered.addLast(line);
        if (line.visible) filtered.addLast(line);

        if (skipFiltered >= 0 && line.visible) skipFiltered++;
        if (skipUnfiltered >= 0) skipUnfiltered++;

        // if we hit max size while the buffer is not open, behave as if lines were requested
        if (!ready() && unfiltered.size() == maxUnfilteredSize) onLinesListed();
    }

    void clear() {
        filtered.clear();
        unfiltered.clear();
        maxUnfilteredSize = P.lineIncrement;
        status = null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    void onMoreLinesRequested() {
        if (status != null) maxUnfilteredSize += P.lineIncrement;
        status = LINES.FETCHING;
    }

    void onLinesListed() {
        status = unfiltered.size() == maxUnfilteredSize ? LINES.CAN_FETCH_MORE : LINES.EVERYTHING_FETCHED;
        setSkipsUsingPointer();
    }

    // true if Lines contain all necessary line information and read marker stuff
    // note that the number of lines can be zero and read markers can be not preset
    public boolean ready() {
        return status == LINES.CAN_FETCH_MORE || status == LINES.EVERYTHING_FETCHED;
    }

    int getMaxLines() {
        return maxUnfilteredSize;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    Iterator<Line> getDescendingFilteredIterator() {
        return filtered.descendingIterator();
    }


    boolean contains(Line line) {
        for (Line l: unfiltered) if (line.pointer == l.pointer) return true;
        return false;
    }

    void processAllMessages(boolean force) {
        for (Line l: unfiltered) if (force) l.processMessage(); else l.processMessageIfNeeded();
    }

    void eraseProcessedMessages() {
        for (Line l: unfiltered) l.eraseProcessedMessage();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    void moveReadMarkerToEnd() {
        if (skipFilteredOffset >= 0 && skipUnfilteredOffset >= 0 && skipFiltered >= skipFilteredOffset && skipUnfiltered >= skipFilteredOffset) {
            skipFiltered -= skipFilteredOffset;
            skipUnfiltered -= skipUnfilteredOffset;
        } else {
            skipFiltered = skipUnfiltered = 0;
        }
        skipFilteredOffset = skipUnfilteredOffset = -1;
    }

    void rememberCurrentSkipsOffset() {
        skipFilteredOffset = skipFiltered;
        skipUnfilteredOffset = skipUnfiltered;
        if (unfiltered.size() > 0) lastSeenLine = unfiltered.getLast().pointer;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private long lastSeenLine = -1;
    @UiThread public long getLastSeenLine() {return lastSeenLine;}
    @UiThread public void setLastSeenLine(long pointer) {lastSeenLine = pointer;}

    private void setSkipsUsingPointer() {
        assertTrue(ready());
        Iterator<Line> it = unfiltered.descendingIterator();
        int idx_f = 0, idx_u = 0;
        skipFiltered = skipUnfiltered = -1;
        while (it.hasNext()) {
            Line line = it.next();
            if (line.pointer == lastSeenLine) {
                skipFiltered = idx_f;
                skipUnfiltered = idx_u;
                //Weechat.showLongToast("%s -> %s %s", name, skipFiltered, skipUnfiltered);
                return;
            }
            idx_u++;
            if (line.visible) idx_f++;
        }
        //Weechat.showLongToast("%s -> fail", name);
    }

//    private void setSkipsUsingHotlist(int h, int u, int o) {
//        Iterator<Line> it = unfiltered.descendingIterator();
//        int hu = h + u;
//        int idx_f = 0, idx_u = 0;
//        skipFiltered = skipUnfiltered = -1;
//        while (it.hasNext()) {
//            Line line = it.next();
//            if (line.highlighted || (line.visible && line.type == Line.LINE_MESSAGE)) hu--;
//            else if (line.visible && line.type == Line.LINE_OTHER) o--;
//            if (hu == -2 || (hu < 0 && o < 0)) {
//                skipFiltered = idx_f;
//                skipUnfiltered = idx_u;
//                return;
//            }
//            idx_u++;
//            if (line.visible) idx_f++;
//        }
//    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void ensureSizeBeforeAddition() {
        if (unfiltered.size() == maxUnfilteredSize)
            if (unfiltered.removeFirst().visible) filtered.removeFirst();
    }
}
