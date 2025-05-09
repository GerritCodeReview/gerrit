// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.patch.filediff;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.patch.AutoMerger;
import com.google.gerrit.server.patch.ComparisonType;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffUtil;
import com.google.gerrit.server.patch.Text;
import com.google.gerrit.server.patch.filediff.EditTransformer.ContextAwareEdit;
import com.google.gerrit.server.patch.gitfilediff.FileHeaderUtil;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiff;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiffCacheImpl.DiffAlgorithmFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
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
import org.eclipse.jgit.util.sha1.SHA1;

/**
 * Cache for the single file diff between two commits for a single file path. This cache adds extra
 * Gerrit logic such as identifying edits due to rebase.
 *
 * <p>If the {@link FileDiffCacheKey#oldCommit()} is equal to {@link ObjectId#zeroId()}, the git
 * diff will be evaluated against the empty tree.
 */
@Singleton
public class FileDiffCacheImpl implements FileDiffCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DIFF = "gerrit_file_diff";

  private final LoadingCache<FileDiffCacheKey, FileDiffOutput> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(FileDiffCache.class).to(FileDiffCacheImpl.class);

        factory(AllDiffsEvaluator.Factory.class);

        persist(DIFF, FileDiffCacheKey.class, FileDiffOutput.class)
            .maximumWeight(10 << 20)
            .weigher(FileDiffWeigher.class)
            .version(10)
            .keySerializer(FileDiffCacheKey.Serializer.INSTANCE)
            .valueSerializer(FileDiffOutput.Serializer.INSTANCE)
            .loader(FileDiffLoader.class);
      }
    };
  }

  private enum MagicPath {
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
    private final GitRepositoryManager repoManager;
    private final AllDiffsEvaluator.Factory allDiffsEvaluatorFactory;

    @Inject
    FileDiffLoader(
        AllDiffsEvaluator.Factory allDiffsEvaluatorFactory, GitRepositoryManager manager) {
      this.allDiffsEvaluatorFactory = allDiffsEvaluatorFactory;
      this.repoManager = manager;
    }

    @Override
    public FileDiffOutput load(FileDiffCacheKey key) throws IOException, DiffNotAvailableException {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading a single key from file diff cache",
              Metadata.builder().filePath(key.newFilePath()).build())) {
        return loadAll(ImmutableList.of(key)).get(key);
      }
    }

    @Override
    public Map<FileDiffCacheKey, FileDiffOutput> loadAll(Iterable<? extends FileDiffCacheKey> keys)
        throws DiffNotAvailableException {
      try (TraceTimer timer = TraceContext.newTimer("Loading multiple keys from file diff cache")) {
        ImmutableMap.Builder<FileDiffCacheKey, FileDiffOutput> result = ImmutableMap.builder();

        Map<Project.NameKey, List<FileDiffCacheKey>> keysByProject =
            Streams.stream(keys)
                .distinct()
                .collect(Collectors.groupingBy(FileDiffCacheKey::project));

        for (Project.NameKey project : keysByProject.keySet()) {
          List<FileDiffCacheKey> fileKeys = new ArrayList<>();

          try (Repository repo = repoManager.openRepository(project);
              ObjectReader reader = repo.newObjectReader();
              RevWalk rw = new RevWalk(reader)) {

            for (FileDiffCacheKey key : keysByProject.get(project)) {
              if (key.newFilePath().equals(Patch.COMMIT_MSG)) {
                result.put(key, createMagicPathEntry(key, reader, rw, MagicPath.COMMIT));
              } else if (key.newFilePath().equals(Patch.MERGE_LIST)) {
                result.put(key, createMagicPathEntry(key, reader, rw, MagicPath.MERGE_LIST));
              } else {
                fileKeys.add(key);
              }
            }
            result.putAll(createFileEntries(reader, fileKeys, rw));
          } catch (IOException e) {
            logger.atWarning().log("Failed to open the repository %s: %s", project, e.getMessage());
          }
        }
        return result.build();
      }
    }

    private ComparisonType getComparisonType(
        RevWalk rw, ObjectReader reader, ObjectId oldCommitId, ObjectId newCommitId)
        throws IOException {
      if (oldCommitId.equals(ObjectId.zeroId())) {
        return ComparisonType.againstRoot();
      }
      RevCommit oldCommit = DiffUtil.getRevCommit(rw, oldCommitId);
      RevCommit newCommit = DiffUtil.getRevCommit(rw, newCommitId);
      for (int i = 0; i < newCommit.getParentCount(); i++) {
        if (newCommit.getParent(i).equals(oldCommit)) {
          return ComparisonType.againstParent(i + 1);
        }
      }
      // TODO(ghareeb): it's not trivial to distinguish if diff with old commit is against another
      // patchset or auto-merge. Looking at the commit message of old commit gives a strong
      // signal that we are diffing against auto-merge, though not 100% accurate (e.g. if old commit
      // has the auto-merge prefix in the commit message). A better resolution would be to move the
      // COMMIT_MSG and MERGE_LIST evaluations outside of the diff cache. For more details, see
      // discussion in
      // https://gerrit-review.googlesource.com/c/gerrit/+/280519/6..18/java/com/google/gerrit/server/patch/FileDiffCache.java#b540
      String oldCommitMsgTxt = new String(Text.forCommit(reader, oldCommit).getContent(), UTF_8);
      if (oldCommitMsgTxt.contains(AutoMerger.AUTO_MERGE_MSG_PREFIX)) {
        return ComparisonType.againstAutoMerge();
      }
      return ComparisonType.againstOtherPatchSet();
    }

    /**
     * Creates a {@link FileDiffOutput} entry for the "Commit message" or "Merge list" magic paths.
     */
    private FileDiffOutput createMagicPathEntry(
        FileDiffCacheKey key, ObjectReader reader, RevWalk rw, MagicPath magicPath) {
      try {
        RawTextComparator cmp = comparatorFor(key.whitespace());
        ComparisonType comparisonType =
            getComparisonType(rw, reader, key.oldCommit(), key.newCommit());
        RevCommit aCommit =
            key.oldCommit().equals(ObjectId.zeroId())
                ? null
                : DiffUtil.getRevCommit(rw, key.oldCommit());
        RevCommit bCommit = DiffUtil.getRevCommit(rw, key.newCommit());
        return magicPath == MagicPath.COMMIT
            ? createCommitEntry(reader, aCommit, bCommit, comparisonType, cmp, key.diffAlgorithm())
            : createMergeListEntry(
                reader, aCommit, bCommit, comparisonType, cmp, key.diffAlgorithm());
      } catch (IOException e) {
        logger.atWarning().log("Failed to compute commit entry for key %s", key);
      }
      return FileDiffOutput.empty(key.newFilePath(), key.oldCommit(), key.newCommit());
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

    /**
     * Creates a commit entry. {@code oldCommit} is null if the comparison is against a root commit.
     */
    private FileDiffOutput createCommitEntry(
        ObjectReader reader,
        @Nullable RevCommit oldCommit,
        RevCommit newCommit,
        ComparisonType comparisonType,
        RawTextComparator rawTextComparator,
        GitFileDiffCacheImpl.DiffAlgorithm diffAlgorithm)
        throws IOException {
      Text aText =
          oldCommit == null || comparisonType.isAgainstParentOrAutoMerge()
              ? Text.EMPTY
              : Text.forCommit(reader, oldCommit);
      Text bText = Text.forCommit(reader, newCommit);
      return createMagicFileDiffOutput(
          oldCommit,
          newCommit,
          comparisonType,
          rawTextComparator,
          aText,
          bText,
          Patch.COMMIT_MSG,
          diffAlgorithm);
    }

    /**
     * Creates a merge list entry. {@code oldCommit} is null if the comparison is against a root
     * commit.
     */
    private FileDiffOutput createMergeListEntry(
        ObjectReader reader,
        @Nullable RevCommit oldCommit,
        RevCommit newCommit,
        ComparisonType comparisonType,
        RawTextComparator rawTextComparator,
        GitFileDiffCacheImpl.DiffAlgorithm diffAlgorithm)
        throws IOException {
      Text aText =
          oldCommit == null || comparisonType.isAgainstParentOrAutoMerge()
              ? Text.EMPTY
              : Text.forMergeList(comparisonType, reader, oldCommit);
      Text bText = Text.forMergeList(comparisonType, reader, newCommit);
      return createMagicFileDiffOutput(
          oldCommit,
          newCommit,
          comparisonType,
          rawTextComparator,
          aText,
          bText,
          Patch.MERGE_LIST,
          diffAlgorithm);
    }

    private static FileDiffOutput createMagicFileDiffOutput(
        @Nullable ObjectId oldCommit,
        ObjectId newCommit,
        ComparisonType comparisonType,
        RawTextComparator rawTextComparator,
        Text aText,
        Text bText,
        String fileName,
        GitFileDiffCacheImpl.DiffAlgorithm diffAlgorithm) {
      byte[] rawHdr = getRawHeader(!comparisonType.isAgainstParentOrAutoMerge(), fileName);
      byte[] aContent = aText.getContent();
      byte[] bContent = bText.getContent();
      SHA1 aContentDigest = SHA1.newInstance();
      aContentDigest.update(aContent);
      ObjectId aSha = ObjectId.fromRaw(aContentDigest.digest());
      SHA1 bContentDigest = SHA1.newInstance();
      bContentDigest.update(bContent);
      ObjectId bSha = ObjectId.fromRaw(aContentDigest.digest());
      long size = bContent.length;
      long sizeDelta = size - aContent.length;
      RawText aRawText = new RawText(aContent);
      RawText bRawText = new RawText(bContent);
      EditList edits =
          DiffAlgorithmFactory.create(diffAlgorithm).diff(rawTextComparator, aRawText, bRawText);
      FileHeader fileHeader = new FileHeader(rawHdr, edits, PatchType.UNIFIED);
      Patch.ChangeType changeType = FileHeaderUtil.getChangeType(fileHeader);
      return FileDiffOutput.builder()
          .oldCommitId(oldCommit == null ? ObjectId.zeroId() : oldCommit)
          .newCommitId(newCommit)
          .comparisonType(comparisonType)
          .oldPath(FileHeaderUtil.getOldPath(fileHeader))
          .newPath(FileHeaderUtil.getNewPath(fileHeader))
          .oldSha(Optional.of(aSha))
          .newSha(Optional.of(bSha))
          .changeType(changeType)
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
      Map<AugmentedFileDiffCacheKey, AllFileGitDiffs> allFileDiffs =
          allDiffsEvaluatorFactory.create(rw).execute(wrapKeys(keys, rw));

      Map<FileDiffCacheKey, FileDiffOutput> result = new HashMap<>();

      for (AugmentedFileDiffCacheKey augmentedKey : allFileDiffs.keySet()) {
        AllFileGitDiffs allDiffs = allFileDiffs.get(augmentedKey);
        GitFileDiff mainGitDiff = allDiffs.mainDiff().gitDiff();

        if (mainGitDiff.isNegative()) {
          // If the result of the git diff computation was negative, i.e. due to timeout, cache a
          // negative result.
          result.put(
              augmentedKey.key(),
              FileDiffOutput.createNegative(
                  mainGitDiff.newPath().orElse(""),
                  augmentedKey.key().oldCommit(),
                  augmentedKey.key().newCommit()));
          continue;
        }

        FileEdits rebaseFileEdits = FileEdits.empty();
        if (!augmentedKey.ignoreRebase()) {
          rebaseFileEdits = computeRebaseEdits(allDiffs);
        }
        ImmutableList<Edit> rebaseEdits = rebaseFileEdits.edits();

        ObjectId oldTreeId = allDiffs.mainDiff().gitKey().oldTree();

        RevTree aTree = oldTreeId.equals(ObjectId.zeroId()) ? null : rw.parseTree(oldTreeId);
        RevTree bTree = rw.parseTree(allDiffs.mainDiff().gitKey().newTree());

        FileSizeEvaluator aEvaluator = new FileSizeEvaluator(reader, aTree);
        ObjectId oldSha =
            aTree != null && mainGitDiff.oldMode().isPresent() && mainGitDiff.oldPath().isPresent()
                ? aEvaluator.getFileObjectId(
                    mainGitDiff.oldId(), mainGitDiff.oldMode().get(), mainGitDiff.oldPath().get())
                : ObjectId.zeroId();
        Long oldSize = aEvaluator.compute(oldSha);
        FileSizeEvaluator bEvaluator = new FileSizeEvaluator(reader, bTree);
        ObjectId newSha =
            mainGitDiff.newMode().isPresent() && mainGitDiff.newPath().isPresent()
                ? bEvaluator.getFileObjectId(
                    mainGitDiff.newId(), mainGitDiff.newMode().get(), mainGitDiff.newPath().get())
                : ObjectId.zeroId();
        Long newSize = bEvaluator.compute(newSha);

        ObjectId oldCommit = augmentedKey.key().oldCommit();
        ObjectId newCommit = augmentedKey.key().newCommit();
        FileDiffOutput fileDiff =
            FileDiffOutput.builder()
                .oldCommitId(oldCommit)
                .newCommitId(newCommit)
                .comparisonType(getComparisonType(rw, reader, oldCommit, newCommit))
                .changeType(mainGitDiff.changeType())
                .patchType(mainGitDiff.patchType())
                .oldPath(mainGitDiff.oldPath())
                .newPath(mainGitDiff.newPath())
                .oldMode(mainGitDiff.oldMode())
                .newMode(mainGitDiff.newMode())
                .oldSha(!oldSha.equals(ObjectId.zeroId()) ? Optional.of(oldSha) : Optional.empty())
                .newSha(!newSha.equals(ObjectId.zeroId()) ? Optional.of(newSha) : Optional.empty())
                .headerLines(FileHeaderUtil.getHeaderLines(mainGitDiff.fileHeader()))
                .edits(asTaggedEdits(mainGitDiff.edits(), rebaseEdits))
                .size(newSize)
                .sizeDelta(newSize - oldSize)
                .build();

        result.put(augmentedKey.key(), fileDiff);
      }

      return result;
    }

    /**
     * Convert the list of input keys {@link FileDiffCacheKey} to a list of {@link
     * AugmentedFileDiffCacheKey} that also include the old and new parent commit IDs, and a boolean
     * that indicates whether we should include the rebase edits for each key.
     *
     * <p>The output list is expected to have the same size of the input list, i.e. we map all keys.
     */
    private List<AugmentedFileDiffCacheKey> wrapKeys(List<FileDiffCacheKey> keys, RevWalk rw) {
      List<AugmentedFileDiffCacheKey> result = new ArrayList<>();
      for (FileDiffCacheKey key : keys) {
        if (key.oldCommit().equals(ObjectId.zeroId())) {
          result.add(AugmentedFileDiffCacheKey.builder().key(key).ignoreRebase(true).build());
          continue;
        }
        try {
          RevCommit oldRevCommit = DiffUtil.getRevCommit(rw, key.oldCommit());
          RevCommit newRevCommit = DiffUtil.getRevCommit(rw, key.newCommit());
          if (!DiffUtil.areRelated(oldRevCommit, newRevCommit)) {
            result.add(
                AugmentedFileDiffCacheKey.builder()
                    .key(key)
                    .oldParentId(Optional.of(oldRevCommit.getParent(0).getId()))
                    .newParentId(Optional.of(newRevCommit.getParent(0).getId()))
                    .ignoreRebase(false)
                    .build());
          } else {
            result.add(AugmentedFileDiffCacheKey.builder().key(key).ignoreRebase(true).build());
          }
        } catch (IOException e) {
          logger.atWarning().log(
              "Failed to evaluate commits relation for key "
                  + key
                  + ". Skipping this key: "
                  + e.getMessage(),
              e);
          result.add(AugmentedFileDiffCacheKey.builder().key(key).ignoreRebase(true).build());
        }
      }
      return result;
    }

    private static ImmutableList<TaggedEdit> asTaggedEdits(
        List<Edit> normalEdits, List<Edit> rebaseEdits) {
      Set<Edit> rebaseEditsSet = new HashSet<>(rebaseEdits);
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
     * <ol>
     *   <li>The edits between the old commit and its parent {@link
     *       AllFileGitDiffs#oldVsParentDiff}.
     *   <li>The edits between the new commit and its parent {@link
     *       AllFileGitDiffs#newVsParentDiff}.
     *   <li>The edits between the parents of the old commit and new commits {@link
     *       AllFileGitDiffs#parentVsParentDiff}.
     * </ol>
     *
     * @param diffs an entity containing 4 sets of edits: those between the old and new commit,
     *     between the old and new commits vs. their parents, and between the old and new parents.
     * @return the list of edits that are due to rebase.
     */
    private FileEdits computeRebaseEdits(AllFileGitDiffs diffs) {
      if (!diffs.parentVsParentDiff().isPresent()) {
        return FileEdits.empty();
      }

      GitFileDiff parentVsParentDiff = diffs.parentVsParentDiff().get().gitDiff();

      EditTransformer editTransformer =
          new EditTransformer(
              ImmutableList.of(
                  FileEdits.create(
                      parentVsParentDiff.edits().stream().collect(toImmutableList()),
                      parentVsParentDiff.oldPath(),
                      parentVsParentDiff.newPath())));

      if (diffs.oldVsParentDiff().isPresent()) {
        GitFileDiff oldVsParDiff = diffs.oldVsParentDiff().get().gitDiff();
        editTransformer.transformReferencesOfSideA(
            ImmutableList.of(
                FileEdits.create(
                    oldVsParDiff.edits().stream().collect(toImmutableList()),
                    oldVsParDiff.oldPath(),
                    oldVsParDiff.newPath())));
      }

      if (diffs.newVsParentDiff().isPresent()) {
        GitFileDiff newVsParDiff = diffs.newVsParentDiff().get().gitDiff();
        editTransformer.transformReferencesOfSideB(
            ImmutableList.of(
                FileEdits.create(
                    newVsParDiff.edits().stream().collect(toImmutableList()),
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
          edits.stream()
              .map(ContextAwareEdit::toEdit)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .map(Edit::fromJGitEdit)
              .collect(toImmutableList()),
          edits.iterator().next().getOldFilePath(),
          edits.iterator().next().getNewFilePath());
    }
  }
}
