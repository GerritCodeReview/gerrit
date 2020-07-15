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

import com.google.auto.value.AutoValue;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.LabeledContextLineInfo;
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

public class CommentContextCacheImpl implements CommentContextCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CACHE_NAME = "comment_context";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, Key.class, new TypeLiteral<List<LabeledContextLineInfo>>() {})
            .diskLimit(-1)
            .version(1)
            .maximumWeight(0)
            .keySerializer(Serializer.INSTANCE)
            .valueSerializer(CommentContextSerializer.INSTANCE)
            .loader(Loader.class);

        bind(CommentContextCache.class).to(CommentContextCacheImpl.class);
      }
    };
  }

  private final LoadingCache<Key, List<LabeledContextLineInfo>> contextCache;

  @Inject
  CommentContextCacheImpl(
      @Named(CACHE_NAME) LoadingCache<Key, List<LabeledContextLineInfo>> contextCache) {
    this.contextCache = contextCache;
  }

  @Override
  public List<LabeledContextLineInfo> get(
      Project.NameKey project, Change.Id changeId, CommentInfo comment) {
    try {
      return get(project, PatchSet.id(changeId, comment.patchSet), comment.id, comment.path);
    } catch (ExecutionException e) {
      logger.atWarning().log(
          "Failed to retrieve context for change %s, comment %s", changeId, comment.id);
    }
    return ImmutableList.of();
  }

  @Override
  public Map<CommentInfo, List<LabeledContextLineInfo>> getAll(
      NameKey project, Change.Id changeId, Collection<CommentInfo> comments) {
    List<Key> keys = new ArrayList<>();
    for (CommentInfo comment : comments) {
      PatchSet.Id ps = PatchSet.id(changeId, comment.patchSet);
      String hashedPath = Hashing.murmur3_128().hashString(comment.path, UTF_8).toString();
      keys.add(Key.create(project, ps, comment.id, hashedPath));
    }
    try {
      ImmutableMap<Key, List<LabeledContextLineInfo>> all = contextCache.getAll(keys);
      ImmutableMap.Builder result = ImmutableMap.builder();
      for (CommentInfo comment : comments) {
        PatchSet.Id ps = PatchSet.id(changeId, comment.patchSet);
        String hashedPath = Hashing.murmur3_128().hashString(comment.path, UTF_8).toString();
        Key k = Key.create(project, ps, comment.id, hashedPath);
        result.put(comment, all.get(k));
      }
      return result.build();
    } catch (ExecutionException e) {
      logger.atWarning().log("Failed to retrieve comment context for change " + changeId);
    }
    return ImmutableMap.of();
  }

  private List<LabeledContextLineInfo> get(
      Project.NameKey project, PatchSet.Id psId, String commentId, String path)
      throws ExecutionException {
    String pathHash = Hashing.murmur3_128().hashString(path, UTF_8).toString();
    Key k = Key.create(project, psId, commentId, pathHash);
    return contextCache.get(k);
  }

  @AutoValue
  abstract static class Key {
    static CommentContextCacheImpl.Key create(
        Project.NameKey projectKey, PatchSet.Id psId, String commentId, String pathHash) {
      return new AutoValue_CommentContextCacheImpl_Key(projectKey, psId, commentId, pathHash);
    }

    abstract Project.NameKey projectKey();

    abstract PatchSet.Id psId();

    abstract String commentId();

    abstract String pathHash();

    enum Serializer implements CacheSerializer<Key> {
      INSTANCE;

      @Override
      public byte[] serialize(Key object) {
        return Protos.toByteArray(
            Cache.CommentContextKeyProto.newBuilder()
                .setProjectKey(object.projectKey().get())
                .setPsId(object.psId().toString())
                .setPathHash(object.pathHash())
                .setCommentId(object.commentId())
                .build());
      }

      @Override
      public Key deserialize(byte[] in) {
        Cache.CommentContextKeyProto proto =
            Protos.parseUnchecked(Cache.CommentContextKeyProto.parser(), in);
        return Key.create(
            Project.NameKey.parse(proto.getProjectKey()),
            PatchSet.Id.parse(proto.getPsId()),
            proto.getPathHash(),
            proto.getCommentId());
      }
    }
  }

  public enum CommentContextSerializer implements CacheSerializer<List<LabeledContextLineInfo>> {
    INSTANCE;

    @Override
    public byte[] serialize(List<LabeledContextLineInfo> object) {
      AllCommentContextProto.Builder allBuilder = AllCommentContextProto.newBuilder();

      object.stream()
          .forEach(
              c ->
                  allBuilder.addContext(
                      CommentContextProto.newBuilder()
                          .setLineNumber(c.lineNumber)
                          .setContextLine(c.contextLine)
                          .build()));
      return Protos.toByteArray(allBuilder.build());
    }

    @Override
    public List<LabeledContextLineInfo> deserialize(byte[] in) {
      return Protos.parseUnchecked(AllCommentContextProto.parser(), in).getContextList().stream()
          .map(c -> new LabeledContextLineInfo(c.getLineNumber(), c.getContextLine()))
          .collect(Collectors.toList());
    }
  }

  static class Loader extends CacheLoader<Key, List<LabeledContextLineInfo>> {
    private final CommentContextLoader commentContextLoader;
    private final ChangeNotes.Factory notesFactory;
    private final CommentsUtil commentsUtil;

    @Inject
    Loader(
        CommentsUtil commentsUtil,
        CommentContextLoader commentContextUtil,
        ChangeNotes.Factory notesFactory) {
      this.commentsUtil = commentsUtil;
      this.commentContextLoader = commentContextUtil;
      this.notesFactory = notesFactory;
    }

    @Override
    public List<LabeledContextLineInfo> load(Key key) throws Exception {
      ChangeNotes notes = notesFactory.createChecked(key.projectKey(), key.psId().changeId());
      Comment comment =
          commentsUtil
              .getPublishedHumanComment(notes, key.commentId(), key.pathHash(), key.psId().get())
              .orElse(null);
      return commentContextLoader.getContext(key.projectKey(), comment);
    }
  }
}
