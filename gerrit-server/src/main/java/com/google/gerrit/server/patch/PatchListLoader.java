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
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeFormatter;
import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    final RawTextComparator cmp = comparatorFor(key.getWhitespace());
    final ObjectReader reader = repo.newObjectReader();
    try {
      final RevWalk rw = new RevWalk(reader);
      final RevCommit b = rw.parseCommit(key.getNewId());
      final RevObject a = aFor(key, repo, rw, b);

      if (a == null) {
        // TODO(sop) Remove this case.
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
      case 2:
        return automerge(repo, rw, b);
      default:
        // TODO(sop) handle an octopus merge.
        return null;
    }
  }

  private static RevObject automerge(Repository repo, RevWalk rw, RevCommit b)
      throws IOException {
    String hash = b.name();
    String refName = GitRepositoryManager.REFS_CACHE_AUTOMERGE
        + hash.substring(0, 2)
        + "/"
        + hash.substring(2);
    Ref ref = repo.getRef(refName);
    if (ref != null && ref.getObjectId() != null) {
      return rw.parseTree(ref.getObjectId());
    }

    ObjectId treeId;
    ResolveMerger m = (ResolveMerger) MergeStrategy.RESOLVE.newMerger(repo, true);
    ObjectInserter ins = m.getObjectInserter();
    try {
      DirCache dc = DirCache.newInCore();
      m.setDirCache(dc);

      if (m.merge(b.getParents())) {
        treeId = m.getResultTreeId();

      } else {
        RevCommit ours = b.getParent(0);
        RevCommit theirs = b.getParent(1);
        rw.parseBody(ours);
        rw.parseBody(theirs);
        String oursMsg = ours.getShortMessage();
        String theirsMsg = theirs.getShortMessage();

        String oursName = String.format("HEAD   (%s %s)",
            ours.abbreviate(6).name(),
            oursMsg.substring(0, Math.min(oursMsg.length(), 60)));
        String theirsName = String.format("BRANCH (%s %s)",
            theirs.abbreviate(6).name(),
            theirsMsg.substring(0, Math.min(theirsMsg.length(), 60)));

        MergeFormatter fmt = new MergeFormatter();
        Map<String, MergeResult<? extends Sequence>> r = m.getMergeResults();
        Map<String, ObjectId> resolved = new HashMap<String, ObjectId>();
        for (String path : r.keySet()) {
          MergeResult<? extends Sequence> p = r.get(path);
          TemporaryBuffer buf = new TemporaryBuffer.LocalFile(10 * 1024 * 1024);
          try {
            fmt.formatMerge(buf, p, "BASE", oursName, theirsName, "UTF-8");
            buf.close();

            InputStream in = buf.openInputStream();
            try {
              resolved.put(path, ins.insert(Constants.OBJ_BLOB, buf.length(), in));
            } finally {
              in.close();
            }
          } finally {
            buf.destroy();
          }
        }

        DirCacheBuilder builder = dc.builder();
        int cnt = dc.getEntryCount();
        for (int i = 0; i < cnt;) {
          DirCacheEntry entry = dc.getEntry(i);
          if (entry.getStage() == 0) {
            builder.add(entry);
            i++;
            continue;
          }

          int next = dc.nextEntry(i);
          String path = entry.getPathString();
          DirCacheEntry res = new DirCacheEntry(path);
          if (resolved.containsKey(path)) {
            // For a file with content merge conflict that we produced a result
            // above on, collapse the file down to a single stage 0 with just
            // the blob content, and a randomly selected mode (the lowest stage,
            // which should be the merge base, or ours).
            res.setFileMode(entry.getFileMode());
            res.setObjectId(resolved.get(path));

          } else if (next == i + 1) {
            // If there is exactly one stage present, shouldn't be a conflict...
            res.setFileMode(entry.getFileMode());
            res.setObjectId(entry.getObjectId());

          } else if (next == i + 2) {
            // Two stages suggests a delete/modify conflict. Pick the higher
            // stage as the automatic result.
            entry = dc.getEntry(i + 1);
            res.setFileMode(entry.getFileMode());
            res.setObjectId(entry.getObjectId());

          } else { // 3 stage conflict, no resolve above
            // Punt on the 3-stage conflict and show the base, for now.
            res.setFileMode(entry.getFileMode());
            res.setObjectId(entry.getObjectId());
          }
          builder.add(res);
          i = next;
        }
        builder.finish();
        treeId = dc.writeTree(ins);
      }
      ins.flush();
    } finally {
      ins.release();
    }

    RefUpdate update = repo.updateRef(refName);
    update.setNewObjectId(treeId);
    update.disableRefLog();
    update.forceUpdate();
    return rw.parseTree(treeId);
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
