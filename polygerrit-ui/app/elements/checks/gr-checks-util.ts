/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {CheckRun} from '../../services/checks/checks-model';

export interface AttemptSelectedEventDetail {
  checkName: string;
  attempt: number | undefined;
}

export type AttemptSelectedEvent = CustomEvent<AttemptSelectedEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'attempt-selected': AttemptSelectedEvent;
  }
}

export function fireAttemptSelected(
  target: EventTarget,
  checkName: string,
  attempt: number | undefined
) {
  target.dispatchEvent(
    new CustomEvent('attempt-selected', {
      detail: {checkName, attempt},
      composed: true,
      bubbles: true,
    })
  );
}

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

export function isSelected(
  selectedAttempts: Map<string, number | undefined>,
  run: CheckRun
) {
  const selected = selectedAttempts.get(run.checkName);
  return (
    (selected === undefined && run.isLatestAttempt) || selected === run.attempt
  );
}
