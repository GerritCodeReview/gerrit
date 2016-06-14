// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

/** Output options available for retrieval change details. */
public enum SubmittedTogetherOption {
  /** Dummy for changes not visible. */
  DUMMY(0),

  /**
   * In v2.12 the return type of the submitted_together REST API call was
   * @code{List<ChangeInfo>}, but that is hard to extend. When the OBJECT
   * flag is given, we return a SubmittedTogetherInfo object that is easier to
   * extend.
   */
  OBJECT(1);

  private final int value;

  SubmittedTogetherOption(int v) {
    this.value = v;
  }

  public int getValue() {
    return value;
  }
}
