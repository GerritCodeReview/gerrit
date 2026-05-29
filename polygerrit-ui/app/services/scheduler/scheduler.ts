/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
export type Task<T> = () => Promise<T>;
export interface Scheduler<T> {
  schedule(task: Task<T>, name?: string): Promise<T>;
  get activeCount(): number;
  get activeRequests(): string[];
}
export class BaseScheduler<T> implements Scheduler<T> {
  schedule(task: Task<T>, _name?: string) {
    return task();
  }

  get activeCount() {
    return 0;
  }

  get activeRequests() {
    return [];
  }
}
