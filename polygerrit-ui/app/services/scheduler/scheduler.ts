/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
export type Task<T> = () => Promise<T>;
export interface Scheduler<T> {
  schedule(task: Task<T>): Promise<T>;
}
export class BaseScheduler<T> implements Scheduler<T> {
  schedule(task: Task<T>) {
    return task();
  }
}
