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

package com.google.gerrit.server.rpc.patch;

import com.google.gerrit.client.data.EditList;
import com.google.gerrit.client.data.PatchScript;
import com.google.gerrit.client.data.PatchScriptSettings;
import com.google.gerrit.client.data.SparseFileContent;
import com.google.gerrit.client.data.PatchScript.DisplayMethod;
import com.google.gerrit.client.patches.CommentDetail;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.Patch.PatchType;
import com.google.gerrit.server.FileTypeRegistry;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.Text;

import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil2;

import org.spearce.jgit.diff.Edit;
import org.spearce.jgit.errors.CorruptObjectException;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.FileMode;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectLoader;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevTree;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.treewalk.TreeWalk;

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

  private final List<String> header;
  private Repository db;
  private Change change;
  private PatchScriptSettings settings;
  private ObjectId aId;
  private ObjectId bId;

  private final Side a;
  private final Side b;

  private List<Edit> edits;
  private final FileTypeRegistry registry;

  PatchScriptBuilder(final FileTypeRegistry ftr) {
    header = new ArrayList<String>();
    a = new Side();
    b = new Side();
    registry = ftr;
  }

  void setRepository(final Repository r) {
    db = r;
  }

  void setChange(final Change c) {
    this.change = c;
  }

  void setSettings(final PatchScriptSettings s) {
    settings = s;
  }

  void setTrees(final ObjectId a, final ObjectId b) {
    aId = a;
    bId = b;
  }

  private int context() {
    return settings.getContext();
  }

  PatchScript toPatchScript(final PatchListEntry contentWS,
      final CommentDetail comments, final PatchListEntry contentAct)
      throws IOException {
    if (contentAct.getPatchType() == PatchType.N_WAY) {
      // For a diff --cc format we don't support converting it into
      // a patch script. Instead treat everything as a file header.
      //
      return new PatchScript(change.getKey(), contentAct.getHeaderLines(),
          settings, a.dst, b.dst, Collections.<Edit> emptyList(),
          a.displayMethod, b.displayMethod);
    }

    a.path = oldName(contentAct);
    b.path = newName(contentAct);

    a.resolve(null, aId);
    b.resolve(a, bId);

    edits = new ArrayList<Edit>(contentAct.getEdits());
    ensureCommentsVisible(comments);
    header.addAll(contentAct.getHeaderLines());

    if (a.mode == FileMode.GITLINK || b.mode == FileMode.GITLINK) {

    } else if (a.src == b.src && a.src.size() <= context()
        && contentAct.getEdits().isEmpty()) {
      // Odd special case; the files are identical (100% rename or copy)
      // and the user has asked for context that is larger than the file.
      // Send them the entire file, with an empty edit after the last line.
      //
      for (int i = 0; i < a.src.size(); i++) {
        a.src.addLineTo(a.dst, i);
      }
      edits = new ArrayList<Edit>(1);
      edits.add(new Edit(a.src.size(), a.src.size()));
    } else {
      if (BIG_FILE < Math.max(a.src.size(), b.src.size()) && 25 < context()) {
        settings.setContext(25);
      }
      packContent();
    }

    if (contentWS != contentAct) {
      // The edit list we used to pack the file contents doesn't honor the
      // whitespace settings requested. Instead we must rebuild our edit
      // list around the whitespace edit list.
      //
      edits = new ArrayList<Edit>(contentWS.getEdits());
      ensureCommentsVisible(comments);
    }

    return new PatchScript(change.getKey(), header, settings, a.dst, b.dst,
        edits, a.displayMethod, b.displayMethod);
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

  private void packContent() {
    EditList list = new EditList(edits, context(), a.src.size(), b.src.size());
    for (final EditList.Hunk hunk : list.getHunks()) {
      while (hunk.next()) {
        if (hunk.isContextLine()) {
          a.src.addLineTo(a.dst, hunk.getCurA());
          hunk.incBoth();

        } else if (hunk.isDeletedA()) {
          a.src.addLineTo(a.dst, hunk.getCurA());
          hunk.incA();

        } else if (hunk.isInsertedB()) {
          b.src.addLineTo(b.dst, hunk.getCurB());
          hunk.incB();
        }
      }
    }
  }

  private class Side {
    String path;
    ObjectId id;
    FileMode mode;
    Text src;
    MimeType mimeType = MimeUtil2.UNKNOWN_MIME_TYPE;
    DisplayMethod displayMethod = DisplayMethod.DIFF;
    final SparseFileContent dst = new SparseFileContent();

    void resolve(final Side other, final ObjectId within) throws IOException {
      try {
        final TreeWalk tw = find(within);

        id = tw != null ? tw.getObjectId(0) : ObjectId.zeroId();
        mode = tw != null ? tw.getFileMode(0) : FileMode.MISSING;

        final boolean reuse =
            other != null && other.id.equals(id) && other.mode == mode;

        if (reuse) {
          src = other.src;

        } else if (mode.getObjectType() == Constants.OBJ_BLOB) {
          final ObjectLoader ldr = db.openObject(id);
          if (ldr == null) {
            throw new MissingObjectException(id, Constants.TYPE_BLOB);
          }
          final byte[] data = ldr.getCachedBytes();
          if (ldr.getType() != Constants.OBJ_BLOB) {
            throw new IncorrectObjectTypeException(id, Constants.TYPE_BLOB);
          }
          src = new Text(data);

        } else {
          src = Text.EMPTY;
        }

        if (reuse) {
          mimeType = other.mimeType;
          displayMethod = other.displayMethod;

        } else if (src.getContent().length > 0 && FileMode.SYMLINK != mode) {
          mimeType = registry.getMimeType(path, src.getContent());
          if ("image".equals(mimeType.getMediaType())
              && registry.isSafeInline(mimeType)) {
            displayMethod = DisplayMethod.IMG;
          }
        }

        if (mode == FileMode.MISSING) {
          displayMethod = DisplayMethod.NONE;
        }

        dst.setMissingNewlineAtEnd(src.isMissingNewlineAtEnd());
        dst.setSize(src.size());
      } catch (IOException err) {
        throw new IOException("Cannot read " + within.name() + ":" + path, err);
      }
    }

    private TreeWalk find(final ObjectId within) throws MissingObjectException,
        IncorrectObjectTypeException, CorruptObjectException, IOException {
      if (path == null) {
        return null;
      }
      final RevWalk rw = new RevWalk(db);
      final RevTree tree = rw.parseTree(within);
      return TreeWalk.forPath(db, path, tree);
    }
  }
}
