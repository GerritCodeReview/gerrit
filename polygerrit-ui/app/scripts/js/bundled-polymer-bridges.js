/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// This file can't be converted to TS - it imports some .js file which
// can't be imported into typescript

// This file is a replacement for the
// polymer-bridges/polymer/polymer.html file. The polymer.html file loads
// other scripts to setup different global variables. Because plugins
// expects that Polymer is available we must setup all Polymer global
// variables
//
// The bundled-polymer.js imports all scripts in the same order as the
// polymer.html does and must be imported in all es6-modules instead
// of the polymer.html file.

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

// This is needed due to the Polymer.IronFocusablesHelper in gr-overlay.ts
import 'polymer-bridges/iron-overlay-behavior/iron-focusables-helper_bridge.js';

