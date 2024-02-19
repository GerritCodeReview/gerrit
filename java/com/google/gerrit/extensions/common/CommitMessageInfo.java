// Copyright (C) 2024 The Android Open Source Project
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

import java.util.Map;

/** Representation of a commit message used in the API. */
public class CommitMessageInfo {
  /**
   * The subject of the change.
   *
   * <p>First line of the commit message.
   */
  public String subject;

  /** Full commit message of the change. */
  public String fullMessage;

  /**
   * The footers from the commit message.
   *
   * <p>Key-value pairs from the last paragraph of the commit message.
   */
  public Map<String, String> footers;
}
