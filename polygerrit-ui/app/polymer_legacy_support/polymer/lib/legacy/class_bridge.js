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

import './legacy-element-mixin_bridge.js';
import '../elements/dom-module_bridge.js';
import '../utils/settings_bridge.js';
import '../utils/boot_bridge.js';

import {Class as _Class, mixinBehaviors as _mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';

goog.declareModuleId('HtmlImportsNamespace.Polymer.Lib.Legacy.Class');

// This import then const trick is to be clear to the compiler that the
// imported symbols can't be mutated.
const Class = _Class;
const mixinBehaviors = _mixinBehaviors;

/** @const */
Polymer.Class = Class;

/** @const */
Polymer.mixinBehaviors = mixinBehaviors;
