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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.patch.DiffUtil;
import com.google.gerrit.server.util.CommitMessageUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.PatchApplier;
import org.eclipse.jgit.revwalk.FooterLine;
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
    InputStream patchStream =
        new ByteArrayInputStream(decodeIfNecessary(input.patch).getBytes(StandardCharsets.UTF_8));
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

  /**
   * Build commit message for commits with applied patch.
   *
   * <p>Message structure:
   *
   * <ol>
   *   <li>Provided {@code inputMessage} or default one.
   *   <li>In case the result change's patch is not the same as the original patch - a warning
   *       message which also includes the diff.
   *   <li>In case of no specified {@code inputMessage}, or an inconsistency as described in (2) -
   *       the patch's header, or the full patch if there's no header.
   *   <li>The provided footer lines, if any.
   * </ol>
   *
   * @param inputMessage the first message peace
   * @param footerLines footer lines to append to the message
   * @param originalPatch to compare the result patch to
   * @param resultPatch to validate accuracy for
   * @return the commit message
   * @throws BadRequestException if the commit message cannot be sanitized
   */
  public static String buildCommitMessage(
      @Nullable String inputMessage,
      List<FooterLine> footerLines,
      String originalPatch,
      String resultPatch)
      throws BadRequestException {
    String decodedOriginalPatch = decodeIfNecessary(originalPatch);
    StringBuilder res =
        new StringBuilder(inputMessage != null ? inputMessage.trim() : "Applying patch.");
    Optional<String> patchDiff = verifyAppliedPatch(decodedOriginalPatch, resultPatch);
    if (!patchDiff.isEmpty()) {
      res.append(
          "\n\nNOTE FOR REVIEWERS - original patch and result patch are not identical."
              + "\nPLEASE REVIEW CAREFULLY.\nDiffs between the patches:\n"
              + patchDiff.get());
    }
    Optional<String> originalPatchHeader = DiffUtil.getPatchHeader(decodedOriginalPatch);
    if (inputMessage == null || !patchDiff.isEmpty()) {
      res.append(
          "\n\nOriginal patch:\n"
              + (originalPatchHeader.isEmpty() ? decodedOriginalPatch : originalPatchHeader.get()));
    }
    if (!footerLines.isEmpty()) {
      res.append('\n');
    }
    for (FooterLine footer : footerLines) {
      res.append("\n" + footer.toString());
    }
    return CommitMessageUtil.checkAndSanitizeCommitMessage(res.toString());
  }

  /**
   * Fetch the patch of the result tree.
   *
   * @param repo in which the patch was applied
   * @param reader for the repo objects, including {@code resultTree}
   * @param baseCommit to generate patch against
   * @param resultTree to generate the patch for
   * @return the result patch
   * @throws IOException if the result patch cannot be written
   */
  public static String getResultPatch(
      Repository repo, ObjectReader reader, RevCommit baseCommit, RevTree resultTree)
      throws IOException {
    try (OutputStream resultPatchStream = new ByteArrayOutputStream()) {
      DiffUtil.getFormattedDiff(
          repo, reader, baseCommit.getTree(), resultTree, null, resultPatchStream);
      return resultPatchStream.toString();
    }
  }

  private static Optional<String> verifyAppliedPatch(String originalPatch, String resultPatch) {
    String cleanOriginalPatch = DiffUtil.cleanPatch(originalPatch);
    String cleanResultPatch = DiffUtil.cleanPatch(resultPatch);
    if (cleanOriginalPatch.equals(cleanResultPatch)) {
      return Optional.empty();
    }
    return Optional.of(StringUtils.difference(cleanOriginalPatch, cleanResultPatch));
  }

  private static String decodeIfNecessary(String patch) {
    if (Base64.isBase64(patch)) {
      return new String(org.eclipse.jgit.util.Base64.decode(patch), StandardCharsets.UTF_8);
    }
    return patch;
  }

  private ApplyPatchUtil() {}
}
