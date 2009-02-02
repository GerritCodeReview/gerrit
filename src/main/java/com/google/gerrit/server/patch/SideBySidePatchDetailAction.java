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

import com.google.gerrit.client.data.SideBySideLine;
import com.google.gerrit.client.data.SideBySidePatchDetail;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.CorruptEntityException;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Failure;
import com.google.gerrit.git.RepositoryCache;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.AbbreviatedObjectId;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.patch.CombinedFileHeader;
import org.spearce.jgit.patch.CombinedHunkHeader;
import org.spearce.jgit.patch.HunkHeader;
import org.spearce.jgit.util.IntList;
import org.spearce.jgit.util.RawParseUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class SideBySidePatchDetailAction extends
    PatchDetailAction<SideBySidePatchDetail> {
  private int fileCount;
  private byte[][] fileContents;
  private IntList[] lineIndex;

  SideBySidePatchDetailAction(final RepositoryCache rc, final Patch.Key key,
      final List<PatchSet.Id> fileVersions) {
    super(rc, key, fileVersions);
  }

  public SideBySidePatchDetail run(final ReviewDb db) throws OrmException,
      Failure {
    init(db);

    if (file.getHunks().isEmpty()) {
      throw new Failure(new CorruptEntityException(patchKey));
    }

    openContents();

    final ArrayList<List<SideBySideLine>> lines =
        new ArrayList<List<SideBySideLine>>();
    if (file instanceof CombinedFileHeader) {
      for (final CombinedHunkHeader h : ((CombinedFileHeader) file).getHunks()) {
      }

    } else {
      for (final HunkHeader h : file.getHunks()) {
        int oldLine = h.getOldImage().getStartLine();
        int newLine = h.getNewStartLine();

        final byte[] buf = h.getBuffer();
        final int hunkEnd = h.getEndOffset();
        int ptr = h.getStartOffset();
        int eol = nextLF(buf, ptr);
        SCAN: for (ptr = eol; ptr < hunkEnd; ptr = eol) {
          eol = nextLF(buf, ptr);

          final SideBySideLine o, n;
          switch (buf[ptr]) {
            case ' ':
            case '\n': {
              final String text = decode(Constants.CHARSET, buf, ptr + 1, eol);

              o = new SideBySideLine(oldLine, SideBySideLine.Type.EQUAL, text);
              n = new SideBySideLine(newLine, SideBySideLine.Type.EQUAL, text);
              oldLine++;
              newLine++;
              break;
            }
            case '-': {
              final String text = decode(Constants.CHARSET, buf, ptr + 1, eol);
              o = new SideBySideLine(oldLine, SideBySideLine.Type.DELETE, text);
              n = null;
              oldLine++;
              break;
            }
            case '+': {
              final String text = decode(Constants.CHARSET, buf, ptr + 1, eol);
              o = null;
              n = new SideBySideLine(newLine, SideBySideLine.Type.INSERT, text);
              newLine++;

              // Attempt to insert this line backwards where it matches as a
              // replacement for a prior deletion. Typically the delete is
              // presented first in the patch, then the addition, so we only
              // need to backtrack here.
              //
              int p = lines.size();
              while (0 < p && lines.get(p - 1).get(1) == null) {
                p--;
              }
              if (0 < p && p < lines.size() && lines.get(p).get(1) == null) {
                lines.get(p).set(1, n);
                continue;
              }
              break;
            }
            case '\\':
              continue;
            default:
              break SCAN;
          }
          lines.add(Arrays.asList(new SideBySideLine[] {o, n}));
        }
      }
    }

    for (final List<SideBySideLine> p : lines) {
      for (int i = 0; i < fileCount; i++) {
        final SideBySideLine line = p.get(i);
        if (line != null) {
          addComments(line, published, i, line.getLineNumber());
          if (drafted != null) {
            addComments(line, drafted, i, line.getLineNumber());
          }
        }
      }
    }

    int maxLine = 0;
    for (int i = 0; i < fileCount; i++) {
      maxLine = Math.max(maxLine, lineIndex[i].size());
    }

    final SideBySidePatchDetail d;
    d = new SideBySidePatchDetail(patch, accountInfo.create());
    d.setLines(fileCount, maxLine, lines);
    d.setHistory(history(db));
    return d;
  }

  private void openContents() throws Failure {
    final Repository repo = openRepository();

    if (file instanceof CombinedFileHeader) {
      final CombinedFileHeader ch = (CombinedFileHeader) file;

      fileCount = ch.getParentCount() + 1;
      fileContents = new byte[fileCount][];
      for (int i = 0; i < ch.getParentCount(); i++) {
        final AbbreviatedObjectId old = ch.getOldId(i);
        if (old == null || !old.isComplete()) {
          throw new Failure(new CorruptEntityException(patchKey));
        }
        fileContents[i] = read(repo, old.toObjectId());
      }

    } else {
      if (file.getOldId() == null || !file.getOldId().isComplete()) {
        throw new Failure(new CorruptEntityException(patchKey));
      }

      fileCount = 2;
      fileContents = new byte[fileCount][];
      fileContents[0] = read(repo, file.getOldId().toObjectId());
    }

    if (file.getNewId() == null || !file.getNewId().isComplete()) {
      throw new Failure(new CorruptEntityException(patchKey));
    }
    fileContents[fileCount - 1] = read(repo, file.getNewId().toObjectId());

    lineIndex = new IntList[fileCount];
    for (int i = 0; i < fileCount; i++) {
      final byte[] c = fileContents[i];
      lineIndex[i] = RawParseUtils.lineMap(c, 0, c.length);
    }
  }
}
