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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.sql.Timestamp;
import org.junit.Test;

public class EventDeserializerTest extends GerritBaseTests {

  Gson gsonSerializer =
      new GsonBuilder().registerTypeAdapter(Supplier.class, new SupplierSerializer()).create();
  Gson gsonDeserializer = new GsonEventDeserializerProvider().get();

  @Test
  public void refUpdatedEvent() {
    RefUpdatedEvent refUpdatedEvent = new RefUpdatedEvent();

    RefUpdateAttribute refUpdatedAttribute = new RefUpdateAttribute();
    refUpdatedAttribute.refName = "refs/heads/master";
    refUpdatedEvent.refUpdate = createSupplier(refUpdatedAttribute);

    AccountAttribute accountAttribute = new AccountAttribute();
    accountAttribute.email = "some.user@domain.com";
    refUpdatedEvent.submitter = createSupplier(accountAttribute);

    String serializedEvent = gsonSerializer.toJson(refUpdatedEvent);
    RefUpdatedEvent e = (RefUpdatedEvent) gsonDeserializer.fromJson(serializedEvent, Event.class);

    assertThat(e).isNotNull();
    assertThat(e.refUpdate).isInstanceOf(Supplier.class);
    assertThat(e.refUpdate.get().refName).isEqualTo(refUpdatedAttribute.refName);
    assertThat(e.submitter).isInstanceOf(Supplier.class);
    assertThat(e.submitter.get().email).isEqualTo(accountAttribute.email);
  }

  @Test
  public void patchSetCreatedEvent() {
    Change change = newChange("Iabcdef");
    PatchSetCreatedEvent orig = new PatchSetCreatedEvent(change);
    orig.change = asChangeAttribute(change);
    orig.uploader = newAccount("uploader");

    PatchSetCreatedEvent e =
        (PatchSetCreatedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
    assertSameAccount(e.uploader, orig.uploader);
  }

  @Test
  public void assigneeChangedEvent() {
    Change change = newChange("Iabcdef");
    AssigneeChangedEvent orig = new AssigneeChangedEvent(change);
    orig.change = asChangeAttribute(change);
    orig.changer = newAccount("changer");
    orig.oldAssignee = newAccount("oldAssignee");

    AssigneeChangedEvent e =
        (AssigneeChangedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
    assertSameAccount(e.changer, orig.changer);
    assertSameAccount(e.oldAssignee, orig.oldAssignee);
  }

  @Test
  public void changeDeletedEvent() {
    Change change = newChange("Iabcdef");
    ChangeDeletedEvent orig = new ChangeDeletedEvent(change);
    orig.change = asChangeAttribute(change);
    orig.deleter = newAccount("deleter");

    ChangeDeletedEvent e =
        (ChangeDeletedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
    assertSameAccount(e.deleter, orig.deleter);
  }

  @Test
  public void hashtagsChangedEvent() {
    Change change = newChange("Iabcdef");
    HashtagsChangedEvent orig = new HashtagsChangedEvent(change);
    orig.change = asChangeAttribute(change);

    HashtagsChangedEvent e =
        (HashtagsChangedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void changeAbandonedEvent() {
    Change change = newChange("Iabcdef");
    ChangeAbandonedEvent orig = new ChangeAbandonedEvent(change);
    orig.change = asChangeAttribute(change);

    ChangeAbandonedEvent e =
        (ChangeAbandonedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void changeMergedEvent() {
    Change change = newChange("Iabcdef");
    ChangeMergedEvent orig = new ChangeMergedEvent(change);
    orig.change = asChangeAttribute(change);

    ChangeMergedEvent e =
        (ChangeMergedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void changeRestoredEvent() {
    Change change = newChange("Iabcdef");
    ChangeRestoredEvent orig = new ChangeRestoredEvent(change);
    orig.change = asChangeAttribute(change);

    ChangeRestoredEvent e =
        (ChangeRestoredEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void commentAddedEvent() {
    Change change = newChange("Iabcdef");
    CommentAddedEvent orig = new CommentAddedEvent(change);
    orig.change = asChangeAttribute(change);

    CommentAddedEvent e =
        (CommentAddedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void privateStateChangedEvent() {
    Change change = newChange("Iabcdef");
    PrivateStateChangedEvent orig = new PrivateStateChangedEvent(change);
    orig.change = asChangeAttribute(change);

    PrivateStateChangedEvent e =
        (PrivateStateChangedEvent)
            gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void reviewerAddedEvent() {
    Change change = newChange("Iabcdef");
    ReviewerAddedEvent orig = new ReviewerAddedEvent(change);
    orig.change = asChangeAttribute(change);

    ReviewerAddedEvent e =
        (ReviewerAddedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void reviewerDeletedEvent() {
    Change change = newChange("Iabcdef");
    ReviewerDeletedEvent orig = new ReviewerDeletedEvent(change);
    orig.change = asChangeAttribute(change);

    ReviewerDeletedEvent e =
        (ReviewerDeletedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void voteDeletedEvent() {
    Change change = newChange("Iabcdef");
    VoteDeletedEvent orig = new VoteDeletedEvent(change);
    orig.change = asChangeAttribute(change);

    VoteDeletedEvent e =
        (VoteDeletedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void workinProgressStateChangedEvent() {
    Change change = newChange("Iabcdef");
    WorkInProgressStateChangedEvent orig = new WorkInProgressStateChangedEvent(change);
    orig.change = asChangeAttribute(change);

    WorkInProgressStateChangedEvent e =
        (WorkInProgressStateChangedEvent)
            gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  @Test
  public void topicChangedEvent() {
    Change change = newChange("Iabcdef");
    TopicChangedEvent orig = new TopicChangedEvent(change);
    orig.change = asChangeAttribute(change);

    TopicChangedEvent e =
        (TopicChangedEvent) gsonDeserializer.fromJson(gsonSerializer.toJson(orig), Event.class);

    assertThat(e).isNotNull();
    assertSameChangeEvent(e, orig);
  }

  private <T> Supplier<T> createSupplier(T value) {
    return Suppliers.memoize(() -> value);
  }

  private Change newChange(String changeKey) {
    Change change =
        new Change(
            Change.key(changeKey),
            Change.id(1000),
            Account.id(1000),
            Branch.nameKey(Project.nameKey("myproject"), "mybranch"),
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
}
