/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, TemplateResult} from 'lit';

export class HtmlPatched {
  constructor(private readonly reporter?: (key: string) => void) {}

  private readonly lookup = new Map<string, TemplateStringsArray>();

  html: (
    strings: TemplateStringsArray,
    ...values: unknown[]
  ) => TemplateResult<1> = (strings, ...values) => {
    const key = strings.join('\0');
    const oldStrings = this.lookup.get(key);
    const newStrings = strings;

    if (oldStrings === undefined) {
      this.lookup.set(key, newStrings);
      return html(newStrings, ...values);
    }

    if (oldStrings !== newStrings) {
      // Without using HtmlPatcher html`` would be called with `newStrings,
      // which will be considered different, although actually being equal.
      console.warn(`HtmlPatcher was required for '${key.substring(0, 100)}'.`);
      this.reporter?.(key);
    }
    return html(oldStrings, ...values);
  };
}
