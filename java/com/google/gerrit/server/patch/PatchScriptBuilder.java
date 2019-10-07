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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.prettify.common.EditList;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.server.mime.FileTypeRegistry;
import com.google.inject.Inject;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil2;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

class PatchScriptBuilder {

  static final int MAX_CONTEXT = 5000000;
  static final int BIG_FILE = 9000;

  private static final Comparator<Edit> EDIT_SORT = comparing(Edit::getBeginA);

  private Change change;
  private DiffPreferencesInfo diffPrefs;
  private List<Edit> edits;
  private final FileTypeRegistry registry;
  private int context;
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

    context = diffPrefs.context;
    if (context == DiffPreferencesInfo.WHOLE_FILE_CONTEXT) {
      context = MAX_CONTEXT;
    } else if (context > MAX_CONTEXT) {
      context = MAX_CONTEXT;
    }
  }

  void setIntraLineDiffCalculator(IntraLineDiffCalculator calculator) {
    intralineDiffCalculator = calculator;
  }

  void setSidesResolver(SidesResolver resolver) {
    sidesResolver = resolver;
  }

  PatchScript toPatchScript(
      PatchScriptBuilderInput content, CommentDetail comments, List<Patch> history)
      throws IOException {
    boolean intralineFailure = false;
    boolean intralineTimeout = false;

  private PatchScript build(
      PatchScriptBuilderInput content, CommentDetail comments, List<Patch> history)
      throws IOException {

    ResolvedSides sides = sidesResolver.resolveSides(registry, oldName(content), newName(content));
    PatchSide a = sides.a;
    PatchSide b = sides.b;

    edits = new ArrayList<>(content.getEdits());
    ImmutableSet<Edit> editsDueToRebase = content.getEditsDueToRebase();

    if (isModify(content) && diffPrefs.intralineDifference) {
      IntraLineDiff d =
          patchListCache.getIntraLineDiff(
              IntraLineDiffKey.create(a.id, b.id, diffPrefs.ignoreWhitespace),
              IntraLineDiffArgs.create(
                  a.src, b.src, edits, editsDueToRebase, projectKey, bId, b.path));
      if (d != null) {
        switch (d.getStatus()) {
          case EDIT_LIST:
            edits = new ArrayList<>(d.getEdits());
            break;

          case DISABLED:
            break;

          case ERROR:
            intralineFailure = true;
            break;

          case TIMEOUT:
            intralineTimeout = true;
            break;
        }
      } else {
        intralineFailure = true;
      }
    }

    correctForDifferencesInNewlineAtEnd(a, b);

    if (comments != null) {
      ensureCommentsVisible(comments);
    }

    boolean hugeFile = false;
    if (a.src == b.src && a.size() <= context && content.getEdits().isEmpty()) {
      // Odd special case; the files are identical (100% rename or copy)
      // and the user has asked for context that is larger than the file.
      // Send them the entire file, with an empty edit after the last line.
      //
      for (int i = 0; i < a.size(); i++) {
        a.addLine(i);
      }
      edits = new ArrayList<>(1);
      edits.add(new Edit(a.size(), a.size()));

    } else {
      if (BIG_FILE < Math.max(a.size(), b.size())) {
        // IF the file is really large, we disable things to avoid choking
        // the browser client.
        //
        hugeFile = true;
      }

      // In order to expand the skipped common lines or syntax highlight the
      // file properly we need to give the client the complete file contents.
      // So force our context temporarily to the complete file size.
      //
      context = MAX_CONTEXT;

      packContent(a, b, diffPrefs.ignoreWhitespace != Whitespace.IGNORE_NONE);
    }

    return new PatchScript(
        change.getKey(),
        content.getChangeType(),
        content.getOldName(),
        content.getNewName(),
        a.fileMode,
        b.fileMode,
        content.getHeaderLines(),
        diffPrefs,
        a.dst,
        b.dst,
        edits,
        editsDueToRebase,
        a.displayMethod,
        b.displayMethod,
        a.mimeType.toString(),
        b.mimeType.toString(),
        comments,
        history,
        hugeFile,
        intralineFailure,
        intralineTimeout,
        content.getPatchType() == Patch.PatchType.BINARY,
        a.treeId == null ? null : a.treeId.getName(),
        b.treeId == null ? null : b.treeId.getName());
  }

  private static boolean isModify(PatchScriptBuilderInput content) {
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

  private static String oldName(PatchScriptBuilderInput entry) {
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

  private static String newName(PatchScriptBuilderInput entry) {
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

  private void correctForDifferencesInNewlineAtEnd(PatchSide a, PatchSide b) {
    // a.src.size() is the size ignoring a newline at the end whereas a.size() considers it.
    int aSize = a.src.size();
    int bSize = b.src.size();

    if (edits.isEmpty() && (aSize == 0 || bSize == 0)) {
      // The diff was requested for a file which was either added or deleted but which JGit doesn't
      // consider a file addition/deletion (e.g. requesting a diff for the old file name of a
      // renamed file looks like a deletion).
      return;
    }

    Optional<Edit> lastEdit = getLast(edits);
    if (isNewlineAtEndDeleted(a, b)) {
      Optional<Edit> lastLineEdit = lastEdit.filter(edit -> edit.getEndA() == aSize);
      if (lastLineEdit.isPresent()) {
        lastLineEdit.get().extendA();
      } else {
        Edit newlineEdit = new Edit(aSize, aSize + 1, bSize, bSize);
        edits.add(newlineEdit);
      }
    } else if (isNewlineAtEndAdded(a, b)) {
      Optional<Edit> lastLineEdit = lastEdit.filter(edit -> edit.getEndB() == bSize);
      if (lastLineEdit.isPresent()) {
        lastLineEdit.get().extendB();
      } else {
        Edit newlineEdit = new Edit(aSize, aSize, bSize, bSize + 1);
        edits.add(newlineEdit);
      }
    }
  }

  private static <T> Optional<T> getLast(List<T> list) {
    return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(list.size() - 1));
  }

  private boolean isNewlineAtEndDeleted(PatchSide a, PatchSide b) {
    return !a.src.isMissingNewlineAtEnd() && b.src.isMissingNewlineAtEnd();
  }

  private boolean isNewlineAtEndAdded(PatchSide a, PatchSide b) {
    return a.src.isMissingNewlineAtEnd() && !b.src.isMissingNewlineAtEnd();
  }

  private void ensureCommentsVisible(CommentDetail comments) {
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
    final List<Edit> empty = new ArrayList<>();
    int lastLine;

    lastLine = -1;
    for (Comment c : comments.getCommentsA()) {
      final int a = c.lineNbr;
      if (lastLine != a) {
        final int b = mapA2B(a - 1);
        if (0 <= b) {
          safeAdd(empty, new Edit(a - 1, b));
        }
        lastLine = a;
      }
    }

    lastLine = -1;
    for (Comment c : comments.getCommentsB()) {
      int b = c.lineNbr;
      if (lastLine != b) {
        final int a = mapB2A(b - 1);
        if (0 <= a) {
          safeAdd(empty, new Edit(a, b - 1));
        }
        lastLine = b;
      }
    }

    // Sort the final list by the index in A, so packContent can combine
    // them correctly later.
    //
    edits.addAll(empty);
    edits.sort(EDIT_SORT);
  }

  private void safeAdd(List<Edit> empty, Edit toAdd) {
    final int a = toAdd.getBeginA();
    final int b = toAdd.getBeginB();
    for (Edit e : edits) {
      if (e.getBeginA() <= a && a <= e.getEndA()) {
        return;
      }
      if (e.getBeginB() <= b && b <= e.getEndB()) {
        return;
      }
    }
    empty.add(toAdd);
  }

  private int mapA2B(int a) {
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

  private int mapB2A(int b) {
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

  private void packContent(PatchSide a, PatchSide b, boolean ignoredWhitespace) {
    EditList list = new EditList(edits, context, a.size(), b.size());
    for (EditList.Hunk hunk : list.getHunks()) {
      while (hunk.next()) {
        if (hunk.isContextLine()) {
          String lineA = a.getSourceLine(hunk.getCurA());
          a.dst.addLine(hunk.getCurA(), lineA);

          if (ignoredWhitespace) {
            // If we ignored whitespace in some form, also get the line
            // from b when it does not exactly match the line from a.
            //
            String lineB = b.getSourceLine(hunk.getCurB());
            if (!lineA.equals(lineB)) {
              b.dst.addLine(hunk.getCurB(), lineB);
            }
          }
          hunk.incBoth();
          continue;
        }

        if (hunk.isDeletedA()) {
          a.addLine(hunk.getCurA());
          hunk.incA();
        }

        if (hunk.isInsertedB()) {
          b.addLine(hunk.getCurB());
          hunk.incB();
        }
      }
    }
  }

  static class PatchSide {

    final ObjectId treeId;
    final String path;
    final ObjectId id;
    final FileMode mode;
    final byte[] srcContent;
    final Text src;
    final MimeType mimeType;
    final DisplayMethod displayMethod;
    final PatchScript.FileMode fileMode;
    final SparseFileContent dst;

    PatchSide(
        ObjectId treeId,
        String path,
        ObjectId id,
        FileMode mode,
        byte[] srcContent,
        Text src,
        MimeType mimeType,
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
      dst = new SparseFileContent();
      dst.setSize(size());
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

    void addLine(int lineNumber) {
      String lineContent = getSourceLine(lineNumber);
      dst.addLine(lineNumber, lineContent);
    }

    String getSourceLine(int lineNumber) {
      return lineNumber >= src.size() ? "" : src.getString(lineNumber);
    }
  }

  interface SidesResolver {

    ResolvedSides resolveSides(FileTypeRegistry ftr, String oldName, String newName)
        throws IOException;
  }

  static class ResolvedSides {

    final PatchSide a;
    final PatchSide b;

    public ResolvedSides(PatchSide a, PatchSide b) {
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
    public ResolvedSides resolveSides(FileTypeRegistry ftr, String oldName, String newName)
        throws IOException {
      try (ObjectReader reader = db.newObjectReader()) {
        PatchSide a = resolve(ftr, reader, oldName, null, aId, true);
        PatchSide b =
            resolve(
                ftr, reader, newName, a, bId,
                false); // Is it possible to have Object.equals(aId, bId) == true
        return new ResolvedSides(a, b);
      }
    }

    PatchSide resolve(
        final FileTypeRegistry registry,
        final ObjectReader reader,
        final String path,
        final PatchSide other,
        final ObjectId within,
        final boolean isLeftSide)
        throws IOException {
      try {
        boolean isCommitMsg = Patch.COMMIT_MSG.equals(path);
        boolean isMergeList = Patch.MERGE_LIST.equals(path);
        if (isCommitMsg || isMergeList) {
          if (comparisonType.isAgainstParentOrAutoMerge() && isLeftSide) {
            return createSide(
                within,
                path,
                ObjectId.zeroId(),
                FileMode.MISSING,
                Text.NO_BYTES,
                Text.EMPTY,
                MimeUtil2.UNKNOWN_MIME_TYPE,
                DisplayMethod.NONE,
                false);
          } else {
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
                MimeUtil2.UNKNOWN_MIME_TYPE,
                displayMethod,
                false);
          }
        } else {
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
          MimeType mimeType = MimeUtil2.UNKNOWN_MIME_TYPE;
          DisplayMethod displayMethod = DisplayMethod.DIFF;
          if (reuse) {
            mimeType = other.mimeType;
            displayMethod = other.displayMethod;
            src = other.src;

          } else if (srcContent.length > 0 && FileMode.SYMLINK != mode) {
            mimeType = registry.getMimeType(path, srcContent);
            if ("image".equals(mimeType.getMediaType()) && registry.isSafeInline(mimeType)) {
              displayMethod = DisplayMethod.IMG;
            }
          }
          return createSide(
              within, path, id, mode, srcContent, src, mimeType, displayMethod, reuse);
        }
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
        MimeType mimeType,
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
        throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
            IOException {
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

    public static final IntraLineDiffCalculatorResult NO_RESULT =
        new IntraLineDiffCalculatorResult(null, false, false);
    public static final IntraLineDiffCalculatorResult FAILURE =
        new IntraLineDiffCalculatorResult(null, true, false);
    public static final IntraLineDiffCalculatorResult TIMEOUT =
        new IntraLineDiffCalculatorResult(null, false, true);

    public final boolean failure;
    public final boolean timeout;
    public final List<Edit> edits;

    public IntraLineDiffCalculatorResult(List<Edit> edits, boolean failure, boolean timeout) {
      this.failure = failure;
      this.timeout = timeout;
      this.edits = edits;
    }
  }

  interface IntraLineDiffCalculator {

    IntraLineDiffCalculatorResult calculateIntraLineDiff(
        PatchSide a, PatchSide b, List<Edit> edits, Set<Edit> editsDueToRebase);
  }

  interface PatchScriptBuilderInput {

    List<Edit> getEdits();

    ImmutableSet<Edit> getEditsDueToRebase();

    List<String> getHeaderLines();

    String getNewName();

    String getOldName();

    ChangeType getChangeType();

    Patch.PatchType getPatchType();
  }
}
