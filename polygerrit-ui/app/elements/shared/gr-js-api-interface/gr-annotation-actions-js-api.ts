/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {DiffLayer, DiffLayerListener} from '../../../types/types';
import {Side} from '../../../constants/constants';
import {EventType, PluginApi} from '../../../api/plugin';
import {getAppContext} from '../../../services/app-context';
import {AnnotationPluginApi, CoverageProvider} from '../../../api/annotation';

export class GrAnnotationActionsInterface implements AnnotationPluginApi {
  /**
   * Collect all annotation layers instantiated by createLayer. This is only
   * used for being able to look up the appropriate layer when notify() is
   * being called by plugins.
   */
  private annotationLayers: AnnotationLayer[] = [];

  private coverageProvider?: CoverageProvider;

  private readonly reporting = getAppContext().reportingService;

  constructor(private readonly plugin: PluginApi) {
    this.reporting.trackApi(this.plugin, 'annotation', 'constructor');
    plugin.on(EventType.ANNOTATE_DIFF, this);
  }

  setCoverageProvider(
    coverageProvider: CoverageProvider
  ): GrAnnotationActionsInterface {
    this.reporting.trackApi(this.plugin, 'annotation', 'setCoverageProvider');
    if (this.coverageProvider) {
      this.reporting.error(
        new Error(`Overwriting cov provider: ${this.plugin.getPluginName()}`)
      );
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

  notify(path: string, start: number, end: number, side: Side) {
    this.reporting.trackApi(this.plugin, 'annotation', 'notify');
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
  createLayer(path: string) {
    const annotationLayer = new AnnotationLayer(path);
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
   */
  constructor(readonly path: string) {
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

  annotate() {}

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
