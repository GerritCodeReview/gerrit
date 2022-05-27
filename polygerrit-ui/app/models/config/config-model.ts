/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ConfigInfo, RepoName, ServerInfo} from '../../types/common';
import {from, of, Subscription} from 'rxjs';
import {switchMap} from 'rxjs/operators';
import {Finalizable} from '../../services/registry';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {ChangeModel} from '../change/change-model';
import {select} from '../../utils/observable-util';
import {Model} from '../model';
import {define} from '../dependency';

export interface ConfigState {
  repoConfig?: ConfigInfo;
  serverConfig?: ServerInfo;
}

export const configModelToken = define<ConfigModel>('config-model');
export class ConfigModel extends Model<ConfigState> implements Finalizable {
  public repoConfig$ = select(
    this.state$,
    configState => configState.repoConfig
  );

  public repoCommentLinks$ = select(
    this.repoConfig$,
    repoConfig => repoConfig?.commentlinks ?? {}
  );

  public serverConfig$ = select(
    this.state$,
    configState => configState.serverConfig
  );

  private subscriptions: Subscription[];

  constructor(
    readonly changeModel: ChangeModel,
    readonly restApiService: RestApiService
  ) {
    super({});
    this.subscriptions = [
      from(this.restApiService.getConfig()).subscribe((config?: ServerInfo) => {
        this.updateServerConfig(config);
      }),
      this.changeModel.repo$
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
    const current = this.subject$.getValue();
    this.subject$.next({...current, repoConfig});
  }

  updateServerConfig(serverConfig?: ServerInfo) {
    const current = this.subject$.getValue();
    this.subject$.next({...current, serverConfig});
  }

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }
}
