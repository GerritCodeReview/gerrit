// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.index;

import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class IndexMetrics {

  private final MetricMaker metricMaker;
  private static final String ACCOUNT_INDEX = "ACCOUNT";
  private static final String CHANGE_INDEX = "CHANGE";
  private static final String GROUP_INDEX = "GROUP";
  private static final String PROJECT_INDEX = "PROJECT";

  @Inject
  public IndexMetrics(
      MetricMaker metrics,
      ProjectIndexCollection projectIndexCollection,
      ChangeIndexCollection changeIndexCollection,
      GroupIndexCollection groupIndexCollection,
      AccountIndexCollection accountIndexCollection) {

    this.metricMaker = metrics;

    createRegistrationHandler(accountIndexCollection, ACCOUNT_INDEX);
    createRegistrationHandler(changeIndexCollection, CHANGE_INDEX);
    createRegistrationHandler(groupIndexCollection, GROUP_INDEX);
    createRegistrationHandler(projectIndexCollection, PROJECT_INDEX);
  }

  private void createRegistrationHandler(IndexCollection indexCollection, String indexName) {
    metricMaker.newCallbackMetric(
        String.format("indexes/%s", indexName.toLowerCase()),
        Integer.class,
        new Description(String.format("%s Index documents", indexName))
            .setGauge()
            .setUnit("documents"),
        () -> {
          if (indexCollection.getSearchIndex() == null) {
            return -1;
          } else {
            return indexCollection.getSearchIndex().numDocs();
          }
        });
  }
}
