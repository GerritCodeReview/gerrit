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

// Init app context before any other imports
import {initAppContext} from '../services/app-context-init';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';
import {AppContext, appContext} from '../services/app-context';
import {grRestApiMock} from './mocks/gr-rest-api_mock';
import {grStorageMock} from '../services/storage/gr-storage_mock';
import {GrAuthMock} from '../services/gr-auth/gr-auth_mock';

export function _testOnlyInitAppContext() {
  initAppContext();

  function setMock<T extends keyof AppContext>(
    serviceName: T,
    setupMock: AppContext[T]
  ) {
    Object.defineProperty(appContext, serviceName, {
      get() {
        return setupMock;
      },
    });
  }
  setMock('reportingService', grReportingMock);
  setMock('restApiService', grRestApiMock);
  setMock('storageService', grStorageMock);
  setMock('authService', new GrAuthMock(appContext.eventEmitter));
}
