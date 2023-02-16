import * as monaco from 'monaco-editor';
import {DiffInfo} from '../types/diff';

// @ts-ignore
self.MonacoEnvironment = {
  getWorker(_: any, label: string) {
    if (label === 'json') {
      return new Worker(
        'monaco-editor/esm/vs/language/json/json.worker?worker"'
      );
    }
    if (label === 'css' || label === 'scss' || label === 'less') {
      return new Worker('monaco-editor/esm/vs/language/css/css.worker?worker');
    }
    if (label === 'html' || label === 'handlebars' || label === 'razor') {
      return new Worker(
        'monaco-editor/esm/vs/language/html/html.worker?worker'
      );
    }
    if (label === 'typescript' || label === 'javascript') {
      return new Worker(
        'monaco-editor/esm/vs/language/typescript/ts.worker?worker'
      );
    }
    return new Worker('monaco-editor/esm/vs/editor/editor.worker?worker');
  },
};

monaco.languages.typescript.typescriptDefaults.setEagerModelSync(true);

const eventTypes = {
  ready: 'ready',
  diffChanged: 'diffChanged',
  pathChanged: 'pathChanged',
};

class MonacoEditor {
  private editor?: monaco.editor.IStandaloneDiffEditor;

  private diff?: DiffInfo;
  private path?: string;

  constructor() {
    window.addEventListener('message', this.handleMessage);
    console.log('Eureka');
    this.setupEditor();
  }

  private setupEditor() {
    this.editor = monaco.editor.createDiffEditor(
      document.getElementById('monaco-container')!,
      {
        readOnly: true,
        automaticLayout: true,
        scrollBeyondLastLine: false,
      }
    );
    monaco.editor.createDiffNavigator(this.editor, {
      alwaysRevealFirst: true,
      followsCaret: true,
      ignoreCharChanges: true,
    });
    this.postMessage(eventTypes.ready, null);
  }

  private handleMessage = (message: MessageEvent<any>) => {
    try {
      const data = JSON.parse(message.data);
      switch (data.event) {
        case eventTypes.diffChanged:
          this.onDiffChanged(data.payload);
          break;
        case eventTypes.pathChanged:
          this.onPathChanged(data.payload);
          break;
        default:
          break;
      }
    } catch (error) {
      console.error(error);
      return;
    }
  };

  private onDiffChanged(diff: DiffInfo) {
    this.diff = diff;
    this.setupModels();
  }

  private setupModels() {
    if (!this.diff || !this.path) return;
    const left = this.diff.content
      .flatMap((content: any) => content.a ?? content.ab ?? [])
      .join('\n');
    const right = this.diff.content
      .flatMap((content: any) => content.b ?? content.ab ?? [])
      .join('\n');

    const currentModel = this.editor?.getModel();
    currentModel?.original?.dispose();
    currentModel?.modified?.dispose();
    this.editor?.setModel({
      original: monaco.editor.createModel(
        left,
        undefined,
        monaco.Uri.file(`original/${this.path}`)
      ),
      modified: monaco.editor.createModel(
        right,
        undefined,
        monaco.Uri.file(`modified/${this.path}`)
      ),
    });
  }

  private onPathChanged(path: string) {
    this.path = path;
    this.setupModels();
  }

  private postMessage(event: string, payload: unknown) {
    window.parent.postMessage(
      JSON.stringify({event, payload}),
      window.parent.location.href
    );
  }
}
new MonacoEditor();
