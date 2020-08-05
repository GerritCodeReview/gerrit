import {GrAttributeHelper} from '../gr-attribute-helper/gr-attribute-helper';

/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

type HookCallback = (el: Element) => void;
interface HookApi {
  onAttached(callback: HookCallback): void;
}
interface PluginAPI {
  hook(hookname: string): HookApi;
  attributeHelper(element: Element): GrAttributeHelper;
}

/** @constructor */
export class GrChangeMetadataApi {
  // TODO(TS): Convert to GrDomHook once converted
  private _hook: HookApi | null;

  // TODO(TS): Convert type to GrPlugin.
  public plugin: PluginAPI;

  constructor(plugin: PluginAPI) {
    this.plugin = plugin;
    this._hook = null;
  }

  _createHook() {
    this._hook = this.plugin.hook('change-metadata-item');
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  onLabelsChanged(callback: (value: any) => void) {
    if (!this._hook) {
      this._createHook();
    }
    this._hook!.onAttached((element: Element) =>
      this.plugin.attributeHelper(element).bind('labels', callback)
    );
    return this;
  }
}
