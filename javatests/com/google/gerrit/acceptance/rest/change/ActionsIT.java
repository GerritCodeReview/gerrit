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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.client.ListChangesOption.CHANGE_ACTIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_ACTIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.api.changes.ActionVisitor;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ActionsIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Inject private ChangeJson.Factory changeJsonFactory;

  @Inject private DynamicSet<ActionVisitor> actionVisitors;

  private RegistrationHandle visitorHandle;

  @Before
  public void setUp() {
    visitorHandle = null;
  }

  @After
  public void tearDown() {
    if (visitorHandle != null) {
      visitorHandle.remove();
    }
  }

  protected Map<String, ActionInfo> getActions(String id) throws Exception {
    return gApi.changes().id(id).revision(1).actions();
  }

  protected String getETag(String id) throws Exception {
    return gApi.changes().id(id).current().etag();
  }

  @Test
  public void revisionActionsOneChangePerTopicUnapproved() throws Exception {
    String changeId = createChangeWithTopic().getChangeId();
    Map<String, ActionInfo> actions = getActions(changeId);
    assertThat(actions).hasSize(3);
    assertThat(actions).containsKey("cherrypick");
    assertThat(actions).containsKey("rebase");
    assertThat(actions).containsKey("description");
  }

  @Test
  public void revisionActionsOneChangePerTopic() throws Exception {
    String changeId = createChangeWithTopic().getChangeId();
    approve(changeId);
    Map<String, ActionInfo> actions = getActions(changeId);
    commonActionsAssertions(actions);
    // We want to treat a single change in a topic not as a whole topic,
    // so regardless of how submitWholeTopic is configured:
    noSubmitWholeTopicAssertions(actions, 1);
  }

  @Test
  public void revisionActionsTwoChangesInTopic() throws Exception {
    String changeId = createChangeWithTopic().getChangeId();
    approve(changeId);
    String changeId2 = createChangeWithTopic().getChangeId();
    Map<String, ActionInfo> actions = getActions(changeId);
    commonActionsAssertions(actions);
    if (isSubmitWholeTopicEnabled()) {
      ActionInfo info = actions.get("submit");
      assertThat(info.enabled).isNull();
      assertThat(info.label).isEqualTo("Submit whole topic");
      assertThat(info.method).isEqualTo("POST");
      assertThat(info.title).isEqualTo("This change depends on other changes which are not ready");
    } else {
      noSubmitWholeTopicAssertions(actions, 1);

      assertThat(getActions(changeId2).get("submit")).isNull();
      approve(changeId2);
      noSubmitWholeTopicAssertions(getActions(changeId2), 2);
    }
  }

  @Test
  public void revisionActionsETag() throws Exception {
    String parent = createChange().getChangeId();
    String change = createChangeWithTopic().getChangeId();
    approve(change);
    String etag1 = getETag(change);

    approve(parent);
    String etag2 = getETag(change);

    String changeWithSameTopic = createChangeWithTopic().getChangeId();
    String etag3 = getETag(change);

    approve(changeWithSameTopic);
    String etag4 = getETag(change);

    if (isSubmitWholeTopicEnabled()) {
      assertThat(ImmutableList.of(etag1, etag2, etag3, etag4)).containsNoDuplicates();
    } else {
      assertThat(etag2).isNotEqualTo(etag1);
      assertThat(etag3).isEqualTo(etag2);
      assertThat(etag4).isEqualTo(etag2);
    }
  }

  @Test
  public void revisionActionsAnonymousETag() throws Exception {
    String parent = createChange().getChangeId();
    String change = createChangeWithTopic().getChangeId();
    approve(change);

    setApiUserAnonymous();
    String etag1 = getETag(change);

    setApiUser(admin);
    approve(parent);

    setApiUserAnonymous();
    String etag2 = getETag(change);

    setApiUser(admin);
    String changeWithSameTopic = createChangeWithTopic().getChangeId();

    setApiUserAnonymous();
    String etag3 = getETag(change);

    setApiUser(admin);
    approve(changeWithSameTopic);

    setApiUserAnonymous();
    String etag4 = getETag(change);

    if (isSubmitWholeTopicEnabled()) {
      assertThat(ImmutableList.of(etag1, etag2, etag3, etag4)).containsNoDuplicates();
    } else {
      assertThat(etag2).isNotEqualTo(etag1);
      assertThat(etag3).isEqualTo(etag2);
      assertThat(etag4).isEqualTo(etag2);
    }
  }

  @Test
  @TestProjectInput(submitType = SubmitType.CHERRY_PICK)
  public void revisionActionsAnonymousETagCherryPickStrategy() throws Exception {
    String parent = createChange().getChangeId();
    String change = createChange().getChangeId();
    approve(change);

    setApiUserAnonymous();
    String etag1 = getETag(change);

    setApiUser(admin);
    approve(parent);

    setApiUserAnonymous();
    String etag2 = getETag(change);
    assertThat(etag2).isEqualTo(etag1);
  }

  @Test
  public void revisionActionsTwoChangesInTopic_conflicting() throws Exception {
    String changeId = createChangeWithTopic().getChangeId();
    approve(changeId);

    // create another change with the same topic
    String changeId2 =
        createChangeWithTopic(testRepo, "topic", "touching b", "b.txt", "real content")
            .getChangeId();
    int changeNum2 = gApi.changes().id(changeId2).info()._number;
    approve(changeId2);

    // collide with the other change in the same topic
    testRepo.reset("HEAD~2");
    String collidingChange =
        createChangeWithTopic(
                testRepo, "off_topic", "rewriting file b", "b.txt", "garbage\ngarbage\ngarbage")
            .getChangeId();
    gApi.changes().id(collidingChange).current().review(ReviewInput.approve());
    gApi.changes().id(collidingChange).current().submit();

    Map<String, ActionInfo> actions = getActions(changeId);
    commonActionsAssertions(actions);
    if (isSubmitWholeTopicEnabled()) {
      ActionInfo info = actions.get("submit");
      assertThat(info.enabled).isNull();
      assertThat(info.label).isEqualTo("Submit whole topic");
      assertThat(info.method).isEqualTo("POST");
      assertThat(info.title).isEqualTo("Problems with change(s): " + changeNum2);
    } else {
      noSubmitWholeTopicAssertions(actions, 1);
    }
  }

  @Test
  public void revisionActionsTwoChangesInTopicWithAncestorReady() throws Exception {
    String changeId = createChange().getChangeId();
    approve(changeId);
    approve(changeId);
    String changeId1 = createChangeWithTopic().getChangeId();
    approve(changeId1);
    // create another change with the same topic
    String changeId2 = createChangeWithTopic().getChangeId();
    approve(changeId2);
    Map<String, ActionInfo> actions = getActions(changeId1);
    commonActionsAssertions(actions);
    if (isSubmitWholeTopicEnabled()) {
      ActionInfo info = actions.get("submit");
      assertThat(info.enabled).isTrue();
      assertThat(info.label).isEqualTo("Submit whole topic");
      assertThat(info.method).isEqualTo("POST");
      assertThat(info.title)
          .isEqualTo(
              "Submit all 2 changes of the same "
                  + "topic (3 changes including ancestors "
                  + "and other changes related by topic)");
    } else {
      noSubmitWholeTopicAssertions(actions, 2);
    }
  }

  @Test
  public void revisionActionsReadyWithAncestors() throws Exception {
    String changeId = createChange().getChangeId();
    approve(changeId);
    approve(changeId);
    String changeId1 = createChange().getChangeId();
    approve(changeId1);
    String changeId2 = createChangeWithTopic().getChangeId();
    approve(changeId2);
    Map<String, ActionInfo> actions = getActions(changeId2);
    commonActionsAssertions(actions);
    // The topic contains only one change, so standard text applies
    noSubmitWholeTopicAssertions(actions, 3);
  }

  private void noSubmitWholeTopicAssertions(Map<String, ActionInfo> actions, int nrChanges) {
    ActionInfo info = actions.get("submit");
    assertThat(info.enabled).isTrue();
    if (nrChanges == 1) {
      assertThat(info.label).isEqualTo("Submit");
    } else {
      assertThat(info.label).isEqualTo("Submit including parents");
    }
    assertThat(info.method).isEqualTo("POST");
    if (nrChanges == 1) {
      assertThat(info.title).isEqualTo("Submit patch set 1 into master");
    } else {
      assertThat(info.title)
          .isEqualTo(
              String.format(
                  "Submit patch set 1 and ancestors (%d changes altogether) into master",
                  nrChanges));
    }
  }

  @Test
  public void changeActionVisitor() throws Exception {
    String id = createChange().getChangeId();
    ChangeInfo origChange = gApi.changes().id(id).get(CHANGE_ACTIONS);

    class Visitor implements ActionVisitor {
      @Override
      public boolean visit(String name, ActionInfo actionInfo, ChangeInfo changeInfo) {
        assertThat(changeInfo).isNotNull();
        assertThat(changeInfo._number).isEqualTo(origChange._number);
        if (name.equals("followup")) {
          return false;
        }
        if (name.equals("abandon")) {
          actionInfo.label = "Abandon All Hope";
        }
        return true;
      }

      @Override
      public boolean visit(
          String name, ActionInfo actionInfo, ChangeInfo changeInfo, RevisionInfo revisionInfo) {
        throw new UnsupportedOperationException();
      }
    }

    Map<String, ActionInfo> origActions = origChange.actions;
    assertThat(origActions.keySet()).containsAllOf("followup", "abandon");
    assertThat(origActions.get("abandon").label).isEqualTo("Abandon");

    Visitor v = new Visitor();
    visitorHandle = actionVisitors.add(v);

    Map<String, ActionInfo> newActions =
        gApi.changes().id(id).get(EnumSet.of(ListChangesOption.CHANGE_ACTIONS)).actions;

    Set<String> expectedNames = new TreeSet<>(origActions.keySet());
    expectedNames.remove("followup");
    assertThat(newActions.keySet()).isEqualTo(expectedNames);

    ActionInfo abandon = newActions.get("abandon");
    assertThat(abandon).isNotNull();
    assertThat(abandon.label).isEqualTo("Abandon All Hope");
  }

  @Test
  public void currentRevisionActionVisitor() throws Exception {
    String id = createChange().getChangeId();
    amendChange(id);
    ChangeInfo origChange = gApi.changes().id(id).get(CHANGE_ACTIONS);
    Change.Id changeId = new Change.Id(origChange._number);

    class Visitor implements ActionVisitor {
      @Override
      public boolean visit(String name, ActionInfo actionInfo, ChangeInfo changeInfo) {
        return true; // Do nothing; implicitly called for CURRENT_ACTIONS.
      }

      @Override
      public boolean visit(
          String name, ActionInfo actionInfo, ChangeInfo changeInfo, RevisionInfo revisionInfo) {
        assertThat(changeInfo).isNotNull();
        assertThat(changeInfo._number).isEqualTo(origChange._number);
        assertThat(revisionInfo).isNotNull();
        assertThat(revisionInfo._number).isEqualTo(2);
        if (name.equals("cherrypick")) {
          return false;
        }
        if (name.equals("rebase")) {
          actionInfo.label = "All Your Base";
        }
        return true;
      }
    }

    Map<String, ActionInfo> origActions = gApi.changes().id(id).current().actions();
    assertThat(origActions.keySet()).containsAllOf("cherrypick", "rebase");
    assertThat(origActions.get("rebase").label).isEqualTo("Rebase");

    Visitor v = new Visitor();
    visitorHandle = actionVisitors.add(v);

    // Test different codepaths within ActionJson...
    // ...via revision API.
    visitedCurrentRevisionActionsAssertions(origActions, gApi.changes().id(id).current().actions());

    // ...via change API with option.
    EnumSet<ListChangesOption> opts = EnumSet.of(CURRENT_ACTIONS, CURRENT_REVISION);
    ChangeInfo changeInfo = gApi.changes().id(id).get(opts);
    RevisionInfo revisionInfo = Iterables.getOnlyElement(changeInfo.revisions.values());
    visitedCurrentRevisionActionsAssertions(origActions, revisionInfo.actions);

    // ...via ChangeJson directly.
    ChangeData cd = changeDataFactory.create(db, project, changeId);
    revisionInfo =
        changeJsonFactory
            .create(opts)
            .getRevisionInfo(cd, cd.patchSet(new PatchSet.Id(changeId, 1)));
  }

  private void visitedCurrentRevisionActionsAssertions(
      Map<String, ActionInfo> origActions, Map<String, ActionInfo> newActions) {
    assertThat(newActions).isNotNull();
    Set<String> expectedNames = new TreeSet<>(origActions.keySet());
    expectedNames.remove("cherrypick");
    assertThat(newActions.keySet()).isEqualTo(expectedNames);

    ActionInfo rebase = newActions.get("rebase");
    assertThat(rebase).isNotNull();
    assertThat(rebase.label).isEqualTo("All Your Base");
  }

  @Test
  public void oldRevisionActionVisitor() throws Exception {
    String id = createChange().getChangeId();
    amendChange(id);
    ChangeInfo origChange = gApi.changes().id(id).get(CHANGE_ACTIONS);

    class Visitor implements ActionVisitor {
      @Override
      public boolean visit(String name, ActionInfo actionInfo, ChangeInfo changeInfo) {
        return true; // Do nothing; implicitly called for CURRENT_ACTIONS.
      }

      @Override
      public boolean visit(
          String name, ActionInfo actionInfo, ChangeInfo changeInfo, RevisionInfo revisionInfo) {
        assertThat(changeInfo).isNotNull();
        assertThat(changeInfo._number).isEqualTo(origChange._number);
        assertThat(revisionInfo).isNotNull();
        assertThat(revisionInfo._number).isEqualTo(1);
        if (name.equals("description")) {
          actionInfo.label = "Describify";
        }
        return true;
      }
    }

    Map<String, ActionInfo> origActions = gApi.changes().id(id).revision(1).actions();
    assertThat(origActions.keySet()).containsExactly("description");
    assertThat(origActions.get("description").label).isEqualTo("Edit Description");

    Visitor v = new Visitor();
    visitorHandle = actionVisitors.add(v);

    // Unlike for the current revision, actions for old revisions are only available via the
    // revision API.
    Map<String, ActionInfo> newActions = gApi.changes().id(id).revision(1).actions();
    assertThat(newActions).isNotNull();
    assertThat(newActions.keySet()).isEqualTo(origActions.keySet());

    ActionInfo description = newActions.get("description");
    assertThat(description).isNotNull();
    assertThat(description.label).isEqualTo("Describify");
  }

  private void commonActionsAssertions(Map<String, ActionInfo> actions) {
    assertThat(actions).hasSize(4);
    assertThat(actions).containsKey("cherrypick");
    assertThat(actions).containsKey("submit");
    assertThat(actions).containsKey("description");
    assertThat(actions).containsKey("rebase");
  }
}
