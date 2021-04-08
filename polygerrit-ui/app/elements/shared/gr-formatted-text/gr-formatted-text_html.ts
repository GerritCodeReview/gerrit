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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style>
    :host {
      display: block;
      font-family: var(--font-family);
    }
    p,
    ul,
    code,
    blockquote,
    gr-linked-text.pre {
      margin: 0 0 var(--spacing-m) 0;
    }
    p,
    ul,
    code,
    blockquote {
      max-width: var(--gr-formatted-text-prose-max-width, none);
    }
    :host(.noTrailingMargin) p:last-child,
    :host(.noTrailingMargin) ul:last-child,
    :host(.noTrailingMargin) blockquote:last-child,
    :host(.noTrailingMargin) gr-linked-text.pre:last-child {
      margin: 0;
    }
    code,
    blockquote {
      border-left: 1px solid #aaa;
      padding: 0 var(--spacing-m);
    }
    code {
      display: block;
      white-space: pre-wrap;
      color: var(--deemphasized-text-color);
    }
    li {
      list-style-type: disc;
      margin-left: var(--spacing-xl);
    }
    code,
    gr-linked-text.pre {
      font-family: var(--monospace-font-family);
      font-size: var(--font-size-code);
      /* usually 16px = 12px + 4px */
      line-height: calc(var(--font-size-code) + var(--spacing-s));
    }
  </style>
  <div id="container"></div>
`;
