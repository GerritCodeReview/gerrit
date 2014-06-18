// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.api.changes.ChangeType;

import java.util.List;

public class DiffInfo {
  public FileMeta metaA;
  public FileMeta metaB;
  public IntraLineStatus intralineStatus;
  public ChangeType changeType;
  public List<String> diffHeader;
  public List<ContentEntry> content;

  public enum IntraLineStatus {
    OK,
    TIMEOUT,
    FAILURE
  }

  public static class FileMeta {
    public String name;
    public String contentType;
    public Integer lines;
  }

  public static final class ContentEntry {
    // Common lines to both sides.
    public List<String> ab;
    // Lines of a.
    public List<String> a;
    // Lines of b.
    public List<String> b;

    // A list of changed sections of the corresponding line list.
    // Each entry is a character <offset, length> pair. The offset is from the
    // beginning of the first line in the list. Also, the offset includes an
    // implied trailing newline character for each line.
    public List<List<Integer>> editA;
    public List<List<Integer>> editB;

    // a and b are actually common with this whitespace ignore setting.
    public Boolean common;

    // Number of lines to skip on both sides.
    public Integer skip;
  }
}
