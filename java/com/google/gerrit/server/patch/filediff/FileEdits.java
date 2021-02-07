//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.server.patch.filediff;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/**
 * An entity class containing the list of edits between two commits for a file, and the old and new
 * paths.
 */
@AutoValue
public abstract class FileEdits {
  public static FileEdits create(
      ImmutableList<Edit> edits, Optional<String> oldPath, Optional<String> newPath) {
    return new AutoValue_FileEdits(edits, oldPath, newPath);
  }

  public static FileEdits create2(
      ImmutableList<org.eclipse.jgit.diff.Edit> jgitEdits,
      Optional<String> oldPath,
      Optional<String> newPath) {
    return new AutoValue_FileEdits(
        jgitEdits.stream().map(Edit::fromJGitEdit).collect(toImmutableList()), oldPath, newPath);
  }

  public abstract ImmutableList<Edit> edits();

  public abstract Optional<String> oldPath();

  public abstract Optional<String> newPath();

  public static FileEdits empty() {
    return new AutoValue_FileEdits(ImmutableList.of(), Optional.empty(), Optional.empty());
  }
}
