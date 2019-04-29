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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.MapSubject;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class EventJsonTest extends GerritBaseTests {
  // Must match StreamEvents#gson. (In master, the definition is refactored to be hared.)
  private final Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(Supplier.class, new SupplierSerializer())
          .registerTypeAdapter(Project.NameKey.class, new ProjectNameKeySerializer())
          .create();

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @Test
  public void refUpdatedEvent() {
    RefUpdatedEvent event = new RefUpdatedEvent();

    RefUpdateAttribute refUpdatedAttribute = new RefUpdateAttribute();
    refUpdatedAttribute.refName = "refs/heads/master";
    event.refUpdate = createSupplier(refUpdatedAttribute);

    AccountAttribute accountAttribute = new AccountAttribute();
    accountAttribute.email = "some.user@domain.com";
    event.submitter = createSupplier(accountAttribute);

    assertThatJsonMap(event)
        .isEqualTo(
            ImmutableMap.builder()
                .put("submitter", ImmutableMap.of("email", "some.user@domain.com"))
                .put("refUpdate", ImmutableMap.of("refName", "refs/heads/master"))
                .put("type", "ref-updated")
                .put("eventCreatedOn", 1.2543444E9)
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
                        .put("name", "uploader")
                        .put("email", "uploader@somewhere.com")
                        .put("username", "uploader")
                        .build())
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "patchset-created")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("name", "changer")
                        .put("email", "changer@somewhere.com")
                        .put("username", "changer")
                        .build())
                .put(
                    "oldAssignee",
                    ImmutableMap.builder()
                        .put("name", "oldAssignee")
                        .put("email", "oldAssignee@somewhere.com")
                        .put("username", "oldAssignee")
                        .build())
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "assignee-changed")
                .put("eventCreatedOn", 1.254344401E9)
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
                    ImmutableMap.of(
                        "name", "deleter", "email", "deleter@somewhere.com", "username", "deleter"))
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "change-deleted")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("name", "editor")
                        .put("email", "editor@somewhere.com")
                        .put("username", "editor")
                        .build())
                .put("added", list("added"))
                .put("removed", list("removed"))
                .put("hashtags", list("hashtags"))
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "hashtags-changed")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("name", "abandoner")
                        .put("email", "abandoner@somewhere.com")
                        .put("username", "abandoner")
                        .build())
                .put("reason", "some reason")
                .put(
                    "change",
                    ImmutableMap.builder()
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "change-abandoned")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "change-merged")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "change-restored")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "comment-added")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "private-state-changed")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "reviewer-added")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "reviewer-deleted")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "vote-deleted")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "wip-state-changed")
                .put("eventCreatedOn", 1.254344401E9)
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
                        .put("project", "myproject")
                        .put("branch", "mybranch")
                        .put("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef")
                        .put("number", 1000.0)
                        .put("url", "http://somewhere.com")
                        .put("commitMessage", "This is a test commit message")
                        .put("createdOn", 1.2543444E9)
                        .put("status", "NEW")
                        .build())
                .put("project", "myproject")
                .put("refName", "refs/heads/mybranch")
                .put("changeKey", map("id", "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
                .put("type", "topic-changed")
                .put("eventCreatedOn", 1.254344401E9)
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
        new Change.Key("Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
        new Change.Id(1000),
        new Account.Id(1000),
        new Branch.NameKey(new Project.NameKey("myproject"), "mybranch"),
        TimeUtil.nowTs());
  }

  private <T> Supplier<T> createSupplier(T value) {
    return Suppliers.memoize(() -> value);
  }

  private Supplier<ChangeAttribute> asChangeAttribute(Change change) {
    ChangeAttribute a = new ChangeAttribute();
    a.project = change.getProject().get();
    a.branch = change.getDest().getShortName();
    a.topic = change.getTopic();
    a.id = change.getKey().get();
    a.number = change.getId().get();
    a.subject = change.getSubject();
    a.commitMessage = "This is a test commit message";
    a.url = "http://somewhere.com";
    a.status = change.getStatus();
    a.createdOn = change.getCreatedOn().getTime() / 1000L;
    a.wip = change.isWorkInProgress() ? true : null;
    a.isPrivate = change.isPrivate() ? true : null;
    return Suppliers.ofInstance(a);
  }

  private MapSubject assertThatJsonMap(Object src) {
    // Parse JSON into a raw Java map:
    //  * Doesn't depend on field iteration order.
    //  * Avoids excessively long string literals in asserts.
    Map<Object, Object> map =
        gson.fromJson(gson.toJson(src), new TypeToken<Map<Object, Object>>() {}.getType());
    return assertThat(map);
  }

  private static ImmutableMap<Object, Object> map(Object k, Object v) {
    return ImmutableMap.of(k, v);
  }

  private static ImmutableList<Object> list(Object... es) {
    return ImmutableList.builder().add(es).build();
  }
}
