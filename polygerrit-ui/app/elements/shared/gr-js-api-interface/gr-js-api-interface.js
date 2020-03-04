/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
/*
  Note: the order matters as files depend on each other.
  1. gr-api-utils will be used in multiple files below.
  2. gr-gerrit depends on gr-plugin-loader, gr-public-js-api and
    also gr-plugin-endpoints
  3. gr-public-js-api depends on gr-plugin-rest-api
*/
/*
  FIXME(polymer-modulizer): the above comments were extracted
  from HTML and may be out of place here. Review them and
  then delete this comment!
*/
import './gr-js-api-interface-element.js';
import './gr-plugin-endpoints.js';
import './gr-plugin-action-context.js';
import './gr-plugin-rest-api.js';
import './gr-public-js-api.js';
import './gr-plugin-loader.js';
import './gr-gerrit.js';
