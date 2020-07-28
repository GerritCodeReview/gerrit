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
import {FlagsService} from './flags/flags';

interface AppContext {
  flagsService: FlagsService;
  reportingService: any;
  eventEmitter: any;
  authService: any;
}

/**
 * The AppContext holds immortal singleton instances of services. It's a
 * convenient way to provide singletons that can be swapped out for testing.
 *
 * AppContext is initialized in ./app-context-init.js
 */
export const appContext: AppContext = {
  // It is guaranteed that all fields in appContext is always initialized
  // (except for shared gr-diff)
  // We are using null! to suppress compiler message that elements here can't
  // be null
  flagsService: null!,
  reportingService: null!,
  eventEmitter: null!,
  authService: null!,
};
