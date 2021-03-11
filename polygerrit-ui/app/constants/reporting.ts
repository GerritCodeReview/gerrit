/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

export enum LifeCycle {
  PLUGIN_LIFE_CYCLE = 'Plugin life cycle',
  STARTED_AS_USER = 'Started as user',
  STARTED_AS_GUEST = 'Started as guest',
  VISIBILILITY_HIDDEN = 'Visibility changed to hidden',
  VISIBILILITY_VISIBLE = 'Visibility changed to visible',
  EXTENSION_DETECTED = 'Extension detected',
  PLUGINS_INSTALLED = 'Plugins installed',
}

export enum Execution {
  PLUGIN_API = 'plugin-api',
  REACHABLE_CODE = 'reachable code',
  METHOD_USED = 'method used',
}
