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
// limitations under the License

package com.google.gerrit.server.comment;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.hash.Hashing;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.ContextLine;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.CommentContextLoader;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.proto.Cache.AllCommentContextProto;
import com.google.gerrit.server.cache.proto.Cache.AllCommentContextProto.CommentContextProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.comment.CommentContextCacheImpl.Key.Serializer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class CommentContextCacheImpl implements CommentContextCache {
  private static final String CACHE_NAME = "comment_context";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, Key.class, new TypeLiteral<ImmutableList<ContextLine>>() {})
            .diskLimit(1 << 30) // limit the total cache size to 1 GB
            .version(1)
            .maximumWeight(1 << 20) // Limit the size of cache entries to 1 MB
            .weigher(CommentContextWeigher.class)
            .keySerializer(Serializer.INSTANCE)
            .valueSerializer(CommentContextSerializer.INSTANCE)
            .loader(Loader.class);

        bind(CommentContextCache.class).to(CommentContextCacheImpl.class);
      }
    };
  }

  private final LoadingCache<Key, ImmutableList<ContextLine>> contextCache;

  @Inject
  CommentContextCacheImpl(
      @Named(CACHE_NAME) LoadingCache<Key, ImmutableList<ContextLine>> contextCache) {
    this.contextCache = contextCache;
  }

  @Override
  public ImmutableList<ContextLine> get(
      Project.NameKey project, Change.Id changeId, CommentContextKey comment) {
    return getAll(project, changeId, ImmutableList.of(comment)).get(comment);
  }

  @Override
  public ImmutableMap<CommentContextKey, ImmutableList<ContextLine>> getAll(
      Project.NameKey project, Change.Id changeId, Collection<CommentContextKey> keys) {
    ImmutableMap.Builder result = ImmutableMap.builder();
    Map<CommentContextKey, Key> keysToCacheKeys = new HashMap<>();
    for (CommentContextKey key : keys) {
      keysToCacheKeys.put(key, key.asCacheKey(project, changeId));
    }
    try {
      ImmutableMap<Key, ImmutableList<ContextLine>> allContext =
          contextCache.getAll(keysToCacheKeys.values());

      for (CommentContextKey comment : keys) {
        Key cacheKey = keysToCacheKeys.get(comment);
        result.put(comment, allContext.get(cacheKey));
      }
      return result.build();
    } catch (ExecutionException e) {
      throw new StorageException(
          String.format("Failed to retrieve comments' context for change %s", changeId), e);
    }
  }

  /**
   * A cache Key for the comment {@link #contextCache}. The key identifies a unique specific comment
   * using a project, changeId, patchset, comment ID and the path of the comment.
   *
   * <p>These fields are sufficient to load a comment from the change notes, hence computing the
   * range of the comment (start/end lines and characters of the comment) which will be used to load
   * these lines from the source file at which the comment was written.
   *
   * <p>The proto representation of this key is {@link
   * com.google.gerrit.server.cache.proto.Cache.CommentContextKeyProto}
   */
  @AutoValue
  public abstract static class Key {
    public static CommentContextCacheImpl.Key create(
        Project.NameKey projectKey,
        Change.Id changeId,
        Integer patchset,
        String commentId,
        String pathHash) {
      return new AutoValue_CommentContextCacheImpl_Key(
          projectKey, changeId, patchset, commentId, pathHash);
    }

    abstract Project.NameKey projectKey();

    abstract Change.Id changeId();

    abstract Integer patchset();

    abstract String commentId();

    // The path is hashed for security and compliance reasons. For persisted caches, file paths
    // should not be stored as plain text in the storage
    abstract String pathHash();

    public enum Serializer implements CacheSerializer<Key> {
      INSTANCE;

      @Override
      public byte[] serialize(Key key) {
        return Protos.toByteArray(
            Cache.CommentContextKeyProto.newBuilder()
                .setProject(key.projectKey().get())
                .setChangeId(key.changeId().toString())
                .setPatchset(key.patchset())
                .setPathHash(key.pathHash())
                .setUuid(key.commentId())
                .build());
      }

      @Override
      public Key deserialize(byte[] in) {
        Cache.CommentContextKeyProto proto =
            Protos.parseUnchecked(Cache.CommentContextKeyProto.parser(), in);
        return Key.create(
            Project.NameKey.parse(proto.getProject()),
            Change.Id.tryParse(proto.getChangeId()).get(),
            proto.getPatchset(),
            proto.getUuid(),
            proto.getPathHash());
      }
    }
  }

  public enum CommentContextSerializer implements CacheSerializer<ImmutableList<ContextLine>> {
    INSTANCE;

    @Override
    public byte[] serialize(ImmutableList<ContextLine> contextLineList) {
      AllCommentContextProto.Builder allBuilder = AllCommentContextProto.newBuilder();

      contextLineList.stream()
          .forEach(
              c ->
                  allBuilder.addContext(
                      CommentContextProto.newBuilder()
                          .setLineNumber(c.lineNumber())
                          .setContextLine(c.contextLine())));
      return Protos.toByteArray(allBuilder.build());
    }

    @Override
    public ImmutableList<ContextLine> deserialize(byte[] in) {
      return Protos.parseUnchecked(AllCommentContextProto.parser(), in).getContextList().stream()
          .map(c -> ContextLine.create(c.getLineNumber(), c.getContextLine()))
          .collect(toImmutableList());
    }
  }

  static class Loader extends CacheLoader<Key, ImmutableList<ContextLine>> {
    private final ChangeNotes.Factory notesFactory;
    private final CommentsUtil commentsUtil;
    private final CommentContextLoader.Factory factory;

    @Inject
    Loader(
        CommentsUtil commentsUtil,
        ChangeNotes.Factory notesFactory,
        CommentContextLoader.Factory factory) {
      this.commentsUtil = commentsUtil;
      this.notesFactory = notesFactory;
      this.factory = factory;
    }

    @Override
    public ImmutableList<ContextLine> load(Key key) {
      return loadAll(ImmutableList.of(key)).get(key);
    }

    @Override
    public Map<Key, ImmutableList<ContextLine>> loadAll(Iterable<? extends Key> keys) {
      ImmutableMap.Builder<Key, ImmutableList<ContextLine>> result =
          ImmutableMap.builderWithExpectedSize(Iterables.size(keys));

      Map<Project.NameKey, Map<Change.Id, List<Key>>> groupedKeys =
          Streams.stream(keys)
              .distinct()
              .map(k -> (Key) k)
              .collect(
                  Collectors.groupingBy(Key::projectKey, Collectors.groupingBy(Key::changeId)));

      for (Map.Entry<Project.NameKey, Map<Change.Id, List<Key>>> perProject :
          groupedKeys.entrySet()) {
        Map<Change.Id, List<Key>> keysPerProject = perProject.getValue();

        for (Map.Entry<Change.Id, List<Key>> perChange : keysPerProject.entrySet()) {
          Map<Key, ImmutableList<ContextLine>> context =
              loadForSameChange(perChange.getValue(), perProject.getKey(), perChange.getKey());
          result.putAll(context);
        }
      }
      return result.build();
    }

    /**
     * Load the comment context for comments of the same project and change ID.
     *
     * @param keys a list of keys corresponding to some comments
     * @param project a gerrit project/repository
     * @param changeId an identifier for a change
     * @return a map of the input keys to their corresponding list of {@link ContextLine}
     */
    private Map<Key, ImmutableList<ContextLine>> loadForSameChange(
        List<Key> keys, Project.NameKey project, Change.Id changeId) {
      ImmutableMap.Builder<Key, List<ContextLine>> result =
          ImmutableMap.builderWithExpectedSize(keys.size());

      ChangeNotes notes = notesFactory.createChecked(project, changeId);
      List<HumanComment> humanComments = commentsUtil.publishedHumanCommentsByChange(notes);

      CommentContextLoader loader = factory.create(project);
      // TODO(ghareeb): reduce the quadratic complexity (getCommentForKey now works in O(n))
      for (Key key : keys) {
        Comment comment = getCommentForKey(humanComments, key);
        // loader.getContext returns an empty reference to a list of ContextLine. Calling the fill()
        // method at the end will load the context for all comments
        result.put(key, loader.getContext(comment));
      }
      loader.fill();
      return result.build().entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, e -> ImmutableList.copyOf(e.getValue())));
    }

    /**
     * Return the single comment from the {@code allComments} input list corresponding to the key
     * parameter.
     *
     * @param allComments a list of comments.
     * @param key a key representing a single comment.
     * @return the single comment corresponding to the key parameter.
     */
    private Comment getCommentForKey(List<HumanComment> allComments, Key key) {
      return allComments.stream()
          .filter(
              c ->
                  key.commentId().equals(c.key.uuid)
                      && key.patchset() == c.key.patchSetId
                      && key.pathHash().equals(hashPath(c.key.filename)))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unable to find comment for key " + key));
    }

    /**
     * Hash an input String using the general {@link Hashing#murmur3_128()} hash.
     *
     * @param input the input String
     * @return a hashed representation of the input String
     */
    static String hashPath(String input) {
      return Hashing.murmur3_128().hashString(input, UTF_8).toString();
    }
  }

  private static class CommentContextWeigher implements Weigher<Key, ImmutableList<ContextLine>> {
    @Override
    public int weigh(Key key, ImmutableList<ContextLine> contextLines) {
      int size = 0;
      size += key.commentId().length();
      size += key.pathHash().length();
      size += key.projectKey().get().length();
      size += 4;
      for (ContextLine line : contextLines) {
        size += 4; // line number
        size += line.contextLine().length(); // number of characters in the context line
      }
      return size;
    }
  }
}
