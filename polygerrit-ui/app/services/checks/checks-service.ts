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

import {
  switchMap,
  takeWhile,
  tap,
  throttleTime,
  withLatestFrom,
} from 'rxjs/operators';
import {
  ChangeData,
  ChecksApiConfig,
  ChecksProvider,
  FetchResponse,
  ResponseCode,
} from '../../api/checks';
import {change$, currentPatchNum$} from '../change/change-model';
import {
  updateStateSetLoading,
  checkToPluginMap$,
  updateStateSetProvider,
  updateStateSetResults,
} from './checks-model';
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

  private readonly reloadSubjects: {[name: string]: Subject<void>} = {};

  private changeAndPatchNum$ = change$.pipe(withLatestFrom(currentPatchNum$));

  private checkToPluginMap = new Map<string, string>();

  constructor() {
    checkToPluginMap$.subscribe(x => (this.checkToPluginMap = x));
  }

  reload(pluginName: string) {
    this.reloadSubjects[pluginName].next();
  }

  reloadAll() {
    Object.keys(this.providers).forEach(key => this.reload(key));
  }

  reloadForCheck(checkName?: string) {
    if (!checkName) return;
    const plugin = this.checkToPluginMap.get(checkName);
    if (plugin) this.reload(plugin);
  }

  register(
    pluginName: string,
    provider: ChecksProvider,
    config: ChecksApiConfig
  ) {
    this.providers[pluginName] = provider;
    this.reloadSubjects[pluginName] = new BehaviorSubject<void>(undefined);
    updateStateSetProvider(pluginName, config);
    // Both, changed numbers and and announceUpdate request should trigger.
    combineLatest([
      this.changeAndPatchNum$,
      this.reloadSubjects[pluginName].pipe(throttleTime(1000)),
    ])
      .pipe(
        takeWhile(_ => !!this.providers[pluginName]),
        switchMap(
          ([[change, patchNum], _]): Observable<FetchResponse> => {
            if (!change || !patchNum || typeof patchNum !== 'number') {
              return of({
                responseCode: ResponseCode.OK,
                runs: [],
              });
            }
            const data: ChangeData = {
              changeNumber: change._number,
              patchsetNumber: patchNum,
              repo: change.project,
            };
            updateStateSetLoading(pluginName);
            return from(this.providers[pluginName].fetch(data));
          }
        ),
        tap(response => {
          updateStateSetResults(
            pluginName,
            response.runs ?? [],
            response.actions
          );
        })
      )
      .subscribe(() => {});
    this.reload(pluginName);
  }
}
