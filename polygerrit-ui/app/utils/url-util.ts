/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AuthInfo,
  BasePatchSetNum,
  PARENT,
  RevisionPatchSetNum,
  ServerInfo,
} from '../types/common';
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';
import {AuthType} from '../api/rest-api';

const PROBE_PATH = '/Documentation/index.html';
const DOCS_BASE_PATH = '/Documentation';

export function getBaseUrl(): string {
  // window is not defined in service worker, therefore no CANONICAL_PATH
  if (typeof window === 'undefined') return '';
  return self.CANONICAL_PATH || '';
}

export interface PatchRangeParams {
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
}

export function rootUrl() {
  return `${getBaseUrl()}/`;
}

/**
 * Given an object of parameters, potentially including a `patchNum` or a
 * `basePatchNum` or both, return a string representation of that range. If
 * no range is indicated in the params, the empty string is returned.
 */
export function getPatchRangeExpression(params: PatchRangeParams) {
  let range = '';
  if (params.patchNum) {
    range = `${params.patchNum}`;
  }
  if (params.basePatchNum && params.basePatchNum !== PARENT) {
    range = `${params.basePatchNum}..${range}`;
  }
  return range;
}

/**
 * Return the url to use for login. If the server configuration
 * contains the `loginUrl` in the `auth` section then that custom url
 * will be used, defaults to `/login` otherwise.
 *
 * @param authConfig the auth section of gerrit configuration if defined
 */
export function loginUrl(authConfig: AuthInfo | undefined): string {
  const baseUrl = getBaseUrl();
  const customLoginUrl = authConfig?.login_url;
  const authType = authConfig?.auth_type;
  if (
    customLoginUrl &&
    (authType === AuthType.HTTP || authType === AuthType.HTTP_LDAP)
  ) {
    return customLoginUrl.startsWith('http')
      ? customLoginUrl
      : baseUrl + sanitizeRelativeUrl(customLoginUrl);
  } else {
    // Strip the canonical path from the path since needing canonical in
    // the path is unneeded and breaks the url.
    const defaultUrl = `${baseUrl}/login/`;
    const postFix = encodeURIComponent(
      window.location.pathname.substring(baseUrl.length) +
        window.location.search +
        window.location.hash
    );
    return defaultUrl + postFix;
  }
}

function sanitizeRelativeUrl(relativeUrl: string): string {
  return relativeUrl.startsWith('/') ? relativeUrl : `/${relativeUrl}`;
}

export function prependOrigin(path: string): string {
  if (path.startsWith('http')) return path;
  if (path.startsWith('/')) return window.location.origin + path;
  throw new Error(`Cannot prepend origin to relative path '${path}'.`);
}

let getDocsBaseUrlCachedPromise: Promise<string | null> | undefined;

/**
 * Get the docs base URL from either the server config or by probing.
 *
 * @return A promise that resolves with the docs base URL.
 */
export function getDocsBaseUrl(
  config: ServerInfo | undefined,
  restApi: RestApiService
): Promise<string | null> {
  if (!getDocsBaseUrlCachedPromise) {
    getDocsBaseUrlCachedPromise = new Promise(resolve => {
      if (config?.gerrit?.doc_url) {
        resolve(config.gerrit.doc_url);
      } else {
        restApi.probePath(getBaseUrl() + PROBE_PATH).then(ok => {
          resolve(ok ? getBaseUrl() + DOCS_BASE_PATH : null);
        });
      }
    });
  }
  return getDocsBaseUrlCachedPromise;
}

export function _testOnly_clearDocsBaseUrlCache() {
  getDocsBaseUrlCachedPromise = undefined;
}

/**
 * Pretty-encodes a URL. Double-encodes the string, and then replaces
 *   benevolent characters for legibility.
 */
export function encodeURL(url: string, replaceSlashes?: boolean): string {
  // @see Issue 4255 regarding double-encoding.
  let output = encodeURIComponent(encodeURIComponent(url));
  // @see Issue 4577 regarding more readable URLs.
  output = output.replace(/%253A/g, ':');
  output = output.replace(/%2520/g, '+');
  if (replaceSlashes) {
    output = output.replace(/%252F/g, '/');
  }
  return output;
}

/**
 * Single decode for URL components. Will decode plus signs ('+') to spaces.
 * Note: because this function decodes once, it is not the inverse of
 * encodeURL.
 */
export function singleDecodeURL(url: string): string {
  const withoutPlus = url.replace(/\+/g, '%20');
  return decodeURIComponent(withoutPlus);
}

/**
 * @param path URL path including search params, but without host
 */
export function toPathname(path: string) {
  const i = path.indexOf('?');
  const hasQuery = i > -1;
  const pathname = hasQuery ? path.slice(0, i) : path;
  return pathname;
}

/**
 * @param path URL path including search params, but without host
 */
export function toSearchParams(path: string) {
  const i = path.indexOf('?');
  const hasQuery = i > -1;
  const querystring = hasQuery ? path.slice(i + 1) : '';
  return new URLSearchParams(querystring);
}

/**
 * @param pathname URL path without search params
 * @param params
 */
export function toPath(pathname: string, searchParams: URLSearchParams) {
  const paramString = searchParams.toString();
  const middle = paramString ? '?' : '';
  return pathname + middle + paramString;
}

/**
 * Primary use case is to copy the absolute comments url to clipboard.
 */
export function generateAbsoluteUrl(url: string) {
  return new URL(url, window.location.href).toString();
}
