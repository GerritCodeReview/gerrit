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

/** @constructor */
export function GrAdminApi(plugin) {
  this.plugin = plugin;
  plugin.on('admin-menu-links', this);
  this._menuLinks = [];
}

/**
 * @param {string} text
 * @param {string} url
 */
GrAdminApi.prototype.addMenuLink = function(text, url, opt_capability) {
  this._menuLinks.push({text, url, capability: opt_capability || null});
};

GrAdminApi.prototype.getMenuLinks = function() {
  return this._menuLinks.slice(0);
};
