// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.diff;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.common.data.PatchScript.PatchScriptFileInfo;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffInfo.ContentEntry;
import com.google.gerrit.extensions.common.DiffInfo.FileMeta;
import com.google.gerrit.extensions.common.DiffInfo.IntraLineStatus;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.project.ProjectState;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.diff.Edit;

public class DiffCalculator {

  private static final ImmutableMap<Patch.ChangeType, ChangeType> CHANGE_TYPE =
      Maps.immutableEnumMap(
          new ImmutableMap.Builder<Patch.ChangeType, ChangeType>()
              .put(Patch.ChangeType.ADDED, ChangeType.ADDED)
              .put(Patch.ChangeType.MODIFIED, ChangeType.MODIFIED)
              .put(Patch.ChangeType.DELETED, ChangeType.DELETED)
              .put(Patch.ChangeType.RENAMED, ChangeType.RENAMED)
              .put(Patch.ChangeType.COPIED, ChangeType.COPIED)
              .put(Patch.ChangeType.REWRITE, ChangeType.REWRITE)
              .build());

  private final DiffWebLinksProvider webLinksProvider;
  private final boolean intraline;
  ProjectState state;

  public DiffCalculator(
      ProjectState state, DiffWebLinksProvider webLinksProvider, boolean intraline) {
    this.webLinksProvider = webLinksProvider;
    this.state = state;
    this.intraline = intraline;
  }

  public DiffInfo createDiffInfo(PatchScript ps, DiffSide sideA, DiffSide sideB) {
    Content content = calculateDiffContent(ps);

    DiffInfo result = new DiffInfo();

    ImmutableList<DiffWebLinkInfo> links = webLinksProvider.getDiffLinks();
    result.webLinks = links.isEmpty() ? null : links;

    if (ps.isBinary()) {
      result.binary = true;
    }
    result.metaA = createFileMeta(sideA);
    result.metaB = createFileMeta(sideB);

    if (intraline) {
      if (ps.hasIntralineTimeout()) {
        result.intralineStatus = IntraLineStatus.TIMEOUT;
      } else if (ps.hasIntralineFailure()) {
        result.intralineStatus = IntraLineStatus.FAILURE;
      } else {
        result.intralineStatus = IntraLineStatus.OK;
      }
    }

    result.changeType = CHANGE_TYPE.get(ps.getChangeType());
    if (result.changeType == null) {
      throw new IllegalStateException("unknown change type: " + ps.getChangeType());
    }

    if (ps.getPatchHeader().size() > 0) {
      result.diffHeader = ps.getPatchHeader();
    }
    result.content = content.lines;
    return result;
  }

  private Content calculateDiffContent(PatchScript ps) {
    ContentCollector contentCollector = new ContentCollector(ps);
    Set<Edit> editsDueToRebase = ps.getEditsDueToRebase();
    for (Edit edit : ps.getEdits()) {
      if (edit.getType() == Edit.Type.EMPTY) {
        continue;
      }
      contentCollector.addCommon(edit.getBeginA());

      checkState(
          contentCollector.nextA == edit.getBeginA(),
          "nextA = %s; want %s",
          contentCollector.nextA,
          edit.getBeginA());
      checkState(
          contentCollector.nextB == edit.getBeginB(),
          "nextB = %s; want %s",
          contentCollector.nextB,
          edit.getBeginB());
      switch (edit.getType()) {
        case DELETE:
        case INSERT:
        case REPLACE:
          List<Edit> internalEdit =
              edit instanceof ReplaceEdit ? ((ReplaceEdit) edit).getInternalEdits() : null;
          boolean dueToRebase = editsDueToRebase.contains(edit);
          contentCollector.addDiff(edit.getEndA(), edit.getEndB(), internalEdit, dueToRebase);
          break;
        case EMPTY:
        default:
          throw new IllegalStateException();
      }
    }
    contentCollector.addCommon(ps.getA().size());

    return new Content(contentCollector.lines);
  }

