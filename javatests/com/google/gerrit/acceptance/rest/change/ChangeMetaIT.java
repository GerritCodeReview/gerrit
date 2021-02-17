// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.entities.RefNames.changeMetaRef;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.change.ChangePluginDefinedInfoFactory;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.inject.AbstractModule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

/** Test handling of the NoteDb commit hash in the GetChange endpoint */
public class ChangeMetaIT extends AbstractDaemonTest {
  @Test
  public void metaSha1_fromIndex() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();

    try (AutoCloseable ignored = disableNoteDb()) {
      ChangeInfo change =
          Iterables.getOnlyElement(gApi.changes().query().withQuery("change:" + changeId).get());

      try (Repository repo = repoManager.openRepository(project)) {
        assertThat(change.metaRevId)
            .isEqualTo(
                repo.exactRef(changeMetaRef(Change.id(change._number))).getObjectId().getName());
      }
    }
  }

  @Test
  public void metaSha1_fromNoteDb() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    ChangeInfo before = gApi.changes().id(changeId).get();
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(before.metaRevId)
          .isEqualTo(
              repo.exactRef(changeMetaRef(Change.id(before._number))).getObjectId().getName());
    }
  }

  @Test
  public void ChangeInfo_metaSha1_parameter() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    gApi.changes().id(changeId).setMessage("before\n\n" + "Change-Id: " + result.getChangeId());
    ChangeInfo before = gApi.changes().id(changeId).get();
    gApi.changes().id(changeId).setMessage("after\n\n" + "Change-Id: " + result.getChangeId());
    ChangeInfo after = gApi.changes().id(changeId).get();
    assertThat(after.metaRevId).isNotEqualTo(before.metaRevId);

    RestResponse resp = adminRestSession.get("/changes/" + changeId + "/?meta=" + before.metaRevId);
    resp.assertOK();

    ChangeInfo got;
    try (JsonReader jsonReader = new JsonReader(resp.getReader())) {
      jsonReader.setLenient(true);
      got = newGson().fromJson(jsonReader, ChangeInfo.class);
    }
    assertThat(got.subject).isEqualTo(before.subject);
  }

  @Test
  public void metaUnreachableSha1() throws Exception {
    PushOneCommit.Result ch1 = createChange();
    PushOneCommit.Result ch2 = createChange();

    ChangeInfo info2 = gApi.changes().id(ch2.getChangeId()).get();

    RestResponse resp =
        adminRestSession.get("/changes/" + ch1.getChangeId() + "/?meta=" + info2.metaRevId);

    resp.assertStatus(412);
  }

  protected static class PluginDefinedSimpleAttributeModule extends AbstractModule {
    static class MyMetaHash extends PluginDefinedInfo {
      String myMetaRef;
    };

    static PluginDefinedInfo newMyMetaHash(ChangeData cd) {
      MyMetaHash mmh = new MyMetaHash();
      mmh.myMetaRef = cd.notes().getMetaId().name();
      return mmh;
    }

    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .toInstance(
              (cds, bp, p) -> {
                Map<Change.Id, PluginDefinedInfo> out = new HashMap<>();
                cds.forEach(cd -> out.put(cd.getId(), newMyMetaHash(cd)));
                return out;
              });
    }
  }

  @Test
  public void pluginDefinedAttribute() throws Exception {
    try (AutoCloseable ignored =
        installPlugin("my-plugin", PluginDefinedSimpleAttributeModule.class)) {
      PushOneCommit.Result result = createChange();
      String changeId = result.getChangeId();
      gApi.changes().id(changeId).setMessage("before\n\n" + "Change-Id: " + result.getChangeId());
      ChangeInfo before = gApi.changes().id(changeId).get();
      gApi.changes().id(changeId).setMessage("after\n\n" + "Change-Id: " + result.getChangeId());
      ChangeInfo after = gApi.changes().id(changeId).get();

      RestResponse resp =
          adminRestSession.get("/changes/" + changeId + "/?meta=" + before.metaRevId);
      resp.assertOK();

      Map<String, Object> changeInfo =
          newGson().fromJson(resp.getReader(), new TypeToken<Map<String, Object>>() {}.getType());
      List<Object> plugins = (List<Object>) changeInfo.get("plugins");
      Map<String, Object> myplugin = (Map<String, Object>) plugins.get(0);

      assertThat(myplugin.get("my_meta_ref")).isEqualTo(before.metaRevId);
    }
  }
}
