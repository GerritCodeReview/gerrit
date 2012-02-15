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

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.ReceiveSession;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadSession;

import java.util.HashMap;
import java.util.Map;

/** Exposes only the non refs/changes/ reference names. */
public class ReceiveCommitsAdvertiseRefsHook implements AdvertiseRefsHook {
  private final AdvertiseRefsHook base;

  public ReceiveCommitsAdvertiseRefsHook(AdvertiseRefsHook base) {
    this.base = base != null ? base : AdvertiseRefsHook.DEFAULT;
  }

  @Override
  public void advertiseRefs(UploadSession us) {
    throw new UnsupportedOperationException(
        "ReceiveCommitsAdvertiseRefsHook cannot be used for UploadSession");
  }

  @Override
  public void advertiseRefs(ReceiveSession rs)
      throws ServiceMayNotContinueException {
    base.advertiseRefs(rs);
    HashMap<String, Ref> r = new HashMap<String, Ref>();
    for (Map.Entry<String, Ref> e : rs.getAdvertisedRefs().entrySet()) {
      if (!e.getKey().startsWith("refs/changes/")) {
        r.put(e.getKey(), e.getValue());
      }
    }
    rs.setAdvertisedRefs(r, rs.getAdvertisedObjects());
  }
}
