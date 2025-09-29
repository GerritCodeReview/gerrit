
# AI Code Review – Use Cases

## Goal
Integrate AI-assisted code review into Gerrit to support and enhance 
the human review process.

## Background / Status Quo
Currently, on the change view page, we have the 
**“Create AI Review Prompt”** feature. This functionality allows a 
Gerrit user to generate AI prompts such as:
1. **Help me with review** – ask AI to review code (includes patch).
2. **Improve commit message** – ask AI to enhance the commit message 
(includes patch).
3. **Just patch content** – provide only the patch-set content of 
4. the change.

## Limitations
This functionality is limited to prompt generation. The Gerrit user must 
manually copy the prompt and paste it into an AI model interface to get 
review insights, which creates friction and reduces usability.

## Objective
The goal of this feature is to enhance the Gerrit code review process by 
integrating AI capabilities directly into the review workflow.

Traditional code reviews rely entirely on human reviewers, which can be 
time-consuming and inconsistent depending on reviewer expertise. 
By leveraging AI models, we aim to:

- **Assist Reviewers:** Provide AI-generated insights, comments, and 
suggestions on submitted patches to help reviewers identify potential 
issues faster.
- **Improve Review Quality:** Detect common bugs, style violations, and 
inconsistencies that may otherwise be overlooked in manual reviews.
- **Accelerate Feedback:** Reduce turnaround time for initial feedback 
by allowing AI to pre-analyze code changes.
- **Support Developers:** Enable developers to request AI-generated review 
feedback before sending code for human review, giving them early insights 
- to improve their patches.

This feature will integrate AI models into Gerrit as a first-class 
extension, making AI review suggestions accessible through both 
the Gerrit UI and REST APIs. The AI functionality is intended to 
**complement, not replace**, human reviewers.

## Acceptance Criteria

- Gerrit provides a tool that allows users to interact with AI while 
reviewing changes.
- Users can select and interact with the AI model of their choice.
- Users can provide custom prompts or use predefined commands 
(e.g., *Code Review*, *Commit Message Feedback*).
- The AI processes the relevant context (e.g., patch set or commit message)
when generating a response.
- AI responses are displayed clearly and in a user-friendly format.
- If the AI service fails or is unreachable, the user sees a meaningful 
error message, and Gerrit functionality remains unaffected.
- If AI integration is disabled or not configured, no AI-related options 
appear in the UI.
- AI responses must not block or delay Gerrit’s core review features 
(e.g., commenting, voting, patch navigation). The change page must 
remain accessible at all times, regardless of AI interactions.
- Configuration (API key, model URL, etc.) is securely stored.
- The system logs errors and failures for administrators without 
exposing sensitive information to end users.
- The solution should support incremental improvements 
(e.g., adding new commands or models) without requiring major UI or 
workflow changes.
