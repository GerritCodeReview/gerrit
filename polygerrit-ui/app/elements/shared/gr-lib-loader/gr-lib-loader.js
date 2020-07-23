/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../gr-js-api-interface/gr-js-api-interface.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-lib-loader_html.js';

// preloaded in PolyGerritIndexHtml.soy
const HLJS_PATH = 'bower_components/highlightjs/highlight.min.js';

/** @extends PolymerElement */
class GrLibLoader extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-lib-loader'; }

  static get properties() {
    return {
      _hljsState: {
        type: Object,

        // NOTE: intended singleton.
        value: {
          configured: false,
          loading: false,
          callbacks: [],
        },
      },
    };
  }

  /**
   * Get the HLJS library. Returns a promise that resolves with a reference to
   * the library after it's been loaded. The promise resolves immediately if
   * it's already been loaded.
   *
   * @return {!Promise<Object>}
   */
  getHLJS() {
    return new Promise((resolve, reject) => {
      // If the lib is totally loaded, resolve immediately.
      if (this._getHighlightLib()) {
        resolve(this._getHighlightLib());
        return;
      }

      // If the library is not currently being loaded, then start loading it.
      if (!this._hljsState.loading) {
        this._hljsState.loading = true;
        this._loadScript(this._getHLJSUrl())
            .then(this._onHLJSLibLoaded.bind(this))
            .catch(reject);
      }

      this._hljsState.callbacks.push(resolve);
    });
  }

  /**
   * Execute callbacks awaiting the HLJS lib load.
   */
  _onHLJSLibLoaded() {
    const lib = this._getHighlightLib();
    this._hljsState.loading = false;
    this.$.jsAPI.handleEvent(this.$.jsAPI.EventType.HIGHLIGHTJS_LOADED, {
      hljs: lib,
    });
    for (const cb of this._hljsState.callbacks) {
      cb(lib);
    }
    this._hljsState.callbacks = [];
  }

  /**
   * Get the HLJS library, assuming it has been loaded. Configure the library
   * if it hasn't already been configured.
   *
   * @return {!Object}
   */
  _getHighlightLib() {
    const lib = window.hljs;
    if (lib && !this._hljsState.configured) {
      this._hljsState.configured = true;

      lib.configure({classPrefix: 'gr-diff gr-syntax gr-syntax-'});
    }
    return lib;
  }

  /**
   * Get the resource path used to load the application. If the application
   * was loaded through a CDN, then this will be the path to CDN resources.
   *
   * @return {string}
   */
  _getLibRoot() {
    if (window.STATIC_RESOURCE_PATH) {
      return window.STATIC_RESOURCE_PATH + '/';
    }
    return '/';
  }

  /**
   * Load and execute a JS file from the lib root.
   *
   * @param {string} src The path to the JS file without the lib root.
   * @return {Promise} a promise that resolves when the script's onload
   *     executes.
   */
  _loadScript(src) {
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');

      if (!src) {
        reject(new Error('Unable to load blank script url.'));
        return;
      }

      script.setAttribute('src', src);
      script.onload = resolve;
      script.onerror = reject;
      dom(document.head).appendChild(script);
    });
  }

  _getHLJSUrl() {
    const root = this._getLibRoot();
    if (!root) { return null; }
    return root + HLJS_PATH;
  }
}

customElements.define(GrLibLoader.is, GrLibLoader);
