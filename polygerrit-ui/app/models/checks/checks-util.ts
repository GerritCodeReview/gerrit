/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  Action,
  Category,
  CheckResult as CheckResultApi,
  CheckRun as CheckRunApi,
  Fix,
  Link,
  LinkIcon,
  Replacement,
  RunStatus,
} from '../../api/checks';
import {PatchSetNumber, RevisionPatchSetNum} from '../../api/rest-api';
import {CommentSide} from '../../constants/constants';
import {
  FixSuggestionInfo,
  FixReplacementInfo,
  DraftInfo,
} from '../../types/common';
import {OpenFixPreviewEventDetail} from '../../types/events';
import {isDefined} from '../../types/types';
import {PROVIDED_FIX_ID, createNew} from '../../utils/comment-util';
import {assert, assertIsDefined, assertNever} from '../../utils/common-util';
import {fire} from '../../utils/event-util';
import {CheckResult, CheckRun, RunResult} from './checks-model';

export interface ChecksIcon {
  name: string;
  filled?: boolean;
}

export function iconForLink(linkIcon?: LinkIcon): ChecksIcon {
  if (linkIcon === undefined) return {name: 'open_in_new'};
  switch (linkIcon) {
    case LinkIcon.EXTERNAL:
      return {name: 'open_in_new'};
    case LinkIcon.IMAGE:
      return {name: 'image', filled: true};
    case LinkIcon.HISTORY:
      return {name: 'history'};
    case LinkIcon.DOWNLOAD:
      return {name: 'download'};
    case LinkIcon.DOWNLOAD_MOBILE:
      return {name: 'system_update'};
    case LinkIcon.HELP_PAGE:
      return {name: 'help'};
    case LinkIcon.REPORT_BUG:
      return {name: 'bug_report', filled: true};
    case LinkIcon.CODE:
      return {name: 'code'};
    case LinkIcon.FILE_PRESENT:
      return {name: 'file_present'};
    default:
      // We don't throw an assertion error here, because plugins don't have to
      // be written in TypeScript, so we may encounter arbitrary strings for
      // linkIcon.
      return {name: 'open_in_new'};
  }
}

export function tooltipForLink(linkIcon?: LinkIcon) {
  if (linkIcon === undefined) return 'Link to details';
  switch (linkIcon) {
    case LinkIcon.EXTERNAL:
      return 'Link to details';
    case LinkIcon.IMAGE:
      return 'Link to image';
    case LinkIcon.HISTORY:
      return 'Link to result history';
    case LinkIcon.DOWNLOAD:
      return 'Download';
    case LinkIcon.DOWNLOAD_MOBILE:
      return 'Download';
    case LinkIcon.HELP_PAGE:
      return 'Link to help page';
    case LinkIcon.REPORT_BUG:
      return 'Link for reporting a problem';
    case LinkIcon.CODE:
      return 'Link to code';
    case LinkIcon.FILE_PRESENT:
      return 'Link to file';
    default:
      // We don't throw an assertion error here, because plugins don't have to
      // be written in TypeScript, so we may encounter arbitrary strings for
      // linkIcon.
      return 'Link to details';
  }
}

function pleaseFixMessage(result: RunResult) {
  return `Please fix this ${result.category} reported by ${result.checkName}: ${result.summary}

${result.message}`;
}

export function createPleaseFixComment(result: RunResult): DraftInfo {
  const pointer = result.codePointers?.[0];
  assertIsDefined(pointer, 'codePointer');
  return {
    ...createNew(pleaseFixMessage(result), true),
    path: pointer.path,
    patch_set: result.patchset as RevisionPatchSetNum,
    side: CommentSide.REVISION,
    line: pointer.range.end_line ?? pointer.range.start_line,
    range: pointer.range,
  };
}

export function createFixAction(
  target: EventTarget,
  result?: RunResult
): Action | undefined {
  if (!result?.patchset) return;
  if (!result?.fixes) return;
  const fixSuggestions = result.fixes
    .map(f => rectifyFix(f, result?.checkName))
    .filter(isDefined);
  if (fixSuggestions.length === 0) return;
  const eventDetail: OpenFixPreviewEventDetail = {
    patchNum: result.patchset as PatchSetNumber,
    fixSuggestions,
    onCloseFixPreviewCallbacks: [],
  };
  return {
    name: 'Show Fix',
    callback: () => {
      fire(target, 'open-fix-preview', eventDetail);
      return undefined;
    },
  };
}

