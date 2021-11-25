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
import {AppContext, injectAppContext} from '../services/app-context';
import {AuthService} from '../services/gr-auth/gr-auth';
import {FlagsService} from '../services/flags/flags';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';

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
    flagsService: (_ctx: Partial<AppContext>) => new MockFlagsService(),
    authService: (_ctx: Partial<AppContext>) => new MockAuthService(),
    reportingService: (_ctx: Partial<AppContext>) => grReportingMock,
    eventEmitter: (_ctx: Partial<AppContext>) => {
      throw new Error('eventEmitter is not implemented');
    },
    restApiService: (_ctx: Partial<AppContext>) => {
      throw new Error('restApiService is not implemented');
    },
    changeService: (_ctx: Partial<AppContext>) => {
      throw new Error('changeService is not implemented');
    },
    commentsService: (_ctx: Partial<AppContext>) => {
      throw new Error('commentsService is not implemented');
    },
    checksService: (_ctx: Partial<AppContext>) => {
      throw new Error('checksService is not implemented');
    },
    jsApiService: (_ctx: Partial<AppContext>) => {
      throw new Error('jsApiService is not implemented');
    },
    storageService: (_ctx: Partial<AppContext>) => {
      throw new Error('storageService is not implemented');
    },
    configModel: (_ctx: Partial<AppContext>) => {
      throw new Error('configModel is not implemented');
    },
    userModel: (_ctx: Partial<AppContext>) => {
      throw new Error('userModel is not implemented');
    },
    shortcutsService: (_ctx: Partial<AppContext>) => {
      throw new Error('shortcutsService is not implemented');
    },
    browserModel: (_ctx: Partial<AppContext>) => {
      throw new Error('browserModel is not implemented');
    },
  };
  appContext = create<AppContext>(appRegistry);
  injectAppContext(appContext);
}
