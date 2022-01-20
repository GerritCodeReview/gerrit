/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Scheduler, Task} from './scheduler';

type FakeTask = (error?: unknown) => Promise<void>;
export class FakeScheduler<T> implements Scheduler<T> {
  readonly scheduled: Array<FakeTask> = [];

  schedule(task: Task<T>) {
    return new Promise<T>((resolve, reject) => {
      this.scheduled.push(async (error?: unknown) => {
        if (error) {
          reject(error);
        } else {
          try {
            resolve(await task());
          } catch (e: unknown) {
            reject(e);
          }
        }
      });
    });
  }

  async resolve(): Promise<void> {
    if (this.scheduled.length === 0) return;
    const fakeTask = this.scheduled.shift() as FakeTask;
    await fakeTask();
  }

  async reject(error: unknown): Promise<void> {
    if (this.scheduled.length === 0) return;
    const fakeTask = this.scheduled.shift() as FakeTask;
    await fakeTask(error);
  }
}
