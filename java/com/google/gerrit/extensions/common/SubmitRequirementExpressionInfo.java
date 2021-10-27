// Copyright (C) 2021 The Android Open Source Project
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

/** Result of evaluating a single submit requirement expression. */
public class SubmitRequirementExpressionInfo {

  /** Submit requirement expression as a String. */
  public String expression;

  /** A boolean indicating if the expression is fulfilled on a change. */
  public boolean fulfilled;

  /**
   * A list of all atoms that are passing, for example query "branch:refs/heads/foo and project:bar"
   * has two atoms: ["branch:refs/heads/foo", "project:bar"].
   */
  public List<String> passingAtoms;

  /**
   * A list of all atoms that are failing, for example query "branch:refs/heads/foo and project:bar"
   * has two atoms: ["branch:refs/heads/foo", "project:bar"].
   */
  public List<String> failingAtoms;

  /**
   * If true, {@link #expression}, {@link #passingAtoms} and {@link #failingAtoms} will be null.
   * This is used to hide the submit requirement expressions from calling users.
   */
  public boolean hidden;
}
