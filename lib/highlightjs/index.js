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

import hljs from '../../polygerrit-ui/app/node_modules/highlight.js';
import soy from '../../polygerrit-ui/app/node_modules/highlightjs-closure-templates';
import epp from '../../polygerrit-ui/app/node_modules/highlightjs-epp';
import iecst from '../../polygerrit-ui/app/node_modules/highlightjs-structured-text';
import ttcn3 from '../../polygerrit-ui/app/node_modules/highlightjs-ttcn3';
import vue from '../../polygerrit-ui/app/node_modules/highlightjs-vue';
import gn from './gn';

hljs.registerLanguage('soy', soy);
hljs.registerLanguage('epp', epp);
hljs.registerLanguage('iecst', iecst);
hljs.registerLanguage('ttcn3', ttcn3);
hljs.registerLanguage('vue', vue);
hljs.registerLanguage('gn', gn);

// Patch the Objective-C language definition to support C++14 digit separators.
// TODO(upstream): Remove this workaround once highlight.js is upgraded to a
// version that includes https://github.com/highlightjs/highlight.js/pull/4322
const objc = hljs.getLanguage('objectivec');
const cpp = hljs.getLanguage('cpp');
if (objc && cpp) {
  const cppNumberMode = cpp.contains.find(m => m.className === 'number' || m.scope === 'number');
  const objcContains = objc.contains;
  const objcNumIdx = objcContains.findIndex(m => m.scope === 'number' || m.className === 'number');
  if (cppNumberMode && objcNumIdx !== -1) {
    objcContains[objcNumIdx] = cppNumberMode;
  }
}

export default hljs;
