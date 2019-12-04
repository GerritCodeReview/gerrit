/**
@license
Copyright (c) 2017 The Polymer Project Authors. All rights reserved.
This code may only be used under the BSD style license found at
http://polymer.github.io/LICENSE.txt The complete set of authors may be found at
http://polymer.github.io/AUTHORS.txt The complete set of contributors may be
found at http://polymer.github.io/CONTRIBUTORS.txt Code distributed by Google as
part of the polymer project is also subject to an additional IP rights grant
found at http://polymer.github.io/PATENTS.txt
*/

/**
 * @fileoverview This file is a backwards-compatibility shim. Before Polymer
 *     converted to ES Modules, it wrote its API out onto the global Polymer
 *     object. The *_bridge.js files (like this one) maintain compatibility
 *     with that API.
 */

import './boot_bridge.js';
import '../mixins/property-effects_bridge.js';
import '../mixins/mutable-data_bridge.js';

import {templatize as _templatize, modelForElement as _modelForElement, TemplateInstanceBase as _TemplateInstanceBase} from '@polymer/polymer/lib/utils/templatize.js';

// This import then const trick is to be clear to the compiler that the
// imported symbols can't be mutated.
const templatize = _templatize;
const modelForElement = _modelForElement;
const TemplateInstanceBase = _TemplateInstanceBase;


goog.declareModuleId('HtmlImportsNamespace.Polymer.Lib.Utils.Templatize');

/** @const */
Polymer.Templatize = {templatize, modelForElement};
/** @const */
Polymer.TemplateInstanceBase = TemplateInstanceBase;
