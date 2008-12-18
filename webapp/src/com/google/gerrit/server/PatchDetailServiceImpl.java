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

package com.google.gerrit.server;

import static org.spearce.jgit.util.RawParseUtils.decode;
import static org.spearce.jgit.util.RawParseUtils.nextLF;

import com.google.gerrit.client.data.PatchLine;
import com.google.gerrit.client.data.UnifiedPatchDetail;
import com.google.gerrit.client.data.PatchLine.Type;
import com.google.gerrit.client.patches.PatchDetailService;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchContent;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.CorruptEntityException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.patch.FileHeader;
import org.spearce.jgit.patch.FormatError;
import org.spearce.jgit.patch.HunkHeader;

import java.util.ArrayList;

public class PatchDetailServiceImpl extends BaseServiceImplementation implements
    PatchDetailService {
  public PatchDetailServiceImpl(final SchemaFactory<ReviewDb> rdf) {
    super(rdf);
  }

  public void unifiedPatchDetail(final Patch.Id key,
      final AsyncCallback<UnifiedPatchDetail> callback) {
    run(callback, new Action<UnifiedPatchDetail>() {
      public UnifiedPatchDetail run(final ReviewDb db) throws OrmException,
          Failure {
        final Patch patch = db.patches().get(key);
        if (patch == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final FileHeader fh = parse(patch, read(db, patch));
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
          lines.add(new PatchLine(0, 0, Type.FILE_HEADER, decode(
              Constants.CHARSET, buf, ptr, eol)));
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
            lines.add(new PatchLine(oldLine, newLine, type, decode(
                Constants.CHARSET, buf, ptr, eol)));
          }
        }

        final UnifiedPatchDetail d = new UnifiedPatchDetail(patch);
        d.setLines(lines);
        return d;
      }
    });
  }

  private static String read(final ReviewDb db, final Patch patch)
      throws Failure, OrmException {
    final PatchContent.Key key = patch.getContent();
    if (key == null) {
      throw new Failure(new CorruptEntityException(patch.getKey()));
    }

    final PatchContent pc = db.patchContents().get(key);
    if (pc == null || pc.getContent() == null) {
      throw new Failure(new CorruptEntityException(patch.getKey()));
    }

    return pc.getContent();
  }

  private static FileHeader parse(final Patch patch, final String content)
      throws Failure {
    final byte[] buf = Constants.encode(content);
    final org.spearce.jgit.patch.Patch p = new org.spearce.jgit.patch.Patch();
    p.parse(buf, 0, buf.length);
    for (final FormatError err : p.getErrors()) {
      if (err.getSeverity() == FormatError.Severity.ERROR) {
        throw new Failure(new CorruptEntityException(patch.getKey()));
      }
    }
    if (p.getFiles().size() != 1) {
      throw new Failure(new CorruptEntityException(patch.getKey()));
    }
    return p.getFiles().get(0);
  }
}
