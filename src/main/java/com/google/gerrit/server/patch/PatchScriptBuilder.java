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
import com.google.gerrit.client.data.PatchScriptSettings;
import com.google.gerrit.client.data.SparseFileContent;
import com.google.gerrit.client.patches.CommentDetail;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
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
import java.util.Comparator;
import java.util.List;

class PatchScriptBuilder {
  static final int MAX_CONTEXT = 5000000;
  static final int BIG_FILE = 9000;

  private static final Logger log =
      LoggerFactory.getLogger(PatchScriptBuilder.class);

  private static final Comparator<Edit> EDIT_SORT = new Comparator<Edit>() {
    @Override
    public int compare(final Edit o1, final Edit o2) {
      return o1.getBeginA() - o2.getBeginA();
    }
  };

  private final List<String> header;
  private final SparseFileContent dstA;
  private final SparseFileContent dstB;
  private Repository db;
  private Patch patch;
  private Patch.Key patchKey;
  private PatchScriptSettings settings;

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

  void setSettings(final PatchScriptSettings s) {
    settings = s;
  }

  private int context() {
    return settings.getContext();
  }

  PatchScript toPatchScript(final DiffCacheContent contentWS,
      final CommentDetail comments, final DiffCacheContent contentAct)
      throws CorruptEntityException {
    final FileHeader fh = contentAct.getFileHeader();
    if (fh instanceof CombinedFileHeader) {
      // For a diff --cc format we don't support converting it into
      // a patch script. Instead treat everything as a file header.
      //
      edits = Collections.emptyList();
      packHeader(fh);
      return new PatchScript(header, settings, dstA, dstB, edits);
    }

    srcA = open(contentAct.getOldId());
    if (eq(contentAct.getOldId(), contentAct.getNewId())) {
      srcB = srcA;
    } else {
      srcB = open(contentAct.getNewId());
    }
    edits = contentAct.getEdits();
    ensureCommentsVisible(comments);

    dstA.setMissingNewlineAtEnd(srcA.isMissingNewlineAtEnd());
    dstB.setMissingNewlineAtEnd(srcA.isMissingNewlineAtEnd());

    dstA.setSize(srcA.size());
    dstB.setSize(srcB.size());

    if (fh != null) {
      packHeader(fh);
    }
    if (srcA == srcB && srcA.size() <= context()
        && contentAct.getEdits().isEmpty()) {
      // Odd special case; the files are identical (100% rename or copy)
      // and the user has asked for context that is larger than the file.
      // Send them the entire file, with an empty edit after the last line.
      //
      for (int i = 0; i < srcA.size(); i++) {
        srcA.addLineTo(dstA, i);
      }
      edits = Collections.singletonList(new Edit(srcA.size(), srcA.size()));
    } else {
      if (BIG_FILE < Math.max(srcA.size(), srcB.size()) && 25 < context()) {
        settings.setContext(25);
      }
      packContent();
    }

    if (contentWS != contentAct) {
      // The edit list we used to pack the file contents doesn't honor the
      // whitespace settings requested. Instead we must rebuild our edit
      // list around the whitespace edit list.
      //
      edits = contentWS.getEdits();
      ensureCommentsVisible(comments);
    }

    return new PatchScript(header, settings, dstA, dstB, edits);
  }

  private void ensureCommentsVisible(final CommentDetail comments) {
    if (comments.getCommentsA().isEmpty() && comments.getCommentsB().isEmpty()) {
      // No comments, no additional dummy edits are required.
      //
      return;
    }

    // Construct empty Edit blocks around each location where a comment is.
    // This will force the later packContent method to include the regions
    // containing comments, potentially combining those regions together if
    // they have overlapping contexts. UI renders will also be able to make
    // correct hunks from this, but because the Edit is empty they will not
    // style it specially.
    //
    final List<Edit> empty = new ArrayList<Edit>();
    int lastLine;

    lastLine = -1;
    for (PatchLineComment plc : comments.getCommentsA()) {
      final int a = plc.getLine();
      if (lastLine != a) {
        safeAdd(empty, new Edit(a - 1, mapA2B(a - 1)));
        lastLine = a;
      }
    }

    lastLine = -1;
    for (PatchLineComment plc : comments.getCommentsB()) {
      final int b = plc.getLine();
      if (lastLine != b) {
        safeAdd(empty, new Edit(mapB2A(b - 1), b - 1));
        lastLine = b;
      }
    }

    // Build the final list as a copy, as we cannot modify the cached
    // edit list we started out with. Also sort the final list by the
    // index in A, so packContent can combine them correctly later.
    //
    final List<Edit> n = new ArrayList<Edit>(edits.size() + empty.size());
    n.addAll(edits);
    n.addAll(empty);
    Collections.sort(n, EDIT_SORT);
    edits = n;
  }

  private void safeAdd(final List<Edit> empty, final Edit toAdd) {
    final int a = toAdd.getBeginA();
    final int b = toAdd.getBeginB();
    for (final Edit e : edits) {
      if (e.getBeginA() <= a && a <= e.getEndA()) {
        return;
      }
      if (e.getBeginB() <= b && b <= e.getEndB()) {
        return;
      }
    }
    empty.add(toAdd);
  }

  private int mapA2B(final int a) {
    if (edits.isEmpty()) {
      // Magic special case of an unmodified file.
      //
      return a;
    }

    if (a < edits.get(0).getBeginA()) {
      // Special case of context at start of file.
      //
      return a;
    }

    for (Edit e : edits) {
      if (e.getBeginA() <= a && a <= e.getEndA()) {
        return e.getBeginB() + (a - e.getBeginA());
      }
    }

    final Edit last = edits.get(edits.size() - 1);
    return last.getBeginB() + (a - last.getEndA());
  }

  private int mapB2A(final int b) {
    if (edits.isEmpty()) {
      // Magic special case of an unmodified file.
      //
      return b;
    }

    if (b < edits.get(0).getBeginB()) {
      // Special case of context at start of file.
      //
      return b;
    }

    for (Edit e : edits) {
      if (e.getBeginB() <= b && b <= e.getEndB()) {
        return e.getBeginA() + (b - e.getBeginB());
      }
    }

    final Edit last = edits.get(edits.size() - 1);
    return last.getBeginA() + (b - last.getEndB());
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

      int aCur = Math.max(0, curEdit.getBeginA() - context());
      int bCur = Math.max(0, curEdit.getBeginB() - context());
      final int aEnd = Math.min(srcA.size(), endEdit.getEndA() + context());
      final int bEnd = Math.min(srcB.size(), endEdit.getEndB() + context());

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
    return e.get(i).getBeginA() - e.get(i - 1).getEndA() <= 2 * context();
  }

  private boolean combineB(final List<Edit> e, final int i) {
    return e.get(i).getBeginB() - e.get(i - 1).getEndB() <= 2 * context();
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
