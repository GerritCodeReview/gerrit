// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

public class Text extends RawText {
  private static final Logger log = LoggerFactory.getLogger(Text.class);
  private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
  private static final int bigFileThreshold = 10 * 1024 * 1024;

  public static final byte[] NO_BYTES = {};
  public static final Text EMPTY = new Text(NO_BYTES);

  public static String asString(byte[] content, String encoding) {
    return new String(content, charset(content, encoding));
  }

  public static byte[] asByteArray(ObjectLoader ldr)
      throws MissingObjectException, LargeObjectException, IOException {
    if (!ldr.isLarge()) {
      return ldr.getCachedBytes();
    }

    long sz = ldr.getSize();
    if (sz > bigFileThreshold || sz > Integer.MAX_VALUE)
      throw new LargeObjectException();

    byte[] buf;
    try {
      buf = new byte[(int) sz];
    } catch (OutOfMemoryError noMemory) {
      LargeObjectException e;

      e = new LargeObjectException();
      e.initCause(noMemory);
      throw e;
    }

    InputStream in = ldr.openStream();
    try {
      IO.readFully(in, buf, 0, buf.length);
    } finally {
      in.close();
    }
    return buf;
  }

  private static Charset charset(byte[] content, String encoding) {
    if (encoding == null) {
      UniversalDetector d = new UniversalDetector(null);
      d.handleData(content, 0, content.length);
      d.dataEnd();
      encoding = d.getDetectedCharset();
    }
    if (encoding == null) {
      return ISO_8859_1;
    }
    try {
      return Charset.forName(encoding);

    } catch (IllegalCharsetNameException err) {
      log.error("Invalid detected charset name '" + encoding + "': " + err);
      return ISO_8859_1;

    } catch (UnsupportedCharsetException err) {
      log.error("Detected charset '" + encoding + "' not supported: " + err);
      return ISO_8859_1;
    }
  }

  private Charset charset;

  public Text(final byte[] r) {
    super(r);
  }

  public Text(ObjectLoader ldr) throws MissingObjectException,
      LargeObjectException, IOException {
    this(asByteArray(ldr));
  }

  public byte[] getContent() {
    return content;
  }

  public String getLine(final int i) {
    return getLines(i, i + 1, true);
  }

  public String getLines(final int begin, final int end, boolean dropLF) {
    if (begin == end) {
      return "";
    }

    final int s = getLineStart(begin);
    int e = getLineEnd(end - 1);
    if (dropLF && content[e - 1] == '\n') {
      e--;
    }
    return decode(s, e);
  }

  private String decode(final int s, int e) {
    if (charset == null) {
      charset = charset(content, null);
    }
    return RawParseUtils.decode(charset, content, s, e);
  }

  private int getLineStart(final int i) {
    return lines.get(i + 1);
  }

  private int getLineEnd(final int i) {
    return lines.get(i + 2);
  }
}
