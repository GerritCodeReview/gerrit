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
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class ChangeUtil {
  public static final int TOPIC_MAX_LENGTH = 2048;

  private static final Random UUID_RANDOM = new SecureRandom();
  private static final BaseEncoding UUID_ENCODING = BaseEncoding.base16().lowerCase();

  public static final Ordering<PatchSet> PS_ID_ORDER =
      Ordering.from(comparingInt(PatchSet::number));

  private final DynamicItem<UrlFormatter> urlFormatter;
  private final boolean enableLinkChangeIdFooters;

  @Inject
  ChangeUtil(DynamicItem<UrlFormatter> urlFormatter, @GerritServerConfig Config config) {
    this.urlFormatter = urlFormatter;
    this.enableLinkChangeIdFooters =
        config.getBoolean("receive", "enableChangeIdLinkFooters", true);
  }

  /** Returns a new unique identifier for change message entities. */
  public static String messageUuid() {
    byte[] buf = new byte[8];
    UUID_RANDOM.nextBytes(buf);
    return UUID_ENCODING.encode(buf, 0, 4) + '_' + UUID_ENCODING.encode(buf, 4, 4);
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
   * <p>This patch set ID may or may not be available in the database.
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

  /**
   * Make sure that the change commit message has a correct footer.
   *
   * @param requireChangeId true if Change-Id is a mandatory footer for the project
   * @param currentChangeId current Change-Id value before the commit message is updated
   * @param newCommitMessage new commit message for the change
   * @throws ResourceConflictException if the new commit message has a missing or invalid Change-Id
   * @throws BadRequestException if the new commit message is null or empty
   */
  public void ensureChangeIdIsCorrect(
      boolean requireChangeId, String currentChangeId, String newCommitMessage)
      throws ResourceConflictException, BadRequestException {
    RevCommit revCommit =
        RevCommit.parse(
            Constants.encode("tree " + ObjectId.zeroId().name() + "\n\n" + newCommitMessage));

    // Check that the commit message without footers is not empty
    CommitMessageUtil.checkAndSanitizeCommitMessage(revCommit.getShortMessage());

    List<String> changeIdFooters = getChangeIdsFromFooter(revCommit);
    if (requireChangeId && changeIdFooters.isEmpty()) {
      throw new ResourceConflictException("missing Change-Id footer");
    }
    if (!changeIdFooters.isEmpty() && !changeIdFooters.get(0).equals(currentChangeId)) {
      throw new ResourceConflictException("wrong Change-Id footer");
    }
    if (changeIdFooters.size() > 1) {
      throw new ResourceConflictException("multiple Change-Id footers");
    }
  }

  public static String status(Change c) {
    return c != null ? c.getStatus().name().toLowerCase(Locale.US) : "deleted";
  }

  private static final Pattern LINK_CHANGE_ID_PATTERN = Pattern.compile("I[0-9a-f]{40}");

  public List<String> getChangeIdsFromFooter(RevCommit c) {
    List<String> changeIds = c.getFooterLines(FooterConstants.CHANGE_ID);
    if (!enableLinkChangeIdFooters) {
      return changeIds;
    }

    Optional<String> webUrl = urlFormatter.get().getWebUrl();
    if (!webUrl.isPresent()) {
      return changeIds;
    }

    String prefix = webUrl.get() + "id/";
    for (String link : c.getFooterLines(FooterConstants.LINK)) {
      if (!link.startsWith(prefix)) {
        continue;
      }
      String changeId = link.substring(prefix.length());
      Matcher m = LINK_CHANGE_ID_PATTERN.matcher(changeId);
      if (m.matches()) {
        changeIds.add(changeId);
      }
    }

    return changeIds;
  }
}
