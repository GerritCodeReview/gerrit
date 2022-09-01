/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {CheckRun, RunResult} from '../../models/checks/checks-model';

export interface RunSelectedEventDetail {
  reset: boolean;
  checkName?: string;
}

export type RunSelectedEvent = CustomEvent<RunSelectedEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'run-selected': RunSelectedEvent;
  }
}

export function fireRunSelected(target: EventTarget, checkName: string) {
  target.dispatchEvent(
    new CustomEvent('run-selected', {
      detail: {reset: false, checkName},
      composed: true,
      bubbles: true,
    })
  );
}

export function fireRunSelectionReset(target: EventTarget) {
  target.dispatchEvent(
    new CustomEvent('run-selected', {
      detail: {reset: true},
      composed: true,
      bubbles: true,
    })
  );
}

export function isAttemptSelected(
  selectedAttempt: number | undefined,
  run: CheckRun
) {
  if (!selectedAttempt) return run.isLatestAttempt;
  return selectedAttempt === run.attempt;
}

export function matches(result: RunResult, regExp: RegExp) {
  return (
    regExp.test(result.checkName) ||
    regExp.test(result.summary) ||
    (result.tags ?? []).some(tag => regExp.test(tag.name)) ||
    regExp.test(result.message ?? '')
  );
}
