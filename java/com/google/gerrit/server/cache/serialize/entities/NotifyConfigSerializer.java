// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.gerrit.entities.NotifyConfig;
import com.google.gerrit.server.cache.proto.Cache;

/** Helper to (de)serialize values for caches. */
public class NotifyConfigSerializer {
  private static final Converter<String, NotifyConfig.Header> HEADER_CONVERTER =
      Enums.stringConverter(NotifyConfig.Header.class);

  private static final Converter<String, NotifyConfig.NotifyType> NOTIFY_TYPE_CONVERTER =
      Enums.stringConverter(NotifyConfig.NotifyType.class);

  public static NotifyConfig deserialize(Cache.NotifyConfigProto proto) {
    NotifyConfig.Builder builder =
        NotifyConfig.builder()
            .setName(emptyToNull(proto.getName()))
            .setNotify(
                proto.getTypeList().stream()
                    .map(t -> NOTIFY_TYPE_CONVERTER.convert(t))
                    .collect(toImmutableSet()))
            .setFilter(emptyToNull(proto.getFilter()))
            .setHeader(
                proto.getHeader().isEmpty() ? null : HEADER_CONVERTER.convert(proto.getHeader()));
    proto.getGroupsList().stream()
        .map(GroupReferenceSerializer::deserialize)
        .forEach(g -> builder.addGroup(g));
    proto.getAddressesList().stream()
        .map(AddressSerializer::deserialize)
        .forEach(a -> builder.addAddress(a));
    return builder.build();
  }

  public static Cache.NotifyConfigProto serialize(NotifyConfig autoValue) {
    return Cache.NotifyConfigProto.newBuilder()
        .setName(nullToEmpty(autoValue.getName()))
        .addAllType(
            autoValue.getNotify().stream()
                .map(t -> NOTIFY_TYPE_CONVERTER.reverse().convert(t))
                .collect(toImmutableSet()))
        .setFilter(nullToEmpty(autoValue.getFilter()))
        .setHeader(
            autoValue.getHeader() == null
                ? ""
                : HEADER_CONVERTER.reverse().convert(autoValue.getHeader()))
        .addAllGroups(
            autoValue.getGroups().stream()
                .map(GroupReferenceSerializer::serialize)
                .collect(toImmutableSet()))
        .addAllAddresses(
            autoValue.getAddresses().stream()
                .map(AddressSerializer::serialize)
                .collect(toImmutableList()))
        .build();
  }

  private NotifyConfigSerializer() {}
}
