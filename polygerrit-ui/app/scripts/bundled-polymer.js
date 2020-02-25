// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import 'polymer-bridges/polymer/lib/utils/boot_bridge.js';
import 'polymer-bridges/polymer/lib/utils/resolve-url_bridge.js';
import 'polymer-bridges/polymer/lib/utils/settings_bridge.js';
import 'polymer-bridges/polymer/lib/utils/mixin_bridge.js';
import 'polymer-bridges/polymer/lib/elements/dom-module_bridge.js';
import 'polymer-bridges/polymer/lib/utils/style-gather_bridge.js';
import 'polymer-bridges/polymer/lib/utils/path_bridge.js';
import 'polymer-bridges/polymer/lib/utils/case-map_bridge.js';
import 'polymer-bridges/polymer/lib/utils/async_bridge.js';
import 'polymer-bridges/polymer/lib/utils/wrap_bridge.js';
import 'polymer-bridges/polymer/lib/mixins/properties-changed_bridge.js';
import 'polymer-bridges/polymer/lib/mixins/property-accessors_bridge.js';
import 'polymer-bridges/polymer/lib/mixins/template-stamp_bridge.js';
import 'polymer-bridges/polymer/lib/mixins/property-effects_bridge.js';
import 'polymer-bridges/polymer/lib/utils/telemetry_bridge.js';
import 'polymer-bridges/polymer/lib/mixins/properties-mixin_bridge.js';
import 'polymer-bridges/polymer/lib/utils/debounce_bridge.js';
import 'polymer-bridges/polymer/lib/utils/gestures_bridge.js';
import 'polymer-bridges/polymer/lib/mixins/gesture-event-listeners_bridge.js';
import 'polymer-bridges/polymer/lib/mixins/dir-mixin_bridge.js';
import 'polymer-bridges/polymer/lib/utils/render-status_bridge.js';
import 'polymer-bridges/polymer/lib/utils/unresolved_bridge.js';
import 'polymer-bridges/polymer/lib/utils/array-splice_bridge.js';
import 'polymer-bridges/polymer/lib/utils/flattened-nodes-observer_bridge.js';
import 'polymer-bridges/polymer/lib/utils/flush_bridge.js';
import 'polymer-bridges/polymer/lib/legacy/polymer.dom_bridge.js';
import 'polymer-bridges/polymer/lib/legacy/legacy-element-mixin_bridge.js';
import 'polymer-bridges/polymer/lib/legacy/class_bridge.js';
import 'polymer-bridges/polymer/lib/legacy/polymer-fn_bridge.js';
import 'polymer-bridges/polymer/lib/mixins/mutable-data_bridge.js';
import 'polymer-bridges/polymer/lib/utils/templatize_bridge.js';
import 'polymer-bridges/polymer/lib/legacy/templatizer-behavior_bridge.js';
import 'polymer-bridges/polymer/lib/elements/dom-bind_bridge.js';
import 'polymer-bridges/polymer/lib/utils/html-tag_bridge.js';
import 'polymer-bridges/polymer/polymer-element_bridge.js';
import 'polymer-bridges/polymer/lib/elements/dom-repeat_bridge.js';
import 'polymer-bridges/polymer/lib/elements/dom-if_bridge.js';
import 'polymer-bridges/polymer/lib/elements/array-selector_bridge.js';
import 'polymer-bridges/polymer/lib/elements/custom-style_bridge.js';
import 'polymer-bridges/polymer/lib/legacy/mutable-data-behavior_bridge.js';
import 'polymer-bridges/polymer/polymer-legacy_bridge.js';
import {importHref} from './import-href.js';

Polymer.importHref = importHref;
