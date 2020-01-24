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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Patch.PatchType;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.fixes.FixCalculator;
import com.google.gerrit.server.mime.FileTypeRegistry;
import com.google.gerrit.server.patch.DiffContentCalculator.DiffCalculatorResult;
import com.google.gerrit.server.patch.DiffContentCalculator.TextSource;
import com.google.inject.Inject;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil2;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

class PatchScriptBuilder {

  private Change change;
  private DiffPreferencesInfo diffPrefs;
  private final FileTypeRegistry registry;
  private IntraLineDiffCalculator intralineDiffCalculator;

  @Inject
  PatchScriptBuilder(FileTypeRegistry ftr) {
    registry = ftr;
  }

  void setChange(Change c) {
    this.change = c;
  }

  void setDiffPrefs(DiffPreferencesInfo dp) {
    diffPrefs = dp;
  }

  void setIntraLineDiffCalculator(IntraLineDiffCalculator calculator) {
    intralineDiffCalculator = calculator;
  }

  PatchScript toPatchScript(
      Repository git,
      PatchList list,
      PatchListEntry content,
      CommentDetail comments,
      ImmutableList<Patch> history)
      throws IOException {

    PatchFileChange change =
        new PatchFileChange(
            content.getEdits(),
            content.getEditsDueToRebase(),
            content.getHeaderLines(),
            content.getOldName(),
            content.getNewName(),
            content.getChangeType(),
            content.getPatchType());
    SidesResolver sidesResolver = new SidesResolver(git, list.getComparisonType());
    ResolvedSides sides =
        resolveSides(
            git, sidesResolver, oldName(change), newName(change), list.getOldId(), list.getNewId());
    return build(sides.a, sides.b, change, comments, history);
  }

  private ResolvedSides resolveSides(
      Repository git,
      SidesResolver sidesResolver,
      String oldName,
      String newName,
      ObjectId aId,
      ObjectId bId)
      throws IOException {
    try (ObjectReader reader = git.newObjectReader()) {
      PatchSide a = sidesResolver.resolve(registry, reader, oldName, null, aId, true);
      PatchSide b =
          sidesResolver.resolve(registry, reader, newName, a, bId, Objects.equals(aId, bId));
      return new ResolvedSides(a, b);
    }
  }

  PatchScript toPatchScript(
      Repository git, ObjectId baseId, String fileName, List<FixReplacement> fixReplacements)
      throws IOException, ResourceConflictException, ResourceNotFoundException {
    SidesResolver sidesResolver = new SidesResolver(git, ComparisonType.againstOtherPatchSet());
    PatchSide a = resolveSideA(git, sidesResolver, fileName, baseId);
    if (a.mode == FileMode.MISSING) {
      throw new ResourceNotFoundException();
    }
    FixCalculator.FixResult fixResult = FixCalculator.calculateFix(a.src, fixReplacements);
    PatchSide b =
        new PatchSide(
            null,
            fileName,
            ObjectId.zeroId(),
            a.mode,
            fixResult.text.getContent(),
            fixResult.text,
            a.mimeType,
            a.displayMethod,
            a.fileMode);

    PatchFileChange change =
        new PatchFileChange(
            fixResult.edits,
            ImmutableSet.of(),
            ImmutableList.of(),
            fileName,
            fileName,
            ChangeType.MODIFIED,
            PatchType.UNIFIED);

    return build(a, b, change, null, null);
  }

  private PatchSide resolveSideA(
      Repository git, SidesResolver sidesResolver, String path, ObjectId baseId)
      throws IOException {
    try (ObjectReader reader = git.newObjectReader()) {
      return sidesResolver.resolve(registry, reader, path, null, baseId, true);
    }
  }

