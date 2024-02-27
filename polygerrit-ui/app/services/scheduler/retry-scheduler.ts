/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Scheduler, Task} from './scheduler';

export class RetryError<T> extends Error {
  constructor(readonly payload: T, message = 'Retry Error') {
    super(message);
  }
}

function untilTimeout(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms));
}

/**
 * The scheduler that retries tasks on RetryError.
 *
 * The task is only retried if the RetryError was thrown, all other errors cause
 * the worker to stop and the error is re-thrown.
 *
 * The number of retries are limited by maxRetry, the retries are performed
 * according to exponential backoff, configured by backoffIntervalMs
 * and backoffFactor.
 */
export class RetryScheduler<T> implements Scheduler<T> {
  constructor(
    private readonly base: Scheduler<T>,
    private maxRetry: number,
    private backoffIntervalMs: number,
    private backoffFactor: number = 1.618
  ) {}

  async schedule(task: Task<T>): Promise<T> {
    let tries = 0;
    let timeout = this.backoffIntervalMs;

    const worker: Task<T> = async () => {
      try {
        return await this.base.schedule(task);
      } catch (e: unknown) {
        if (e instanceof RetryError && tries++ < this.maxRetry) {
          await untilTimeout(timeout);
          timeout = timeout * this.backoffFactor;
          return await worker();
        } else {
          throw e;
        }
      }
    };
    return worker();
  }
}
