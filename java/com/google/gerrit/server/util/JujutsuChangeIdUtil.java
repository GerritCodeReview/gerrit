// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.util;

import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Utilities for handling Jujutsu (jj) change identifiers.
 *
 * <p>Jujutsu stores change IDs as a {@code change-id} header in the raw git commit object (before
 * the blank line that separates headers from the commit message body), rather than as a footer in
 * the commit message.
 *
 * <p>The JJ change-id format is a 32-character lowercase string (reverse-hex encoding), e.g. {@code
 * mlqnqnkrxpuvuuxzlzoltostwlwyskpx}, which is distinct from Gerrit's {@code I}-prefixed SHA1-based
 * Change-Id.
 */
public final class JujutsuChangeIdUtil {

  /** Pattern matching a Jujutsu change ID: exactly 32 lowercase letters. */
  private static final Pattern JJ_CHANGE_ID_PATTERN = Pattern.compile("^[a-z]{32}$");

  private static final byte[] CHANGE_ID_HEADER = Constants.encode("change-id ");

  private JujutsuChangeIdUtil() {}

  /**
   * Returns {@code true} if the given string looks like a Jujutsu change ID (32 lowercase letters).
   */
  public static boolean isJujutsuChangeId(String id) {
    return id != null && JJ_CHANGE_ID_PATTERN.matcher(id).matches();
  }

  /**
   * Reads the {@code change-id} commit-object header from a JGit {@link RevCommit}, if present.
   *
   * <pre>
   * tree ...
   * parent ...
   * author ...
   * committer ...
   * change-id mlqnqnkrxpuvuuxzlzoltostwlwyskpx
   *
   * Commit message here
   * </pre>
   *
   * @param commit the commit to inspect; must have its raw buffer populated (i.e. parsed with a
   *     {@code RevWalk})
   * @return the JJ change ID value, or {@link Optional#empty()} if not present or not in JJ format
   */
  public static Optional<String> getChangeIdFromCommitHeader(RevCommit commit) {
    byte[] raw = commit.getRawBuffer();
    if (raw == null) {
      return Optional.empty();
    }

    int ptr = 0;
    while (ptr < raw.length) {
      int eol = RawParseUtils.nextLF(raw, ptr);

      // A line containing only '\n' is the blank line ending the header section.
      if (eol == ptr + 1) {
        break;
      }

      int matchEnd = RawParseUtils.match(raw, ptr, CHANGE_ID_HEADER);
      if (matchEnd >= 0) {
        // matchEnd points to the first byte of the value; eol-1 is just before the '\n'.
        int valueEnd = eol - 1;
        if (valueEnd > matchEnd) {
          String value = RawParseUtils.decode(raw, matchEnd, valueEnd).trim();
          if (isJujutsuChangeId(value)) {
            return Optional.of(value);
          }
        }
      }

      ptr = eol;
    }

    return Optional.empty();
  }

  /**
   * Returns {@code true} if the commit contains a {@code change-id} header in Jujutsu format.
   *
   * <p>Convenience wrapper around {@link #getChangeIdFromCommitHeader(RevCommit)}.
   */
  public static boolean hasJujutsuChangeId(RevCommit commit) {
    return getChangeIdFromCommitHeader(commit).isPresent();
  }
}
