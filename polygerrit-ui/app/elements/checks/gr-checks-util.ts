/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {CheckRun, RunResult} from '../../models/checks/checks-model';
import {
  ALL_ATTEMPTS,
  AttemptChoice,
  LATEST_ATTEMPT,
} from '../../models/checks/checks-util';
import {fire} from '../../utils/event-util';

export interface RunSelectedEventDetail {
  checkName?: string;
}

export type RunSelectedEvent = CustomEvent<RunSelectedEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'run-selected': RunSelectedEvent;
  }
}

export function fireRunSelected(target: EventTarget, checkName: string) {
  fire(target, 'run-selected', {checkName});
}

export function isAttemptSelected(
  selectedAttempt: AttemptChoice,
  run: CheckRun
) {
  if (selectedAttempt === LATEST_ATTEMPT) return run.isLatestAttempt;
  if (selectedAttempt === ALL_ATTEMPTS) return true;
  return selectedAttempt === (run.attempt ?? 0);
}

export function matches(result: RunResult, regExp: RegExp) {
  return (
    regExp.test(result.checkName) ||
    regExp.test(result.summary) ||
    (result.tags ?? []).some(tag => regExp.test(tag.name)) ||
    regExp.test(result.message ?? '')
  );
}
