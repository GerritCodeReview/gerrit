// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.config;

import java.util.List;

public abstract class TrackingValueExtractor {
  /**
   * Invoked by Gerrit during change indexing, allowing implementors to
   * provide a list of tracking values from the commit message using custom
   * extraction when the tracking values are not in the standard Key:Value
   * format in the commit message footer.
   *
   * @param commitMessage the commit message.
   * @return a list of tracking values from the commit message.
   */
  public abstract List<String> getValues(String commitMessage);
}
