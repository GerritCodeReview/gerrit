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
import {ChangeInfo, ConfigInfo, RevisionInfo} from './rest-api';
import {PluginApi} from './plugin';

export declare interface GerritElementExtensions {
  content?: HTMLElement & {hidden?: boolean};
  change?: ChangeInfo;
  revision?: RevisionInfo;
  token?: string;
  repoName?: string;
  config?: ConfigInfo;
  plugin?: PluginApi;
}

export type PluginElement = HTMLElement & GerritElementExtensions;

export type HookCallback<T extends PluginElement> = (el: T) => void;

export declare interface RegisterOptions {
  /** Defaults to empty string. */
  slot?: string;
  /** Defaults to false. */
  replace?: boolean;
}

export declare interface HookApi<T extends PluginElement> {
  onAttached(callback: HookCallback<T>): HookApi<T>;

  onDetached(callback: HookCallback<T>): HookApi<T>;

  getAllAttached(): HTMLElement[];

  getLastAttached(): Promise<HTMLElement>;

  getModuleName(): string;

  handleInstanceDetached(instance: HTMLElement): void;

  handleInstanceAttached(instance: HTMLElement): void;
}
