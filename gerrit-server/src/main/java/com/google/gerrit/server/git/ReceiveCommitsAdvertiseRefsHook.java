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

package com.google.gerrit.server.git;

import com.google.common.collect.Maps;
import com.google.gerrit.server.util.MagicBranch;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.BaseReceivePack;
import org.eclipse.jgit.transport.UploadPack;

import java.util.HashMap;
import java.util.Map;

/** Exposes only the non refs/changes/ reference names. */
public class ReceiveCommitsAdvertiseRefsHook implements AdvertiseRefsHook {
  Map<String, Ref> refsToFilter = null;

  public ReceiveCommitsAdvertiseRefsHook(Map<String, Ref> refs) {
    refsToFilter = refs;
  }

  @Override
  public void advertiseRefs(UploadPack us) {
    throw new UnsupportedOperationException(
        "ReceiveCommitsAdvertiseRefsHook cannot be used for UploadPack");
  }

  @Override
  public void advertiseRefs(BaseReceivePack rp) {
    Map<String, Ref> oldRefs = rp.getAdvertisedRefs();
    if (refsToFilter == null && oldRefs == null) {
      // No filtered refs given as a starting point
      // and no refs added to the BaseReceivePack from
      // previous setAdvertisedRefs().
      refsToFilter = new HashMap<String, Ref>();
      refsToFilter.putAll(rp.getRepository().getAllRefs());
    } else if (refsToFilter == null){
      // No filtered refs given as a starting point
      // but we have refs previously added to the
      // BaseReceivePack from previous setAdvertisedRefs().
      // Let's use them.
      refsToFilter = oldRefs;
    } else if (oldRefs != null) {
      // Let's merge the previously filtered refs
      // with the oldRefs
      refsToFilter.putAll(oldRefs);
    }
    Map<String, Ref> r = Maps.newHashMapWithExpectedSize(refsToFilter.size());
    for (Map.Entry<String, Ref> e : refsToFilter.entrySet()) {
      String name = e.getKey();
      if (!skip(name)) {
        r.put(name, e.getValue());
      }
    }
    rp.setAdvertisedRefs(r, rp.getAdvertisedObjects());
  }

  private static boolean skip(String name) {
    return name.startsWith("refs/changes/")
        || name.startsWith(GitRepositoryManager.REFS_CACHE_AUTOMERGE)
        || MagicBranch.isMagicBranch(name);
  }
}
