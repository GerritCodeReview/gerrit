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
 *
 *     This file specifically is the root bridge file, as it ensures that
 *     calls to certain goog.* methods will work even if
 *     //javascript/closure:base is not loaded.
 */

import '@polymer/polymer/lib/utils/boot.js';
import './require_shim.js';

/**
 * @suppress {visibility} goog.isInEs6ModuleLoader_ This is for debug-mode code
 *     to work with HTML Imports + native ES module loading.
 */
function polyfillDeclareNamespace() {
  // For code that doesn't compile or use the goog debug loader, we need to stub
  // out goog.declareModuleId
  //
  // We only use it for ordering and strict deps checking, and when loaded
  // uncompiled with pure HTML Imports and ES modules, we don't need either of
  // these.

  // This is a wrapper around goog.declareModuleId that is a no-op if
  // this code is loaded through native ES modules
  // (almost certainly via HTML Imports) rather than the closure debug
  // loader, but should behave exactly the same when compiled.
  // Note that combining HTML Imports with the closure debug loader
  // has never been supported, and would always result in duplicated code.
  // This is introducing another way that this could happen, but hopefully
  // this will not affect anyone who was already working.
  let realDeclareModuleId =
      window.goog && window.goog.declareModuleId || (() => {});
  const lenientDeclareModuleId = function(id) {
    if (!goog.isInEs6ModuleLoader_()) {
      // The real declareNamespace would throw. Don't do that.
      return;
    }
    return realDeclareModuleId(id);
  };

  if (window.goog.declareModuleId) {
    // Closure base already loaded, this is the happy normal case. Just
    // make declareModuleId more lenient.
    goog.declareModuleId = lenientDeclareModuleId;
  } else {
    // Closure base is not loaded. We need to handle two cases here:
    //     - the case where closure base will be loaded later
    //     - the case where Polymer core's usage is the only usage of
    //       closure base in the whole program

    // Bootstrap parts of goog that lenientDeclareModuleId needs.
    window.goog.isInEs6ModuleLoader_ = () => {
      return false;
    };

    // Ensure that declareModuleId is lenient even if goog is loaded
    // later.
    Object.defineProperty(window.goog, 'declareModuleId', {
      get() {
        return lenientDeclareModuleId;
      },
      set(newRealDeclareModuleId) {
        realDeclareModuleId = newRealDeclareModuleId;
        return lenientDeclareModuleId;
      }
    });
  }
}
if (!COMPILED) {
  polyfillDeclareNamespace();
}
goog.declareModuleId('HtmlImportsNamespace.Polymer.Lib.Utils.Boot');

const userPolymer = window.Polymer;

/**
 * @namespace Polymer
 * @summary Polymer is a lightweight library built on top of the web
 *   standards-based Web Components API's, and makes it easy to build your
 *   own custom HTML elements.
 * @param {!PolymerInit} info Prototype for the custom element. It must contain
 *   an `is` property to specify the element name. Other properties populate
 *   the element prototype. The `properties`, `observers`, `hostAttributes`,
 *   and `listeners` properties are processed to create element features.
 * @return {function(new:HTMLElement)} Returns a custom element class for the
 *   given provided prototype `info` object. The name of the element is
 *   given by `info.is`.
 */
window.Polymer = function(info) {
  return window.Polymer._polymerFn(info);
};

// support user settings on the Polymer object
if (userPolymer) {
  Object.assign(Polymer, userPolymer);
}

// To be plugged by legacy implementation if loaded
/* eslint-disable valid-jsdoc */
/**
 * @param {!PolymerInit} info Prototype for the custom element. It must contain
 *   an `is` property to specify the element name. Other properties populate
 *   the element prototype. The `properties`, `observers`, `hostAttributes`,
 *   and `listeners` properties are processed to create element features.
 * @return {function(new:HTMLElement)} Returns a custom element class for the given provided
 *   prototype `info` object. The name of the element if given by `info.is`.
 */
Polymer._polymerFn = function(info) { // eslint-disable-line no-unused-vars
  throw new Error('Load polymer.html to use the Polymer() function.');
};
/* eslint-enable */
