/**
@license
Copyright (c) 2019 The Polymer Project Authors. All rights reserved.
This code may only be used under the BSD style license found at
http://polymer.github.io/LICENSE.txt The complete set of authors may be found at
http://polymer.github.io/AUTHORS.txt The complete set of contributors may be
found at http://polymer.github.io/CONTRIBUTORS.txt Code distributed by Google as
part of the polymer project is also subject to an additional IP rights grant
found at http://polymer.github.io/PATENTS.txt
*/

/**
 * @fileoverview This is a google3-only file that exists to do a goog.require
 *   of the es5 custom elements shim. See go/es5-custom-elements for more info.
 *
 * It's written slightly strangely because we have a small number of clients
 * which are still migrating from legacy test setups where goog.require won't
 * work. Fortunately for such cases, they also don't need the es5 shim because
 * they're also not transpiling. For the time being, we'll just make that
 * goog.require for such users a no-op.
 */

// polyfill the global 'COMPILED' define without assuming that base.js has
// loaded.
window['COMPILED'] = window['COMPILED'] || false;

function polyfillGoogRequire() {
  // For code that doesn't compile or use the goog debug loader, we need to stub
  // out goog.require

  if (!window.goog) {
    window.goog = {
      require(id) {
        if (id === 'webcomponentsjs.custom_elements.auto_es5_shim') {
          // we're in a (deprecated) uncompiled configuration, that file is
          // unnecessary
          return;
        }
        throw new Error(
            `Called goog.require without loading //javascript/closure:base`);
      }
    };
    // For uncompiled tests that want to only do a goog.require if it's a
    // real implementation of goog.require.
    window.goog.require['isDevModeNoOpImpl'] = true;
  }
}
if (!COMPILED) {
  polyfillGoogRequire();
}

/** @suppress {extraRequire} */
goog.require('webcomponentsjs.custom_elements.auto_es5_shim');

export {};
