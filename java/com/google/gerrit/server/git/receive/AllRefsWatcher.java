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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.git.HookUtil;
import java.util.Map;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.BaseReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;

/**
 * Hook that scans all refs and holds onto the results reference.
 *
 * <p>This allows a caller who has an {@code AllRefsWatcher} instance to get the full map of refs in
 * the repo, even if refs are filtered by a later hook or filter.
 */
class AllRefsWatcher implements AdvertiseRefsHook {
  private Map<String, Ref> allRefs;

  @Override
  public void advertiseRefs(BaseReceivePack rp) throws ServiceMayNotContinueException {
    allRefs = HookUtil.ensureAllRefsAdvertised(rp);
  }

  @Override
  public void advertiseRefs(UploadPack uploadPack) {
    throw new UnsupportedOperationException();
  }

  Map<String, Ref> getAllRefs() {
    checkState(allRefs != null, "getAllRefs() only valid after refs were advertised");
    return allRefs;
  }
}
