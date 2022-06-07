/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export interface LibraryConfig {
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
   * before resolving promises for getLibrary(). Promises returned from
   * getLibrary() will resolve to the return value of this function, if any.
   */
  configureCallback?: () => unknown;
}

export class GrLibLoader {
  /*
   * Pending library loads, keyed by library config, populated when getLibrary()
   * is first called for a given config. This retains the promise for each
   * library so that later calls of getLibrary() for the same config can
   * directly return a resolved promise.
   */
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
      const configured = loaded.then(() =>
        config.configureCallback ? config.configureCallback() : undefined
      );
      this.libraries.set(config, configured);
    }
    return this.libraries.get(config)!;
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
      script.setAttribute('crossorigin', 'anonymous');
      script.setAttribute('src', src);
      script.onload = resolve;
      script.onerror = reject;
      document.head.appendChild(script);
    });
  }
}
