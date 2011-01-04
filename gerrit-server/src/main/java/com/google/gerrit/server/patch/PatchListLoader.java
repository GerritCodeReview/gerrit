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
//

package com.google.gerrit.server.patch;

import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.AccountDiffPreference.Whitespace;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

class PatchListLoader extends EntryCreator<PatchListKey, PatchList> {
  private final GitRepositoryManager repoManager;

  @Inject
  PatchListLoader(GitRepositoryManager mgr) {
    repoManager = mgr;
  }

  @Override
  public PatchList createEntry(final PatchListKey key) throws Exception {
    final Repository repo = repoManager.openRepository(key.projectKey);
    try {
      return readPatchList(key, repo);
    } finally {
      repo.close();
    }
  }

  private static RawTextComparator comparatorFor(Whitespace ws) {
    switch (ws) {
      case IGNORE_ALL_SPACE:
        return RawTextComparator.WS_IGNORE_ALL;

      case IGNORE_SPACE_AT_EOL:
        return RawTextComparator.WS_IGNORE_TRAILING;

      case IGNORE_SPACE_CHANGE:
        return RawTextComparator.WS_IGNORE_CHANGE;

      case IGNORE_NONE:
      default:
        return RawTextComparator.DEFAULT;
    }
  }

  private PatchList readPatchList(final PatchListKey key,
      final Repository repo) throws IOException {
    // TODO(jeffschu) correctly handle merge commits

    final RawTextComparator cmp = comparatorFor(key.getWhitespace());
    final ObjectReader reader = repo.newObjectReader();
    try {
      final RevWalk rw = new RevWalk(reader);
      final RevCommit b = rw.parseCommit(key.getNewId());
      final RevObject a = aFor(key, repo, rw, b);

      if (a == null) {
        // This is a merge commit, compared to its ancestor.
        //
        final PatchListEntry[] entries = new PatchListEntry[1];
        entries[0] = newCommitMessage(cmp, repo, reader, null, b);
        return new PatchList(a, b, true, entries);
      }

      final boolean againstParent =
          b.getParentCount() > 0 && b.getParent(0) == a;

      RevCommit aCommit;
      RevTree aTree;
      if (a instanceof RevCommit) {
        aCommit = (RevCommit) a;
        aTree = aCommit.getTree();
      } else if (a instanceof RevTree) {
        aCommit = null;
        aTree = (RevTree) a;
      } else {
        throw new IOException("Unexpected type: " + a.getClass());
      }

      RevTree bTree = b.getTree();

      final TreeWalk walk = new TreeWalk(reader);
      walk.reset();
      walk.setRecursive(true);
      walk.addTree(aTree);
      walk.addTree(bTree);
      walk.setFilter(TreeFilter.ANY_DIFF);

      DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
      df.setRepository(repo);
      df.setDiffComparator(cmp);
      df.setDetectRenames(true);
      List<DiffEntry> diffEntries = df.scan(aTree, bTree);

      final int cnt = diffEntries.size();
      final PatchListEntry[] entries = new PatchListEntry[1 + cnt];
      entries[0] = newCommitMessage(cmp, repo, reader, //
          againstParent ? null : aCommit, b);
      for (int i = 0; i < cnt; i++) {
        FileHeader fh = df.toFileHeader(diffEntries.get(i));
        entries[1 + i] = newEntry(aTree, fh);
      }
      return new PatchList(a, b, againstParent, entries);
    } finally {
      reader.release();
    }
  }

  private PatchListEntry newCommitMessage(final RawTextComparator cmp,
      final Repository db, final ObjectReader reader,
      final RevCommit aCommit, final RevCommit bCommit) throws IOException {
    StringBuilder hdr = new StringBuilder();

    hdr.append("diff --git");
    if (aCommit != null) {
      hdr.append(" a/" + Patch.COMMIT_MSG);
    } else {
      hdr.append(" " + FileHeader.DEV_NULL);
    }
    hdr.append(" b/" + Patch.COMMIT_MSG);
    hdr.append("\n");

    if (aCommit != null) {
      hdr.append("--- a/" + Patch.COMMIT_MSG + "\n");
    } else {
      hdr.append("--- " + FileHeader.DEV_NULL + "\n");
    }
    hdr.append("+++ b/" + Patch.COMMIT_MSG + "\n");

    Text aText =
        aCommit != null ? Text.forCommit(db, reader, aCommit) : Text.EMPTY;
    Text bText = Text.forCommit(db, reader, bCommit);

    byte[] rawHdr = hdr.toString().getBytes("UTF-8");
    RawText aRawText = new RawText(aText.getContent());
    RawText bRawText = new RawText(bText.getContent());
    EditList edits = new HistogramDiff().diff(cmp, aRawText, bRawText);
    FileHeader fh = new FileHeader(rawHdr, edits, PatchType.UNIFIED);
    return new PatchListEntry(fh, edits);
  }

  private PatchListEntry newEntry(RevTree aTree, FileHeader fileHeader) {
    final FileMode oldMode = fileHeader.getOldMode();
    final FileMode newMode = fileHeader.getNewMode();

    if (oldMode == FileMode.GITLINK || newMode == FileMode.GITLINK) {
      return new PatchListEntry(fileHeader, Collections.<Edit> emptyList());
    }

    if (aTree == null // want combined diff
        || fileHeader.getPatchType() != PatchType.UNIFIED
        || fileHeader.getHunks().isEmpty()) {
      return new PatchListEntry(fileHeader, Collections.<Edit> emptyList());
    }

    List<Edit> edits = fileHeader.toEditList();
    if (edits.isEmpty()) {
      return new PatchListEntry(fileHeader, Collections.<Edit> emptyList());
    } else {
      return new PatchListEntry(fileHeader, edits);
    }
  }

  private static RevObject aFor(final PatchListKey key,
      final Repository repo, final RevWalk rw, final RevCommit b)
      throws IOException {
    if (key.getOldId() != null) {
      return rw.parseAny(key.getOldId());
    }

    switch (b.getParentCount()) {
      case 0:
        return rw.parseAny(emptyTree(repo));
      case 1: {
        RevCommit r = b.getParent(0);
        rw.parseBody(r);
        return r;
      }
      default:
        // merge commit, return null to force combined diff behavior
        return null;
    }
  }

  private static ObjectId emptyTree(final Repository repo) throws IOException {
    ObjectInserter oi = repo.newObjectInserter();
    try {
      ObjectId id = oi.insert(Constants.OBJ_TREE, new byte[] {});
      oi.flush();
      return id;
    } finally {
      oi.release();
    }
  }
}
