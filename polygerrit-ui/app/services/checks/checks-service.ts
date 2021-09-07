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
  takeUntil,
  takeWhile,
  throttleTime,
  withLatestFrom,
} from 'rxjs/operators';
import {
  Action,
  ChangeData,
  CheckResult,
  CheckRun,
  ChecksApiConfig,
  ChecksProvider,
  FetchResponse,
  ResponseCode,
} from '../../api/checks';
import {change$, changeNum$, latestPatchNum$} from '../change/change-model';
import {
  ChecksPatchset,
  checksSelectedPatchsetNumber$,
  checkToPluginMap$,
  updateStateSetError,
  updateStateSetLoading,
  updateStateSetNotLoggedIn,
  updateStateSetPatchset,
  updateStateSetProvider,
  updateStateSetResults,
  updateStateUpdateResult,
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
import {ChangeInfo, NumericChangeId, PatchSetNumber} from '../../types/common';
import {getCurrentRevision} from '../../utils/change-util';
import {getShaByPatchNum} from '../../utils/patch-set-util';
import {assertIsDefined} from '../../utils/common-util';
import {ReportingService} from '../gr-reporting/gr-reporting';
import {routerPatchNum$} from '../router/router-model';
import {Execution} from '../../constants/reporting';
import {fireAlert, fireEvent} from '../../utils/event-util';

export class ChecksService {
  private readonly providers: {[name: string]: ChecksProvider} = {};

  private readonly reloadSubjects: {[name: string]: Subject<void>} = {};

  private checkToPluginMap = new Map<string, string>();

  private changeNum?: NumericChangeId;

  private latestPatchNum?: PatchSetNumber;

  private readonly documentVisibilityChange$ = new BehaviorSubject(undefined);

  constructor(readonly reporting: ReportingService) {
    changeNum$.subscribe(x => (this.changeNum = x));
    checkToPluginMap$.subscribe(map => {
      this.checkToPluginMap = map;
    });
    combineLatest([routerPatchNum$, latestPatchNum$]).subscribe(
      ([routerPs, latestPs]) => {
        this.latestPatchNum = latestPs;
        if (latestPs === undefined) {
          this.setPatchset(undefined);
        } else if (typeof routerPs === 'number') {
          this.setPatchset(routerPs);
        } else {
          this.setPatchset(latestPs);
        }
      }
    );
    document.addEventListener('visibilitychange', () => {
      this.documentVisibilityChange$.next(undefined);
    });
    document.addEventListener('reload', () => {
      this.reloadAll();
    });
  }

  setPatchset(num?: PatchSetNumber) {
    updateStateSetPatchset(num === this.latestPatchNum ? undefined : num);
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

  updateResult(pluginName: string, run: CheckRun, result: CheckResult) {
    updateStateUpdateResult(pluginName, run, result, ChecksPatchset.LATEST);
    updateStateUpdateResult(pluginName, run, result, ChecksPatchset.SELECTED);
  }

  triggerAction(action?: Action, run?: CheckRun) {
    if (!action?.callback) return;
    if (!this.changeNum) return;
    const patchSet = run?.patchset ?? this.latestPatchNum;
    if (!patchSet) return;
    const promise = action.callback(
      this.changeNum,
      patchSet,
      run?.attempt,
      run?.externalId,
      run?.checkName,
      action.name
    );
    // If plugins return undefined or not a promise, then show no toast.
    if (!promise?.then) return;

    fireAlert(document, `Triggering action '${action.name}' ...`);
    from(promise)
      // If the action takes longer than 5 seconds, then most likely the
      // user is either not interested or the result not relevant anymore.
      .pipe(takeUntil(timer(5000)))
      .subscribe(result => {
        if (result.errorMessage || result.message) {
          fireAlert(document, `${result.message ?? result.errorMessage}`);
        } else {
          fireEvent(document, 'hide-alert');
        }
        if (result.shouldReload) {
          this.reloadForCheck(run?.checkName);
        }
      });
  }

  register(
    pluginName: string,
    provider: ChecksProvider,
    config: ChecksApiConfig
  ) {
    if (this.providers[pluginName]) {
      console.warn(
        `Plugin '${pluginName}' was trying to register twice as a Checks UI provider. Ignored.`
      );
      return;
    }
    this.providers[pluginName] = provider;
    this.reloadSubjects[pluginName] = new BehaviorSubject<void>(undefined);
    updateStateSetProvider(pluginName, ChecksPatchset.LATEST);
    updateStateSetProvider(pluginName, ChecksPatchset.SELECTED);
    this.initFetchingOfData(pluginName, config, ChecksPatchset.LATEST);
    this.initFetchingOfData(pluginName, config, ChecksPatchset.SELECTED);
  }

  initFetchingOfData(
    pluginName: string,
    config: ChecksApiConfig,
    patchset: ChecksPatchset
  ) {
    const pollIntervalMs = (config?.fetchPollingIntervalSeconds ?? 60) * 1000;
    // Various events should trigger fetching checks from the provider:
    // 1. Change number and patchset number changes.
    // 2. Specific reload requests.
    // 3. Regular polling starting with an initial fetch right now.
    // 4. A hidden Gerrit tab becoming visible.
    combineLatest([
      changeNum$,
      patchset === ChecksPatchset.LATEST
        ? latestPatchNum$
        : checksSelectedPatchsetNumber$,
      this.reloadSubjects[pluginName].pipe(throttleTime(1000)),
      timer(0, pollIntervalMs),
      this.documentVisibilityChange$,
    ])
      .pipe(
        takeWhile(_ => !!this.providers[pluginName]),
        filter(_ => document.visibilityState !== 'hidden'),
        withLatestFrom(change$),
        switchMap(
          ([[changeNum, patchNum], change]): Observable<FetchResponse> => {
            if (!change || !changeNum || !patchNum) return of(this.empty());
            if (typeof patchNum !== 'number') return of(this.empty());
            assertIsDefined(change.revisions, 'change.revisions');
            const patchsetSha = getShaByPatchNum(change.revisions, patchNum);
            // Sometimes patchNum is updated earlier than change, so change
            // revisions don't have patchNum yet
            if (!patchsetSha) return of(this.empty());
            const data: ChangeData = {
              changeNumber: changeNum,
              patchsetNumber: patchNum,
              patchsetSha,
              repo: change.project,
              commitMessage: getCurrentRevision(change)?.commit?.message,
              changeInfo: change as ChangeInfo,
            };
            return this.fetchResults(pluginName, data, patchset);
          }
        ),
        catchError(e => {
          // This should not happen and is really severe, because it means that
          // the Observable has terminated and we won't recover from that. No
          // further attempts to fetch results for this plugin will be made.
          this.reporting.error(e, `checks-service crash for ${pluginName}`);
          return of(this.createErrorResponse(pluginName, e));
        })
      )
      .subscribe(response => {
        switch (response.responseCode) {
          case ResponseCode.ERROR: {
            const message = response.errorMessage ?? '-';
            this.reporting.reportExecution(Execution.CHECKS_API_ERROR, {
              plugin: pluginName,
              message,
            });
            updateStateSetError(pluginName, message, patchset);
            break;
          }
          case ResponseCode.NOT_LOGGED_IN: {
            assertIsDefined(response.loginCallback, 'loginCallback');
            this.reporting.reportExecution(Execution.CHECKS_API_NOT_LOGGED_IN, {
              plugin: pluginName,
            });
            updateStateSetNotLoggedIn(
              pluginName,
              response.loginCallback,
              patchset
            );
            break;
          }
          case ResponseCode.OK: {
            updateStateSetResults(
              pluginName,
              response.runs ?? [],
              response.actions ?? [],
              response.links ?? [],
              patchset
            );
            break;
          }
        }
      });
  }

  private empty(): FetchResponse {
    return {
      responseCode: ResponseCode.OK,
      runs: [],
    };
  }

  private createErrorResponse(
    pluginName: string,
    message: object
  ): FetchResponse {
    return {
      responseCode: ResponseCode.ERROR,
      errorMessage:
        `Error message from plugin '${pluginName}':` +
        ` ${JSON.stringify(message)}`,
    };
  }

  private fetchResults(
    pluginName: string,
    data: ChangeData,
    patchset: ChecksPatchset
  ): Observable<FetchResponse> {
    updateStateSetLoading(pluginName, patchset);
    const timer = this.reporting.getTimer('ChecksPluginFetch');
    const fetchPromise = this.providers[pluginName]
      .fetch(data)
      .then(response => {
        timer.end({pluginName});
        return response;
      });
    return from(fetchPromise).pipe(
      catchError(e => of(this.createErrorResponse(pluginName, e)))
    );
  }
}
