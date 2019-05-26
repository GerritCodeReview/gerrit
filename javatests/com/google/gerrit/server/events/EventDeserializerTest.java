// Copyright (C) 2016 The Android Open Source Project
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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gson.Gson;
import java.sql.Timestamp;
import org.junit.Test;

public class EventDeserializerTest {
  private final Gson gson = new EventGsonProvider().get();

  @Test
  public void refUpdatedEvent() {
    RefUpdatedEvent orig = new RefUpdatedEvent();
    RefUpdateAttribute refUpdatedAttribute = new RefUpdateAttribute();
    refUpdatedAttribute.refName = "refs/heads/master";
    orig.refUpdate = createSupplier(refUpdatedAttribute);

    AccountAttribute accountAttribute = new AccountAttribute();
    accountAttribute.email = "some.user@domain.com";
    orig.submitter = createSupplier(accountAttribute);

    RefUpdatedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertThat(e.refUpdate).isInstanceOf(Supplier.class);
    assertThat(e.refUpdate.get().refName).isEqualTo(refUpdatedAttribute.refName);
    assertThat(e.submitter).isInstanceOf(Supplier.class);
    assertThat(e.submitter.get().email).isEqualTo(accountAttribute.email);
  }

  @Test
  public void patchSetCreatedEvent() {
    Change change = newChange();
    PatchSetCreatedEvent orig = new PatchSetCreatedEvent(change);
    orig.change = asChangeAttribute(change);
    orig.uploader = newAccount("uploader");

    PatchSetCreatedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
    assertSameAccount(e.uploader, orig.uploader);
  }

  @Test
  public void assigneeChangedEvent() {
    Change change = newChange();
    AssigneeChangedEvent orig = new AssigneeChangedEvent(change);
    orig.change = asChangeAttribute(change);
    orig.changer = newAccount("changer");
    orig.oldAssignee = newAccount("oldAssignee");

    AssigneeChangedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
    assertSameAccount(e.changer, orig.changer);
    assertSameAccount(e.oldAssignee, orig.oldAssignee);
  }

  @Test
  public void changeDeletedEvent() {
    Change change = newChange();
    ChangeDeletedEvent orig = new ChangeDeletedEvent(change);
    orig.change = asChangeAttribute(change);
    orig.deleter = newAccount("deleter");

    ChangeDeletedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
    assertSameAccount(e.deleter, orig.deleter);
  }

  @Test
  public void hashtagsChangedEvent() {
    Change change = newChange();
    HashtagsChangedEvent orig = new HashtagsChangedEvent(change);
    orig.change = asChangeAttribute(change);
    orig.editor = newAccount("editor");
    orig.added = new String[] {"added"};
    orig.removed = new String[] {"removed"};
    orig.hashtags = new String[] {"hashtags"};

    HashtagsChangedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
    assertSameAccount(e.editor, orig.editor);
    assertThat(e.added).isEqualTo(orig.added);
    assertThat(e.removed).isEqualTo(orig.removed);
    assertThat(e.hashtags).isEqualTo(orig.hashtags);
  }

  @Test
  public void changeAbandonedEvent() {
    Change change = newChange();
    ChangeAbandonedEvent orig = new ChangeAbandonedEvent(change);
    orig.change = asChangeAttribute(change);
    orig.abandoner = newAccount("abandoner");
    orig.reason = "some reason";

    ChangeAbandonedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
    assertSameAccount(e.abandoner, orig.abandoner);
    assertThat(e.reason).isEqualTo(orig.reason);
  }

