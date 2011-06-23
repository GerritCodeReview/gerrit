// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.git;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefFilter;

import java.util.HashMap;
import java.util.Map;

public class NoRefsChangesFilter implements RefFilter {
  private final RefFilter next;

  public NoRefsChangesFilter(RefFilter next) {
    this.next = next;
  }

  @Override
  public Map<String, Ref> filter(Map<String, Ref> refs) {
    Map<String, Ref> res = new HashMap<String, Ref>();
    for (Ref ref : next.filter(refs).values()) {
      if (!ref.getName().startsWith("refs/changes/")) {
        res.put(ref.getName(), ref);
      }
    }
    return res;
  }
}
