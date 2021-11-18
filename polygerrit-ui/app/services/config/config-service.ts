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
import {updateRepoConfig, updateServerConfig} from './config-model';
import {repo$} from '../change/change-model';
import {switchMap} from 'rxjs/operators';
import {ConfigInfo, RepoName, ServerInfo} from '../../types/common';
import {from, of, Subscription} from 'rxjs';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {Finalizable} from '../registry';

export class ConfigService implements Finalizable {
  private readonly subscriptions: Subscription[] = [];

  constructor(readonly restApiService: RestApiService) {
    this.subscriptions.push(
      from(this.restApiService.getConfig()).subscribe((config?: ServerInfo) => {
        updateServerConfig(config);
      })
    );
    this.subscriptions.push(
      repo$
        .pipe(
          switchMap((repo?: RepoName) => {
            if (repo === undefined) return of(undefined);
            return from(this.restApiService.getProjectConfig(repo));
          })
        )
        .subscribe((repoConfig?: ConfigInfo) => {
          updateRepoConfig(repoConfig);
        })
    );
  }

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions.splice(0, this.subscriptions.length);
  }
}
