/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// This file adds some simple checks to match internal Google rules.
// Internally at Google it has different a implementation.

import {BrandType} from '../types/common';
export {sanitizeHtml, htmlEscape, sanitizeHtmlToFragment} from 'safevalues';

export type SafeStyleSheet = BrandType<string, '_safeHtml'>;

export function safeStyleSheet(
  templateObj: TemplateStringsArray
): SafeStyleSheet {
  const styleSheet = templateObj[0];
  if (/[<>]/.test(styleSheet)) {
    throw new Error('Forbidden characters in styleSheet string: ' + styleSheet);
  }
  return styleSheet as SafeStyleSheet;
}

export function setStyleTextContent(
  elem: HTMLStyleElement,
  safeStyleSheet: SafeStyleSheet
) {
  elem.textContent = safeStyleSheet;
}
