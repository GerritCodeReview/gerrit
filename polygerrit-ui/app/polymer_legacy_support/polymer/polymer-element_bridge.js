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
import './lib/mixins/element-mixin_bridge.js';
import './lib/utils/html-tag_bridge.js';

import {PolymerElement as _PolymerElement} from '@polymer/polymer/polymer-element.js';

goog.declareModuleId('HtmlImportsNamespace.Polymer.PolymerElement');

// This import then const trick is to be clear to the compiler that the
// imported symbols can't be mutated.
/** @constructor */
const PolymerElement = _PolymerElement;

/**
 * @const
 * @constructor
 */
Polymer.Element = PolymerElement;

// Polymer.html already set in html-tag_bridge.js
