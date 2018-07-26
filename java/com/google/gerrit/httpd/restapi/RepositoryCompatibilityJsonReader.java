// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.httpd.restapi;

import com.google.gerrit.server.config.RepositoryProjectCompatibility;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;

/** Replaces {@code project} with {@code repository}. */
class RepositoryCompatibilityJsonReader extends JsonReader {

  private final RepositoryProjectCompatibility repositoryProjectCompatibility;

  RepositoryCompatibilityJsonReader(
      RepositoryProjectCompatibility repositoryProjectCompatibility, Reader reader) {
    super(reader);
    this.repositoryProjectCompatibility = repositoryProjectCompatibility;
  }

  @Override
  public String nextName() throws IOException {
    String nextName = super.nextName();
    if ("repository".equals(nextName)
        && repositoryProjectCompatibility != RepositoryProjectCompatibility.PROJECT_ONLY) {
      return "project";
    }
    if ("project".equals(nextName)
        && repositoryProjectCompatibility != RepositoryProjectCompatibility.REPOSITORY_ONLY) {
      return "<undefined>";
    }
    return nextName;
  }
}
