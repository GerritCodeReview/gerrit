/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Scheduler, Task} from './scheduler';

// This scheduler ensures that at most one request is made per throttleMs period.
export class QueueingScheduler<T> implements Scheduler<T> {
  private lastRequest = 0;

  private waiting: Array<Task<void>> = [];

  private busy = false;

  constructor(
    private readonly base: Scheduler<T>,
    private throttleMs: number = 500
  ) {}

  async schedule(task: Task<T>): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      this.waiting.push(async () => {
        try {
          const result = await this.base.schedule(task);
          resolve(result);
        } catch (e: unknown) {
          reject(e);
        }
      });
      this.next();
    });
  }

  private next() {
    // If the scheduler is busy we expect a task to finish and that task to call
    // this.next().
    // Otherwise, we check if the recent request was too recent. If so we wait
    // before calling next again.
    // Finally, we pick the first task, set the scheduler as busy and execute.
    if (this.busy) return;
    if (this.waiting.length === 0) return;
    const now = Date.now();
    if (now - this.lastRequest < this.throttleMs) {
      setTimeout(() => this.next(), this.throttleMs - (now - this.lastRequest));
      return;
    }
    const task = this.waiting.shift() as Task<void>;
    this.busy = true;
    task().finally(() => {
      this.busy = false;
      this.lastRequest = now;
      setTimeout(() => this.next(), this.throttleMs);
    });
  }
}