  private PatchScript build(
      PatchSide a,
      PatchSide b,
      PatchFileChange content,
      CommentDetail comments,
      ImmutableList<Patch> history) {

    ImmutableList<Edit> contentEdits = content.getEdits();
    ImmutableSet<Edit> editsDueToRebase = content.getEditsDueToRebase();

    IntraLineDiffCalculatorResult intralineResult = IntraLineDiffCalculatorResult.NO_RESULT;

    if (isModify(content) && intralineDiffCalculator != null && isIntralineModeAllowed(b)) {
      intralineResult =
          intralineDiffCalculator.calculateIntraLineDiff(
              contentEdits, editsDueToRebase, a.id, b.id, a.src, b.src, b.treeId, b.path);
    }
    ImmutableList<Edit> finalEdits = intralineResult.edits.orElse(contentEdits);
    DiffContentCalculator calculator = new DiffContentCalculator(diffPrefs);
    DiffCalculatorResult diffCalculatorResult =
        calculator.calculateDiffContent(
            new TextSource(a.src), new TextSource(b.src), finalEdits, comments);

    return new PatchScript(
        change.getKey(),
        content.getChangeType(),
        content.getOldName(),
        content.getNewName(),
        a.fileMode,
        b.fileMode,
        content.getHeaderLines(),
        diffPrefs,
        diffCalculatorResult.diffContent.a,
        diffCalculatorResult.diffContent.b,
        diffCalculatorResult.edits,
        editsDueToRebase,
        a.displayMethod,
        b.displayMethod,
        a.mimeType,
        b.mimeType,
        history,
        intralineResult.failure,
        intralineResult.timeout,
        content.getPatchType() == Patch.PatchType.BINARY,
        a.treeId == null ? null : a.treeId.getName(),
        b.treeId == null ? null : b.treeId.getName());
  }

  private static boolean isModify(PatchFileChange content) {
    switch (content.getChangeType()) {
      case MODIFIED:
      case COPIED:
      case RENAMED:
      case REWRITE:
        return true;

      case ADDED:
      case DELETED:
      default:
        return false;
    }
  }

  private static String oldName(PatchFileChange entry) {
    switch (entry.getChangeType()) {
      case ADDED:
        return null;
      case DELETED:
      case MODIFIED:
      case REWRITE:
        return entry.getNewName();
      case COPIED:
      case RENAMED:
      default:
        return entry.getOldName();
    }
  }

  private static String newName(PatchFileChange entry) {
    switch (entry.getChangeType()) {
      case DELETED:
        return null;
      case ADDED:
      case MODIFIED:
      case COPIED:
      case RENAMED:
      case REWRITE:
      default:
        return entry.getNewName();
    }
  }

  private static boolean isIntralineModeAllowed(PatchSide side) {
    // The intraline diff cache keys are the same for these cases. It's better to not show
    // intraline results than showing completely wrong diffs or to run into a server error.
    return !Patch.isMagic(side.path) && !isSubmoduleCommit(side.mode);
  }

  private static boolean isSubmoduleCommit(FileMode mode) {
    return mode.getObjectType() == Constants.OBJ_COMMIT;
  }

  private static class PatchSide {
    final ObjectId treeId;
    final String path;
    final ObjectId id;
    final FileMode mode;
    final byte[] srcContent;
    final Text src;
    final String mimeType;
    final DisplayMethod displayMethod;
    final PatchScript.FileMode fileMode;

    private PatchSide(
        ObjectId treeId,
        String path,
        ObjectId id,
        FileMode mode,
        byte[] srcContent,
        Text src,
        String mimeType,
        DisplayMethod displayMethod,
        PatchScript.FileMode fileMode) {
      this.treeId = treeId;
      this.path = path;
      this.id = id;
      this.mode = mode;
      this.srcContent = srcContent;
      this.src = src;
      this.mimeType = mimeType;
      this.displayMethod = displayMethod;
      this.fileMode = fileMode;
    }
  }

  private static class ResolvedSides {
    // Not an @AutoValue because PatchSide can't be AutoValue
    public final PatchSide a;
    public final PatchSide b;

    ResolvedSides(PatchSide a, PatchSide b) {
      this.a = a;
      this.b = b;
    }
  }

