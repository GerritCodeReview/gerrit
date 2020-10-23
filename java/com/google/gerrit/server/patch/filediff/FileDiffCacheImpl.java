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

package com.google.gerrit.server.patch.filediff;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.EMPTY_TREE_ID;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
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
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCache;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl.DiffAlgorithmFactory;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheKey;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Cache for the single file diff between two commits for a single file path. This cache adds extra
 * Gerrit logic such as identifying the edits due to rebase.
 *
 * <p>If the {@link FileDiffCacheKey#oldCommit()} ()} is equal to {@link
 * org.eclipse.jgit.lib.Constants#EMPTY_TREE_ID}, the git diff will be evaluated against the empty
 * tree.
 */
public class FileDiffCacheImpl implements FileDiffCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DIFF = "gerrit_file_diff";

  private final LoadingCache<FileDiffCacheKey, FileDiffOutput> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(FileDiffCache.class).to(FileDiffCacheImpl.class);

        persist(DIFF, FileDiffCacheKey.class, FileDiffOutput.class)
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
  public FileDiffCacheImpl(@Named(DIFF) LoadingCache<FileDiffCacheKey, FileDiffOutput> cache) {
    this.cache = cache;
  }

  @Override
  public FileDiffOutput get(FileDiffCacheKey key) throws DiffNotAvailableException {
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  @Override
  public ImmutableMap<FileDiffCacheKey, FileDiffOutput> getAll(Iterable<FileDiffCacheKey> keys)
      throws DiffNotAvailableException {
    try {
      ImmutableMap<FileDiffCacheKey, FileDiffOutput> result = cache.getAll(keys);
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

  static class FileDiffLoader extends CacheLoader<FileDiffCacheKey, FileDiffOutput> {
    private final GitFileDiffCache gitCache;
    private final GitRepositoryManager repoManager;

    @Inject
    FileDiffLoader(GitRepositoryManager manager, GitFileDiffCache gitCache) {
      this.repoManager = manager;
      this.gitCache = gitCache;
    }

    @Override
    public FileDiffOutput load(FileDiffCacheKey key) throws IOException, DiffNotAvailableException {
      return loadAll(ImmutableList.of(key)).get(key);
    }

    @Override
    public Map<FileDiffCacheKey, FileDiffOutput> loadAll(Iterable<? extends FileDiffCacheKey> keys)
        throws DiffNotAvailableException {
      Map<FileDiffCacheKey, FileDiffOutput> result = new HashMap<>();

      Map<Project.NameKey, List<FileDiffCacheKey>> keysByProject =
          Streams.stream(keys).distinct().collect(Collectors.groupingBy(FileDiffCacheKey::project));

      for (Project.NameKey project : keysByProject.keySet()) {
        List<FileDiffCacheKey> fileKeys = new ArrayList<>();

        try (Repository repo = repoManager.openRepository(project);
            ObjectReader reader = repo.newObjectReader();
            RevWalk rw = new RevWalk(reader)) {

          for (FileDiffCacheKey key : keysByProject.get(project)) {
            if (key.newFilePath().equals(Patch.COMMIT_MSG)) {
              result.put(key, createMagicPathEntry(key, reader, rw, MagicPathType.COMMIT));
            } else if (key.newFilePath().equals(Patch.MERGE_LIST)) {
              result.put(key, createMagicPathEntry(key, reader, rw, MagicPathType.MERGE_LIST));
            } else {
              fileKeys.add(key);
            }
          }
          result.putAll(createFileEntries(reader, fileKeys, rw));
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
        FileDiffCacheKey key, ObjectReader reader, RevWalk rw, MagicPathType magicPathType) {
      try {
        RawTextComparator cmp = comparatorFor(key.whitespace());
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
      FileHeader fileHeader = new FileHeader(rawHdr, edits, PatchType.UNIFIED);
      Patch.ChangeType changeType = FileHeaderUtil.getChangeType(fileHeader);
      return FileDiffOutput.builder()
          .oldPath(FileHeaderUtil.getOldPath(fileHeader))
          .newPath(FileHeaderUtil.getNewPath(fileHeader))
          .changeType(Optional.of(changeType))
          .patchType(Optional.of(FileHeaderUtil.getPatchType(fileHeader)))
          .headerLines(FileHeaderUtil.getHeaderLines(fileHeader))
          .edits(
              asTaggedEdits(
                  edits.stream().map(Edit::fromJGitEdit).collect(Collectors.toList()),
                  ImmutableList.of()))
          .size(size)
          .sizeDelta(sizeDelta)
          .build();
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

    private Map<FileDiffCacheKey, FileDiffOutput> createFileEntries(
        ObjectReader reader, List<FileDiffCacheKey> keys, RevWalk rw)
        throws DiffNotAvailableException, IOException {
      Map<WrappedKey, AllFileGitDiffs> allFileDiffs =
          new AllDiffsEvaluator(rw).execute(wrapKeys(keys, rw));

      Map<FileDiffCacheKey, FileDiffOutput> result = new HashMap<>();

      for (WrappedKey wrappedKey : allFileDiffs.keySet()) {
        AllFileGitDiffs allDiffs = allFileDiffs.get(wrappedKey);

        FileEdits rebaseFileEdits = FileEdits.empty();
        if (!wrappedKey.ignoreRebase()) {
          rebaseFileEdits = computeRebaseEdits(allDiffs);
        }
        List<Edit> rebaseEdits = rebaseFileEdits.edits();

        RevTree aTree = rw.parseTree(allDiffs.mainDiff().gitKey().oldTree());
        RevTree bTree = rw.parseTree(allDiffs.mainDiff().gitKey().newTree());
        GitFileDiff mainGitDiff = allDiffs.mainDiff().gitDiff();

        Long oldSize =
            mainGitDiff.oldPath().isPresent()
                ? new FileSizeEvaluator(reader, aTree)
                    .compute(
                        mainGitDiff.oldId(),
                        mainGitDiff.oldMode().get(),
                        mainGitDiff.oldPath().get())
                : 0;
        Long newSize =
            mainGitDiff.newPath().isPresent()
                ? new FileSizeEvaluator(reader, bTree)
                    .compute(
                        mainGitDiff.newId(),
                        mainGitDiff.newMode().get(),
                        mainGitDiff.newPath().get())
                : 0;

        FileDiffOutput fileDiff =
            FileDiffOutput.builder()
                .changeType(mainGitDiff.changeType())
                .patchType(mainGitDiff.patchType())
                .oldPath(mainGitDiff.oldPath())
                .newPath(mainGitDiff.newPath())
                .headerLines(FileHeaderUtil.getHeaderLines(mainGitDiff.fileHeader()))
                .edits(asTaggedEdits(mainGitDiff.edits(), rebaseEdits))
                .size(newSize)
                .sizeDelta(newSize - oldSize)
                .build();

        result.put(wrappedKey.key(), fileDiff);
      }

      return result;
    }

    /**
     * Convert the list of input keys {@link FileDiffCacheKey} to a list of {@link WrappedKey} that
     * also include the old and new parent commit IDs, and a boolean that indicates whether we
     * should include the rebase edits for each key.
     *
     * <p>The output list is expected to have the same size of the input list, i.e. we map all keys.
     */
    private List<WrappedKey> wrapKeys(List<FileDiffCacheKey> keys, RevWalk rw) {
      List<WrappedKey> result = new ArrayList<>();
      for (FileDiffCacheKey key : keys) {
        if (key.oldCommit().equals(EMPTY_TREE_ID)) {
          result.add(WrappedKey.builder().key(key).ignoreRebase(true).build());
          continue;
        }
        try {
          RevCommit oldRevCommit = DiffUtil.getRevCommit(rw, key.oldCommit());
          RevCommit newRevCommit = DiffUtil.getRevCommit(rw, key.newCommit());
          if (!DiffUtil.areRelated(oldRevCommit, newRevCommit)) {
            result.add(
                WrappedKey.builder()
                    .key(key)
                    .oldParentId(Optional.of(oldRevCommit.getParent(0).getId()))
                    .newParentId(Optional.of(newRevCommit.getParent(0).getId()))
                    .ignoreRebase(false)
                    .build());
          } else {
            result.add(WrappedKey.builder().key(key).ignoreRebase(true).build());
          }
        } catch (IOException e) {
          logger.atWarning().log(
              "Failed to evaluate commits relation for key "
                  + key
                  + ". Skipping this key: "
                  + e.getMessage(),
              e);
          result.add(WrappedKey.builder().key(key).ignoreRebase(true).build());
        }
      }
      return result;
    }

    private static ImmutableList<TaggedEdit> asTaggedEdits(
        List<Edit> normalEdits, List<Edit> rebaseEdits) {
      Set<Edit> rebaseEditsSet = new HashSet(rebaseEdits);
      ImmutableList.Builder<TaggedEdit> result =
          ImmutableList.builderWithExpectedSize(normalEdits.size());
      for (Edit e : normalEdits) {
        result.add(TaggedEdit.create(e, rebaseEditsSet.contains(e)));
      }
      return result.build();
    }

    /**
     * Computes the subset of edits that are due to rebase between 2 commits.
     *
     * <p>The input parameter {@link AllFileGitDiffs#mainDiff} contains all the edits in
     * consideration. Of those, we identify the edits due to rebase as a function of:
     *
     * <p>1) The edits between the old commit and its parent {@link AllFileGitDiffs#oldVsParDiff}.
     *
     * <p>2) The edits between the new commit and its parent {@link AllFileGitDiffs#newVsParDiff}.
     *
     * <p>3) The edits between the parents of the old commit and new commits {@link
     * AllFileGitDiffs#parVsParDiff}.
     *
     * @param diffs an entity containing 4 sets of edits: those between the old and new commit,
     *     between the old and new commits vs. their parents, and between the old and new parents.
     * @return the list of edits that are due to rebase.
     */
    private FileEdits computeRebaseEdits(AllFileGitDiffs diffs) {
      if (!diffs.parVsParDiff().isPresent()) {
        return FileEdits.empty();
      }

      GitFileDiff parVsParDiff = diffs.parVsParDiff().get().gitDiff();

      EditTransformer editTransformer =
          new EditTransformer(
              ImmutableList.of(
                  FileEdits.create(
                      parVsParDiff.edits().stream()
                          .map(Edit::toJGitEdit)
                          .collect(Collectors.toList()),
                      parVsParDiff.oldPath(),
                      parVsParDiff.newPath())));

      if (diffs.oldVsParDiff().isPresent()) {
        GitFileDiff oldVsParDiff = diffs.oldVsParDiff().get().gitDiff();
        editTransformer.transformReferencesOfSideA(
            ImmutableList.of(
                FileEdits.create(
                    oldVsParDiff.edits().stream()
                        .map(Edit::toJGitEdit)
                        .collect(Collectors.toList()),
                    oldVsParDiff.oldPath(),
                    oldVsParDiff.newPath())));
      }

      if (diffs.newVsParDiff().isPresent()) {
        GitFileDiff newVsParDiff = diffs.newVsParDiff().get().gitDiff();
        editTransformer.transformReferencesOfSideB(
            ImmutableList.of(
                FileEdits.create(
                    newVsParDiff.edits().stream()
                        .map(Edit::toJGitEdit)
                        .collect(Collectors.toList()),
                    newVsParDiff.oldPath(),
                    newVsParDiff.newPath())));
      }

      Multimap<String, ContextAwareEdit> editsPerFilePath = editTransformer.getEditsPerFilePath();

      if (editsPerFilePath.isEmpty()) {
        return FileEdits.empty();
      }

      // editsPerFilePath is expected to have a single item representing the file
      String filePath = editsPerFilePath.keys().iterator().next();
      Collection<ContextAwareEdit> edits = editsPerFilePath.get(filePath);
      return FileEdits.create(
          Streams.stream(edits)
              .map(ContextAwareEdit::toEdit)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(Collectors.toList()),
          edits.iterator().next().getOldFilePath(),
          edits.iterator().next().getNewFilePath());
    }

    /**
     * A helper class that computes the 4 {@link GitFileDiff}s for a list of {@link
     * FileDiffCacheKey}s:
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

      private Map<WrappedKey, AllFileGitDiffs> execute(List<WrappedKey> wrappedKeys)
          throws DiffNotAvailableException {
        Map<WrappedKey, AllFileGitDiffs> keyToAllDiffs = new HashMap<>();

        List<WrappedKey> keysWithRebaseEdits =
            wrappedKeys.stream().filter(k -> !k.ignoreRebase()).collect(Collectors.toList());

        // TODO(ghareeb): as an enhancement, you can batch these calls as follows.
        // First batch: "old commit vs. new commit" and "new parent vs. new commit"
        // Second batch: "old parent vs. old commit" and "old parent vs. new parent"

        Map<FileDiffCacheKey, GitDiffEntity> mainDiffs =
            getGitFileDiffs(
                createGitKeys(
                    wrappedKeys,
                    k -> k.key().oldCommit(),
                    k -> k.key().newCommit(),
                    k -> k.key().newFilePath()));

        Map<FileDiffCacheKey, GitDiffEntity> oldVsParDiffs =
            getGitFileDiffs(
                createGitKeys(
                    keysWithRebaseEdits,
                    k -> k.oldParentId().get(), // oldParent is set for keysWithRebaseEdits
                    k -> k.key().oldCommit(),
                    k -> mainDiffs.get(k.key()).gitDiff().oldPath().orElse(null)));

        Map<FileDiffCacheKey, GitDiffEntity> newVsParDiffs =
            getGitFileDiffs(
                createGitKeys(
                    keysWithRebaseEdits,
                    k -> k.newParentId().get(), // newParent is set for keysWithRebaseEdits
                    k -> k.key().newCommit(),
                    k -> k.key().newFilePath()));

        Map<FileDiffCacheKey, GitDiffEntity> parVsParDiffs =
            getGitFileDiffs(
                createGitKeys(
                    keysWithRebaseEdits,
                    k -> k.oldParentId().get(),
                    k -> k.newParentId().get(),
                    k -> {
                      GitFileDiff newVsParDiff = newVsParDiffs.get(k.key()).gitDiff();
                      // TODO(ghareeb): Follow up on replacing key.newFilePath as a fallback.
                      // If the file was added between newParent and newCommit, we actually wouldn't
                      // need to have to determine the oldParent vs. newParent diff as nothing in
                      // that file could be an edit due to rebase anymore. Only if the returned diff
                      // is empty, the oldParent vs. newParent diff becomes relevant again (e.g. to
                      // identify a file deletion which was due to rebase. Check if the structure
                      // can be improved to make this clearer. Can we maybe even skip the diff in
                      // the first situation described?
                      return newVsParDiff.oldPath().orElse(k.key().newFilePath());
                    }));

        for (WrappedKey wrappedKey : wrappedKeys) {
          FileDiffCacheKey key = wrappedKey.key();
          AllFileGitDiffs.Builder builder =
              AllFileGitDiffs.builder().wrappedKey(wrappedKey).mainDiff(mainDiffs.get(key));

          if (wrappedKey.ignoreRebase()) {
            keyToAllDiffs.put(wrappedKey, builder.build());
            continue;
          }

          if (oldVsParDiffs.containsKey(key) && !oldVsParDiffs.get(key).gitDiff().isEmpty()) {
            builder.oldVsParDiff(Optional.of(oldVsParDiffs.get(key)));
          }

          if (newVsParDiffs.containsKey(key) && !newVsParDiffs.get(key).gitDiff().isEmpty()) {
            builder.newVsParDiff(Optional.of(newVsParDiffs.get(key)));
          }

          if (parVsParDiffs.containsKey(key) && !parVsParDiffs.get(key).gitDiff().isEmpty()) {
            builder.parVsParDiff(Optional.of(parVsParDiffs.get(key)));
          }

          keyToAllDiffs.put(wrappedKey, builder.build());
        }
        return keyToAllDiffs;
      }

      /**
       * Computes the git diff for the git keys of the input map {@code keys} parameter. The
       * computation uses the underlying {@link GitFileDiffCache}.
       */
      private Map<FileDiffCacheKey, GitDiffEntity> getGitFileDiffs(
          Map<FileDiffCacheKey, GitFileDiffCacheKey> keys) throws DiffNotAvailableException {
        ImmutableMap.Builder<FileDiffCacheKey, GitDiffEntity> result =
            ImmutableMap.builderWithExpectedSize(keys.size());
        ImmutableMap<GitFileDiffCacheKey, GitFileDiff> gitDiffs = gitCache.getAll(keys.values());
        for (FileDiffCacheKey key : keys.keySet()) {
          GitFileDiffCacheKey gitKey = keys.get(key);
          GitFileDiff gitFileDiff = gitDiffs.get(gitKey);
          result.put(key, GitDiffEntity.create(gitKey, gitFileDiff));
        }
        return result.build();
      }

      /**
       * Convert a list of {@link WrappedKey} to their corresponding {@link GitFileDiffCacheKey}
       * which can be used to call the underlying {@link GitFileDiffCache}.
       *
       * @param keys a list of input {@link WrappedKey}s.
       * @param oldCommitFn a function to compute the old commit of the git key.
       * @param newCommitFn a function to compute the new commit of the git key.
       * @param newPathFn a function to compute the new path of the git key.
       * @return a map of the input {@link FileDiffCacheKey} to the {@link GitFileDiffCacheKey}.
       */
      private Map<FileDiffCacheKey, GitFileDiffCacheKey> createGitKeys(
          List<WrappedKey> keys,
          Function<WrappedKey, ObjectId> oldCommitFn,
          Function<WrappedKey, ObjectId> newCommitFn,
          Function<WrappedKey, String> newPathFn) {
        Map<FileDiffCacheKey, GitFileDiffCacheKey> result = new HashMap<>();
        for (WrappedKey key : keys) {
          try {
            String path = newPathFn.apply(key);
            if (path != null) {
              result.put(
                  key.key(),
                  createGitKey(
                      key.key(), oldCommitFn.apply(key), newCommitFn.apply(key), path, rw));
            }
          } catch (IOException e) {
            logger.atWarning().log(
                "Failed to compute the git key for key %s: %s", key, e.getMessage());
          }
        }
        return result;
      }

      /** Returns the {@link GitFileDiffCacheKey} for the {@code key} input parameter. */
      private GitFileDiffCacheKey createGitKey(
          FileDiffCacheKey key, ObjectId aCommit, ObjectId bCommit, String pathNew, RevWalk rw)
          throws IOException {
        ObjectId oldTreeId =
            aCommit.equals(EMPTY_TREE_ID) ? EMPTY_TREE_ID : DiffUtil.getTreeId(rw, aCommit);
        ObjectId newTreeId = DiffUtil.getTreeId(rw, bCommit);
        return GitFileDiffCacheKey.builder()
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

    /**
     * An entity containing the 4 git diffs for a {@link FileDiffCacheKey}: 1) The old vs. new
     * commit 2) the old commit vs. the old parent 3) the new commit vs. the new parent 4) the old
     * parent vs. the new parent
     */
    @AutoValue
    abstract static class AllFileGitDiffs {
      abstract WrappedKey wrappedKey();

      abstract GitDiffEntity mainDiff();

      abstract Optional<GitDiffEntity> oldVsParDiff();

      abstract Optional<GitDiffEntity> newVsParDiff();

      abstract Optional<GitDiffEntity> parVsParDiff();

      static AllFileGitDiffs.Builder builder() {
        return new AutoValue_FileDiffCacheImpl_FileDiffLoader_AllFileGitDiffs.Builder();
      }

      @AutoValue.Builder
      public abstract static class Builder {

        public abstract Builder wrappedKey(WrappedKey value);

        public abstract Builder mainDiff(GitDiffEntity value);

        public abstract Builder oldVsParDiff(Optional<GitDiffEntity> value);

        public abstract Builder newVsParDiff(Optional<GitDiffEntity> value);

        public abstract Builder parVsParDiff(Optional<GitDiffEntity> value);

        public abstract AllFileGitDiffs build();
      }
    }

    /**
     * An entity containing a {@link GitFileDiffCacheKey} and its loaded value {@link GitFileDiff}.
     */
    @AutoValue
    abstract static class GitDiffEntity {
      public static GitDiffEntity create(GitFileDiffCacheKey gitKey, GitFileDiff gitDiff) {
        return new AutoValue_FileDiffCacheImpl_FileDiffLoader_GitDiffEntity(gitKey, gitDiff);
      }

      abstract GitFileDiffCacheKey gitKey();

      abstract GitFileDiff gitDiff();
    }

    /**
     * A wrapper entity to the {@link FileDiffCacheKey} that also includes the old parent commit ID,
     * the new parent commit ID and if we should ignore computing the rebase edits for that key.
     */
    @AutoValue
    abstract static class WrappedKey {
      abstract FileDiffCacheKey key();

      abstract boolean ignoreRebase();

      abstract Optional<ObjectId> oldParentId();

      abstract Optional<ObjectId> newParentId();

      abstract Builder toBuilder();

      static Builder builder() {
        return new AutoValue_FileDiffCacheImpl_FileDiffLoader_WrappedKey.Builder();
      }

      @AutoValue.Builder
      public abstract static class Builder {

        public abstract Builder oldParentId(Optional<ObjectId> value);

        public abstract Builder newParentId(Optional<ObjectId> value);

        public abstract Builder ignoreRebase(boolean value);

        public abstract Builder key(FileDiffCacheKey value);

        public abstract WrappedKey build();
      }
    }
  }
}
