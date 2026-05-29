/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.proto.Entities;
import com.google.protobuf.Parser;

/**
 * Proto converter between {@link NotifyInfo} and {@link
 * com.google.gerrit.proto.Entities.NotifyInfo}.
 */
@Immutable
public enum NotifyInfoProtoConverter
    implements SafeProtoConverter<Entities.NotifyInfo, NotifyInfo> {
  INSTANCE;

  @Override
  public Entities.NotifyInfo toProto(NotifyInfo notifyInfo) {
    Entities.NotifyInfo.Builder builder = Entities.NotifyInfo.newBuilder();
    if (notifyInfo.accounts != null) {
      builder.addAllAccounts(notifyInfo.accounts);
    }
    return builder.build();
  }

  @Override
  public NotifyInfo fromProto(Entities.NotifyInfo proto) {
    return new NotifyInfo(proto.getAccountsList());
  }

  @Override
  public Parser<Entities.NotifyInfo> getParser() {
    return Entities.NotifyInfo.parser();
  }

  @Override
  public Class<Entities.NotifyInfo> getProtoClass() {
    return Entities.NotifyInfo.class;
  }

  @Override
  public Class<NotifyInfo> getEntityClass() {
    return NotifyInfo.class;
  }
}
