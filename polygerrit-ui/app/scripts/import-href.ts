/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

// This file is a replacement for the
// polymer-bridges/polymer/lib/utils/import-href.html file. The html
// file contains code inside <script>...</script> and can't be imported
// in es6 modules.

interface ImportHrefElement extends HTMLLinkElement {
  __dynamicImportLoaded?: boolean;
}

// run a callback when HTMLImports are ready or immediately if
// this api is not available.
function whenImportsReady(cb: () => void) {
  const win = window as Window;
  if (win.HTMLImports) {
    win.HTMLImports.whenReady(cb);
  } else {
    cb();
  }
}

/**
 * Convenience method for importing an HTML document imperatively.
 *
 * This method creates a new `<link rel="import">` element with
 * the provided URL and appends it to the document to start loading.
 * In the `onload` callback, the `import` property of the `link`
 * element will contain the imported document contents.
 *
 * @memberof Polymer
 * @param href URL to document to load.
 * @param onload Callback to notify when an import successfully
 *   loaded.
 * @param onerror Callback to notify when an import
 *   unsuccessfully loaded.
 * @param async True if the import should be loaded `async`.
 *   Defaults to `false`.
 * @return The link element for the URL to be loaded.
 */
export function importHref(
  href: string,
  onload: (e: Event) => void,
  onerror: (e: Event) => void,
  async = false
): HTMLLinkElement {
  let link = document.head.querySelector(
    'link[href="' + href + '"][import-href]'
  ) as ImportHrefElement;
  if (!link) {
    link = document.createElement('link') as ImportHrefElement;
    link.setAttribute('rel', 'import');
    link.setAttribute('href', href);
    link.setAttribute('import-href', '');
  }
  // always ensure link has `async` attribute if user specified one,
  // even if it was previously not async. This is considered less confusing.
  if (async) {
    link.setAttribute('async', '');
  }
  // NOTE: the link may now be in 3 states: (1) pending insertion,
  // (2) inflight, (3) already loaded. In each case, we need to add
  // event listeners to process callbacks.
  const cleanup = function () {
    link.removeEventListener('load', loadListener);
    link.removeEventListener('error', errorListener);
  };
  const loadListener = function (event: Event) {
    cleanup();
    // In case of a successful load, cache the load event on the link so
    // that it can be used to short-circuit this method in the future when
    // it is called with the same href param.
    link.__dynamicImportLoaded = true;
    if (onload) {
      whenImportsReady(() => {
        onload(event);
      });
    }
  };
  const errorListener = function (event: Event) {
    cleanup();
    // In case of an error, remove the link from the document so that it
    // will be automatically created again the next time `importHref` is
    // called.
    if (link.parentNode) {
      link.parentNode.removeChild(link);
    }
    if (onerror) {
      whenImportsReady(() => {
        onerror(event);
      });
    }
  };
  link.addEventListener('load', loadListener);
  link.addEventListener('error', errorListener);
  if (link.parentNode === null) {
    document.head.appendChild(link);
    // if the link already loaded, dispatch a fake load event
    // so that listeners are called and get a proper event argument.
  } else if (link.__dynamicImportLoaded) {
    link.dispatchEvent(new Event('load'));
  }
  return link;
}
