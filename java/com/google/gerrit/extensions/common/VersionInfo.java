// Copyright (C) 2023 The Android Open Source Project
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

public class VersionInfo {
  public String gerritVersion;
  public int noteDbVersion;
  public int changeIndexVersion;
  public int accountIndexVersion;
  public int projectIndexVersion;
  public int groupIndexVersion;

  public String compact() {
    return "gerrit version " + gerritVersion + "\n";
  }

  public String verbose() {
    StringBuilder s = new StringBuilder();
    s.append("gerrit version " + gerritVersion).append("\n");
    s.append("NoteDb version " + noteDbVersion).append("\n");
    s.append("index versions\n");
    s.append(String.format("  %3d changes\n", changeIndexVersion));
    s.append(String.format("  %3d accounts\n", accountIndexVersion));
    s.append(String.format("  %3d projects\n", projectIndexVersion));
    s.append(String.format("  %3d groups\n", groupIndexVersion));
    return s.toString();
  }
}
