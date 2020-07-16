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

import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubscribeSection;
import com.google.gerrit.server.cache.proto.Cache;

/** Helper to (de)serialize values for caches. */
public class SubscribeSectionSerializer {
  public static SubscribeSection deserialize(Cache.SubscribeSectionProto proto) {
    SubscribeSection.Builder builder =
        SubscribeSection.builder(Project.nameKey(proto.getProjectName()));
    proto.getMatchingRefSpecsList().forEach(rs -> builder.addMatchingRefSpec(rs));
    proto.getMultiMatchRefSpecsList().forEach(rs -> builder.addMultiMatchRefSpec(rs));
    return builder.build();
  }

  public static Cache.SubscribeSectionProto serialize(SubscribeSection autoValue) {
    Cache.SubscribeSectionProto.Builder builder =
        Cache.SubscribeSectionProto.newBuilder().setProjectName(autoValue.project().get());
    autoValue.multiMatchRefSpecsAsString().forEach(rs -> builder.addMultiMatchRefSpecs(rs));
    autoValue.matchingRefSpecsAsString().forEach(rs -> builder.addMatchingRefSpecs(rs));
    return builder.build();
  }

  private SubscribeSectionSerializer() {}
}
