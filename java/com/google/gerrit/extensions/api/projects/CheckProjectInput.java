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

package com.google.gerrit.extensions.api.projects;

public class CheckProjectInput {
  public AutoCloseableChangesCheckInput autoCloseableChangesCheck;

  public static class AutoCloseableChangesCheckInput {
    /** Whether auto-closeable changes should be fixed by setting their status to MERGED. */
    public Boolean fix;

    /** Branch that should be checked for auto-closeable changes. */
    public String branch;

    /** Number of commits to skip. */
    public Integer skipCommits;

    /** Maximum number of commits to walk. */
    public Integer maxCommits;
  }
}
