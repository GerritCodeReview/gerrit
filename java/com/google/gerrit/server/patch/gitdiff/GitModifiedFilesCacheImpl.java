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

package com.google.gerrit.server.patch.gitdiff;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.GitModifiedFilesKeyProto;
import com.google.gerrit.server.cache.proto.Cache.ModifiedFilesProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffUtil;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/** Implementation of the {@link GitModifiedFilesCache} */
public class GitModifiedFilesCacheImpl implements GitModifiedFilesCache {
  private static final String GIT_MODIFIED_FILES = "git_modified_files";
  private static final ImmutableMap<ChangeType, Patch.ChangeType> changeTypeMap =
      ImmutableMap.of(
          DiffEntry.ChangeType.ADD,
          Patch.ChangeType.ADDED,
          DiffEntry.ChangeType.MODIFY,
          Patch.ChangeType.MODIFIED,
          DiffEntry.ChangeType.DELETE,
          Patch.ChangeType.DELETED,
          DiffEntry.ChangeType.RENAME,
          Patch.ChangeType.RENAMED,
          DiffEntry.ChangeType.COPY,
          Patch.ChangeType.COPIED);

  private LoadingCache<Key, ImmutableList<ModifiedFile>> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(GitModifiedFilesCache.class).to(GitModifiedFilesCacheImpl.class);

