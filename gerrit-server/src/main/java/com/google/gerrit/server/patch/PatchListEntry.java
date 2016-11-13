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

import static com.google.gerrit.server.ioutil.BasicSerialization.readBytes;
import static com.google.gerrit.server.ioutil.BasicSerialization.readEnum;
import static com.google.gerrit.server.ioutil.BasicSerialization.readFixInt64;
import static com.google.gerrit.server.ioutil.BasicSerialization.readString;
import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeBytes;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeEnum;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeFixInt64;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeString;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;

import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.Patch.PatchType;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.patch.CombinedFileHeader;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.RawParseUtils;

public class PatchListEntry {
  private static final byte[] EMPTY_HEADER = {};

  static PatchListEntry empty(final String fileName) {
    return new PatchListEntry(
        ChangeType.MODIFIED,
        PatchType.UNIFIED,
        null,
        fileName,
        EMPTY_HEADER,
        Collections.<Edit>emptyList(),
        0,
        0,
        0,
        0);
  }

  private final ChangeType changeType;
  private final PatchType patchType;
  private final String oldName;
  private final String newName;
  private final byte[] header;
  private final List<Edit> edits;
  private final int insertions;
  private final int deletions;
  private final long size;
  private final long sizeDelta;
  // Note: When adding new fields, the serialVersionUID in PatchListKey must be
  // incremented so that entries from the cache are automatically invalidated.

  PatchListEntry(FileHeader hdr, List<Edit> editList, long size, long sizeDelta) {
    changeType = toChangeType(hdr);
    patchType = toPatchType(hdr);

    switch (changeType) {
      case DELETED:
        oldName = null;
        newName = hdr.getOldPath();
        break;

      case ADDED:
      case MODIFIED:
      case REWRITE:
        oldName = null;
        newName = hdr.getNewPath();
        break;

      case COPIED:
      case RENAMED:
        oldName = hdr.getOldPath();
        newName = hdr.getNewPath();
        break;

      default:
        throw new IllegalArgumentException("Unsupported type " + changeType);
    }

    header = compact(hdr);

    if (hdr instanceof CombinedFileHeader || hdr.getHunks().isEmpty()) {
      edits = Collections.emptyList();
    } else {
      edits = Collections.unmodifiableList(editList);
    }

    int ins = 0;
    int del = 0;
    for (Edit e : editList) {
      del += e.getEndA() - e.getBeginA();
      ins += e.getEndB() - e.getBeginB();
    }
    insertions = ins;
    deletions = del;
    this.size = size;
    this.sizeDelta = sizeDelta;
  }

  private PatchListEntry(
      ChangeType changeType,
      PatchType patchType,
      String oldName,
      String newName,
      byte[] header,
      List<Edit> edits,
      int insertions,
      int deletions,
      long size,
      long sizeDelta) {
    this.changeType = changeType;
    this.patchType = patchType;
    this.oldName = oldName;
    this.newName = newName;
    this.header = header;
    this.edits = edits;
    this.insertions = insertions;
    this.deletions = deletions;
    this.size = size;
    this.sizeDelta = sizeDelta;
  }

  int weigh() {
    int size = 16 + 6 * 8 + 2 * 4 + 20 + 16 + 8 + 4 + 20;
    size += stringSize(oldName);
    size += stringSize(newName);
    size += header.length;
    size += (8 + 16 + 4 * 4) * edits.size();
    return size;
  }

  private static int stringSize(String str) {
    if (str != null) {
      return 16 + 3 * 4 + 16 + str.length() * 2;
    }
    return 0;
  }

  public ChangeType getChangeType() {
    return changeType;
  }

  public PatchType getPatchType() {
    return patchType;
  }

  public String getOldName() {
    return oldName;
  }

  public String getNewName() {
    return newName;
  }

  public List<Edit> getEdits() {
    return edits;
  }

  public int getInsertions() {
    return insertions;
  }

  public int getDeletions() {
    return deletions;
  }

  public long getSize() {
    return size;
  }

  public long getSizeDelta() {
    return sizeDelta;
  }

  public List<String> getHeaderLines() {
    final IntList m = RawParseUtils.lineMap(header, 0, header.length);
    final List<String> headerLines = new ArrayList<>(m.size() - 1);
    for (int i = 1; i < m.size() - 1; i++) {
      final int b = m.get(i);
      int e = m.get(i + 1);
      if (header[e - 1] == '\n') {
        e--;
      }
      headerLines.add(RawParseUtils.decode(Constants.CHARSET, header, b, e));
    }
    return headerLines;
  }

  Patch toPatch(final PatchSet.Id setId) {
    final Patch p = new Patch(new Patch.Key(setId, getNewName()));
    p.setChangeType(getChangeType());
    p.setPatchType(getPatchType());
    p.setSourceFileName(getOldName());
    p.setInsertions(insertions);
    p.setDeletions(deletions);
    return p;
  }

  void writeTo(OutputStream out) throws IOException {
    writeEnum(out, changeType);
    writeEnum(out, patchType);
    writeString(out, oldName);
    writeString(out, newName);
    writeBytes(out, header);
    writeVarInt32(out, insertions);
    writeVarInt32(out, deletions);
    writeFixInt64(out, size);
    writeFixInt64(out, sizeDelta);

    writeVarInt32(out, edits.size());
    for (final Edit e : edits) {
      writeVarInt32(out, e.getBeginA());
      writeVarInt32(out, e.getEndA());
      writeVarInt32(out, e.getBeginB());
      writeVarInt32(out, e.getEndB());
    }
  }

  static PatchListEntry readFrom(InputStream in) throws IOException {
    ChangeType changeType = readEnum(in, ChangeType.values());
    PatchType patchType = readEnum(in, PatchType.values());
    String oldName = readString(in);
    String newName = readString(in);
    byte[] hdr = readBytes(in);
    int ins = readVarInt32(in);
    int del = readVarInt32(in);
    long size = readFixInt64(in);
    long sizeDelta = readFixInt64(in);

    int editCount = readVarInt32(in);
    Edit[] editArray = new Edit[editCount];
    for (int i = 0; i < editCount; i++) {
      int beginA = readVarInt32(in);
      int endA = readVarInt32(in);
      int beginB = readVarInt32(in);
      int endB = readVarInt32(in);
      editArray[i] = new Edit(beginA, endA, beginB, endB);
    }

    return new PatchListEntry(
        changeType, patchType, oldName, newName, hdr, toList(editArray), ins, del, size, sizeDelta);
  }

  private static List<Edit> toList(Edit[] l) {
    return Collections.unmodifiableList(Arrays.asList(l));
  }

  private static byte[] compact(final FileHeader h) {
    final int end = end(h);
    if (h.getStartOffset() == 0 && end == h.getBuffer().length) {
      return h.getBuffer();
    }

    final byte[] buf = new byte[end - h.getStartOffset()];
    System.arraycopy(h.getBuffer(), h.getStartOffset(), buf, 0, buf.length);
    return buf;
  }

  private static int end(final FileHeader h) {
    if (h instanceof CombinedFileHeader) {
      return h.getEndOffset();
    }
    if (!h.getHunks().isEmpty()) {
      return h.getHunks().get(0).getStartOffset();
    }
    return h.getEndOffset();
  }

  private static ChangeType toChangeType(final FileHeader hdr) {
    switch (hdr.getChangeType()) {
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
        throw new IllegalArgumentException("Unsupported type " + hdr.getChangeType());
    }
  }

  private static PatchType toPatchType(final FileHeader hdr) {
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
