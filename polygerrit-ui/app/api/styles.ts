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

/**
 * We are sharing a couple of sets of CSS rules with plugins such that they can
 * adopt Gerrit's styling beyond just using CSS properties from the theme.
 *
 * This is a bit tricky, because plugin elements have their own shadow DOM, and
 * unfortunately Firefox has not adopted "constructable stylesheets" yet. So we
 * are basically just exposing CSS strings here.
 *
 * Plugins that use Lit can cast `Style` to `CSSResult`.
 *
 * Non-Lit plugins can call call `toString()` on `Style`.
 */

/** Lit plugins can cast Style to CSSResult. */
export declare interface Style {
  toString(): string;
}

export declare interface Styles {
  font: Style;
  form: Style;
  menuPage: Style;
  spinner: Style;
  subPage: Style;
  table: Style;
}
