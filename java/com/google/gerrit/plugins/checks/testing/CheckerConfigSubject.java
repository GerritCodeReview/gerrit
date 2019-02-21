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

package com.google.gerrit.plugins.checks.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.common.truth.Truth8;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.db.CheckerConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.testing.ObjectIdSubject;
import com.google.gerrit.truth.OptionalSubject;
import java.sql.Timestamp;
import java.util.Optional;

public class CheckerConfigSubject extends Subject<CheckerConfigSubject, CheckerConfig> {
  public static CheckerConfigSubject assertThat(CheckerConfig checkerConfig) {
    return assertAbout(CheckerConfigSubject::new).that(checkerConfig);
  }

  private CheckerConfigSubject(FailureMetadata metadata, CheckerConfig actual) {
    super(metadata, actual);
  }

  public void hasUuid(String expectedUuid) {
    Truth.assertThat(checker().getUuid()).named("uuid").isEqualTo(expectedUuid);
  }

  public void hasName(String expectedName) {
    Truth.assertThat(checker().getName()).named("name").isEqualTo(expectedName);
  }

  public OptionalSubject<StringSubject, String> hasDescriptionThat() {
    return OptionalSubject.assertThat(checker().getDescription(), Truth::assertThat)
        .named("description");
  }

  public OptionalSubject<StringSubject, String> hasUrlThat() {
    return OptionalSubject.assertThat(checker().getUrl(), Truth::assertThat).named("url");
  }

  public void hasRepository(Project.NameKey expectedRepository) {
    Truth.assertThat(checker().getRepository()).named("repository").isEqualTo(expectedRepository);
  }

  public void hasStatus(CheckerStatus expectedStatus) {
    Truth.assertThat(checker().getStatus()).named("status").isEqualTo(expectedStatus);
  }

  public ComparableSubject<?, Timestamp> hasCreatedOnThat() {
    return Truth.assertThat(checker().getCreatedOn()).named("createdOn");
  }

  public ObjectIdSubject hasRefStateThat() {
    return ObjectIdSubject.assertThat(checker().getRefState()).named("refState");
  }

  public IterableSubject configStringList(String name) {
    isNotNull();
    return Truth.assertThat(actual().getConfigForTesting().getStringList("checker", null, name))
        .asList()
        .named("value of checker.%s", name);
  }

  private Checker checker() {
    isNotNull();
    Optional<Checker> checker = actual().getLoadedChecker();
    Truth8.assertThat(checker).named("checker is loaded").isPresent();
    return checker.get();
  }
}
