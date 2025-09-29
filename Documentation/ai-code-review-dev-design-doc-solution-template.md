---
title: "Design Doc - ${title} - Solution - ${solution-name}"
sidebar: gerritdoc_sidebar
permalink: design-doc-${folder-name}-solution-${solution-name}.html
hide_sidebar: true
hide_navtoggle: true
toc: false
folder: design-docs/${folder-name}
---

# Solution - AI Code Review.

## Overview

The solution integrates AI capabilities directly into Gerrit’s change screen, allowing reviewers to request AI-generated insights during the code review process. The solution is a modular way so that the underlying AI service, interaction style, or supported commands can evolve without disrupting current Code review flow.

---

## Detailed Design

The feature is designed to integrate AI assistance into Gerrit code reviews by introducing an extension point in Gerrit core and a plugin-based implementation.

### Backend (Core + Plugin)

#### Gerrit Core

Gerrit core will provide a new @ExtensionPoint interface:

```
public interface AiCodeReviewProvider {
  AiResponse getAiReview(AiCodeReviewInput input) throws IOException;
}
```

This extension point allows plugins to implement custom integrations with AI models.

> Change: https://gerrit-review.googlesource.com/c/gerrit/+/500001

#### Plugin Implementation

A plugin will provide the implementation of `AiCodeReviewProvider`.

The plugin will:

Fetch AI API configuration (e.g., model endpoint, auth URL) from `gerrit.config`

Read credentials (e.g., client id, secret, API key) from `secure.config`

Connect to the external AI service (e.g., Gemini, GPT) and forward the review request.

> Change: https://github.com/Hiteshchaudhari1/poc-ai-review

### REST API in Core

A new Gerrit REST endpoint will be introduced:

POST /changes/{change}/revisions/{patch-set}/ai-review


Input format:
```
{
"models": ["gemini-2.5-pro"],
"prompt": "Hello, my name is Hitesh",
"plugin_name": "sap-ai-review"
}
```

Output format:
```
{
  "gemini-2.5-pro": {
  "response": "Hello from AI",
  "status": "SUCCESS"
  }
}
```

The REST layer will invoke the plugin via the AiCodeReviewProvider extension point, gather responses from configured models, and return them in a structured JSON format.

### Frontend

On `gr-change-view` page, there will be an option `AI Code Review Assitant`.

On clicking this UI button, The existing `gr-change-view` will be split vertically:

**Left Panel**: Existing Gerrit change page (diffs, comments, metadata etc).

**Right Panel**: Interactive AI panel.

 - AI Panel: The AI panel will provide an interactive chat-like interface (Chatbot).

    Users can:
     - Select one of the configured AI models. Implemented AI models information will be exposed using `ServerInfo API (config/server/info)`
     - Enter a prompt/query.
     - View AI-generated responses.


The UI will call the new /ai-review endpoint with the user’s input and selected models.

Responses will be displayed in the right-side panel in a conversational manner.

> Change: https://gerrit-review.googlesource.com/c/gerrit/+/510583
---

## Alternatives Considered

**Floating / Hovering Dialog Box**
One option considered was to implement the AI interaction panel as a floating dialog box overlaying the change screen. This approach would allow the user to open and close the chatbot dynamically without altering the existing screen layout. However, it posed challenges in terms of accessibility, discoverability, and usability. Long conversations could become difficult to manage within a small, constrained popup window. It also risked obscuring important parts of the change screen, leading to a bad user experience.

[Chatbot Hovering dialogue box UI](https://github.com/Hiteshchaudhari1/poc-ai-review/blob/main/Screenshot%202025-09-15%20at%2013.45.18.png)

**Split Screen (Chosen Design)**
The chosen approach is to split the gr-change-view vertically, where the left side continues to display the existing change screen, and the right side provides a dedicated panel for AI interactions. This ensures that users can engage with the AI contextually while still keeping full visibility of the code review details. The design is more accessible, supports longer conversations, and avoids UI overlap or screen clutter.

[Chatbot split screen view UI](https://github.com/Hiteshchaudhari1/poc-ai-review/blob/main/Screenshot%202025-09-29%20at%2013.07.38.png)

---
## Pros and Cons

**Floating / Hovering Dialog Box**
Pros
  - Lightweight design, does not require significant restructuring of the Gerrit UI.
  - Can be opened or dismissed dynamically, offering flexibility.

Cons
  - Limited space makes long AI conversations difficult to manage.
  - Risk of obscuring critical parts of the change screen, reducing usability.
  - Poor accessibility: harder to discover and interact with compared to a dedicated panel.

Split Screen (Preferred)
Pros
  - Dedicated panel for AI interactions without interfering with the change view.
  - Supports long and contextual AI conversations alongside the code review.
  - Improves accessibility and discoverability—always visible once enabled.
  - Clean, structured layout that avoids screen clutter.
  - Easier to maintain a consistent interaction state across page reloads.

Cons
  - Reduces available width for the change screen, which may impact users on smaller monitors.
  - Requires more significant changes in the Gerrit UI layout.

---

## Implementation Plan

Based on the mentioned changes, you can try out a POC version of the feature already.

Core:
  - [504802](https://gerrit-review.googlesource.com/c/gerrit/+/504802)
  - [500001](https://gerrit-review.googlesource.com/c/gerrit/+/500001)
  - [510583](https://gerrit-review.googlesource.com/c/gerrit/+/510583)

Plugin:
  - [poc-ai-review](https://github.com/Hiteshchaudhari1/poc-ai-review)
