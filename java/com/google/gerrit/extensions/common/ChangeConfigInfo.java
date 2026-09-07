// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.client.SubmitWholeTopicMode;

/** API response containing values from the {@code change} section of {@code gerrit.config}. */
public class ChangeConfigInfo {
  public Boolean allowBlame;
  public Boolean disablePrivateChanges;
  public int updateDelay;

  /**
   * Backward-compatible boolean: {@code true} when {@link #submitWholeTopicMode} is {@link
   * SubmitWholeTopicMode#ENFORCED}, {@code false} or {@code null} otherwise.
   *
   * @deprecated Use {@link #submitWholeTopicMode} instead.
   */
  @Deprecated public Boolean submitWholeTopic;

  /**
   * The full {@link SubmitWholeTopicMode} controlling how topic-based submission behaves. Clients
   * that understand this field should prefer it over {@link #submitWholeTopic}.
   */
  public SubmitWholeTopicMode submitWholeTopicMode;

  public String mergeabilityComputationBehavior;
  public Boolean conflictsPredicateEnabled;
  public Boolean allowMarkdownBase64ImagesInComments;
}
