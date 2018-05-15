// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.base.MoreObjects;
import com.google.common.cache.Cache;
import com.google.common.cache.Weigher;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.cache.BooleanCacheSerializer;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.CacheSerializer;
import com.google.gerrit.server.cache.ProtoCacheSerializers;
import com.google.gerrit.server.cache.proto.Cache.MergeabilityKeyProto;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.submit.SubmitDryRun;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MergeabilityCacheImpl implements MergeabilityCache {
  private static final Logger log = LoggerFactory.getLogger(MergeabilityCacheImpl.class);

  private static final String CACHE_NAME = "mergeability";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, EntryKey.class, Boolean.class)
            .maximumWeight(1 << 20)
            .weigher(MergeabilityWeigher.class)
            .version(1)
            .keySerializer(EntryKey.Serializer.INSTANCE)
            .valueSerializer(BooleanCacheSerializer.INSTANCE);
        bind(MergeabilityCache.class).to(MergeabilityCacheImpl.class);
      }
    };
  }

  public static ObjectId toId(Ref ref) {
    return ref != null && ref.getObjectId() != null ? ref.getObjectId() : ObjectId.zeroId();
  }

  public static class EntryKey {
    private ObjectId commit;
    private ObjectId into;
    private SubmitType submitType;
    private String mergeStrategy;

    public EntryKey(ObjectId commit, ObjectId into, SubmitType submitType, String mergeStrategy) {
      checkArgument(
          submitType != SubmitType.INHERIT,
          "Cannot cache %s.%s",
          SubmitType.class.getSimpleName(),
          submitType);
      this.commit = checkNotNull(commit, "commit");
      this.into = checkNotNull(into, "into");
      this.submitType = checkNotNull(submitType, "submitType");
      this.mergeStrategy = checkNotNull(mergeStrategy, "mergeStrategy");
    }

    public ObjectId getCommit() {
      return commit;
    }

    public ObjectId getInto() {
      return into;
    }

    public SubmitType getSubmitType() {
      return submitType;
    }

    public String getMergeStrategy() {
      return mergeStrategy;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof EntryKey) {
        EntryKey k = (EntryKey) o;
        return commit.equals(k.commit)
            && into.equals(k.into)
            && submitType == k.submitType
            && mergeStrategy.equals(k.mergeStrategy);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(commit, into, submitType, mergeStrategy);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("commit", commit.name())
          .add("into", into.name())
          .addValue(submitType)
          .addValue(mergeStrategy)
          .toString();
    }

    static enum Serializer implements CacheSerializer<EntryKey> {
      INSTANCE;

      private static final Converter<String, SubmitType> SUBMIT_TYPE_CONVERTER =
          Enums.stringConverter(SubmitType.class);

      @Override
      public byte[] serialize(EntryKey object) {
        byte[] buf = new byte[Constants.OBJECT_ID_LENGTH];
        MergeabilityKeyProto.Builder b = MergeabilityKeyProto.newBuilder();
        object.getCommit().copyRawTo(buf, 0);
        b.setCommit(ByteString.copyFrom(buf));
        object.getInto().copyRawTo(buf, 0);
        b.setInto(ByteString.copyFrom(buf));
        b.setSubmitType(SUBMIT_TYPE_CONVERTER.reverse().convert(object.getSubmitType()));
        b.setMergeStrategy(object.getMergeStrategy());
        return ProtoCacheSerializers.toByteArray(b.build());
      }

      @Override
      public EntryKey deserialize(byte[] in) {
        MergeabilityKeyProto proto;
        try {
          proto = MergeabilityKeyProto.parseFrom(in);
        } catch (IOException e) {
          throw new IllegalArgumentException("Failed to deserialize mergeability cache key");
        }
        byte[] buf = new byte[Constants.OBJECT_ID_LENGTH];
        proto.getCommit().copyTo(buf, 0);
        ObjectId commit = ObjectId.fromRaw(buf);
        proto.getInto().copyTo(buf, 0);
        ObjectId into = ObjectId.fromRaw(buf);
        return new EntryKey(
            commit,
            into,
            SUBMIT_TYPE_CONVERTER.convert(proto.getSubmitType()),
            proto.getMergeStrategy());
      }
    }
  }

  public static class MergeabilityWeigher implements Weigher<EntryKey, Boolean> {
    @Override
    public int weigh(EntryKey k, Boolean v) {
      return 16
          + 2 * (16 + 20)
          + 3 * 8 // Size of EntryKey, 64-bit JVM.
          + 8; // Size of Boolean.
    }
  }

  private final SubmitDryRun submitDryRun;
  private final Cache<EntryKey, Boolean> cache;

  @Inject
  MergeabilityCacheImpl(
      SubmitDryRun submitDryRun, @Named(CACHE_NAME) Cache<EntryKey, Boolean> cache) {
    this.submitDryRun = submitDryRun;
    this.cache = cache;
  }

  @Override
  public boolean get(
      ObjectId commit,
      Ref intoRef,
      SubmitType submitType,
      String mergeStrategy,
      Branch.NameKey dest,
      Repository repo) {
    ObjectId into = intoRef != null ? intoRef.getObjectId() : ObjectId.zeroId();
    EntryKey key = new EntryKey(commit, into, submitType, mergeStrategy);
    try {
      return cache.get(
          key,
          () -> {
            if (key.into.equals(ObjectId.zeroId())) {
              return true; // Assume yes on new branch.
            }
            try (CodeReviewRevWalk rw = CodeReviewCommit.newRevWalk(repo)) {
              Set<RevCommit> accepted = SubmitDryRun.getAlreadyAccepted(repo, rw);
              accepted.add(rw.parseCommit(key.into));
              accepted.addAll(Arrays.asList(rw.parseCommit(key.commit).getParents()));
              return submitDryRun.run(
                  key.submitType, repo, rw, dest, key.into, key.commit, accepted);
            }
          });
    } catch (ExecutionException | UncheckedExecutionException e) {
      log.error(
          String.format(
              "Error checking mergeability of %s into %s (%s)",
              key.commit.name(), key.into.name(), key.submitType.name()),
          e.getCause());
      return false;
    }
  }

  @Override
  public Boolean getIfPresent(
      ObjectId commit, Ref intoRef, SubmitType submitType, String mergeStrategy) {
    return cache.getIfPresent(new EntryKey(commit, toId(intoRef), submitType, mergeStrategy));
  }
}
