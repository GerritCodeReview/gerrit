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

import com.google.gerrit.entities.StoredCommentLinkInfo;
import com.google.gerrit.server.cache.proto.Cache;

/** Helper to (de)serialize values for caches. */
public class StoredCommentLinkInfoSerializer {
  public static StoredCommentLinkInfo deserialize(Cache.StoredCommentLinkInfoProto proto) {
    return StoredCommentLinkInfo.builder(proto.getName())
        .setMatch(emptyToNull(proto.getMatch()))
        .setLink(emptyToNull(proto.getLink()))
        .setHtml(emptyToNull(proto.getHtml()))
        .setEnabled(proto.getEnabled())
        .setOverrideOnly(proto.getOverrideOnly())
        .build();
  }

  public static Cache.StoredCommentLinkInfoProto serialize(StoredCommentLinkInfo autoValue) {
    return Cache.StoredCommentLinkInfoProto.newBuilder()
        .setName(autoValue.getName())
        .setMatch(nullToEmpty(autoValue.getMatch()))
        .setLink(nullToEmpty(autoValue.getLink()))
        .setHtml(nullToEmpty(autoValue.getHtml()))
        .setEnabled(autoValue.getEnabled())
        .setOverrideOnly(autoValue.getOverrideOnly())
        .build();
  }

  private StoredCommentLinkInfoSerializer() {}
}
