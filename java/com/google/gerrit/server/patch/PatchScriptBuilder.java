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
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.prettify.common.EditList;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.prettify.common.SparseFileContentBuilder;
import com.google.gerrit.server.mime.FileTypeRegistry;
import com.google.inject.Inject;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil2;
import java.io.IOException;
import java.util.Comparator;
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
  private SidesResolver sidesResolver;

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

  void setSidesResolver(SidesResolver resolver) {
    sidesResolver = resolver;
  }

  PatchScript toPatchScript(PatchFileChange content, CommentDetail comments, ImmutableList<Patch> history)
      throws IOException {
    return build(content, comments, history);
  }

  private PatchScript build(PatchFileChange content, CommentDetail comments, ImmutableList<Patch> history)
      throws IOException {

    ResolvedSides sides = sidesResolver.resolveSides(registry, oldName(content), newName(content));
    PatchSide a = sides.a;
    PatchSide b = sides.b;

    ImmutableList<Edit> contentEdits = content.getEdits();
    ImmutableSet<Edit> editsDueToRebase = content.getEditsDueToRebase();

    IntraLineDiffCalculatorResult intralineResult = IntraLineDiffCalculatorResult.NO_RESULT;

    if (isModify(content) && intralineDiffCalculator != null && isIntralineModeAllowed(b)) {
      intralineResult =
          intralineDiffCalculator.calculateIntraLineDiff(
              contentEdits, editsDueToRebase, a.id, b.id, a.src, b.src, b.treeId, b.path);
    }
    ImmutableList<Edit> finalEdits = intralineResult.edits().orElse(contentEdits);
    DiffContentCalculator calculator = new DiffContentCalculator(diffPrefs);
    DiffCalculatorResult diffCalculatorResult = calculator.calculateDiffContent(new TextSource(a.src), new TextSource(b.src), contentEdits, finalEdits, comments);

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

  private static class DiffContentCalculator {
    private static final int MAX_CONTEXT = 5000000;
    private static final int BIG_FILE = 9000;

    private static final Comparator<Edit> EDIT_SORT = comparing(Edit::getBeginA);

    private final DiffPreferencesInfo diffPrefs;
    DiffContentCalculator(DiffPreferencesInfo diffPrefs) {
      this.diffPrefs = diffPrefs;
    }

    DiffCalculatorResult calculateDiffContent(TextSource srcA, TextSource srcB, ImmutableList<Edit> originalEdits, ImmutableList<Edit> finalEdits, CommentDetail comments) {
      int context = getContext();
      if (srcA.src == srcB.src && srcA.size() <= context && originalEdits.isEmpty()) {
        // Odd special case; the files are identical (100% rename or copy)
        // and the user has asked for context that is larger than the file.
        // Send them the entire file, with an empty edit after the last line.
        //
        SparseFileContentBuilder diffA = new SparseFileContentBuilder();
        diffA.setSize(srcA.size());
        for (int i = 0; i < srcA.size(); i++) {
          srcA.copyLineTo(diffA, i);
        }
        ImmutableList<Edit> edits = ImmutableList.of(new Edit(srcA.size(), srcA.size()));
        DiffContent diffContent = new DiffContent(diffA.build(), SparseFileContent.create(ImmutableList.of(), srcB.size()));
        return new DiffCalculatorResult(diffContent, edits);
      } else {
        ImmutableList.Builder<Edit> builder = ImmutableList.builder();

        builder.addAll(correctForDifferencesInNewlineAtEnd(srcA, srcB, finalEdits));

        boolean nonsortedEdits = false;
        if (comments != null) {
          ImmutableList<Edit> commentEdits = ensureCommentsVisible(comments, finalEdits);
          builder.addAll(commentEdits);
          nonsortedEdits = !commentEdits.isEmpty();
        }

        ImmutableList<Edit> edits = builder.build();
        if (nonsortedEdits) {
          edits = ImmutableList.sortedCopyOf(EDIT_SORT, edits);
        }

        // In order to expand the skipped common lines or syntax highlight the
        // file properly we need to give the client the complete file contents.
        // So force our context temporarily to the complete file size.
        //
        DiffContent diffContent = packContent(srcA, srcB,
            diffPrefs.ignoreWhitespace != Whitespace.IGNORE_NONE, edits, MAX_CONTEXT);
        return new DiffCalculatorResult(diffContent, edits);
      }
    }

    private int getContext() {
      if (diffPrefs.context == DiffPreferencesInfo.WHOLE_FILE_CONTEXT) {
        return MAX_CONTEXT;
      }
      return Math.min(diffPrefs.context, MAX_CONTEXT);
    }


    private ImmutableList<Edit> correctForDifferencesInNewlineAtEnd(TextSource a, TextSource b, ImmutableList<Edit> edits) {
      // a.src.size() is the size ignoring a newline at the end whereas a.size() considers it.
      int aSize = a.src.size();
      int bSize = b.src.size();

      if (edits.isEmpty() && (aSize == 0 || bSize == 0)) {
        // The diff was requested for a file which was either added or deleted but which JGit doesn't
        // consider a file addition/deletion (e.g. requesting a diff for the old file name of a
        // renamed file looks like a deletion).
        return edits;
      }

      if (edits.isEmpty() && (aSize != bSize)) {
        // Only edits due to rebase were present. If we now added the edits for the newlines, the
        // code which later assembles the file contents would fail.
        return edits;
      }

      Optional<Edit> lastEdit = getLast(edits);
      if (isNewlineAtEndDeleted(a, b)) {
        Optional<Edit> lastLineEdit = lastEdit.filter(edit -> edit.getEndA() == aSize);

        if (lastLineEdit.isPresent()) {
          Edit edit = lastLineEdit.get();
          Edit updatedLastLineEdit = new Edit(edit.getBeginA(), edit.getEndA() + 1, edit.getBeginB(),
              edit.getEndB());

          ImmutableList.Builder<Edit> newEditsBuilder = ImmutableList
              .builderWithExpectedSize(edits.size());
          return newEditsBuilder
              .addAll(edits.subList(0, edits.size() - 1))
              .add(updatedLastLineEdit)
              .build();
        }
        ImmutableList.Builder<Edit> newEditsBuilder = ImmutableList
            .builderWithExpectedSize(edits.size() + 1);
        Edit newlineEdit = new Edit(aSize, aSize + 1, bSize, bSize);
        return newEditsBuilder
            .addAll(edits)
            .add(newlineEdit)
            .build();

      } else if (isNewlineAtEndAdded(a, b)) {
        Optional<Edit> lastLineEdit = lastEdit.filter(edit -> edit.getEndB() == bSize);
        if (lastLineEdit.isPresent()) {
          Edit edit = lastLineEdit.get();
          Edit updatedLastLineEdit = new Edit(edit.getBeginA(), edit.getEndA(), edit.getBeginB(),
              edit.getEndB() + 1);

          ImmutableList.Builder<Edit> newEditsBuilder = ImmutableList
              .builderWithExpectedSize(edits.size());
          return newEditsBuilder
              .addAll(edits.subList(0, edits.size() - 1))
              .add(updatedLastLineEdit)
              .build();
        }
        ImmutableList.Builder<Edit> newEditsBuilder = ImmutableList
            .builderWithExpectedSize(edits.size() + 1);
        Edit newlineEdit = new Edit(aSize, aSize, bSize, bSize + 1);
        return newEditsBuilder
            .addAll(edits)
            .add(newlineEdit)
            .build();
      }
      return edits;
    }

    private static <T> Optional<T> getLast(List<T> list) {
      return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(list.size() - 1));
    }

    private boolean isNewlineAtEndDeleted(TextSource a, TextSource b) {
      return !a.src.isMissingNewlineAtEnd() && b.src.isMissingNewlineAtEnd();
    }

    private boolean isNewlineAtEndAdded(TextSource a, TextSource b) {
      return a.src.isMissingNewlineAtEnd() && !b.src.isMissingNewlineAtEnd();
    }

    private ImmutableList<Edit> ensureCommentsVisible(CommentDetail comments, ImmutableList<Edit> edits) {
      if (comments.getCommentsA().isEmpty() && comments.getCommentsB().isEmpty()) {
        // No comments, no additional dummy edits are required.
        //
        return ImmutableList.of();
      }

      // Construct empty Edit blocks around each location where a comment is.
      // This will force the later packContent method to include the regions
      // containing comments, potentially combining those regions together if
      // they have overlapping contexts. UI renders will also be able to make
      // correct hunks from this, but because the Edit is empty they will not
      // style it specially.
      //
      final ImmutableList.Builder<Edit> commmentEdits = ImmutableList.builder();
      int lastLine;

      lastLine = -1;
      for (Comment c : comments.getCommentsA()) {
        final int a = c.lineNbr;
        if (lastLine != a) {
          final int b = mapA2B(a - 1, edits);
          if (0 <= b) {
            getNewEditForComment(edits, new Edit(a - 1, b))
                .ifPresent(commmentEdits::add);
          }
          lastLine = a;
        }
      }

      lastLine = -1;
      for (Comment c : comments.getCommentsB()) {
        int b = c.lineNbr;
        if (lastLine != b) {
          final int a = mapB2A(b - 1, edits);
          if (0 <= a) {
            getNewEditForComment(edits, new Edit(a, b - 1))
                .ifPresent(commmentEdits::add);
          }
          lastLine = b;
        }
      }
      return commmentEdits.build();
    }

    private Optional<Edit> getNewEditForComment(ImmutableList<Edit> edits, Edit toAdd) {
      final int a = toAdd.getBeginA();
      final int b = toAdd.getBeginB();
      for (Edit e : edits) {
        if (e.getBeginA() <= a && a <= e.getEndA()) {
          return Optional.empty();
        }
        if (e.getBeginB() <= b && b <= e.getEndB()) {
          return Optional.empty();
        }
      }
      return Optional.of(toAdd);
    }

    private int mapA2B(int a, ImmutableList<Edit> edits) {
      if (edits.isEmpty()) {
        // Magic special case of an unmodified file.
        //
        return a;
      }

      for (int i = 0; i < edits.size(); i++) {
        final Edit e = edits.get(i);
        if (a < e.getBeginA()) {
          if (i == 0) {
            // Special case of context at start of file.
            //
            return a;
          }
          return e.getBeginB() - (e.getBeginA() - a);
        }
        if (e.getBeginA() <= a && a <= e.getEndA()) {
          return -1;
        }
      }

      final Edit last = edits.get(edits.size() - 1);
      return last.getEndB() + (a - last.getEndA());
    }

    private int mapB2A(int b, ImmutableList<Edit> edits) {
      if (edits.isEmpty()) {
        // Magic special case of an unmodified file.
        //
        return b;
      }

      for (int i = 0; i < edits.size(); i++) {
        final Edit e = edits.get(i);
        if (b < e.getBeginB()) {
          if (i == 0) {
            // Special case of context at start of file.
            //
            return b;
          }
          return e.getBeginA() - (e.getBeginB() - b);
        }
        if (e.getBeginB() <= b && b <= e.getEndB()) {
          return -1;
        }
      }

      final Edit last = edits.get(edits.size() - 1);
      return last.getEndA() + (b - last.getEndB());
    }

    private DiffContent packContent(TextSource a, TextSource b, boolean ignoredWhitespace, ImmutableList<Edit> edits, int context) {
      SparseFileContentBuilder diffA = new SparseFileContentBuilder();
      SparseFileContentBuilder diffB = new SparseFileContentBuilder();
      diffA.setSize(a.size());
      diffB.setSize(b.size());
      EditList list = new EditList(edits, context, a.size(), b.size());
      for (EditList.Hunk hunk : list.getHunks()) {
        while (hunk.next()) {
          if (hunk.isContextLine()) {
            String lineA = a.getSourceLine(hunk.getCurA());
            diffA.addLine(hunk.getCurA(), lineA);

            if (ignoredWhitespace) {
              // If we ignored whitespace in some form, also get the line
              // from b when it does not exactly match the line from a.
              //
              String lineB = b.getSourceLine(hunk.getCurB());
              if (!lineA.equals(lineB)) {
                diffB.addLine(hunk.getCurB(), lineB);
              }
            }
            hunk.incBoth();
            continue;
          }

          if (hunk.isDeletedA()) {
            a.copyLineTo(diffA, hunk.getCurA());
            hunk.incA();
          }

          if (hunk.isInsertedB()) {
            b.copyLineTo(diffB, hunk.getCurB());
            hunk.incB();
          }
        }
      }
      return new DiffContent(diffA.build(), diffB.build());
    }

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

  private static class DiffCalculatorResult {
    final DiffContent diffContent;
    final ImmutableList<Edit> edits;

    DiffCalculatorResult(DiffContent diffContent, ImmutableList<Edit> edits) {
      this.diffContent = diffContent;
      this.edits = edits;
    }
  }

  private static class DiffContent {
    //This class is not @AutoValue, because Edit is mutable
    final SparseFileContent a;
    final SparseFileContent b;

    DiffContent(SparseFileContent a, SparseFileContent b) {
      this.a = a;
      this.b = b;
    }
  }

  private static class TextSource {
    final Text src;
    public TextSource(Text src) {
      this.src = src;
    }

    int size() {
      if (src == null) {
        return 0;
      }
      if (src.isMissingNewlineAtEnd()) {
        return src.size();
      }
      return src.size() + 1;
    }

    void copyLineTo(SparseFileContentBuilder target, int lineNumber) {
      target.addLine(lineNumber, getSourceLine(lineNumber));
    }

    private String getSourceLine(int lineNumber) {
      return lineNumber >= src.size() ? "" : src.getString(lineNumber);
    }
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

  interface SidesResolver {

    ResolvedSides resolveSides(FileTypeRegistry typeRegistry, String oldName, String newName)
        throws IOException;
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

  static class SidesResolverImpl implements SidesResolver {

    private final Repository db;
    private ComparisonType comparisonType;
    private ObjectId aId;
    private ObjectId bId;

    SidesResolverImpl(Repository db) {
      this.db = db;
    }

    void setTrees(ComparisonType comparisonType, ObjectId a, ObjectId b) {
      this.comparisonType = comparisonType;
      this.aId = a;
      this.bId = b;
    }

    @Override
    public ResolvedSides resolveSides(FileTypeRegistry typeRegistry, String oldName, String newName)
        throws IOException {
      try (ObjectReader reader = db.newObjectReader()) {
        PatchSide a = resolve(typeRegistry, reader, oldName, null, aId);
        PatchSide b = resolve(typeRegistry, reader, newName, a, bId);
        return new ResolvedSides(a, b);
      }
    }

    PatchSide resolve(
        final FileTypeRegistry registry,
        final ObjectReader reader,
        final String path,
        final PatchSide other,
        final ObjectId within)
        throws IOException {
      try {
        boolean isCommitMsg = Patch.COMMIT_MSG.equals(path);
        boolean isMergeList = Patch.MERGE_LIST.equals(path);
        if (isCommitMsg || isMergeList) {
          if (comparisonType.isAgainstParentOrAutoMerge() && Objects.equals(aId, within)) {
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
          if ("image".equals(registryMimeType.getMediaType()) && registry.isSafeInline(registryMimeType)) {
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

    private TreeWalk find(ObjectReader reader, String path, ObjectId within)
        throws IOException {
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

  interface PatchFileChange {

    ImmutableList<Edit> getEdits();

    ImmutableSet<Edit> getEditsDueToRebase();

    ImmutableList<String> getHeaderLines();

    String getNewName();

    String getOldName();

    ChangeType getChangeType();

    Patch.PatchType getPatchType();
  }
}
