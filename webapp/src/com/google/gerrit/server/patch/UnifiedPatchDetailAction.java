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

import com.google.gerrit.client.data.PatchLine;
import com.google.gerrit.client.data.UnifiedPatchDetail;
import com.google.gerrit.client.data.PatchLine.Type;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Failure;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.patch.HunkHeader;

import java.util.ArrayList;

class UnifiedPatchDetailAction extends PatchDetailAction<UnifiedPatchDetail> {
  UnifiedPatchDetailAction(final Patch.Id key) {
    super(key);
  }

  public UnifiedPatchDetail run(final ReviewDb db) throws OrmException, Failure {
    init(db);

    final byte[] buf = file.getBuffer();
    int ptr = file.getStartOffset();
    final int end = file.getEndOffset();
    final int hdrEnd;
    final ArrayList<PatchLine> lines = new ArrayList<PatchLine>();

    if (file.getHunks().size() > 0) {
      hdrEnd = file.getHunks().get(0).getStartOffset();
    } else if (file.getForwardBinaryHunk() != null) {
      hdrEnd = file.getForwardBinaryHunk().getStartOffset();
    } else if (file.getReverseBinaryHunk() != null) {
      hdrEnd = file.getReverseBinaryHunk().getStartOffset();
    } else {
      hdrEnd = end;
    }

    int eol;
    for (; ptr < hdrEnd; ptr = eol) {
      eol = nextLF(buf, ptr);
      lines.add(new PatchLine(0, 0, Type.FILE_HEADER, decode(Constants.CHARSET,
          buf, ptr, eol)));
    }

    for (final HunkHeader h : file.getHunks()) {
      final int hunkEnd = h.getEndOffset();
      if (ptr < hunkEnd) {
        eol = nextLF(buf, ptr);
        lines.add(new PatchLine(h.getOldImage().getStartLine(), h
            .getNewStartLine(), Type.HUNK_HEADER, decode(Constants.CHARSET,
            buf, ptr, eol)));
        ptr = eol;
      }

      int oldLine = h.getOldImage().getStartLine() - 1;
      int newLine = h.getNewStartLine() - 1;
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
        addComments(pLine, published, 0, oldLine);
        addComments(pLine, published, 1, newLine);
        if (drafted != null) {
          addComments(pLine, drafted, 0, oldLine);
          addComments(pLine, drafted, 1, newLine);
        }
        lines.add(pLine);
      }
    }

    final UnifiedPatchDetail d;
    d = new UnifiedPatchDetail(patch, accountInfo.create());
    d.setLines(lines);
    return d;
  }
}
