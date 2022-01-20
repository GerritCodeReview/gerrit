/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Scheduler, Task} from './scheduler';

export class MaxInFlightScheduler<T> implements Scheduler<T> {
  private inflight = 0;

  private waiting: Array<Task<void>> = [];

  constructor(
    private readonly base: Scheduler<T>,
    private maxInflight: number = 10
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
    if (this.inflight >= this.maxInflight) return;
    if (this.waiting.length === 0) return;
    const task = this.waiting.shift() as Task<void>;
    ++this.inflight;
    task().finally(() => {
      --this.inflight;
      this.next();
    });
  }
}
