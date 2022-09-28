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

import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.PatchApplier;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;

/** Utility for applying a patch. */
public class ApplyPatchUtil {

  /**
   * Applies the given patch on top of the merge tip, using the given object inserter.
   *
   * @param repo
   * @param oi
   * @param input
   * @param mergeTip
   * @return
   * @throws IOException
   * @throws PreconditionFailedException
   */
  public static ObjectId applyPatch(
      Repository repo, ObjectInserter oi, ApplyPatchInput input, RevCommit mergeTip)
      throws IOException, PreconditionFailedException {
    if (mergeTip == null) {
      throw new PreconditionFailedException("Cannot apply patch on top of an empty tree.");
    }
    RevTree tip = mergeTip.getTree();
    InputStream patchStream =
        new ByteArrayInputStream(input.patch.getBytes(StandardCharsets.UTF_8));
    try {
      PatchApplier applier = new PatchApplier(repo, tip, oi);
      PatchApplier.Result applyResult = applier.applyPatch(patchStream);
      return applyResult.getTreeId();
    } catch (GitAPIException e) {
      throw new IOException("Cannot apply patch: " + input.patch, e);
    }
  }
}
