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

interface LibraryConfig {
  /** Path to the library to be loaded. */
  src: string;
  /**
   * Optional check to see if the library has already been loaded outside of
   * this class. If this returns true, src will not be loaded, but
   * configureCallback will be run if present.
   */
  checkPresent?: () => boolean;
  /**
   * Optional library initialization to be run once after loading the library,
   * before resolving promises for getLibrary().
   */
  configureCallback?: () => void;
}

// TODO(hermannloose): Move into separate file.
const HLJS_LIBRARY_CONFIG: LibraryConfig = {
  // preloaded in PolyGerritIndexHtml.soy
  src: 'bower_components/highlightjs/highlight.min.js',
  checkPresent: () => window.hljs !== undefined,
  configureCallback: () => {
    window.hljs!.configure({classPrefix: 'gr-diff gr-syntax gr-syntax-'});
    appContext.jsApiService.handleEvent(EventType.HIGHLIGHTJS_LOADED, {
      hljs: window.hljs,
    });
  },
};

export class GrLibLoader {
  private readonly libraries = new Map<LibraryConfig, Promise<unknown>>();

  _getPath(src: string) {
    const root = this._getLibRoot();
    return root ? root + src : null;
  }

  getLibrary(config: LibraryConfig): Promise<unknown> {
    if (!this.libraries.has(config)) {
      const loaded =
        config.checkPresent && config.checkPresent()
          ? Promise.resolve()
          : this._loadScript(this._getPath(config.src));
      const configured = loaded.then(() => {
        if (config.configureCallback) config.configureCallback();
      });
      this.libraries.set(config, configured);
    }
    return this.libraries.get(config)!;
  }

  /**
   * Get the HLJS library. Returns a promise that resolves with a reference to
   * the library after it's been loaded. The promise resolves immediately if
   * it's already been loaded.
   */
  // TODO(hermannloose): Update callers to use getLibrary() directly.
  getHLJS(): Promise<HighlightJS | undefined> {
    return this.getLibrary(HLJS_LIBRARY_CONFIG).then(() => window.hljs);
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
}
