/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
