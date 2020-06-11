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
import {initAppContext} from '../services/app-context-init';
import {
  initVisibilityReporter,
  initPerformanceReporter,
  initErrorReporter,
} from '../services/gr-reporting/gr-reporting_impl';
import {appContext} from '../services/app-context';

initAppContext();
initVisibilityReporter(appContext);
initPerformanceReporter(appContext);
initErrorReporter(appContext);

if ('serviceWorker' in navigator) {
  // Use the window load event to keep the page load performant
  setTimeout(() => {
    console.log('Register service worker');
    navigator.serviceWorker.register('/service-worker.js');
    // https://github.com/w3c/ServiceWorker/issues/940
    // navigator.serviceWorker.register('/service-worker.js');
  }, 2000);
}