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
import './gr-custom-plugin-header';
import {GrCustomPluginHeader} from './gr-custom-plugin-header';

// TODO(TS): replace with Plugin once gr-public-js-api migrated
interface PluginApi {
  hook(
    endpointName: string,
    option: {replace?: boolean}
  ): {
    onAttached(callback: (el: Element) => void): void;
  };
}

/**
 * Defines api for theme, can be used to set header logo and title.
 */
export class GrThemeApi {
  constructor(private readonly plugin: PluginApi) {}

  setHeaderLogoAndTitle(logoUrl: string, title: string) {
    this.plugin.hook('header-title', {replace: true}).onAttached(element => {
      const customHeader: GrCustomPluginHeader = document.createElement(
        'gr-custom-plugin-header'
      );
      customHeader.logoUrl = logoUrl;
      customHeader.title = title;
      element.appendChild(customHeader);
    });
  }
}
