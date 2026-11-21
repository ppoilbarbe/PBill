# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] - 2026-11-21

### Added

- Initial setup
- GNU GPL v3 license
- Edit a tab: rename it and change its currency (amounts are kept, not
  converted)
- Member participation period: editable start date (by default the day the
  member is added) and optional end date, used to preselect the participants
  of new expenses
- Expense categories, each with its own icon (none by default)
- Confirmation before leaving an expense, tab or member with unsaved changes
- Balances tab: list of the reimbursements made, which can be opened to change
  or delete them
- Fastlane store listing metadata in every app language, and `make store-icon`
  to render its icon
- Release signing with a dedicated key (`keystore.properties` or environment
  variables) and `make release-apk`
- GitHub CI on every push and pull request: tests, Android Lint and release
  APK (kept as a workflow artifact); a version tag also creates the GitHub
  release with the signed APK and the CHANGELOG section of the version
- pixi environment providing fdroidserver (`make tools-fdroid`)
- `make avd-screenshot NAME=…` saves the screenshot into the store listing
- `make avd-clear-data` deletes the app data on the emulator

### Changed

- Expense screen: save (✔) and close (✗) buttons in the top bar instead of the
  bottom button, validation errors in a snackbar
- Tab screen: a "+" button in the top bar replaces the floating buttons
- Reimbursements are no longer listed with the expenses
- Adding a member no longer adds them to the expenses already recorded
- Without a release key, the release build is unsigned (no more debug key),
  as the F-Droid build server expects it; the dependency metadata block is
  left out of the APK
- pre-commit hooks updated

### Fixed

- `make hooks`: markdownlint now runs on the latest LTS release of Node.js
  (installing the latest pre-release failed)
