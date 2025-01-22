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

package com.google.gerrit.acceptance.testsuite.index;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.gerrit.index.IndexType;
import com.google.inject.Inject;
import org.junit.Test;

public class LuceneIndexMetricsIT extends AbstractDaemonTest {

  @Inject protected TestMetricMaker testMetricMaker;
  private boolean isLuceneIndex =
      IndexType.fromEnvironment().map(IndexType::isLucene).orElse(false);

  @Test
  public void checkProjectsIndexMetric() throws Exception {
    assume().that(isLuceneIndex).isTrue();
    int numberProjects = luceneIndexMetricValueOf("projects");
    gApi.projects().create("some_project");
    assertThat(luceneIndexMetricValueOf("projects")).isEqualTo(numberProjects + 1);
  }

  @Test
  public void checkChangesIndexMetric() throws Exception {
    assume().that(isLuceneIndex).isTrue();
    int numberChanges = luceneIndexMetricValueOf("changes");
    createChange();
    assertThat(luceneIndexMetricValueOf("changes")).isEqualTo(numberChanges + 1);
  }

  @Test
  public void checkAccountsIndexMetric() throws Exception {
    assume().that(isLuceneIndex).isTrue();
    int numberAccounts = luceneIndexMetricValueOf("accounts");
    gApi.accounts().create("some_account");
    assertThat(luceneIndexMetricValueOf("accounts")).isEqualTo(numberAccounts + 1);
  }

  @Test
  public void checkGroupsIndexMetric() throws Exception {
    assume().that(isLuceneIndex).isTrue();
    int numberGroups = luceneIndexMetricValueOf("groups");
    gApi.groups().create("some_group");
    assertThat(luceneIndexMetricValueOf("groups")).isEqualTo(numberGroups + 1);
  }

  private int luceneIndexMetricValueOf(String metric) {
    return (int) testMetricMaker.getCallbackMetricValue(String.format("index/lucene/%s", metric));
  }
}
