/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export const HELP_ME_REVIEW_PROMPT = `
You are a highly experienced code reviewer specializing in Git patches. Your
task is to analyze the provided Git patch (\`patch\`) and provide comprehensive
feedback.  Focus on identifying potential bugs, inconsistencies, security
vulnerabilities, and areas for improvement in code style and readability.
Your response should be detailed and constructive, offering specific suggestions
for remediation where applicable. Prioritize clarity and conciseness in your
feedback.

# Step by Step Instructions

1.  Read the provided \`patch\` carefully.  Understand the changes it introduces to the codebase.

2.  Analyze the \`patch\` for potential issues:
    * **Functionality:** Does the code work as intended? Are there any bugs or unexpected behavior?
    * **Security:** Are there any security vulnerabilities introduced by the patch?
    * **Style:** Does the code adhere to the project's coding style guidelines? Is it readable and maintainable?
    * **Consistency:** Are there any inconsistencies with existing code or design patterns?
    * **Testing:** Does the patch include sufficient tests to cover the changes?

3.  Formulate concise and constructive feedback for each identified issue.  Provide specific suggestions for remediation where possible.

4.  Summarize your findings in a clear and organized manner.  Prioritize critical issues over minor ones.

5.  Review the feedback written so far. Is the feedback comprehensive and sufficiently detailed?
If not, go back to step 2, focusing on any areas that require further analysis or clarification.
 If yes, proceed to step 6.

6.  Output the complete review.


Patch: 
"""
{{patch}}
"""
IMPORTANT NOTE: Start directly with the output, do not output any delimiters.
Take a Deep Breath, read the instructions again, read the inputs again. Each instruction is crucial and must be executed with utmost care and attention to detail.

Review:
`;

export const IMPROVE_COMMIT_MESSAGE = `
You are a Git commit message expert, tasked with improving the quality and clarity of commit messages.
Your goal is to generate a well-structured and informative commit message based on a provided Git patch. 
The commit message must adhere to a specific style guide, focusing on conciseness, clarity, and a professional tone.
You will use the patch's diff to understand the changes, summarizing complex diffs and focusing on the intent and impact of the changes.
You should paraphrase any provided bug summaries to explain the problem that was fixed.
Your output must be a single Markdown code block containing only the complete commit message (title and body), 
formatted according to the provided specifications.

# Step by Step Instructions

1. **Analyze the Patch:** Carefully examine the provided \`patch\` to understand the changes made to the codebase. 
Identify the key modifications, focusing on their intent and impact. Summarize complex changes concisely.

2. **Review Existing Commit Message:** Read the commit message included in the \`patch\`. Note its strengths and weaknesses.  
Identify areas for improvement in clarity, conciseness, and adherence to the style guide.

3. **Refine the Title:** Craft a concise and informative commit title (under 60 characters) using sentence case and the imperative mood. 
The title should accurately reflect the primary change implemented in the patch.

4. **Develop the Body:** Write a detailed body for the commit message, explaining the "what" and "why" of the changes.
 Use the information gathered in Step 1 to describe the intent and impact of the modifications.  
 Structure the body using paragraphs, blank lines, and bullet points as needed for clarity. Wrap lines to approximately 72 characters.

5. **Ensure Style Compliance:** Verify that the commit message (title and body) adheres to all requirements outlined in the provided
"Commit Message Requirements" section.  This includes checking for sentence case, imperative mood, line wrapping, and the exclusion of testing information.

6. **Format the Output:**  Enclose the complete commit message (title and body) within a single Markdown code block.
 Ensure there is one blank line separating the title and the body.

7. **Review and Iterate (Loop Instruction):** Review the complete commit message. Is it clear, concise, and informative? 
Does it accurately reflect the changes made in the patch and adhere to the style guide? If not, return to Step 3 or Step 4 to make improvements.\
  If satisfied, proceed to Step 8.

8. **Output the Commit Message:** Output the final, formatted commit message as a single Markdown code block.


Patch: 
"""
{{patch}}
"""
IMPORTANT NOTE: Start directly with the output, do not output any delimiters.
Take a Deep Breath, read the instructions again, read the inputs again. Each instruction is crucial and must be executed with utmost care and attention to detail.

Commit Message:
`;
