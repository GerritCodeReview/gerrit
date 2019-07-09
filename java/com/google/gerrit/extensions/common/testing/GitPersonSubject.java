// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.common.testing;

import static com.google.common.truth.Truth.assertAbout;
import static java.util.Objects.requireNonNull;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.GitPerson;
import java.sql.Timestamp;
import java.util.Date;
import org.eclipse.jgit.lib.PersonIdent;

public class GitPersonSubject extends Subject {

  public static GitPersonSubject assertThat(GitPerson gitPerson) {
    return assertAbout(gitPersons()).that(gitPerson);
  }

  public static Factory<GitPersonSubject, GitPerson> gitPersons() {
    return GitPersonSubject::new;
  }

  private final GitPerson gitPerson;

  private GitPersonSubject(FailureMetadata failureMetadata, GitPerson gitPerson) {
    super(failureMetadata, gitPerson);
    this.gitPerson = gitPerson;
  }

  public StringSubject name() {
    isNotNull();
    return check("name").that(gitPerson.name);
  }

  public StringSubject email() {
    isNotNull();
    return check("email").that(gitPerson.email);
  }

  public ComparableSubject<Timestamp> date() {
    isNotNull();
    return check("date").that(gitPerson.date);
  }

  public IntegerSubject tz() {
    isNotNull();
    return check("tz").that(gitPerson.tz);
  }

  public void hasSameDateAs(GitPerson other) {
    requireNonNull(other, "'other' GitPerson must not be null");
    isNotNull();
    date().isEqualTo(other.date);
    tz().isEqualTo(other.tz);
  }

  public void matches(PersonIdent ident) {
    isNotNull();
    name().isEqualTo(ident.getName());
    email().isEqualTo(ident.getEmailAddress());
    check("roundedDate()").that(new Date(gitPerson.date.getTime())).isEqualTo(ident.getWhen());
    tz().isEqualTo(ident.getTimeZoneOffset());
  }
}
