// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

/** Output options available for retrieval of change details. */
public enum ListChangesOption implements ListOption {
  LABELS(0),
  DETAILED_LABELS(8),

  /** Return information on the current patch set of the change. */
  CURRENT_REVISION(1),
  ALL_REVISIONS(2),

  /** If revisions are included, parse the commit object. */
  CURRENT_COMMIT(3),
  ALL_COMMITS(4),

  /** If a patch set is included, include the files of the patch set. */
  CURRENT_FILES(5),
  ALL_FILES(6),

  /** If accounts are included, include detailed account info. */
  DETAILED_ACCOUNTS(7),

  /** Include messages associated with the change. */
  MESSAGES(9),

  /** Include allowed actions client could perform. */
  CURRENT_ACTIONS(10),

  /** Set the reviewed boolean for the caller. */
  REVIEWED(11),

  /** Not used anymore, kept for backward compatibility */
  @Deprecated
  DRAFT_COMMENTS(12),

  /** Include download commands for the caller. */
  DOWNLOAD_COMMANDS(13),

  /** Include patch set weblinks. */
  WEB_LINKS(14),

  /** Include consistency check results. */
  CHECK(15),

  /** Include allowed change actions client could perform. */
  CHANGE_ACTIONS(16),

  /** Include a copy of commit messages including review footers. */
  COMMIT_FOOTERS(17),

  /** Include push certificate information along with any patch sets. */
  PUSH_CERTIFICATES(18),

  /** Include change's reviewer updates. */
  REVIEWER_UPDATES(19),

  /** Set the submittable boolean. */
  SUBMITTABLE(20),

  /** If tracking Ids are included, include detailed tracking Ids info. */
  TRACKING_IDS(21),

  /**
   * Use {@code gerrit.config} instead to turn this off for your instance. See {@code
   * change.mergeabilityComputationBehavior}.
   */
  @Deprecated
  SKIP_MERGEABLE(22),

  /**
   * Skip diffstat computation that compute the insertions field (number of lines inserted) and
   * deletions field (number of lines deleted)
   */
  SKIP_DIFFSTAT(23);

  private final int value;

  ListChangesOption(int v) {
    this.value = v;
  }

  @Override
  public int getValue() {
    return value;
  }
}
