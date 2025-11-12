// Copyright (C) 2020 The Android Open Source Project
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
//

package com.google.gerrit.server.patch;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.server.patch.diff.ModifiedFilesCache;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCache;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * A utility class used by the diff cache interfaces {@link GitModifiedFilesCache} and {@link
 * ModifiedFilesCache}.
 */
public class DiffUtil {

  /**
   * Return the {@code modifiedFiles} input list while merging rewritten entries.
   *
   * <p>Background: In some cases, JGit returns two diff entries (ADDED/DELETED, RENAMED/DELETED,
   * etc...) for the same file path. This happens e.g. when a file's mode is changed between
   * patchsets, for example converting a symlink file to a regular file. We identify this case and
   * return a single modified file with changeType = {@link ChangeType#REWRITE}.
   */
  public static ImmutableList<ModifiedFile> mergeRewrittenModifiedFiles(
      List<ModifiedFile> modifiedFiles) {
    if (modifiedFiles == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<ModifiedFile> result = ImmutableList.builder();
    ListMultimap<String, ModifiedFile> byPath = ArrayListMultimap.create();
    modifiedFiles.stream()
        .forEach(
            f -> {
              if (f.changeType() == ChangeType.DELETED) {
                byPath.get(f.oldPath().get()).add(f);
              } else {
                byPath.get(f.newPath().get()).add(f);
              }
            });
    for (String path : byPath.keySet()) {
      List<ModifiedFile> entries = byPath.get(path);
      if (entries.size() == 1) {
        result.add(entries.get(0));
      } else {
        // More than one. Return a single REWRITE entry.
        // Convert the first entry (prioritized according to change type enum order) to REWRITE
        entries.sort(Comparator.comparingInt(o -> o.changeType().ordinal()));
        result.add(entries.get(0).toBuilder().changeType(ChangeType.REWRITE).build());
      }
    }
    return result.build();
  }

  /**
   * Returns the Git tree object ID pointed to by the commitId parameter.
   *
   * @param rw a {@link RevWalk} of an opened repository that is used to walk the commit graph.
   * @param commitId 20 bytes commitId SHA-1 hash.
   * @return Git tree object ID pointed to by the commitId.
   */
  public static ObjectId getTreeId(RevWalk rw, ObjectId commitId) throws IOException {
    RevCommit current = rw.parseCommit(commitId);
    return current.getTree().getId();
  }

  /**
   * Returns the RevCommit object given the 20 bytes commitId SHA-1 hash.
   *
   * @param rw a {@link RevWalk} of an opened repository that is used to walk the commit graph.
   * @param commitId 20 bytes commitId SHA-1 hash
   * @return The RevCommit representing the commit in Git
   * @throws IOException a pack file or loose object could not be read while parsing the commits.
   */
  public static RevCommit getRevCommit(RevWalk rw, ObjectId commitId) throws IOException {
    return rw.parseCommit(commitId);
  }

  /**
   * Returns true if the commitA and commitB parameters are parent/child, if they have a common
   * parent, or if any of them is a root or merge commit.
   */
  public static boolean areRelated(RevCommit commitA, RevCommit commitB) {
    return commitA == null
        || isRootOrMergeCommit(commitA)
        || isRootOrMergeCommit(commitB)
        || areParentAndChild(commitA, commitB)
        || haveCommonParent(commitA, commitB);
  }

  public static int stringSize(String str) {
    if (str != null) {
      // each character in the string occupies 2 bytes. Ignoring the fixed overhead for the string
      // (length, offset and hash code) since they are negligible and do not affect the comparison
      // of 2 strings.
      return str.length() * 2;
    }
    return 0;
  }

  /**
   * Get formatted diff between the given commits, either for a single path if specified, or for the
   * full trees.
   *
   * @param repo to get the diff from
   * @param baseCommit to compare with
   * @param childCommit to compare
   * @param path to narrow the diff to
   * @param out to append the diff to
   * @throws IOException if the diff couldn't be written
   */
  public static void getFormattedDiff(
      Repository repo,
      RevCommit baseCommit,
      RevCommit childCommit,
      @Nullable String path,
      OutputStream out)
      throws IOException {
    getFormattedDiff(repo, null, baseCommit.getTree(), childCommit.getTree(), path, out);
  }

  public static void getFormattedDiff(
      Repository repo,
      RevCommit baseCommit,
      RevCommit childCommit,
      @Nullable String path,
      OutputStream out,
      int context)
      throws IOException {
    try (DiffFormatter fmt = new DiffFormatter(out)) {
      fmt.setRepository(repo);
      fmt.setContext(context);
      if (path != null) {
        fmt.setPathFilter(PathFilter.create(path));
      }
      fmt.format(baseCommit, childCommit);
      fmt.flush();
    }
  }

  public static void getFormattedDiff(
      Repository repo,
      @Nullable ObjectReader reader,
      RevTree baseTree,
      RevTree childTree,
      @Nullable String path,
      OutputStream out)
      throws IOException {
    try (DiffFormatter fmt = new DiffFormatter(out)) {
      fmt.setRepository(repo);
      if (reader != null) {
        fmt.setReader(reader, repo.getConfig());
      }
      if (path != null) {
        fmt.setPathFilter(PathFilter.create(path));
      }
      fmt.format(baseTree, childTree);
      fmt.flush();
    }
  }

  public static String normalizePatchForComparison(final String patch) {
    String res = removePatchHeader(patch);
    return res
        // Remove any lines which are not diff lines or file header lines - such index,
        // hunk-headers, and context lines.
        .replaceAll("(?m)^[^+-].*", "")
        .replaceAll("(?m)^[+]{3} [ab]/", "+++ ")
        .replaceAll("(?m)^-{3} [ab]/", "--- ")
        // Remove empty lines
        .replaceAll("\n+", "\n")
        // Trim
        .trim();
  }

  public static String removePatchHeader(final String patch) {
    String res = patch.trim();
    if (!res.startsWith("diff --") && res.contains("\ndiff --")) {
      return res.substring(res.indexOf("\ndiff --"));
    }
    return res;
  }

  public static Optional<String> getPatchHeader(final String patch) {
    String res = patch.trim();
    if (res.startsWith("diff ")) {
      return Optional.empty();
    }
    return Optional.ofNullable(Strings.emptyToNull(res.substring(0, res.indexOf("\ndiff "))));
  }

  public static String normalizePatchForComparison(BinaryResult bin) throws IOException {
    return normalizePatchForComparison(bin.asString());
  }

  private static boolean isRootOrMergeCommit(RevCommit commit) {
    return commit.getParentCount() != 1;
  }

  private static boolean areParentAndChild(RevCommit commitA, RevCommit commitB) {
    return ObjectId.isEqual(commitA.getParent(0), commitB)
        || ObjectId.isEqual(commitB.getParent(0), commitA);
  }

  private static boolean haveCommonParent(RevCommit commitA, RevCommit commitB) {
    return ObjectId.isEqual(commitA.getParent(0), commitB.getParent(0));
  }
}
