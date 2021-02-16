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
import {
  AnnotationCallback,
  AnnotationPluginApi,
  CoverageProvider,
} from '../../../api/annotation';

export class GrAnnotationActionsInterface implements AnnotationPluginApi {
  /**
   * Collect all annotation layers instantiated by createLayer. This is only
   * used for being able to look up the appropriate layer when notify() is
   * being called by plugins.
   */
  private annotationLayers: AnnotationLayer[] = [];

  private coverageProvider?: CoverageProvider;

  private annotationCallback?: AnnotationCallback;

  private readonly reporting = appContext.reportingService;

  constructor(private readonly plugin: PluginApi) {
    plugin.on(EventType.ANNOTATE_DIFF, this);
  }

  addLayer(annotationCallback: AnnotationCallback) {
    if (this.annotationCallback) {
      console.warn('Overwriting an existing plugin annotation layer.');
    }
    this.annotationCallback = annotationCallback;
    return this;
  }

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
   * Factory method called by Gerrit for creating a DiffLayer for each diff that
   * is rendered.
   *
   * Don't forget to also call disposeLayer().
   */
  createLayer(path: string, changeNum: number) {
    if (!this.annotationCallback) return undefined;
    const annotationLayer = new AnnotationLayer(
      path,
      changeNum,
      this.annotationCallback
    );
    this.annotationLayers.push(annotationLayer);
    return annotationLayer;
  }

  /**
   * Called by Gerrit for each diff renderer that had called createLayer().
   */
  disposeLayer(path: string) {
    this.annotationLayers = this.annotationLayers.filter(
      annotationLayer => annotationLayer.path !== path
    );
  }
}

/**
 * An AnnotationLayer exists for each file that is being rendered. This class is
 * not exposed to plugins, but being used by Gerrit's diff rendering.
 */
export class AnnotationLayer implements DiffLayer {
  private listeners: DiffLayerListener[] = [];

  /**
   * Used to create an instance of the Annotation Layer interface.
   *
   * @param path The file path (eg: /COMMIT_MSG').
   * @param changeNum The Gerrit change number.
   * @param annotationCallback The function
   * that will be called when the AnnotationLayer is ready to annotate.
   */
  constructor(
    readonly path: string,
    private readonly changeNum: number,
    private readonly annotationCallback: AnnotationCallback
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
   * Called by Gerrit during diff rendering for each line. Delegates to the
   * plugin provided callback for potentially annotating this line.
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
    const context = new GrAnnotationActionsContext(
      contentEl,
      lineNumberEl,
      line,
      this.path,
      this.changeNum
    );
    this.annotationCallback(context);
  }

  /**
   * Notify layer listeners (which typically is just Gerrit's diff renderer) of
   * changes to annotations after the diff rendering had already completed. This
   * is indirectly called by plugins using the AnnotationPluginApi.notify().
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
