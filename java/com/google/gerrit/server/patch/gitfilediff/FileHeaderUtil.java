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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.PatchType;
import java.util.Optional;
import org.eclipse.jgit.patch.CombinedFileHeader;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.RawParseUtils;

public class FileHeaderUtil {
  static byte[] toByteArray(FileHeader h) {
    final int end = end(h);
    if (h.getStartOffset() == 0 && end == h.getBuffer().length) {
      return h.getBuffer();
    }

    final byte[] buf = new byte[end - h.getStartOffset()];
    System.arraycopy(h.getBuffer(), h.getStartOffset(), buf, 0, buf.length);
    return buf;
  }

  public static ImmutableList<String> getHeaderLines(FileHeader fileHeader) {
    return getHeaderLines(toByteArray(fileHeader));
  }

  public static ImmutableList<String> getHeaderLines(String header) {
    return getHeaderLines(header.getBytes(UTF_8));
  }

  public static ImmutableList<String> getHeaderLines(byte[] header) {
    final IntList m = RawParseUtils.lineMap(header, 0, header.length);
    final ImmutableList.Builder<String> headerLines =
        ImmutableList.builderWithExpectedSize(m.size() - 1);
    for (int i = 1; i < m.size() - 1; i++) {
      final int b = m.get(i);
      int e = m.get(i + 1);
      if (header[e - 1] == '\n') {
        e--;
      }
      headerLines.add(RawParseUtils.decode(UTF_8, header, b, e));
    }
    return headerLines.build();
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

  public static Optional<String> getOldPath(
      org.eclipse.jgit.patch.FileHeader header, Patch.ChangeType changeType) {
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

  public static Optional<String> getNewPath(
      org.eclipse.jgit.patch.FileHeader header, Patch.ChangeType changeType) {
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

  public static Patch.ChangeType getChangeType(org.eclipse.jgit.patch.FileHeader header) {
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

  public static PatchType getPatchType(FileHeader hdr) {
    PatchType pt;

    switch (hdr.getPatchType()) {
      case UNIFIED:
        pt = Patch.PatchType.UNIFIED;
        break;
      case GIT_BINARY:
      case BINARY:
        pt = Patch.PatchType.BINARY;
        break;
      default:
        throw new IllegalArgumentException("Unsupported type " + hdr.getPatchType());
    }

    if (pt != PatchType.BINARY) {
      final byte[] buf = hdr.getBuffer();
      for (int ptr = hdr.getStartOffset(); ptr < hdr.getEndOffset(); ptr++) {
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
}
