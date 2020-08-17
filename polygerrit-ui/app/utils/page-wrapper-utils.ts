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
import 'page/page';

// Reexport page.js. To make it work, karma, server.go and rollup patch
// page.js and replace "this" to "window". Otherwise, it can't assign global
// property. We can't import page.mjs because typescript doesn't support mjs
// extensions
export interface Page {
  (pattern: string | RegExp, ...pageCallback: PageCallback[]): void;
  (pageCallback: PageCallback): void;
  show(url: string): void;
  redirect(url: string): void;
  base(url: string): void;
  start(): void;
  exit(pattern: string | RegExp, ...pageCallback: PageCallback[]): void;
}

// See https://visionmedia.github.io/page.js/ for details
export interface PageContext {
  save(): void;
  handled: boolean;
  canonicalPath: string;
  path: string;
  querystring: string;
  pathname: string;
  state: unknown;
  title: string;
  hash: string;
  params: {[paramIndex: string]: string};
}

export type PageNextCallback = () => void;

export type PageCallback = (
  context: PageContext,
  next: PageNextCallback
) => void;

export const page = window['page'] as Page;
