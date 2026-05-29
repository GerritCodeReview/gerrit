// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.git.validators;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/** Validator for topic changes. */
@Singleton
public class TopicValidator {

  private final Provider<InternalChangeQuery> queryProvider;
  private final int topicLimit;

  @Inject
  TopicValidator(
      @GerritServerConfig Config serverConfig, Provider<InternalChangeQuery> queryProvider) {
    this.queryProvider = queryProvider;
    int configuredLimit = serverConfig.getInt("change", "topicLimit", 5_000);
    this.topicLimit = configuredLimit > 0 ? configuredLimit : Integer.MAX_VALUE;
  }

  public void validateSize(@Nullable String topic) throws ValidationException {
    if (Strings.isNullOrEmpty(topic)) {
      return;
    }
    try (TraceTimer ignored = TraceContext.newTimer("Validate Topic Size", Metadata.empty())) {
      int topicSize =
          queryProvider.get().noFields().setLimit(topicLimit + 1).byTopicOpen(topic).size();
      if (topicSize >= topicLimit) {
        throw new ValidationException(
            String.format(
                "Topic '%s' already contains maximum number of allowed changes per 'topicLimit'"
                    + " server config value %d.",
                topic, topicLimit));
      }
    }
  }
}
