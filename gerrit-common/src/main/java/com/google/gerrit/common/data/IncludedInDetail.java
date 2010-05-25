// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.common.data;

import java.util.Collections;
import java.util.List;

public class IncludedInDetail {
  private List<String> branches;
  private List<String> tags;

  public IncludedInDetail() {
  }

  public void setBranches(final List<String> b) {
    Collections.sort(b);
    branches = b;
  }

  public List<String> getBranches() {
    return branches;
  }

  public void setTags(final List<String> t) {
    Collections.sort(t);
    tags = t;
  }

  public List<String> getTags() {
    return tags;
  }
}
