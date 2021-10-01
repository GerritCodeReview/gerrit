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

export declare interface PopupPluginApi {
  /**
   * Opens the popup, inserts it into the DOM over current UI.
   * Creates the popup if not previously created. Creates and inserts the popup
   * content element, if a `moduleName` was provided in the constructor.
   * Otherwise you have to call `setContent()` when the promise resolves.
   */
  open(): Promise<PopupPluginApi>;

  /**
   * Hides the popup.
   */
  close(): void;

  /**
   * Appends the given element as a child to the popup. Only call this method
   * when you have called `popup()` without a `moduleName`.
   */
  setContent(el: HTMLElement): void;
}
