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

import static org.eclipse.jgit.lib.RefDatabase.ALL;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.BaseReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advertises part of history to git push clients.
 *
 * <p>This is a hack to work around the lack of negotiation in the send-pack/receive-pack wire
 * protocol.
 *
 * <p>When the server is frequently advancing master by creating merge commits, the client may not
 * be able to discover a common ancestor during push. Attempting to push will re-upload a very large
 * amount of history. This hook hacks in a fake negotiation replacement by walking history and
 * sending recent commits as {@code ".have"} lines in the wire protocol, allowing the client to find
 * a common ancestor.
 */
public class HackPushNegotiateHook implements AdvertiseRefsHook {
  private static final Logger log = LoggerFactory.getLogger(HackPushNegotiateHook.class);

  /** Size of an additional ".have" line. */
  private static final int HAVE_LINE_LEN = 4 + Constants.OBJECT_ID_STRING_LENGTH + 1 + 5 + 1;

  /**
   * Maximum number of bytes to "waste" in the advertisement with a peek at this repository's
   * current reachable history.
   */
  private static final int MAX_EXTRA_BYTES = 8192;

  /**
   * Number of recent commits to advertise immediately, hoping to show a client a nearby merge base.
   */
  private static final int BASE_COMMITS = 64;

  /** Number of commits to skip once base has already been shown. */
  private static final int STEP_COMMITS = 16;

  /** Total number of commits to extract from the history. */
  private static final int MAX_HISTORY = MAX_EXTRA_BYTES / HAVE_LINE_LEN;

  @Override
  public void advertiseRefs(UploadPack us) {
    throw new UnsupportedOperationException("HackPushNegotiateHook cannot be used for UploadPack");
  }

  @Override
  public void advertiseRefs(BaseReceivePack rp) throws ServiceMayNotContinueException {
    Map<String, Ref> r = rp.getAdvertisedRefs();
    if (r == null) {
      try {
        r = rp.getRepository().getRefDatabase().getRefs(ALL);
      } catch (ServiceMayNotContinueException e) {
        throw e;
      } catch (IOException e) {
        ServiceMayNotContinueException ex = new ServiceMayNotContinueException();
        ex.initCause(e);
        throw ex;
      }
    }
    rp.setAdvertisedRefs(r, history(r.values(), rp));
  }

  private Set<ObjectId> history(Collection<Ref> refs, BaseReceivePack rp) {
    Set<ObjectId> alreadySending = rp.getAdvertisedObjects();
    if (alreadySending.isEmpty()) {
      alreadySending = idsOf(refs);
    }

    int max = MAX_HISTORY - Math.max(0, alreadySending.size() - refs.size());
    if (max <= 0) {
      return Collections.emptySet();
    }

    // Scan history until the advertisement is full.
    RevWalk rw = rp.getRevWalk();
    rw.reset();
    try {
      for (Ref ref : refs) {
        try {
          if (ref.getObjectId() != null) {
            rw.markStart(rw.parseCommit(ref.getObjectId()));
          }
        } catch (IOException badCommit) {
          continue;
        }
      }

      Set<ObjectId> history = Sets.newHashSetWithExpectedSize(max);
      try {
        int stepCnt = 0;
        for (RevCommit c; history.size() < max && (c = rw.next()) != null; ) {
          if (c.getParentCount() <= 1
              && !alreadySending.contains(c)
              && (history.size() < BASE_COMMITS || (++stepCnt % STEP_COMMITS) == 0)) {
            history.add(c);
          }
        }
      } catch (IOException err) {
        log.error("error trying to advertise history", err);
      }
      return history;
    } finally {
      rw.reset();
    }
  }

  private static Set<ObjectId> idsOf(Collection<Ref> refs) {
    Set<ObjectId> r = Sets.newHashSetWithExpectedSize(refs.size());
    for (Ref ref : refs) {
      if (ref.getObjectId() != null) {
        r.add(ref.getObjectId());
      }
    }
    return r;
  }
}
