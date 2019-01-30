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

package com.google.gerrit.server.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import org.eclipse.jgit.lib.ObjectId;

public class ObjectIdSubject extends Subject<ObjectIdSubject, ObjectId> {
  public static ObjectIdSubject assertThat(ObjectId objectId) {
    return assertAbout(ObjectIdSubject::new).that(objectId);
  }

  private ObjectIdSubject(FailureMetadata metadata, ObjectId actual) {
    super(metadata, actual);
  }

  public void hasName(String expectedName) {
    isNotNull();
    ObjectId objectId = actual();
    Truth.assertThat(objectId.getName()).named("name").isEqualTo(expectedName);
  }
}
