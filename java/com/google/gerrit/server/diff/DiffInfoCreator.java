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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.common.data.PatchScript.PatchScriptFileInfo;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffInfo.ContentEntry;
import com.google.gerrit.extensions.common.DiffInfo.FileMeta;
import com.google.gerrit.extensions.common.DiffInfo.IntraLineStatus;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.project.ProjectState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.diff.Edit;

/** Creates and fills a new {@link DiffInfo} object based on diff between files. */
public class DiffInfoCreator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
  private final ProjectState state;

  public DiffInfoCreator(
      ProjectState state, DiffWebLinksProvider webLinksProvider, boolean intraline) {
    this.webLinksProvider = webLinksProvider;
    this.state = state;
    this.intraline = intraline;
  }

  /** Returns the {@link DiffInfo} to display for end-users */
  public DiffInfo create(PatchScript ps, DiffSide sideA, DiffSide sideB) {
    DiffInfo result = new DiffInfo();

    ImmutableList<DiffWebLinkInfo> links = webLinksProvider.getDiffLinks();
    result.webLinks = links.isEmpty() ? null : links;
    ImmutableList<WebLinkInfo> editLinks = webLinksProvider.getEditWebLinks();
    result.editWebLinks = editLinks.isEmpty() ? null : editLinks;

    if (ps.isBinary()) {
      result.binary = true;
    }
    result.metaA = createFileMeta(sideA).orElse(null);
    result.metaB = createFileMeta(sideB).orElse(null);

    if (intraline) {
      if (ps.hasIntralineTimeout()) {
        result.intralineStatus = IntraLineStatus.TIMEOUT;
      } else if (ps.hasIntralineFailure()) {
        result.intralineStatus = IntraLineStatus.FAILURE;
      } else {
        result.intralineStatus = IntraLineStatus.OK;
      }
      logger.atFine().log("intralineStatus = %s", result.intralineStatus);
    }

    result.changeType = CHANGE_TYPE.get(ps.getChangeType());
    logger.atFine().log("changeType = %s", result.changeType);
    if (result.changeType == null) {
      throw new IllegalStateException("unknown change type: " + ps.getChangeType());
    }

    if (ps.getPatchHeader().size() > 0) {
      result.diffHeader = ps.getPatchHeader();
    }
    result.content = calculateDiffContentEntries(ps);
    return result;
  }

  private static List<ContentEntry> calculateDiffContentEntries(PatchScript ps) {
    ContentCollector contentCollector = new ContentCollector(ps);
    Set<Edit> editsDueToRebase = ps.getEditsDueToRebase();
    for (Edit edit : ps.getEdits()) {
      logger.atFine().log("next edit = %s", edit);

      if (edit.getType() == Edit.Type.EMPTY) {
        logger.atFine().log("skip empty edit");
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
    contentCollector.addCommon(ps.getA().getSize());

    return contentCollector.lines;
  }

  private Optional<FileMeta> createFileMeta(DiffSide side) {
    PatchScriptFileInfo fileInfo = side.fileInfo();
    if (fileInfo.displayMethod == DisplayMethod.NONE) {
      return Optional.empty();
    }
    FileMeta result = new FileMeta();
    result.name = side.fileName();
    result.contentType =
        FileContentUtil.resolveContentType(
            state, side.fileName(), fileInfo.mode, fileInfo.mimeType);
    result.lines = fileInfo.content.getSize();
    ImmutableList<WebLinkInfo> fileLinks = webLinksProvider.getFileWebLinks(side.type());
    result.webLinks = fileLinks.isEmpty() ? null : fileLinks;
    result.commitId = fileInfo.commitId;
    return Optional.of(result);
  }

  private static class ContentCollector {

    private final List<ContentEntry> lines;
    private final SparseFileContent.Accessor fileA;
    private final SparseFileContent.Accessor fileB;
    private final boolean ignoreWS;

    private int nextA;
    private int nextB;

    ContentCollector(PatchScript ps) {
      lines = Lists.newArrayListWithExpectedSize(ps.getEdits().size() + 2);
      fileA = ps.getA().createAccessor();
      fileB = ps.getB().createAccessor();
      ignoreWS = ps.isIgnoreWhitespace();
    }

    void addCommon(int end) {
      logger.atFine().log("addCommon: end = %d", end);

      end = Math.min(end, fileA.getSize());
      logger.atFine().log("end = %d", end);

      if (nextA >= end) {
        logger.atFine().log("nextA >= end: nextA = %d, end = %d", nextA, end);
        return;
      }

      while (nextA < end) {
        logger.atFine().log("nextA < end: nextA = %d, end = %d", nextA, end);

        if (!fileA.contains(nextA)) {
          logger.atFine().log("fileA does not contain nextA: nextA = %d", nextA);

          int endRegion = Math.min(end, nextA == 0 ? fileA.first() : fileA.next(nextA - 1));
          int len = endRegion - nextA;
          entry().skip = len;
          nextA = endRegion;
          nextB += len;

          logger.atFine().log("setting: nextA = %d, nextB = %d", nextA, nextB);
          continue;
        }

        ContentEntry e = null;
        for (int i = nextA; i == nextA && i < end; i = fileA.next(i), nextA++, nextB++) {
          if (ignoreWS && fileB.contains(nextB)) {
            if (e == null || e.common == null) {
              logger.atFine().log("create new common entry: nextA = %d, nextB = %d", nextA, nextB);
              e = entry();
              e.a = Lists.newArrayListWithCapacity(end - nextA);
              e.b = Lists.newArrayListWithCapacity(end - nextA);
              e.common = true;
            }
            e.a.add(fileA.get(nextA));
            e.b.add(fileB.get(nextB));
          } else {
            if (e == null || e.common != null) {
              logger.atFine().log(
                  "create new non-common entry: nextA = %d, nextB = %d", nextA, nextB);
              e = entry();
              e.ab = Lists.newArrayListWithCapacity(end - nextA);
            }
            e.ab.add(fileA.get(nextA));
          }
        }
      }
    }

    void addDiff(int endA, int endB, List<Edit> internalEdit, boolean dueToRebase) {
      logger.atFine().log(
          "addDiff: endA = %d, endB = %d, numberOfInternalEdits = %d, dueToRebase = %s",
          endA, endB, internalEdit != null ? internalEdit.size() : 0, dueToRebase);

      int lenA = endA - nextA;
      int lenB = endB - nextB;
      logger.atFine().log("lenA = %d, lenB = %d", lenA, lenB);
      checkState(lenA > 0 || lenB > 0);

      logger.atFine().log("create non-common entry");
      ContentEntry e = entry();
      if (lenA > 0) {
        logger.atFine().log("lenA > 0: lenA = %d", lenA);
        e.a = Lists.newArrayListWithCapacity(lenA);
        for (; nextA < endA; nextA++) {
          e.a.add(fileA.get(nextA));
        }
      }
      if (lenB > 0) {
        logger.atFine().log("lenB > 0: lenB = %d", lenB);
        e.b = Lists.newArrayListWithCapacity(lenB);
        for (; nextB < endB; nextB++) {
          e.b.add(fileB.get(nextB));
        }
      }
      if (internalEdit != null && !internalEdit.isEmpty()) {
        logger.atFine().log("processing internal edits");

        e.editA = Lists.newArrayListWithCapacity(internalEdit.size() * 2);
        e.editB = Lists.newArrayListWithCapacity(internalEdit.size() * 2);
        int lastA = 0;
        int lastB = 0;
        for (Edit edit : internalEdit) {
          logger.atFine().log("internal edit = %s", edit);

          if (edit.getBeginA() != edit.getEndA()) {
            logger.atFine().log(
                "edit.getBeginA() != edit.getEndA(): edit.getBeginA() = %d, edit.getEndA() = %d",
                edit.getBeginA(), edit.getEndA());
            e.editA.add(
                ImmutableList.of(edit.getBeginA() - lastA, edit.getEndA() - edit.getBeginA()));
            lastA = edit.getEndA();
            logger.atFine().log("lastA = %d", lastA);
          }
          if (edit.getBeginB() != edit.getEndB()) {
            logger.atFine().log(
                "edit.getBeginB() != edit.getEndB(): edit.getBeginB() = %d, edit.getEndB() = %d",
                edit.getBeginB(), edit.getEndB());
            e.editB.add(
                ImmutableList.of(edit.getBeginB() - lastB, edit.getEndB() - edit.getBeginB()));
            lastB = edit.getEndB();
            logger.atFine().log("lastB = %d", lastB);
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
}
