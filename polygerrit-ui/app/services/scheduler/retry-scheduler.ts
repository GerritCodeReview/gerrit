/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Scheduler, Task} from './scheduler';

export class RetryError<T> extends Error {
  constructor(readonly payload: T, message: string = 'Retry Error') {
    super(message);
  }
}

function untilTimeout(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms));
}

export class RetryScheduler<T> implements Scheduler<T> {
  constructor(
    private readonly base: Scheduler<T>,
    private maxRetry: number = 3,
    private timeoutMs: number = 50,
    private timeoutFactor: number = 1.618
  ) {}

  async schedule(task: Task<T>): Promise<T> {
    let tries = 0;
    let timeout = this.timeoutMs;

    const worker: Task<T> = async () => {
      try {
        return await this.base.schedule(task);
      } catch (e: unknown) {
        if (e instanceof RetryError && tries++ < this.maxRetry) {
          await untilTimeout(timeout);
          timeout = timeout * this.timeoutFactor;
          return await worker();
        } else {
          throw e;
        }
      }
    };
    return worker();
  }
}
