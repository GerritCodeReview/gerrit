/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
