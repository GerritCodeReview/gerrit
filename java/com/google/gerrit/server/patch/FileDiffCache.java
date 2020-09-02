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

package com.google.gerrit.server.patch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.EditTransformer.ContextAwareEdit;
import com.google.gerrit.server.patch.GitFileDiffCache.Key.DiffAlgorithm;
import com.google.gerrit.server.patch.entities.FileEdits;
import com.google.gerrit.server.patch.entities.GitFileDiff;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Cache for the single file diff between two commits for a single file path. This cache adds extra
 * Gerrit logic such as identifying the edits due to rebase
 */
public class FileDiffCache {
  static final String DIFF = "gerrit_diff";

  private final LoadingCache<Key, PatchListEntry> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(DIFF, Key.class, PatchListEntry.class)
            .maximumWeight(10 << 20)
            .loader(FileDiffLoader.class);
      }
    };
  }

  @Inject
  public FileDiffCache(@Named(DIFF) LoadingCache<Key, PatchListEntry> cache) {
    this.cache = cache;
  }

  public PatchListEntry get(Key key) throws ExecutionException {
    return cache.get(key);
  }

  public ImmutableMap<Key, PatchListEntry> getAll(Collection<Key> keys) throws ExecutionException {
    return cache.getAll(keys);
  }

  static class FileDiffLoader extends CacheLoader<Key, PatchListEntry> {
    private final DiffUtil diffUtil;
    private final GitFileDiffCache gitCache;
    private final GitRepositoryManager repoManager;

    @Inject
    FileDiffLoader(GitRepositoryManager manager, GitFileDiffCache gitCache, DiffUtil diffUtil) {
      this.repoManager = manager;
      this.gitCache = gitCache;
      this.diffUtil = diffUtil;
    }

    @Override
    public PatchListEntry load(Key key) throws IOException, ExecutionException {
      try (Repository repo = repoManager.openRepository(key.project());
          ObjectReader reader = repo.newObjectReader();
          ObjectInserter ins = repo.newObjectInserter();
          RevWalk rw = new RevWalk(reader)) {
        if (key.newFilePath().equals(Patch.COMMIT_MSG)) {
          return forCommit(key, reader, rw);
        }
        if (key.newFilePath().equals(Patch.MERGE_LIST)) {
          return forMergeList(key, reader, rw);
        }
        return forFile(key, repo, ins, rw);
      }
    }

    @Override
    public Map<Key, PatchListEntry> loadAll(Iterable<? extends Key> keys)
        throws IOException, ExecutionException {
      Map<Key, PatchListEntry> result = new HashMap<>();
      List<Key> keyList = Lists.newArrayList(keys);

      Map<Project.NameKey, List<Key>> keysByProject =
          keyList.stream().collect(Collectors.groupingBy(Key::project));

      for (Project.NameKey project : keysByProject.keySet()) {
        List<Key> fileKeys = new ArrayList<>();

        try (Repository repo = repoManager.openRepository(project);
            ObjectReader reader = repo.newObjectReader();
            ObjectInserter ins = repo.newObjectInserter();
            RevWalk rw = new RevWalk(reader)) {

          for (Key key : keysByProject.get(project)) {
            if (key.newFilePath().equals(Patch.COMMIT_MSG)) {
              result.put(key, forCommit(key, reader, rw));
            } else if (key.newFilePath().equals(Patch.MERGE_LIST)) {
              result.put(key, forMergeList(key, reader, rw));
            } else {
              fileKeys.add(key);
            }
          }
          result.putAll(forFiles(fileKeys, repo, ins, rw));
        }
      }
      return result;
    }

    private PatchListEntry forCommit(Key key, ObjectReader reader, RevWalk rw) throws IOException {
      RawTextComparator cmp = comparatorFor(key.ws());
      RevCommit aCommit =
          key.cmp().isAgainstParentOrAutoMerge()
              ? null
              : diffUtil.getRevCommit(rw, key.oldCommit());
      RevCommit bCommit = diffUtil.getRevCommit(rw, key.newCommit());
      Text aText = aCommit != null ? Text.forCommit(reader, aCommit) : Text.EMPTY;
      Text bText = Text.forCommit(reader, bCommit);
      return createPatchListEntry(cmp, aCommit, aText, bText, Patch.COMMIT_MSG);
    }

    private PatchListEntry forMergeList(Key key, ObjectReader reader, RevWalk rw)
        throws IOException {
      RawTextComparator cmp = comparatorFor(key.ws());
      RevCommit aCommit =
          key.cmp().isAgainstParentOrAutoMerge()
              ? null
              : diffUtil.getRevCommit(rw, key.oldCommit());
      RevCommit bCommit = diffUtil.getRevCommit(rw, key.newCommit());
      Text aText = aCommit != null ? Text.forMergeList(key.cmp(), reader, aCommit) : Text.EMPTY;
      Text bText = Text.forMergeList(key.cmp(), reader, bCommit);
      return createPatchListEntry(cmp, aCommit, aText, bText, Patch.MERGE_LIST);
    }

    private Map<Key, PatchListEntry> forFiles(
        List<Key> keys, Repository repo, ObjectInserter ins, RevWalk rw)
        throws IOException, ExecutionException {
      Map<Key, AllFileGitDiffs> fileToAllDiffs =
          new AllDiffsEvaluator(repo, ins, rw).evaluate(keys);
      Map<Key, PatchListEntry> result = new HashMap<>();

      for (Map.Entry<Key, AllFileGitDiffs> entry : fileToAllDiffs.entrySet()) {
        AllFileGitDiffs allDiffs = entry.getValue();
        GitFileDiff gitDiff = allDiffs.main.value;

        Set<ContextAwareEdit> editsDueToRebase = ImmutableSet.of();

        if (entry.getValue().hasRebaseEdits) {
          editsDueToRebase =
              getRebaseEdits(
                  ImmutableList.of(
                      gitDiff,
                      allDiffs.oldVsPar.value,
                      allDiffs.newVsPar.value,
                      allDiffs.parVsPar.value));
        }

        FileHeader fileHeader = gitDiff.fileHeader().jgitHeader();

        PatchListEntry ple =
            new PatchListEntry(
                fileHeader,
                fileHeader.toEditList(),
                getContentEdits(editsDueToRebase),
                gitDiff.newSize(),
                gitDiff.newSize() - gitDiff.oldSize());

        if (EditTransformer.toEdits(ple).allMatch(editsDueToRebase::contains)) {
          result.put(entry.getKey(), PatchListEntry.empty(entry.getKey().newFilePath()));
        } else {
          result.put(entry.getKey(), ple);
        }
      }

      return result;
    }

    private class AllDiffsEvaluator {
      private Repository repo;
      private ObjectInserter ins;
      private RevWalk rw;

      private AllDiffsEvaluator(Repository repo, ObjectInserter ins, RevWalk rw) {
        this.repo = repo;
        this.ins = ins;
        this.rw = rw;
      }

      private Map<Key, AllFileGitDiffs> evaluate(List<Key> keys)
          throws ExecutionException, IOException {
        Map<Key, AllFileGitDiffs> fileToAllDiffs = new HashMap<>();
        Map<Key, GitFileDiffCache.Key> keyToGitKeys = new HashMap<>();

        for (Key key : keys) {
          AllFileGitDiffs allDiffs = new AllFileGitDiffs();
          fileToAllDiffs.put(key, allDiffs);
          if (diffUtil.areRelated(rw, key.oldCommit(), key.newCommit())) {
            allDiffs.hasRebaseEdits = true;
            allDiffs.oldParent = diffUtil.getParentCommit(repo, ins, rw, 0, key.oldCommit());
            allDiffs.newParent = diffUtil.getParentCommit(repo, ins, rw, 0, key.newCommit());
          }
        }

        getGitDiffsForManyKeys(
            keys,
            fileToAllDiffs,
            (Key key) -> key.oldCommit(),
            (Key key) -> key.newCommit(),
            (Key key) -> null,
            (AllFileGitDiffs allValues, GitFileDiffCache.Key key) -> allValues.main.key = key,
            (AllFileGitDiffs allValues, GitFileDiff val) -> allValues.main.value = val,
            true);

        getGitDiffsForManyKeys(
            keys,
            fileToAllDiffs,
            (Key key) -> getParentObjectId(key.oldCommit()),
            (Key key) -> key.oldCommit(),
            (Key key) -> fileToAllDiffs.get(key).main.value.oldName(),
            (AllFileGitDiffs allValues, GitFileDiffCache.Key key) -> allValues.oldVsPar.key = key,
            (AllFileGitDiffs allValues, GitFileDiff val) -> allValues.oldVsPar.value = val,
            false);

        getGitDiffsForManyKeys(
            keys,
            fileToAllDiffs,
            (Key key) -> getParentObjectId(key.newCommit()),
            (Key key) -> key.newCommit(),
            (Key key) -> fileToAllDiffs.get(key).main.value.newName(),
            (AllFileGitDiffs allValues, GitFileDiffCache.Key key) -> allValues.newVsPar.key = key,
            (AllFileGitDiffs allValues, GitFileDiff val) -> allValues.newVsPar.value = val,
            false);

        getGitDiffsForManyKeys(
            keys,
            fileToAllDiffs,
            (Key key) -> fileToAllDiffs.get(key).oldParent,
            (Key key) -> fileToAllDiffs.get(key).newParent,
            (Key key) -> {
              GitFileDiff newVsParDiff = fileToAllDiffs.get(key).newVsPar.value;
              return newVsParDiff.oldName() != null ? newVsParDiff.oldName() : key.newFilePath();
            },
            (AllFileGitDiffs allValues, GitFileDiffCache.Key key) -> allValues.parVsPar.key = key,
            (AllFileGitDiffs allValues, GitFileDiff val) -> allValues.parVsPar.value = val,
            false);

        return fileToAllDiffs;
      }

      private ObjectId getParentObjectId(ObjectId objectId) {
        try {
          return diffUtil.getParentCommit(repo, ins, rw, 0, objectId);
        } catch (IOException e) {
          e.printStackTrace();
        } finally {
        }
        return null;
      }

      private void getGitDiffsForManyKeys(
          List<Key> keys,
          Map<Key, AllFileGitDiffs> fileToAllDiffs,
          Function<Key, ObjectId> oldCommitExtractor,
          Function<Key, ObjectId> newCommitExtractor,
          Function<Key, String> newNameExtractor,
          BiConsumer<AllFileGitDiffs, GitFileDiffCache.Key> keyFiller,
          BiConsumer<AllFileGitDiffs, GitFileDiff> valueFiller,
          boolean isMain)
          throws IOException, ExecutionException {
        Map<Key, GitFileDiffCache.Key> keyToGitKeys = new HashMap<>();

        List<GitFileDiffCache.Key> gitKeys = new ArrayList<>();

        for (Key key : keys) {
          if (fileToAllDiffs.get(key).hasRebaseEdits || isMain) {
            GitFileDiffCache.Key gitKey =
                getGitKeyFor(
                    key,
                    oldCommitExtractor.apply(key),
                    newCommitExtractor.apply(key),
                    newNameExtractor.apply(key),
                    rw);
            gitKeys.add(gitKey);
            keyToGitKeys.put(key, gitKey);
            keyFiller.accept(fileToAllDiffs.get(key), gitKey);
          }
        }

        retrieveAndBackFill(gitKeys, keyToGitKeys, fileToAllDiffs, valueFiller);
      }

      private void retrieveAndBackFill(
          List<GitFileDiffCache.Key> gitKeys,
          Map<Key, GitFileDiffCache.Key> keyMap,
          Map<Key, AllFileGitDiffs> fileToAllDiffs,
          BiConsumer<AllFileGitDiffs, GitFileDiff> valueFiller)
          throws ExecutionException {
        ImmutableMap<GitFileDiffCache.Key, GitFileDiff> gitDiffs = gitCache.getAll(gitKeys);
        backFill(keyMap, gitDiffs, fileToAllDiffs, valueFiller);
      }

      private void backFill(
          Map<Key, GitFileDiffCache.Key> keyToGitKeysMap,
          Map<GitFileDiffCache.Key, GitFileDiff> values,
          Map<Key, AllFileGitDiffs> fileToAllDiffs,
          BiConsumer<AllFileGitDiffs, GitFileDiff> valueFiller) {
        for (Key k : keyToGitKeysMap.keySet()) {
          GitFileDiffCache.Key gitKey = keyToGitKeysMap.get(k);
          GitFileDiff gitFileDiff = values.get(gitKey);
          if (gitFileDiff != null) {
            AllFileGitDiffs allGitDiffs = fileToAllDiffs.get(k);
            valueFiller.accept(allGitDiffs, gitFileDiff);
          }
        }
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
        private GitFileDiffCache.Key key;
        private GitFileDiff value;
      }
    }

    private PatchListEntry forFile(Key key, Repository repo, ObjectInserter ins, RevWalk rw)
        throws IOException, ExecutionException {
      ObjectId oldCommit = key.oldCommit();
      ObjectId newCommit = key.newCommit();

      GitFileDiff gitDiff = gitCache.get(getGitKeyFor(key, oldCommit, newCommit, rw));

      Set<ContextAwareEdit> editsDueToRebase = ImmutableSet.of();
      if (diffUtil.areRelated(rw, oldCommit, newCommit)) {
        ObjectId oldParent = diffUtil.getParentCommit(repo, ins, rw, 0, oldCommit);
        ObjectId newParent = diffUtil.getParentCommit(repo, ins, rw, 0, newCommit);

        ImmutableList<GitFileDiff> diffsWithParents =
            ImmutableList.<GitFileDiff>builderWithExpectedSize(4)
                .add(gitDiff)
                .addAll(
                    getGitDiffsWithParents(
                        rw,
                        key,
                        oldParent,
                        oldCommit,
                        newParent,
                        newCommit,
                        gitDiff.oldName(),
                        gitDiff.newName()))
                .build();

        editsDueToRebase = getRebaseEdits(diffsWithParents);
      }

      FileHeader fileHeader = gitDiff.fileHeader().jgitHeader();

      PatchListEntry result =
          new PatchListEntry(
              fileHeader,
              fileHeader.toEditList(),
              getContentEdits(editsDueToRebase),
              gitDiff.newSize(),
              gitDiff.newSize() - gitDiff.oldSize());

      if (EditTransformer.toEdits(result).allMatch(editsDueToRebase::contains)) {
        return PatchListEntry.empty(key.newFilePath());
      }

      return result;
    }

    private Set<ContextAwareEdit> getRebaseEdits(ImmutableList<GitFileDiff> diffs) {
      GitFileDiff parVsParDiff = diffs.get(3);
      if (parVsParDiff.isEmpty()) {
        return ImmutableSet.of();
      }
      GitFileDiff gitDiff = diffs.get(0);
      GitFileDiff oldVsParDiff = diffs.get(1);
      GitFileDiff newVsParDiff = diffs.get(2);

      EditTransformer editTransformer =
          new EditTransformer(
              ImmutableList.of(
                  FileEdits.create(
                      parVsParDiff.edits(),
                      parVsParDiff.fileHeader().getOldPath(),
                      parVsParDiff.fileHeader().getNewPath(),
                      parVsParDiff.fileHeader().getChangeType())));

      if (!oldVsParDiff.isEmpty()) {
        editTransformer.transformReferencesOfSideA(
            ImmutableList.of(
                FileEdits.create(
                    oldVsParDiff.edits(),
                    oldVsParDiff.fileHeader().getOldPath(),
                    oldVsParDiff.fileHeader().getNewPath(),
                    oldVsParDiff.changeType())));
      }

      if (!newVsParDiff.isEmpty()) {
        editTransformer.transformReferencesOfSideB(
            ImmutableList.of(
                FileEdits.create(
                    newVsParDiff.edits(),
                    newVsParDiff.fileHeader().getOldPath(),
                    newVsParDiff.fileHeader().getNewPath(),
                    newVsParDiff.changeType())));
      }

      Multimap<String, ContextAwareEdit> editsPerFilePath = editTransformer.getEditsPerFilePath();

      return getEditsDueToRebase(
          editsPerFilePath, gitDiff.changeType(), gitDiff.oldName(), gitDiff.newName());
    }

    private ImmutableList<GitFileDiff> getGitDiffsWithParents(
        RevWalk rw,
        Key key,
        ObjectId oldParent,
        ObjectId oldCommit,
        ObjectId newParent,
        ObjectId newCommit,
        String oldName,
        String newName)
        throws IOException, ExecutionException {
      GitFileDiff oldVsParDiff = gitCache.get(getGitKeyFor(key, oldParent, oldCommit, oldName, rw));

      GitFileDiff newVsParDiff = gitCache.get(getGitKeyFor(key, newParent, newCommit, newName, rw));

      GitFileDiff parVsParDiff =
          gitCache.get(
              getGitKeyFor(
                  key,
                  oldParent,
                  newParent,
                  newVsParDiff.oldName() != null ? newVsParDiff.oldName() : key.newFilePath(),
                  rw));

      return ImmutableList.of(oldVsParDiff, newVsParDiff, parVsParDiff);
    }

    private List<GitFileDiffCache.Key> getGitDiffKeysWithParents(
        RevWalk rw,
        Key key,
        ObjectId oldParent,
        ObjectId oldCommit,
        ObjectId newParent,
        ObjectId newCommit,
        String oldName,
        String newName)
        throws IOException {
      List<GitFileDiffCache.Key> result = new ArrayList<>();
      result.add(getGitKeyFor(key, oldParent, oldCommit, oldName, rw));
      result.add(getGitKeyFor(key, newParent, newCommit, newName, rw));
      return result;
    }

    private GitFileDiffCache.Key getGitKeyFor(
        Key key, ObjectId aCommit, ObjectId bCommit, RevWalk rw) throws IOException {
      return getGitKeyFor(key, aCommit, bCommit, null, rw);
    }

    private GitFileDiffCache.Key getGitKeyFor(
        Key key, ObjectId aCommit, ObjectId bCommit, String pathNew, RevWalk rw)
        throws IOException {
      ObjectId oldTreeId = diffUtil.getTreeId(rw, aCommit);
      ObjectId newTreeId = diffUtil.getTreeId(rw, bCommit);
      return GitFileDiffCache.Key.create(
          key.project(),
          oldTreeId,
          newTreeId,
          pathNew == null ? key.newFilePath() : pathNew,
          key.similarityLevel(),
          key.diffAlgorithm(),
          key.ws());
    }

    private static Set<ContextAwareEdit> getEditsDueToRebase(
        Multimap<String, ContextAwareEdit> editsDueToRebasePerFilePath,
        ChangeType changeType,
        String oldPath,
        String newPath) {
      if (editsDueToRebasePerFilePath.isEmpty()) {
        return ImmutableSet.of();
      }

      if (changeType == ChangeType.DELETED) {
        return ImmutableSet.copyOf(editsDueToRebasePerFilePath.get(oldPath));
      }
      return ImmutableSet.copyOf(editsDueToRebasePerFilePath.get(newPath));
    }

    private static Set<Edit> getContentEdits(Set<ContextAwareEdit> edits) {
      return edits.stream()
          .map(ContextAwareEdit::toEdit)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(toSet());
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
  }

  @AutoValue
  public abstract static class Key implements Serializable {
    public static Key create(
        Project.NameKey project,
        ObjectId oldCommit,
        ObjectId newCommit,
        String newFilePath,
        @Nullable Integer similarityLevel,
        @Nullable DiffAlgorithm diffAlgorithm,
        DiffPreferencesInfo.Whitespace ws,
        ComparisonType cmp) {
      return new AutoValue_FileDiffCache_Key(
          project, oldCommit, newCommit, newFilePath, similarityLevel, diffAlgorithm, ws, cmp);
    }

    public abstract Project.NameKey project();

    public abstract ObjectId oldCommit();

    public abstract ObjectId newCommit();

    public abstract String newFilePath();

    @Nullable
    public abstract Integer similarityLevel();

    @Nullable
    public abstract DiffAlgorithm diffAlgorithm();

    public abstract DiffPreferencesInfo.Whitespace ws();

    public abstract ComparisonType cmp();
  }
}
