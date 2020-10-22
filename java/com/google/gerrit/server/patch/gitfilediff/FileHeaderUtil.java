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

  /** Converts the {@link FileHeader} parameter ot a String representation. */
  static String toString(FileHeader header) {
    return new String(FileHeaderUtil.toByteArray(header), UTF_8);
  }

  /** Converts the {@link FileHeader} parameter to a byte array. */
  static byte[] toByteArray(FileHeader header) {
    final int end = end(header);
    if (header.getStartOffset() == 0 && end == header.getBuffer().length) {
      return header.getBuffer();
    }

    final byte[] buf = new byte[end - header.getStartOffset()];
    System.arraycopy(header.getBuffer(), header.getStartOffset(), buf, 0, buf.length);
    return buf;
  }

  /**
   * Returns the old file path associated with the {@link FileHeader}, or empty if the file is
   * {@link Patch.ChangeType#ADDED} or {@link Patch.ChangeType#REWRITE}.
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
   * {@link Patch.ChangeType#DELETED}.
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
    PatchType pt;

    switch (header.getPatchType()) {
      case UNIFIED:
        pt = Patch.PatchType.UNIFIED;
        break;
      case GIT_BINARY:
      case BINARY:
        pt = Patch.PatchType.BINARY;
        break;
      default:
        throw new IllegalArgumentException("Unsupported type " + header.getPatchType());
    }

    if (pt != PatchType.BINARY) {
      final byte[] buf = header.getBuffer();
      for (int ptr = header.getStartOffset(); ptr < header.getEndOffset(); ptr++) {
        if (buf[ptr] == '\0') {
          // Its really binary, but Git couldn't see the nul early enough
          // to realize its binary, and instead produced the diff.
          //
          // Force it to be a binary; it really should have been that.
          //
          pt = PatchType.BINARY;
          break;
        }
      }
    }
    return pt;
  }

  private static int end(FileHeader h) {
    if (h instanceof CombinedFileHeader) {
      return h.getEndOffset();
    }
    if (!h.getHunks().isEmpty()) {
      return h.getHunks().get(0).getStartOffset();
    }
    return h.getEndOffset();
  }
}
