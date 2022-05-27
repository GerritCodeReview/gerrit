/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ParsedChangeInfo} from '../types/types';

export enum Metadata {
  OWNER = 'Owner',
  REVIEWERS = 'Reviewers',
  REPO_BRANCH = 'Repo | Branch',
  SUBMITTED = 'Submitted',
  PARENT = 'Parent',
  MERGED_AS = 'Merged as',
  REVERT_CREATED_AS = 'Revert Created as',
  STRATEGY = 'Strategy',
  UPDATED = 'Updated',
  CC = 'CC',
  HASHTAGS = 'Hashtags',
  TOPIC = 'Topic',
  UPLOADER = 'Uploader',
  AUTHOR = 'Author',
  COMMITTER = 'Committer',
  CHERRY_PICK_OF = 'Cherry pick of',
}

export const DisplayRules = {
  ALWAYS_SHOW: [
    Metadata.OWNER,
    Metadata.REVIEWERS,
    Metadata.REPO_BRANCH,
    Metadata.SUBMITTED,
    Metadata.TOPIC,
  ],
  SHOW_IF_SET: [
    Metadata.CC,
    Metadata.HASHTAGS,
    Metadata.UPLOADER,
    Metadata.AUTHOR,
    Metadata.COMMITTER,
    Metadata.CHERRY_PICK_OF,
  ],
  ALWAYS_HIDE: [
    Metadata.PARENT,
    Metadata.MERGED_AS,
    Metadata.REVERT_CREATED_AS,
    Metadata.STRATEGY,
    Metadata.UPDATED,
  ],
};

export function isSectionSet(section: Metadata, change?: ParsedChangeInfo) {
  switch (section) {
    case Metadata.CC:
      return !!change?.reviewers?.CC?.length;
    case Metadata.HASHTAGS:
      return !!change?.hashtags?.length;
    case Metadata.TOPIC:
      return !!change?.topic;
    case Metadata.UPLOADER:
    case Metadata.AUTHOR:
    case Metadata.COMMITTER:
      return false;
    case Metadata.CHERRY_PICK_OF:
      return !!change?.cherry_pick_of_change;
  }
  return true;
}
