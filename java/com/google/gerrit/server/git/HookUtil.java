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

package com.google.gerrit.server.git;

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;

/** Static utilities for writing git protocol hooks. */
public class HookUtil {
  /**
   * Scan and advertise all refs in the repo if refs have not already been advertised; otherwise,
   * just return the advertised map.
   *
   * @param rp receive-pack handler.
   * @return map of refs that were advertised.
   * @throws ServiceMayNotContinueException if a problem occurred.
   */
  public static Map<String, Ref> ensureAllRefsAdvertised(ReceivePack rp)
      throws ServiceMayNotContinueException {
    Map<String, Ref> refs = rp.getAdvertisedRefs();
    if (refs != null) {
      return refs;
    }
    try {
      refs =
          rp.getRepository().getRefDatabase().getRefs().stream()
              .collect(toMap(Ref::getName, r -> r));
    } catch (ServiceMayNotContinueException e) {
      throw e;
    } catch (IOException e) {
      throw new ServiceMayNotContinueException(e);
    }
    rp.setAdvertisedRefs(refs, rp.getAdvertisedObjects());
    return refs;
  }

  /**
   * Scan and advertise all refs in the repo if refs have not already been advertised; otherwise,
   * just return the advertised map.
   *
   * @param up upload-pack handler.
   * @return map of refs that were advertised.
   * @throws ServiceMayNotContinueException if a problem occurred.
   */
  public static Map<String, Ref> ensureAllRefsAdvertised(UploadPack up)
      throws ServiceMayNotContinueException {
    Map<String, Ref> refs = up.getAdvertisedRefs();
    if (refs != null) {
      return refs;
    }
    try {
      refs =
          up.getRepository().getRefDatabase().getRefs().stream()
              .collect(toMap(Ref::getName, r -> r));
    } catch (ServiceMayNotContinueException e) {
      throw e;
    } catch (IOException e) {
      throw new ServiceMayNotContinueException(e);
    }
    up.setAdvertisedRefs(refs);
    return refs;
  }

  private HookUtil() {}
}
