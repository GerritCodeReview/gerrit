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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.UserScopedEventListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.ChangeMergedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.events.ReviewerDeletedEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

public class EventRecorder {
  private final RegistrationHandle eventListenerRegistration;
  private final Multimap<String, RefEvent> recordedEvents;

  @Singleton
  public static class Factory {
    private final DynamicSet<UserScopedEventListener> eventListeners;
    private final IdentifiedUser.GenericFactory userFactory;

    @Inject
    Factory(
        DynamicSet<UserScopedEventListener> eventListeners,
        IdentifiedUser.GenericFactory userFactory) {
      this.eventListeners = eventListeners;
      this.userFactory = userFactory;
    }

    public EventRecorder create(TestAccount user) {
      return new EventRecorder(eventListeners, userFactory.create(user.id));
    }
  }

  public EventRecorder(
      DynamicSet<UserScopedEventListener> eventListeners, final IdentifiedUser user) {
    recordedEvents = LinkedListMultimap.create();

    eventListenerRegistration =
        eventListeners.add(
            new UserScopedEventListener() {
              @Override
              public void onEvent(Event e) {
                if (e instanceof ReviewerDeletedEvent) {
                  recordedEvents.put(ReviewerDeletedEvent.TYPE, (ReviewerDeletedEvent) e);
                } else if (e instanceof RefEvent) {
                  RefEvent event = (RefEvent) e;
                  String key =
                      refEventKey(
                          event.getType(), event.getProjectNameKey().get(), event.getRefName());
                  recordedEvents.put(key, event);
                }
              }

              @Override
              public CurrentUser getUser() {
                return user;
              }
            });
  }

  private static String refEventKey(String type, String project, String ref) {
    return String.format("%s-%s-%s", type, project, ref);
  }

  private ImmutableList<RefUpdatedEvent> getRefUpdatedEvents(
      String project, String refName, int expectedSize) {
    String key = refEventKey(RefUpdatedEvent.TYPE, project, refName);
    if (expectedSize == 0) {
      assertThat(recordedEvents).doesNotContainKey(key);
      return ImmutableList.of();
    }

    assertThat(recordedEvents).containsKey(key);
    ImmutableList<RefUpdatedEvent> events =
        FluentIterable.from(recordedEvents.get(key))
            .transform(RefUpdatedEvent.class::cast)
            .toList();
    assertThat(events).hasSize(expectedSize);
    return events;
  }

  private ImmutableList<ChangeMergedEvent> getChangeMergedEvents(
      String project, String branch, int expectedSize) {
    String key = refEventKey(ChangeMergedEvent.TYPE, project, branch);
    if (expectedSize == 0) {
      assertThat(recordedEvents).doesNotContainKey(key);
      return ImmutableList.of();
    }

    assertThat(recordedEvents).containsKey(key);
    ImmutableList<ChangeMergedEvent> events =
        FluentIterable.from(recordedEvents.get(key))
            .transform(ChangeMergedEvent.class::cast)
            .toList();
    assertThat(events).hasSize(expectedSize);
    return events;
  }

  private ImmutableList<ReviewerDeletedEvent> getReviewerDeletedEvents(int expectedSize) {
    String key = ReviewerDeletedEvent.TYPE;
    if (expectedSize == 0) {
      assertThat(recordedEvents).doesNotContainKey(key);
      return ImmutableList.of();
    }
    assertThat(recordedEvents).containsKey(key);
    ImmutableList<ReviewerDeletedEvent> events =
        FluentIterable.from(recordedEvents.get(key))
            .transform(ReviewerDeletedEvent.class::cast)
            .toList();
    assertThat(events).hasSize(expectedSize);
    return events;
  }

  public void assertRefUpdatedEvents(String project, String branch, String... expected)
      throws Exception {
    ImmutableList<RefUpdatedEvent> events =
        getRefUpdatedEvents(project, branch, expected.length / 2);
    int i = 0;
    for (RefUpdatedEvent event : events) {
      RefUpdateAttribute actual = event.refUpdate.get();
      String oldRev = expected[i] == null ? ObjectId.zeroId().name() : expected[i];
      String newRev = expected[i + 1] == null ? ObjectId.zeroId().name() : expected[i + 1];
      assertThat(actual.oldRev).isEqualTo(oldRev);
      assertThat(actual.newRev).isEqualTo(newRev);
      i += 2;
    }
  }

  public void assertRefUpdatedEvents(String project, String branch, RevCommit... expected)
      throws Exception {
    ImmutableList<RefUpdatedEvent> events =
        getRefUpdatedEvents(project, branch, expected.length / 2);
    int i = 0;
    for (RefUpdatedEvent event : events) {
      RefUpdateAttribute actual = event.refUpdate.get();
      String oldRev = expected[i] == null ? ObjectId.zeroId().name() : expected[i].name();
      String newRev = expected[i + 1] == null ? ObjectId.zeroId().name() : expected[i + 1].name();
      assertThat(actual.oldRev).isEqualTo(oldRev);
      assertThat(actual.newRev).isEqualTo(newRev);
      i += 2;
    }
  }

  public void assertChangeMergedEvents(String project, String branch, String... expected)
      throws Exception {
    ImmutableList<ChangeMergedEvent> events =
        getChangeMergedEvents(project, branch, expected.length / 2);
    int i = 0;
    for (ChangeMergedEvent event : events) {
      String id = event.change.get().id;
      assertThat(id).isEqualTo(expected[i]);
      assertThat(event.newRev).isEqualTo(expected[i + 1]);
      i += 2;
    }
  }

  public void assertReviewerDeletedEvents(String... expected) {
    ImmutableList<ReviewerDeletedEvent> events = getReviewerDeletedEvents(expected.length / 2);
    int i = 0;
    for (ReviewerDeletedEvent event : events) {
      String id = event.change.get().id;
      assertThat(id).isEqualTo(expected[i]);
      String reviewer = event.reviewer.get().email;
      assertThat(reviewer).isEqualTo(expected[i + 1]);
      i += 2;
    }
  }

  public void close() {
    eventListenerRegistration.remove();
  }
}
