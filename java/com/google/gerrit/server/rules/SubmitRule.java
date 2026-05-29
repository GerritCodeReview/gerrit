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

import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.query.change.ChangeData;
import java.util.Optional;

/**
 * Allows plugins to decide whether a change is ready to be submitted or not.
 *
 * <p>For a given {@link ChangeData}, each plugin is called and returns a {@link Optional} of {@link
 * SubmitRecord}.
 *
 * <p>A Change can only be submitted if all the plugins give their consent.
 *
 * <p>Each {@link SubmitRecord} represents a decision made by the plugin. If the plugin rejects a
 * change, it should hold valuable informations to help the end user understand and correct the
 * blocking points.
 *
 * <p>It should be noted that each plugin can handle rules inheritance.
 *
 * <p>This interface should be used to write pre-submit validation rules. This includes both simple
 * checks, coded in Java, and more complex fully fledged expression evaluators (think: Prolog,
 * JavaCC, or even JavaScript rules).
 */
@ExtensionPoint
public interface SubmitRule {
  /**
   * Returns a {@link Optional} of {@link SubmitRecord} status for the change. {@code
   * Optional#empty()} if the SubmitRule was a no-op.
   */
  Optional<SubmitRecord> evaluate(ChangeData changeData);
}