export function rectifyFix(
  fix: Fix | undefined,
  checkName: string
): FixSuggestionInfo | undefined {
  if (!fix?.replacements) return undefined;
  const replacements = fix.replacements
    .map(rectifyReplacement)
    .filter(isDefined);
  if (replacements.length === 0) return undefined;

  return {
    description: fix.description ?? `Fix provided by ${checkName}`,
    fix_id: PROVIDED_FIX_ID,
    replacements,
  };
}

export function rectifyReplacement(
  r: Replacement | undefined
): FixReplacementInfo | undefined {
  if (!r?.path) return undefined;
  if (!r?.range) return undefined;
  if (r?.replacement === undefined) return undefined;
  if (!Number.isInteger(r.range.start_line)) return undefined;
  if (!Number.isInteger(r.range.end_line)) return undefined;
  if (!Number.isInteger(r.range.start_character)) return undefined;
  if (!Number.isInteger(r.range.end_character)) return undefined;
  return r;
}

export function worstCategory(run: CheckRun) {
  if (hasResultsOf(run, Category.ERROR)) return Category.ERROR;
  if (hasResultsOf(run, Category.WARNING)) return Category.WARNING;
  if (hasResultsOf(run, Category.INFO)) return Category.INFO;
  if (hasResultsOf(run, Category.SUCCESS)) return Category.SUCCESS;
  return undefined;
}

export function isCategory(
  catStat?: Category | RunStatus
): catStat is Category {
  return (
    catStat === Category.ERROR ||
    catStat === Category.WARNING ||
    catStat === Category.INFO ||
    catStat === Category.SUCCESS
  );
}

export function isStatus(catStat?: Category | RunStatus): catStat is RunStatus {
  return (
    catStat === RunStatus.COMPLETED ||
    catStat === RunStatus.RUNNABLE ||
    catStat === RunStatus.SCHEDULED ||
    catStat === RunStatus.RUNNING
  );
}

export function labelFor(catStat: Category | RunStatus) {
  switch (catStat) {
    case Category.ERROR:
      return 'error';
    case Category.INFO:
      return 'info';
    case Category.WARNING:
      return 'warning';
    case Category.SUCCESS:
      return 'success';
    case RunStatus.COMPLETED:
      return 'completed';
    case RunStatus.RUNNABLE:
      return 'runnable';
    case RunStatus.RUNNING:
      return 'running';
    case RunStatus.SCHEDULED:
      return 'scheduled';
    default:
      assertNever(catStat, `Unsupported category/status: ${catStat}`);
  }
}

export function iconFor(catStat: Category | RunStatus): ChecksIcon {
  switch (catStat) {
    case Category.ERROR:
      return {name: 'error', filled: true};
    case Category.INFO:
      return {name: 'info'};
    case Category.WARNING:
      return {name: 'warning', filled: true};
    case Category.SUCCESS:
      return {name: 'check_circle'};
    // Note that this is only for COMPLETED without results!
    case RunStatus.COMPLETED:
      return {name: 'check_circle'};
    case RunStatus.RUNNABLE:
      return {name: ''};
    case RunStatus.RUNNING:
      return {name: 'timelapse'};
    case RunStatus.SCHEDULED:
      return {name: 'pending_actions'};
    default:
      assertNever(catStat, `Unsupported category/status: ${catStat}`);
  }
}

export enum PRIMARY_STATUS_ACTIONS {
  RERUN = 'rerun',
  RUN = 'run',
}

export function toCanonicalAction(action: Action, status: RunStatus) {
  let name = action.name.toLowerCase();
  if (status === RunStatus.COMPLETED && (name === 'run' || name === 're-run')) {
    name = PRIMARY_STATUS_ACTIONS.RERUN;
  }
  return {...action, name};
}

