---
trigger: model_decision
description: When user want to write unit test report
---

# Unit Test Specification & Reporting Guidelines

This document outlines the standard rules and patterns for generating Unit Test Specifications using the Decision Table technique in this repository. All future unit test reports must strictly adhere to these rules.

---

## 1. Structure of a Method Test Suite
For each tested method, the specification must include the following sections in order:
1. **Method Header**: Format `## <Number>. <Method Signature>` (e.g. `## 1. createUser(CreateUserRequest request)`).
2. **Total Test Cases**: A note showing the total test cases which **must match 1-to-1** with the number of unit test methods in the corresponding test class.
3. **Brief Description**: A brief, concise description explaining what the method does and what the test scenarios validate. All test cases for the same method share this description.
4. **Preconditions**: General preconditions required for the tests to run (e.g. active DB, encoder, event publisher).
5. **Inputs Section**: A summary listing of parameters and database states.
6. **Decision & Test Case Table**: A markdown table mapping inputs to outputs.

---

## 2. Table Column & Row Group Structure
The Decision Table must use columns for test cases (TC) and group its rows into exactly five categories:

| Row Group | Description & Naming Rules | Example |
| :--- | :--- | :--- |
| **Inputs - request.param** | Method arguments and incoming request parameters. All values must be prefix-grouped as `request.<param_name>` (even if they are independent method parameters). Must contain **concrete values** (e.g. exact emails, passwords) rather than generic descriptions. | `request.email` = `"new@example.com"` <br> `request.status` = `ACTIVE` |
| **Inputs - DB State** | The mock database state or external mock responses. Use concise descriptions. | email `"new@example.com"` exists in DB |
| **Outputs** | Expected return type or behavior. | Returns `UserResponseDTO` <br> Throws `AppException` |
| **Output Actions** | Database operations, emails sent, or events published. | Save user to DB (password encoded) |
| **Messages** | The returned message or exception body. <br> - **For successful/passing cases**: Write `*(success — no message)*`. <br> - **For exception cases**: Write the exact JSON error format from `ErrorCode` enum. | `{"code": "USER-001", "message": "User not found"}` |
| **Exceptions** | The exact exception type thrown. | `AppException(ErrorCode.USER_NOT_FOUND)` |

---

## 3. General Formatting Rules
1. **No Raw Code Snippets in Inputs**: Do not copy raw Java implementation lines or complex code snippets into inputs. Keep them as simple variables, values, or states.
2. **1-to-1 Mapping**: Ensure every unit test method in the test file is mapped to exactly one column (`TC-...`) in the decision table. The names of the test methods must be specified under the TC identifier in parentheses.
3. **Clickable Links**: Link classes, service methods, and test classes to their absolute file paths using the `file:///` scheme.
