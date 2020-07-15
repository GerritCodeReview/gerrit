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
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.ContextLine;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Caches the context lines of comments (source file content surrounding and including the lines
 * where the comment was written)
 */
public class CommentContextCacheImpl implements CommentContextCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
      Project.NameKey project,
      Change.Id changeId,
      Integer patchSet,
      String commentId,
      String path) {
    try {
      return get(project, PatchSet.id(changeId, patchSet), commentId, path);
    } catch (ExecutionException e) {
      throw new StorageException(
          String.format(
              "Failed to retrieve context for change %s, comment %s", changeId, commentId),
          e);
    }
  }

  @Override
  public ImmutableMap<CommentContextKey, List<ContextLine>> getAll(
      Project.NameKey project, Change.Id changeId, Collection<CommentContextKey> comments) {
    List<Key> keys = new ArrayList<>();
    for (CommentContextKey comment : comments) {
      PatchSet.Id ps = PatchSet.id(changeId, comment.patchset());
      String hashedPath = Hashing.murmur3_128().hashString(comment.path(), UTF_8).toString();
      keys.add(Key.create(project, ps, comment.id(), hashedPath));
    }
    try {
      ImmutableMap<Key, ImmutableList<ContextLine>> allContext = contextCache.getAll(keys);
      ImmutableMap.Builder result = ImmutableMap.builder();
      for (CommentContextKey comment : comments) {
        PatchSet.Id ps = PatchSet.id(changeId, comment.patchset());
        String hashedPath = Hashing.murmur3_128().hashString(comment.path(), UTF_8).toString();
        Key k = Key.create(project, ps, comment.id(), hashedPath);
        result.put(comment, allContext.get(k));
      }
      return result.build();
    } catch (ExecutionException e) {
      throw new StorageException(
          String.format("Failed to retrieve comments' context for change %s", changeId), e);
    }
  }

  private ImmutableList<ContextLine> get(
      Project.NameKey project, PatchSet.Id psId, String commentId, String path)
      throws ExecutionException {
    String pathHash = Hashing.murmur3_128().hashString(path, UTF_8).toString();
    Key k = Key.create(project, psId, commentId, pathHash);
    return contextCache.get(k);
  }

  @AutoValue
  public abstract static class Key {
    public static CommentContextCacheImpl.Key create(
        Project.NameKey projectKey, PatchSet.Id psId, String commentId, String pathHash) {
      return new AutoValue_CommentContextCacheImpl_Key(projectKey, psId, commentId, pathHash);
    }

    abstract Project.NameKey projectKey();

    abstract PatchSet.Id psId();

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
                .setPatchSetId(object.psId().toString())
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
            PatchSet.Id.parse(proto.getPatchSetId()),
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
      return loadForProjectAndChange(ImmutableList.of(key), key.projectKey(), key.psId().changeId())
          .get(key);
    }

    @Override
    public Map<Key, ImmutableList<ContextLine>> loadAll(Iterable<? extends Key> keys) {
      List<Key> keyList = Lists.newArrayList(keys);
      ImmutableMap.Builder<Key, ImmutableList<ContextLine>> result =
          ImmutableMap.builderWithExpectedSize(keyList.size());

      Map<Project.NameKey, Map<PatchSet.Id, List<Key>>> keysByProjectAndPs =
          keyList.stream()
              .collect(Collectors.groupingBy(Key::projectKey, Collectors.groupingBy(Key::psId)));

      for (Map.Entry<Project.NameKey, Map<PatchSet.Id, List<Key>>> projectAndKeys :
          keysByProjectAndPs.entrySet()) {
        Project.NameKey project = projectAndKeys.getKey();
        Map<PatchSet.Id, List<Key>> keysPerPs = projectAndKeys.getValue();
        for (Map.Entry<PatchSet.Id, List<Key>> patchsetAndKeys : keysPerPs.entrySet()) {
          PatchSet.Id patchset = patchsetAndKeys.getKey();
          List<Key> keysForProjectAndPatchset = patchsetAndKeys.getValue();
          Map<Key, ImmutableList<ContextLine>> context =
              loadForProjectAndChange(keysForProjectAndPatchset, project, patchset.changeId());
          result.putAll(context);
        }
      }
      return result.build();
    }

    private Map<Key, ImmutableList<ContextLine>> loadForProjectAndChange(
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
        result.put(key, ldr.getContext(comment, comment.getCommitId()));
      }
      ldr.fill();
      return convertToImmutable(result.build());
    }

    private Map<Key, ImmutableList<ContextLine>> convertToImmutable(
        Map<Key, List<ContextLine>> in) {
      ImmutableMap.Builder<Key, ImmutableList<ContextLine>> result =
          ImmutableMap.builderWithExpectedSize(in.size());
      for (Map.Entry<Key, List<ContextLine>> entry : in.entrySet()) {
        result.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
      }
      return result.build();
    }

    private Comment getCommentForKey(List<HumanComment> allComments, Key key) {
      return allComments
          .parallelStream()
          .filter(
              c ->
                  key.commentId().equals(c.key.uuid)
                      && key.psId().get() == c.key.patchSetId
                      && key.pathHash()
                          .equals(
                              Hashing.murmur3_128().hashString(c.key.filename, UTF_8).toString()))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unable to find comment for key " + key));
    }
  }

  private static class CommentContextWeigher implements Weigher<Key, ImmutableList<ContextLine>> {
    @Override
    public int weigh(Key key, ImmutableList<ContextLine> contextLines) {
      int size = 0;
      size += key.commentId().length();
      size += key.pathHash().length();
      size += key.projectKey().get().length();
      size += key.psId().toString().length();
      for (ContextLine line : contextLines) {
        size += 4; // line number
        size += line.contextLine().length(); // number of characters in the context line
      }
      return size;
    }
  }
}
