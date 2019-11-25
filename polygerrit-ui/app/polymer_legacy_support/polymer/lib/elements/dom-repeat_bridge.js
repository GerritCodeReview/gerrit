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

import '../../polymer-element_bridge.js';

import '../utils/templatize_bridge.js';
import '../utils/debounce_bridge.js';
import '../utils/flush_bridge.js';
import '../mixins/mutable-data_bridge.js';
import '../utils/path_bridge.js';
import '../utils/async_bridge.js';

import {DomRepeat} from '@polymer/polymer/lib/elements/dom-repeat.js';

goog.declareModuleId('HtmlImportsNamespace.Polymer.Lib.Elements.DomRepeat');

/** @const */
Polymer.DomRepeat = DomRepeat;
