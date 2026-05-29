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

package com.google.gerrit.server.patch;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.util.CommitMessageUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.Patch;
import org.eclipse.jgit.patch.PatchApplier;
import org.eclipse.jgit.patch.PatchApplier.Result.Error;
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
  public static PatchApplier.Result applyPatch(
      Repository repo, ObjectInserter oi, ApplyPatchInput input, RevCommit mergeTip)
      throws IOException, RestApiException {
    checkNotNull(mergeTip);
    RevTree tip = mergeTip.getTree();
    Patch patch = new Patch();
    try (InputStream patchStream =
        new ByteArrayInputStream(decodeIfNecessary(input.patch).getBytes(UTF_8))) {
      patch.parse(patchStream);
      if (!patch.getErrors().isEmpty()) {
        throw new BadRequestException(
            "Invalid patch format. Got the following errors:\n"
                + patch.getErrors().stream()
                    .map(Objects::toString)
                    .collect(Collectors.joining("\n"))
                + "\nFor the patch:\n"
                + input.patch);
      }
    }
    try {
      PatchApplier applier = new PatchApplier(repo, tip, oi);
      if (Boolean.TRUE.equals(input.allowConflicts)) {
        applier.allowConflicts();
      }
      PatchApplier.Result applyResult = applier.applyPatch(patch);
      return applyResult;
    } catch (IOException e) {
      throw RestApiException.wrap("Cannot apply patch: " + input.patch, e);
    }
  }

  /**
   * Build commit message for commits with applied patch.
   *
   * <p>Message structure:
   *
   * <ol>
   *   <li>Provided {@code message}.
   *   <li>In case of errors while applying the patch - a warning message which includes the errors;
   *       as well as the original patch's header if available, or the full original patch
   *       otherwise.
   *   <li>If there are no explicit errors, but the result change's patch is not the same as the
   *       original patch - a warning message which includes the diff; as well as the original
   *       patch's header if available, or the full original patch otherwise.
   *   <li>The provided {@code footerLines}, if any.
   * </ol>
   *
   * @param message the first message piece, excluding footers
   * @param footerLines footer lines to append to the message
   * @param patchInput API input that triggered this action
   * @param resultPatch to validate accuracy for
   * @return the commit message
   * @throws BadRequestException if the commit message cannot be sanitized
   */
  public static String buildCommitMessage(
      String message,
      List<FooterLine> footerLines,
      ApplyPatchInput patchInput,
      String resultPatch,
      List<PatchApplier.Result.Error> errors)
      throws BadRequestException {
    StringBuilder res = new StringBuilder(message.trim());

    boolean appendOriginalPatch = false;
    boolean appendResultPatch = false;
    String decodedOriginalPatch = decodeIfNecessary(patchInput.patch);
    if (!errors.isEmpty()) {
      if (errors.stream().allMatch(Error::isGitConflict)) {
        res.append(
            "\n\nATTENTION: Conflicts occurred while applying the patch.\n"
                + "Please resolve conflict markers.");
      } else {
        res.append(
            "\n\nNOTE FOR REVIEWERS - errors occurred while applying the patch."
                + "\nPLEASE REVIEW CAREFULLY.\nErrors:\n"
                + errors.stream().map(Objects::toString).collect(Collectors.joining("\n")));
        appendOriginalPatch = true;
      }
    } else {
      // Only surface the diff if no explicit errors occurred.
      Optional<String> patchDiff = verifyAppliedPatch(decodedOriginalPatch, resultPatch);
      if (!patchDiff.isEmpty()) {
        res.append(
            "\n\nNOTE FOR REVIEWERS - original patch and result patch are not identical."
                + "\nPLEASE REVIEW CAREFULLY.\nDiffs between the patches:\n "
                + patchDiff.get());
        appendOriginalPatch = true;
        appendResultPatch = true;
      }
    }

    if (appendOriginalPatch) {
      Optional<String> originalPatchHeader = DiffUtil.getPatchHeader(decodedOriginalPatch);
      String patchDescription =
          (originalPatchHeader.isEmpty() ? decodedOriginalPatch : originalPatchHeader.get()).trim();
      res.append("\n\nOriginal patch:\n ");
      if (patchDescription.length() <= 1024) {
        res.append(patchDescription);
      } else {
        res.append(
            patchDescription.substring(0, 1024)
                + "\n[[[Original patch trimmed due to size. Decoded string size: "
                + patchDescription.length()
                + ". Decoded string SHA1: "
                + sha1(patchDescription)
                + ".]]]");
      }
    }
    if (appendResultPatch) {
      res.append("\n\nResult patch:\n ");
      if (resultPatch.length() <= 1024) {
        res.append(resultPatch);
      } else {
        res.append(
            resultPatch.substring(0, 1024)
                + "\n[[[Result patch trimmed due to size. Decoded string size: "
                + resultPatch.length()
                + ". Decoded string SHA1: "
                + sha1(resultPatch)
                + ".]]]");
      }
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
    String cleanOriginalPatch = DiffUtil.normalizePatchForComparison(originalPatch);
    String cleanResultPatch = DiffUtil.normalizePatchForComparison(resultPatch);
    if (cleanOriginalPatch.equals(cleanResultPatch)) {
      return Optional.empty();
    }
    return Optional.of(
        StringUtils.difference(
            cleanOriginalPatch.replaceAll("\n", "\n> "),
            cleanResultPatch.replaceAll("\n", "\n> ")));
  }

  private static String decodeIfNecessary(String patch) {
    if (Base64.isBase64(patch.getBytes(UTF_8))) {
      try {
        return new String(org.eclipse.jgit.util.Base64.decode(patch), UTF_8);
      } catch (IllegalArgumentException e) {
        // It's possible that all the chars in the patch are valid Base64 chars, but the full string
        // is not a valid Base64 string as expected by jGit. In this case, we assume the patch is
        // already unencoded.
        return patch;
      }
    }
    return patch;
  }

  @SuppressWarnings("deprecation")
  @VisibleForTesting
  public static HashCode sha1(String s) {
    return Hashing.sha1().hashString(s, UTF_8);
  }

  private ApplyPatchUtil() {}
}
