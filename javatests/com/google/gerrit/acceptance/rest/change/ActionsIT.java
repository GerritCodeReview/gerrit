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
import static com.google.gerrit.truth.MapSubject.assertThatMap;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.ActionVisitor;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.server.change.RevisionJson;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class ActionsIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Inject private RevisionJson.Factory revisionJsonFactory;
  @Inject private ExtensionRegistry extensionRegistry;

  protected Map<String, ActionInfo> getActions(String id) throws Exception {
    return gApi.changes().id(id).revision(1).actions();
  }

  protected Map<String, ActionInfo> getChangeActions(String id) throws Exception {
    return gApi.changes().id(id).get().actions;
  }

  @Test
  public void changeActionOneMergedChangeHasOnlyNormalRevert() throws Exception {
    String changeId = createChangeWithTopic().getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    gApi.changes().id(changeId).current().submit();
    Map<String, ActionInfo> actions = getChangeActions(changeId);
    assertThat(actions).containsKey("revert");
    assertThat(actions).doesNotContainKey("revert_submission");
  }

  @Test
  public void changeActionTwoMergedChangesHaveReverts() throws Exception {
    String changeId1 = createChangeWithTopic().getChangeId();
    String changeId2 = createChangeWithTopic().getChangeId();
    gApi.changes().id(changeId1).current().review(ReviewInput.approve());
    gApi.changes().id(changeId2).current().review(ReviewInput.approve());
    gApi.changes().id(changeId2).current().submit();
    Map<String, ActionInfo> actions1 = getChangeActions(changeId1);
    assertThatMap(actions1).keys().containsAtLeast("revert", "revert_submission");
    Map<String, ActionInfo> actions2 = getChangeActions(changeId2);
    assertThatMap(actions2).keys().containsAtLeast("revert", "revert_submission");
  }

  @Test
  public void revisionActionsOneChangePerTopicUnapproved() throws Exception {
    String changeId = createChangeWithTopic().getChangeId();
    Map<String, ActionInfo> actions = getActions(changeId);
    assertThatMap(actions)
        .keys()
        .containsExactly("cherrypick", "rebase", "rebase:chain", "description");
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
    PushOneCommit.Result change1 = createChangeWithTopic();
    String changeId = change1.getChangeId();
    approve(changeId);
    PushOneCommit.Result change2 = createChangeWithTopic();
    int legacyId2 = change2.getChange().getId().get();
    String changeId2 = change2.getChangeId();
    Map<String, ActionInfo> actions = getActions(changeId);
    commonActionsAssertions(actions);
    if (isSubmitWholeTopicEnabled()) {
      ActionInfo info = actions.get("submit");
      assertThat(info.enabled).isNull();
      assertThat(info.label).isEqualTo("Submit whole topic");
      assertThat(info.method).isEqualTo("POST");
      assertThat(info.title)
          .startsWith(
              "Change "
                  + change1.getChange().getId()
                  + " must be submitted with change "
                  + legacyId2
                  + " but "
                  + legacyId2
                  + " is not ready: submit requirement 'Code-Review' is unsatisfied.");
    } else {
      noSubmitWholeTopicAssertions(actions, 1);

      assertThat(getActions(changeId2).get("submit")).isNull();
      approve(changeId2);
      noSubmitWholeTopicAssertions(getActions(changeId2), 2);
    }
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
    assertThat(origActions.keySet()).containsAtLeast("followup", "abandon");
    assertThat(origActions.get("abandon").label).isEqualTo("Abandon");

    try (Registration registration = extensionRegistry.newRegistration().add(new Visitor())) {
      Map<String, ActionInfo> newActions =
          gApi.changes().id(id).get(EnumSet.of(ListChangesOption.CHANGE_ACTIONS)).actions;

      Set<String> expectedNames = new TreeSet<>(origActions.keySet());
      expectedNames.remove("followup");
      assertThat(newActions.keySet()).isEqualTo(expectedNames);

      ActionInfo abandon = newActions.get("abandon");
      assertThat(abandon).isNotNull();
      assertThat(abandon.label).isEqualTo("Abandon All Hope");
    }
  }

  @Test
  public void currentRevisionActionVisitor() throws Exception {
    String id = createChange().getChangeId();
    amendChange(id);
    ChangeInfo origChange = gApi.changes().id(id).get(CHANGE_ACTIONS);
    Change.Id changeId = Change.id(origChange._number);

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
    assertThat(origActions.keySet()).containsAtLeast("cherrypick", "rebase");
    assertThat(origActions.get("rebase").label).isEqualTo("Rebase");

    try (Registration registration = extensionRegistry.newRegistration().add(new Visitor())) {
      // Test different codepaths within ActionJson...
      // ...via revision API.
      visitedCurrentRevisionActionsAssertions(
          origActions, gApi.changes().id(id).current().actions());

      // ...via change API with option.
      EnumSet<ListChangesOption> opts = EnumSet.of(CURRENT_ACTIONS, CURRENT_REVISION);
      ChangeInfo changeInfo = gApi.changes().id(id).get(opts);
      RevisionInfo revisionInfo = Iterables.getOnlyElement(changeInfo.revisions.values());
      visitedCurrentRevisionActionsAssertions(origActions, revisionInfo.actions);

      // ...via ChangeJson directly.
      ChangeData cd = changeDataFactory.create(project, changeId);
      revisionJsonFactory.create(opts).getRevisionInfo(cd, cd.patchSet(PatchSet.id(changeId, 1)));
    }
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

    try (Registration registration = extensionRegistry.newRegistration().add(new Visitor())) {
      // Unlike for the current revision, actions for old revisions are only available via the
      // revision API.
      Map<String, ActionInfo> newActions = gApi.changes().id(id).revision(1).actions();
      assertThat(newActions).isNotNull();
      assertThat(newActions.keySet()).isEqualTo(origActions.keySet());

      ActionInfo description = newActions.get("description");
      assertThat(description).isNotNull();
      assertThat(description.label).isEqualTo("Describify");
    }
  }

  private void commonActionsAssertions(Map<String, ActionInfo> actions) {
    assertThatMap(actions)
        .keys()
        .containsExactly("cherrypick", "submit", "description", "rebase", "rebase:chain");
  }

  private PushOneCommit.Result createChangeWithTopic() throws Exception {
    return createChangeWithTopic(testRepo, "topic", "message", "a.txt", "content\n");
  }
}
