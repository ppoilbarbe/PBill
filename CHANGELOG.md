# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- `make icon-png` renders the icon as shown by Android (rounded corners,
  512 px) into `art/pbill-android.png`
- CI: every push to `main` updates the "Development build" pre-release
  (`dev-build` tag) with the latest APK, downloadable under a fixed name
  (`pbill-dev.apk`)

### Changed

- Back on the list of tabs asks to press again, then quits the app instead of
  leaving it in the background
- About screen, README and store listing: PBill shows no notifications
- Launcher icon source renamed from `art/icon.svg` to `art/pbill-android.svg`

## [0.3.0] - 2026-11-23

### Added

- "Fuel/Energy" expense category, with a fuel pump icon
- "Tolls" expense category (motorway tolls), with a toll booth icon
- Settings: export of every tab to a JSON file (`pbill-<date>.json`, in Downloads
  by default, so it survives the uninstallation of the app) and import of such a
  file; an imported tab replaces the tab with the same id, the others are kept

### Changed

- "Reopen the last tab" reopens the last tab opened even when the app was left
  from the list of tabs; only a deleted tab is not reopened
- Member names are trimmed of every kind of blank (Unicode spaces, zero-width
  characters) and must be unique within a tab

## [0.2.0] - 2026-11-21

### Added

- `make fdroid-check` checks and builds the F-Droid build metadata in the
  fdroiddata clone (`FDROIDDATA`, default `../fdroiddata`)
- `keystore.properties.example`, template of the local release signing
  settings (missing from 0.1.0)

### Changed

- Store listing and README: "deliberately simple" highlighted, and PBill
  "tries to" cover every way of sharing costs

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
