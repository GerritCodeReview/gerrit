import * as ts from 'typescript';
import {LegacyLifecycleMethodName, OrdinaryMethods} from './polymerComponentParser';
import * as tsUtils from './tsUtils';

interface LegacyLifecycleMethodContent {
  codeAtMethodStart: ts.Statement[];
  existingMethod?: ts.MethodDeclaration;
  codeAtMethodEnd: ts.Statement[];
}

export interface LifecycleMethod {
  originalPos?: number;
  method: ts.MethodDeclaration;
  name: LegacyLifecycleMethodName;
}

export class LifecycleMethodsBuilder {
  private readonly methods: Map<LegacyLifecycleMethodName, LegacyLifecycleMethodContent> = new Map();

  private getMethodContent(name: LegacyLifecycleMethodName): LegacyLifecycleMethodContent {
    if(!this.methods.has(name)) {
      this.methods.set(name, {
        codeAtMethodStart: [],
        codeAtMethodEnd: []
      });
    }
    return this.methods.get(name)!;
  }

  public addListeners(legacyListeners: ts.ObjectLiteralExpression, legacyOrdinaryMethods: OrdinaryMethods) {
    for(const listener of legacyListeners.properties) {
      const propertyAssignment = tsUtils.assertNodeKind(listener, ts.SyntaxKind.PropertyAssignment) as ts.PropertyAssignment;
      if(!propertyAssignment.name) {
        throw new Error("Listener must have event name");
      }
      let eventNameLiteral: ts.StringLiteral;
      if(propertyAssignment.name.kind === ts.SyntaxKind.StringLiteral) {
        eventNameLiteral = propertyAssignment.name;
      } else if(propertyAssignment.name.kind === ts.SyntaxKind.Identifier) {
        eventNameLiteral = ts.createStringLiteral(propertyAssignment.name.text);
      } else {
        throw new Error(`Unsupported type ${ts.SyntaxKind[propertyAssignment.name.kind]}`);
      }
      const handlerLiteral = tsUtils.assertNodeKind(propertyAssignment.initializer, ts.SyntaxKind.StringLiteral) as ts.StringLiteral;
      const handlerImpl = legacyOrdinaryMethods.get(handlerLiteral.text);
      if(!handlerImpl) {
        throw new Error(`Can't find event handler '${handlerLiteral.text}'`);
      }
      const eventHandlerAccess = ts.createPropertyAccess(ts.createThis(), handlerLiteral.text);
      //ts.forEachChild(handler)
      const args: ts.Identifier[] = handlerImpl.parameters.map((arg) => tsUtils.assertNodeKind(arg.name, ts.SyntaxKind.Identifier));
      const eventHandlerCall = ts.createCall(eventHandlerAccess, [], args);
      const arrowFunc = ts.createArrowFunction([], [], handlerImpl.parameters, undefined, undefined, eventHandlerCall);

      const methodContent = this.getMethodContent("created");
      //See https://polymer-library.polymer-project.org/3.0/docs/devguide/gesture-events for a list of events
      if(["down", "up", "tap", "track"].indexOf(eventNameLiteral.text) >= 0) {
        const methodCall = ts.createCall(tsUtils.createNameExpression("Polymer.Gestures.addListener"), [], [ts.createThis(), eventNameLiteral, arrowFunc]);
        methodContent.codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
      }
      else {
        const methodCall = ts.createCall(ts.createPropertyAccess(ts.createThis(), "addEventListener"), [], [eventNameLiteral, arrowFunc]);
        methodContent.codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
      }
    }
  }

  public addHostAttributes(legacyHostAttributes: ts.ObjectLiteralExpression) {
    for(const listener of legacyHostAttributes.properties) {
      const propertyAssignment = tsUtils.assertNodeKind(listener, ts.SyntaxKind.PropertyAssignment) as ts.PropertyAssignment;
      if(!propertyAssignment.name) {
        throw new Error("Listener must have event name");
      }
      let attributeNameLiteral: ts.StringLiteral;
      if(propertyAssignment.name.kind === ts.SyntaxKind.StringLiteral) {
        attributeNameLiteral = propertyAssignment.name;
      } else if(propertyAssignment.name.kind === ts.SyntaxKind.Identifier) {
        attributeNameLiteral = ts.createStringLiteral(propertyAssignment.name.text);
      } else {
        throw new Error(`Unsupported type ${ts.SyntaxKind[propertyAssignment.name.kind]}`);
      }
      let attributeValueLiteral: ts.StringLiteral | ts.NumericLiteral;
      if(propertyAssignment.initializer.kind === ts.SyntaxKind.StringLiteral) {
        attributeValueLiteral = propertyAssignment.initializer as ts.StringLiteral;
      } else if(propertyAssignment.initializer.kind === ts.SyntaxKind.NumericLiteral) {
        attributeValueLiteral = propertyAssignment.initializer as ts.NumericLiteral;
      } else {
        throw new Error(`Unsupported type ${ts.SyntaxKind[propertyAssignment.initializer.kind]}`);
      }
      const methodCall = ts.createCall(ts.createPropertyAccess(ts.createThis(), "_ensureAttribute"), [], [attributeNameLiteral, attributeValueLiteral]);
      this.getMethodContent("ready").codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
    }
  }

  public addLegacyLifecycleMethod(name: LegacyLifecycleMethodName, method: ts.MethodDeclaration) {
    const content = this.getMethodContent(name);
    if(content.existingMethod) {
      throw new Error(`Legacy lifecycle method ${name} already added`);
    }
    content.existingMethod = method;
  }

  public buildNewMethods(): LifecycleMethod[] {
    const result = [];
    for(const [name, content] of this.methods) {
      const newMethod = this.createLifecycleMethod(name, content.existingMethod, content.codeAtMethodStart, content.codeAtMethodEnd);
      if(!newMethod) continue;
      result.push({
        name,
        originalPos: content.existingMethod ? content.existingMethod.pos : undefined,
        method: newMethod
      })
    }
    return result;
  }

  private createLifecycleMethod(name: string, methodDecl: ts.MethodDeclaration | undefined, codeAtStart: ts.Statement[], codeAtEnd: ts.Statement[]): ts.MethodDeclaration | undefined {
    return tsUtils.createMethod(name, methodDecl, codeAtStart, codeAtEnd, true);
  }
}