  private FileMeta createFileMeta(DiffSide side) {
    PatchScriptFileInfo fileInfo = side.fileInfo;
    if (fileInfo.displayMethod == DisplayMethod.NONE) {
      return null;
    }
    FileMeta result = new FileMeta();
    result.name = side.fileName;
    result.contentType =
        FileContentUtil.resolveContentType(state, side.fileName, fileInfo.mode, fileInfo.mimeType);
    result.lines = fileInfo.content.size();
    ImmutableList<WebLinkInfo> links = webLinksProvider.getFileWebLinks(side.type);
    result.webLinks = links.isEmpty() ? null : links;
    result.commitId = fileInfo.commitId;
    return result;
  }

  private static class ContentCollector {

    final List<ContentEntry> lines;
    final SparseFileContent fileA;
    final SparseFileContent fileB;
    final boolean ignoreWS;

    int nextA;
    int nextB;

    ContentCollector(PatchScript ps) {
      lines = Lists.newArrayListWithExpectedSize(ps.getEdits().size() + 2);
      fileA = ps.getA();
      fileB = ps.getB();
      ignoreWS = ps.isIgnoreWhitespace();
    }

    void addCommon(int end) {
      end = Math.min(end, fileA.size());
      if (nextA >= end) {
        return;
      }

      while (nextA < end) {
        if (!fileA.contains(nextA)) {
          int endRegion = Math.min(end, nextA == 0 ? fileA.first() : fileA.next(nextA - 1));
          int len = endRegion - nextA;
          entry().skip = len;
          nextA = endRegion;
          nextB += len;
          continue;
        }

        ContentEntry e = null;
        for (int i = nextA; i == nextA && i < end; i = fileA.next(i), nextA++, nextB++) {
          if (ignoreWS && fileB.contains(nextB)) {
            if (e == null || e.common == null) {
              e = entry();
              e.a = Lists.newArrayListWithCapacity(end - nextA);
              e.b = Lists.newArrayListWithCapacity(end - nextA);
              e.common = true;
            }
            e.a.add(fileA.get(nextA));
            e.b.add(fileB.get(nextB));
          } else {
            if (e == null || e.common != null) {
              e = entry();
              e.ab = Lists.newArrayListWithCapacity(end - nextA);
            }
            e.ab.add(fileA.get(nextA));
          }
        }
      }
    }

    void addDiff(int endA, int endB, List<Edit> internalEdit, boolean dueToRebase) {
      int lenA = endA - nextA;
      int lenB = endB - nextB;
      checkState(lenA > 0 || lenB > 0);

      ContentEntry e = entry();
      if (lenA > 0) {
        e.a = Lists.newArrayListWithCapacity(lenA);
        for (; nextA < endA; nextA++) {
          e.a.add(fileA.get(nextA));
        }
      }
      if (lenB > 0) {
        e.b = Lists.newArrayListWithCapacity(lenB);
        for (; nextB < endB; nextB++) {
          e.b.add(fileB.get(nextB));
        }
      }
      if (internalEdit != null && !internalEdit.isEmpty()) {
        e.editA = Lists.newArrayListWithCapacity(internalEdit.size() * 2);
        e.editB = Lists.newArrayListWithCapacity(internalEdit.size() * 2);
        int lastA = 0;
        int lastB = 0;
        for (Edit edit : internalEdit) {
          if (edit.getBeginA() != edit.getEndA()) {
            e.editA.add(
                ImmutableList.of(edit.getBeginA() - lastA, edit.getEndA() - edit.getBeginA()));
            lastA = edit.getEndA();
          }
          if (edit.getBeginB() != edit.getEndB()) {
            e.editB.add(
                ImmutableList.of(edit.getBeginB() - lastB, edit.getEndB() - edit.getBeginB()));
            lastB = edit.getEndB();
          }
        }
      }
      e.dueToRebase = dueToRebase ? true : null;
    }

    private ContentEntry entry() {
      ContentEntry e = new ContentEntry();
      lines.add(e);
      return e;
    }
  }

  private static class Content {

    final List<ContentEntry> lines;

    Content(List<ContentEntry> lines) {
      this.lines = lines;
    }
  }
}
