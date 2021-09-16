/*
 * LZDecoder
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz.lz;

import java.io.DataInputStream;
import java.io.IOException;
import org.tukaani.xz.ArrayCache;
import org.tukaani.xz.CorruptedInputException;

public final class LZDecoder {
    private final byte[] buf;
    private final int bufSize; // To avoid buf.length with an array-cached buf.
    private int start = 0;
    private int pos = 0;
    private int full = 0;
    private int limit = 0;
    private int pendingLen = 0;
    private int pendingDist = 0;

    public LZDecoder(int dictSize, byte[] presetDict, ArrayCache arrayCache) {
        bufSize = dictSize;
        buf = arrayCache.getByteArray(bufSize, false);

        if (presetDict != null) {
            pos = Math.min(presetDict.length, dictSize);
            full = pos;
            start = pos;
            System.arraycopy(presetDict, presetDict.length - pos, buf, 0, pos);
        }
    }

    public void putArraysToCache(ArrayCache arrayCache) {
        arrayCache.putArray(buf);
    }

    public void reset() {
        start = 0;
        pos = 0;
        full = 0;
        limit = 0;
        buf[bufSize - 1] = 0x00;
    }

    public void setLimit(int outMax) {
        if (bufSize - pos <= outMax)
            limit = bufSize;
        else
            limit = pos + outMax;
    }

    public boolean hasSpace() {
        return pos < limit;
    }

    public boolean hasPending() {
        return pendingLen > 0;
    }

    public int getPos() {
        return pos;
    }

    public int getByte(int dist) {
        int offset = pos - dist - 1;
        if (dist >= pos)
            offset += bufSize;

        return buf[offset] & 0xFF;
    }

    public void putByte(byte b) {
        buf[pos++] = b;

        if (full < pos)
            full = pos;
    }

    public void repeat(int dist, int len) throws IOException {
        if (dist < 0 || dist >= full)
            throw new CorruptedInputException();

        int left = Math.min(limit - pos, len);
        pendingLen = len - left;
        pendingDist = dist;

        int back = pos - dist - 1;
        if (back < 0) {
            // The distance wraps around to the end of the cyclic dictionary
            // buffer. We cannot get here if the dictionary isn't full.
            assert full == bufSize;
            back += bufSize;

            // Here we will never copy more than dist + 1 bytes and
            // so the copying won't repeat from its own output.
            // Thus, we can always use arraycopy safely.
            int copySize = Math.min(bufSize - back, left);
            assert copySize <= dist + 1;

            System.arraycopy(buf, back, buf, pos, copySize);
            pos += copySize;
            back = 0;
            left -= copySize;

            if (left == 0)
                return;
        }

        assert back < pos;
        assert left > 0;

        do {
            // Determine the number of bytes to copy on this loop iteration:
            // copySize is set so that the source and destination ranges
            // don't overlap. If "left" is large enough, the destination
            // range will start right after the last byte of the source
            // range. This way we don't need to advance "back" which
            // allows the next iteration of this loop to copy (up to)
            // twice the number of bytes.
            int copySize = Math.min(left, pos - back);
            System.arraycopy(buf, back, buf, pos, copySize);
            pos += copySize;
            left -= copySize;
        } while (left > 0);

        if (full < pos)
            full = pos;
    }

    public void repeatPending() throws IOException {
        if (pendingLen > 0)
            repeat(pendingDist, pendingLen);
    }

    public void copyUncompressed(DataInputStream inData, int len)
            throws IOException {
        int copySize = Math.min(bufSize - pos, len);
        inData.readFully(buf, pos, copySize);
        pos += copySize;

        if (full < pos)
            full = pos;
    }

    public int flush(byte[] out, int outOff) {
        int copySize = pos - start;
        if (pos == bufSize)
            pos = 0;

        System.arraycopy(buf, start, out, outOff, copySize);
        start = pos;

        return copySize;
    }
}
