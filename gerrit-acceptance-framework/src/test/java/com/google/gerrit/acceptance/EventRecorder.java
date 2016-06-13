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
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.UserScopedEventListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.ChangeMergedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;

public class EventRecorder {
  private final RegistrationHandle eventListenerRegistration;
  private final Multimap<String, RefEvent> recordedEvents;

  @Singleton
  public static class Factory {
    private final DynamicSet<UserScopedEventListener> eventListeners;
    private final IdentifiedUser.GenericFactory userFactory;

    @Inject
    Factory(DynamicSet<UserScopedEventListener> eventListeners,
        IdentifiedUser.GenericFactory userFactory) {
      this.eventListeners = eventListeners;
      this.userFactory = userFactory;
    }

    public EventRecorder create(TestAccount user) {
      return new EventRecorder(eventListeners, userFactory.create(user.id));
    }
  }

  public EventRecorder(DynamicSet<UserScopedEventListener> eventListeners,
      final IdentifiedUser user) {
    recordedEvents = LinkedListMultimap.create();

    eventListenerRegistration = eventListeners.add(
        new UserScopedEventListener() {
          @Override
          public void onEvent(Event e) {
            if (e instanceof RefEvent) {
              RefEvent event = (RefEvent) e;
              String key = key(event.getType(), event.getProjectNameKey().get(),
                  event.getRefName());
              recordedEvents.put(key, event);
            }
          }

          @Override
          public CurrentUser getUser() {
            return user;
          }
        });
  }

  private static String key(String type, String project, String ref) {
    return String.format("%s-%s-%s", type, project, ref);
  }

  private static class RefEventTransformer<T extends RefEvent>
      implements Function<RefEvent, T> {

    @SuppressWarnings("unchecked")
    @Override
    public T apply(RefEvent e) {
      return (T) e;
    }
  }

  private static class ChangeMergedEventFilter implements Predicate<RefEvent> {
    private final String changeNumber;

    ChangeMergedEventFilter(String changeNumber) {
      this.changeNumber = changeNumber;
    }

    @Override
    public boolean apply(RefEvent e) {
      assertThat(e).isInstanceOf(ChangeMergedEvent.class);
      return ((ChangeMergedEvent) e).change.get().number.equals(changeNumber);
    }
  }

  public ImmutableList<RefUpdatedEvent> getRefUpdatedEvents(String project,
      String refName, int expectedSize) {
    String key = key(RefUpdatedEvent.TYPE, project, refName);
    if (expectedSize == 0) {
      assertThat(recordedEvents).doesNotContainKey(key);
      return ImmutableList.of();
    }

    assertThat(recordedEvents).containsKey(key);
    ImmutableList<RefUpdatedEvent> events = FluentIterable
        .from(recordedEvents.get(key))
        .transform(new RefEventTransformer<RefUpdatedEvent>())
        .toList();
    assertThat(events).hasSize(expectedSize);
    return events;
  }

  public RefUpdatedEvent getRefUpdatedEvent(String project, String refName) {
    return getRefUpdatedEvents(project, refName, 1).get(0);
  }

  public ImmutableList<ChangeMergedEvent> getChangeMergedEvents(String project,
      String branch) {
    String key = key(ChangeMergedEvent.TYPE, project,
        branch.startsWith(R_HEADS) ? branch : R_HEADS + branch);
    if (recordedEvents.containsKey(key)) {
      return FluentIterable
          .from(recordedEvents.get(key))
          .transform(new RefEventTransformer<ChangeMergedEvent>())
          .toList();
    }
    return ImmutableList.of();
  }

  public ChangeMergedEvent getChangeMergedEvent(String project, String branch,
      String changeNumber) {
    ImmutableList<ChangeMergedEvent> events = FluentIterable
        .from(getChangeMergedEvents(project, branch))
        .filter(new ChangeMergedEventFilter(changeNumber))
        .toList();
    assertThat(events).hasSize(1);
    return events.get(0);
  }

  public void close() {
    eventListenerRegistration.remove();
  }
}