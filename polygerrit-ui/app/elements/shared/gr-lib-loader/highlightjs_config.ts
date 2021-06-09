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
import '../gr-js-api-interface/gr-js-api-interface';

import {EventType} from '../../../api/plugin';
import {appContext} from '../../../services/app-context';

import {LibraryConfig} from './gr-lib-loader';

export const HLJS_LIBRARY_CONFIG: LibraryConfig = {
  // preloaded in PolyGerritIndexHtml.soy
  src: 'bower_components/highlightjs/highlight.min.js',
  checkPresent: () => window.hljs !== undefined,
  configureCallback: () => {
    window.hljs!.configure({classPrefix: 'gr-diff gr-syntax gr-syntax-'});
    appContext.jsApiService.handleEvent(EventType.HIGHLIGHTJS_LOADED, {
      hljs: window.hljs,
    });
    return window.hljs;
  },
};
