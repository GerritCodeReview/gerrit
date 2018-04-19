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

import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.cache.proto.Cache.ChangeKindProto;

public class ChangeKindProtoConverter
    extends AbstractProtoConverter<ChangeKind, ChangeKindProto, ChangeKindProto.Builder> {
  private static final int VERSION = 1;

  public ChangeKindProtoConverter() {
    super(ChangeKindProto.class, ChangeKindProto.Builder.class, VERSION);
  }

  @Override
  protected void populateBuilder(ChangeKindProto.Builder builder, ChangeKind obj) {
    builder.setKind(convertEnumByName(obj, ChangeKindProto.Kind.class));
  }

  @Override
  protected ChangeKind fromProto(ChangeKindProto proto) {
    return convertEnumByName(proto.getKind(), ChangeKind.class);
  }
}
