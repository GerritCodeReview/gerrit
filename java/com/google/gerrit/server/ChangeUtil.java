// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Ordering;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.inject.Singleton;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class ChangeUtil {
  public static final int TOPIC_MAX_LENGTH = 2048;

  private static final Random UUID_RANDOM = new SecureRandom();
  private static final BaseEncoding UUID_ENCODING = BaseEncoding.base16().lowerCase();

  public static final Ordering<PatchSet> PS_ID_ORDER =
      Ordering.from(comparingInt(PatchSet::getPatchSetId));

  /** @return a new unique identifier for change message entities. */
  public static String messageUuid() {
    byte[] buf = new byte[8];
    UUID_RANDOM.nextBytes(buf);
    return UUID_ENCODING.encode(buf, 0, 4) + '_' + UUID_ENCODING.encode(buf, 4, 4);
  }

  /**
   * Get the next patch set ID from a previously-read map of all refs.
   *
   * @param allRefs map of full ref name to ref.
   * @param id previous patch set ID.
   * @return next unused patch set ID for the same change, skipping any IDs whose corresponding ref
   *     names appear in the {@code allRefs} map.
   */
  public static PatchSet.Id nextPatchSetIdFromAllRefsMap(Map<String, Ref> allRefs, PatchSet.Id id) {
    PatchSet.Id next = nextPatchSetId(id);
    while (allRefs.containsKey(next.toRefName())) {
      next = nextPatchSetId(next);
    }
    return next;
  }

  /**
   * Get the next patch set ID from a previously-read map of refs below the change prefix.
   *
   * @param changeRefNames existing full change ref names with the same change ID as {@code id}.
   * @param id previous patch set ID.
   * @return next unused patch set ID for the same change, skipping any IDs whose corresponding ref
   *     names appear in the {@code changeRefs} map.
   */
  public static PatchSet.Id nextPatchSetIdFromChangeRefs(
      Collection<String> changeRefNames, PatchSet.Id id) {
    return nextPatchSetIdFromChangeRefs(changeRefNames.stream(), id);
  }

  private static PatchSet.Id nextPatchSetIdFromChangeRefs(
      Stream<String> changeRefNames, PatchSet.Id id) {
    Set<PatchSet.Id> existing =
        changeRefNames
            .map(PatchSet.Id::fromRef)
            .filter(psId -> psId != null && psId.changeId().equals(id.changeId()))
            .collect(toSet());
    PatchSet.Id next = nextPatchSetId(id);
    while (existing.contains(next)) {
      next = nextPatchSetId(next);
    }
    return next;
  }

  /**
   * Get the next patch set ID just looking at a single previous patch set ID.
   *
   * <p>This patch set ID may or may not be available in the database; callers that want a
   * previously-unused ID should use {@link #nextPatchSetIdFromAllRefsMap} or {@link
   * #nextPatchSetIdFromChangeRefs}.
   *
   * @param id previous patch set ID.
   * @return next patch set ID for the same change, incrementing by 1.
   */
  public static PatchSet.Id nextPatchSetId(PatchSet.Id id) {
    return PatchSet.id(id.changeId(), id.get() + 1);
  }

  /**
   * Get the next patch set ID from scanning refs in the repo.
   *
   * @param git repository to scan for patch set refs.
   * @param id previous patch set ID.
   * @return next unused patch set ID for the same change, skipping any IDs whose corresponding ref
   *     names appear in the repository.
   */
  public static PatchSet.Id nextPatchSetId(Repository git, PatchSet.Id id) throws IOException {
    return nextPatchSetIdFromChangeRefs(
        git.getRefDatabase().getRefsByPrefix(id.changeId().toRefPrefix()).stream()
            .map(Ref::getName),
        id);
  }

  public static String status(Change c) {
    return c != null ? c.getStatus().name().toLowerCase() : "deleted";
  }

  private ChangeUtil() {}
}
