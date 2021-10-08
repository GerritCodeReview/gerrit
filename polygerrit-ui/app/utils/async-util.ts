/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

/**
 * @param fn An iteratee function to be passed each element of
 *     the array in order. Must return a promise, and the following
 *     iteration will not begin until resolution of the promise returned by
 *     the previous iteration.
 *
 *     An optional second argument to fn is a callback that will halt the
 *     loop if called.
 */
export function asyncForeach<T>(
  array: T[],
  fn: (item: T, stopCallback: () => void) => Promise<unknown>
): Promise<T | void> {
  if (!array.length) {
    return Promise.resolve();
  }
  let stop = false;
  const stopCallback = () => {
    stop = true;
  };
  return fn(array[0], stopCallback).then(() => {
    if (stop) {
      return Promise.resolve();
    }
    return asyncForeach(array.slice(1), fn);
  });
}

export const _testOnly_allTasks = new Map<number, DelayedTask>();

/**
 * This is just a very simple and small wrapper around setTimeout(). Instead of
 * the usual:
 *
 * const timer = window.setTimeout(() => {...do stuff...}, 123);
 * window.clearTimeout(timer);
 *
 * With this class you can do:
 *
 * const task = new Task(() => {...do stuff...}, 123);
 * task.cancel();
 *
 * It is just nicer to have an object for this instead of a number as a handle.
 */
export class DelayedTask {
  private timer?: number;

  constructor(private callback: () => void, waitMs = 0) {
    this.timer = window.setTimeout(() => {
      if (this.timer) _testOnly_allTasks.delete(this.timer);
      this.timer = undefined;
      if (this.callback) this.callback();
    }, waitMs);
    _testOnly_allTasks.set(this.timer, this);
  }

  cancel() {
    if (this.isActive()) {
      window.clearTimeout(this.timer);
      if (this.timer) _testOnly_allTasks.delete(this.timer);
      this.timer = undefined;
    }
  }

  flush() {
    if (this.isActive()) {
      this.cancel();
      if (this.callback) this.callback();
    }
  }

  isActive() {
    return this.timer !== undefined;
  }
}

/**
 * The usage pattern is:
 *
 * this.myDebouncedTask = debounce(this.myDebouncedTask, () => {...}, 123);
 *
 * It is identical to:
 *
 * this.myTask = new DelayedTask(() => {...}, 123);
 *
 * But it would cancel a potentially scheduled task beforehand.
 */
export function debounce(
  existingTask: DelayedTask | undefined,
  callback: () => void,
  waitMs = 0
) {
  existingTask?.cancel();
  return new DelayedTask(callback, waitMs);
}

const THROTTLE_INTERVAL_MS = 500;

/**
 * Ensure only one call is made within THROTTLE_INTERVAL_MS and any call within
 * this interval is ignored
 */
export function throttleWrap<T>(fn: (e: T) => void) {
  let lastCall: number | undefined;
  return (e: T) => {
    if (
      lastCall !== undefined &&
      Date.now() - lastCall < THROTTLE_INTERVAL_MS
    ) {
      return;
    }
    lastCall = Date.now();
    fn(e);
  };
}