export function headerForStatus(status: RunStatus) {
  switch (status) {
    case RunStatus.COMPLETED:
      return 'Completed';
    case RunStatus.RUNNABLE:
      return 'Not run';
    case RunStatus.RUNNING:
      return 'Running';
    case RunStatus.SCHEDULED:
      return 'Scheduled';
    default:
      assertNever(status, `Unsupported status: ${status}`);
  }
}

function primaryActionName(status: RunStatus) {
  switch (status) {
    case RunStatus.COMPLETED:
      return PRIMARY_STATUS_ACTIONS.RERUN;
    case RunStatus.RUNNABLE:
      return PRIMARY_STATUS_ACTIONS.RUN;
    case RunStatus.RUNNING:
    case RunStatus.SCHEDULED:
      return undefined;
    default:
      assertNever(status, `Unsupported status: ${status}`);
  }
}

export function primaryRunAction(run?: CheckRun): Action | undefined {
  if (!run) return undefined;
  return runActions(run).filter(
    action => !action.disabled && action.name === primaryActionName(run.status)
  )[0];
}

export function runActions(run?: CheckRun | RunResult): Action[] {
  if (!run?.actions) return [];
  return run.actions.map(action => toCanonicalAction(action, run.status));
}

export function iconForRun(run: CheckRun) {
  if (run.status !== RunStatus.COMPLETED) {
    return iconFor(run.status);
  } else {
    const category = worstCategory(run);
    return category ? iconFor(category) : iconFor(run.status);
  }
}

export function hasCompleted(run: CheckRun) {
  return run.status === RunStatus.COMPLETED;
}

export function isRunningOrScheduled(run: CheckRun) {
  return run.status === RunStatus.RUNNING || run.status === RunStatus.SCHEDULED;
}

export function isRunningScheduledOrCompleted(run: CheckRun) {
  return (
    run.status === RunStatus.COMPLETED ||
    run.status === RunStatus.RUNNING ||
    run.status === RunStatus.SCHEDULED
  );
}

export function hasCompletedWithoutResults(run: CheckRun) {
  return run.status === RunStatus.COMPLETED && (run.results ?? []).length === 0;
}

export function hasCompletedWith(run: CheckRun, category: Category) {
  return hasCompleted(run) && hasResultsOf(run, category);
}

export function hasResults(run: CheckRun): boolean {
  return (run.results ?? []).length > 0;
}

export function allResults(runs: CheckRun[]): CheckResult[] {
  return runs.reduce(
    (results: CheckResult[], run: CheckRun) => [
      ...results,
      ...(run.results ?? []),
    ],
    []
  );
}

export function hasResultsOf(run: CheckRun, category: Category) {
  return getResultsOf(run, category).length > 0;
}

export function getResultsOf(run: CheckRun, category: Category) {
  return (run.results ?? []).filter(r => r.category === category);
}

export function compareByWorstCategory(a: CheckRun, b: CheckRun) {
  const catComp = catLevel(worstCategory(b)) - catLevel(worstCategory(a));
  if (catComp !== 0) return catComp;
  const statusComp = runLevel(b.status) - runLevel(a.status);
  return statusComp;
}

function catLevel(cat?: Category) {
  if (!cat) return -1;
  switch (cat) {
    case Category.SUCCESS:
      return 0;
    case Category.INFO:
      return 1;
    case Category.WARNING:
      return 2;
    case Category.ERROR:
      return 3;
  }
}

function runLevel(status: RunStatus) {
  switch (status) {
    case RunStatus.COMPLETED:
      return 0;
    case RunStatus.RUNNABLE:
      return 1;
    case RunStatus.RUNNING:
      return 2;
    case RunStatus.SCHEDULED:
      return 3;
    default:
      assertNever(status, `Unsupported status: ${status}`);
  }
}

export interface AttemptDetail {
  attempt?: AttemptChoice;
  icon?: ChecksIcon;
}

export interface AttemptInfo {
  latestAttempt: AttemptChoice;
  isSingleAttempt: boolean;
  attempts: AttemptDetail[];
}

export type AttemptChoice = number | 'latest' | 'all';
export const ALL_ATTEMPTS = 'all' as AttemptChoice;
export const LATEST_ATTEMPT = 'latest' as AttemptChoice;

