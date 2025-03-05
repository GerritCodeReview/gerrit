/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.extensions.auth.AuthTokenInput;
import com.google.gerrit.proto.Entities;
import com.google.protobuf.Parser;

/**
 * Proto converter between {@link AuthTokenInput} and {@link
 * com.google.gerrit.proto.Entities.AuthTokenInput}.
 */
@Immutable
public enum TokenInputProtoConverter
    implements ProtoConverter<Entities.AuthTokenInput, AuthTokenInput> {
  INSTANCE;

  @Override
  public Entities.AuthTokenInput toProto(AuthTokenInput tokenInput) {
    Entities.AuthTokenInput.Builder builder = Entities.AuthTokenInput.newBuilder();
    if (tokenInput.id != null) {
      builder.setId(tokenInput.id);
    }
    if (tokenInput.token != null) {
      builder.setToken(tokenInput.token);
    }

    return builder.build();
  }

  @Override
  public AuthTokenInput fromProto(Entities.AuthTokenInput proto) {
    AuthTokenInput tokenInput = new AuthTokenInput();
    if (proto.hasId()) {
      tokenInput.id = proto.getId();
    }
    if (proto.hasToken()) {
      tokenInput.token = proto.getToken();
    }

    return tokenInput;
  }

  @Override
  public Parser<Entities.AuthTokenInput> getParser() {
    return Entities.AuthTokenInput.parser();
  }
}
