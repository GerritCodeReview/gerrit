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
import {ConfigInfo} from '../types/common';

interface GerritElementExtensions {
  content?: HTMLElement & {hidden?: boolean};
  change?: unknown;
  revision?: unknown;
  token?: string;
  repoName?: string;
  config?: ConfigInfo;
}

export type HookCallback = (el: HTMLElement & GerritElementExtensions) => void;

export interface RegisterOptions {
  slot?: string;
  replace: unknown;
}

export interface HookApi {
  onAttached(callback: HookCallback): HookApi;

  onDetached(callback: HookCallback): HookApi;

  getAllAttached(): HTMLElement[];

  getLastAttached(): Promise<HTMLElement>;

  getModuleName(): string;

  handleInstanceDetached(instance: HTMLElement): void;

  handleInstanceAttached(instance: HTMLElement): void;
}