        persist(GIT_MODIFIED_FILES, Key.class, new TypeLiteral<ImmutableList<ModifiedFile>>() {})
            .keySerializer(Key.KeySerializer.INSTANCE)
            .valueSerializer(ValueSerializer.INSTANCE)
            // The documentation has some defaults and recommendations for setting the cache
            // attributes:
            // https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#cache.
            .maximumWeight(10 << 20)
            .weigher(GitModifiedFilesWeigher.class)
            // The cache is using the default disk limit as per section cache.<name>.diskLimit
            // in the cache documentation link.
            .version(1)
            .loader(GitModifiedFilesCacheImpl.Loader.class);
      }
    };
  }

  @Inject
  public GitModifiedFilesCacheImpl(
      @Named(GIT_MODIFIED_FILES) LoadingCache<Key, ImmutableList<ModifiedFile>> cache) {
    this.cache = cache;
  }

  @Override
  public ImmutableList<ModifiedFile> get(Key key) throws DiffNotAvailableException {
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  static class Loader extends CacheLoader<Key, ImmutableList<ModifiedFile>> {
    private final GitRepositoryManager repoManager;

    @Inject
    Loader(GitRepositoryManager repoManager) {
      this.repoManager = repoManager;
    }

    @Override
    public ImmutableList<ModifiedFile> load(Key key) throws IOException {
      try (Repository repo = repoManager.openRepository(key.project());
          ObjectReader reader = repo.newObjectReader()) {
        List<DiffEntry> entries = getGitTreeDiff(repo, reader, key);

        return entries.stream().map(Loader::toModifiedFile).collect(toImmutableList());
      }
    }

    private List<DiffEntry> getGitTreeDiff(
        Repository repo, ObjectReader reader, GitModifiedFilesCacheImpl.Key key)
        throws IOException {
      try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        df.setReader(reader, repo.getConfig());
        if (key.renameDetection()) {
          df.setDetectRenames(true);
          df.getRenameDetector().setRenameScore(key.renameScore());
        }
        // The scan method only returns the file paths that are different. Callers may choose to
        // format these paths themselves.
        return df.scan(key.aTree(), key.bTree());
      }
    }

    private static ModifiedFile toModifiedFile(DiffEntry entry) {
      String oldPath = entry.getOldPath();
      String newPath = entry.getNewPath();
      return ModifiedFile.builder()
          .changeType(toChangeType(entry.getChangeType()))
          .oldPath(oldPath.equals(DiffEntry.DEV_NULL) ? Optional.empty() : Optional.of(oldPath))
          .newPath(newPath.equals(DiffEntry.DEV_NULL) ? Optional.empty() : Optional.of(newPath))
          .build();
    }

    private static Patch.ChangeType toChangeType(DiffEntry.ChangeType changeType) {
      if (!changeTypeMap.containsKey(changeType)) {
        throw new IllegalArgumentException("Unsupported type " + changeType);
      }
      return changeTypeMap.get(changeType);
    }
  }

  /**
   * In this cache, we evaluate the diffs between two git trees (instead of git commits), hence the
   * key contains the tree IDs.
   */
  @AutoValue
  public abstract static class Key {

    public abstract Project.NameKey project();

    /**
     * The git SHA-1 {@link ObjectId} of the first git tree object for which the diff should be
     * computed.
     */
    public abstract ObjectId aTree();

    /**
     * The git SHA-1 {@link ObjectId} of the second git tree object for which the diff should be
     * computed.
     */
    public abstract ObjectId bTree();

    /**
     * Percentage score used to identify a file as a rename. This value is only available if {@link
     * #renameDetection()} is true. Otherwise, this method will return -1.
     *
     * <p>This value will be used to set the rename score of {@link
     * DiffFormatter#getRenameDetector()}.
     */
    public abstract int renameScore();

    /** Returns true if rename detection was set for this key. */
    public boolean renameDetection() {
      return renameScore() != -1;
    }

    public static Key create(
        Project.NameKey project, ObjectId aCommit, ObjectId bCommit, int renameScore, RevWalk rw)
        throws IOException {
      ObjectId aTree = DiffUtil.getTreeId(rw, aCommit);
      ObjectId bTree = DiffUtil.getTreeId(rw, bCommit);
      return builder().project(project).aTree(aTree).bTree(bTree).renameScore(renameScore).build();
    }

    public static Builder builder() {
      return new AutoValue_GitModifiedFilesCacheImpl_Key.Builder();
    }

    /** Returns the size of the object in bytes */
    public int weight() {
      return project().get().length()
          + 20 * 2 // old and new tree IDs
          + 4; // rename score
    }

    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder project(NameKey value);

      public abstract Builder aTree(ObjectId value);

      public abstract Builder bTree(ObjectId value);

      public abstract Builder renameScore(int value);

      public Builder disableRenameDetection() {
        renameScore(-1);
        return this;
      }

      public abstract Key build();
    }

    public enum KeySerializer implements CacheSerializer<Key> {
      INSTANCE;

      @Override
      public byte[] serialize(Key key) {
        ObjectIdConverter idConverter = ObjectIdConverter.create();
        return Protos.toByteArray(
            GitModifiedFilesKeyProto.newBuilder()
                .setProject(key.project().get())
                .setATree(idConverter.toByteString(key.aTree()))
                .setBTree(idConverter.toByteString(key.bTree()))
                .setRenameScore(key.renameScore())
                .build());
      }

      @Override
      public Key deserialize(byte[] in) {
        GitModifiedFilesKeyProto proto =
            Protos.parseUnchecked(GitModifiedFilesKeyProto.parser(), in);
        ObjectIdConverter idConverter = ObjectIdConverter.create();
        return Key.builder()
            .project(Project.NameKey.parse(proto.getProject()))
            .aTree(idConverter.fromByteString(proto.getATree()))
            .bTree(idConverter.fromByteString(proto.getBTree()))
            .renameScore(proto.getRenameScore())
            .build();
      }
    }
  }

  public enum ValueSerializer implements CacheSerializer<ImmutableList<ModifiedFile>> {
    INSTANCE;

    @Override
    public byte[] serialize(ImmutableList<ModifiedFile> modifiedFiles) {
      ModifiedFilesProto.Builder builder = ModifiedFilesProto.newBuilder();
      modifiedFiles.forEach(
          f -> builder.addModifiedFile(ModifiedFile.Serializer.INSTANCE.toProto(f)));
      return Protos.toByteArray(builder.build());
    }

    @Override
    public ImmutableList<ModifiedFile> deserialize(byte[] in) {
      ImmutableList.Builder<ModifiedFile> modifiedFiles = ImmutableList.builder();
      ModifiedFilesProto modifiedFilesProto =
          Protos.parseUnchecked(ModifiedFilesProto.parser(), in);
      modifiedFilesProto
          .getModifiedFileList()
          .forEach(f -> modifiedFiles.add(ModifiedFile.Serializer.INSTANCE.fromProto(f)));
      return modifiedFiles.build();
    }
  }
}
