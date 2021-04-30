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
  catchError,
  filter,
  switchMap,
  takeWhile, tap,
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
  checksPatchsetNumber$,
  checkToPluginMap$,
  updateStateSetError,
  updateStateSetLoading,
  updateStateSetNotLoggedIn,
  updateStateSetPatchset,
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
  timer,
} from 'rxjs';
import {PatchSetNumber} from '../../types/common';
import {getCurrentRevision} from '../../utils/change-util';
import {getShaByPatchNum} from '../../utils/patch-set-util';
import {assertIsDefined} from '../../utils/common-util';
import {ReportingService} from '../gr-reporting/gr-reporting';

export class ChecksService {
  private readonly providers: {[name: string]: ChecksProvider} = {};

  private readonly reloadSubjects: {[name: string]: Subject<void>} = {};

  private checkToPluginMap = new Map<string, string>();

  private readonly documentVisibilityChange$ = new BehaviorSubject(undefined);

  constructor(readonly reporting: ReportingService) {
    checkToPluginMap$.subscribe(map => {
      this.checkToPluginMap = map;
    });
    latestPatchNum$.subscribe(num => {
      updateStateSetPatchset(num);
    });
    document.addEventListener('visibilitychange', () => {
      this.documentVisibilityChange$.next(undefined);
    });
  }

  setPatchset(num: PatchSetNumber) {
    updateStateSetPatchset(num);
  }

  reload(pluginName: string) {
    console.log(`fetchChecks reload requested for ${pluginName}`);
    this.reloadSubjects[pluginName].next();
  }

  reloadAll() {
    console.log(`fetchChecks reload requested for all`);
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
    const pollIntervalMs = (config?.fetchPollingIntervalSeconds ?? 60) * 1000;
    // Various events should trigger fetching checks from the provider:
    // 1. Change number and patchset number changes.
    // 2. Specific reload requests.
    // 3. Regular polling starting with an initial fetch right now.
    // 4. A hidden Gerrit tab becoming visible.
    combineLatest([
      changeNum$.pipe(tap(num => console.log(`fetchChecks changeNum ${pluginName} ${num}`))),
      checksPatchsetNumber$.pipe(tap(num => console.log(`fetchChecks psNum ${pluginName} ${num}`))),
      this.reloadSubjects[pluginName].pipe(throttleTime(1000), tap(_ => console.log(`fetchChecks reload ${pluginName}`))),
      timer(0, pollIntervalMs).pipe(tap(_ => console.log(`fetchChecks timer ${pluginName}`))),
      this.documentVisibilityChange$.pipe(tap(_ => console.log(`fetchChecks visibility ${pluginName}`))),
    ])
      .pipe(
        takeWhile(_ => !!this.providers[pluginName]),
        filter(_ => document.visibilityState !== 'hidden'),
        withLatestFrom(change$),
        switchMap(
          ([[changeNum, patchNum], change]): Observable<FetchResponse> => {
            console.log(`fetchChecks switchMap: ${pluginName} ${changeNum}`);
            if (
              !change ||
              !changeNum ||
              !patchNum ||
              typeof patchNum !== 'number'
            ) {
              console.log(`fetchChecks undefined: ${pluginName} ${changeNum}`);
              return of({
                responseCode: ResponseCode.OK,
                runs: [],
              });
            }
            assertIsDefined(change.revisions, 'change.revisions');
            const patchsetSha = getShaByPatchNum(change.revisions, patchNum);
            // Sometimes patchNum is updated earlier than change, so change
            // revisions don't have patchNum yet
            if (!patchsetSha) {
              console.log(`fetchChecks noPatchset: ${pluginName} ${changeNum}`);
              return of({
                responseCode: ResponseCode.OK,
                runs: [],
              });
            }
            const data: ChangeData = {
              changeNumber: changeNum,
              patchsetNumber: patchNum,
              patchsetSha,
              repo: change.project,
              commmitMessage: getCurrentRevision(change)?.commit?.message,
              changeInfo: change,
            };
            return this.fetchResults(pluginName, data);
          }
        ),
        catchError(e => {
          const errorResponse: FetchResponse = {
            responseCode: ResponseCode.ERROR,
            errorMessage: `Error message from plugin '${pluginName}': ${e}`,
          };
          return of(errorResponse);
        })
      )
      .subscribe(response => {
        switch (response.responseCode) {
          case ResponseCode.ERROR:
            assertIsDefined(response.errorMessage, 'errorMessage');
            updateStateSetError(pluginName, response.errorMessage);
            break;
          case ResponseCode.NOT_LOGGED_IN:
            assertIsDefined(response.loginCallback, 'loginCallback');
            updateStateSetNotLoggedIn(pluginName, response.loginCallback);
            break;
          case ResponseCode.OK:
            updateStateSetResults(
              pluginName,
              response.runs ?? [],
              response.actions ?? [],
              response.links ?? []
            );
            break;
        }
      });
  }

  private fetchResults(pluginName: string, data: ChangeData) {
    updateStateSetLoading(pluginName);
    const timer = this.reporting.getTimer('ChecksPluginFetch');
    const start = new Date().getTime();
    const fetchPromise = this.providers[pluginName]
      .fetch(data)
      .then(response => {
        const stop = new Date().getTime();
        console.log(`fetchChecks stop:  ${pluginName} ${data.changeNumber} ${stop} ${stop - start}`);
        timer.end({pluginName});
        return response;
      });
    console.log(`fetchChecks start: ${pluginName} ${data.changeNumber} ${start}`);
    return from(fetchPromise);
  }
}
