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
import '../gr-js-api-interface/gr-js-api-interface';
import {EventType} from '../../../api/plugin';
import {HighlightJS} from '../../../types/types';
import {appContext} from '../../../services/app-context';

// preloaded in PolyGerritIndexHtml.soy
const HLJS_PATH = 'bower_components/highlightjs/highlight.min.js';

type HljsCallback = (value?: HighlightJS) => void;

interface HljsState {
  configured: boolean;
  loading: boolean;
  callbacks: HljsCallback[];
}

export class GrLibLoader {
  private readonly jsAPI = appContext.jsApiService;

  _hljsState: HljsState = {
    configured: false,
    loading: false,
    callbacks: [],
  };

  /**
   * Get the HLJS library. Returns a promise that resolves with a reference to
   * the library after it's been loaded. The promise resolves immediately if
   * it's already been loaded.
   */
  getHLJS(): Promise<HighlightJS | undefined> {
    return new Promise<HighlightJS | undefined>((resolve, reject) => {
      // If the lib is totally loaded, resolve immediately.
      if (this._getHighlightLib()) {
        resolve(this._getHighlightLib());
        return;
      }

      // If the library is not currently being loaded, then start loading it.
      if (!this._hljsState.loading) {
        this._hljsState.loading = true;
        this._loadScript(this._getHLJSUrl())
          .then(() => this._onHLJSLibLoaded())
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
    this.jsAPI.handleEvent(EventType.HIGHLIGHTJS_LOADED, {
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
   */
  _getHighlightLib(): HighlightJS | undefined {
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
   * @param src The path to the JS file without the lib root.
   * @return a promise that resolves when the script's onload
   * executes.
   */
  _loadScript(src: string | null) {
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');

      if (!src) {
        reject(new Error('Unable to load blank script url.'));
        return;
      }

      script.setAttribute('src', src);
      script.onload = resolve;
      script.onerror = reject;
      document.head.appendChild(script);
    });
  }

  _getHLJSUrl() {
    const root = this._getLibRoot();
    if (!root) {
      return null;
    }
    return root + HLJS_PATH;
  }
}
