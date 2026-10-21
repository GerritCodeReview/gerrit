/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AccountInfo, ChangeInfo, FlowStageInfo} from '../api/rest-api';
import {ParsedChangeInfo} from '../types/types';
import {
  changeIsAbandoned,
  changeIsMerged,
  getCurrentRevision,
} from './change-util';
import {capitalizeFirstLetter} from './string-util';

export const STAGE_SEPARATOR = ';';
export const EMAIL_PATTERN = /\S+@\S+\.\S+/;

/**
 * Matches the base change path prefix up to the change number (e.g. `/c/project/+/123`),
 * ignoring any trailing patchsets, diff ranges, comment IDs, or file paths.
 *
 * @see RoutePattern.CHANGE in `gr-router.ts` for corresponding route pattern.
 */
export const CHANGE_PREFIX_PATTERN = /^(.*?\/(?:c\/.+?\/\+)\/\d+)/;

/**
 * Returns the change URL prefix (e.g. `http://host/c/project/+/123`), stripping
 * patchset numbers, diff ranges, comment IDs, and file paths from pathname.
 * If pathname does not contain `/c/.../+/<num>` and `change` is provided,
 * falls back to `/c/${change.project}/+/${change._number}`.
 */
export function getChangePrefix(
  change?: Pick<ChangeInfo, 'project' | '_number'>,
  origin = window.location.origin,
  pathname = window.location.pathname
): string {
  const match = pathname.match(CHANGE_PREFIX_PATTERN);
  const resolvedPath =
    match?.[1] ??
    (change?.project && change?._number
      ? `/c/${change.project}/+/${change._number}`
      : pathname);
  return origin + resolvedPath;
}

/**
 * Formats a Gerrit query into a full flow stage condition string
 * (`<changePrefix> is <query>`).
 */
export function buildFlowCondition(
  query: string,
  change?: Pick<ChangeInfo, 'project' | '_number'>,
  origin = window.location.origin,
  pathname = window.location.pathname
): string {
  return `${getChangePrefix(change, origin, pathname)} is ${query}`;
}

function accountsMatch(a?: AccountInfo, b?: AccountInfo): boolean {
  if (!a || !b) return false;
  if (a._account_id !== undefined && b._account_id !== undefined) {
    return a._account_id === b._account_id;
  }
  if (a.email && b.email) {
    return a.email.toLowerCase() === b.email.toLowerCase();
  }
  return false;
}

/**
 * Returns whether the given user account is allowed to create a flow on the
 * change (the change must be open, and the user must be both the change owner
 * and the latest patchset uploader).
 */
export function canUserCreateFlow(
  change?: ChangeInfo | ParsedChangeInfo,
  account?: AccountInfo
): boolean {
  if (
    !change?.owner ||
    !account ||
    changeIsMerged(change) ||
    changeIsAbandoned(change)
  ) {
    return false;
  }
  if (!accountsMatch(change.owner, account)) return false;
  const uploader = getCurrentRevision(change)?.uploader;
  return !uploader || accountsMatch(uploader, account);
}

export interface Stage {
  condition: string;
  action: string;
  parameterStr: string;
}

export function computeFlowString(stages: Stage[]) {
  const stageToString = (stage: Stage) => {
    if (stage.action) {
      if (stage.parameterStr) {
        return `${stage.condition} -> ${stage.action} ${stage.parameterStr}`;
      }
      return `${stage.condition} -> ${stage.action}`;
    }
    return stage.condition;
  };
  return stages.map(stageToString).join(STAGE_SEPARATOR);
}

export function computeFlowStringFromFlowStageInfo(stages: FlowStageInfo[]) {
  return computeFlowString(
    stages.map(s => {
      return {
        condition: s.expression.condition,
        action: s.expression.action?.name ?? '',
        parameterStr: s.expression.action?.parameters?.join(' ') ?? '',
      };
    })
  );
}

/**
 * Formats a flow action name for display.
 * Converts snake_case (e.g., 'add_reviewer') to Title Case (e.g., 'Add Reviewer').
 */
export function formatActionName(name?: string): string {
  if (!name) {
    return '';
  }
  return name
    .split('_')
    .map(word => capitalizeFirstLetter(word))
    .join(' ');
}
