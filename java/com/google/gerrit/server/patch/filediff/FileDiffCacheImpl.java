//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//
//
//

package com.google.gerrit.server.patch.filediff;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.EMPTY_TREE_ID;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.FileMode;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.ComparisonType;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffUtil;
import com.google.gerrit.server.patch.Text;
import com.google.gerrit.server.patch.filediff.EditTransformer.ContextAwareEdit;
import com.google.gerrit.server.patch.gitfilediff.FileHeaderUtil;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiff;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl.DiffAlgorithm;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl.DiffAlgorithmFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Cache for the single file diff between two commits for a single file path. This cache adds extra
 * Gerrit logic such as identifying the edits due to rebase.
 *
 * <p>If the {@link Key#oldCommit()} ()} is equal to {@link
 * org.eclipse.jgit.lib.Constants#EMPTY_TREE_ID}, the git diff will be evaluated against the empty
 * tree.
 */
public class FileDiffCacheImpl implements FileDiffCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DIFF = "gerrit_file_diff";

  private final LoadingCache<Key, FileDiffOutput> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(FileDiffCache.class).to(FileDiffCacheImpl.class);

        persist(DIFF, Key.class, FileDiffOutput.class)
            .maximumWeight(10 << 20)
            .weigher(FileDiffWeigher.class)
            .loader(FileDiffLoader.class);
      }
    };
  }

  private enum MagicPathType {
    COMMIT,
    MERGE_LIST
  }

  @Inject
  public FileDiffCacheImpl(@Named(DIFF) LoadingCache<Key, FileDiffOutput> cache) {
    this.cache = cache;
  }

  @Override
  public FileDiffOutput get(Key key) throws DiffNotAvailableException {
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  @Override
  public ImmutableMap<Key, FileDiffOutput> getAll(Iterable<Key> keys)
      throws DiffNotAvailableException {
    try {
      ImmutableMap<Key, FileDiffOutput> result = cache.getAll(keys);
      if (result.size() != Iterables.size(keys)) {
        throw new DiffNotAvailableException(
            String.format(
                "Failed to load the value for all %d keys. Returned "
                    + "map contains only %d values",
                Iterables.size(keys), result.size()));
      }
      return result;
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  static class FileDiffLoader extends CacheLoader<Key, FileDiffOutput> {
    private final GitFileDiffCacheImpl gitCache;
    private final GitRepositoryManager repoManager;

    @Inject
    FileDiffLoader(GitRepositoryManager manager, GitFileDiffCacheImpl gitCache) {
      this.repoManager = manager;
      this.gitCache = gitCache;
    }

    @Override
    public FileDiffOutput load(Key key) throws IOException, DiffNotAvailableException {
      return loadAll(ImmutableList.of(key)).get(key);
    }

    @Override
    public Map<Key, FileDiffOutput> loadAll(Iterable<? extends Key> keys)
        throws DiffNotAvailableException {
      Map<Key, FileDiffOutput> result = new HashMap<>();

      Map<Project.NameKey, List<Key>> keysByProject =
          Streams.stream(keys).distinct().collect(Collectors.groupingBy(Key::project));

      for (Project.NameKey project : keysByProject.keySet()) {
        List<Key> fileKeys = new ArrayList<>();

        try (Repository repo = repoManager.openRepository(project);
            ObjectReader reader = repo.newObjectReader();
            RevWalk rw = new RevWalk(reader)) {

          for (Key key : keysByProject.get(project)) {
            if (key.newFilePath().equals(Patch.COMMIT_MSG)) {
              result.put(key, createMagicPathEntry(key, reader, rw, MagicPathType.COMMIT));
            } else if (key.newFilePath().equals(Patch.MERGE_LIST)) {
              result.put(key, createMagicPathEntry(key, reader, rw, MagicPathType.MERGE_LIST));
            } else {
              fileKeys.add(key);
            }
          }
          result.putAll(forFiles(reader, fileKeys, rw));
        } catch (IOException e) {
          logger.atWarning().log("Failed to open the repository %s: %s", project, e.getMessage());
        }
      }
      return result;
    }

    private ComparisonType getComparisonType(RevWalk rw, ObjectId oldCommitId, ObjectId newCommitId)
        throws IOException {
      RevCommit oldCommit = DiffUtil.getRevCommit(rw, oldCommitId);
      RevCommit newCommit = DiffUtil.getRevCommit(rw, newCommitId);
      for (int i = 0; i < newCommit.getParentCount(); i++) {
        if (newCommit.getParent(i).equals(oldCommit)) {
          return ComparisonType.againstParent(i + 1);
        }
      }
      if (newCommit.getParentCount() > 0) {
        return ComparisonType.againstAutoMerge();
      }
      return ComparisonType.againstOtherPatchSet();
    }

    /**
     * Creates a {@link FileDiffOutput} entry for the "Commit message" and "Merge list" file paths.
     */
    private FileDiffOutput createMagicPathEntry(
        Key key, ObjectReader reader, RevWalk rw, MagicPathType magicPathType) {
      try {
        RawTextComparator cmp = DiffUtil.comparatorFor(key.whitespace());
        ComparisonType comparisonType = getComparisonType(rw, key.oldCommit(), key.newCommit());
        RevCommit aCommit =
            comparisonType.isAgainstParentOrAutoMerge()
                ? null
                : DiffUtil.getRevCommit(rw, key.oldCommit());
        RevCommit bCommit = DiffUtil.getRevCommit(rw, key.newCommit());
        if (magicPathType == MagicPathType.COMMIT) {
          return createCommitEntry(reader, aCommit, bCommit, cmp, key.diffAlgorithm());
        } else if (magicPathType == MagicPathType.MERGE_LIST) {
          return createMergeListEntry(
              reader, aCommit, bCommit, comparisonType, cmp, key.diffAlgorithm());
        }
      } catch (IOException e) {
        logger.atWarning().log("Failed to compute commit entry for key " + key);
      }
      return FileDiffOutput.empty(key.newFilePath());
    }

    private FileDiffOutput createCommitEntry(
        ObjectReader reader,
        RevCommit oldCommit,
        RevCommit newCommit,
        RawTextComparator rawTextComparator,
        GitFileDiffCacheImpl.DiffAlgorithm diffAlgorithm)
        throws IOException {
      Text aText = newCommit != null ? Text.forCommit(reader, newCommit) : Text.EMPTY;
      Text bText = Text.forCommit(reader, newCommit);
      return createMagicFileDiffOutput(
          rawTextComparator, oldCommit, aText, bText, Patch.COMMIT_MSG, diffAlgorithm);
    }

    private FileDiffOutput createMergeListEntry(
        ObjectReader reader,
        RevCommit oldCommit,
        RevCommit newCommit,
        ComparisonType comparisonType,
        RawTextComparator rawTextComparator,
        GitFileDiffCacheImpl.DiffAlgorithm diffAlgorithm)
        throws IOException {
      Text aText =
          oldCommit != null ? Text.forMergeList(comparisonType, reader, oldCommit) : Text.EMPTY;
      Text bText = Text.forMergeList(comparisonType, reader, newCommit);
      return createMagicFileDiffOutput(
          rawTextComparator, oldCommit, aText, bText, Patch.MERGE_LIST, diffAlgorithm);
    }

    private Map<Key, FileDiffOutput> forFiles(ObjectReader reader, List<Key> keys, RevWalk rw)
        throws DiffNotAvailableException, IOException {
      Map<Key, AllFileGitDiffs> fileToAllDiffs = new AllDiffsEvaluator(rw).execute(keys);
      Map<Key, FileDiffOutput> result = new HashMap<>();

      for (Map.Entry<Key, AllFileGitDiffs> entry : fileToAllDiffs.entrySet()) {
        AllFileGitDiffs allDiffs = entry.getValue();
        GitFileDiff gitDiff = allDiffs.main.value;

        FileEdits editsDueToRebase = FileEdits.empty();

        if (entry.getValue().hasRebaseEdits) {
          editsDueToRebase = computeRebaseEdits(allDiffs);
        }

        // TODO(ghareeb): Unify the usage of JGit's Edit class and ours.
        List<Edit> normalEdits = gitDiff.edits();
        Set<Edit> rebaseEdits =
            ImmutableSet.copyOf(
                editsDueToRebase.edits().stream()
                    .map(com.google.gerrit.server.patch.filediff.Edit::asJGitEdit)
                    .collect(Collectors.toList()));

        RevTree aTree = rw.parseTree(allDiffs.main.key.oldTree());
        RevTree bTree = rw.parseTree(allDiffs.main.key.newTree());

        Long oldSize =
            gitDiff.oldPath().isPresent()
                ? new FileSizeFactory(reader, aTree)
                    .get(gitDiff.oldId().get(), gitDiff.oldMode().get(), gitDiff.oldPath().get())
                : 0;
        Long newSize =
            gitDiff.newPath().isPresent()
                ? new FileSizeFactory(reader, bTree)
                    .get(gitDiff.newId().get(), gitDiff.newMode().get(), gitDiff.newPath().get())
                : 0;

        FileDiffOutput fileDiff =
            FileDiffOutput.builder()
                .changeType(gitDiff.changeType())
                .patchType(gitDiff.patchType())
                .oldPath(gitDiff.oldPath())
                .newPath(gitDiff.newPath())
                .headerLines(FileHeaderUtil.getHeaderLines(gitDiff.fileHeader()))
                .edits(asTaggedEdits(normalEdits, rebaseEdits))
                .size(newSize)
                .sizeDelta(newSize - oldSize)
                .build();

        result.put(entry.getKey(), fileDiff);
      }

      return result;
    }

    private static ImmutableList<TaggedEdit> asTaggedEdits(
        List<Edit> normalEdits, Set<Edit> rebaseEdits) {
      ImmutableList.Builder<TaggedEdit> result =
          ImmutableList.builderWithExpectedSize(normalEdits.size());
      for (Edit e : normalEdits) {
        result.add(TaggedEdit.create(e, rebaseEdits.contains(e)));
      }
      return result.build();
    }

    /**
     * Computes the subset of edits that are due to rebase between 2 commits.
     *
     * <p>The input parameter {@link AllFileGitDiffs#main} contains all the edits in consideration.
     * Of those, we identify the edits due to rebase as a function of:
     *
     * <p>1) The edits between the old commit and its parent {@link AllFileGitDiffs#oldVsPar}.
     *
     * <p>2) The edits between the new commit and its parent {@link AllFileGitDiffs#newVsPar}.
     *
     * <p>3) The edits between the parents of the old commit and new commits {@link
     * AllFileGitDiffs#parVsPar}.
     *
     * @param diffs an entity containing 4 sets of edits: those between the old and new commit,
     *     between the old and new commits vs. their parents, and between the old and new parents.
     * @return the list of edits that are due to rebase.
     */
    private FileEdits computeRebaseEdits(AllFileGitDiffs diffs) {
      GitFileDiff parVsParDiff = diffs.parVsPar.value;
      if (parVsParDiff.isEmpty()) {
        return FileEdits.empty();
      }
      GitFileDiff oldVsParDiff = diffs.oldVsPar.value;
      GitFileDiff newVsParDiff = diffs.newVsPar.value;

      EditTransformer editTransformer =
          new EditTransformer(
              ImmutableList.of(
                  FileEdits.create(
                      parVsParDiff.edits(), parVsParDiff.oldPath(), parVsParDiff.newPath())));

      if (!oldVsParDiff.isEmpty()) {
        editTransformer.transformReferencesOfSideA(
            ImmutableList.of(
                FileEdits.create(
                    oldVsParDiff.edits(), oldVsParDiff.oldPath(), oldVsParDiff.newPath())));
      }

      if (!newVsParDiff.isEmpty()) {
        editTransformer.transformReferencesOfSideB(
            ImmutableList.of(
                FileEdits.create(
                    newVsParDiff.edits(), newVsParDiff.oldPath(), newVsParDiff.newPath())));
      }

      Multimap<String, ContextAwareEdit> editsPerFilePath = editTransformer.getEditsPerFilePath();

      if (editsPerFilePath.isEmpty()) {
        return FileEdits.empty();
      }

      // editsPerFilePath is expected to have a single item representing the file
      String filePath = editsPerFilePath.keys().iterator().next();
      Collection<ContextAwareEdit> edits = editsPerFilePath.get(filePath);
      return FileEdits.create(
          getContentEdits(edits),
          edits.iterator().next().getOldFilePath(),
          edits.iterator().next().getNewFilePath());
    }

    private static List<Edit> getContentEdits(Iterable<ContextAwareEdit> edits) {
      return Streams.stream(edits)
          .map(ContextAwareEdit::toEdit)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());
    }

    private static FileDiffOutput createMagicFileDiffOutput(
        RawTextComparator cmp,
        RevCommit aCommit,
        Text aText,
        Text bText,
        String fileName,
        GitFileDiffCacheImpl.DiffAlgorithm diffAlgorithm) {
      byte[] rawHdr = getRawHeader(aCommit != null, fileName);
      byte[] aContent = aText.getContent();
      byte[] bContent = bText.getContent();
      long size = bContent.length;
      long sizeDelta = size - aContent.length;
      RawText aRawText = new RawText(aContent);
      RawText bRawText = new RawText(bContent);
      EditList edits = DiffAlgorithmFactory.create(diffAlgorithm).diff(cmp, aRawText, bRawText);
      FileHeader fh = new FileHeader(rawHdr, edits, PatchType.UNIFIED);
      Patch.ChangeType changeType = FileHeaderUtil.getChangeType(fh);
      return FileDiffOutput.builder()
          .oldPath(FileHeaderUtil.getOldPath(fh, changeType))
          .newPath(FileHeaderUtil.getNewPath(fh, changeType))
          .changeType(changeType)
          .patchType(FileHeaderUtil.getPatchType(fh))
          .headerLines(FileHeaderUtil.getHeaderLines(fh))
          .edits(asTaggedEdits(edits, ImmutableSet.of()))
          .size(size)
          .sizeDelta(sizeDelta)
          .build();
    }

    static class FileSizeFactory {
      private final ObjectReader reader;
      private final RevTree tree;

      FileSizeFactory(ObjectReader reader, RevTree tree) {
        this.reader = reader;
        this.tree = tree;
      }

      private long get(AbbreviatedObjectId abbreviatedId, Patch.FileMode mode, String path)
          throws IOException {
        if (!isBlob(mode)) {
          return 0;
        }
        ObjectId fileId =
            toObjectId(reader, abbreviatedId).orElseGet(() -> lookupObjectId(reader, path, tree));
        if (ObjectId.zeroId().equals(fileId)) {
          return 0;
        }
        return reader.getObjectSize(fileId, OBJ_BLOB);
      }

      private static ObjectId lookupObjectId(ObjectReader reader, String path, RevTree tree) {
        // This variant is very expensive.
        try (TreeWalk treeWalk = TreeWalk.forPath(reader, path, tree)) {
          return treeWalk != null ? treeWalk.getObjectId(0) : ObjectId.zeroId();
        } catch (IOException e) {
          throw new StorageException(e);
        }
      }

      private static Optional<ObjectId> toObjectId(
          ObjectReader reader, AbbreviatedObjectId abbreviatedId) throws IOException {
        if (abbreviatedId == null) {
          // In theory, DiffEntry#getOldId or DiffEntry#getNewId can be null for pure renames or
          // pure
          // mode changes (e.g. DiffEntry#modify doesn't set the IDs). However, the method we call
          // for diffs (DiffFormatter#scan) seems to always produce DiffEntries with set IDs, even
          // for
          // pure renames.
          return Optional.empty();
        }
        if (abbreviatedId.isComplete()) {
          // With the current JGit version and the method we call for diffs (DiffFormatter#scan),
          // this
          // is the only code path taken right now.
          return Optional.ofNullable(abbreviatedId.toObjectId());
        }
        Collection<ObjectId> objectIds = reader.resolve(abbreviatedId);
        // It seems very unlikely that an ObjectId which was just abbreviated by the diff
        // computation
        // now can't be resolved to exactly one ObjectId. The API allows this possibility, though.
        return objectIds.size() == 1
            ? Optional.of(Iterables.getOnlyElement(objectIds))
            : Optional.empty();
      }

      private static boolean isBlob(Patch.FileMode mode) {
        return mode.equals(FileMode.REGULAR_FILE) || mode.equals(FileMode.SYMLINK);
      }
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

    /**
     * A helper class that computes the 4 {@link GitFileDiff}s for a list of {@link Key}s:
     *
     * <p>
     *
     * <ul>
     *   <li>old commit vs. new commit
     *   <li>old parent vs. old commit
     *   <li>new parent vs. new commit
     *   <li>old parent vs. new parent
     * </ul>
     *
     * The 4 {@link GitFileDiff} are stored in the entity class {@link AllFileGitDiffs}. We use
     * these diffs to identify the edits due to rebase using the {@link EditTransformer} class.
     */
    private class AllDiffsEvaluator {
      private final RevWalk rw;

      private AllDiffsEvaluator(RevWalk rw) {
        this.rw = rw;
      }

      private Map<Key, AllFileGitDiffs> execute(List<Key> keys) throws DiffNotAvailableException {
        Map<Key, AllFileGitDiffs> fileToAllDiffs = new HashMap<>();
        List<Key> keysWithRebaseEdits = new ArrayList();
        for (Key key : keys) {
          try {
            AllFileGitDiffs allDiffs = new AllFileGitDiffs();
            fileToAllDiffs.put(key, allDiffs);
            if (key.oldCommit().equals(EMPTY_TREE_ID)) {
              continue;
            }
            RevCommit oldRevCommit = DiffUtil.getRevCommit(rw, key.oldCommit());
            RevCommit newRevCommit = DiffUtil.getRevCommit(rw, key.newCommit());
            if (!DiffUtil.areRelated(oldRevCommit, newRevCommit)) {
              allDiffs.hasRebaseEdits = true;
              allDiffs.oldParent = oldRevCommit.getParent(0).getId();
              allDiffs.newParent = newRevCommit.getParent(0).getId();
              keysWithRebaseEdits.add(key);
            }
          } catch (IOException e) {
            logger.atWarning().log(
                "Failed to evaluate commits relation for key " + key + ". Skipping this key");
          }
        }

        // TODO(ghareeb): as an enhancement, you can batch these calls as follows.
        // First batch: "old commit vs. new commit" and "new parent vs. new commit"
        // Second batch: "old parent vs. old commit" and "old parent vs. new parent"

        Map<Key, GitFileDiffCacheImpl.Key> keysToGitKeys =
            getGitKeysFor(keys, k -> k.oldCommit(), k -> k.newCommit(), k -> k.newFilePath());
        Map<Key, Pair<GitFileDiffCacheImpl.Key, GitFileDiff>> oldVsNewDiffs =
            getGitDiffsForGitKeys(keysToGitKeys);

        keysToGitKeys =
            getGitKeysFor(
                keysWithRebaseEdits,
                k -> fileToAllDiffs.get(k).oldParent,
                k -> k.oldCommit(),
                k -> oldVsNewDiffs.get(k).getRight().oldPath().orElse(null));
        Map<Key, Pair<GitFileDiffCacheImpl.Key, GitFileDiff>> oldVsParDiffs =
            getGitDiffsForGitKeys(keysToGitKeys);

        keysToGitKeys =
            getGitKeysFor(
                keysWithRebaseEdits,
                k -> fileToAllDiffs.get(k).newParent,
                k -> k.newCommit(),
                k -> k.newFilePath());
        Map<Key, Pair<GitFileDiffCacheImpl.Key, GitFileDiff>> newVsParDiffs =
            getGitDiffsForGitKeys(keysToGitKeys);

        keysToGitKeys =
            getGitKeysFor(
                keysWithRebaseEdits,
                k -> fileToAllDiffs.get(k).oldParent,
                k -> fileToAllDiffs.get(k).newParent,
                k -> {
                  GitFileDiff newVsParDiff = newVsParDiffs.get(k).getRight();
                  // TODO(ghareeb): Follow up on replacing key.newFilePath as a fallback.
                  // If the file was added between newParent and newCommit, we actually wouldn't
                  // need to have to determine the oldParent vs. newParent diff as nothing in that
                  // file could be an edit due to rebase anymore. Only if the returned diff is
                  // empty, the oldParent vs. newParent diff becomes relevant again (e.g. to
                  // identify a file deletion which was due to rebase. Check if the structure can
                  // be improved to make this clearer. Can we maybe even skip the diff in the first
                  // situation described?
                  return newVsParDiff.oldPath().orElse(k.newFilePath());
                });
        Map<Key, Pair<GitFileDiffCacheImpl.Key, GitFileDiff>> parVsParDiffs =
            getGitDiffsForGitKeys(keysToGitKeys);

        for (Key key : keys) {
          fileToAllDiffs.get(key).main.key = oldVsNewDiffs.get(key).getLeft();
          fileToAllDiffs.get(key).main.value = oldVsNewDiffs.get(key).getRight();

          if (oldVsParDiffs.containsKey(key) && fileToAllDiffs.get(key).hasRebaseEdits) {
            fileToAllDiffs.get(key).oldVsPar.key = oldVsParDiffs.get(key).getLeft();
            fileToAllDiffs.get(key).oldVsPar.value = oldVsParDiffs.get(key).getRight();

            fileToAllDiffs.get(key).newVsPar.key = newVsParDiffs.get(key).getLeft();
            fileToAllDiffs.get(key).newVsPar.value = newVsParDiffs.get(key).getRight();

            fileToAllDiffs.get(key).parVsPar.key = parVsParDiffs.get(key).getLeft();
            fileToAllDiffs.get(key).parVsPar.value = parVsParDiffs.get(key).getRight();
          } else {
            fileToAllDiffs.get(key).hasRebaseEdits = false;
          }
        }

        return fileToAllDiffs;
      }

      private Map<Key, Pair<GitFileDiffCacheImpl.Key, GitFileDiff>> getGitDiffsForGitKeys(
          Map<Key, GitFileDiffCacheImpl.Key> keys) throws DiffNotAvailableException {
        ImmutableMap.Builder<Key, Pair<GitFileDiffCacheImpl.Key, GitFileDiff>> result =
            ImmutableMap.builderWithExpectedSize(keys.size());
        ImmutableMap<GitFileDiffCacheImpl.Key, GitFileDiff> gitDiffs =
            gitCache.getAll(keys.values());
        for (Key key : keys.keySet()) {
          GitFileDiffCacheImpl.Key gitKey = keys.get(key);
          GitFileDiff gitFileDiff = gitDiffs.get(gitKey);
          result.put(key, ImmutablePair.of(gitKey, gitFileDiff));
        }
        return result.build();
      }

      private Map<Key, GitFileDiffCacheImpl.Key> getGitKeysFor(
          List<Key> keys,
          Function<Key, ObjectId> oldCommit,
          Function<Key, ObjectId> newCommit,
          Function<Key, String> newPath) {
        Map<Key, GitFileDiffCacheImpl.Key> keysToGitKeys = new HashMap<>();
        for (Key key : keys) {
          try {
            String path = newPath.apply(key);
            if (path != null) {
              keysToGitKeys.put(
                  key, getGitKeyFor(key, oldCommit.apply(key), newCommit.apply(key), path, rw));
            }
          } catch (IOException e) {
            logger.atWarning().log("Failed to compute the git key for key %s", key);
          }
        }
        return keysToGitKeys;
      }

      private GitFileDiffCacheImpl.Key getGitKeyFor(
          Key key, ObjectId aCommit, ObjectId bCommit, String pathNew, RevWalk rw)
          throws IOException {
        ObjectId oldTreeId =
            aCommit.equals(EMPTY_TREE_ID) ? EMPTY_TREE_ID : DiffUtil.getTreeId(rw, aCommit);
        ObjectId newTreeId = DiffUtil.getTreeId(rw, bCommit);
        return GitFileDiffCacheImpl.Key.builder()
            .project(key.project())
            .oldTree(oldTreeId)
            .newTree(newTreeId)
            .newFilePath(pathNew == null ? key.newFilePath() : pathNew)
            .renameScore(key.renameScore())
            .diffAlgorithm(key.diffAlgorithm())
            .whitespace(key.whitespace())
            .build();
      }
    }

    private static class AllFileGitDiffs {
      private GitDiffEntity main = new GitDiffEntity();
      private GitDiffEntity oldVsPar = new GitDiffEntity();
      private GitDiffEntity newVsPar = new GitDiffEntity();
      private GitDiffEntity parVsPar = new GitDiffEntity();

      private boolean hasRebaseEdits;
      private ObjectId oldParent;
      private ObjectId newParent;

      private static class GitDiffEntity {
        private GitFileDiffCacheImpl.Key key;
        private GitFileDiff value;
      }
    }
  }

  // TODO(ghareeb): Implement protobuf serializer for the key in a follow up change
  @AutoValue
  public abstract static class Key implements Serializable {

    public abstract Project.NameKey project();

    public abstract ObjectId oldCommit();

    public abstract ObjectId newCommit();

    public abstract String newFilePath();

    public abstract Integer renameScore();

    public abstract DiffAlgorithm diffAlgorithm();

    public abstract DiffPreferencesInfo.Whitespace whitespace();

    public int weight() {
      // TODO(ghareeb): implement a proper weigher
      return 1;
    }

    public static Builder builder() {
      return new AutoValue_FileDiffCacheImpl_Key.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder project(NameKey value);

      public abstract Builder oldCommit(ObjectId value);

      public abstract Builder newCommit(ObjectId value);

      public abstract Builder newFilePath(String value);

      public abstract Builder renameScore(Integer value);

      public Builder disableRenameDetection() {
        renameScore(-1);
        return this;
      }

      public abstract Builder diffAlgorithm(DiffAlgorithm value);

      public abstract Builder whitespace(Whitespace value);

      public abstract Key build();
    }
  }
}
