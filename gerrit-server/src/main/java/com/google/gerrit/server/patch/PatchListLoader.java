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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

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
import org.eclipse.jgit.lib.Config;
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
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PatchListLoader implements Callable<PatchList> {
  static final Logger log = LoggerFactory.getLogger(PatchListLoader.class);

  public interface Factory {
    PatchListLoader create(PatchListKey key, Project.NameKey project);
  }

  private final GitRepositoryManager repoManager;
  private final PatchListCache patchListCache;
  private final ThreeWayMergeStrategy mergeStrategy;
  private final ExecutorService diffExecutor;
  private final PatchListKey key;
  private final Project.NameKey project;
  private final long timeoutMillis;
  private final Object lock;

  @AssistedInject
  PatchListLoader(GitRepositoryManager mgr,
      PatchListCache plc,
      @GerritServerConfig Config cfg,
      @DiffExecutor ExecutorService de,
      @Assisted PatchListKey k,
      @Assisted Project.NameKey p) {
    repoManager = mgr;
    patchListCache = plc;
    mergeStrategy = MergeUtil.getMergeStrategy(cfg);
    diffExecutor = de;
    key = k;
    project = p;
    lock = new Object();
    timeoutMillis =
        ConfigUtil.getTimeUnit(cfg, "cache", PatchListCacheImpl.FILE_NAME,
            "timeout", TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS),
            TimeUnit.MILLISECONDS);
  }

  @Override
  public PatchList call() throws IOException,
      PatchListNotAvailableException {
    try (Repository repo = repoManager.openRepository(project)) {
      return readPatchList(key, repo);
    }
  }

  private static RawTextComparator comparatorFor(Whitespace ws) {
    switch (ws) {
      case IGNORE_ALL:
        return RawTextComparator.WS_IGNORE_ALL;

      case IGNORE_TRAILING:
        return RawTextComparator.WS_IGNORE_TRAILING;

      case IGNORE_LEADING_AND_TRAILING:
        return RawTextComparator.WS_IGNORE_CHANGE;

      case IGNORE_NONE:
      default:
        return RawTextComparator.DEFAULT;
    }
  }

  private PatchList readPatchList(final PatchListKey key, final Repository repo)
      throws IOException, PatchListNotAvailableException {
    final RawTextComparator cmp = comparatorFor(key.getWhitespace());
    try (ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader);
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      final RevCommit b = rw.parseCommit(key.getNewId());
      final RevObject a = aFor(key, repo, rw, b);

      if (a == null) {
        // TODO(sop) Remove this case.
        // This is a merge commit, compared to its ancestor.
        //
        final PatchListEntry[] entries = new PatchListEntry[1];
        entries[0] = newCommitMessage(cmp, reader, null, b);
        return new PatchList(a, b, true, entries);
      }

      final boolean againstParent =
          b.getParentCount() > 0 && b.getParent(0) == a;

      RevCommit aCommit = a instanceof RevCommit ? (RevCommit) a : null;
      RevTree aTree = rw.parseTree(a);
      RevTree bTree = b.getTree();

      df.setRepository(repo);
      df.setDiffComparator(cmp);
      df.setDetectRenames(true);
      List<DiffEntry> diffEntries = df.scan(aTree, bTree);

      Set<String> paths = null;
      if (key.getOldId() != null) {
        PatchListKey newKey =
            new PatchListKey(null, key.getNewId(), key.getWhitespace());
        PatchListKey oldKey =
            new PatchListKey(null, key.getOldId(), key.getWhitespace());
        paths = FluentIterable
            .from(patchListCache.get(newKey, project).getPatches())
            .append(patchListCache.get(oldKey, project).getPatches())
            .transform(new Function<PatchListEntry, String>() {
              @Override
              public String apply(PatchListEntry entry) {
                return entry.getNewName();
              }
            })
            .toSet();
      }

      int cnt = diffEntries.size();
      List<PatchListEntry> entries = new ArrayList<>();
      entries.add(newCommitMessage(cmp, reader,
          againstParent ? null : aCommit, b));
      for (int i = 0; i < cnt; i++) {
        DiffEntry e = diffEntries.get(i);
        if (paths == null || paths.contains(e.getNewPath())
            || paths.contains(e.getOldPath())) {

          FileHeader fh = toFileHeader(key, df, e);
          long oldSize =
              getFileSize(repo, reader, e.getOldMode(), e.getOldPath(), aTree);
          long newSize =
              getFileSize(repo, reader, e.getNewMode(), e.getNewPath(), bTree);
          entries.add(newEntry(aTree, fh, newSize - oldSize));
        }
      }
      return new PatchList(a, b, againstParent,
          entries.toArray(new PatchListEntry[entries.size()]));
    }
  }

  private static long getFileSize(Repository repo, ObjectReader reader,
      FileMode mode, String path, RevTree t) throws IOException {
    if (!isBlob(mode)) {
      return 0;
    }
    try (TreeWalk tw = TreeWalk.forPath(reader, path, t)) {
      return tw != null
          ? repo.open(tw.getObjectId(0), OBJ_BLOB).getSize()
          : 0;
    }
  }

  private static boolean isBlob(FileMode mode) {
    int t = mode.getBits() & FileMode.TYPE_MASK;
    return t == FileMode.TYPE_FILE || t == FileMode.TYPE_SYMLINK;
  }

  private FileHeader toFileHeader(PatchListKey key,
      final DiffFormatter diffFormatter, final DiffEntry diffEntry)
      throws IOException {

    Future<FileHeader> result = diffExecutor.submit(new Callable<FileHeader>() {
      @Override
      public FileHeader call() throws IOException {
        synchronized (lock) {
          return diffFormatter.toFileHeader(diffEntry);
        }
      }
    });

    try {
      return result.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | TimeoutException e) {
      log.warn(timeoutMillis + " ms timeout reached for Diff loader"
                      + " in project " + project
                      + " on commit " + key.getNewId().name()
                      + " on path " + diffEntry.getNewPath()
                      + " comparing " + diffEntry.getOldId().name()
                      + ".." + diffEntry.getNewId().name());
      result.cancel(true);
      synchronized (lock) {
        return toFileHeaderWithoutMyersDiff(diffFormatter, diffEntry);
      }
    } catch (ExecutionException e) {
      // If there was an error computing the result, carry it
      // up to the caller so the cache knows this key is invalid.
      Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
      throw new IOException(e.getMessage(), e.getCause());
    }
  }

  private FileHeader toFileHeaderWithoutMyersDiff(DiffFormatter diffFormatter,
      DiffEntry diffEntry) throws IOException {
    HistogramDiff histogramDiff = new HistogramDiff();
    histogramDiff.setFallbackAlgorithm(null);
    diffFormatter.setDiffAlgorithm(histogramDiff);
    return diffFormatter.toFileHeader(diffEntry);
  }

  private PatchListEntry newCommitMessage(final RawTextComparator cmp,
      final ObjectReader reader,
      final RevCommit aCommit, final RevCommit bCommit) throws IOException {
    StringBuilder hdr = new StringBuilder();

    hdr.append("diff --git");
    if (aCommit != null) {
      hdr.append(" a/").append(Patch.COMMIT_MSG);
    } else {
      hdr.append(" ").append(FileHeader.DEV_NULL);
    }
    hdr.append(" b/").append(Patch.COMMIT_MSG);
    hdr.append("\n");

    if (aCommit != null) {
      hdr.append("--- a/").append(Patch.COMMIT_MSG).append("\n");
    } else {
      hdr.append("--- ").append(FileHeader.DEV_NULL).append("\n");
    }
    hdr.append("+++ b/").append(Patch.COMMIT_MSG).append("\n");

    Text aText =
        aCommit != null ? Text.forCommit(reader, aCommit) : Text.EMPTY;
    Text bText = Text.forCommit(reader, bCommit);

    byte[] rawHdr = hdr.toString().getBytes(UTF_8);
    byte[] aContent = aText.getContent();
    byte[] bContent = bText.getContent();
    long sizeDelta = bContent.length - aContent.length;
    RawText aRawText = new RawText(aContent);
    RawText bRawText = new RawText(bContent);
    EditList edits = new HistogramDiff().diff(cmp, aRawText, bRawText);
    FileHeader fh = new FileHeader(rawHdr, edits, PatchType.UNIFIED);
    return new PatchListEntry(fh, edits, sizeDelta);
  }

  private PatchListEntry newEntry(RevTree aTree, FileHeader fileHeader,
      long sizeDelta) {
    if (aTree == null // want combined diff
        || fileHeader.getPatchType() != PatchType.UNIFIED
        || fileHeader.getHunks().isEmpty()) {
      return new PatchListEntry(fileHeader, Collections.<Edit> emptyList(),
          sizeDelta);
    }

    List<Edit> edits = fileHeader.toEditList();
    if (edits.isEmpty()) {
      return new PatchListEntry(fileHeader, Collections.<Edit> emptyList(),
          sizeDelta);
    } else {
      return new PatchListEntry(fileHeader, edits, sizeDelta);
    }
  }

  private RevObject aFor(final PatchListKey key,
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
        return automerge(repo, rw, b, mergeStrategy);
      default:
        // TODO(sop) handle an octopus merge.
        return null;
    }
  }

  public static RevTree automerge(Repository repo, RevWalk rw, RevCommit b,
      ThreeWayMergeStrategy mergeStrategy) throws IOException {
    return automerge(repo, rw, b, mergeStrategy, true);
  }

  public static RevTree automerge(Repository repo, RevWalk rw, RevCommit b,
      ThreeWayMergeStrategy mergeStrategy, boolean save) throws IOException {
    String hash = b.name();
    String refName = RefNames.REFS_CACHE_AUTOMERGE
        + hash.substring(0, 2)
        + "/"
        + hash.substring(2);
    Ref ref = repo.getRefDatabase().exactRef(refName);
    if (ref != null && ref.getObjectId() != null) {
      return rw.parseTree(ref.getObjectId());
    }

    ResolveMerger m = (ResolveMerger) mergeStrategy.newMerger(repo, true);
    try (ObjectInserter ins = repo.newObjectInserter()) {
      DirCache dc = DirCache.newInCore();
      m.setDirCache(dc);
      m.setObjectInserter(new ObjectInserter.Filter() {
        @Override
        protected ObjectInserter delegate() {
          return ins;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
      });

      boolean couldMerge;
      try {
        couldMerge = m.merge(b.getParents());
      } catch (IOException e) {
        // It is not safe to continue further down in this method as throwing
        // an exception most likely means that the merge tree was not created
        // and m.getMergeResults() is empty. This would mean that all paths are
        // unmerged and Gerrit UI would show all paths in the patch list.
        log.warn("Error attempting automerge " + refName, e);
        return null;
      }

      ObjectId treeId;
      if (couldMerge) {
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
        Map<String, ObjectId> resolved = new HashMap<>();
        for (Map.Entry<String, MergeResult<? extends Sequence>> entry : r.entrySet()) {
          MergeResult<? extends Sequence> p = entry.getValue();
          try (TemporaryBuffer buf =
              new TemporaryBuffer.LocalFile(null, 10 * 1024 * 1024)) {
            fmt.formatMerge(buf, p, "BASE", oursName, theirsName, UTF_8.name());
            buf.close();

            try (InputStream in = buf.openInputStream()) {
              resolved.put(entry.getKey(), ins.insert(Constants.OBJ_BLOB, buf.length(), in));
            }
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

      if (save) {
        RefUpdate update = repo.updateRef(refName);
        update.setNewObjectId(treeId);
        update.disableRefLog();
        update.forceUpdate();
      }

      return rw.lookupTree(treeId);
    }
  }

  private static ObjectId emptyTree(final Repository repo) throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId id = oi.insert(Constants.OBJ_TREE, new byte[] {});
      oi.flush();
      return id;
    }
  }
}
