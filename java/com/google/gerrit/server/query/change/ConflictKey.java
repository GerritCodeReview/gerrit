// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.collect.Ordering;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.cache.CacheSerializer;
import com.google.gerrit.server.cache.ProtoCacheSerializers;
import com.google.gerrit.server.cache.ProtoCacheSerializers.ObjectIdConverter;
import com.google.gerrit.server.cache.proto.Cache.ConflictKeyProto;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class ConflictKey {
  public static ConflictKey create(
      AnyObjectId commit, AnyObjectId otherCommit, SubmitType submitType, boolean contentMerge) {
    ObjectId commitCopy = commit.copy();
    ObjectId otherCommitCopy = otherCommit.copy();
    if (submitType == SubmitType.FAST_FORWARD_ONLY) {
      // The conflict check for FF-only is non-symmetrical, and we need to treat (X, Y) differently
      // from (Y, X). Store the commits in the input order.
      return new AutoValue_ConflictKey(commitCopy, otherCommitCopy, submitType, contentMerge);
    }
    // Otherwise, the check is symmetrical; sort commit/otherCommit before storing, so the actual
    // key is independent of the order in which they are passed to this method.
    return new AutoValue_ConflictKey(
        Ordering.natural().min(commitCopy, otherCommitCopy),
        Ordering.natural().max(commitCopy, otherCommitCopy),
        submitType,
        contentMerge);
  }

  @VisibleForTesting
  static ConflictKey createWithoutNormalization(
      AnyObjectId commit, AnyObjectId otherCommit, SubmitType submitType, boolean contentMerge) {
    return new AutoValue_ConflictKey(commit.copy(), otherCommit.copy(), submitType, contentMerge);
  }

  public abstract ObjectId commit();

  public abstract ObjectId otherCommit();

  public abstract SubmitType submitType();

  public abstract boolean contentMerge();

  public static enum Serializer implements CacheSerializer<ConflictKey> {
    INSTANCE;

    private static final Converter<String, SubmitType> SUBMIT_TYPE_CONVERTER =
        Enums.stringConverter(SubmitType.class);

    @Override
    public byte[] serialize(ConflictKey object) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return ProtoCacheSerializers.toByteArray(
          ConflictKeyProto.newBuilder()
              .setCommit(idConverter.toByteString(object.commit()))
              .setOtherCommit(idConverter.toByteString(object.otherCommit()))
              .setSubmitType(SUBMIT_TYPE_CONVERTER.reverse().convert(object.submitType()))
              .setContentMerge(object.contentMerge())
              .build());
    }

    @Override
    public ConflictKey deserialize(byte[] in) {
      ConflictKeyProto proto = ProtoCacheSerializers.parseUnchecked(ConflictKeyProto.parser(), in);
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      return create(
          idConverter.fromByteString(proto.getCommit()),
          idConverter.fromByteString(proto.getOtherCommit()),
          SUBMIT_TYPE_CONVERTER.convert(proto.getSubmitType()),
          proto.getContentMerge());
    }
  }
}
