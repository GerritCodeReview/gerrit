// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.binary.Base64;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.PatchApplier;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;

/** Utility for applying a patch. */
public final class ApplyPatchUtil {

  /**
   * Applies the given patch on top of the merge tip, using the given object inserter.
   *
   * @param repo to apply the patch in
   * @param oi to operate with
   * @param input the patch for applying
   * @param mergeTip the tip to apply the patch on
   * @return the tree ID with the applied patch
   * @throws IOException if unable to create the jgit PatchApplier object
   * @throws RestApiException for any other failure
   */
  public static ObjectId applyPatch(
      Repository repo, ObjectInserter oi, ApplyPatchInput input, RevCommit mergeTip)
      throws IOException, RestApiException {
    checkNotNull(mergeTip);
    RevTree tip = mergeTip.getTree();
    InputStream patchStream;
    if (Base64.isBase64(input.patch)) {
      patchStream = new ByteArrayInputStream(org.eclipse.jgit.util.Base64.decode(input.patch));
    } else {
      patchStream = new ByteArrayInputStream(input.patch.getBytes(StandardCharsets.UTF_8));
    }
    try {
      PatchApplier applier = new PatchApplier(repo, tip, oi);
      PatchApplier.Result applyResult = applier.applyPatch(patchStream);
      return applyResult.getTreeId();
    } catch (PatchFormatException e) {
      throw new BadRequestException("Invalid patch format: " + input.patch, e);
    } catch (PatchApplyException e) {
      throw RestApiException.wrap("Cannot apply patch: " + input.patch, e);
    }
  }

  private ApplyPatchUtil() {}
}
