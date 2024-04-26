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

package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.converter.SafeProtoConverter.ConvertibleToProto;
import com.google.protobuf.MessageLite;

/**
 * An extension to {@link ProtoConverter} that enforces the Entity class and the Proto class to stay
 * in sync. The enforcement is done by {@link SafeProtoConverterTest}.
 *
 * <p>Implementing classes must be: 1. Enums with a single enum. 2. Located under {@code
 * com.google.gerrit.entities}.
 *
 * <p>In addition, the Java entities must implement {@link ConvertibleToProto}.
 */
@Immutable
public interface SafeProtoConverter<P extends MessageLite, C extends ConvertibleToProto>
    extends ProtoConverter<P, C> {
  interface ConvertibleToProto {}

  Class<P> getProtoClass();

  Class<C> getEntityClass();
}
