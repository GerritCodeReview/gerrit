/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Scheduler, Task} from './scheduler';

export class MaxInFlightScheduler<T> implements Scheduler<T> {
  private inflight = 0;

  private waiting: Array<{task: Task<void>; name?: string}> = [];

  private readonly running: string[] = [];

  get activeCount(): number {
    return this.running.length + this.waiting.length;
  }

  get activeRequests(): string[] {
    const waitingNames = this.waiting.map(w => w.name || 'unknown');
    return [...this.running, ...waitingNames];
  }

  constructor(
    private readonly base: Scheduler<T>,
    private maxInflight: number = 10
  ) {}

  async schedule(task: Task<T>, name?: string): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      this.waiting.push({
        task: async () => {
          try {
            const result = await this.base.schedule(task, name);
            resolve(result);
          } catch (e: unknown) {
            reject(e);
          }
        },
        name,
      });
      this.next();
    });
  }

  private next() {
    if (this.inflight >= this.maxInflight) return;
    if (this.waiting.length === 0) return;
    const {task, name} = this.waiting.shift()!;
    ++this.inflight;
    const taskName = name || 'unknown';
    this.running.push(taskName);
    task().finally(() => {
      --this.inflight;
      const index = this.running.indexOf(taskName);
      if (index > -1) this.running.splice(index, 1);
      this.next();
    });
  }
}
