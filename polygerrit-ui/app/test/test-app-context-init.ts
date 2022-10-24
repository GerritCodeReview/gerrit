/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Init app context before any other imports
import {create, Registry, Finalizable} from '../services/registry';
import {assertIsDefined} from '../utils/common-util';
import {AppContext} from '../services/app-context';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';
import {grRestApiMock} from './mocks/gr-rest-api_mock';
import {grStorageMock} from '../services/storage/gr-storage_mock';
import {GrAuthMock} from '../services/gr-auth/gr-auth_mock';
import {FlagsServiceImplementation} from '../services/flags/flags_impl';
import {EventEmitter} from '../services/gr-event-interface/gr-event-interface_impl';
import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
import {RouterModel} from '../services/router/router-model';
import {PluginsModel} from '../models/plugins/plugins-model';
import {MockHighlightService} from '../services/highlight/highlight-service-mock';
import {AccountsModel} from '../models/accounts-model/accounts-model';
import {UserModel} from '../models/user/user-model';
import {ShortcutsService} from '../services/shortcuts/shortcuts-service';

export function createTestAppContext(): AppContext & Finalizable {
  const appRegistry: Registry<AppContext> = {
    routerModel: (_ctx: Partial<AppContext>) => new RouterModel(),
    flagsService: (_ctx: Partial<AppContext>) =>
      new FlagsServiceImplementation(),
    reportingService: (_ctx: Partial<AppContext>) => grReportingMock,
    eventEmitter: (_ctx: Partial<AppContext>) => new EventEmitter(),
    authService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.eventEmitter, 'eventEmitter');
      return new GrAuthMock(ctx.eventEmitter);
    },
    restApiService: (_ctx: Partial<AppContext>) => grRestApiMock,
    jsApiService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new GrJsApiInterface(ctx.reportingService);
    },
    storageService: (_ctx: Partial<AppContext>) => grStorageMock,
    accountsModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new AccountsModel(ctx.restApiService);
    },
    pluginsModel: (_ctx: Partial<AppContext>) => new PluginsModel(),
    highlightService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new MockHighlightService(ctx.reportingService);
    },
  };
  return create<AppContext>(appRegistry);
}
