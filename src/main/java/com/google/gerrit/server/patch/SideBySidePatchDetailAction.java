// Copyright (C) 2008 The Android Open Source Project
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

import static com.google.gerrit.client.reviewdb.Account.WHOLE_FILE_CONTEXT;
import static org.spearce.jgit.util.RawParseUtils.decode;
import static org.spearce.jgit.util.RawParseUtils.nextLF;

import com.google.gerrit.client.data.SideBySideLine;
import com.google.gerrit.client.data.SideBySidePatchDetail;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.CorruptEntityException;
import com.google.gerrit.client.rpc.NoDifferencesException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Failure;
import com.google.gerrit.git.RepositoryCache;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.patch.CombinedFileHeader;
import org.spearce.jgit.patch.CombinedHunkHeader;
import org.spearce.jgit.patch.FileHeader;
import org.spearce.jgit.patch.HunkHeader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class SideBySidePatchDetailAction extends
    PatchDetailAction<SideBySidePatchDetail> {
  SideBySidePatchDetailAction(final RepositoryCache rc, final Patch.Key key,
      final List<PatchSet.Id> fileVersions) {
    super(rc, key, fileVersions);
  }

  public SideBySidePatchDetail run(final ReviewDb db) throws OrmException,
      Failure {
    init(db);

    final FileHeader fh;
    final int fileCount;
    int maxLine = 0;
    try {
      fh = file.getFileHeader();
      if (fh.getHunks().isEmpty()) {
        throw new Failure(new CorruptEntityException(patchKey));
      }
      fileCount = file.getFileCount();
      for (int i = 0; i < fileCount; i++) {
        maxLine = Math.max(maxLine, file.getLineCount(i));
      }
    } catch (CorruptEntityException e) {
      throw new Failure(e);
    } catch (NoSuchEntityException e) {
      throw new Failure(e);
    } catch (IOException e) {
      throw new Failure(e);
    } catch (NoDifferencesException e) {
      throw new Failure(e);
    }

    final ArrayList<List<SideBySideLine>> lines =
        new ArrayList<List<SideBySideLine>>();
    if (fh instanceof CombinedFileHeader) {
      for (final CombinedHunkHeader h : ((CombinedFileHeader) fh).getHunks()) {
      }

    } else {
      final int wantContext = getContextSetting();
      for (final HunkHeader h : fh.getHunks()) {
        int oldLine = h.getOldImage().getStartLine();
        int newLine = h.getNewStartLine();

        final int leadingContext = leadingContext(h);
        final byte[] buf = h.getBuffer();
        final int hunkEnd = h.getEndOffset();
        int ptr = h.getStartOffset();
        int eol = nextLF(buf, ptr);

        if (wantContext == WHOLE_FILE_CONTEXT) {
          // Cover any gaps between the last line we added and the next line.
          //
          expandContext(lines, oldLine, newLine, Integer.MAX_VALUE);

        } else if (leadingContext < wantContext) {
          // Not enough context is inherit in the patch. Expand it out.
          //
          expandContext(lines, oldLine, newLine, wantContext - leadingContext);

        } else if (leadingContext > wantContext) {
          // Actually, we have too much context in this hunk.
          //
          int extra = leadingContext - wantContext;
          SCAN: for (ptr = eol; ptr < hunkEnd && 0 < extra; ptr = eol) {
            eol = nextLF(buf, ptr);
            switch (buf[ptr]) {
              case ' ':
              case '\n':
                extra--;
                oldLine++;
                newLine++;
                continue;
              default:
                break SCAN;
            }
          }
        }

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

        final int trailingContext = trailingContext(lines);
        if (wantContext == WHOLE_FILE_CONTEXT) {
          // Don't do anything here.  The next hunk will copy in whatever it
          // requires, or we'll handle it below if this is the last hunk.
          //
        } else if (trailingContext < wantContext) {
          // We don't have enough context.  Add more.
          //
          final int extra = wantContext - trailingContext;
          expandContext(lines, oldLine + extra, newLine + extra, extra);

        } else if (trailingContext > wantContext) {
          // We copied in too many lines of context.  Drop them off the tail
          // end of the collection.
          //
          int toRemove = trailingContext - wantContext;
          while (toRemove-- > 0) {
            lines.remove(lines.size() - 1);
          }
        }
      }

      if (wantContext == WHOLE_FILE_CONTEXT) {
        // User wants the full file, expand the context to the end.
        //
        expandContext(lines, maxLine + 1, maxLine + 1, Integer.MAX_VALUE);
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

    final SideBySidePatchDetail d;
    d = new SideBySidePatchDetail(patch, accountInfo.create());
    d.setLines(fileCount, maxLine, lines);
    d.setHistory(history(db));
    return d;
  }

  private void expandContext(final List<List<SideBySideLine>> lines,
      final int oldLine, final int newLine, final int want) throws Failure,
      OrmException {
    try {
      int lastOld = lastLine(lines, 0);
      int lastNew = lastLine(lines, 1);

      if (want < oldLine - lastOld && want < newLine - lastNew) {
        final int skip = (oldLine - lastOld) - want;
        lastOld += skip;
        lastNew += skip;
      }

      final int maxOld = file.getLineCount(0);
      final int maxNew = file.getLineCount(1);

      while (lastOld < oldLine || lastNew < newLine) {
        final SideBySideLine o, n;

        if (lastOld <= maxOld) {
          final String text = file.getLine(0, lastOld);
          o = new SideBySideLine(lastOld, SideBySideLine.Type.EQUAL, text);
        } else {
          o = null;
        }

        if (lastNew <= maxNew) {
          final String text;
          if (o != null) {
            text = o.getText();
          } else {
            text = file.getLine(1, lastNew);
          }
          n = new SideBySideLine(lastNew, SideBySideLine.Type.EQUAL, text);
        } else {
          n = null;
        }

        if (o == null && n == null) {
          break;
        }

        lines.add(Arrays.asList(new SideBySideLine[] {o, n}));
        lastOld++;
        lastNew++;
      }
    } catch (CorruptEntityException e) {
      throw new Failure(e);
    } catch (IOException e) {
      throw new Failure(e);
    } catch (NoSuchEntityException e) {
      throw new Failure(e);
    } catch (NoDifferencesException e) {
      throw new Failure(e);
    }
  }

  private static int lastLine(final List<List<SideBySideLine>> lines,
      final int side) {
    int p = lines.size() - 1;
    while (0 <= p && lines.get(p).get(side) == null) {
      p--;
    }
    return 0 <= p ? lines.get(p).get(side).getLineNumber() : 1;
  }

  private static int leadingContext(final HunkHeader h) {
    final byte[] buf = h.getBuffer();
    final int hunkEnd = h.getEndOffset();
    int ptr = h.getStartOffset();
    int eol = nextLF(buf, ptr);
    int context = 0;
    SCAN: for (ptr = eol; ptr < hunkEnd; ptr = eol) {
      eol = nextLF(buf, ptr);
      switch (buf[ptr]) {
        case ' ':
        case '\n':
          context++;
          continue;
        default:
          break SCAN;
      }
    }
    return context;
  }

  private static int trailingContext(final List<List<SideBySideLine>> lines) {
    int p = lines.size() - 1;
    int context = 0;
    SCAN: while (0 <= p) {
      for (final SideBySideLine s : lines.get(p--)) {
        if (s != null) {
          switch (s.getType()) {
            case EQUAL:
              context++;
              continue SCAN;
            default:
              break SCAN;
          }
        }
      }
    }
    return context;
  }
}
