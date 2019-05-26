// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.proto.Entities;
import com.google.protobuf.Parser;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Proto converter for {@code ObjectId}s.
 *
 * <p>This converter uses the hex representation of object IDs embedded in a wrapper proto type,
 * rather than a more parsimonious implementation (e.g. a raw byte array), for two reasons:
 *
 * <ul>
 *   <li>Hex strings are easier to read and work with when reading and writing protos in text
 *       formats, for example in test failure messages, or when using command-line tools.
 *   <li>This maintains backwards wire compatibility with a pre-NoteDb implementation.
 * </ul>
 */
@Immutable
public enum ObjectIdProtoConverter implements ProtoConverter<Entities.ObjectId, ObjectId> {
  INSTANCE;

  @Override
  public Entities.ObjectId toProto(ObjectId objectId) {
    return Entities.ObjectId.newBuilder().setName(objectId.name()).build();
  }

  @Override
  public ObjectId fromProto(Entities.ObjectId proto) {
    return ObjectId.fromString(proto.getName());
  }

  @Override
  public Parser<Entities.ObjectId> getParser() {
    return Entities.ObjectId.parser();
  }
}
