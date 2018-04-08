// Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeFormatter;
import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoMerger {
  private static final Logger log = LoggerFactory.getLogger(AutoMerger.class);

  public static boolean cacheAutomerge(Config cfg) {
    return cfg.getBoolean("change", null, "cacheAutomerge", true);
  }

  private final PersonIdent gerritIdent;
  private final boolean save;

  @Inject
  AutoMerger(@GerritServerConfig Config cfg, @GerritPersonIdent PersonIdent gerritIdent) {
    save = cacheAutomerge(cfg);
    this.gerritIdent = gerritIdent;
  }

  /**
   * Perform an auto-merge of the parents of the given merge commit.
   *
   * @return auto-merge commit or {@code null} if an auto-merge commit couldn't be created. Headers
   *     of the returned RevCommit are parsed.
   */
  public RevCommit merge(
      Repository repo,
      RevWalk rw,
      ObjectInserter ins,
      RevCommit merge,
      ThreeWayMergeStrategy mergeStrategy)
      throws IOException {
    checkArgument(rw.getObjectReader().getCreatedFromInserter() == ins);
    InMemoryInserter tmpIns = null;
    if (ins instanceof InMemoryInserter) {
      // Caller gave us an in-memory inserter, so ensure anything we write from
      // this method is visible to them.
      tmpIns = (InMemoryInserter) ins;
    } else if (!save) {
      // If we don't plan on saving results, use a fully in-memory inserter.
      // Using just a non-flushing wrapper is not sufficient, since in
      // particular DfsInserter might try to write to storage after exceeding an
      // internal buffer size.
      tmpIns = new InMemoryInserter(rw.getObjectReader());
    }

    rw.parseHeaders(merge);
    String refName = RefNames.refsCacheAutomerge(merge.name());
    Ref ref = repo.getRefDatabase().exactRef(refName);
    if (ref != null && ref.getObjectId() != null) {
      RevObject obj = rw.parseAny(ref.getObjectId());
      if (obj instanceof RevCommit) {
        return (RevCommit) obj;
      }
      return commit(repo, rw, tmpIns, ins, refName, obj, merge);
    }

    ResolveMerger m = (ResolveMerger) mergeStrategy.newMerger(repo, true);
    DirCache dc = DirCache.newInCore();
    m.setDirCache(dc);
    m.setObjectInserter(tmpIns == null ? new NonFlushingWrapper(ins) : tmpIns);

    boolean couldMerge;
    try {
      couldMerge = m.merge(merge.getParents());
    } catch (IOException | RuntimeException e) {
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
      RevCommit ours = merge.getParent(0);
      RevCommit theirs = merge.getParent(1);
      rw.parseBody(ours);
      rw.parseBody(theirs);
      String oursMsg = ours.getShortMessage();
      String theirsMsg = theirs.getShortMessage();

      String oursName =
          String.format(
              "HEAD   (%s %s)",
              ours.abbreviate(6).name(), oursMsg.substring(0, Math.min(oursMsg.length(), 60)));
      String theirsName =
          String.format(
              "BRANCH (%s %s)",
              theirs.abbreviate(6).name(),
              theirsMsg.substring(0, Math.min(theirsMsg.length(), 60)));

      MergeFormatter fmt = new MergeFormatter();
      Map<String, MergeResult<? extends Sequence>> r = m.getMergeResults();
      Map<String, ObjectId> resolved = new HashMap<>();
      for (Map.Entry<String, MergeResult<? extends Sequence>> entry : r.entrySet()) {
        MergeResult<? extends Sequence> p = entry.getValue();
        try (TemporaryBuffer buf = new TemporaryBuffer.LocalFile(null, 10 * 1024 * 1024)) {
          fmt.formatMerge(buf, p, "BASE", oursName, theirsName, UTF_8.name());
          buf.close();

          try (InputStream in = buf.openInputStream()) {
            resolved.put(entry.getKey(), ins.insert(Constants.OBJ_BLOB, buf.length(), in));
          }
        }
      }

      DirCacheBuilder builder = dc.builder();
      int cnt = dc.getEntryCount();
      for (int i = 0; i < cnt; ) {
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

        } else {
          // 3 stage conflict, no resolve above
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

    return commit(repo, rw, tmpIns, ins, refName, treeId, merge);
  }

  private RevCommit commit(
      Repository repo,
      RevWalk rw,
      @Nullable InMemoryInserter tmpIns,
      ObjectInserter ins,
      String refName,
      ObjectId tree,
      RevCommit merge)
      throws IOException {
    rw.parseHeaders(merge);
    // For maximum stability, choose a single ident using the committer time of
    // the input commit, using the server name and timezone.
    PersonIdent ident =
        new PersonIdent(
            gerritIdent, merge.getCommitterIdent().getWhen(), gerritIdent.getTimeZone());
    CommitBuilder cb = new CommitBuilder();
    cb.setAuthor(ident);
    cb.setCommitter(ident);
    cb.setTreeId(tree);
    cb.setMessage("Auto-merge of " + merge.name() + '\n');
    for (RevCommit p : merge.getParents()) {
      cb.addParentId(p);
    }

    if (!save) {
      checkArgument(tmpIns != null);
      try (ObjectReader tmpReader = tmpIns.newReader();
          RevWalk tmpRw = new RevWalk(tmpReader)) {
        return tmpRw.parseCommit(tmpIns.insert(cb));
      }
    }

    checkArgument(tmpIns == null);
    checkArgument(!(ins instanceof InMemoryInserter));
    ObjectId commitId = ins.insert(cb);
    ins.flush();

    RefUpdate ru = repo.updateRef(refName);
    ru.setNewObjectId(commitId);
    ru.disableRefLog();
    ru.forceUpdate();
    return rw.parseCommit(commitId);
  }

  private static class NonFlushingWrapper extends ObjectInserter.Filter {
    private final ObjectInserter ins;

    private NonFlushingWrapper(ObjectInserter ins) {
      this.ins = ins;
    }

    @Override
    protected ObjectInserter delegate() {
      return ins;
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
