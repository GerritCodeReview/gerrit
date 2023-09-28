/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ConfigInfo, RepoName, ServerInfo} from '../../types/common';
import {from, of} from 'rxjs';
import {switchMap} from 'rxjs/operators';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {ChangeModel} from '../change/change-model';
import {select} from '../../utils/observable-util';
import {Model} from '../base/model';
import {define} from '../dependency';
import {loginUrl} from '../../utils/url-util';

export interface ConfigState {
  repoConfig?: ConfigInfo;
  serverConfig?: ServerInfo;
}

export const configModelToken = define<ConfigModel>('config-model');
export class ConfigModel extends Model<ConfigState> {
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

  public download$ = select(
    this.serverConfig$,
    serverConfig => serverConfig?.download
  );

  public loginUrl$ = select(this.serverConfig$, serverConfig =>
    loginUrl(serverConfig?.auth)
  );

  public loginText$ = select(
    this.serverConfig$,
    serverConfig => serverConfig?.auth.login_text ?? 'Sign in'
  );

  public mergeabilityComputationBehavior$ = select(
    this.serverConfig$,
    serverConfig => serverConfig?.change?.mergeability_computation_behavior
  );

  public docsBaseUrl$ = select(
    this.serverConfig$.pipe(
      switchMap(serverConfig =>
        from(this.restApiService.getDocsBaseUrl(serverConfig))
      )
    ),
    url => url
  );

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
          switchMap((repo: RepoName | undefined) => {
            if (repo === undefined) return of(undefined);
            return from(this.restApiService.getProjectConfig(repo));
          })
        )
        .subscribe((repoConfig?: ConfigInfo) => {
          this.updateRepoConfig(repoConfig);
        }),
    ];
  }

  // visible for testing
  updateRepoConfig(repoConfig?: ConfigInfo) {
    this.updateState({repoConfig});
  }

  // visible for testing
  updateServerConfig(serverConfig?: ServerInfo) {
    this.updateState({serverConfig});
  }
}
