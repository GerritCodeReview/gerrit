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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.WatchConfig.NotifyValue;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.git.ValidationError;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class WatchConfigTest implements ValidationError.Sink {
  private List<ValidationError> validationErrors = new ArrayList<>();

  @Before
  public void setup() {
    validationErrors.clear();
  }

  @Test
  public void parseWatchConfig() throws Exception {
    Config cfg = new Config();
    cfg.fromText(
        "[project \"myProject\"]\n"
            + "  notify = * [ALL_COMMENTS, NEW_PATCHSETS]\n"
            + "  notify = branch:master [NEW_CHANGES]\n"
            + "  notify = branch:master [NEW_PATCHSETS]\n"
            + "  notify = branch:foo []\n"
            + "[project \"otherProject\"]\n"
            + "  notify = [NEW_PATCHSETS]\n"
            + "  notify = * [NEW_PATCHSETS, ALL_COMMENTS]\n");
    Map<ProjectWatchKey, Set<NotifyType>> projectWatches =
        WatchConfig.parse(new Account.Id(1000000), cfg, this);

    assertThat(validationErrors).isEmpty();

    Project.NameKey myProject = new Project.NameKey("myProject");
    Project.NameKey otherProject = new Project.NameKey("otherProject");
    Map<ProjectWatchKey, Set<NotifyType>> expectedProjectWatches = new HashMap<>();
    expectedProjectWatches.put(
        ProjectWatchKey.create(myProject, null),
        EnumSet.of(NotifyType.ALL_COMMENTS, NotifyType.NEW_PATCHSETS));
    expectedProjectWatches.put(
        ProjectWatchKey.create(myProject, "branch:master"),
        EnumSet.of(NotifyType.NEW_CHANGES, NotifyType.NEW_PATCHSETS));
    expectedProjectWatches.put(
        ProjectWatchKey.create(myProject, "branch:foo"), EnumSet.noneOf(NotifyType.class));
    expectedProjectWatches.put(
        ProjectWatchKey.create(otherProject, null), EnumSet.of(NotifyType.NEW_PATCHSETS));
    expectedProjectWatches.put(
        ProjectWatchKey.create(otherProject, null),
        EnumSet.of(NotifyType.ALL_COMMENTS, NotifyType.NEW_PATCHSETS));
    assertThat(projectWatches).containsExactlyEntriesIn(expectedProjectWatches);
  }

  @Test
  public void parseInvalidWatchConfig() throws Exception {
    Config cfg = new Config();
    cfg.fromText(
        "[project \"myProject\"]\n"
            + "  notify = * [ALL_COMMENTS, NEW_PATCHSETS]\n"
            + "  notify = branch:master [INVALID, NEW_CHANGES]\n"
            + "[project \"otherProject\"]\n"
            + "  notify = [NEW_PATCHSETS]\n");

    WatchConfig.parse(new Account.Id(1000000), cfg, this);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).getMessage())
        .isEqualTo(
            "watch.config: Invalid notify type INVALID in project watch of"
                + " account 1000000 for project myProject: branch:master"
                + " [INVALID, NEW_CHANGES]");
  }

  @Test
  public void parseNotifyValue() throws Exception {
    assertParseNotifyValue("* []", null, EnumSet.noneOf(NotifyType.class));
    assertParseNotifyValue("* [ALL_COMMENTS]", null, EnumSet.of(NotifyType.ALL_COMMENTS));
    assertParseNotifyValue("[]", null, EnumSet.noneOf(NotifyType.class));
    assertParseNotifyValue(
        "[ALL_COMMENTS, NEW_PATCHSETS]",
        null,
        EnumSet.of(NotifyType.ALL_COMMENTS, NotifyType.NEW_PATCHSETS));
    assertParseNotifyValue("branch:master []", "branch:master", EnumSet.noneOf(NotifyType.class));
    assertParseNotifyValue(
        "branch:master || branch:stable []",
        "branch:master || branch:stable",
        EnumSet.noneOf(NotifyType.class));
    assertParseNotifyValue(
        "branch:master [ALL_COMMENTS]", "branch:master", EnumSet.of(NotifyType.ALL_COMMENTS));
    assertParseNotifyValue(
        "branch:master [ALL_COMMENTS, NEW_PATCHSETS]",
        "branch:master",
        EnumSet.of(NotifyType.ALL_COMMENTS, NotifyType.NEW_PATCHSETS));
    assertParseNotifyValue("* [ALL]", null, EnumSet.of(NotifyType.ALL));

    assertThat(validationErrors).isEmpty();
  }

  @Test
  public void parseInvalidNotifyValue() {
    assertParseNotifyValueFails("* [] illegal-characters-at-the-end");
    assertParseNotifyValueFails("* [INVALID]");
    assertParseNotifyValueFails("* [ALL_COMMENTS, UNKNOWN]");
    assertParseNotifyValueFails("* [ALL_COMMENTS NEW_CHANGES]");
    assertParseNotifyValueFails("* [ALL_COMMENTS, NEW_CHANGES");
    assertParseNotifyValueFails("* ALL_COMMENTS, NEW_CHANGES]");
  }

  @Test
  public void toNotifyValue() throws Exception {
    assertToNotifyValue(null, EnumSet.noneOf(NotifyType.class), "* []");
    assertToNotifyValue("*", EnumSet.noneOf(NotifyType.class), "* []");
    assertToNotifyValue(null, EnumSet.of(NotifyType.ALL_COMMENTS), "* [ALL_COMMENTS]");
    assertToNotifyValue("branch:master", EnumSet.noneOf(NotifyType.class), "branch:master []");
    assertToNotifyValue(
        "branch:master",
        EnumSet.of(NotifyType.ALL_COMMENTS, NotifyType.NEW_PATCHSETS),
        "branch:master [ALL_COMMENTS, NEW_PATCHSETS]");
    assertToNotifyValue(
        "branch:master",
        EnumSet.of(
            NotifyType.ABANDONED_CHANGES,
            NotifyType.ALL_COMMENTS,
            NotifyType.NEW_CHANGES,
            NotifyType.NEW_PATCHSETS,
            NotifyType.SUBMITTED_CHANGES),
        "branch:master [ABANDONED_CHANGES, ALL_COMMENTS, NEW_CHANGES,"
            + " NEW_PATCHSETS, SUBMITTED_CHANGES]");
    assertToNotifyValue("*", EnumSet.of(NotifyType.ALL), "* [ALL]");
  }

  private void assertParseNotifyValue(
      String notifyValue, String expectedFilter, Set<NotifyType> expectedNotifyTypes) {
    NotifyValue nv = parseNotifyValue(notifyValue);
    assertThat(nv.filter()).isEqualTo(expectedFilter);
    assertThat(nv.notifyTypes()).containsExactlyElementsIn(expectedNotifyTypes);
  }

  private static void assertToNotifyValue(
      String filter, Set<NotifyType> notifyTypes, String expectedNotifyValue) {
    NotifyValue nv = NotifyValue.create(filter, notifyTypes);
    assertThat(nv.toString()).isEqualTo(expectedNotifyValue);
  }

  private void assertParseNotifyValueFails(String notifyValue) {
    assertThat(validationErrors).isEmpty();
    parseNotifyValue(notifyValue);
    assertThat(validationErrors)
        .named("expected validation error for notifyValue: " + notifyValue)
        .isNotEmpty();
    validationErrors.clear();
  }

  private NotifyValue parseNotifyValue(String notifyValue) {
    return NotifyValue.parse(new Account.Id(1000000), "project", notifyValue, this);
  }

  @Override
  public void error(ValidationError error) {
    validationErrors.add(error);
  }
}
