// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/** redirects.json schema*/
export interface JSONRedirects {
  /** Short text description. Do not used anywhere*/
  description?: string;
  /** List of redirects, from the highest to lower priority. */
  redirects: Redirect[];
}

/** Redirect - describes one redirect.
 * Each link in the html file is converted to a path relative to site root
 * Redirect is applied, if converted link started with 'from'
 * */
export interface Redirect {
  /** from - path prefix. The '/' is added to the end of string if not present */
  from: string;
  /** New location - can be either other directory or node module*/
  to: PathRedirect;
}

export type PathRedirect = RedirectToDir | RedirectToNodeModule;

/** RedirectToDir - use another dir instead of original one*/
export interface RedirectToDir {
  /** New dir (relative to site root)*/
  dir: string;
  /** Redirects for files inside directory
   * Key is the original relative path, value is the new relative path (relative to new dir) */
  files?: { [name: string]: string }
}

export interface RedirectToNodeModule {
  /** Import from this node module instead of directory*/
  npm_module: string;
  /** Redirects for files inside node module
   * Key is the original relative path, value is the new relative path (relative to npm_module) */
  files?: { [name: string]: string }
}

export function isRedirectToNodeModule(redirect: PathRedirect): redirect is RedirectToNodeModule {
  return (redirect as RedirectToNodeModule).npm_module !== undefined;
}

export function isRedirectToDir(redirect: PathRedirect): redirect is RedirectToDir {
  return (redirect as RedirectToDir).dir !== undefined;
}


