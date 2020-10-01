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

import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.RawParseUtils;
import org.mozilla.universalchardet.UniversalDetector;

public class Text extends RawText {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int bigFileThreshold = PackConfig.DEFAULT_BIG_FILE_THRESHOLD;

  public static final byte[] NO_BYTES = {};
  public static final Text EMPTY = new Text(NO_BYTES);

  public static Text forCommit(ObjectReader reader, AnyObjectId commitId) throws IOException {
    MagicFile commitMessageFile = MagicFile.forCommitMessage(reader, commitId);
    return new Text(commitMessageFile.getFileContent().getBytes(UTF_8));
  }

  public static Text forMergeList(
      ComparisonType comparisonType, ObjectReader reader, AnyObjectId commitId) throws IOException {
    MagicFile mergeListFile = MagicFile.forMergeList(comparisonType, reader, commitId);
    return new Text(mergeListFile.getFileContent().getBytes(UTF_8));
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
      logger.atSevere().log("Invalid detected charset name '%s': %s", encoding, err.getMessage());
      return ISO_8859_1;

    } catch (UnsupportedCharsetException err) {
      logger.atSevere().log("Detected charset '%s' not supported: %s", encoding, err.getMessage());
      return ISO_8859_1;
    }
  }

  private Charset charset;

  public Text(byte[] r) {
    super(r);
  }

  public Text(ObjectLoader ldr) throws MissingObjectException, LargeObjectException, IOException {
    this(asByteArray(ldr));
  }

  public byte[] getContent() {
    return content;
  }

  @Override
  protected String decode(int s, int e) {
    if (charset == null) {
      charset = charset(content, null);
    }
    return RawParseUtils.decode(charset, content, s, e);
  }
}
