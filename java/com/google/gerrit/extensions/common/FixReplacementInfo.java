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

import com.google.gerrit.extensions.client.Comment;
import java.util.Objects;

public class FixReplacementInfo {
  public String path;
  public Comment.Range range;
  public String replacement;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FixReplacementInfo)) {
      return false;
    }
    FixReplacementInfo fs = (FixReplacementInfo) o;
    return Objects.equals(path, fs.path)
        && Objects.equals(range, fs.range)
        && Objects.equals(replacement, fs.replacement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, range, replacement);
  }
}
