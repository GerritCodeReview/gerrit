// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.events;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.entities.Change.Status.NEW;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.MapSubject;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class EventJsonTest {
  private static final String BRANCH = "mybranch";
  private static final String CHANGE_ID = "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
  private static final int CHANGE_NUM = 1000;
  private static final double CHANGE_NUM_DOUBLE = CHANGE_NUM;
  private static final String COMMIT_MESSAGE = "This is a test commit message";
  private static final String PROJECT = "myproject";
  private static final String REF = "refs/heads/" + BRANCH;
  private static final double TS1 = 1.2543444E9;
  private static final double TS2 = 1.254344401E9;
  private static final String URL = "http://somewhere.com";

  private final Gson gson = new EventGsonProvider().get();

  static class CustomEvent extends Event {
    static String TYPE = "custom-type";

    public String customField;

    protected CustomEvent() {
      super(TYPE);
    }
  }

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @Test
  public void customEvent() {
    CustomEvent event = new CustomEvent();
    event.customField = "customValue";
    String json = gson.toJson(event);
    CustomEvent resullt = gson.fromJson(json, CustomEvent.class);
    assertThat(resullt.type).isEqualTo(CustomEvent.TYPE);
    assertThat(resullt.customField).isEqualTo(event.customField);
  }

  @Test
  public void customEventSimulateClassloaderIssue() {
    EventTypes.register(CustomEvent.TYPE, CustomEvent.class);
    CustomEvent event = new CustomEvent();
    event.customField = "customValue";
    // Need to serialise using the Event interface instead of json.getClass()
    // for simulating the serialisation of an object owned by another class loader
    String json = gson.toJson(event, Event.class);
    CustomEvent resullt = gson.fromJson(json, CustomEvent.class);
    assertThat(resullt.type).isEqualTo(CustomEvent.TYPE);
    assertThat(resullt.customField).isEqualTo(event.customField);
  }

  @Test
  public void refUpdatedEvent() {
    RefUpdatedEvent event = new RefUpdatedEvent();

    RefUpdateAttribute refUpdatedAttribute = new RefUpdateAttribute();
    refUpdatedAttribute.refName = REF;
    event.refUpdate = createSupplier(refUpdatedAttribute);
    event.submitter = newAccount("submitter");

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "submitter",
                    ImmutableMap.builder()
                        .put("name", event.submitter.get().name)
                        .put("email", event.submitter.get().email)
                        .put("username", event.submitter.get().username)
                        .build())
                .put("refUpdate", ImmutableMap.of("refName", REF))
                .put("type", "ref-updated")
                .put("eventCreatedOn", TS1)
                .build());
  }

  @Test
  public void patchSetCreatedEvent() {
    Change change = newChange();
    PatchSetCreatedEvent event = new PatchSetCreatedEvent(change);
    event.change = asChangeAttribute(change);
    event.uploader = newAccount("uploader");

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "uploader",
                    ImmutableMap.builder()
                        .put("name", event.uploader.get().name)
                        .put("email", event.uploader.get().email)
                        .put("username", event.uploader.get().username)
                        .build())
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "patchset-created")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void assigneeChangedEvent() {
    Change change = newChange();
    AssigneeChangedEvent event = new AssigneeChangedEvent(change);
    event.change = asChangeAttribute(change);
    event.changer = newAccount("changer");
    event.oldAssignee = newAccount("oldAssignee");

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "changer",
                    ImmutableMap.builder()
                        .put("name", event.changer.get().name)
                        .put("email", event.changer.get().email)
                        .put("username", event.changer.get().username)
                        .build())
                .put(
                    "oldAssignee",
                    ImmutableMap.builder()
                        .put("name", event.oldAssignee.get().name)
                        .put("email", event.oldAssignee.get().email)
                        .put("username", event.oldAssignee.get().username)
                        .build())
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "assignee-changed")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void changeDeletedEvent() {
    Change change = newChange();
    ChangeDeletedEvent event = new ChangeDeletedEvent(change);
    event.change = asChangeAttribute(change);
    event.deleter = newAccount("deleter");

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "deleter",
                    ImmutableMap.builder()
                        .put("name", event.deleter.get().name)
                        .put("email", event.deleter.get().email)
                        .put("username", event.deleter.get().username)
                        .build())
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "change-deleted")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void hashtagsChangedEvent() {
    Change change = newChange();
    HashtagsChangedEvent event = new HashtagsChangedEvent(change);
    event.change = asChangeAttribute(change);
    event.editor = newAccount("editor");
    event.added = new String[] {"added"};
    event.removed = new String[] {"removed"};
    event.hashtags = new String[] {"hashtags"};

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "editor",
                    ImmutableMap.builder()
                        .put("name", event.editor.get().name)
                        .put("email", event.editor.get().email)
                        .put("username", event.editor.get().username)
                        .build())
                .put("added", list("added"))
                .put("removed", list("removed"))
                .put("hashtags", list("hashtags"))
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "hashtags-changed")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void changeAbandonedEvent() {
    Change change = newChange();
    ChangeAbandonedEvent event = new ChangeAbandonedEvent(change);
    event.change = asChangeAttribute(change);
    event.abandoner = newAccount("abandoner");
    event.reason = "some reason";

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "abandoner",
                    ImmutableMap.builder()
                        .put("name", event.abandoner.get().name)
                        .put("email", event.abandoner.get().email)
                        .put("username", event.abandoner.get().username)
                        .build())
                .put("reason", "some reason")
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "change-abandoned")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void changeMergedEvent() {
    Change change = newChange();
    ChangeMergedEvent event = new ChangeMergedEvent(change);
    event.change = asChangeAttribute(change);

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "change-merged")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void changeRestoredEvent() {
    Change change = newChange();
    ChangeRestoredEvent event = new ChangeRestoredEvent(change);
    event.change = asChangeAttribute(change);

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "change-restored")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void commentAddedEvent() {
    Change change = newChange();
    CommentAddedEvent event = new CommentAddedEvent(change);
    event.change = asChangeAttribute(change);

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "comment-added")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void privateStateChangedEvent() {
    Change change = newChange();
    PrivateStateChangedEvent event = new PrivateStateChangedEvent(change);
    event.change = asChangeAttribute(change);

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "private-state-changed")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void reviewerAddedEvent() {
    Change change = newChange();
    ReviewerAddedEvent event = new ReviewerAddedEvent(change);
    event.change = asChangeAttribute(change);

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "reviewer-added")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void reviewerDeletedEvent() {
    Change change = newChange();
    ReviewerDeletedEvent event = new ReviewerDeletedEvent(change);
    event.change = asChangeAttribute(change);

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "reviewer-deleted")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void voteDeletedEvent() {
    Change change = newChange();
    VoteDeletedEvent event = new VoteDeletedEvent(change);
    event.change = asChangeAttribute(change);

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "vote-deleted")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void workInProgressStateChangedEvent() {
    Change change = newChange();
    WorkInProgressStateChangedEvent event = new WorkInProgressStateChangedEvent(change);
    event.change = asChangeAttribute(change);

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "wip-state-changed")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void topicChangedEvent() {
    Change change = newChange();
    TopicChangedEvent event = new TopicChangedEvent(change);
    event.change = asChangeAttribute(change);

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", PROJECT)
                        .put("branch", BRANCH)
                        .put("id", CHANGE_ID)
                        .put("number", CHANGE_NUM_DOUBLE)
                        .put("url", URL)
                        .put("commitMessage", COMMIT_MESSAGE)
                        .put("createdOn", TS1)
                        .put("status", NEW.name())
                        .build())
                .put("project", PROJECT)
                .put("refName", REF)
                .put("changeKey", map("id", CHANGE_ID))
                .put("type", "topic-changed")
                .put("eventCreatedOn", TS2)
                .build());
  }

  @Test
  public void projectCreatedEvent() {
    ProjectCreatedEvent event = new ProjectCreatedEvent();
    event.projectName = PROJECT;
    event.headName = REF;

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put("projectName", PROJECT)
                .put("headName", REF)
                .put("type", "project-created")
                .put("eventCreatedOn", TS1)
                .build());
  }

  @Test
  public void projectHeadUpdatedEvent() {
    ProjectHeadUpdatedEvent event = new ProjectHeadUpdatedEvent();
    event.projectName = PROJECT;
    event.oldHead = "refs/heads/master";
    event.newHead = REF;

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put("projectName", PROJECT)
                .put("oldHead", "refs/heads/master")
                .put("newHead", REF)
                .put("type", "project-head-updated")
                .put("eventCreatedOn", TS1)
                .build());
  }

  private Supplier<AccountAttribute> newAccount(String name) {
    AccountAttribute account = new AccountAttribute();
    account.name = name;
    account.email = name + "@somewhere.com";
    account.username = name;
    return Suppliers.ofInstance(account);
  }

  private Change newChange() {
    return new Change(
        Change.key(CHANGE_ID),
        Change.id(CHANGE_NUM),
        Account.id(9999),
        BranchNameKey.create(Project.nameKey(PROJECT), BRANCH),
        TimeUtil.now());
  }

  private <T> Supplier<T> createSupplier(T value) {
    return Suppliers.memoize(() -> value);
  }

  private Supplier<ChangeAttribute> asChangeAttribute(Change change) {
    ChangeAttribute a = new ChangeAttribute();
    a.project = change.getProject().get();
    a.branch = change.getDest().shortName();
    a.topic = change.getTopic();
    a.id = change.getKey().get();
    a.number = change.getId().get();
    a.subject = change.getSubject();
    a.commitMessage = COMMIT_MESSAGE;
    a.url = URL;
    a.status = change.getStatus();
    a.createdOn = change.getCreatedOn().getEpochSecond();
    a.wip = change.isWorkInProgress() ? true : null;
    a.isPrivate = change.isPrivate() ? true : null;
    return Suppliers.ofInstance(a);
  }

  private MapSubject assertThatJsonMap(Object src) {
    // Parse JSON into a raw Java map:
    //  * Doesn't depend on field iteration order.
    //  * Avoids excessively long string literals in asserts.
    String json = gson.toJson(src);
    Map<Object, Object> map =
        gson.fromJson(json, new TypeToken<Map<Object, Object>>() {}.getType());
    return assertThat(map);
  }

  private static ImmutableMap<Object, Object> map(Object k, Object v) {
    return ImmutableMap.of(k, v);
  }

  private static ImmutableList<Object> list(Object... es) {
    return ImmutableList.copyOf(es);
  }
}
