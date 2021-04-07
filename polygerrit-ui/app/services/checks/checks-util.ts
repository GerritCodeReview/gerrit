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
import {Action, Category, LinkIcon, RunStatus} from '../../api/checks';
import {assertNever} from '../../utils/common-util';
import {CheckResult, CheckRun} from './checks-model';

export function iconForLink(linkIcon: LinkIcon) {
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
    default:
      assertNever(linkIcon, `Unsupported LinkIcon: ${linkIcon}`);
  }
}

export function tooltipForLink(linkIcon: LinkIcon) {
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
    default:
      assertNever(linkIcon, `Unsupported LinkIcon: ${linkIcon}`);
  }
}

export function worstCategory(run: CheckRun) {
  if (hasResultsOf(run, Category.ERROR)) return Category.ERROR;
  if (hasResultsOf(run, Category.WARNING)) return Category.WARNING;
  if (hasResultsOf(run, Category.INFO)) return Category.INFO;
  return undefined;
}

export function iconForCategory(category: Category | 'SUCCESS') {
  switch (category) {
    case Category.ERROR:
      return 'error';
    case Category.INFO:
      return 'info-outline';
    case Category.WARNING:
      return 'warning';
    case 'SUCCESS':
      return 'check-circle-outline';
    default:
      assertNever(category, `Unsupported category: ${category}`);
  }
}

enum PRIMARY_STATUS_ACTIONS {
  RERUN = 'rerun',
  RUN = 'run',
  CANCEL = 'cancel',
}

export function toCanonicalAction(action: Action, status: RunStatus) {
  let name = action.name.toLowerCase();
  if (status === RunStatus.COMPLETED && (name === 'run' || name === 're-run')) {
    name = PRIMARY_STATUS_ACTIONS.RERUN;
  }
  if (status === RunStatus.RUNNING && name === 'stop') {
    name = PRIMARY_STATUS_ACTIONS.CANCEL;
  }
  return {...action, name};
}

export function primaryActionName(status: RunStatus) {
  switch (status) {
    case RunStatus.COMPLETED:
      return PRIMARY_STATUS_ACTIONS.RERUN;
    case RunStatus.RUNNABLE:
      return PRIMARY_STATUS_ACTIONS.RUN;
    case RunStatus.RUNNING:
      return PRIMARY_STATUS_ACTIONS.CANCEL;
    default:
      assertNever(status, `Unsupported status: ${status}`);
  }
}

export function primaryRunAction(run: CheckRun): Action | undefined {
  return (run.actions ?? [])
    .map(action => toCanonicalAction(action, run.status))
    .filter(action => action.name === primaryActionName(run.status))[0];
}

export function iconForRun(run: CheckRun) {
  if (run.status !== RunStatus.COMPLETED) {
    return iconForStatus(run.status);
  } else {
    const category = worstCategory(run);
    return category ? iconForCategory(category) : iconForStatus(run.status);
  }
}

export function iconForStatus(status: RunStatus) {
  switch (status) {
    // Note that this is only for COMPLETED without results!
    case RunStatus.COMPLETED:
      return 'check-circle-outline';
    case RunStatus.RUNNABLE:
      return 'placeholder';
    case RunStatus.RUNNING:
      return 'timelapse';
    default:
      assertNever(status, `Unsupported status: ${status}`);
  }
}

export function hasCompleted(run: CheckRun) {
  return run.status === RunStatus.COMPLETED;
}

export function isRunning(run: CheckRun) {
  return run.status === RunStatus.RUNNING;
}

export function isRunningOrHasCompleted(run: CheckRun) {
  return run.status === RunStatus.COMPLETED || run.status === RunStatus.RUNNING;
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
  return level(worstCategory(b)) - level(worstCategory(a));
}

export function level(cat?: Category) {
  if (!cat) return -1;
  switch (cat) {
    case Category.INFO:
      return 0;
    case Category.WARNING:
      return 1;
    case Category.ERROR:
      return 2;
  }
}

export interface ActionTriggeredEventDetail {
  action: Action;
  run?: CheckRun;
}

export type ActionTriggeredEvent = CustomEvent<ActionTriggeredEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'action-triggered': ActionTriggeredEvent;
  }
}

export function fireActionTriggered(
  target: EventTarget,
  action: Action,
  run?: CheckRun
) {
  target.dispatchEvent(
    new CustomEvent('action-triggered', {
      detail: {action, run},
      composed: true,
      bubbles: true,
    })
  );
}
