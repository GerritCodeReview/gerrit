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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.cache.CacheSerializer;
import com.google.gerrit.server.cache.ProtoCacheSerializers;
import com.google.gerrit.server.cache.ProtoCacheSerializers.ObjectIdHelper;
import com.google.gerrit.server.cache.proto.Cache.ConflictKeyProto;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class ConflictKey {
  public static ConflictKey create(
      ObjectId commit, ObjectId otherCommit, SubmitType submitType, boolean contentMerge) {
    return new AutoValue_ConflictKey(
        checkNotNull(commit, "commit").copy(),
        checkNotNull(otherCommit, "otherCommit").copy(),
        submitType,
        contentMerge);
  }

  public abstract ObjectId commit();

  public abstract ObjectId otherCommit();

  public abstract SubmitType submitType();

  public abstract boolean isContentMerge();

  public static enum Serializer implements CacheSerializer<ConflictKey> {
    INSTANCE;

    private static final Converter<String, SubmitType> SUBMIT_TYPE_CONVERTER =
        Enums.stringConverter(SubmitType.class);

    @Override
    public byte[] serialize(ConflictKey object) {
      ObjectIdHelper idHelper = new ObjectIdHelper();
      return ProtoCacheSerializers.toByteArray(
          ConflictKeyProto.newBuilder()
              .setCommit(idHelper.toByteString(object.commit()))
              .setOtherCommit(idHelper.toByteString(object.otherCommit()))
              .setSubmitType(SUBMIT_TYPE_CONVERTER.reverse().convert(object.submitType()))
              .setIsContentMerge(object.isContentMerge())
              .build());
    }

    @Override
    public ConflictKey deserialize(byte[] in) {
      ConflictKeyProto proto = ProtoCacheSerializers.parseUnchecked(ConflictKeyProto.parser(), in);
      return create(
          ObjectIdHelper.fromByteString(proto.getCommit()),
          ObjectIdHelper.fromByteString(proto.getOtherCommit()),
          SUBMIT_TYPE_CONVERTER.convert(proto.getSubmitType()),
          proto.getIsContentMerge());
    }
  }
}
