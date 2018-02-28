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
package com.google.gerrit.server.rules;

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.query.change.ChangeData;

/**
 * Allows plugins to define custom rules to accept or reject submission of a change. The returned
 * value, hold in a SubmitRecord, should contain enough information for the end user to "fix".
 * Several points should be noted:
 *
 * <p>- Multiple plugins may be called for a given change.
 *
 * <p>- Each plugin is responsible for handling (or not) inheritance of configurations.
 *
 * <p>In a nutshell, this interface makes it possible to write validation rules in Java, or even to
 * write adapters for Script Engines (Prolog, JavaScript or even custom expression parsers).
 */
@ExtensionPoint
public interface SubmitRuleEvaluator {
  /** Returns a {@link SubmitRecord} status for the change. */
  SubmitRecord evaluate(ChangeData changeData);
}
