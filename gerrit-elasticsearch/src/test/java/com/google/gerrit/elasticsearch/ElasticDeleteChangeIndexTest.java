// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.elasticsearch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.elasticsearch.testing.ElasticContainer;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils;
import com.google.gerrit.elasticsearch.testing.ElasticTestUtils.ElasticNodeInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Injector;
import java.util.UUID;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@NoHttpd
@UseLocalDisk
@RunWith(ConfigSuite.class)
public class ElasticDeleteChangeIndexTest extends AbstractDaemonTest {

  private static ElasticContainer<?> container;

  @ConfigSuite.Default
  public static Config elasticsearch() {
    ElasticNodeInfo elasticNodeInfo;
    try {
      container = ElasticContainer.createAndStart();
      elasticNodeInfo = new ElasticNodeInfo(container.getHttpHost().getPort());
    } catch (Throwable t) {
      return null;
    }
    String indicesPrefix = UUID.randomUUID().toString();
    Config cfg = new Config();
    ElasticTestUtils.configure(cfg, elasticNodeInfo.port, indicesPrefix);
    return cfg;
  }

  public void configureIndex(Injector injector) throws Exception {
    ElasticTestUtils.createAllIndexes(injector);
  }

  @Test
  public void testRemoveChangeIndex() throws Exception {
    configureIndex(server.getTestInjector());
    PushOneCommit.Result change = createMergeCommitChange("refs/for/master", "t.txt");
    String changeId = change.getChangeId();
    Change.Id changeLegacyId = change.getChange().getId();

    assertThat(gApi.changes().query("file:t.txt").get().stream().map(c -> c.changeId))
        .contains(changeId);

    indexer.delete(changeLegacyId);

    assertThat(gApi.changes().query("file:t.txt").get().stream().map(c -> c.changeId))
        .doesNotContain(changeId);
  }

  @After
  public void stopElasticServer() {
    if (container != null) {
      container.stop();
      container = null;
    }
  }
}
