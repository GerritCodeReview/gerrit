// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.Weigher;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.MergedKeyProto;
import com.google.gerrit.server.cache.serialize.BooleanCacheSerializer;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class MergedCacheImpl implements MergedCache {
  public static final String CACHE_NAME = "merged_into";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, Key.class, Boolean.class)
            .maximumWeight(8 << 20)
            .weigher(KeyWeigher.class)
            .version(0)
            .keySerializer(KeySerializer.INSTANCE)
            .valueSerializer(BooleanCacheSerializer.INSTANCE);
        bind(MergedCache.class).to(MergedCacheImpl.class);
      }
    };
  }

  public record Key(Project.NameKey project, ObjectId commit, ObjectId into) {
    public Key {
      requireNonNull(project);
      commit = requireNonNull(commit).copy();
      into = requireNonNull(into).copy();
    }
  }

  public enum KeySerializer implements CacheSerializer<Key> {
    INSTANCE;

    @Override
    public byte[] serialize(Key key) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return Protos.toByteArray(
          MergedKeyProto.newBuilder()
              .setProject(ProjectNameKeyProtoConverter.INSTANCE.toProto(key.project()))
              .setCommit(idConverter.toByteString(key.commit()))
              .setInto(idConverter.toByteString(key.into()))
              .build());
    }

    @Override
    public Key deserialize(byte[] bytes) {
      MergedKeyProto proto = Protos.parseUnchecked(MergedKeyProto.parser(), bytes);
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return new Key(
          ProjectNameKeyProtoConverter.INSTANCE.fromProto(proto.getProject()),
          idConverter.fromByteString(proto.getCommit()),
          idConverter.fromByteString(proto.getInto()));
    }
  }

  public static class KeyWeigher implements Weigher<Key, Boolean> {
    @Override
    public int weigh(Key key, Boolean value) {
      return 128 + 2 * key.project().get().length();
    }
  }

  private final Cache<Key, Boolean> cache;

  @Inject
  public MergedCacheImpl(@Named(CACHE_NAME) Cache<Key, Boolean> cache) {
    this.cache = cache;
  }

  @Override
  public boolean isMergedInto(Project.NameKey project, RevWalk rw, ObjectId commit, ObjectId into)
      throws IOException {
    Key key = new Key(project, commit, into);
    try {
      return cache.get(
          key, () -> rw.isMergedInto(rw.parseCommit(key.commit()), rw.parseCommit(key.into())));
    } catch (ExecutionException | UncheckedExecutionException e) {
      Throwable cause = e.getCause();
      for (Throwable c = cause; c != null; c = c.getCause()) {
        if (c instanceof InterruptedException
            || c instanceof InterruptedIOException
            || c instanceof ClosedByInterruptException) {
          Thread.currentThread().interrupt();
          InterruptedIOException interrupted =
              new InterruptedIOException("Ancestry check interrupted");
          interrupted.initCause(cause);
          throw interrupted;
        }
      }
      Throwables.throwIfInstanceOf(cause, IOException.class);
      Throwables.throwIfInstanceOf(cause, RuntimeException.class);
      throw new IOException("Failed to check commit ancestry", cause);
    }
  }
}
