// Copyright 2008 Google Inc.
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

import static org.spearce.jgit.util.RawParseUtils.decode;
import static org.spearce.jgit.util.RawParseUtils.nextLF;

import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.data.PatchLine;
import com.google.gerrit.client.data.UnifiedPatchDetail;
import com.google.gerrit.client.data.PatchLine.Type;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.RpcUtil;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Failure;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.patch.CombinedFileHeader;
import org.spearce.jgit.patch.FileHeader;
import org.spearce.jgit.patch.HunkHeader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class UnifiedPatchDetailAction extends PatchDetailAction<UnifiedPatchDetail> {
  UnifiedPatchDetailAction(final Patch.Id key) {
    super(key);
  }

  public UnifiedPatchDetail run(final ReviewDb db) throws OrmException, Failure {
    final Patch patch = db.patches().get(key);
    if (patch == null) {
      throw new Failure(new NoSuchEntityException());
    }

    final FileHeader fh = parse(patch, read(db, patch));
    final int fileCnt;
    if (fh instanceof CombinedFileHeader) {
      fileCnt = ((CombinedFileHeader) fh).getParentCount() + 1;
    } else {
      fileCnt = 2;
    }

    final AccountInfoCacheFactory acc = new AccountInfoCacheFactory(db);
    final HashMap<Integer, List<PatchLineComment>> published[];
    published = new HashMap[fileCnt];
    for (int n = 0; n < fileCnt; n++) {
      published[n] = new HashMap<Integer, List<PatchLineComment>>();
    }
    indexComments(published, db.patchComments().published(key));

    final Account.Id me = RpcUtil.getAccountId();
    final HashMap<Integer, List<PatchLineComment>> drafted[];
    if (me != null) {
      drafted = new HashMap[fileCnt];
      for (int n = 0; n < fileCnt; n++) {
        drafted[n] = new HashMap<Integer, List<PatchLineComment>>();
      }
      indexComments(drafted, db.patchComments().draft(key, me));
    } else {
      drafted = null;
    }

    final byte[] buf = fh.getBuffer();
    int ptr = fh.getStartOffset();
    final int end = fh.getEndOffset();
    final int hdrEnd;
    final ArrayList<PatchLine> lines = new ArrayList<PatchLine>();

    if (fh.getHunks().size() > 0) {
      hdrEnd = fh.getHunks().get(0).getStartOffset();
    } else if (fh.getForwardBinaryHunk() != null) {
      hdrEnd = fh.getForwardBinaryHunk().getStartOffset();
    } else if (fh.getReverseBinaryHunk() != null) {
      hdrEnd = fh.getReverseBinaryHunk().getStartOffset();
    } else {
      hdrEnd = end;
    }

    int eol;
    for (; ptr < hdrEnd; ptr = eol) {
      eol = nextLF(buf, ptr);
      lines.add(new PatchLine(0, 0, Type.FILE_HEADER, decode(Constants.CHARSET,
          buf, ptr, eol)));
    }

    for (final HunkHeader h : fh.getHunks()) {
      final int hunkEnd = h.getEndOffset();
      if (ptr < hunkEnd) {
        eol = nextLF(buf, ptr);
        lines.add(new PatchLine(h.getOldImage().getStartLine(), h
            .getNewStartLine(), Type.HUNK_HEADER, decode(Constants.CHARSET,
            buf, ptr, eol)));
        ptr = eol;
      }
      int oldLine = h.getOldImage().getStartLine();
      int newLine = h.getNewStartLine();
      SCAN: for (; ptr < hunkEnd; ptr = eol) {
        eol = nextLF(buf, ptr);
        final PatchLine.Type type;
        switch (buf[ptr]) {
          case ' ':
          case '\n':
            oldLine++;
            newLine++;
            type = Type.CONTEXT;
            break;
          case '-':
            oldLine++;
            type = Type.PRE_IMAGE;
            break;
          case '+':
            newLine++;
            type = Type.POST_IMAGE;
            break;
          case '\\':
            type = Type.CONTEXT;
            break;
          default:
            break SCAN;
        }

        final PatchLine pLine =
            new PatchLine(oldLine, newLine, type, decode(Constants.CHARSET,
                buf, ptr, eol));
        addComments(acc, pLine, published, 0, oldLine);
        addComments(acc, pLine, published, 1, newLine);
        if (drafted != null) {
          addComments(acc, pLine, drafted, 0, oldLine);
          addComments(acc, pLine, drafted, 1, newLine);
        }
        lines.add(pLine);
      }
    }

    final UnifiedPatchDetail d;
    d = new UnifiedPatchDetail(patch, acc.create());
    d.setLines(lines);
    return d;
  }
}