export function isAttemptChoice(x: number | string): x is AttemptChoice {
  if (typeof x === 'string') {
    return x === ALL_ATTEMPTS || x === LATEST_ATTEMPT;
  }
  if (typeof x === 'number') {
    return x >= 0;
  }
  return false;
}

export function stringToAttemptChoice(
  s?: string | null
): AttemptChoice | undefined {
  if (s === undefined) return undefined;
  if (s === null) return undefined;
  if (s === '') return undefined;
  if (isAttemptChoice(s)) return s;
  const n = Number(s);
  if (isAttemptChoice(n)) return n;
  return undefined;
}

export function attemptChoiceLabel(attempt: AttemptChoice): string {
  if (attempt === LATEST_ATTEMPT) return 'Latest Attempt';
  if (attempt === ALL_ATTEMPTS) return 'All Attempts';
  return `Attempt ${attempt}`;
}

export function sortAttemptDetails(a: AttemptDetail, b: AttemptDetail): number {
  return sortAttemptChoices(a.attempt, b.attempt);
}

export function sortAttemptChoices(
  a?: AttemptChoice,
  b?: AttemptChoice
): number {
  if (a === b) return 0;
  if (a === undefined) return -1;
  if (b === undefined) return 1;
  if (a === LATEST_ATTEMPT) return -1;
  if (b === LATEST_ATTEMPT) return 1;
  if (a === ALL_ATTEMPTS) return -1;
  if (b === ALL_ATTEMPTS) return 1;
  assert(typeof a === 'number', `unexpected attempt ${a}`);
  assert(typeof b === 'number', `unexpected attempt ${b}`);
  return a - b;
}

export function createAttemptMap(runs: CheckRunApi[]) {
  const map = new Map<string, AttemptInfo>();
  for (const run of runs) {
    const value = map.get(run.checkName);
    const detail: AttemptDetail = {
      attempt: run.attempt ?? 0,
      icon: iconForRun(fromApiToInternalRun(run)),
    };
    if (value === undefined) {
      map.set(run.checkName, {
        latestAttempt: run.attempt ?? 0,
        isSingleAttempt: true,
        attempts: [detail],
      });
      continue;
    }
    if (!run.attempt || !value.latestAttempt) {
      throw new Error(
        'If multiple run attempts are provided, ' +
          'then each run must have the "attempt" property set.'
      );
    }
    value.isSingleAttempt = false;
    if (
      value.latestAttempt !== 'all' &&
      value.latestAttempt !== 'latest' &&
      run.attempt > value.latestAttempt
    ) {
      value.latestAttempt = run.attempt;
    }
    value.attempts.push(detail);
  }
  return map;
}

export function fromApiToInternalRun(run: CheckRunApi): CheckRun {
  return {
    ...run,
    pluginName: 'fake',
    internalRunId: 'fake',
    isSingleAttempt: false,
    isLatestAttempt: false,
    attemptDetails: [],
    results: (run.results ?? []).map(fromApiToInternalResult),
  };
}

export function fromApiToInternalResult(result: CheckResultApi): CheckResult {
  return {
    ...result,
    internalResultId: 'fake',
  };
}

function allPrimaryLinks(result?: CheckResultApi): Link[] {
  return (result?.links ?? []).filter(link => link.primary);
}

export function firstPrimaryLink(result?: CheckResultApi): Link | undefined {
  return allPrimaryLinks(result).find(link => link.icon === LinkIcon.EXTERNAL);
}

export function otherPrimaryLinks(result?: CheckResultApi): Link[] {
  const first = firstPrimaryLink(result);
  return allPrimaryLinks(result).filter(link => link !== first);
}

export function secondaryLinks(result?: CheckResultApi): Link[] {
  return (result?.links ?? []).filter(link => !link.primary);
}

export function computeIsExpandable(result?: CheckResultApi) {
  if (!result?.summary) return false;
  const hasMessage = !!result?.message;
  const hasMultipleLinks = (result?.links ?? []).length > 1;
  const hasPointers = (result?.codePointers ?? []).length > 0;
  const hasFixes = (result?.fixes ?? []).length > 0;
  return hasMessage || hasMultipleLinks || hasPointers || hasFixes;
}
