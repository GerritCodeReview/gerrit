/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {getBaseUrl} from '../../../utils/url-util';

export const PLUGIN_LOADING_TIMEOUT_MS = 10000;

export const THEME_JS = '/static/gerrit-theme.js';

const THEME_NAME = 'gerrit-theme';

export function isThemeFile(path: string) {
  return path.endsWith(THEME_JS);
}

/**
 * Retrieves the name of the plugin base on the url.
 */
export function getPluginNameFromUrl(url: URL | string) {
  if (!(url instanceof URL)) {
    try {
      url = new URL(url);
    } catch (e) {
      console.warn(e);
      return null;
    }
  }
  const base = getBaseUrl();
  let pathname = url.pathname.replace(base, '');
  // Load from ASSETS_PATH
  if (window.ASSETS_PATH && url.href.includes(window.ASSETS_PATH)) {
    pathname = url.href.replace(window.ASSETS_PATH, '');
  }

  if (isThemeFile(pathname)) return THEME_NAME;

  if (!pathname.startsWith('/plugins')) {
    console.warn(
      'Plugin not being loaded from /plugins base path:',
      url.href,
      '— Unable to determine name.'
    );
    return null;
  }

  // Pathname should normally look like this:
  // /plugins/PLUGINNAME/static/SCRIPTNAME.js
  // Or:
  // /plugins/PLUGINNAME.js
  // TODO(taoalpha): guard with a regex
  return pathname.split('/')[2].split('.')[0];
}
