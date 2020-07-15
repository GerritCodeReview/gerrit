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

import static java.nio.charset.StandardCharsets.UTF_8;

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
import com.google.gerrit.entities.CommentContext;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.CommentContextLoader;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.AllCommentContextProto;
import com.google.gerrit.server.cache.proto.Cache.AllCommentContextProto.CommentContextProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Implementation of {@link CommentContextCache}. */
public class CommentContextCacheImpl implements CommentContextCache {
  private static final String CACHE_NAME = "comment_context";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, CommentContextKey.class, new TypeLiteral<CommentContext>() {})
            .version(1)
            .diskLimit(1 << 30) // limit the total cache size to 1 GB
            .maximumWeight(1 << 23) // Limit the size of the in-memory cache to 8 MB
            .weigher(CommentContextWeigher.class)
            .keySerializer(CommentContextKey.Serializer.INSTANCE)
            .valueSerializer(CommentContextSerializer.INSTANCE)
            .loader(Loader.class);

        bind(CommentContextCache.class).to(CommentContextCacheImpl.class);
      }
    };
  }

  private final LoadingCache<CommentContextKey, CommentContext> contextCache;

  @Inject
  CommentContextCacheImpl(
      @Named(CACHE_NAME) LoadingCache<CommentContextKey, CommentContext> contextCache) {
    this.contextCache = contextCache;
  }

  @Override
  public CommentContext get(CommentContextKey comment) {
    return getAll(ImmutableList.of(comment)).get(comment);
  }

  @Override
  public ImmutableMap<CommentContextKey, CommentContext> getAll(
      Iterable<CommentContextKey> inputKeys) {
    ImmutableMap.Builder<CommentContextKey, CommentContext> result = ImmutableMap.builder();

    // Convert the input keys to the same keys but with their file paths hashed
    Map<CommentContextKey, CommentContextKey> keysToCacheKeys =
        Streams.stream(inputKeys)
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    k -> k.toBuilder().path(Loader.hashPath(k.path())).build()));

    try {
      ImmutableMap<CommentContextKey, CommentContext> allContext =
          contextCache.getAll(keysToCacheKeys.values());

      for (CommentContextKey inputKey : inputKeys) {
        CommentContextKey cacheKey = keysToCacheKeys.get(inputKey);
        result.put(inputKey, allContext.get(cacheKey));
      }
      return result.build();
    } catch (ExecutionException e) {
      throw new StorageException(String.format("Failed to retrieve comments' context"), e);
    }
  }

  public enum CommentContextSerializer implements CacheSerializer<CommentContext> {
    INSTANCE;

    @Override
    public byte[] serialize(CommentContext commentContext) {
      AllCommentContextProto.Builder allBuilder = AllCommentContextProto.newBuilder();

      commentContext.lines().entrySet().stream()
          .forEach(
              c ->
                  allBuilder.addContext(
                      CommentContextProto.newBuilder()
                          .setLineNumber(c.getKey())
                          .setContextLine(c.getValue())));
      return Protos.toByteArray(allBuilder.build());
    }

    @Override
    public CommentContext deserialize(byte[] in) {
      ImmutableMap.Builder<Integer, String> contextLinesMap = ImmutableMap.builder();
      Protos.parseUnchecked(AllCommentContextProto.parser(), in).getContextList().stream()
          .forEach(c -> contextLinesMap.put(c.getLineNumber(), c.getContextLine()));
      return CommentContext.create(contextLinesMap.build());
    }
  }

  static class Loader extends CacheLoader<CommentContextKey, CommentContext> {
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
    public CommentContext load(CommentContextKey key) {
      return loadAll(ImmutableList.of(key)).get(key);
    }

    @Override
    public Map<CommentContextKey, CommentContext> loadAll(
        Iterable<? extends CommentContextKey> keys) {
      ImmutableMap.Builder<CommentContextKey, CommentContext> result =
          ImmutableMap.builderWithExpectedSize(Iterables.size(keys));

      Map<Project.NameKey, Map<Change.Id, List<CommentContextKey>>> groupedKeys =
          Streams.stream(keys)
              .distinct()
              .map(k -> (CommentContextKey) k)
              .collect(
                  Collectors.groupingBy(
                      CommentContextKey::project,
                      Collectors.groupingBy(CommentContextKey::changeId)));

      for (Map.Entry<Project.NameKey, Map<Change.Id, List<CommentContextKey>>> perProject :
          groupedKeys.entrySet()) {
        Map<Change.Id, List<CommentContextKey>> keysPerProject = perProject.getValue();

        for (Map.Entry<Change.Id, List<CommentContextKey>> perChange : keysPerProject.entrySet()) {
          Map<CommentContextKey, CommentContext> context =
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
     * @return a map of the input keys to their corresponding {@link CommentContext}
     */
    private Map<CommentContextKey, CommentContext> loadForSameChange(
        List<CommentContextKey> keys, Project.NameKey project, Change.Id changeId) {
      ChangeNotes notes = notesFactory.createChecked(project, changeId);
      List<HumanComment> humanComments = commentsUtil.publishedHumanCommentsByChange(notes);
      CommentContextLoader loader = factory.create(project);
      Map<Comment, CommentContextKey> commentsToKeys = new HashMap<>();
      for (CommentContextKey key : keys) {
        commentsToKeys.put(getCommentForKey(humanComments, key), key);
      }
      Map<Comment, CommentContext> allContext = loader.getContext(commentsToKeys.keySet());
      return allContext.entrySet().stream()
          .collect(Collectors.toMap(e -> commentsToKeys.get(e.getKey()), e -> e.getValue()));
    }

    /**
     * Return the single comment from the {@code allComments} input list corresponding to the key
     * parameter.
     *
     * @param allComments a list of comments.
     * @param key a key representing a single comment.
     * @return the single comment corresponding to the key parameter.
     */
    private Comment getCommentForKey(List<HumanComment> allComments, CommentContextKey key) {
      return allComments.stream()
          .filter(
              c ->
                  key.id().equals(c.key.uuid)
                      && key.patchset() == c.key.patchSetId
                      && key.path().equals(hashPath(c.key.filename)))
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

  private static class CommentContextWeigher implements Weigher<CommentContextKey, CommentContext> {
    @Override
    public int weigh(CommentContextKey key, CommentContext commentContext) {
      int size = 0;
      size += key.id().length();
      size += key.path().length();
      size += key.project().get().length();
      size += 4;
      for (String line : commentContext.lines().values()) {
        size += 4; // line number
        size += line.length(); // number of characters in the context line
      }
      return size;
    }
  }
}
