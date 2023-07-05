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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.CommentContext;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.DraftCommentsReader;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.AllCommentContextProto;
import com.google.gerrit.server.cache.proto.Cache.AllCommentContextProto.CommentContextProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.comment.CommentContextLoader.ContextInput;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Implementation of {@link CommentContextCache}. */
public class CommentContextCacheImpl implements CommentContextCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CACHE_NAME = "comment_context";

  /**
   * Comment context is expected to contain just few lines of code to be displayed beside the
   * comment. Setting an upper bound of 100 for padding.
   */
  @VisibleForTesting public static final int MAX_CONTEXT_PADDING = 50;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, CommentContextKey.class, CommentContext.class)
            .version(5)
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

    // We do two transformations to the input keys: first we adjust the max context padding, and
    // second we hash the file path. The transformed keys are used to request context from the
    // cache. Keeping a map of the original inputKeys to the transformed keys
    Map<CommentContextKey, CommentContextKey> inputKeysToCacheKeys =
        Streams.stream(inputKeys)
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    k ->
                        adjustMaxContextPadding(k)
                            .toBuilder()
                            .path(Loader.hashPath(k.path()))
                            .build()));

    try {
      ImmutableMap<CommentContextKey, CommentContext> allContext =
          contextCache.getAll(inputKeysToCacheKeys.values());

      for (CommentContextKey inputKey : inputKeys) {
        CommentContextKey cacheKey = inputKeysToCacheKeys.get(inputKey);
        result.put(inputKey, allContext.get(cacheKey));
      }
      return result.build();
    } catch (ExecutionException e) {
      throw new StorageException("Failed to retrieve comments' context", e);
    }
  }

  private static CommentContextKey adjustMaxContextPadding(CommentContextKey key) {
    if (key.contextPadding() < 0) {
      logger.atWarning().log(
          "Cannot set context padding to a negative number %d. Adjusting the number to 0",
          key.contextPadding());
      return key.toBuilder().contextPadding(0).build();
    }
    if (key.contextPadding() > MAX_CONTEXT_PADDING) {
      logger.atWarning().log(
          "Number of requested context lines is %d and exceeding the configured maximum of %d."
              + " Adjusting the number to the maximum.",
          key.contextPadding(), MAX_CONTEXT_PADDING);
      return key.toBuilder().contextPadding(MAX_CONTEXT_PADDING).build();
    }
    return key;
  }

  public enum CommentContextSerializer implements CacheSerializer<CommentContext> {
    INSTANCE;

    @Override
    public byte[] serialize(CommentContext commentContext) {
      AllCommentContextProto.Builder allBuilder = AllCommentContextProto.newBuilder();
      allBuilder.setContentType(commentContext.contentType());

      commentContext
          .lines()
          .entrySet()
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
      AllCommentContextProto proto = Protos.parseUnchecked(AllCommentContextProto.parser(), in);
      proto.getContextList().stream()
          .forEach(c -> contextLinesMap.put(c.getLineNumber(), c.getContextLine()));
      return CommentContext.create(contextLinesMap.build(), proto.getContentType());
    }
  }

  static class Loader extends CacheLoader<CommentContextKey, CommentContext> {
    private final ChangeNotes.Factory notesFactory;
    private final CommentsUtil commentsUtil;
    private final CommentContextLoader.Factory factory;
    private final DraftCommentsReader draftCommentsReader;

    @Inject
    Loader(
        CommentsUtil commentsUtil,
        ChangeNotes.Factory notesFactory,
        CommentContextLoader.Factory factory,
        DraftCommentsReader draftCommentsReader) {
      this.commentsUtil = commentsUtil;
      this.notesFactory = notesFactory;
      this.factory = factory;
      this.draftCommentsReader = draftCommentsReader;
    }

    /**
     * Load the comment context of a single comment identified by its key.
     *
     * @param key a {@link CommentContextKey} identifying a comment.
     * @return the comment context associated with the comment.
     * @throws IOException an error happened while parsing the commit or loading the file where the
     *     comment is written.
     */
    @Override
    public CommentContext load(CommentContextKey key) throws IOException {
      return loadAll(ImmutableList.of(key)).get(key);
    }

    /**
     * Load the comment context of different comments identified by their keys.
     *
     * @param keys list of {@link CommentContextKey} identifying some comments.
     * @return a map of the input keys to their corresponding comment context.
     * @throws IOException an error happened while parsing the commits or loading the files where
     *     the comments are written.
     */
    @Override
    public Map<CommentContextKey, CommentContext> loadAll(
        Iterable<? extends CommentContextKey> keys) throws IOException {
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
     * Load the comment context for comments (published and drafts) of the same project and change
     * ID.
     *
     * @param keys a list of keys corresponding to some comments
     * @param project a gerrit project/repository
     * @param changeId an identifier for a change
     * @return a map of the input keys to their corresponding {@link CommentContext}
     */
    private Map<CommentContextKey, CommentContext> loadForSameChange(
        List<CommentContextKey> keys, Project.NameKey project, Change.Id changeId)
        throws IOException {
      ChangeNotes notes = notesFactory.createChecked(project, changeId);
      List<HumanComment> humanComments = commentsUtil.publishedHumanCommentsByChange(notes);
      List<HumanComment> drafts = draftCommentsReader.getDraftsByChange(notes);
      List<HumanComment> allComments =
          Streams.concat(humanComments.stream(), drafts.stream()).collect(Collectors.toList());
      CommentContextLoader loader = factory.create(project);
      Map<CommentContextKey, ContextInput> keysToComments = new HashMap<>();
      for (CommentContextKey key : keys) {
        Comment comment = getCommentForKey(allComments, key);
        keysToComments.put(key, ContextInput.fromComment(comment, key.contextPadding()));
      }
      Map<ContextInput, CommentContext> allContext =
          loader.getContext(
              keysToComments.values().stream().distinct().collect(Collectors.toList()));
      return keys.stream()
          .collect(
              Collectors.toMap(Function.identity(), k -> allContext.get(keysToComments.get(k))));
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
