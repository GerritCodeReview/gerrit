/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
// @ts-ignore: Bazel is not yet configured to download the types
import pagejs from 'page';

// Reexport page.js. To make it work rollup patches page.js and replace "this"
// to "window". Otherwise, it can't assign global property. We can't import
// page.mjs because typescript doesn't support mjs extensions
export interface Page {
  (pattern: string | RegExp, ...pageCallback: PageCallback[]): void;
  (pageCallback: PageCallback): void;
  show(url: string): void;
  redirect(url: string): void;
  replace(path: string, state: null, init: boolean, dispatch: boolean): void;
  base(url: string): void;
  start(): void;
  stop(): void;
  exit(pattern: string | RegExp, ...pageCallback: PageCallback[]): void;
}

// See https://visionmedia.github.io/page.js/ for details
export interface PageContext {
  canonicalPath: string;
  path: string;
  querystring: string;
  pathname: string;
  hash: string;
  params: {[paramIndex: string]: string};
}

export type PageNextCallback = () => void;

export type PageCallback = (
  context: PageContext,
  next: PageNextCallback
) => void;

// Must only be used by gr-router and its test!
export const page = pagejs as unknown as {create(): Page};
