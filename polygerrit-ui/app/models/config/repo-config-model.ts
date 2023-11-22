/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ConfigInfo, RepoName} from '../../types/common';
import {from, of} from 'rxjs';
import {switchMap} from 'rxjs/operators';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {ChangeModel} from '../change/change-model';
import {select} from '../../utils/observable-util';
import {Model} from '../base/model';
import {define} from '../dependency';

export const PROBE_PATH = '/Documentation/index.html';
export const DOCS_BASE_PATH = '/Documentation';

export interface RepoConfigState {
  repoConfig?: ConfigInfo;
}

export const repoConfigModelToken =
  define<RepoConfigModel>('repo-config-model');
export class RepoConfigModel extends Model<RepoConfigState> {
  public repoConfig$ = select(
    this.state$,
    configState => configState.repoConfig
  );

  public repoCommentLinks$ = select(
    this.repoConfig$,
    repoConfig => repoConfig?.commentlinks ?? {}
  );

  constructor(
    readonly changeModel: ChangeModel,
    readonly restApiService: RestApiService
  ) {
    super({});
    this.subscriptions = [
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
}
