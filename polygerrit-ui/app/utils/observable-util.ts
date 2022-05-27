/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {distinctUntilChanged, map, shareReplay} from 'rxjs/operators';
import {deepEqual} from './deep-util';

export function select<A, B>(obs$: Observable<A>, mapper: (_: A) => B) {
  return obs$.pipe(
    map(mapper),
    distinctUntilChanged(deepEqual),
    shareReplay(1)
  );
}
