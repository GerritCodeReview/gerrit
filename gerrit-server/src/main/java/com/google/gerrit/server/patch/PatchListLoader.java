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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.MergeUtil;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatchListLoader implements Callable<PatchList> {
  static final Logger log = LoggerFactory.getLogger(PatchListLoader.class);

  public interface Factory {
    PatchListLoader create(PatchListKey key, Project.NameKey project);
  }

  private final GitRepositoryManager repoManager;
  private final PatchListCache patchListCache;
  private final ThreeWayMergeStrategy mergeStrategy;
  private final ExecutorService diffExecutor;
  private final AutoMerger autoMerger;
  private final PatchListKey key;
  private final Project.NameKey project;
  private final long timeoutMillis;
  private final boolean save;

  @Inject
  PatchListLoader(
      GitRepositoryManager mgr,
      PatchListCache plc,
      @GerritServerConfig Config cfg,
      @DiffExecutor ExecutorService de,
      AutoMerger am,
      @Assisted PatchListKey k,
      @Assisted Project.NameKey p) {
    repoManager = mgr;
    patchListCache = plc;
    mergeStrategy = MergeUtil.getMergeStrategy(cfg);
    diffExecutor = de;
    autoMerger = am;
    key = k;
    project = p;
    timeoutMillis =
        ConfigUtil.getTimeUnit(
            cfg,
            "cache",
            PatchListCacheImpl.FILE_NAME,
            "timeout",
            TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS),
            TimeUnit.MILLISECONDS);
    save = AutoMerger.cacheAutomerge(cfg);
  }

  @Override
  public PatchList call() throws IOException, PatchListNotAvailableException {
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter ins = newInserter(repo);
        ObjectReader reader = ins.newReader();
        RevWalk rw = new RevWalk(reader)) {
      return readPatchList(repo, rw, ins);
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

  private ObjectInserter newInserter(Repository repo) {
    return save ? repo.newObjectInserter() : new InMemoryInserter(repo);
  }

  private PatchList readPatchList(Repository repo, RevWalk rw, ObjectInserter ins)
      throws IOException, PatchListNotAvailableException {
    ObjectReader reader = rw.getObjectReader();
    checkArgument(reader.getCreatedFromInserter() == ins);
    RawTextComparator cmp = comparatorFor(key.getWhitespace());
    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      RevCommit b = rw.parseCommit(key.getNewId());
      RevObject a = aFor(key, repo, rw, ins, b);

      if (a == null) {
        // TODO(sop) Remove this case.
        // This is an octopus merge commit which should be compared against the
        // auto-merge. However since we don't support computing the auto-merge
        // for octopus merge commits, we fall back to diffing against the first
        // parent, even though this wasn't what was requested.
        //
        ComparisonType comparisonType = ComparisonType.againstParent(1);
        PatchListEntry[] entries = new PatchListEntry[2];
        entries[0] = newCommitMessage(cmp, reader, null, b);
        entries[1] = newMergeList(cmp, reader, null, b, comparisonType);
        return new PatchList(a, b, true, comparisonType, entries);
      }

      ComparisonType comparisonType = getComparisonType(a, b);

      RevCommit aCommit = a instanceof RevCommit ? (RevCommit) a : null;
      RevTree aTree = rw.parseTree(a);
      RevTree bTree = b.getTree();

      df.setReader(reader, repo.getConfig());
      df.setDiffComparator(cmp);
      df.setDetectRenames(true);
      List<DiffEntry> diffEntries = df.scan(aTree, bTree);

      List<PatchListEntry> entries = new ArrayList<>();
      entries.add(
          newCommitMessage(
              cmp, reader, comparisonType.isAgainstParentOrAutoMerge() ? null : aCommit, b));
      boolean isMerge = b.getParentCount() > 1;
      if (isMerge) {
        entries.add(
            newMergeList(
                cmp,
                reader,
                comparisonType.isAgainstParentOrAutoMerge() ? null : aCommit,
                b,
                comparisonType));
      }
      Multimap<String, Edit> editsDueToRebasePerFilePath =
          key.getAlgorithm() == PatchListKey.Algorithm.OPTIMIZED_DIFF
              ? getEditsDueToRebasePerFilePath(aCommit, b)
              : ImmutableMultimap.of();
      for (DiffEntry diffEntry : diffEntries) {
        Set<Edit> editsDueToRebase = getEditsDueToRebase(editsDueToRebasePerFilePath, diffEntry);
        Optional<PatchListEntry> patchListEntry =
            getPatchListEntry(reader, df, diffEntry, aTree, bTree, editsDueToRebase);
        patchListEntry.ifPresent(entries::add);
      }
      return new PatchList(
          a, b, isMerge, comparisonType, entries.toArray(new PatchListEntry[entries.size()]));
    }
  }

  /**
   * Identifies the {@code Edit}s which are present between {@code commitA} and {@code commitB} due
   * to other commits in between those two. {@code Edit}s which cannot be clearly attributed to
   * those other commits (because they overlap with modifications introduced by {@code commitA} or
   * {@code commitB}) are omitted from the result. The {@code Edit}s are expressed as differences
   * between {@code treeA} of {@code commitA} and {@code treeB} of {@code commitB}.
   *
   * <p><b>Note:</b> If one of the commits is a merge commit, an empty {@code Multimap} will be
   * returned.
   *
   * <p><b>Warning:</b> This method assumes that commitA and commitB are either a parent and child
   * commit or represent two patch sets which belong to the same change. No checks are made to
   * confirm this assumption! Passing arbitrary commits to this method may lead to strange results
   * or take very long.
   *
   * <p>This logic could be expanded to arbitrary commits if the following adjustments were applied:
   *
   * <ul>
   *   <li>If {@code commitA} is an ancestor of {@code commitB} (or the other way around), {@code
   *       commitA} (or {@code commitB}) is used instead of its parent in this method.
   *   <li>Special handling for merge commits is added. If only one of them is a merge commit, the
   *       whole computation has to be done between the single parent and all parents of the merge
   *       commit. If both of them are merge commits, all combinations of parents have to be
   *       considered. Alternatively, we could decide to not support this feature for merge commits
   *       (or just for specific types of merge commits).
   * </ul>
   *
   * @param commitA the commit defining {@code treeA}
   * @param commitB the commit defining {@code treeB}
   * @return the {@code Edit}s per file path they modify in {@code treeB}
   * @throws PatchListNotAvailableException if the {@code Edit}s can't be identified
   */
  private Multimap<String, Edit> getEditsDueToRebasePerFilePath(
      RevCommit commitA, RevCommit commitB) throws PatchListNotAvailableException {
    if (!areSingleParentCommitsWithOthersInBetween(commitA, commitB)) {
      return ImmutableMultimap.of();
    }

    PatchListKey parentDiffKey =
        PatchListKey.againstCommitWithPureTreeDiff(
            commitA.getParent(0), commitB.getParent(0), key.getWhitespace());
    PatchList parentPatchList = patchListCache.get(parentDiffKey, project);
    PatchListKey oldKey = PatchListKey.againstDefaultBase(key.getOldId(), key.getWhitespace());
    PatchList oldPatchList = patchListCache.get(oldKey, project);
    PatchListKey newKey = PatchListKey.againstDefaultBase(key.getNewId(), key.getWhitespace());
    PatchList newPatchList = patchListCache.get(newKey, project);

    EditTransformer editTransformer = new EditTransformer(parentPatchList.getPatches());
    editTransformer.transformReferencesOfSideA(oldPatchList.getPatches());
    editTransformer.transformReferencesOfSideB(newPatchList.getPatches());
    return editTransformer.getEditsPerFilePath();
  }

  private boolean areSingleParentCommitsWithOthersInBetween(RevCommit commitA, RevCommit commitB) {
    return key.getOldId() != null
        && commitB.getParentCount() == 1
        && commitA != null
        && commitA.getParentCount() == 1
        && !ObjectId.equals(commitB.getParent(0), commitA.getParent(0))
        && !ObjectId.equals(commitB, commitA.getParent(0))
        && !ObjectId.equals(commitB.getParent(0), commitA);
  }

  private static Set<Edit> getEditsDueToRebase(
      Multimap<String, Edit> editsDueToRebasePerFilePath, DiffEntry diffEntry) {
    if (editsDueToRebasePerFilePath.isEmpty()) {
      return ImmutableSet.of();
    }

    String filePath = diffEntry.getNewPath();
    if (diffEntry.getChangeType() == ChangeType.DELETE) {
      filePath = diffEntry.getOldPath();
    }
    return ImmutableSet.copyOf(editsDueToRebasePerFilePath.get(filePath));
  }

  private Optional<PatchListEntry> getPatchListEntry(
      ObjectReader objectReader,
      DiffFormatter diffFormatter,
      DiffEntry diffEntry,
      RevTree treeA,
      RevTree treeB,
      Set<Edit> editsDueToRebase)
      throws IOException {
    FileHeader fileHeader = toFileHeader(key, diffFormatter, diffEntry);
    long oldSize = getFileSize(objectReader, diffEntry.getOldMode(), diffEntry.getOldPath(), treeA);
    long newSize = getFileSize(objectReader, diffEntry.getNewMode(), diffEntry.getNewPath(), treeB);
    PatchListEntry patchListEntry =
        newEntry(treeA, fileHeader, editsDueToRebase, newSize, newSize - oldSize);
    // All edits in a file are due to rebase -> exclude the file from the diff.
    if (patchListEntry.getEditsDueToRebase().containsAll(patchListEntry.getEdits())) {
      return Optional.empty();
    }
    return Optional.of(patchListEntry);
  }

  private ComparisonType getComparisonType(RevObject a, RevCommit b) {
    for (int i = 0; i < b.getParentCount(); i++) {
      if (b.getParent(i).equals(a)) {
        return ComparisonType.againstParent(i + 1);
      }
    }

    if (key.getOldId() == null && b.getParentCount() > 0) {
      return ComparisonType.againstAutoMerge();
    }

    return ComparisonType.againstOtherPatchSet();
  }

  private static long getFileSize(ObjectReader reader, FileMode mode, String path, RevTree t)
      throws IOException {
    if (!isBlob(mode)) {
      return 0;
    }
    try (TreeWalk tw = TreeWalk.forPath(reader, path, t)) {
      return tw != null ? reader.open(tw.getObjectId(0), OBJ_BLOB).getSize() : 0;
    }
  }

  private static boolean isBlob(FileMode mode) {
    int t = mode.getBits() & FileMode.TYPE_MASK;
    return t == FileMode.TYPE_FILE || t == FileMode.TYPE_SYMLINK;
  }

  private FileHeader toFileHeader(
      PatchListKey key, DiffFormatter diffFormatter, DiffEntry diffEntry) throws IOException {

    Future<FileHeader> result =
        diffExecutor.submit(
            () -> {
              synchronized (diffEntry) {
                return diffFormatter.toFileHeader(diffEntry);
              }
            });

    try {
      return result.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | TimeoutException e) {
      log.warn(
          timeoutMillis
              + " ms timeout reached for Diff loader"
              + " in project "
              + project
              + " on commit "
              + key.getNewId().name()
              + " on path "
              + diffEntry.getNewPath()
              + " comparing "
              + diffEntry.getOldId().name()
              + ".."
              + diffEntry.getNewId().name());
      result.cancel(true);
      synchronized (diffEntry) {
        return toFileHeaderWithoutMyersDiff(diffFormatter, diffEntry);
      }
    } catch (ExecutionException e) {
      // If there was an error computing the result, carry it
      // up to the caller so the cache knows this key is invalid.
      Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
      throw new IOException(e.getMessage(), e.getCause());
    }
  }

  private FileHeader toFileHeaderWithoutMyersDiff(DiffFormatter diffFormatter, DiffEntry diffEntry)
      throws IOException {
    HistogramDiff histogramDiff = new HistogramDiff();
    histogramDiff.setFallbackAlgorithm(null);
    diffFormatter.setDiffAlgorithm(histogramDiff);
    return diffFormatter.toFileHeader(diffEntry);
  }

  private PatchListEntry newCommitMessage(
      RawTextComparator cmp, ObjectReader reader, RevCommit aCommit, RevCommit bCommit)
      throws IOException {
    Text aText = aCommit != null ? Text.forCommit(reader, aCommit) : Text.EMPTY;
    Text bText = Text.forCommit(reader, bCommit);
    return createPatchListEntry(cmp, aCommit, aText, bText, Patch.COMMIT_MSG);
  }

  private PatchListEntry newMergeList(
      RawTextComparator cmp,
      ObjectReader reader,
      RevCommit aCommit,
      RevCommit bCommit,
      ComparisonType comparisonType)
      throws IOException {
    Text aText = aCommit != null ? Text.forMergeList(comparisonType, reader, aCommit) : Text.EMPTY;
    Text bText = Text.forMergeList(comparisonType, reader, bCommit);
    return createPatchListEntry(cmp, aCommit, aText, bText, Patch.MERGE_LIST);
  }

  private static PatchListEntry createPatchListEntry(
      RawTextComparator cmp, RevCommit aCommit, Text aText, Text bText, String fileName) {
    byte[] rawHdr = getRawHeader(aCommit != null, fileName);
    byte[] aContent = aText.getContent();
    byte[] bContent = bText.getContent();
    long size = bContent.length;
    long sizeDelta = bContent.length - aContent.length;
    RawText aRawText = new RawText(aContent);
    RawText bRawText = new RawText(bContent);
    EditList edits = new HistogramDiff().diff(cmp, aRawText, bRawText);
    FileHeader fh = new FileHeader(rawHdr, edits, PatchType.UNIFIED);
    return new PatchListEntry(fh, edits, ImmutableSet.of(), size, sizeDelta);
  }

  private static byte[] getRawHeader(boolean hasA, String fileName) {
    StringBuilder hdr = new StringBuilder();
    hdr.append("diff --git");
    if (hasA) {
      hdr.append(" a/").append(fileName);
    } else {
      hdr.append(" ").append(FileHeader.DEV_NULL);
    }
    hdr.append(" b/").append(fileName);
    hdr.append("\n");

    if (hasA) {
      hdr.append("--- a/").append(fileName).append("\n");
    } else {
      hdr.append("--- ").append(FileHeader.DEV_NULL).append("\n");
    }
    hdr.append("+++ b/").append(fileName).append("\n");
    return hdr.toString().getBytes(UTF_8);
  }

  private static PatchListEntry newEntry(
      RevTree aTree, FileHeader fileHeader, Set<Edit> editsDueToRebase, long size, long sizeDelta) {
    if (aTree == null // want combined diff
        || fileHeader.getPatchType() != PatchType.UNIFIED
        || fileHeader.getHunks().isEmpty()) {
      return new PatchListEntry(fileHeader, ImmutableList.of(), ImmutableSet.of(), size, sizeDelta);
    }

    List<Edit> edits = fileHeader.toEditList();
    if (edits.isEmpty()) {
      return new PatchListEntry(fileHeader, ImmutableList.of(), ImmutableSet.of(), size, sizeDelta);
    }
    return new PatchListEntry(fileHeader, edits, editsDueToRebase, size, sizeDelta);
  }

  private RevObject aFor(
      PatchListKey key, Repository repo, RevWalk rw, ObjectInserter ins, RevCommit b)
      throws IOException {
    if (key.getOldId() != null) {
      return rw.parseAny(key.getOldId());
    }

    switch (b.getParentCount()) {
      case 0:
        return rw.parseAny(emptyTree(ins));
      case 1:
        {
          RevCommit r = b.getParent(0);
          rw.parseBody(r);
          return r;
        }
      case 2:
        if (key.getParentNum() != null) {
          RevCommit r = b.getParent(key.getParentNum() - 1);
          rw.parseBody(r);
          return r;
        }
        return autoMerger.merge(repo, rw, ins, b, mergeStrategy);
      default:
        // TODO(sop) handle an octopus merge.
        return null;
    }
  }

  private static ObjectId emptyTree(ObjectInserter ins) throws IOException {
    ObjectId id = ins.insert(Constants.OBJ_TREE, new byte[] {});
    ins.flush();
    return id;
  }
}
