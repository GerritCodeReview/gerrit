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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.server.change.GetRevisionActions;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Inject;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class ActionsIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Inject private GetRevisionActions getRevisionActions;

  @Test
  public void revisionActionsOneChangePerTopicUnapproved() throws Exception {
    String changeId = createChangeWithTopic().getChangeId();
    Map<String, ActionInfo> actions = getActions(changeId);
    assertThat(actions).containsKey("cherrypick");
    assertThat(actions).containsKey("rebase");
    assertThat(actions).hasSize(2);
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
      assertThat(info.title)
          .isEqualTo("This change depends on other " + "changes which are not ready");
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
    String etag1 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

    approve(parent);
    String etag2 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

    String changeWithSameTopic = createChangeWithTopic().getChangeId();
    String etag3 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

    approve(changeWithSameTopic);
    String etag4 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

    if (isSubmitWholeTopicEnabled()) {
      assertThat(ImmutableList.of(etag1, etag2, etag3, etag4)).containsNoDuplicates();
    } else {
      assertThat(etag2).isNotEqualTo(etag1);
      assertThat(etag3).isEqualTo(etag2);
      assertThat(etag4).isEqualTo(etag2);
    }
  }

  @Test
  public void revisionActionsETagWithHiddenDraftInTopic() throws Exception {
    String change = createChangeWithTopic().getChangeId();
    approve(change);

    setApiUser(user);
    String etag1 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

    setApiUser(admin);
    String draft = createDraftWithTopic().getChangeId();
    approve(draft);

    setApiUser(user);
    String etag2 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

    if (isSubmitWholeTopicEnabled()) {
      assertThat(etag2).isNotEqualTo(etag1);
    } else {
      assertThat(etag2).isEqualTo(etag1);
    }
  }

  @Test
  public void revisionActionsAnonymousETag() throws Exception {
    String parent = createChange().getChangeId();
    String change = createChangeWithTopic().getChangeId();
    approve(change);

    setApiUserAnonymous();
    String etag1 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

    setApiUser(admin);
    approve(parent);

    setApiUserAnonymous();
    String etag2 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

    setApiUser(admin);
    String changeWithSameTopic = createChangeWithTopic().getChangeId();

    setApiUserAnonymous();
    String etag3 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

    setApiUser(admin);
    approve(changeWithSameTopic);

    setApiUserAnonymous();
    String etag4 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

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
    String etag1 = getRevisionActions.getETag(parseCurrentRevisionResource(change));

    setApiUser(admin);
    approve(parent);

    setApiUserAnonymous();
    String etag2 = getRevisionActions.getETag(parseCurrentRevisionResource(change));
    assertThat(etag2).isEqualTo(etag1);
  }

  @Test
  public void revisionActionsTwoChangesInTopic_conflicting() throws Exception {
    String changeId = createChangeWithTopic().getChangeId();
    approve(changeId);

    // create another change with the same topic
    String changeId2 =
        createChangeWithTopic(testRepo, "foo2", "touching b", "b.txt", "real content")
            .getChangeId();
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
      assertThat(info.title).isEqualTo("Problems with change(s): 2");
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
                  "Submit patch set 1 and ancestors (%d changes " + "altogether) into master",
                  nrChanges));
    }
  }

  private void commonActionsAssertions(Map<String, ActionInfo> actions) {
    assertThat(actions).hasSize(3);
    assertThat(actions).containsKey("cherrypick");
    assertThat(actions).containsKey("submit");
    assertThat(actions).containsKey("rebase");
  }

  private PushOneCommit.Result createCommitAndPush(
      TestRepository<InMemoryRepository> repo,
      String ref,
      String commitMsg,
      String fileName,
      String content)
      throws Exception {
    return pushFactory.create(db, admin.getIdent(), repo, commitMsg, fileName, content).to(ref);
  }

  private PushOneCommit.Result createChangeWithTopic(
      TestRepository<InMemoryRepository> repo,
      String topic,
      String commitMsg,
      String fileName,
      String content)
      throws Exception {
    assertThat(topic).isNotEmpty();
    return createCommitAndPush(
        repo, "refs/for/master/" + name(topic), commitMsg, fileName, content);
  }

  private PushOneCommit.Result createChangeWithTopic() throws Exception {
    return createChangeWithTopic(testRepo, "foo2", "a message", "a.txt", "content\n");
  }

  private PushOneCommit.Result createDraftWithTopic() throws Exception {
    return createCommitAndPush(
        testRepo, "refs/drafts/master/" + name("foo2"), "a message", "a.txt", "content\n");
  }
}
