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
const customTheme = document.createElement('dom-module');
customTheme.id = 'theme-plugin';
customTheme.innerHTML = `
  <template>
    <style>
    html {
      --primary-text-color: red;
    }
    </style>
  </template>
`;

const darkCustomTheme = document.createElement('dom-module');
darkCustomTheme.id = 'dark-theme-plugin';
darkCustomTheme.innerHTML = `
  <template>
    <style>
    html {
      --background-color-primary: yellow;
    }
    </style>
  </template>
`;

/**
 * This plugin will change the primary text color to red.
 *
 * Also change the primary background color to yellow for dark theme.
 */
Gerrit.install(plugin => {
  plugin.registerStyleModule('app-theme', 'theme-plugin');
  plugin.registerStyleModule('app-theme-dark', 'dark-theme-plugin');
});