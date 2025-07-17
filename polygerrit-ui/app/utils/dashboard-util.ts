/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeConfigInfo, ChangeInfo} from '../api/rest-api';
import {UserId} from '../types/common';

export interface DashboardSection {
  name: string;
  query: string;
  suffixForDashboard?: string;
  selfOnly?: boolean;
  hideIfEmpty?: boolean;
  results?: ChangeInfo[];
}

const USER_PLACEHOLDER_PATTERN = /\${user}/g;

export interface UserDashboardConfig {
  change?: ChangeConfigInfo;
}

export interface UserDashboard {
  title?: string;
  sections: DashboardSection[];
}

// NOTE: These queries are tested in Java. Any changes made to definitions
// here require corresponding changes to:
// java/com/google/gerrit/httpd/raw/IndexPreloadingUtil.java
const HAS_DRAFTS: DashboardSection = {
  // Changes with unpublished draft comments. This section is omitted when
  // viewing other users, so we don't need to filter anything out.
  name: 'Has draft comments',
  query: 'has:draft',
  selfOnly: true,
  hideIfEmpty: true,
  suffixForDashboard: 'limit:10',
};

export const YOUR_TURN: DashboardSection = {
  // Changes where the user is in the attention set.
  name: 'Your turn',
  query: 'attention:${user}',
  hideIfEmpty: false,
  suffixForDashboard: 'limit:25',
};

const WIP: DashboardSection = {
  // WIP open changes owned by viewing user. This section is omitted when
  // viewing other users, so we don't need to filter anything out.
  name: 'Work in progress',
  query: 'is:open owner:${user} is:wip',
  selfOnly: true,
  hideIfEmpty: true,
  suffixForDashboard: 'limit:25',
};

export const OUTGOING: DashboardSection = {
  // Non-WIP open changes owned by viewed user.
  name: 'Outgoing reviews',
  query: 'is:open owner:${user} -is:wip',
  suffixForDashboard: 'limit:25',
};

const INCOMING: DashboardSection = {
  // Non-WIP open changes not owned by the viewed user, that the viewed user
  // is associated with as a reviewer.
  name: 'Incoming reviews',
  query: 'is:open -owner:${user} -is:wip reviewer:${user}',
  suffixForDashboard: 'limit:25',
};

const CCED: DashboardSection = {
  // Open changes the viewed user is CCed on.
  name: 'CCed on',
  query: 'is:open -is:wip cc:${user}',
  suffixForDashboard: 'limit:10',
};

export const CLOSED: DashboardSection = {
  name: 'Recently closed',
  // Closed changes where viewed user is owner or reviewer.
  // WIP changes not owned by the viewing user (the one instance of
  // 'owner:self' is intentional and implements this logic) are filtered out.
  query:
    'is:closed (-is:wip OR owner:self) ' +
    '(owner:${user} OR reviewer:${user} OR cc:${user})',
  suffixForDashboard: '-age:4w limit:10',
};

const DEFAULT_SECTIONS: DashboardSection[] = [
  HAS_DRAFTS,
  YOUR_TURN,
  WIP,
  OUTGOING,
  INCOMING,
  CCED,
  CLOSED,
];

export function getUserDashboard(
  user: UserId | 'self' = 'self',
  sections = DEFAULT_SECTIONS,
  title = ''
): UserDashboard {
  sections = sections
    .filter(section => user === 'self' || !section.selfOnly)
    .map(section => {
      return {
        ...section,
        name: section.name,
        // user is usually account_id which is type number
        query: section.query.replace(USER_PLACEHOLDER_PATTERN, String(user)),
      };
    });
  return {title, sections};
}
