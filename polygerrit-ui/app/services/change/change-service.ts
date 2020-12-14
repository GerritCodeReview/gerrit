/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import {routerChangeNum$} from '../router/router-model';
import {updateState} from './change-model';
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';
import {switchMap, tap} from 'rxjs/operators';
import {of, from} from 'rxjs';

export class ChangeService {
  private routerChangeNumEffect = routerChangeNum$.pipe(
    switchMap(changeNum => {
      if (!changeNum) return of(undefined);
      return from(this.restApiService.getChangeDetail(changeNum));
    }),
    tap(change => {
      updateState(change ?? undefined);
    })
  );

  constructor(private readonly restApiService: RestApiService) {
    this.routerChangeNumEffect.subscribe();
  }

  // TODO: Remove.
  dontDoAnything() {}
}
