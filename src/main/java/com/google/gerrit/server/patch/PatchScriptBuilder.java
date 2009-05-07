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

import com.google.gerrit.client.data.PatchScript;
import com.google.gerrit.client.data.SparseFileContent;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.rpc.CorruptEntityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.diff.Edit;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectLoader;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.patch.CombinedFileHeader;
import org.spearce.jgit.patch.FileHeader;
import org.spearce.jgit.util.IntList;
import org.spearce.jgit.util.RawParseUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class PatchScriptBuilder {
  static final int MAX_CONTEXT = 5000000;
  static final int BIG_FILE = 9000;

  private static final Logger log =
      LoggerFactory.getLogger(PatchScriptBuilder.class);

  private final List<String> header;
  private final SparseFileContent dstA;
  private final SparseFileContent dstB;
  private Repository db;
  private Patch patch;
  private Patch.Key patchKey;
  private int context;

  private Text srcA;
  private Text srcB;
  private List<Edit> edits;

  PatchScriptBuilder() {
    header = new ArrayList<String>();
    dstA = new SparseFileContent();
    dstB = new SparseFileContent();
  }

  void setRepository(final Repository r) {
    db = r;
  }

  void setPatch(final Patch p) {
    patch = p;
    patchKey = patch.getKey();
  }

  void setContext(final int c) {
    context = c;
  }

  PatchScript toPatchScript(final DiffCacheContent content)
      throws CorruptEntityException {
    final FileHeader fh = content.getFileHeader();
    if (fh instanceof CombinedFileHeader) {
      // For a diff --cc format we don't support converting it into
      // a patch script. Instead treat everything as a file header.
      //
      edits = Collections.emptyList();
      packHeader(fh);
      return new PatchScript(header, context, dstA, dstB, edits);
    }

    srcA = open(content.getOldId());
    if (eq(content.getOldId(), content.getNewId())) {
      srcB = srcA;
    } else {
      srcB = open(content.getNewId());
    }
    edits = content.getEdits();

    dstA.setMissingNewlineAtEnd(srcA.isMissingNewlineAtEnd());
    dstB.setMissingNewlineAtEnd(srcA.isMissingNewlineAtEnd());

    dstA.setSize(srcA.size());
    dstB.setSize(srcB.size());

    packHeader(fh);
    if (srcA == srcB && srcA.size() <= context && edits.isEmpty()) {
      // Odd special case; the files are identical (100% rename or copy)
      // and the user has asked for context that is larger than the file.
      // Send them the entire file, with an empty edit after the last line.
      //
      for (int i = 0; i < srcA.size(); i++) {
        srcA.addLineTo(dstA, i);
      }
      edits = Collections.singletonList(new Edit(srcA.size(), srcA.size()));
    } else {
      if (BIG_FILE < Math.max(srcA.size(), srcB.size()) && 25 < context) {
        context = 25;
      }
      packContent();
    }
    return new PatchScript(header, context, dstA, dstB, edits);
  }

  private static boolean eq(final ObjectId a, final ObjectId b) {
    if (a == null && b == null) {
      return true;
    }
    return a != null && b != null ? a.equals(b) : false;
  }

  private Text open(final ObjectId id) throws CorruptEntityException {
    if (id == null) {
      return Text.EMPTY;
    }
    try {
      final ObjectLoader ldr = db.openObject(id);
      if (ldr == null) {
        throw new MissingObjectException(id, Constants.TYPE_BLOB);
      }
      return new Text(ldr.getCachedBytes());
    } catch (IOException e) {
      log.error("In " + patchKey + " blob " + id.name() + " gone", e);
      throw new CorruptEntityException(patchKey);
    }
  }

  private void packHeader(final FileHeader fh) {
    final byte[] buf = fh.getBuffer();
    final IntList m = RawParseUtils.lineMap(buf, fh.getStartOffset(), end(fh));
    for (int i = 1; i < m.size() - 1; i++) {
      final int b = m.get(i);
      final int e = m.get(i + 1);
      header.add(RawParseUtils.decode(Constants.CHARSET, buf, b, e));
    }
  }

  private void packContent() {
    for (int curIdx = 0; curIdx < edits.size();) {
      Edit curEdit = edits.get(curIdx);
      final int endIdx = findCombinedEnd(edits, curIdx);
      final Edit endEdit = edits.get(endIdx);

      int aCur = Math.max(0, curEdit.getBeginA() - context);
      int bCur = Math.max(0, curEdit.getBeginB() - context);
      final int aEnd = Math.min(srcA.size(), endEdit.getEndA() + context);
      final int bEnd = Math.min(srcB.size(), endEdit.getEndB() + context);

      while (aCur < aEnd || bCur < bEnd) {
        if (aCur < curEdit.getBeginA() || endIdx + 1 < curIdx) {
          srcA.addLineTo(dstA, aCur);
          aCur++;
          bCur++;

        } else if (aCur < curEdit.getEndA()) {
          srcA.addLineTo(dstA, aCur++);

        } else if (bCur < curEdit.getEndB()) {
          srcB.addLineTo(dstB, bCur++);
        }

        if (end(curEdit, aCur, bCur) && ++curIdx < edits.size())
          curEdit = edits.get(curIdx);
      }
    }
  }

  private int findCombinedEnd(final List<Edit> edits, final int i) {
    int end = i + 1;
    while (end < edits.size() && (combineA(edits, end) || combineB(edits, end)))
      end++;
    return end - 1;
  }

  private boolean combineA(final List<Edit> e, final int i) {
    return e.get(i).getBeginA() - e.get(i - 1).getEndA() <= 2 * context;
  }

  private boolean combineB(final List<Edit> e, final int i) {
    return e.get(i).getBeginB() - e.get(i - 1).getEndB() <= 2 * context;
  }

  private static boolean end(final Edit edit, final int a, final int b) {
    return edit.getEndA() <= a && edit.getEndB() <= b;
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
}
