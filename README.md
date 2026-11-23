# PBill

PBill is an Android app to split shared expenses between friends, family or
housemates, for a holiday, a shared flat or an evening out. Its specificity:
each member has a **weight**, so that one payer can stand for a whole
household.

- A person counts for 1, a couple for 2, a couple with a child for 2.5 or 3…
- Someone who takes part without paying (a guest, a child) can have a weight
  of 0.

PBill is **deliberately simple**, with no bells and whistles: no account, no
cloud, no ads, no statistics or charts, just the expenses and who owes what.
It still tries to cover every way of sharing costs: uneven shares, people
joining along the way, someone who only pays for part of the expenses,
reimbursements between members.

## Features

- **Tabs**: one tab per shared budget (a trip, a flat…), each with its own
  currency, chosen from a searchable list. Name and currency can be changed
  later (amounts are kept as they are, not converted).
- **Members** with a default weight and a participation period: a start date
  (by default the day they are added) and an optional end date. New expenses
  are shared by default between the members taking part on their date;
  expenses already recorded are never changed.
- **Expenses** split proportionally to the weights. Weights can be changed or
  members unchecked for a single expense; a live preview shows each share.
  Weights are copied into every expense, so changing a member's weight later
  does not rewrite the past.
- **Categories**: an optional category per expense (groceries, restaurant,
  accommodation, car, travel…) shown as an icon in the list.
- **Exact amounts**: amounts are stored in the currency minor unit (cents)
  and every split adds up exactly to the expense amount.
- **Balances** of every member and **suggested reimbursements**, which can be
  recorded as paid in one tap; manual reimbursements between members. The
  reimbursements made are listed with the balances, where they can be changed
  or deleted.
- **PDF report** of the amounts paid, due and already reimbursed, the
  remaining reimbursements and the list of all expenses, opened in a PDF
  viewer or shared through the standard Android share sheet.
- **Backup**: export of every tab to a JSON file, in Downloads by default (a
  shared folder, so the file survives the uninstallation of the app), and
  import of such a file; an imported tab replaces the one with the same id.
- **Settings**: default currency for new tabs, and an option to reopen the
  last tab at startup.
- **Languages**: English, French, German, Spanish, Italian, Russian and
  Vietnamese.
- **Privacy**: no account, no network access, no tracking; the data stays on
  the device, and only leaves it in the PDF reports and backups you share or
  export yourself.

## Requirements

- Android 12 (API 31) or later.
- To build: Linux, `make`, `curl`, `tar` and `unzip`. The JDK, the Android SDK and
  the emulator are installed locally under `tools/` by the Makefile; no
  system-wide install is needed.
- For the F-Droid tooling: [pixi](https://pixi.sh) (installed by the
  Makefile if missing); `make tools-fdroid` creates the environment from
  `pixi.toml` and `pixi.lock`.

## Building

Every operation goes through the Makefile (`make help` lists all targets):

```bash
make tools-install      # first time: JDK, Android SDK and Gradle wrapper in tools/
make debug              # debug APK: app/build/outputs/apk/debug/app-debug.apk
make build              # release APK
make bundle             # Android App Bundle (.aab) for Google Play
make test               # unit tests
make lint               # Android Lint
```

Without a release key, `make build` produces an unsigned release APK (as the
F-Droid build server expects it); `make release-apk` builds the signed one, see
[Release signing](#release-signing).

## Running

On a phone connected over USB (USB debugging enabled):

```bash
make run-debug                # build, install and launch
make run-debug LANG=de_DE     # same, with the app shown in German
make lang LANG=system         # back to the phone language
```

On the emulator:

```bash
make tools-install-emu        # once: emulator, system image and AVD (~2 GB)
make avd-start                # start it; the app language follows LANG
make avd-run-debug            # build, install and launch
```

## Project layout

| Path | Content |
| --- | --- |
| `app/src/main/java/net/cardolan/pbill/domain/` | Pure logic: money, weights, splits, balances, settle-up, currencies |
| `app/src/main/java/net/cardolan/pbill/data/` | Room database (tabs, members, expenses, shares) and settings |
| `app/src/main/java/net/cardolan/pbill/ui/` | Jetpack Compose screens and view models |
| `app/src/main/java/net/cardolan/pbill/report/` | PDF report |
| `app/src/main/res/values*/strings.xml` | Translations |
| `app/src/test/` | Unit tests of the domain logic |
| `app/schemas/` | Exported Room schemas |
| `art/icon.svg` | Launcher icon source |
| `tools/make_icon.py` | Converts the icon into Android vector drawables |

The code calls a tab a *group* (`GroupEntity`, `GroupScreen`…); only the
user-facing text says *tab*.

## Launcher icon

`art/icon.svg` is the final adaptive icon on the 108 × 108 dp canvas, with one
Inkscape layer per Android layer (`background`, `foreground`, and
`monochrome` for Android 13+ themed icons). After editing it:

```bash
make icon             # regenerate the vector drawables
make icon-preview     # preview under the launcher masks: build/icon/previews.png
```

## Translations

Strings live in `app/src/main/res/values*/strings.xml` (English is the default
in `values/`). Automatic translations are marked with
`<!-- ==AUTO== Automatically translated -->` until a human reviews them:

```bash
grep -r "==AUTO==" app/src/main/res
```

## Release signing

The APKs published on GitHub (and on F-Droid when its build is reproducible)
are signed with the PBill release key. SHA-256 fingerprint of its certificate:

```text
BD:81:BE:4B:A1:AF:1A:D4:3B:F3:EC:9C:31:D4:6B:2C:F8:10:D5:83:AD:64:04:AC:B5:5F:ED:D6:BB:86:66:C9
```

Check a downloaded APK with
`apksigner verify --print-certs pbill-X.Y.Z.apk`. The Google Play version is
signed by Google with another key: switching between it and the other
versions requires uninstalling the app (and losing its data).

- Locally: copy `keystore.properties.example` to `keystore.properties`
  (ignored by git), fill it in, then `make release-apk` builds
  `dist/pbill-X.Y.Z.apk` and checks its certificate (`-dev` is appended
  unless HEAD is exactly tagged `vX.Y.Z` with a clean working tree).
- On GitHub: the CI (`.github/workflows/ci.yml`) runs the tests and Android
  Lint and builds the APK on every push and pull request, signed with the
  repository secrets `PBILL_KEYSTORE_BASE64`, `PBILL_KEYSTORE_PASSWORD` and
  `PBILL_KEY_ALIAS` when available; the APK is kept as a workflow artifact.
  Only a `vX.Y.Z` tag also creates the release of the tag with the APK, whose
  text is the `CHANGELOG.md` section of the version
  (`tools/extract_changelog.py`).

## Development

Pre-commit hooks run Android Lint, the unit tests and markdownlint before
every commit (`pre-commit install` once, `make hooks` to run them on demand).
Changes are recorded in [CHANGELOG.md](CHANGELOG.md).

PBill is developed with the help of
[Claude Code](https://claude.com/claude-code).

## License

PBill is free software: you can redistribute it and/or modify it under the
terms of the **GNU General Public License v3** as published by the Free Software
Foundation. See the [LICENSE](LICENSE) file for details.