  @Test
  public void changeMergedEvent() {
    Change change = newChange();
    ChangeMergedEvent orig = new ChangeMergedEvent(change);
    orig.change = asChangeAttribute(change);

    ChangeMergedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void changeRestoredEvent() {
    Change change = newChange();
    ChangeRestoredEvent orig = new ChangeRestoredEvent(change);
    orig.change = asChangeAttribute(change);

    ChangeRestoredEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void commentAddedEvent() {
    Change change = newChange();
    CommentAddedEvent orig = new CommentAddedEvent(change);
    orig.change = asChangeAttribute(change);

    CommentAddedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void privateStateChangedEvent() {
    Change change = newChange();
    PrivateStateChangedEvent orig = new PrivateStateChangedEvent(change);
    orig.change = asChangeAttribute(change);

    PrivateStateChangedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void reviewerAddedEvent() {
    Change change = newChange();
    ReviewerAddedEvent orig = new ReviewerAddedEvent(change);
    orig.change = asChangeAttribute(change);

    ReviewerAddedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void reviewerDeletedEvent() {
    Change change = newChange();
    ReviewerDeletedEvent orig = new ReviewerDeletedEvent(change);
    orig.change = asChangeAttribute(change);

    ReviewerDeletedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void voteDeletedEvent() {
    Change change = newChange();
    VoteDeletedEvent orig = new VoteDeletedEvent(change);
    orig.change = asChangeAttribute(change);

    VoteDeletedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void workinProgressStateChangedEvent() {
    Change change = newChange();
    WorkInProgressStateChangedEvent orig = new WorkInProgressStateChangedEvent(change);
    orig.change = asChangeAttribute(change);

    WorkInProgressStateChangedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void topicChangedEvent() {
    Change change = newChange();
    TopicChangedEvent orig = new TopicChangedEvent(change);
    orig.change = asChangeAttribute(change);

    TopicChangedEvent e = roundTrip(orig);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  private <T> Supplier<T> createSupplier(T value) {
    return Suppliers.memoize(() -> value);
  }

  private Change newChange() {
    Change change =
        new Change(
            Change.key("Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
            Change.id(1000),
            Account.id(1000),
            BranchNameKey.create(Project.nameKey("myproject"), "mybranch"),
            new Timestamp(System.currentTimeMillis()));
    return change;
  }

  private Supplier<AccountAttribute> newAccount(String name) {
    AccountAttribute account = new AccountAttribute();
    account.name = name;
    account.email = name + "@somewhere.com";
    account.username = name;
    return Suppliers.ofInstance(account);
  }

  private void assertSameChangeEvent(ChangeEvent current, ChangeEvent expected) {
    assertThat(current.changeKey.get()).isEqualTo(expected.changeKey.get());
    assertThat(current.refName).isEqualTo(expected.refName);
    assertThat(current.project).isEqualTo(expected.project);
    assertSameChange(current.change, expected.change);
  }

  private void assertSameChange(
      Supplier<ChangeAttribute> currentSupplier, Supplier<ChangeAttribute> expectedSupplier) {
    ChangeAttribute current = currentSupplier.get();
    ChangeAttribute expected = expectedSupplier.get();
    assertThat(current.project).isEqualTo(expected.project);
    assertThat(current.branch).isEqualTo(expected.branch);
    assertThat(current.topic).isEqualTo(expected.topic);
    assertThat(current.id).isEqualTo(expected.id);
    assertThat(current.number).isEqualTo(expected.number);
    assertThat(current.subject).isEqualTo(expected.subject);
    assertThat(current.commitMessage).isEqualTo(expected.commitMessage);
    assertThat(current.url).isEqualTo(expected.url);
    assertThat(current.status).isEqualTo(expected.status);
    assertThat(current.createdOn).isEqualTo(expected.createdOn);
    assertThat(current.wip).isEqualTo(expected.wip);
    assertThat(current.isPrivate).isEqualTo(expected.isPrivate);
  }

  private void assertSameAccount(
      Supplier<AccountAttribute> currentSupplier, Supplier<AccountAttribute> expectedSupplier) {
    AccountAttribute current = currentSupplier.get();
    AccountAttribute expected = expectedSupplier.get();
    assertThat(current.name).isEqualTo(expected.name);
    assertThat(current.email).isEqualTo(expected.email);
    assertThat(current.username).isEqualTo(expected.username);
  }

  public Supplier<ChangeAttribute> asChangeAttribute(Change change) {
    ChangeAttribute a = new ChangeAttribute();
    a.project = change.getProject().get();
    a.branch = change.getDest().shortName();
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

  @SuppressWarnings("unchecked")
  private <E extends Event> E roundTrip(E event) {
    String json = gson.toJson(event);
    return (E) gson.fromJson(json, event.getClass());
  }
}
