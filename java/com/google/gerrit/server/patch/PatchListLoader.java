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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.auto.value.AutoValue;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.patch.EditTransformer.ContextAwareEdit;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
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

public class PatchListLoader implements Callable<PatchList> {
  static final FluentLogger logger = FluentLogger.forEnclosingClass();

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

      EditsDueToRebaseResult editsDueToRebaseResult =
          determineEditsDueToRebase(aCommit, b, diffEntries, df, rw);
      diffEntries = editsDueToRebaseResult.getRelevantOriginalDiffEntries();
      Multimap<String, ContextAwareEdit> editsDueToRebasePerFilePath =
          editsDueToRebaseResult.getEditsDueToRebasePerFilePath();

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
      for (DiffEntry diffEntry : diffEntries) {
        Set<ContextAwareEdit> editsDueToRebase =
            getEditsDueToRebase(editsDueToRebasePerFilePath, diffEntry);
        Optional<PatchListEntry> patchListEntry =
            getPatchListEntry(reader, df, diffEntry, aTree, bTree, editsDueToRebase);
        patchListEntry.ifPresent(entries::add);
      }
      return new PatchList(
          a, b, isMerge, comparisonType, entries.toArray(new PatchListEntry[entries.size()]));
    }
  }

  /**
   * Identifies the edits which are present between {@code commitA} and {@code commitB} due to other
   * commits in between those two. Edits which cannot be clearly attributed to those other commits
   * (because they overlap with modifications introduced by {@code commitA} or {@code commitB}) are
   * omitted from the result. The edits are expressed as differences between {@code treeA} of {@code
   * commitA} and {@code treeB} of {@code commitB}.
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
   * @param diffEntries the list of {@code DiffEntries} for the diff between {@code commitA} and
   *     {@code commitB}
   * @param df the {@code DiffFormatter}
   * @param rw the current {@code RevWalk}
   * @return an aggregated result of the computation
   * @throws PatchListNotAvailableException if the edits can't be identified
   * @throws IOException if an error occurred while accessing the repository
   */
  private EditsDueToRebaseResult determineEditsDueToRebase(
      RevCommit commitA,
      RevCommit commitB,
      List<DiffEntry> diffEntries,
      DiffFormatter df,
      RevWalk rw)
      throws PatchListNotAvailableException, IOException {
    if (commitA == null
        || isRootOrMergeCommit(commitA)
        || isRootOrMergeCommit(commitB)
        || areParentChild(commitA, commitB)
        || haveCommonParent(commitA, commitB)) {
      return EditsDueToRebaseResult.create(diffEntries, ImmutableMultimap.of());
    }

    PatchListKey oldKey = PatchListKey.againstDefaultBase(key.getOldId(), key.getWhitespace());
    PatchList oldPatchList = patchListCache.get(oldKey, project);
    PatchListKey newKey = PatchListKey.againstDefaultBase(key.getNewId(), key.getWhitespace());
    PatchList newPatchList = patchListCache.get(newKey, project);

    List<PatchListEntry> oldPatches = oldPatchList.getPatches();
    List<PatchListEntry> newPatches = newPatchList.getPatches();
    // TODO(aliceks): Have separate but more limited lists for parents and patch sets (but don't
    // mess up renames/copies).
    Set<String> touchedFilePaths = new HashSet<>();
    for (PatchListEntry patchListEntry : oldPatches) {
      touchedFilePaths.addAll(getTouchedFilePaths(patchListEntry));
    }
    for (PatchListEntry patchListEntry : newPatches) {
      touchedFilePaths.addAll(getTouchedFilePaths(patchListEntry));
    }

    List<DiffEntry> relevantDiffEntries =
        diffEntries.stream()
            .filter(diffEntry -> isTouched(touchedFilePaths, diffEntry))
            .collect(toImmutableList());

    RevCommit parentCommitA = commitA.getParent(0);
    rw.parseBody(parentCommitA);
    RevCommit parentCommitB = commitB.getParent(0);
    rw.parseBody(parentCommitB);
    List<DiffEntry> parentDiffEntries = df.scan(parentCommitA, parentCommitB);
    // TODO(aliceks): Find a way to not construct a PatchListEntry as it contains many unnecessary
    // details and we don't fill all of them properly.
    List<PatchListEntry> parentPatchListEntries =
        getRelevantPatchListEntries(
            parentDiffEntries, parentCommitA, parentCommitB, touchedFilePaths, df);

    EditTransformer editTransformer = new EditTransformer(parentPatchListEntries);
    editTransformer.transformReferencesOfSideA(oldPatches);
    editTransformer.transformReferencesOfSideB(newPatches);
    return EditsDueToRebaseResult.create(
        relevantDiffEntries, editTransformer.getEditsPerFilePath());
  }

  private static boolean isRootOrMergeCommit(RevCommit commit) {
    return commit.getParentCount() != 1;
  }

  private static boolean areParentChild(RevCommit commitA, RevCommit commitB) {
    return ObjectId.isEqual(commitA.getParent(0), commitB)
        || ObjectId.isEqual(commitB.getParent(0), commitA);
  }

  private static boolean haveCommonParent(RevCommit commitA, RevCommit commitB) {
    return ObjectId.isEqual(commitA.getParent(0), commitB.getParent(0));
  }

  private static Set<String> getTouchedFilePaths(PatchListEntry patchListEntry) {
    String oldFilePath = patchListEntry.getOldName();
    String newFilePath = patchListEntry.getNewName();

    return oldFilePath == null
        ? ImmutableSet.of(newFilePath)
        : ImmutableSet.of(oldFilePath, newFilePath);
  }

  private static boolean isTouched(Set<String> touchedFilePaths, DiffEntry diffEntry) {
    String oldFilePath = diffEntry.getOldPath();
    String newFilePath = diffEntry.getNewPath();
    // One of the above file paths could be /dev/null but we need not explicitly check for this
    // value as the set of file paths shouldn't contain it.
    return touchedFilePaths.contains(oldFilePath) || touchedFilePaths.contains(newFilePath);
  }

  private List<PatchListEntry> getRelevantPatchListEntries(
      List<DiffEntry> parentDiffEntries,
      RevCommit parentCommitA,
      RevCommit parentCommitB,
      Set<String> touchedFilePaths,
      DiffFormatter diffFormatter)
      throws IOException {
    List<PatchListEntry> parentPatchListEntries = new ArrayList<>(parentDiffEntries.size());
    for (DiffEntry parentDiffEntry : parentDiffEntries) {
      if (!isTouched(touchedFilePaths, parentDiffEntry)) {
        continue;
      }
      FileHeader fileHeader = toFileHeader(parentCommitB, diffFormatter, parentDiffEntry);
      // The code which uses this PatchListEntry doesn't care about the last three parameters. As
      // they are expensive to compute, we use arbitrary values for them.
      PatchListEntry patchListEntry =
          newEntry(parentCommitA.getTree(), fileHeader, ImmutableSet.of(), 0, 0);
      parentPatchListEntries.add(patchListEntry);
    }
    return parentPatchListEntries;
  }

  private static Set<ContextAwareEdit> getEditsDueToRebase(
      Multimap<String, ContextAwareEdit> editsDueToRebasePerFilePath, DiffEntry diffEntry) {
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
      Set<ContextAwareEdit> editsDueToRebase)
      throws IOException {
    FileHeader fileHeader = toFileHeader(key.getNewId(), diffFormatter, diffEntry);
    long oldSize = getFileSize(objectReader, diffEntry.getOldMode(), diffEntry.getOldPath(), treeA);
    long newSize = getFileSize(objectReader, diffEntry.getNewMode(), diffEntry.getNewPath(), treeB);
    Set<Edit> contentEditsDueToRebase = getContentEdits(editsDueToRebase);
    PatchListEntry patchListEntry =
        newEntry(treeA, fileHeader, contentEditsDueToRebase, newSize, newSize - oldSize);
    // All edits in a file are due to rebase -> exclude the file from the diff.
    if (EditTransformer.toEdits(patchListEntry).allMatch(editsDueToRebase::contains)) {
      return Optional.empty();
    }
    return Optional.of(patchListEntry);
  }

  private static Set<Edit> getContentEdits(Set<ContextAwareEdit> editsDueToRebase) {
    return editsDueToRebase.stream()
        .map(ContextAwareEdit::toEdit)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toSet());
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
      ObjectId commitB, DiffFormatter diffFormatter, DiffEntry diffEntry) throws IOException {

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
      logger.atWarning().log(
          "%s ms timeout reached for Diff loader in project %s"
              + " on commit %s on path %s comparing %s..%s",
          timeoutMillis,
          project,
          commitB.name(),
          diffEntry.getNewPath(),
          diffEntry.getOldId().name(),
          diffEntry.getNewId().name());
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
    long sizeDelta = size - aContent.length;
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

  @AutoValue
  abstract static class EditsDueToRebaseResult {
    public static EditsDueToRebaseResult create(
        List<DiffEntry> relevantDiffEntries,
        Multimap<String, ContextAwareEdit> editsDueToRebasePerFilePath) {
      return new AutoValue_PatchListLoader_EditsDueToRebaseResult(
          relevantDiffEntries, editsDueToRebasePerFilePath);
    }

    public abstract List<DiffEntry> getRelevantOriginalDiffEntries();

    /** Returns the edits per file path they modify in {@code treeB}. */
    public abstract Multimap<String, ContextAwareEdit> getEditsDueToRebasePerFilePath();
  }
}
