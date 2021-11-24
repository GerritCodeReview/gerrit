/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {ConfigInfo, RepoName, ServerInfo} from '../../types/common';
import {BehaviorSubject, from, Observable, of, Subscription} from 'rxjs';
import {switchMap} from 'rxjs/operators';
import {Finalizable} from '../registry';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {repo$} from '../change/change-model';
import {select} from '../../utils/observable-util';

export interface ConfigState {
  repoConfig?: ConfigInfo;
  serverConfig?: ServerInfo;
}

export class ConfigModel implements Finalizable {
  // TODO: Figure out how to best enforce immutability of all states. Use Immer?
  // Use DeepReadOnly?
  private initialState: ConfigState = {};

  private privateState$ = new BehaviorSubject(this.initialState);

  // Re-exporting as Observable so that you can only subscribe, but not emit.
  public configState$: Observable<ConfigState> =
    this.privateState$.asObservable();

  public repoConfig$ = select(
    this.privateState$,
    configState => configState.repoConfig
  );

  public serverConfig$ = select(
    this.privateState$,
    configState => configState.serverConfig
  );

  private subscriptions: Subscription[];

  constructor(readonly restApiService: RestApiService) {
    this.subscriptions = [
      from(this.restApiService.getConfig()).subscribe((config?: ServerInfo) => {
        this.updateServerConfig(config);
      }),
      repo$
        .pipe(
          switchMap((repo?: RepoName) => {
            if (repo === undefined) return of(undefined);
            return from(this.restApiService.getProjectConfig(repo));
          })
        )
        .subscribe((repoConfig?: ConfigInfo) => {
          this.updateRepoConfig(repoConfig);
        }),
    ];
  }

  updateRepoConfig(repoConfig?: ConfigInfo) {
    const current = this.privateState$.getValue();
    this.privateState$.next({...current, repoConfig});
  }

  updateServerConfig(serverConfig?: ServerInfo) {
    const current = this.privateState$.getValue();
    this.privateState$.next({...current, serverConfig});
  }

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }
}
