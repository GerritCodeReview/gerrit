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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.text.SimpleDateFormat;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.RawParseUtils;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Text extends RawText {
  private static final Logger log = LoggerFactory.getLogger(Text.class);
  private static final int bigFileThreshold = PackConfig.DEFAULT_BIG_FILE_THRESHOLD;

  public static final byte[] NO_BYTES = {};
  public static final Text EMPTY = new Text(NO_BYTES);

  public static Text forCommit(ObjectReader reader, AnyObjectId commitId) throws IOException {
    try (RevWalk rw = new RevWalk(reader)) {
      RevCommit c;
      if (commitId instanceof RevCommit) {
        c = (RevCommit) commitId;
      } else {
        c = rw.parseCommit(commitId);
      }

      StringBuilder b = new StringBuilder();
      switch (c.getParentCount()) {
        case 0:
          break;
        case 1:
          {
            RevCommit p = c.getParent(0);
            rw.parseBody(p);
            b.append("Parent:     ");
            b.append(reader.abbreviate(p, 8).name());
            b.append(" (");
            b.append(p.getShortMessage());
            b.append(")\n");
            break;
          }
        default:
          for (int i = 0; i < c.getParentCount(); i++) {
            RevCommit p = c.getParent(i);
            rw.parseBody(p);
            b.append(i == 0 ? "Merge Of:   " : "            ");
            b.append(reader.abbreviate(p, 8).name());
            b.append(" (");
            b.append(p.getShortMessage());
            b.append(")\n");
          }
      }
      appendPersonIdent(b, "Author", c.getAuthorIdent());
      appendPersonIdent(b, "Commit", c.getCommitterIdent());
      b.append("\n");
      b.append(c.getFullMessage());
      return new Text(b.toString().getBytes(UTF_8));
    }
  }

  public static Text forMergeList(
      ComparisonType comparisonType, ObjectReader reader, AnyObjectId commitId) throws IOException {
    try (RevWalk rw = new RevWalk(reader)) {
      RevCommit c = rw.parseCommit(commitId);
      StringBuilder b = new StringBuilder();
      switch (c.getParentCount()) {
        case 0:
          break;
        case 1:
          {
            break;
          }
        default:
          int uniterestingParent =
              comparisonType.isAgainstParent() ? comparisonType.getParentNum() : 1;

          b.append("Merge List:\n\n");
          for (RevCommit commit : MergeListBuilder.build(rw, c, uniterestingParent)) {
            b.append("* ");
            b.append(reader.abbreviate(commit, 8).name());
            b.append(" ");
            b.append(commit.getShortMessage());
            b.append("\n");
          }
      }
      return new Text(b.toString().getBytes(UTF_8));
    }
  }

  private static void appendPersonIdent(StringBuilder b, String field, PersonIdent person) {
    if (person != null) {
      b.append(field).append(":    ");
      if (person.getName() != null) {
        b.append(" ");
        b.append(person.getName());
      }
      if (person.getEmailAddress() != null) {
        b.append(" <");
        b.append(person.getEmailAddress());
        b.append(">");
      }
      b.append("\n");

      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZ");
      sdf.setTimeZone(person.getTimeZone());
      b.append(field).append("Date: ");
      b.append(sdf.format(person.getWhen()));
      b.append("\n");
    }
  }

  public static byte[] asByteArray(ObjectLoader ldr)
      throws MissingObjectException, LargeObjectException, IOException {
    return ldr.getCachedBytes(bigFileThreshold);
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

  public Text(ObjectLoader ldr) throws MissingObjectException, LargeObjectException, IOException {
    this(asByteArray(ldr));
  }

  public byte[] getContent() {
    return content;
  }

  @Override
  protected String decode(final int s, int e) {
    if (charset == null) {
      charset = charset(content, null);
    }
    return RawParseUtils.decode(charset, content, s, e);
  }
}
