/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function(window) {
  'use strict';

  /**
   * Plugin popup API.
   * Provides method for opening and closing popups from plugin.
   * opt_moduleName is a name of custom element that will be automatically
   * inserted on popup opening.
   * @param {!Object} plugin
   * @param {opt_moduleName=} string
   */
  function GrPopupInterface(plugin, opt_moduleName) {
    this.plugin = plugin;
    this._openingPromise = null;
    this._popup = null;
    this._moduleName = opt_moduleName || null;
  }

  GrPopupInterface.prototype._getElement = function() {
    return Polymer.dom(this._popup);
  };

  /**
   * Opens the popup, inserts it into DOM over current UI.
   * Creates the popup if not previously created. Creates popup content element,
   * if it was provided with constructor.
   * @returns {!Promise<!Object>}
   */
  GrPopupInterface.prototype.open = function() {
    if (!this._openingPromise) {
      this._openingPromise =
          this.plugin.hook('plugin-overlay').getLastAttached()
              .then(hookEl => {
                const popup = document.createElement('gr-plugin-popup');
                if (this._moduleName) {
                  const el = Polymer.dom(popup).appendChild(
                      document.createElement(this._moduleName));
                  el.plugin = this.plugin;
                }
                this._popup = Polymer.dom(hookEl).appendChild(popup);
                Polymer.dom.flush();
                return this._popup.open().then(() => this);
              });
    }
    return this._openingPromise;
  };

  /**
   * Hides the popup.
   */
  GrPopupInterface.prototype.close = function() {
    if (!this._popup) { return; }
    this._popup.close();
    this._openingPromise = null;
  };

  window.GrPopupInterface = GrPopupInterface;
})(window);
