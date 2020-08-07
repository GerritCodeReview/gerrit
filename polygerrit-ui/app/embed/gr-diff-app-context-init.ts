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

import {appContext} from '../services/app-context';
import {FlagsService} from '../services/flags/flags';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';
import {
  AuthService,
  GetTokenCallback,
  AuthRequestInit,
  DefaultAuthOptions,
} from '../services/gr-auth/gr-auth';

class MockFlagsService implements FlagsService {
  isEnabled(experimentId: string) {
    return false;
  }

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

  setup(getToken: GetTokenCallback, defaultOptions: DefaultAuthOptions) {}

  fetch(url: string, opt_options?: AuthRequestInit) {
    return Promise.resolve({} as Response);
  }
}

// Setup mocks for appContext.
// This is a temporary solution
// TODO(dmfilippov): find a better solution for gr-diff
export function initDiffAppContext() {
  function setMock(serviceName: string, setupMock: unknown) {
    Object.defineProperty(appContext, serviceName, {
      get() {
        return setupMock;
      },
    });
  }
  setMock('flagsService', new MockFlagsService());
  setMock('reportingService', grReportingMock);
  setMock('authService', new MockAuthService());
}
