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
import {AppContext} from '../services/app-context';
import {AuthService} from '../services/gr-auth/gr-auth';
import {FlagsService} from '../services/flags/flags';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';

class MockFlagsService implements FlagsService {
  isEnabled() {
    return false;
  }

  finalize() {}

  /**
   * @return array of all enabled experiments.
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

// Setup mocks for appContext.
// This is a temporary solution
// TODO(dmfilippov): find a better solution for gr-diff
export function createDiffAppContext(): AppContext & Finalizable {
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
    jsApiService: (_ctx: Partial<AppContext>) => {
      throw new Error('jsApiService is not implemented');
    },
    storageService: (_ctx: Partial<AppContext>) => {
      throw new Error('storageService is not implemented');
    },
    userModel: (_ctx: Partial<AppContext>) => {
      throw new Error('userModel is not implemented');
    },
    routerModel: (_ctx: Partial<AppContext>) => {
      throw new Error('routerModel is not implemented');
    },
    shortcutsService: (_ctx: Partial<AppContext>) => {
      throw new Error('shortcutsService is not implemented');
    },
    pluginsModel: (_ctx: Partial<AppContext>) => {
      throw new Error('pluginsModel is not implemented');
    },
    highlightService: (_ctx: Partial<AppContext>) => {
      throw new Error('highlightService is not implemented');
    },
  };
  return create<AppContext>(appRegistry);
}
