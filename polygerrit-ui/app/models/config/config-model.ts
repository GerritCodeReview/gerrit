/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ServerInfo} from '../../types/common';
import {from} from 'rxjs';
import {switchMap} from 'rxjs/operators';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {ChangeModel} from '../change/change-model';
import {select} from '../../utils/observable-util';
import {Model} from '../base/model';
import {define} from '../dependency';
import {getBaseUrl, loginUrl} from '../../utils/url-util';

export const PROBE_PATH = '/Documentation/index.html';
export const DOCS_BASE_PATH = '/Documentation';

export interface ConfigState {
  serverConfig?: ServerInfo;
}

export const configModelToken = define<ConfigModel>('config-model');
export class ConfigModel extends Model<ConfigState> {
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
      switchMap(serverConfig => from(this.getDocsBaseUrl(serverConfig)))
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
    ];
  }

  // visible for testing
  async getDocsBaseUrl(config: ServerInfo | undefined): Promise<string> {
    if (config?.gerrit?.doc_url) return config.gerrit.doc_url;

    const ok = await this.restApiService.probePath(getBaseUrl() + PROBE_PATH);
    if (ok) return getBaseUrl() + DOCS_BASE_PATH;

    return 'https://gerrit-review.googlesource.com/Documentation';
  }

  // visible for testing
  updateServerConfig(serverConfig?: ServerInfo) {
    this.updateState({serverConfig});
  }
}
