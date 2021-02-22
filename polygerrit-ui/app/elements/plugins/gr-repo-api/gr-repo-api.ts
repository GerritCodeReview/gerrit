/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-plugin-repo-command';
import {ConfigInfo} from '../../../types/common';
import {PluginApi} from '../../../api/plugin';
import {RepoCommandCallback, RepoPluginApi} from '../../../api/repo';
import {HookApi} from '../../../api/hook';

/**
 * Parameters provided on repo-command endpoint
 */
export interface GrRepoCommandEndpointEl extends HTMLElement {
  repoName: string;
  config: ConfigInfo;
}

export class GrRepoApi implements RepoPluginApi {
  private hook?: HookApi;

  constructor(readonly plugin: PluginApi) {}

  // TODO(TS): should mark as public since used in gr-change-metadata-api
  _createHook(title: string) {
    return this.plugin.hook('repo-command').onAttached(element => {
      const pluginCommand = document.createElement('gr-plugin-repo-command');
      pluginCommand.title = title;
      element.appendChild(pluginCommand);
    });
  }

  createCommand(title: string, callback: RepoCommandCallback) {
    if (this.hook) {
      console.warn('Already set up.');
      return this;
    }
    this.hook = this._createHook(title);
    this.hook.onAttached(element => {
      if (callback(element.repoName, element.config) === false) {
        element.hidden = true;
      }
    });
    return this;
  }

  onTap(callback: (event: Event) => boolean) {
    if (!this.hook) {
      console.warn('Call createCommand first.');
      return this;
    }
    this.hook.onAttached(element => {
      this.plugin.eventHelper(element).on('command-tap', callback);
    });
    return this;
  }
}
