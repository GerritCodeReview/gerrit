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

import java.util.List;

/* This entity contains information about the diff of a file in a revision. */
public class DiffInfo {
  // Meta information about the file on side A
  public FileMeta metaA;
  // Meta information about the file on side B
  public FileMeta metaB;
  // Intraline status
  public IntraLineStatus intralineStatus;
  // The type of change
  public ChangeType changeType;
  // A list of strings representing the patch set diff header
  public List<String> diffHeader;
  // The content differences in the file as a list of entities
  public List<ContentEntry> content;
  // Links to the file diff in external sites
  public List<DiffWebLinkInfo> webLinks;
  // Binary file
  public Boolean binary;

  public enum IntraLineStatus {
    OK,
    TIMEOUT,
    FAILURE
  }

  public static class FileMeta {
    // The ID of the commit containing the file
    public transient String commitId;
    // The name of the file
    public String name;
    // The content type of the file
    public String contentType;
    // The total number of lines in the file
    public Integer lines;
    // Links to the file in external sites
    public List<WebLinkInfo> webLinks;
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

    // Indicates that this entry only exists because of a rebase (and not because of a real change
    // between 'a' and 'b').
    public Boolean dueToRebase;

    // a and b are actually common with this whitespace ignore setting.
    public Boolean common;

    // Number of lines to skip on both sides.
    public Integer skip;
  }
}
