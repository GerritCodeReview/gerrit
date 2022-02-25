/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {LibraryConfig} from './gr-lib-loader';

export const RESEMBLEJS_LIBRARY_CONFIG: LibraryConfig = {
  src: 'bower_components/resemblejs/resemble.js',
  checkPresent: () => window.resemble !== undefined,
  configureCallback: () => {
    window.resemble.outputSettings({
      errorColor: {red: 255, green: 0, blue: 255},
      errorType: 'flat',
      transparency: 0,
      // Disable large image threshold; by default this otherwise skips pixels
      // if width or height exceed 1200 pixels.
      largeImageThreshold: 0,
    });
    return window.resemble;
  },
};
