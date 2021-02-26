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
import {change$, changeNum$, latestPatchNum$} from '../change/change-model';
import {
  updateStateSetLoading,
  checkToPluginMap$,
  updateStateSetProvider,
  updateStateSetResults, checksPatchsetNumber$, updateStateSetPatchset,
} from './checks-model';
import {
  BehaviorSubject,
  combineLatest,
  from,
  Observable,
  of,
  Subject,
} from 'rxjs';
import {PatchSetNumber} from '../../types/common';

export class ChecksService {
  private readonly providers: {[name: string]: ChecksProvider} = {};

  private readonly reloadSubjects: {[name: string]: Subject<void>} = {};

  private checkToPluginMap = new Map<string, string>();

  constructor() {
    checkToPluginMap$.subscribe(map => {
      this.checkToPluginMap = map;
    });
    latestPatchNum$.subscribe(num => {
      updateStateSetPatchset(num);
    });
  }

  setPatchset(num: PatchSetNumber) {
    updateStateSetPatchset(num);
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
      changeNum$,
      checksPatchsetNumber$,
      this.reloadSubjects[pluginName].pipe(throttleTime(1000)),
    ])
      .pipe(
        takeWhile(_ => !!this.providers[pluginName]),
        withLatestFrom(change$),
        switchMap(
          ([[changeNum, patchNum, _], change]): Observable<FetchResponse> => {
            if (
              !change ||
              !changeNum ||
              !patchNum ||
              typeof patchNum !== 'number'
            ) {
              return of({
                responseCode: ResponseCode.OK,
                runs: [],
              });
            }
            const data: ChangeData = {
              changeNumber: changeNum,
              patchsetNumber: patchNum,
              repo: change.project,
            };
            updateStateSetLoading(pluginName);
            return from(this.providers[pluginName].fetch(data));
          }
        )
      )
      .subscribe(response => {
        updateStateSetResults(
          pluginName,
          response.runs ?? [],
          response.actions
        );
      });
    this.reload(pluginName);
  }
}
