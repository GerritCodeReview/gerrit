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

 import {create, Registry, Finalizable} from '../services/registry';
 import {assertIsDefined} from '../utils/common-util';
 import {AppContext, injectAppContext} from '../services/app-context';
 import {AuthService} from '../services/gr-auth/gr-auth';
 import {FlagsService} from '../services/flags/flags';
 import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';
 import {grRestApiMock} from '../test/mocks/gr-rest-api_mock';
 import {grStorageMock} from '../services/storage/gr-storage_mock';
 import {EventEmitter} from '../services/gr-event-interface/gr-event-interface_impl';
 import {ChangeService} from '../services/change/change-service';
 import {ChecksService} from '../services/checks/checks-service';
 import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
 import {ConfigService} from '../services/config/config-service';
 import {UserService} from '../services/user/user-service';
 import {CommentsService} from '../services/comments/comments-service';
 import {ShortcutsService} from '../services/shortcuts/shortcuts-service';
 import {BrowserModel} from '../services/browser/browser-model';

class MockFlagsService implements FlagsService {
  isEnabled() {
    return false;
  }

  finalize() {}

  /**
   * @returns array of all enabled experiments.
   */
  get enabledExperiments() {
    return [];
  }
}

class MockAuthService implements AuthService {
  clearCache() {}

  get isAuthed() {
    return false;
  }

  authCheck() {
    return Promise.resolve(false);
  }

  baseUrl = '';

  setup() {}

  finalize() {}

  fetch() {
    const blob = new Blob();
    const init = {status: 200, statusText: 'Ack'};
    const response = new Response(blob, init);
    return Promise.resolve(response);
  }
}

let appContext: (AppContext & Finalizable) | undefined;

// Setup mocks for appContext.
// This is a temporary solution
// TODO(dmfilippov): find a better solution for gr-diff
export function initDiffAppContext() {
  const appRegistry: Registry<AppContext> = {
    flagsService: (_ctx: Partial<AppContext>) =>
      new MockFlagsService(),
    reportingService: (_ctx: Partial<AppContext>) => grReportingMock,
    eventEmitter: (_ctx: Partial<AppContext>) => new EventEmitter(),
    authService: (_ctx: Partial<AppContext>) => new MockAuthService(),
    restApiService: (_ctx: Partial<AppContext>) => grRestApiMock,
    changeService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new ChangeService(ctx.restApiService!);
    },
    commentsService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new CommentsService(ctx.restApiService!);
    },
    checksService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new ChecksService(ctx.reportingService!);
    },
    jsApiService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new GrJsApiInterface(ctx.reportingService!);
    },
    storageService: (_ctx: Partial<AppContext>) => grStorageMock,
    configService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new ConfigService(ctx.restApiService!);
    },
    userService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new UserService(ctx.restApiService!);
    },
    shortcutsService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new ShortcutsService(ctx.reportingService!);
    },
    browserModel: (_ctx: Partial<AppContext>) => new BrowserModel(),
  };
  appContext = create<AppContext>(appRegistry);
  injectAppContext(appContext);
}


export function finalizeDiffAppContext() {
  appContext?.finalize();
  appContext = undefined;
}
