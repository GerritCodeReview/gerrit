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

package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchScript.DisplayMethod;
import com.google.gerrit.prettify.common.EditList;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.server.FileTypeRegistry;
import com.google.gerrit.server.patch.IntraLineDiff;
import com.google.gerrit.server.patch.IntraLineDiffKey;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.Text;
import com.google.inject.Inject;

import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil2;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class PatchScriptBuilder {
  static final int MAX_CONTEXT = 5000000;
  static final int BIG_FILE = 9000;

  private static final Comparator<Edit> EDIT_SORT = new Comparator<Edit>() {
    @Override
    public int compare(final Edit o1, final Edit o2) {
      return o1.getBeginA() - o2.getBeginA();
    }
  };

  private Repository db;
  private Project.NameKey projectKey;
  private ObjectReader reader;
  private Change change;
  private AccountDiffPreference diffPrefs;
  private boolean againstParent;
  private ObjectId aId;
  private ObjectId bId;

  private final Side a;
  private final Side b;

  private List<Edit> edits;
  private final FileTypeRegistry registry;
  private final PatchListCache patchListCache;
  private int context;

  @Inject
  PatchScriptBuilder(final FileTypeRegistry ftr, final PatchListCache plc) {
    a = new Side();
    b = new Side();
    registry = ftr;
    patchListCache = plc;
  }

  void setRepository(Repository r, Project.NameKey projectKey) {
    this.db = r;
    this.projectKey = projectKey;
  }

  void setChange(final Change c) {
    this.change = c;
  }

  void setDiffPrefs(final AccountDiffPreference dp) {
    diffPrefs = dp;

    context = diffPrefs.getContext();
    if (context == AccountDiffPreference.WHOLE_FILE_CONTEXT) {
      context = MAX_CONTEXT;
    } else if (context > MAX_CONTEXT) {
      context = MAX_CONTEXT;
    }
  }

  void setTrees(final boolean ap, final ObjectId a, final ObjectId b) {
    againstParent = ap;
    aId = a;
    bId = b;
  }

  PatchScript toPatchScript(final PatchListEntry content,
      final CommentDetail comments, final List<Patch> history)
      throws IOException {
    reader = db.newObjectReader();
    try {
      return build(content, comments, history);
    } finally {
      reader.release();
    }
  }

  private PatchScript build(final PatchListEntry content,
      final CommentDetail comments, final List<Patch> history)
      throws IOException {
    boolean intralineDifferenceIsPossible = true;
    boolean intralineFailure = false;

    a.path = oldName(content);
    b.path = newName(content);

    a.resolve(null, aId);
    b.resolve(a, bId);

    edits = new ArrayList<Edit>(content.getEdits());

    if (!isModify(content)) {
      intralineDifferenceIsPossible = false;
    } else if (diffPrefs.isIntralineDifference()) {
      IntraLineDiff d =
          patchListCache.getIntraLineDiff(new IntraLineDiffKey(a.id, a.src,
              b.id, b.src, edits, projectKey, bId, b.path,
              diffPrefs.getIgnoreWhitespace() != Whitespace.IGNORE_NONE));
      if (d != null) {
        switch (d.getStatus()) {
          case EDIT_LIST:
            edits = new ArrayList<Edit>(d.getEdits());
            break;

          case DISABLED:
            intralineDifferenceIsPossible = false;
            break;

          case ERROR:
          case TIMEOUT:
            intralineDifferenceIsPossible = false;
            intralineFailure = true;
            break;
        }
      } else {
        intralineDifferenceIsPossible = false;
        intralineFailure = true;
      }
    }

    ensureCommentsVisible(comments);

    boolean hugeFile = false;
    if (a.mode == FileMode.GITLINK || b.mode == FileMode.GITLINK) {

    } else if (a.src == b.src && a.size() <= context
        && content.getEdits().isEmpty()) {
      // Odd special case; the files are identical (100% rename or copy)
      // and the user has asked for context that is larger than the file.
      // Send them the entire file, with an empty edit after the last line.
      //
      for (int i = 0; i < a.size(); i++) {
        a.addLine(i);
      }
      edits = new ArrayList<Edit>(1);
      edits.add(new Edit(a.size(), a.size()));

    } else {
      if (BIG_FILE < Math.max(a.size(), b.size())) {
        // IF the file is really large, we disable things to avoid choking
        // the browser client.
        //
        diffPrefs.setContext((short) Math.min(25, context));
        diffPrefs.setSyntaxHighlighting(false);
        context = diffPrefs.getContext();
        hugeFile = true;

      } else {
        // In order to expand the skipped common lines or syntax highlight the
        // file properly we need to give the client the complete file contents.
        // So force our context temporarily to the complete file size.
        //
        context = MAX_CONTEXT;
      }
      packContent(diffPrefs.getIgnoreWhitespace() != Whitespace.IGNORE_NONE);
    }

    return new PatchScript(change.getKey(), content.getChangeType(),
        content.getOldName(), content.getNewName(), a.fileMode, b.fileMode,
        content.getHeaderLines(), diffPrefs, a.dst, b.dst, edits,
        a.displayMethod, b.displayMethod, comments, history, hugeFile,
        intralineDifferenceIsPossible, intralineFailure);
  }

  private static boolean isModify(PatchListEntry content) {
    switch (content.getChangeType()) {
      case MODIFIED:
      case COPIED:
      case RENAMED:
        return true;

      case ADDED:
      case DELETED:
      default:
        return false;
    }
  }

  private static String oldName(final PatchListEntry entry) {
    switch (entry.getChangeType()) {
      case ADDED:
        return null;
      case DELETED:
      case MODIFIED:
        return entry.getNewName();
      case COPIED:
      case RENAMED:
      default:
        return entry.getOldName();
    }
  }

  private static String newName(final PatchListEntry entry) {
    switch (entry.getChangeType()) {
      case DELETED:
        return null;
      case ADDED:
      case MODIFIED:
      case COPIED:
      case RENAMED:
      default:
        return entry.getNewName();
    }
  }

  private void ensureCommentsVisible(final CommentDetail comments) {
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
    final List<Edit> empty = new ArrayList<Edit>();
    int lastLine;

    lastLine = -1;
    for (PatchLineComment plc : comments.getCommentsA()) {
      final int a = plc.getLine();
      if (lastLine != a) {
        final int b = mapA2B(a - 1);
        if (0 <= b) {
          safeAdd(empty, new Edit(a - 1, b));
        }
        lastLine = a;
      }
    }

    lastLine = -1;
    for (PatchLineComment plc : comments.getCommentsB()) {
      final int b = plc.getLine();
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
    Collections.sort(edits, EDIT_SORT);
  }

  private void safeAdd(final List<Edit> empty, final Edit toAdd) {
    final int a = toAdd.getBeginA();
    final int b = toAdd.getBeginB();
    for (final Edit e : edits) {
      if (e.getBeginA() <= a && a <= e.getEndA()) {
        return;
      }
      if (e.getBeginB() <= b && b <= e.getEndB()) {
        return;
      }
    }
    empty.add(toAdd);
  }

  private int mapA2B(final int a) {
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
    return last.getBeginB() + (a - last.getEndA());
  }

  private int mapB2A(final int b) {
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
    return last.getBeginA() + (b - last.getEndB());
  }

  private void packContent(boolean ignoredWhitespace) {
    EditList list = new EditList(edits, context, a.size(), b.size());
    for (final EditList.Hunk hunk : list.getHunks()) {
      while (hunk.next()) {
        if (hunk.isContextLine()) {
          final String lineA = a.src.getString(hunk.getCurA());
          a.dst.addLine(hunk.getCurA(), lineA);

          if (ignoredWhitespace) {
            // If we ignored whitespace in some form, also get the line
            // from b when it does not exactly match the line from a.
            //
            final String lineB = b.src.getString(hunk.getCurB());
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

  private class Side {
    String path;
    ObjectId id;
    FileMode mode;
    byte[] srcContent;
    Text src;
    MimeType mimeType = MimeUtil2.UNKNOWN_MIME_TYPE;
    DisplayMethod displayMethod = DisplayMethod.DIFF;
    PatchScript.FileMode fileMode = PatchScript.FileMode.FILE;
    final SparseFileContent dst = new SparseFileContent();

    int size() {
      return src != null ? src.size() : 0;
    }

    void addLine(int line) {
      dst.addLine(line, src.getString(line));
    }

    void resolve(final Side other, final ObjectId within) throws IOException {
      try {
        final boolean reuse;
        if (Patch.COMMIT_MSG.equals(path)) {
          if (againstParent && (aId == within || within.equals(aId))) {
            id = ObjectId.zeroId();
            src = Text.EMPTY;
            srcContent = Text.NO_BYTES;
            mode = FileMode.MISSING;
            displayMethod = DisplayMethod.NONE;
          } else {
            id = within;
            src = Text.forCommit(db, reader, within);
            srcContent = src.getContent();
            if (src == Text.EMPTY) {
              mode = FileMode.MISSING;
              displayMethod = DisplayMethod.NONE;
            } else {
              mode = FileMode.REGULAR_FILE;
            }
          }
          reuse = false;

        } else {
          final TreeWalk tw = find(within);

          id = tw != null ? tw.getObjectId(0) : ObjectId.zeroId();
          mode = tw != null ? tw.getFileMode(0) : FileMode.MISSING;
          reuse = other != null && other.id.equals(id) && other.mode == mode;

          if (reuse) {
            srcContent = other.srcContent;

          } else if (mode.getObjectType() == Constants.OBJ_BLOB) {
            srcContent = Text.asByteArray(db.open(id, Constants.OBJ_BLOB));

          } else {
            srcContent = Text.NO_BYTES;
          }

          if (reuse) {
            mimeType = other.mimeType;
            displayMethod = other.displayMethod;
            src = other.src;

          } else if (srcContent.length > 0 && FileMode.SYMLINK != mode) {
            mimeType = registry.getMimeType(path, srcContent);
            if ("image".equals(mimeType.getMediaType())
                && registry.isSafeInline(mimeType)) {
              displayMethod = DisplayMethod.IMG;
            }
          }
        }

        if (mode == FileMode.MISSING) {
          displayMethod = DisplayMethod.NONE;
        }

        if (!reuse) {
          if (srcContent == Text.NO_BYTES) {
            src = Text.EMPTY;
          } else {
            src = new Text(srcContent);
          }
        }

        if (srcContent.length > 0 && srcContent[srcContent.length - 1] != '\n') {
          dst.setMissingNewlineAtEnd(true);
        }
        dst.setSize(size());
        dst.setPath(path);

        if (mode == FileMode.SYMLINK) {
          fileMode = PatchScript.FileMode.SYMLINK;
        } else if (mode == FileMode.GITLINK) {
          fileMode = PatchScript.FileMode.GITLINK;
        }
      } catch (IOException err) {
        throw new IOException("Cannot read " + within.name() + ":" + path, err);
      }
    }

    private TreeWalk find(final ObjectId within) throws MissingObjectException,
        IncorrectObjectTypeException, CorruptObjectException, IOException {
      if (path == null || within == null) {
        return null;
      }
      final RevWalk rw = new RevWalk(reader);
      final RevTree tree = rw.parseTree(within);
      return TreeWalk.forPath(reader, path, tree);
    }
  }
}
