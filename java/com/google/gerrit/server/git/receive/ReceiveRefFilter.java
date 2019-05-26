// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.entities.RefNames.REFS_CACHE_AUTOMERGE;
import static com.google.gerrit.entities.RefNames.REFS_CHANGES;

import com.google.common.collect.Maps;
import java.util.Map;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefFilter;

class ReceiveRefFilter implements RefFilter {
  @Override
  public Map<String, Ref> filter(Map<String, Ref> refs) {
    Map<String, Ref> filteredRefs = Maps.newHashMapWithExpectedSize(refs.size());
    for (Map.Entry<String, Ref> e : refs.entrySet()) {
      String name = e.getKey();
      if (!name.startsWith(REFS_CHANGES) && !name.startsWith(REFS_CACHE_AUTOMERGE)) {
        filteredRefs.put(name, e.getValue());
      }
    }
    return filteredRefs;
  }
}
