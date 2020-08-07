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

// This file is a replacement for the
// polymer-bridges/polymer/polymer.html file. The polymer.html file loads
// other scripts to setup different global variables. Because plugins
// expects that Polymer is available we must setup all Polymer global
// variables
//
// The bundled-polymer imports all scripts in the same order as the
// polymer.html does and must be imported in all es6-modules instead
// of the polymer.html file.

import 'polymer-bridges/polymer/lib/utils/boot_bridge';
import 'polymer-bridges/polymer/lib/utils/resolve-url_bridge';
import 'polymer-bridges/polymer/lib/utils/settings_bridge';
import 'polymer-bridges/polymer/lib/utils/mixin_bridge';
import 'polymer-bridges/polymer/lib/elements/dom-module_bridge';
import 'polymer-bridges/polymer/lib/utils/style-gather_bridge';
import 'polymer-bridges/polymer/lib/utils/path_bridge';
import 'polymer-bridges/polymer/lib/utils/case-map_bridge';
import 'polymer-bridges/polymer/lib/utils/async_bridge';
import 'polymer-bridges/polymer/lib/utils/wrap_bridge';
import 'polymer-bridges/polymer/lib/mixins/properties-changed_bridge';
import 'polymer-bridges/polymer/lib/mixins/property-accessors_bridge';
import 'polymer-bridges/polymer/lib/mixins/template-stamp_bridge';
import 'polymer-bridges/polymer/lib/mixins/property-effects_bridge';
import 'polymer-bridges/polymer/lib/utils/telemetry_bridge';
import 'polymer-bridges/polymer/lib/mixins/properties-mixin_bridge';
import 'polymer-bridges/polymer/lib/utils/debounce_bridge';
import 'polymer-bridges/polymer/lib/utils/gestures_bridge';
import 'polymer-bridges/polymer/lib/mixins/gesture-event-listeners_bridge';
import 'polymer-bridges/polymer/lib/mixins/dir-mixin_bridge';
import 'polymer-bridges/polymer/lib/utils/render-status_bridge';
import 'polymer-bridges/polymer/lib/utils/unresolved_bridge';
import 'polymer-bridges/polymer/lib/utils/array-splice_bridge';
import 'polymer-bridges/polymer/lib/utils/flattened-nodes-observer_bridge';
import 'polymer-bridges/polymer/lib/utils/flush_bridge';
import 'polymer-bridges/polymer/lib/legacy/polymer.dom_bridge';
import 'polymer-bridges/polymer/lib/legacy/legacy-element-mixin_bridge';
import 'polymer-bridges/polymer/lib/legacy/class_bridge';
import 'polymer-bridges/polymer/lib/legacy/polymer-fn_bridge';
import 'polymer-bridges/polymer/lib/mixins/mutable-data_bridge';
import 'polymer-bridges/polymer/lib/utils/templatize_bridge';
import 'polymer-bridges/polymer/lib/legacy/templatizer-behavior_bridge';
import 'polymer-bridges/polymer/lib/elements/dom-bind_bridge';
import 'polymer-bridges/polymer/lib/utils/html-tag_bridge';
import 'polymer-bridges/polymer/polymer-element_bridge';
import 'polymer-bridges/polymer/lib/elements/dom-repeat_bridge';
import 'polymer-bridges/polymer/lib/elements/dom-if_bridge';
import 'polymer-bridges/polymer/lib/elements/array-selector_bridge';
import 'polymer-bridges/polymer/lib/elements/custom-style_bridge';
import 'polymer-bridges/polymer/lib/legacy/mutable-data-behavior_bridge';
import 'polymer-bridges/polymer/polymer-legacy_bridge';
import {importHref} from './import-href';

window.Polymer.importHref = importHref;
