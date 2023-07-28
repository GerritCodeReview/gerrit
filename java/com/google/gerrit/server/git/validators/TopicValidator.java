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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;

/** Validator for topic changes. */
public class TopicValidator {

  private final Provider<InternalChangeQuery> queryProvider;
  private final int topicLimit;

  @Inject
  public TopicValidator(
      @GerritServerConfig Config serverConfig, Provider<InternalChangeQuery> queryProvider) {
    this.queryProvider = queryProvider;
    this.topicLimit = serverConfig.getInt("change", "topicLimit", 5_000);
  }

  public void validateSize(String topic) throws ValidationException {
    if (Strings.isNullOrEmpty(topic)) {
      return;
    }
    int topicSize = queryProvider.get().noFields().byTopicOpen(topic).size();
    if (topicSize >= topicLimit) {
      throw new ValidationException(
          String.format(
              "Topic '%s' already contains maximum number of allowed changes per 'topicLimit'"
                  + " server config value %d.",
              topic, topicLimit));
    }
  }
}
