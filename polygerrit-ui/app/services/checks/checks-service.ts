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

import {switchMap, takeWhile, tap, withLatestFrom} from 'rxjs/operators';
import {
  ChecksApiConfig,
  ChecksProvider,
  FetchResponse,
  ResponseCode,
} from '../../elements/plugins/gr-checks-api/gr-checks-api-types';
import {change$, currentPatchNum$} from '../change/change-model';
import {updateStateSetProvider, updateStateSetResults} from './checks-model';
import {
  BehaviorSubject,
  combineLatest,
  from,
  Observable,
  of,
  Subject,
} from 'rxjs';

export class ChecksService {
  private readonly providers: {[name: string]: ChecksProvider} = {};

  private readonly anouncementSubjects: {[name: string]: Subject<void>} = {};

  private changeAndPatchNum$ = change$.pipe(withLatestFrom(currentPatchNum$));

  constructor() {
    console.log('ChecksService constructor');
  }

  announceUpdate(pluginName: string) {
    this.anouncementSubjects[pluginName].next();
  }

  register(
    pluginName: string,
    provider: ChecksProvider,
    config: ChecksApiConfig
  ) {
    console.log('ChecksService register: ' + pluginName);
    this.providers[pluginName] = provider;
    this.anouncementSubjects[pluginName] = new BehaviorSubject<void>(undefined);
    updateStateSetProvider(pluginName, config);
    this.changeAndPatchNum$.subscribe(([change, patchNum]) =>
      console.log(`tap changeAndPatchNum ${change?._number} ${patchNum}`)
    );
    this.anouncementSubjects[pluginName].subscribe(_ =>
      console.log(`tap anouncementSubjects ${pluginName}`)
    );
    // Both, changed numbers and and announceUpdate request should trigger.
    combineLatest([
      this.changeAndPatchNum$,
      this.anouncementSubjects[pluginName],
    ])
      .pipe(
        tap(_ => console.log('tap1')),
        takeWhile(_ => !!this.providers[pluginName]),
        switchMap(
          ([[change, patchNum], _]: any[]): Observable<FetchResponse> => {
            console.log(`tap2 ${change?._number} ${patchNum}`);
            if (!change || !patchNum) {
              console.log('tap2a return empty');
              return of({
                responseCode: ResponseCode.OK,
                runs: [],
                results: [],
              });
            }
            return from(
              this.providers[pluginName].fetch(change._number, patchNum)
            );
          }
        ),
        tap(response => {
          console.log('tap3 ' + response.runs.length);
          updateStateSetResults(pluginName, response.runs);
        })
      )
      .subscribe(() => {
        console.log('subscribe');
      });
  }
}