  static class SidesResolver {

    private final Repository db;
    private final ComparisonType comparisonType;

    SidesResolver(Repository db, ComparisonType comparisonType) {
      this.db = db;
      this.comparisonType = comparisonType;
    }

    PatchSide resolve(
        final FileTypeRegistry registry,
        final ObjectReader reader,
        final String path,
        final PatchSide other,
        final ObjectId within,
        final boolean isWithinEqualsA)
        throws IOException {
      try {
        boolean isCommitMsg = Patch.COMMIT_MSG.equals(path);
        boolean isMergeList = Patch.MERGE_LIST.equals(path);
        if (isCommitMsg || isMergeList) {
          if (comparisonType.isAgainstParentOrAutoMerge() && isWithinEqualsA) {
            return createSide(
                within,
                path,
                ObjectId.zeroId(),
                FileMode.MISSING,
                Text.NO_BYTES,
                Text.EMPTY,
                MimeUtil2.UNKNOWN_MIME_TYPE.toString(),
                DisplayMethod.NONE,
                false);
          }
          Text src =
              isCommitMsg
                  ? Text.forCommit(reader, within)
                  : Text.forMergeList(comparisonType, reader, within);
          byte[] srcContent = src.getContent();
          DisplayMethod displayMethod;
          FileMode mode;
          if (src == Text.EMPTY) {
            mode = FileMode.MISSING;
            displayMethod = DisplayMethod.NONE;
          } else {
            mode = FileMode.REGULAR_FILE;
            displayMethod = DisplayMethod.DIFF;
          }
          return createSide(
              within,
              path,
              within,
              mode,
              srcContent,
              src,
              MimeUtil2.UNKNOWN_MIME_TYPE.toString(),
              displayMethod,
              false);
        }
        final TreeWalk tw = find(reader, path, within);
        ObjectId id = tw != null ? tw.getObjectId(0) : ObjectId.zeroId();
        FileMode mode = tw != null ? tw.getFileMode(0) : FileMode.MISSING;
        boolean reuse =
            other != null
                && other.id.equals(id)
                && (other.mode == mode || isBothFile(other.mode, mode));
        Text src = null;
        byte[] srcContent;
        if (reuse) {
          srcContent = other.srcContent;

        } else if (mode.getObjectType() == Constants.OBJ_BLOB) {
          srcContent = Text.asByteArray(db.open(id, Constants.OBJ_BLOB));

        } else if (mode.getObjectType() == Constants.OBJ_COMMIT) {
          String strContent = "Subproject commit " + ObjectId.toString(id);
          srcContent = strContent.getBytes(UTF_8);

        } else {
          srcContent = Text.NO_BYTES;
        }
        String mimeType = MimeUtil2.UNKNOWN_MIME_TYPE.toString();
        DisplayMethod displayMethod = DisplayMethod.DIFF;
        if (reuse) {
          mimeType = other.mimeType;
          displayMethod = other.displayMethod;
          src = other.src;

        } else if (srcContent.length > 0 && FileMode.SYMLINK != mode) {
          MimeType registryMimeType = registry.getMimeType(path, srcContent);
          if ("image".equals(registryMimeType.getMediaType())
              && registry.isSafeInline(registryMimeType)) {
            displayMethod = DisplayMethod.IMG;
          }
          mimeType = registryMimeType.toString();
        }
        return createSide(within, path, id, mode, srcContent, src, mimeType, displayMethod, reuse);

      } catch (IOException err) {
        throw new IOException("Cannot read " + within.name() + ":" + path, err);
      }
    }

