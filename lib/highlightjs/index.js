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

import hljs from 'highlight.js';
import soy from 'highlightjs-closure-templates';
import epp from 'highlightjs-epp';
import iecst from 'highlightjs-structured-text';
import vue from 'highlightjs-vue';

hljs.registerLanguage('soy', soy);
hljs.registerLanguage('epp', epp);
hljs.registerLanguage('iecst', iecst);
hljs.registerLanguage('vue', vue);

export default hljs;
