// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.git.receive;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.git.validators.ValidationMessage;

/**
 * Extension point to validate push options.
 *
 * <p>Possible usages are:
 *
 * <ul>
 *   <li>Reject pushes that use a certain push option or a certain combination of push options.
 *   <li>Print a warning if a certain push option is used (e.g because the push option is
 *       deprecated).
 * </ul>
 */
@ExtensionPoint
public interface PushOptionsValidator {
  /**
   * Validate the push options that have been specified for a push to the given ref.
   *
   * @param refName The target ref of the push.
   * @param pushOptions The options that have been specified on push. This map includes the Git push
   *     options (specified by {@code -o <key>=<value>}) and options which have been specified as
   *     part of the target ref name (specified as {@code refs/for/master%<key>=<value>}).
   * @return A list of validation messages. If any message with type {@link
   *     com.google.gerrit.server.git.validators.ValidationMessage.Type#ERROR} or {@link
   *     com.google.gerrit.server.git.validators.ValidationMessage.Type#FATAL} is returned the push
   *     is rejected. Validation messages with other type are returned to the client, but let the
   *     push proceed.
   */
  ImmutableList<ValidationMessage> validate(
      String refName, ListMultimap<String, String> pushOptions);
}
