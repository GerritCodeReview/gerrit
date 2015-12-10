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

package com.google.gerrit.acceptance.server.event;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.value;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.UserScopedEventListener;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ReviewerDeletedEvent;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.Util;
import com.google.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class ReviewerDeletedEventIT extends AbstractDaemonTest {

  @Inject
  private IdentifiedUser.GenericFactory factory;

  @Inject
  private DynamicSet<UserScopedEventListener> source;

  private final LabelType label = category("CustomLabel", value(1, "Positive"),
      value(0, "No score"), value(-1, "Negative"));

  private final LabelType pLabel =
      category("CustomLabel2", value(1, "Positive"), value(0, "No score"));

  private RegistrationHandle eventListenerRegistration;
  private ReviewerDeletedEvent lastReviewerDeletedEvent;

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    AccountGroup.UUID anonymousUsers =
        SystemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    Util.allow(cfg, Permission.forLabel(label.getName()), -1, 1, anonymousUsers,
        "refs/heads/*");
    Util.allow(cfg, Permission.forLabel(pLabel.getName()), 0, 1, anonymousUsers,
        "refs/heads/*");
    saveProjectConfig(project, cfg);

    eventListenerRegistration = source.add(new UserScopedEventListener() {
      @Override
      public void onEvent(Event event) {
        if (event instanceof ReviewerDeletedEvent) {
          lastReviewerDeletedEvent = (ReviewerDeletedEvent) event;
        }
      }

      @Override
      public CurrentUser getUser() {
        return factory.create(user.id);
      }
    });
  }

  @After
  public void cleanup() {
    eventListenerRegistration.remove();
  }

  private void saveLabelConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().put(label.getName(), label);
    cfg.getLabelSections().put(pLabel.getName(), pLabel);
    saveProjectConfig(project, cfg);
  }

  @Test
  public void removeReviewerWithoutVote() throws Exception {
    saveLabelConfig();

    // push a change
    PushOneCommit.Result r = createChange();

    // add reviewers
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    // remove a reviewer without vote should not fire events
    gApi.changes()
        .id(r.getChangeId())
        .reviewer(user.getId().toString()).deleteReviewer();
    assertThat(lastReviewerDeletedEvent).isNull();
  }

  @Test
  public void removeReviewerWithVote() throws Exception {
    saveLabelConfig();

    // push a change
    PushOneCommit.Result r = createChange();

    // add a review
    setApiUser(user);
    ReviewInput reviewInput =
        new ReviewInput().label(label.getName(), (short) -1);
    revision(r).review(reviewInput);

    // remove a reviewer
    gApi.changes().id(r.getChangeId()).reviewer(user.getId().toString())
        .deleteReviewer();
    assertThat(lastReviewerDeletedEvent.comment)
        .isEqualTo("Removed reviewer User with the following "
            + "votes:\n\n* CustomLabel-1 by User <user@example.com>\n");
  }
}