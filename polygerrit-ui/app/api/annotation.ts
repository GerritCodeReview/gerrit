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
import {CoverageRange, GrDiffLine, Side} from './diff';
import {StyleObject} from './styles';

/**
 * This is the callback object that Gerrit calls once for each diff. Gerrit
 * is then responsible for styling the diff according the returned array of
 * CoverageRanges.
 */
export type CoverageProvider = (
  changeNum: number,
  path: string,
  basePatchNum?: number,
  patchNum?: number,
  /**
   * This is a ChangeInfo object as defined here:
   * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-info
   * At the moment we neither want to repeat it nor add a dependency on it here.
   * TODO: Create a dedicated smaller object for exposing a change in the plugin
   * API. Or allow the plugin API to depend on the entire rest API.
   */
  change?: unknown
) => Promise<Array<CoverageRange>>;

export type AnnotationCallback = (ctx: AnnotationContext) => void;

/**
 * This object is passed to the plugin from Gerrit for each line of a diff that
 * is being rendered. The plugin can then call annotateRange() or
 * annotateLineNumber() to apply additional styles to the diff.
 */
export interface AnnotationContext {
  /** Set by Gerrit and consumed by the plugin provided AddLayerFunc. */
  readonly changeNum: number;
  /** Set by Gerrit and consumed by the plugin provided AddLayerFunc. */
  readonly path: string;
  /** Set by Gerrit and consumed by the plugin provided AddLayerFunc. */
  readonly line: GrDiffLine;
  /** Set by Gerrit and consumed by the plugin provided AddLayerFunc. */
  readonly contentEl: HTMLElement;
  /** Set by Gerrit and consumed by the plugin provided AddLayerFunc. */
  readonly lineNumberEl: HTMLElement;

  /**
   * Can be called by the plugin to style a part of the given line of the
   * context.
   *
   * @param offset The char offset where the update starts.
   * @param length The number of chars that the update covers.
   * @param styleObject The style object for the range.
   * @param side The side of the update. ('left' or 'right')
   */
  annotateRange(
    offset: number,
    length: number,
    styleObject: StyleObject,
    side: string
  ): void;

  /**
   * Can be called by the plugin to style a part of the given line of the
   * context.
   *
   * @param styleObject The style object for the range.
   * @param side The side of the update. ('left' or 'right')
   */
  annotateLineNumber(styleObject: StyleObject, side: string): void;
}

export interface AnnotationPluginApi {
  /**
   * Registers a callback for applying annotations. Gerrit will call the
   * callback for every line of every file that is rendered and pass the
   * information about the file and line as an AnnotationContext, which also
   * provides methods for the plugin to style the content.
   */
  setLayer(callback: AnnotationCallback): AnnotationPluginApi;

  /**
   * The specified function will be called when a gr-diff component is built,
   * and feeds the returned coverage data into the diff. Optional.
   *
   * Be sure to call this only once and only from one plugin. Multiple coverage
   * providers are not supported. A second call will just overwrite the
   * provider of the first call.
   */
  setCoverageProvider(coverageProvider: CoverageProvider): AnnotationPluginApi;

  /**
   * Returns a checkbox HTMLElement that can be used to toggle annotations
   * on/off. The checkbox will be initially disabled. Plugins should enable it
   * when data is ready and should add a click handler to toggle CSS on/off.
   *
   * Note1: Calling this method from multiple plugins will only work for the
   * 1st call. It will print an error message for all subsequent calls
   * and will not invoke their onAttached functions.
   * Note2: This method will be deprecated and eventually removed when
   * https://bugs.chromium.org/p/gerrit/issues/detail?id=8077 is
   * implemented.
   *
   * @param checkboxLabel Will be used as the label for the checkbox.
   * Optional. "Enable" is used if this is not specified.
   * @param onAttached The function that will be called
   * when the checkbox is attached to the page.
   */
  enableToggleCheckbox(
    checkboxLabel: string,
    onAttached: (checkboxEl: Element | null) => void
  ): AnnotationPluginApi;

  /**
   * For plugins notifying Gerrit about new annotations being ready to be
   * applied for a certain range. Gerrit will then re-render the relevant lines
   * of the diff and call back to the layer annotation function that was
   * registered in addLayer().
   *
   * @param path The file path whose listeners should be notified.
   * @param start The line where the update starts.
   * @param end The line where the update ends.
   * @param side The side of the update ('left' or 'right').
   */
  notify(path: string, start: number, end: number, side: Side): void;
}
