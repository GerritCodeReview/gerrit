/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  PARENT,
  RevisionPatchSetNum,
  ServerInfo,
} from '../types/common';
import {RestApiService} from '../services/gr-rest-api/gr-rest-api';

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

export function testOnly_clearDocsBaseUrlCache() {
  getDocsBaseUrlCachedPromise = undefined;
}

/**
 * Encodes *parts* of a URL. See inline comments below for the details.
 * Note specifically that ? & = # are encoded. So this is very close to
 * encodeURIComponent() with some tweaks.
 */
export function encodeURL(url: string): string {
  // page.js decodes the entire URL, and then decodes once more the
  // individual regex matching groups. It uses `decodeURIComponent()`, which
  // will choke on singular `%` chars without two trailing digits. We prefer
  // to not double encode *everything* (just for readaiblity and simplicity),
  // but `%` *must* be double encoded.
  let output = url.replaceAll('%', '%25');

  // This escapes ALL characters EXCEPT:
  // A–Z a–z 0–9 - _ . ! ~ * ' ( )
  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURIComponent
  output = encodeURIComponent(output);

  // If we would use `encodeURI()` instead of `encodeURIComponent()`, then we
  // would also NOT encode:
  // ; / ? : @ & = + $ , #
  //
  // That would be more readable, but for example ? and & have special meaning
  // in the URL, so they must be encoded. Let's discuss all these chars and
  // decide whether we have to encode them or not.
  //
  // ? & = # have to be encoded. Otherwise we might mess up the URL.
  //
  // : @ do not have to be encoded, because we are only dealing with path,
  // query and fragment of the URL, not with scheme, user, host, port.
  // For search queries it is much nicer to not encode those chars, think of
  // searching for `owner:spearce@spearce.org`.
  //
  // / does not have to be encoded, because we don't care about individual path
  // components. File path and repo names are so much nicer to read without /
  // being encoded!
  //
  // + must be encoded, because we want to use it instead of %20 for spaces, see
  // below.
  //
  // ; $ , probably don't have to be encoded, but we don't bother about them
  // much, so we don't reverse the encoding here, but we don't think it would
  // cause any harm, if we did.
  output = output.replace(/%3A/g, ':');
  output = output.replace(/%40/g, '@');
  output = output.replace(/%2F/g, '/');

  // page.js replaces `+` by ` ` in addition to calling `decodeURIComponent()`.
  // So we can use `+` to increase readability.
  output = output.replace(/%20/g, '+');

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
