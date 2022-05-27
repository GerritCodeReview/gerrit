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
  Link,
  LinkIcon,
  RunStatus,
} from '../../api/checks';
import {assertNever} from '../../utils/common-util';
import {CheckResult, CheckRun} from './checks-model';

export function iconForLink(linkIcon?: LinkIcon) {
  if (linkIcon === undefined) return 'launch';
  switch (linkIcon) {
    case LinkIcon.EXTERNAL:
      return 'launch';
    case LinkIcon.IMAGE:
      return 'insert-photo';
    case LinkIcon.HISTORY:
      return 'restore';
    case LinkIcon.DOWNLOAD:
      return 'download';
    case LinkIcon.DOWNLOAD_MOBILE:
      return 'system-update';
    case LinkIcon.HELP_PAGE:
      return 'help-outline';
    case LinkIcon.REPORT_BUG:
      return 'bug';
    case LinkIcon.CODE:
      return 'code';
    case LinkIcon.FILE_PRESENT:
      return 'file-present';
    default:
      // We don't throw an assertion error here, because plugins don't have to
      // be written in TypeScript, so we may encounter arbitrary strings for
      // linkIcon.
      return 'launch';
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

export function iconFor(catStat: Category | RunStatus) {
  switch (catStat) {
    case Category.ERROR:
      return 'error';
    case Category.INFO:
      return 'info-outline';
    case Category.WARNING:
      return 'warning';
    case Category.SUCCESS:
      return 'check-circle-outline';
    // Note that this is only for COMPLETED without results!
    case RunStatus.COMPLETED:
      return 'check-circle-outline';
    case RunStatus.RUNNABLE:
      return 'placeholder';
    case RunStatus.RUNNING:
      return 'timelapse';
    case RunStatus.SCHEDULED:
      return 'scheduled';
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

export function runActions(run?: CheckRun): Action[] {
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
  attempt: number | undefined;
  icon: string;
}

export interface AttemptInfo {
  latestAttempt: number | undefined;
  isSingleAttempt: boolean;
  attempts: AttemptDetail[];
}

export function createAttemptMap(runs: CheckRunApi[]) {
  const map = new Map<string, AttemptInfo>();
  for (const run of runs) {
    const value = map.get(run.checkName);
    const detail = {
      attempt: run.attempt,
      icon: iconForRun(fromApiToInternalRun(run)),
    };
    if (value === undefined) {
      map.set(run.checkName, {
        latestAttempt: run.attempt,
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
    if (run.attempt > value.latestAttempt) {
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
