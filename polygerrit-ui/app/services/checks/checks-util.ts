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
import {Category, CheckRun, RunStatus} from '../../api/checks';
import {assertNever} from '../../utils/common-util';

export function worstCategory(run: CheckRun) {
  if (hasResultsOf(run, Category.ERROR)) return Category.ERROR;
  if (hasResultsOf(run, Category.WARNING)) return Category.WARNING;
  if (hasResultsOf(run, Category.INFO)) return Category.INFO;
  return undefined;
}

export function iconForCategory(category: Category) {
  switch (category) {
    case Category.ERROR:
      return 'error';
    case Category.INFO:
      return 'info-outline';
    case Category.WARNING:
      return 'warning';
    default:
      assertNever(category, `Unsupported category: ${category}`);
  }
}

export function iconForRun(run: CheckRun) {
  const category = worstCategory(run);
  return category ? iconForCategory(category) : iconForStatus(run.status);
}

export function iconForStatus(status: RunStatus) {
  switch (status) {
    // Note that this is only for COMPLETED without results!
    case RunStatus.COMPLETED:
      return 'check-circle';
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
