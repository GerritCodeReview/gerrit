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

package com.google.gerrit.extensions.common;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import java.nio.file.Path;

public class PathSubject extends Subject<PathSubject, Path> {
  private static final SubjectFactory<PathSubject, Path> PATH_SUBJECT_FACTORY =
      new SubjectFactory<PathSubject, Path>() {
        @Override
        public PathSubject getSubject(FailureStrategy failureStrategy, Path path) {
          return new PathSubject(failureStrategy, path);
        }
      };

  private PathSubject(FailureStrategy failureStrategy, Path path) {
    super(failureStrategy, path);
  }

  public static PathSubject assertThat(Path path) {
    return assertAbout(PATH_SUBJECT_FACTORY).that(path);
  }
}
