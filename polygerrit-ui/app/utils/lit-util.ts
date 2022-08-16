/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html} from 'lit';

/**
 * This is a patched version of html`` to work around this Chrome bug:
 * https://bugs.chromium.org/p/v8/issues/detail?id=13190.
 *
 * The problem is that Chrome should guarantee that the TemplateStringsArray
 * is always the same instance, if the strings themselves are equal, but that
 * guarantee seems to be broken. So we are maintaining a map from
 * "concatenated strings" to TemplateStringsArray. If "concatenated strings"
 * are equal, then return the already known instance of TemplateStringsArray,
 * so html`` can use its strict equality check on it.
 */
export class HtmlPatched {
  constructor(private readonly reporter?: (key: string) => void) {}

  /**
   * If `strings` are in this set, then we are sure that they are also in the
   * map, and that we will not run into the issue of "same key, but different
   * strings array". So this set allows us to optimize performance a bit, and
   * call the native html`` function early.
   */
  private readonly lookupSet = new Set<TemplateStringsArray>();

  private readonly lookupMap = new Map<string, TemplateStringsArray>();

  /**
   * Proxies lit's html`` tagges template literal. See
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals
   * https://lit.dev/docs/libraries/standalone-templates/
   *
   * Example: If you call html`a${1}b${2}c`, then
   * ['a', 'b', 'c'] are the "strings", and 1, 2 are the "values".
   */
  html(strings: TemplateStringsArray, ...values: unknown[]) {
    if (this.lookupSet.has(strings)) {
      return this.nativeHtml(strings, ...values);
    }

    const key = strings.join('\0');
    const oldStrings = this.lookupMap.get(key);

    if (oldStrings === undefined) {
      this.lookupSet.add(strings);
      this.lookupMap.set(key, strings);
      return this.nativeHtml(strings, ...values);
    }

    if (oldStrings === strings) {
      return this.nativeHtml(strings, ...values);
    }

    // Without using HtmlPatcher html`` would be called with `strings`,
    // which will be considered different, although actually being equal.
    console.warn(`HtmlPatcher was required for '${key.substring(0, 100)}'.`);
    this.reporter?.(key);
    return this.nativeHtml(oldStrings, ...values);
  }

  // Allows spying on calls in tests.
  nativeHtml(strings: TemplateStringsArray, ...values: unknown[]) {
    return html(strings, ...values);
  }
}
