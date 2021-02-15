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
import {GrAnnotationActionsContext} from './gr-annotation-actions-context';
import {GrDiffLine} from '../../diff/gr-diff/gr-diff-line';
import {DiffLayer, DiffLayerListener} from '../../../types/types';
import {Side} from '../../../constants/constants';
import {EventType, PluginApi} from '../../../api/plugin';
import {appContext} from '../../../services/app-context';
import {AddLayerFunc, AnnotationPluginApi, CoverageProvider, NotifyFunc,} from '../../../api/annotation';

export class GrAnnotationActionsInterface implements AnnotationPluginApi {
  // Collect all annotation layers instantiated by getLayer. Will be used when
  // notifying their listeners in the notify function.
  private annotationLayers: AnnotationLayer[] = [];

  private coverageProvider: CoverageProvider | null = null;

  // Default impl is a no-op.
  private addLayerFunc: AddLayerFunc = () => {};

  reporting = appContext.reportingService;

  constructor(private readonly plugin: PluginApi) {
    // Return this instance when there is an annotatediff event.
    plugin.on(EventType.ANNOTATE_DIFF, this);
  }

  /**
   * Register a function to call to apply annotations. Plugins should use
   * GrAnnotationActionsContext.annotateRange and
   * GrAnnotationActionsContext.annotateLineNumber to apply a CSS class to the
   * line content or the line number.
   *
   * @param addLayerFunc The function
   * that will be called when the AnnotationLayer is ready to annotate.
   */
  addLayer(addLayerFunc: AddLayerFunc) {
    this.addLayerFunc = addLayerFunc;
    return this;
  }

  /**
   * The specified function will be called with a notify function for the plugin
   * to call when it has all required data for annotation. Optional.
   *
   * @param notifyFunc See doc of the notify function below to see what it does.
   */
  addNotifier(notifyFunc: (n: NotifyFunc) => void) {
    notifyFunc(
      (path: string, startRange: number, endRange: number, side: Side) =>
        this.notify(path, startRange, endRange, side)
    );
    return this;
  }

  /**
   * The specified function will be called when a gr-diff component is built,
   * and feeds the returned coverage data into the diff. Optional.
   *
   * Be sure to call this only once and only from one plugin. Multiple coverage
   * providers are not supported. A second call will just overwrite the
   * provider of the first call.
   */
  setCoverageProvider(
    coverageProvider: CoverageProvider
  ): GrAnnotationActionsInterface {
    if (this.coverageProvider) {
      console.warn('Overwriting an existing coverage provider.');
    }
    this.coverageProvider = coverageProvider;
    return this;
  }

  /**
   * Used by Gerrit to look up the coverage provider. Not intended to be called
   * by plugins.
   */
  getCoverageProvider() {
    return this.coverageProvider;
  }

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
  ) {
    this.plugin.hook('annotation-toggler').onAttached(element => {
      if (!element.content) {
        this.reporting.error(new Error('plugin endpoint without content.'));
        return;
      }
      if (!element.content.hidden) {
        this.reporting.error(
          new Error(
            `${element.content.id} is already enabled. Cannot re-enable.`
          )
        );
        return;
      }
      element.content.removeAttribute('hidden');

      const label = element.content.querySelector('#annotation-label');
      if (label) {
        if (checkboxLabel) {
          label.textContent = checkboxLabel;
        } else {
          label.textContent = 'Enable';
        }
      }
      const checkbox = element.content.querySelector('#annotation-checkbox');
      onAttached(checkbox);
    });
    return this;
  }

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
  notify(path: string, start: number, end: number, side: Side) {
    for (const annotationLayer of this.annotationLayers) {
      // Notify only the annotation layer that is associated with the specified
      // path.
      if (annotationLayer.path === path) {
        annotationLayer.notifyListeners(start, end, side);
      }
    }
  }

  /**
   * Should be called to register annotation layers by the framework. Not
   * intended to be called by plugins.
   *
   * Don't forget to dispose layer.
   *
   * @param path The file path (eg: /COMMIT_MSG').
   * @param changeNum The Gerrit change number.
   */
  getLayer(path: string, changeNum: number) {
    const annotationLayer = new AnnotationLayer(
      path,
      changeNum,
      this.addLayerFunc
    );
    this.annotationLayers.push(annotationLayer);
    return annotationLayer;
  }

  disposeLayer(path: string) {
    this.annotationLayers = this.annotationLayers.filter(
      annotationLayer => annotationLayer.path !== path
    );
  }
}

export class AnnotationLayer implements DiffLayer {
  private listeners: DiffLayerListener[] = [];

  /**
   * Used to create an instance of the Annotation Layer interface.
   *
   * @param path The file path (eg: /COMMIT_MSG').
   * @param changeNum The Gerrit change number.
   * @param addLayerFunc The function
   * that will be called when the AnnotationLayer is ready to annotate.
   */
  constructor(
    readonly path: string,
    private readonly changeNum: number,
    private readonly addLayerFunc: AddLayerFunc
  ) {
    this.listeners = [];
  }

  /**
   * Register a listener for layer updates.
   * Don't forget to removeListener when you stop using layer.
   *
   * @param fn The update handler function.
   * Should accept as arguments the line numbers for the start and end of
   * the update and the side as a string.
   */
  addListener(listener: DiffLayerListener) {
    this.listeners.push(listener);
  }

  removeListener(listener: DiffLayerListener) {
    this.listeners = this.listeners.filter(f => f !== listener);
  }

  /**
   * Layer method to add annotations to a line.
   *
   * @param contentEl The DIV.contentText element of the line
   * content to apply the annotation to using annotateRange.
   * @param lineNumberEl The TD element of the line number to
   * apply the annotation to using annotateLineNumber.
   * @param line The line object.
   */
  annotate(
    contentEl: HTMLElement,
    lineNumberEl: HTMLElement,
    line: GrDiffLine
  ) {
    const annotationActionsContext = new GrAnnotationActionsContext(
      contentEl,
      lineNumberEl,
      line,
      this.path,
      this.changeNum
    );
    this.addLayerFunc(annotationActionsContext);
  }

  /**
   * Notify Layer listeners of changes to annotations.
   *
   * @param start The line where the update starts.
   * @param end The line where the update ends.
   * @param side The side of the update. ('left' or 'right')
   */
  notifyListeners(start: number, end: number, side: Side) {
    for (const listener of this.listeners) {
      listener(start, end, side);
    }
  }
}
