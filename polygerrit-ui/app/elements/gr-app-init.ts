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

interface UninitializedPolymer {
  lazyRegister: boolean;
}

if (!window.Polymer) {
  // Without as... it violates internal google rules.
  ((window.Polymer as unknown) as UninitializedPolymer) = {
    lazyRegister: true,
  };
}

initAppContext();
initVisibilityReporter(appContext);
initPerformanceReporter(appContext);
initErrorReporter(appContext);
