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

package com.google.gerrit.git.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import org.eclipse.jgit.lib.ObjectId;

public class ObjectIdSubject extends Subject {
  public static ObjectIdSubject assertThat(ObjectId objectId) {
    return assertAbout(objectIds()).that(objectId);
  }

  public static Factory<ObjectIdSubject, ObjectId> objectIds() {
    return ObjectIdSubject::new;
  }

  private final ObjectId objectId;

  private ObjectIdSubject(FailureMetadata metadata, ObjectId objectId) {
    super(metadata, objectId);
    this.objectId = objectId;
  }

  public void hasName(String expectedName) {
    isNotNull();
    check("getName()").that(objectId.getName()).isEqualTo(expectedName);
  }
}
