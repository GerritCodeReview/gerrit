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

import './boot_bridge.js';
import {isPath as _isPath, root as _root, isAncestor as _isAncestor, isDescendant as _isDescendant, translate as _translate, matches as _matches, normalize as _normalize, split as _split, get as _get, set as _set, isDeep as _isDeep} from '@polymer/polymer/lib/utils/path.js';

// This import then const trick is to be clear to the compiler that the
// imported symbols can't be mutated.
const isPath = _isPath;
const root = _root;
const isAncestor = _isAncestor;
const isDescendant = _isDescendant;
const translate = _translate;
const matches = _matches;
const normalize = _normalize;
const split = _split;
const get = _get;
const set = _set;
const isDeep = _isDeep;


goog.declareModuleId('HtmlImportsNamespace.Polymer.Lib.Utils.Path');

/** @const */
Polymer.Path = {isPath, root, isAncestor, isDescendant, translate, matches, normalize, split, get, set, isDeep};
