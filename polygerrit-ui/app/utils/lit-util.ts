/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, TemplateResult} from 'lit';

/**
 * This is a patched version of html`` to work around this Chrome bug:
 * https://bugs.chromium.org/p/v8/issues/detail?id=13190.
 *
 * The problem is that Chrome should guarantee that the TemplateStringsArray
 * is always the same instance, if the strings themselves are equal, but that
 * guarantee seems to be broken. So we are maintaining a map from
 * "concatenated strings" to TemplateStringsArray. If "concatenated strings"
 * are equal, then rather return the already known instance of
 * TemplateStringsArray, so html`` can use its strict equality check on it.
 */
export class HtmlPatched {
  constructor(private readonly reporter?: (key: string) => void) {}

  /**
   * If `strings` are in this set, then we are sure that they are also in the
   * map, and that we will not run into the issue of "same key, but different
   * strings array". So this set allows us to optimize performance a bit, and
   * call the normal html`` early.
   */
  private readonly lookupSet = new Set<TemplateStringsArray>();

  private readonly lookupMap = new Map<string, TemplateStringsArray>();

  html: (
    strings: TemplateStringsArray,
    ...values: unknown[]
  ) => TemplateResult<1> = (strings, ...values) => {
    if (this.lookupSet.has(strings)) {
      return this.nativeHtml(strings, ...values);
    }

    const key = strings.join('\0');
    const oldStrings = this.lookupMap.get(key);
    const newStrings = strings;

    if (oldStrings === undefined) {
      this.lookupSet.add(newStrings);
      this.lookupMap.set(key, newStrings);
      return this.nativeHtml(newStrings, ...values);
    }

    if (oldStrings !== newStrings) {
      // Without using HtmlPatcher html`` would be called with `newStrings,
      // which will be considered different, although actually being equal.
      console.warn(`HtmlPatcher was required for '${key.substring(0, 100)}'.`);
      this.reporter?.(key);
    }
    return this.nativeHtml(oldStrings, ...values);
  };

  // Allows spying on calls in tests.
  nativeHtml(strings: TemplateStringsArray, ...values: unknown[]) {
    return html(strings, values);
  }
}
