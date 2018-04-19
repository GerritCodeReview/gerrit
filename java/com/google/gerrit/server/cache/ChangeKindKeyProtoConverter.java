// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.cache;

import com.google.gerrit.server.cache.proto.Cache.ChangeKindKeyProto;
import com.google.gerrit.server.change.ChangeKindCacheImpl;
import com.google.protobuf.ByteString;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

public class ChangeKindKeyProtoConverter
    extends AbstractProtoConverter<
        ChangeKindCacheImpl.Key, ChangeKindKeyProto, ChangeKindKeyProto.Builder> {
  private static final int VERSION = 1;

  public ChangeKindKeyProtoConverter() {
    super(ChangeKindKeyProto.class, ChangeKindKeyProto.Builder.class, VERSION);
  }

  @Override
  protected void populateBuilder(ChangeKindKeyProto.Builder builder, ChangeKindCacheImpl.Key obj) {
    byte[] buf = new byte[Constants.OBJECT_ID_LENGTH];
    obj.getPrior().copyRawTo(buf, 0);
    builder.setPrior(ByteString.copyFrom(buf));
    obj.getNext().copyRawTo(buf, 0);
    builder.setNext(ByteString.copyFrom(buf));
    builder.setStrategyName(obj.getStrategyName());
  }

  @Override
  protected ChangeKindCacheImpl.Key fromProto(ChangeKindKeyProto proto) {
    return new ChangeKindCacheImpl.Key(
        ObjectId.fromRaw(proto.getPrior().toByteArray()),
        ObjectId.fromRaw(proto.getNext().toByteArray()),
        proto.getStrategyName());
  }
}