    private PatchSide createSide(
        ObjectId treeId,
        String path,
        ObjectId id,
        FileMode mode,
        byte[] srcContent,
        Text src,
        String mimeType,
        DisplayMethod displayMethod,
        boolean reuse) {
      if (!reuse) {
        if (srcContent == Text.NO_BYTES) {
          src = Text.EMPTY;
        } else {
          src = new Text(srcContent);
        }
      }
      if (mode == FileMode.MISSING) {
        displayMethod = DisplayMethod.NONE;
      }
      PatchScript.FileMode fileMode = PatchScript.FileMode.FILE;
      if (mode == FileMode.SYMLINK) {
        fileMode = PatchScript.FileMode.SYMLINK;
      } else if (mode == FileMode.GITLINK) {
        fileMode = PatchScript.FileMode.GITLINK;
      }
      return new PatchSide(
          treeId, path, id, mode, srcContent, src, mimeType, displayMethod, fileMode);
    }

    private TreeWalk find(ObjectReader reader, String path, ObjectId within) throws IOException {
      if (path == null || within == null) {
        return null;
      }
      try (RevWalk rw = new RevWalk(reader)) {
        final RevTree tree = rw.parseTree(within);
        return TreeWalk.forPath(reader, path, tree);
      }
    }
  }

  private static boolean isBothFile(FileMode a, FileMode b) {
    return (a.getBits() & FileMode.TYPE_FILE) == FileMode.TYPE_FILE
        && (b.getBits() & FileMode.TYPE_FILE) == FileMode.TYPE_FILE;
  }

  static class IntraLineDiffCalculatorResult {
    // Not an @AutoValue because Edit is mutable
    final boolean failure;
    final boolean timeout;
    private final Optional<ImmutableList<Edit>> edits;

    private IntraLineDiffCalculatorResult(
        Optional<ImmutableList<Edit>> edits, boolean failure, boolean timeout) {
      this.failure = failure;
      this.timeout = timeout;
      this.edits = edits;
    }

    static final IntraLineDiffCalculatorResult NO_RESULT =
        new IntraLineDiffCalculatorResult(Optional.empty(), false, false);
    static final IntraLineDiffCalculatorResult FAILURE =
        new IntraLineDiffCalculatorResult(Optional.empty(), true, false);
    static final IntraLineDiffCalculatorResult TIMEOUT =
        new IntraLineDiffCalculatorResult(Optional.empty(), false, true);

    static IntraLineDiffCalculatorResult success(ImmutableList<Edit> edits) {
      return new IntraLineDiffCalculatorResult(Optional.of(edits), false, false);
    }
  }

  interface IntraLineDiffCalculator {

    IntraLineDiffCalculatorResult calculateIntraLineDiff(
        ImmutableList<Edit> edits,
        Set<Edit> editsDueToRebase,
        ObjectId aId,
        ObjectId bId,
        Text aSrc,
        Text bSrc,
        ObjectId bTreeId,
        String bPath);
  }

  static class PatchFileChange {
    private final ImmutableList<Edit> edits;
    private final ImmutableSet<Edit> editsDueToRebase;
    private final ImmutableList<String> headerLines;
    private final String oldName;
    private final String newName;
    private final ChangeType changeType;
    private final Patch.PatchType patchType;

    public PatchFileChange(
        ImmutableList<Edit> edits,
        ImmutableSet<Edit> editsDueToRebase,
        ImmutableList<String> headerLines,
        String oldName,
        String newName,
        ChangeType changeType,
        Patch.PatchType patchType) {
      this.edits = edits;
      this.editsDueToRebase = editsDueToRebase;
      this.headerLines = headerLines;
      this.oldName = oldName;
      this.newName = newName;
      this.changeType = changeType;
      this.patchType = patchType;
    }

    ImmutableList<Edit> getEdits() {
      return edits;
    }

    ImmutableSet<Edit> getEditsDueToRebase() {
      return editsDueToRebase;
    }

    ImmutableList<String> getHeaderLines() {
      return headerLines;
    }

    String getNewName() {
      return newName;
    }

    String getOldName() {
      return oldName;
    }

    ChangeType getChangeType() {
      return changeType;
    }

    Patch.PatchType getPatchType() {
      return patchType;
    }
  }
}
