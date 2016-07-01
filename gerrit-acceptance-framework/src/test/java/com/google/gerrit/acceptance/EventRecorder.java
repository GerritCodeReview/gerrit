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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.events.BaseEvent;
import com.google.gerrit.extensions.events.ChangeMergedListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.ReviewerDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class EventRecorder implements
    ChangeMergedListener,
    GitReferenceUpdatedListener,
    ReviewerDeletedListener {
  private final Multimap<String, BaseEvent> recordedEvents;

  static Module module() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        DynamicSet.bind(binder(), ChangeMergedListener.class)
          .to(EventRecorder.class);
        DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
          .to(EventRecorder.class);
        DynamicSet.bind(binder(), ReviewerDeletedListener.class)
          .to(EventRecorder.class);
      }
    };
  }

  public EventRecorder() {
    recordedEvents = LinkedListMultimap.create();
  }

  public void clear() {
    recordedEvents.clear();
  }

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) {
    String key = refEventKey("ref-updated",
        event.getProjectName(),
        event.getRefName());
    recordedEvents.put(key, event);
  }

  @Override
  public void onChangeMerged(ChangeMergedListener.Event event) {
    String key = refEventKey("change-merged",
        event.getChange().project,
        event.getChange().branch);
    recordedEvents.put(key, event);
  }

  @Override
  public void onReviewerDeleted(ReviewerDeletedListener.Event event) {
    recordedEvents.put("reviewer-deleted", event);
  }

  private static String refEventKey(String type, String project, String ref) {
    return String.format("%s-%s-%s", type, project, ref);
  }

  private static class RefEventTransformer<T extends BaseEvent>
      implements Function<BaseEvent, T> {

    @SuppressWarnings("unchecked")
    @Override
    public T apply(BaseEvent e) {
      return (T) e;
    }
  }

  private ImmutableList<GitReferenceUpdatedListener.Event> getRefUpdatedEvents(
      String project, String refName, int expectedSize) {
    String key = refEventKey("ref-updated", project, refName);
    if (expectedSize == 0) {
      assertThat(recordedEvents).doesNotContainKey(key);
      return ImmutableList.of();
    }

    assertThat(recordedEvents).containsKey(key);
    ImmutableList<GitReferenceUpdatedListener.Event> events = FluentIterable
        .from(recordedEvents.get(key))
        .transform(new RefEventTransformer<GitReferenceUpdatedListener.Event>())
        .toList();
    assertThat(events).hasSize(expectedSize);
    return events;
  }

  private ImmutableList<ChangeMergedListener.Event> getChangeMergedEvents(
      String project, String branch, int expectedSize) {
    String key = refEventKey("change-merged", project, branch);
    if (expectedSize == 0) {
      assertThat(recordedEvents).doesNotContainKey(key);
      return ImmutableList.of();
    }

    assertThat(recordedEvents).containsKey(key);
    ImmutableList<ChangeMergedListener.Event> events = FluentIterable
        .from(recordedEvents.get(key))
        .transform(new RefEventTransformer<ChangeMergedListener.Event>())
        .toList();
    assertThat(events).hasSize(expectedSize);
    return events;
  }

  private ImmutableList<ReviewerDeletedListener.Event> getReviewerDeletedEvents(
      int expectedSize) {
    String key = "reviewer-deleted";
    if (expectedSize == 0) {
      assertThat(recordedEvents).doesNotContainKey(key);
      return ImmutableList.of();
    }
    assertThat(recordedEvents).containsKey(key);
    ImmutableList<ReviewerDeletedListener.Event> events = FluentIterable
        .from(recordedEvents.get(key))
        .transform(new RefEventTransformer<ReviewerDeletedListener.Event>())
        .toList();
    assertThat(events).hasSize(expectedSize);
    return events;
  }

  public void assertRefUpdatedEvents(String project, String branch,
      String... expected) throws Exception {
    ImmutableList<GitReferenceUpdatedListener.Event> events =
        getRefUpdatedEvents(project, branch, expected.length / 2);
    int i = 0;
    for (GitReferenceUpdatedListener.Event event : events) {
      String oldRev = expected[i] == null
          ? ObjectId.zeroId().name()
          : expected[i];
      String newRev = expected[i+1] == null
          ? ObjectId.zeroId().name()
          : expected[i+1];
      assertThat(event.getOldObjectId()).isEqualTo(oldRev);
      assertThat(event.getNewObjectId()).isEqualTo(newRev);
      i += 2;
    }
  }

  public void assertRefUpdatedEvents(String project, String branch,
      RevCommit... expected) throws Exception {
    ImmutableList<GitReferenceUpdatedListener.Event> events =
        getRefUpdatedEvents(project, branch, expected.length / 2);
    int i = 0;
    for (GitReferenceUpdatedListener.Event event : events) {
      String oldRev = expected[i] == null
          ? ObjectId.zeroId().name()
          : expected[i].name();
      String newRev = expected[i+1] == null
          ? ObjectId.zeroId().name()
          : expected[i+1].name();
      assertThat(event.getOldObjectId()).isEqualTo(oldRev);
      assertThat(event.getNewObjectId()).isEqualTo(newRev);
      i += 2;
    }
  }

  public void assertChangeMergedEvents(String project, String branch,
      String... expected) throws Exception {
    ImmutableList<ChangeMergedListener.Event> events =
        getChangeMergedEvents(project, branch, expected.length / 2);
    int i = 0;
    for (ChangeMergedListener.Event event : events) {
      String id = event.getChange().id;
      assertThat(id).isEqualTo(expected[i]);
      assertThat(event.getNewRevisionId()).isEqualTo(expected[i+1]);
      i += 2;
    }
  }

  public void assertReviewerDeletedEvents(String... expected) {
    ImmutableList<ReviewerDeletedListener.Event> events =
        getReviewerDeletedEvents(expected.length / 2);
    int i = 0;
    for (ReviewerDeletedListener.Event event : events) {
      String id = event.getChange().id;
      assertThat(id).isEqualTo(expected[i]);
      String reviewer = event.getReviewer().email;
      assertThat(reviewer).isEqualTo(expected[i+1]);
      i += 2;
    }
  }
}