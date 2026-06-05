# Documentation Maintenance Specifications

Update Time: 2026-04-07

## 1. Objective

Ensure that system behavior, configuration, deployment, and troubleshooting remain fully consistent with the documentation at all times, avoiding scenarios where "code has changed but documentation lags behind."

## 2. Scope of Application

The documentation must be synchronously updated for any of the following changes:

* New features or pages
* API additions, modifications, or deprecations
* Changes in fields, status codes, sorting rules, or filtering rules
* Database schema changes
* Changes in deployment methods, ports, domains, or service names
* Changes in configuration items, environment variables, or third-party dependencies
* Changes in handling strategies for known issues

## 3. Mandatory Files to Update

Minimum updates required:

* `docs/System_Complete_User_Manual.md`
* `docs/Changelog.md`

As-needed updates:

* `README.md` (Entry point, quick start, key capabilities)
* Other specialized documentation (if added in the future)

## 4. Update Process (Mandatory)

1. Before Development: Confirm whether the change impacts the documentation.
2. During Development: Record "points of behavioral change" and "points of configuration change."
3. Before Submission: Complete the documentation updates.
4. Upon Acceptance: Verify each item against the "Documentation Verification Checklist."

## 5. Documentation Verification Checklist

Please self-check before each submission:

* Is the feature entry point (frontend/backend path) clearly explained?
* Are the parameter/field changes clearly explained?
* Are the status handling and exception handling clearly explained?
* Are the deployment or configuration impacts clearly explained?
* Has a record been added to `docs/Changelog.md` (including date, module, change, and impact)?
* Are the example commands in the documentation executable?

## 6. Changelog Format (Unified)

Add new records to `docs/Changelog.md` using the following template:

```text
## YYYY-MM-DD
- Module: 
- Type: Add / Modify / Fix / Refactor / DevOps
- Change: 
- Scope of Impact: 
- Migration Required: Yes/No (If yes, specify the steps clearly)

```

## 7. Accountabilities

* Feature Developer: Responsible for synchronously updating the documentation.
* Code Reviewer: Responsible for checking whether the documentation has been synchronized.
* Release Executor: Responsible for confirming that the documentation matches the production status.

## 8. Release Gate (Recommended)

Changes with incomplete documentation updates should not be released to the production environment.
