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

import '../utils/boot_bridge.js';
import '../utils/settings_bridge.js';
import '../utils/flattened-nodes-observer_bridge.js';
import '../utils/flush_bridge.js';

import {DomApi as _DomApi, EventApi as _EventApi, dom as _dom, matchesSelector as _matchesSelector, flush as _flush, addDebouncer as _addDebouncer} from '@polymer/polymer/lib/legacy/polymer.dom.js';

// This import then const trick is to be clear to the compiler that the
// imported symbols can't be mutated.
const DomApi = _DomApi;
const EventApi = _EventApi;
const dom = _dom;
const matchesSelector = _matchesSelector;
const flush = _flush;
const addDebouncer = _addDebouncer;

goog.declareModuleId('HtmlImportsNamespace.Polymer.Lib.Legacy.PolymerDom');

/** @const */
Polymer.DomApi = DomApi;

/** @const */
Polymer.EventApi = EventApi;

/** @const */
Polymer.dom = dom;

/** @const */
Polymer.dom.matchesSelector = matchesSelector;

/** @const */
Polymer.dom.flush = flush;

/** @const */
Polymer.dom.addDebouncer = addDebouncer;
