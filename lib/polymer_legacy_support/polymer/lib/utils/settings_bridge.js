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
import './resolve-url_bridge.js';

// Use a settings object to make things easier on js compiler.
import * as settingsObj from '@polymer/polymer/lib/utils/settings.js';

goog.declareModuleId('HtmlImportsNamespace.Polymer.Lib.Utils.Settings');

/**
 * Sets the global, legacy settings.
 *
 * @deprecated
 * @namespace
 * @memberof Polymer
 */
Polymer.Settings = {
  useShadow: settingsObj.useShadow,
  useNativeCSSProperties: settingsObj.useNativeCSSProperties,
  useNativeCustomElements: settingsObj.useNativeCustomElements,
  get rootPath() {
    return settingsObj.rootPath;
  },
  setRootPath: settingsObj.setRootPath,
  get sanitizeDOMValue() {
    return settingsObj.sanitizeDOMValue;
  },
  setSanitizeDOMValue: settingsObj.setSanitizeDOMValue,
  get passiveTouchGestures() {
    return settingsObj.passiveTouchGestures;
  },
  setPassiveTouchGestures: settingsObj.setPassiveTouchGestures,
  get strictTemplatePolicy() {
    return settingsObj.strictTemplatePolicy;
  },
  setStrictTemplatePolicy: settingsObj.setStrictTemplatePolicy,
  get allowTemplateFromDomModule() {
    return settingsObj.allowTemplateFromDomModule;
  },
  setAllowTemplateFromDomModule: settingsObj.setAllowTemplateFromDomModule,
  get legacyOptimizations() {
    return settingsObj.legacyOptimizations;
  },
  setLegacyOptimizations: settingsObj.setLegacyOptimizations,
  get syncInitialRender() {
    return settingsObj.syncInitialRender;
  },
  setSyncInitialRender: settingsObj.setSyncInitialRender,
};

// Pass settings from Polymer global into settings module
if (Polymer.rootPath !== undefined) {
  settingsObj.setRootPath(Polymer.rootPath);
}
if (Polymer.sanitizeDOMValue !== undefined) {
  settingsObj.setSanitizeDOMValue(Polymer.sanitizeDOMValue);
}
if (Polymer.passiveTouchGestures !== undefined) {
  settingsObj.setPassiveTouchGestures(Polymer.passiveTouchGestures);
}
if (Polymer.strictTemplatePolicy !== undefined) {
  settingsObj.setStrictTemplatePolicy(Polymer.strictTemplatePolicy);
}
if (Polymer.allowTemplateFromDomModule !== undefined) {
  settingsObj.setAllowTemplateFromDomModule(Polymer.allowTemplateFromDomModule);
} else {
  // For the HTML Imports bridge, default to allowing <dom-module>
  settingsObj.setAllowTemplateFromDomModule(true);
}
if (Polymer.legacyOptimizations !== undefined) {
  settingsObj.setLegacyOptimizations(Polymer.legacyOptimizations);
}
if (Polymer.syncInitialRender !== undefined) {
  settingsObj.setSyncInitialRender(Polymer.syncInitialRender);
}

// Workaround to reduce the impact of b/111016838 to the application
// bootup phase.
// This ensures that even if code assigns to Polymer.sanitizeDOMValue after
// this file has run, we will still update the sanitizeDOMValue implementation
// used during databinding.
Object.defineProperty(Polymer, 'sanitizeDOMValue', {
  get() {
    return settingsObj.sanitizeDOMValue;
  },
  set(sdv) {
    settingsObj.setSanitizeDOMValue(sdv);
  }
});
