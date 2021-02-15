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
import {CoverageRange, Side} from './diff';
import {StyleObject} from './styles';
import {ChangeInfo, NumericChangeId} from '../types/common';

export type AddLayerFunc = (ctx: AnnotationContext) => void;

export type NotifyFunc = (
  path: string,
  start: number,
  end: number,
  side: Side
) => void;

export type CoverageProvider = (
  changeNum: NumericChangeId,
  path: string,
  basePatchNum?: number,
  patchNum?: number,
  change?: ChangeInfo
) => Promise<Array<CoverageRange>>;

export interface AnnotationContext {
  /**
   * Method to add annotations to a content line.
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
   * Method to add a CSS class to the line number TD element.
   *
   * @param styleObject The style object for the range.
   * @param side The side of the update. ('left' or 'right')
   */
  annotateLineNumber(styleObject: StyleObject, side: string): void;
}

export interface AnnotationPluginApi {
  /**
   * Register a function to call to apply annotations. Plugins should use
   * GrAnnotationActionsContext.annotateRange and
   * GrAnnotationActionsContext.annotateLineNumber to apply a CSS class to the
   * line content or the line number.
   *
   * @param addLayerFunc The function
   * that will be called when the AnnotationLayer is ready to annotate.
   */
  addLayer(addLayerFunc: AddLayerFunc): AnnotationPluginApi;

  /**
   * The specified function will be called with a notify function for the plugin
   * to call when it has all required data for annotation. Optional.
   *
   * @param notifyFunc See doc of the notify function below to see what it does.
   */
  addNotifier(notifyFunc: (n: NotifyFunc) => void): AnnotationPluginApi;

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
   * The notify function will call the listeners of all required annotation
   * layers. Intended to be called by the plugin when all required data for
   * annotation is available.
   *
   * @param path The file path whose listeners should be notified.
   * @param start The line where the update starts.
   * @param end The line where the update ends.
   * @param side The side of the update ('left' or 'right').
   */
  notify(path: string, start: number, end: number, side: Side): void;
}
