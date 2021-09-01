// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.auto.value.AutoValue;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.IntraLineDiffKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class IntraLineDiffKey {
  public static IntraLineDiffKey create(ObjectId aId, ObjectId bId, Whitespace whitespace) {
    return new AutoValue_IntraLineDiffKey(aId, bId, whitespace);
  }

  public abstract ObjectId getBlobA();

  public abstract ObjectId getBlobB();

  public abstract Whitespace getWhitespace();

  public enum Serializer implements CacheSerializer<IntraLineDiffKey> {
    INSTANCE;

    @Override
    public byte[] serialize(IntraLineDiffKey key) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      IntraLineDiffKeyProto proto =
          IntraLineDiffKeyProto.newBuilder()
              .setAId(idConverter.toByteString(key.getBlobA()))
              .setBId(idConverter.toByteString(key.getBlobB()))
              .setWhitespace(IntraLineDiffKeyProto.Whitespace.valueOf(key.getWhitespace().name()))
              .build();
      return proto.toByteArray();
    }

    @Override
    public IntraLineDiffKey deserialize(byte[] in) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      IntraLineDiffKeyProto proto = Protos.parseUnchecked(IntraLineDiffKeyProto.parser(), in);
      // Handle unknown deserialized values. Convert to the default
      Whitespace ws = Whitespace.IGNORE_ALL; // default
      if (!proto.getWhitespace().equals(IntraLineDiffKeyProto.Whitespace.UNRECOGNIZED)) {
        ws = Whitespace.valueOf(proto.getWhitespace().name());
      }
      return IntraLineDiffKey.create(
          idConverter.fromByteString(proto.getAId()),
          idConverter.fromByteString(proto.getBId()),
          ws);
    }
  }
}
