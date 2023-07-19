/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Init app context before any other imports
import {Finalizable} from '../services/registry';
import {AppContext} from '../services/app-context';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';
import {grRestApiMock} from './mocks/gr-rest-api_mock';
import {grStorageMock} from '../services/storage/gr-storage_mock';
import {GrAuthMock} from '../services/gr-auth/gr-auth_mock';
import {FlagsServiceImplementation} from '../services/flags/flags_impl';
import {MockHighlightService} from '../services/highlight/highlight-service-mock';
import {createAppDependencies, Creator} from '../services/app-context-init';
import {navigationToken} from '../elements/core/gr-navigation/gr-navigation';
import {DependencyToken} from '../models/dependency';
import {storageServiceToken} from '../services/storage/gr-storage_impl';
import {highlightServiceToken} from '../services/highlight/highlight-service';
import {
  diffModelToken,
  DiffModel,
} from '../embed/diff/gr-diff-model/gr-diff-model';

export function createTestAppContext(): AppContext & Finalizable {
  return {
    flagsService: new FlagsServiceImplementation(),
    reportingService: grReportingMock,
    authService: new GrAuthMock(),
    restApiService: grRestApiMock,   
  }
}

export function createTestDependencies(
  appContext: AppContext,
  resolver: <T>(token: DependencyToken<T>) => T
): Map<DependencyToken<unknown>, Creator<unknown>> {
  const dependencies = createAppDependencies(appContext, resolver);
  dependencies.set(storageServiceToken, () => grStorageMock);
  dependencies.set(navigationToken, () => {
    return {
      setUrl: () => {},
      replaceUrl: () => {},
      finalize: () => {},
      blockNavigation: () => {},
      releaseNavigation: () => {},
    };
  });
  dependencies.set(
    highlightServiceToken,
    () => new MockHighlightService(appContext.reportingService)
  );
  dependencies.set(diffModelToken, () => new DiffModel(document));
  return dependencies;
}
