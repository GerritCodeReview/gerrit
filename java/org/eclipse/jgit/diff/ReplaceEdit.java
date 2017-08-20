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

package org.eclipse.jgit.diff;

import java.util.List;

public class ReplaceEdit extends Edit {
  private List<Edit> internalEdit;

  public ReplaceEdit(int as, int ae, int bs, int be, List<Edit> internal) {
    super(as, ae, bs, be);
    internalEdit = internal;
  }

  public ReplaceEdit(Edit orig, List<Edit> internal) {
    super(orig.getBeginA(), orig.getEndA(), orig.getBeginB(), orig.getEndB());
    internalEdit = internal;
  }

  public List<Edit> getInternalEdits() {
    return internalEdit;
  }
}
