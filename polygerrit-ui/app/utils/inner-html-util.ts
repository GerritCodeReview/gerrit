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

// This file adds some simple checks to match internal google rules.
// Internally in google it has different implementation

import {BrandType} from '../types/common';

export type SafeHtml = BrandType<string, '_safeHtml'>;
export type SafeStyleSheet = BrandType<string, '_safeHtml'>;

export function setInnerHtml(el: HTMLElement, innerHTML: SafeHtml) {
  el.innerHTML = innerHTML;
}

export function createStyle(styleSheet: SafeStyleSheet): SafeHtml {
  return `<style>${styleSheet}</style>` as SafeHtml;
}

export function safeStyleSheet(
  templateObj: TemplateStringsArray
): SafeStyleSheet {
  const styleSheet = templateObj[0];
  if (/[<>]/.test(styleSheet)) {
    throw new Error('Forbidden characters in styleSheet string: ' + styleSheet);
  }
  return styleSheet as SafeStyleSheet;
}
