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

package com.google.gerrit.acceptance.server.index.scheduler;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.index.scheduler.PeriodicIndexerConfig;
import com.google.gerrit.server.index.scheduler.PeriodicIndexerConfigProvider;
import com.google.inject.Inject;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class PeriodicIndexerConfigProviderIT extends AbstractDaemonTest {

  @Inject private PeriodicIndexerConfigProvider indexConfigProvider;

  @Test
  public void emptyConfigOnPrimary_noIndexersWillRun() {
    Map<String, PeriodicIndexerConfig> indexConfig = indexConfigProvider.get();
    assertThat(indexConfig).isEmpty();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "container.replica", value = "true")
  public void emptyConfigOnReplica_groupsIndexerWillRun() throws Exception {
    Map<String, PeriodicIndexerConfig> indexConfig = indexConfigProvider.get();
    assertThat(indexConfig).hasSize(1);
    assertThat(indexConfig).containsKey("groups");

    PeriodicIndexerConfig groupsIndexerConfig = indexConfig.get("groups");
    assertThat(groupsIndexerConfig.runOnStartup()).isTrue();
    assertThat(groupsIndexerConfig.enabled()).isTrue();
    assertThat(groupsIndexerConfig.schedule())
        .isEqualTo(PeriodicIndexerConfigProvider.DEFAULT_SCHEDULE);
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "scheduledIndexer.accounts.runOnStartup", value = "false")
  @GerritConfig(name = "scheduledIndexer.accounts.enabled", value = "true")
  @GerritConfig(name = "scheduledIndexer.accounts.startTime", value = "01:00")
  @GerritConfig(name = "scheduledIndexer.accounts.interval", value = "2h")
  public void parseScheduledIndexerConfig() {
    Map<String, PeriodicIndexerConfig> indexConfig = indexConfigProvider.get();
    assertThat(indexConfig).hasSize(1);
    assertThat(indexConfig).containsKey("accounts");

    PeriodicIndexerConfig accountsIndexerConfig = indexConfig.get("accounts");
    assertThat(accountsIndexerConfig.runOnStartup()).isFalse();
    assertThat(accountsIndexerConfig.enabled()).isTrue();

    Schedule schedule = accountsIndexerConfig.schedule();
    assertThat(schedule.interval()).isEqualTo(TimeUnit.HOURS.toMillis(2));
    // NOTE: it is impossible to assert the equality of the two Schedule instance
    // because every instance computes initialDelay based on the current time.
    // Therefore, we only assert the equality of the interval here.
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "container.replica", value = "true")
  @GerritConfig(name = "index.scheduledIndexer.runOnStartup", value = "true")
  @GerritConfig(name = "index.scheduledIndexer.enabled", value = "false")
  @GerritConfig(name = "index.scheduledIndexer.startTime", value = "01:00")
  @GerritConfig(name = "index.scheduledIndexer.interval", value = "3h")
  public void indexSectionStillSupportedForGroups() {
    Map<String, PeriodicIndexerConfig> indexConfig = indexConfigProvider.get();
    assertThat(indexConfig).hasSize(1);
    assertThat(indexConfig).containsKey("groups");

    PeriodicIndexerConfig groupsIndexerConfig = indexConfig.get("groups");
    assertThat(groupsIndexerConfig.runOnStartup()).isTrue();
    assertThat(groupsIndexerConfig.enabled()).isFalse();

    Schedule schedule = groupsIndexerConfig.schedule();
    assertThat(schedule.interval()).isEqualTo(TimeUnit.HOURS.toMillis(3));
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "container.replica", value = "true")
  @GerritConfig(name = "scheduledIndexer.groups.runOnStartup", value = "false")
  @GerritConfig(name = "scheduledIndexer.groups.enabled", value = "true")
  @GerritConfig(name = "scheduledIndexer.groups.startTime", value = "01:00")
  @GerritConfig(name = "scheduledIndexer.groups.interval", value = "2h")
  @GerritConfig(name = "index.scheduledIndexer.runOnStartup", value = "true")
  @GerritConfig(name = "index.scheduledIndexer.enabled", value = "false")
  @GerritConfig(name = "index.scheduledIndexer.startTime", value = "01:00")
  @GerritConfig(name = "index.scheduledIndexer.interval", value = "3h")
  public void scheduledIndexerSectionOverridesIndexSection() {
    Map<String, PeriodicIndexerConfig> indexConfig = indexConfigProvider.get();
    assertThat(indexConfig).hasSize(1);
    assertThat(indexConfig).containsKey("groups");

    PeriodicIndexerConfig groupsIndexerConfig = indexConfig.get("groups");
    assertThat(groupsIndexerConfig.runOnStartup()).isFalse();
    assertThat(groupsIndexerConfig.enabled()).isTrue();

    Schedule schedule = groupsIndexerConfig.schedule();
    assertThat(schedule.interval()).isEqualTo(TimeUnit.HOURS.toMillis(2));
  }
}
