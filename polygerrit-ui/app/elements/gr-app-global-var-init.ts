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

/**
 * @fileoverview This file is a backwards-compatibility shim.
 * Before Polygerrit converted to ES Modules, it exposes some variables out onto
 * the global namespace. Plugins can depend on these variables and we must
 * expose these variables until plugins switch to direct import from polygerrit.
 */

import {GrAnnotation} from './diff/gr-diff-highlight/gr-annotation';
import {page} from '../utils/page-wrapper-utils';
import {GrPluginActionContext} from './shared/gr-js-api-interface/gr-plugin-action-context';
import {initGerritPluginApi} from './shared/gr-js-api-interface/gr-gerrit';

export function initGlobalVariables() {
  window.GrAnnotation = GrAnnotation;
  window.page = page;
  window.GrPluginActionContext = GrPluginActionContext;
  initGerritPluginApi();
}
