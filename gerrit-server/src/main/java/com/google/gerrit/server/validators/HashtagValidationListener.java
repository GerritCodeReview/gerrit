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

package com.google.gerrit.server.validators;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Change;
import java.util.Set;

/** Listener to provide validation of hashtag changes. */
@ExtensionPoint
public interface HashtagValidationListener {
  /**
   * Invoked by Gerrit before hashtags are changed.
   *
   * @param change the change on which the hashtags are changed
   * @param toAdd the hashtags to be added
   * @param toRemove the hashtags to be removed
   * @throws ValidationException if validation fails
   */
  void validateHashtags(Change change, Set<String> toAdd, Set<String> toRemove)
      throws ValidationException;
}
