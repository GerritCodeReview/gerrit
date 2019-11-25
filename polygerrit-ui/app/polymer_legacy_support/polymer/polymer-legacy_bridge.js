/**
@license
Copyright (c) 2017 The Polymer Project Authors. All rights reserved.
This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE.txt
The complete set of authors may be found at http://polymer.github.io/AUTHORS.txt
The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS.txt
Code distributed by Google as part of the polymer project is also
subject to an additional IP rights grant found at http://polymer.github.io/PATENTS.txt
*/

/**
 * @fileoverview This file is a backwards-compatibility shim. Before Polymer
 *     converted to ES Modules, it wrote its API out onto the global Polymer
 *     object. The *_bridge.js files (like this one) maintain compatibility
 *     with that API.
 */

import './lib/utils/boot_bridge.js';
import './lib/utils/html-tag_bridge.js';
import './lib/legacy/polymer-fn_bridge.js';
import './lib/legacy/legacy-element-mixin_bridge.js';
import './lib/legacy/templatizer-behavior_bridge.js';
import './lib/elements/dom-bind_bridge.js';
import './lib/elements/dom-repeat_bridge.js';
import './lib/elements/dom-if_bridge.js';
import './lib/elements/array-selector_bridge.js';
import './lib/elements/custom-style_bridge.js';
import './lib/legacy/mutable-data-behavior_bridge.js';

import {Base as _Base} from '@polymer/polymer/polymer-legacy.js';

goog.declareModuleId('HtmlImportsNamespace.Polymer.Polymer');

// This import then const trick is to be clear to the compiler that the
// imported symbols can't be mutated.
const Base = _Base;

/** @const */
Polymer.Base = Base;

// Polymer.html already set in html-tag_bridge.js
