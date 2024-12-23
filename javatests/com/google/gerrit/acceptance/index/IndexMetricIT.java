// Copyright (C) 2024 The Android Open Source Project
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
package com.google.gerrit.acceptance.index;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.inject.Inject;
import org.junit.Test;

public class IndexMetricIT extends AbstractDaemonTest {
  @Inject protected TestMetricMaker testMetricMaker;

  @Test
  public void checkProjectsIndexMetric() throws Exception {
    int numberProjects = intMetricValueOf("indexes/projects");
    gApi.projects().create("some_project");
    assertThat(intMetricValueOf("indexes/projects")).isEqualTo(numberProjects + 1);
  }

  @Test
  public void checkChangesIndexMetric() throws Exception {
    int numberChanges = intMetricValueOf("indexes/changes");
    createChange();
    assertThat(intMetricValueOf("indexes/changes")).isEqualTo(numberChanges + 1);
  }

  @Test
  public void checkAccountsIndexMetric() throws Exception {
    int numberAccounts = intMetricValueOf("indexes/accounts");
    gApi.accounts().create("some_account");
    assertThat(intMetricValueOf("indexes/accounts")).isEqualTo(numberAccounts + 1);
  }

  @Test
  public void checkGroupsIndexMetric() throws Exception {
    int numberGroups = intMetricValueOf("indexes/groups");
    gApi.groups().create("some_group");
    assertThat(intMetricValueOf("indexes/groups")).isEqualTo(numberGroups + 1);
  }

  private int intMetricValueOf(String metric) {
    return (int) testMetricMaker.getCallbackMetricValue(metric);
  }
}
