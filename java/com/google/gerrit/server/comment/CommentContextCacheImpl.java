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

/**
 * Caches the context lines of comments (source file content surrounding and including the lines
 * where the comment was written)
 */
public class CommentContextCacheImpl implements CommentContextCache {
  private static final String CACHE_NAME = "comment_context";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, Key.class, new TypeLiteral<ImmutableList<ContextLine>>() {})
            .diskLimit(-1)
            .version(1)
            .maximumWeight(1 << 20)
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
      Project.NameKey project, Change.Id changeId, Collection<CommentContextKey> comments) {
    ImmutableList.Builder<Key> cacheKeys = ImmutableList.builderWithExpectedSize(comments.size());
    Map<CommentContextKey, Key> keysToCacheKeys = new HashMap<>();
    for (CommentContextKey comment : comments) {
      Key cacheKey =
          Key.create(
              project, changeId, comment.patchset(), comment.id(), Loader.hashPath(comment.path()));
      cacheKeys.add(cacheKey);
      keysToCacheKeys.put(comment, cacheKey);
    }
    try {
      ImmutableMap<Key, ImmutableList<ContextLine>> allContext =
          contextCache.getAll(cacheKeys.build());
      ImmutableMap.Builder result = ImmutableMap.builder();
      for (CommentContextKey comment : comments) {
        Key cacheKey = keysToCacheKeys.get(comment);
        result.put(comment, allContext.get(cacheKey));
      }
      return result.build();
    } catch (ExecutionException e) {
      throw new StorageException(
          String.format("Failed to retrieve comments' context for change %s", changeId), e);
    }
  }

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
      public byte[] serialize(Key object) {
        return Protos.toByteArray(
            Cache.CommentContextKeyProto.newBuilder()
                .setProject(object.projectKey().get())
                .setChangeId(object.changeId().toString())
                .setPatchset(object.patchset())
                .setPathHash(object.pathHash())
                .setUuid(object.commentId())
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
    public byte[] serialize(ImmutableList<ContextLine> object) {
      AllCommentContextProto.Builder allBuilder = AllCommentContextProto.newBuilder();

      object.stream()
          .forEach(
              c ->
                  allBuilder.addContext(
                      CommentContextProto.newBuilder()
                          .setLineNumber(c.lineNumber())
                          .setContextLine(c.contextLine())
                          .build()));
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
     * @param project a git project/repository
     * @param changeId an identifier for a change
     * @return a map of the input keys to their corresponding list of {@link ContextLine}
     */
    private Map<Key, ImmutableList<ContextLine>> loadForSameChange(
        List<Key> keys, Project.NameKey project, Change.Id changeId) {
      ImmutableMap.Builder<Key, List<ContextLine>> result =
          ImmutableMap.builderWithExpectedSize(keys.size());

      ChangeNotes notes = notesFactory.createChecked(project, changeId);
      List<HumanComment> humanComments = commentsUtil.publishedHumanCommentsByChange(notes);

      CommentContextLoader ldr = factory.create(project);
      // TODO(ghareeb): reduce the quadratic complexity (getCommentForKey now works in O(n))
      for (Key key : keys) {
        Comment comment = getCommentForKey(humanComments, key);
        // ldr.getContext returns an empty reference to a list of ContextLine. Calling the fill()
        // method at the end will load the context for all comments
        result.put(key, ldr.getContext(comment));
      }
      ldr.fill();
      return asImmutableContextLineLists(result.build());
    }

    private Map<Key, ImmutableList<ContextLine>> asImmutableContextLineLists(
        Map<Key, List<ContextLine>> in) {
      ImmutableMap.Builder<Key, ImmutableList<ContextLine>> result =
          ImmutableMap.builderWithExpectedSize(in.size());
      for (Map.Entry<Key, List<ContextLine>> entry : in.entrySet()) {
        result.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
      }
      return result.build();
    }

    /**
     * Return the single comment from the <code>allComments</code> input list corresponding to the
     * key parameter.
     *
     * @param allComments a list of comments.
     * @param key a key representing a single comment.
     * @return the single comment corresponding to the key parameter.
     */
    private Comment getCommentForKey(List<HumanComment> allComments, Key key) {
      return allComments
          .parallelStream()
          .filter(
              c ->
                  key.commentId().equals(c.key.uuid)
                      && key.patchset() == c.key.patchSetId
                      && key.pathHash().equals(hashPath(c.key.filename)))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unable to find comment for key " + key));
    }

    private static String hashPath(String input) {
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
      size += key.patchset().toString().length();
      for (ContextLine line : contextLines) {
        size += 4; // line number
        size += line.contextLine().length(); // number of characters in the context line
      }
      return size;
    }
  }
}
