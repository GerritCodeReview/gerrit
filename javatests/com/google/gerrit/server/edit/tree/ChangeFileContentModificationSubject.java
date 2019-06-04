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

package com.google.gerrit.server.edit.tree;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.io.CharStreams;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.restapi.RawInput;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ChangeFileContentModificationSubject extends Subject {

  public static ChangeFileContentModificationSubject assertThat(
      ChangeFileContentModification modification) {
    return assertAbout(modifications()).that(modification);
  }

  public static Factory<ChangeFileContentModificationSubject, ChangeFileContentModification>
      modifications() {
    return ChangeFileContentModificationSubject::new;
  }

  private final ChangeFileContentModification modification;

  private ChangeFileContentModificationSubject(
      FailureMetadata failureMetadata, ChangeFileContentModification modification) {
    super(failureMetadata, modification);
    this.modification = modification;
  }

  public StringSubject filePath() {
    isNotNull();
    return check("getFilePath()").that(modification.getFilePath());
  }

  public StringSubject newContent() throws IOException {
    isNotNull();
    RawInput newContent = modification.getNewContent();
    check("getNewContent()").that(newContent).isNotNull();
    String contentString =
        CharStreams.toString(
            new InputStreamReader(newContent.getInputStream(), StandardCharsets.UTF_8));
    return check("getNewContent()").that(contentString);
  }
}
