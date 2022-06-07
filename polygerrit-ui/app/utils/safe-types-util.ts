/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

const SAFE_URL_PATTERN = /^(https?:\/\/|mailto:|[^:/?#]*(?:[/?#]|$))/i;

/**
 * Wraps a string to be used as a URL. An error is thrown if the string cannot
 * be considered safe.
 */
class SafeUrl {
  private readonly _url: string;

  constructor(url: string) {
    if (!SAFE_URL_PATTERN.test(url)) {
      throw new Error(`URL not marked as safe: ${url}`);
    }
    this._url = url;
  }

  toString() {
    return this._url;
  }
}

export const _testOnly_SafeUrl = SafeUrl;

/**
 * Get the string representation of the safe URL.
 */
export function safeTypesBridge(value: unknown, type: string): unknown {
  // If the value is being bound to a URL, ensure the value is wrapped in the
  // SafeUrl type first. If the URL is not safe, allow the SafeUrl constructor
  // to surface the error.
  if (type === 'URL') {
    let safeValue = null;
    if (value instanceof SafeUrl) {
      safeValue = value;
    } else if (typeof value === 'string') {
      safeValue = new SafeUrl(value);
    }
    if (safeValue) {
      return safeValue.toString();
    }
  }

  // If the value is being bound to a string or a constant, then the string
  // can be used as is.
  if (type === 'STRING' || type === 'CONSTANT') {
    return value;
  }

  // Otherwise fail.
  throw new Error(`Refused to bind value as ${type}: ${value}`);
}
