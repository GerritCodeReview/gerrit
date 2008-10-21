// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.codereview.manager.unpack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Buffering input stream which can read entire lines. */
class RecordInputStream extends InputStream {
  private InputStream in;
  private byte[] buf;
  private int pos;
  private int end;

  RecordInputStream(final InputStream in) {
    this.in = in;
    buf = new byte[4096];
  }

  @Override
  public void close() throws IOException {
    try {
      in.close();
    } finally {
      in = null;
      buf = null;
    }
  }

  private boolean fill() throws IOException {
    pos = 0;
    end = in.read(buf, pos, buf.length);
    if (end < 0) {
      end = 0;
      return false;
    }
    return true;
  }

  @Override
  public int read() throws IOException {
    if (pos == end && !fill()) {
      return -1;
    }
    return buf[pos++];
  }

  @Override
  public int read(final byte[] b, final int off, final int len)
      throws IOException {
    if (pos == end && !fill()) {
      return -1;
    }
    final int cnt = Math.min(len, end - pos);
    System.arraycopy(buf, pos, b, off, cnt);
    pos += cnt;
    return cnt;
  }

  /**
   * Read a record terminated by the separator byte.
   * 
   * @param sep byte which delimits the end of a record.
   * @return record content, with the separator removed from the end. The empty
   *         array indicates an empty record; null indicates stream EOF.
   * @throws IOException the stream could not be read from.
   */
  byte[] readRecord(final int sep) throws IOException {
    if (pos == end && !fill()) {
      return null;
    }

    byte[] line = fastReadRecord(sep);
    if (line != null) {
      return line;
    }

    if (end - pos <= buf.length / 2) {
      int cnt = end - pos;
      System.arraycopy(buf, pos, buf, 0, cnt);
      pos = 0;
      end = cnt;

      cnt = in.read(buf, end, buf.length - end);
      if (cnt < 0) {
        final byte[] r = new byte[end];
        System.arraycopy(buf, 0, r, 0, end);
        pos = end = 0;
        return r;
      }
      end += cnt;

      line = fastReadRecord(sep);
      if (line != null) {
        return line;
      }
    }

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(buf, pos, end - pos);
    for (;;) {
      if (!fill()) {
        return out.toByteArray();
      }

      for (int lf = pos; lf < end; lf++) {
        if (buf[lf] == sep) {
          out.write(buf, pos, lf - pos);
          pos = lf + 1;
          return out.toByteArray();
        }
      }

      out.write(buf, pos, end - pos);
      pos = end;
    }
  }

  private byte[] fastReadRecord(final int sep) {
    for (int lf = pos; lf < end; lf++) {
      if (buf[lf] == sep) {
        final int cnt = lf - pos;
        final byte[] r = new byte[cnt];
        System.arraycopy(buf, pos, r, 0, cnt);
        pos = lf + 1;
        return r;
      }
    }
    return null;
  }
}
