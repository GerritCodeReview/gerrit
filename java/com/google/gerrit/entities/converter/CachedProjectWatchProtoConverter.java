// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Enums;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.NotifyConfig;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.ProjectWatchKey;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.proto.Cache.ProjectWatchProto;
import com.google.protobuf.Parser;
import java.util.Map;

/**
 * Proto converter between {@link ProjectWatchProto} and {@code
 * Map.Entry<ProjectWatches.ProjectWatchKey, ImmutableSet<NotifyType>>}.
 */
@Immutable
public enum CachedProjectWatchProtoConverter
    implements
        ProtoConverter<ProjectWatchProto, Map.Entry<ProjectWatchKey, ImmutableSet<NotifyType>>> {
  INSTANCE;

  @Override
  public ProjectWatchProto toProto(Map.Entry<ProjectWatchKey, ImmutableSet<NotifyType>> watch) {
    Cache.ProjectWatchProto.Builder builder =
        Cache.ProjectWatchProto.newBuilder().setProject(watch.getKey().project().get());
    if (watch.getKey().filter() != null) {
      builder.setFilter(watch.getKey().filter());
    }
    watch
        .getValue()
        .forEach(
            n ->
                builder.addNotifyType(
                    Enums.stringConverter(NotifyConfig.NotifyType.class).reverse().convert(n)));
    return builder.build();
  }

  @Override
  public Map.Entry<ProjectWatchKey, ImmutableSet<NotifyType>> fromProto(ProjectWatchProto proto) {
    return Map.entry(
        ProjectWatchKey.create(Project.nameKey(proto.getProject()), proto.getFilter()),
        proto.getNotifyTypeList().stream()
            .map(e -> Enums.stringConverter(NotifyConfig.NotifyType.class).convert(e))
            .collect(toImmutableSet()));
  }

  @Override
  public Parser<ProjectWatchProto> getParser() {
    return ProjectWatchProto.parser();
  }
}
