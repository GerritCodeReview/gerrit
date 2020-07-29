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
import {appContext, AppContext} from './app-context';
import {FlagsServiceImplementation} from './flags/flags_impl';
import {GrReporting} from './gr-reporting/gr-reporting_impl';
import {EventEmitter} from './gr-event-interface/gr-event-interface_impl';
import {Auth} from './gr-auth/gr-auth_impl';

type ServiceName = keyof AppContext;
type ServiceCreator<T> = () => T;

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const initializedServices: Map<ServiceName, any> = new Map();

function getService<K extends ServiceName>(
  serviceName: K,
  serviceCreator: ServiceCreator<AppContext[K]>
): AppContext[K] {
  if (!initializedServices.has(serviceName)) {
    initializedServices.set(serviceName, serviceCreator());
  }
  return initializedServices.get(serviceName);
}

/**
 * The AppContext lazy initializator for all services
 */
export function initAppContext() {
  function populateAppContext(
    serviceCreators: {[P in ServiceName]: ServiceCreator<AppContext[P]>}
  ) {
    const registeredServices = Object.keys(serviceCreators).reduce(
      (registeredServices, key) => {
        const serviceName = key as ServiceName;
        const serviceCreator = serviceCreators[serviceName];
        registeredServices[serviceName] = {
          get() {
            return getService(serviceName, serviceCreator);
          },
        };
        return registeredServices;
      },
      {} as PropertyDescriptorMap
    );
    Object.defineProperties(appContext, registeredServices);
  }

  populateAppContext({
    flagsService: () => new FlagsServiceImplementation(),
    reportingService: () => new GrReporting(appContext.flagsService),
    eventEmitter: () => new EventEmitter(),
    authService: () => new Auth(appContext.eventEmitter),
  });
}
