//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.server.patch.gitfilediff;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.PatchType;
import java.util.Optional;
import org.eclipse.jgit.patch.CombinedFileHeader;
import org.eclipse.jgit.patch.FileHeader;

/** A utility class for the {@link FileHeader} JGit object */
public class FileHeaderUtil {
  private static final Byte NUL = '\0';

  /**
   * The maximum number of characters to lookup in the binary file {@link FileHeader}. This is used
   * to scan the file header for the occurrence of the {@link #NUL} character.
   *
   * <p>This limit assumes a uniform distribution of all characters, hence the probability of the
   * occurrence of each character = (1 / 256). We want to find the limit that makes the prob. of
   * finding {@link #NUL} > 0.999. 1 - (255 / 256) ^ N > 0.999 yields N = 1766. We set the limit to
   * this value multiplied by 10 for more confidence.
   */
  private static final int BIN_FILE_MAX_SCAN_LIMIT = 20000;

  /** Converts the {@link FileHeader} parameter ot a String representation. */
  static String toString(FileHeader header) {
    return new String(FileHeaderUtil.toByteArray(header), UTF_8);
  }

  /** Converts the {@link FileHeader} parameter to a byte array. */
  static byte[] toByteArray(FileHeader header) {
    int end = getEndOffset(header);
    if (header.getStartOffset() == 0 && end == header.getBuffer().length) {
      return header.getBuffer();
    }

    final byte[] buf = new byte[end - header.getStartOffset()];
    System.arraycopy(header.getBuffer(), header.getStartOffset(), buf, 0, buf.length);
    return buf;
  }

  /**
   * Returns the old file path associated with the {@link FileHeader}, or empty if the file is
   * {@link com.google.gerrit.entities.Patch.ChangeType#ADDED} or {@link
   * com.google.gerrit.entities.Patch.ChangeType#REWRITE}.
   */
  static Optional<String> getOldPath(FileHeader header) {
    Patch.ChangeType changeType = getChangeType(header);
    switch (changeType) {
      case DELETED:
      case COPIED:
      case RENAMED:
      case MODIFIED:
        return Optional.of(header.getOldPath());

      case ADDED:
      case REWRITE:
        return Optional.empty();
    }
    return Optional.empty();
  }

  /**
   * Returns the new file path associated with the {@link FileHeader}, or empty if the file is
   * {@link com.google.gerrit.entities.Patch.ChangeType#DELETED}.
   */
  static Optional<String> getNewPath(FileHeader header) {
    Patch.ChangeType changeType = getChangeType(header);
    switch (changeType) {
      case DELETED:
        return Optional.empty();

      case ADDED:
      case MODIFIED:
      case REWRITE:
      case COPIED:
      case RENAMED:
        return Optional.of(header.getNewPath());
    }
    return Optional.empty();
  }

  /** Returns the change type associated with the file header. */
  static Patch.ChangeType getChangeType(FileHeader header) {
    // In Gerrit, we define our own entities  of the JGit entities, so that we have full control
    // over their behaviors (e.g. making sure that these entities are immutable so that we can add
    // them as fields of keys / values of persisted caches).

    // TODO(ghareeb): remove the dead code of the value REWRITE and all its handling
    switch (header.getChangeType()) {
      case ADD:
        return Patch.ChangeType.ADDED;
      case MODIFY:
        return Patch.ChangeType.MODIFIED;
      case DELETE:
        return Patch.ChangeType.DELETED;
      case RENAME:
        return Patch.ChangeType.RENAMED;
      case COPY:
        return Patch.ChangeType.COPIED;
      default:
        throw new IllegalArgumentException("Unsupported type " + header.getChangeType());
    }
  }

  static PatchType getPatchType(FileHeader header) {
    PatchType patchType;

    switch (header.getPatchType()) {
      case UNIFIED:
        patchType = Patch.PatchType.UNIFIED;
        break;
      case GIT_BINARY:
      case BINARY:
        patchType = Patch.PatchType.BINARY;
        break;
      default:
        throw new IllegalArgumentException("Unsupported type " + header.getPatchType());
    }

    if (patchType != PatchType.BINARY) {
      byte[] buf = header.getBuffer();
      // TODO(ghareeb): should we adjust the max limit threshold?
      // JGit sometimes misses the detection of binary files. In this case we look into the file
      // header for the occurrence of NUL characters, which is a definite signal that the file is
      // binary. We limit the number of characters to lookup to avoid performance bottlenecks.
      for (int ptr = header.getStartOffset();
          ptr < Math.min(header.getEndOffset(), BIN_FILE_MAX_SCAN_LIMIT);
          ptr++) {
        if (buf[ptr] == NUL) {
          // It's really binary, but Git couldn't see the nul early enough to realize its binary,
          // and instead produced the diff.
          //
          // Force it to be a binary; it really should have been that.
          return PatchType.BINARY;
        }
      }
    }
    return patchType;
  }

  /**
   * Returns the end offset of the diff header line of the {@code FileHeader parameter} before the
   * appearance of any file edits (diff hunks).
   */
  private static int getEndOffset(FileHeader fileHeader) {
    if (fileHeader instanceof CombinedFileHeader) {
      return fileHeader.getEndOffset();
    }
    if (!fileHeader.getHunks().isEmpty()) {
      return fileHeader.getHunks().get(0).getStartOffset();
    }
    return fileHeader.getEndOffset();
  }
}
