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

package com.google.gerrit.index.query;

/** Result returned by {@link Matchable}. */
public class MatchResult {
  /** true if matches */
  public final boolean status;

  /** explanation for why it matched or not */
  public final String explanation;

  public MatchResult(boolean status, String explanation) {
    this.status = status;
    this.explanation = explanation;
  }
}
