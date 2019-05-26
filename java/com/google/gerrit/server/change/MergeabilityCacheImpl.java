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
import static java.util.Objects.requireNonNull;

import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.base.MoreObjects;
import com.google.common.cache.Cache;
import com.google.common.cache.Weigher;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.MergeabilityKeyProto;
import com.google.gerrit.server.cache.serialize.BooleanCacheSerializer;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.submit.SubmitDryRun;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class MergeabilityCacheImpl implements MergeabilityCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
      this.commit = requireNonNull(commit, "commit");
      this.into = requireNonNull(into, "into");
      this.submitType = requireNonNull(submitType, "submitType");
      this.mergeStrategy = requireNonNull(mergeStrategy, "mergeStrategy");
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

    enum Serializer implements CacheSerializer<EntryKey> {
      INSTANCE;

      private static final Converter<String, SubmitType> SUBMIT_TYPE_CONVERTER =
          Enums.stringConverter(SubmitType.class);

      @Override
      public byte[] serialize(EntryKey object) {
        ObjectIdConverter idConverter = ObjectIdConverter.create();
        return Protos.toByteArray(
            MergeabilityKeyProto.newBuilder()
                .setCommit(idConverter.toByteString(object.getCommit()))
                .setInto(idConverter.toByteString(object.getInto()))
                .setSubmitType(SUBMIT_TYPE_CONVERTER.reverse().convert(object.getSubmitType()))
                .setMergeStrategy(object.getMergeStrategy())
                .build());
      }

      @Override
      public EntryKey deserialize(byte[] in) {
        MergeabilityKeyProto proto = Protos.parseUnchecked(MergeabilityKeyProto.parser(), in);
        ObjectIdConverter idConverter = ObjectIdConverter.create();
        return new EntryKey(
            idConverter.fromByteString(proto.getCommit()),
            idConverter.fromByteString(proto.getInto()),
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
      BranchNameKey dest,
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
                  null, key.submitType, repo, rw, dest, key.into, key.commit, accepted);
            }
          });
    } catch (ExecutionException | UncheckedExecutionException e) {
      logger.atSevere().withCause(e.getCause()).log(
          "Error checking mergeability of %s into %s (%s)",
          key.commit.name(), key.into.name(), key.submitType.name());
      return false;
    }
  }

  @Override
  public Boolean getIfPresent(
      ObjectId commit, Ref intoRef, SubmitType submitType, String mergeStrategy) {
    return cache.getIfPresent(new EntryKey(commit, toId(intoRef), submitType, mergeStrategy));
  }
}
