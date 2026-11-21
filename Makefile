# ── Application identifiers ──────────────────────────────────────────────────

APP_ID   := net.cardolan.pbill
ACTIVITY := $(APP_ID)/.MainActivity
APK_DBG  := app/build/outputs/apk/debug/app-debug.apk
APK_REL  := app/build/outputs/apk/release/app-release.apk
AAB_REL  := app/build/outputs/bundle/release/app-release.aab

# Published release APK (make release-apk): dist/pbill-<version>.apk, signed
# with the release key whose certificate SHA-256 fingerprint is below (also in
# the README). The version is versionName; "-dev" is appended unless HEAD is
# exactly tagged v<version> with a clean working tree.
DIST_DIR         := dist
APP_VERSION      := $(shell sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
RELEASE_CERT_SHA256 := BD:81:BE:4B:A1:AF:1A:D4:3B:F3:EC:9C:31:D4:6B:2C:F8:10:D5:83:AD:64:04:AC:B5:5F:ED:D6:BB:86:66:C9

# Store listing metadata (fastlane layout, read by F-Droid)
FASTLANE_DIR := fastlane/metadata/android
# Fastlane locale of the screenshots taken with "make avd-screenshot NAME=…"
LOCALE       ?= en-US

# ── Local tools (tools/) ─────────────────────────────────────────────────────
#
#   make tools-install   → full first-time setup
#   make tools-update    → update SDK packages
#
# Update the variables below when new versions are released.
# Sources:
#   JDK      https://adoptium.net/temurin/releases/?version=17
#   cmdtools https://developer.android.com/studio#command-line-tools-only
#   Studio   https://developer.android.com/studio#downloads
#   fdroid   https://pypi.org/project/fdroidserver/ (pinned in pixi.lock)

TOOLS_DIR    := tools
JDK_DIR      := $(TOOLS_DIR)/jdk
SDK_DIR      := $(TOOLS_DIR)/android-sdk
STUDIO_DIR   := $(TOOLS_DIR)/android-studio
SDK_MANAGER  := $(SDK_DIR)/cmdline-tools/latest/bin/sdkmanager
# fdroidserver (F-Droid tooling) lives in the pixi environment (pixi.toml)
PIXI         := $(HOME)/.pixi/bin/pixi
FDROID       := $(PIXI) run fdroid

JDK_VERSION         := 17
# Android command-line tools build number (folder in the Google URL)
CMDLINE_TOOLS_BUILD := 11076708
STUDIO_VERSION      := 2024.3.2.14

CMDLINE_TOOLS_URL := https://dl.google.com/android/repository/commandlinetools-linux-$(CMDLINE_TOOLS_BUILD)_latest.zip
JDK_URL           := https://api.adoptium.net/v3/binary/latest/$(JDK_VERSION)/ga/linux/x64/jdk/hotspot/normal/eclipse
STUDIO_URL        := https://dl.google.com/dl/android/studio/ide-zips/$(STUDIO_VERSION)/android-studio-$(STUDIO_VERSION)-linux.tar.gz

# SDK packages installed/updated through sdkmanager
SDK_PACKAGES     := platform-tools build-tools;36.0.0 platforms;android-36
# Emulator packages (separate because ~2 GB, installed by tools-install-emu)
SDK_PACKAGES_EMU := emulator system-images;android-35;google_apis_playstore;x86_64

# Emulator AVD
AVD_NAME    := PBill_AVD
AVD_DEVICE  := pixel_5
AVD_PACKAGE := system-images;android-35;google_apis_playstore;x86_64
EMULATOR    := $(SDK_DIR)/emulator/emulator
AVD_MANAGER := $(SDK_DIR)/cmdline-tools/latest/bin/avdmanager
AVD_FLAGS   := -no-snapshot -no-audio -gpu swiftshader_indirect
AVD_CONFIG  := $(or $(ANDROID_AVD_HOME),$(HOME)/.android/avd)/$(AVD_NAME).avd/config.ini

# App language on the emulator, taken from LANG: the user's locale by default,
# overridable with "make avd-start LANG=en_US". Converts the POSIX form
# (fr_FR.UTF-8, de_DE@euro) to an Android language tag (fr-FR); C/POSIX → en-US.
# The Google Play system image has no root, so the system language cannot be
# changed (emulator -change-locale needs su): the per-app language of Android
# 13+ is used instead, which is what matters to test the app translations.
# On a device (install, run…), the language is set only when LANG is given on
# the command line; LANG=system restores the device language.
_LANG_BASE := $(firstword $(subst ., ,$(subst @, ,$(LANG))))
APP_LOCALE := $(if $(filter system,$(LANG)),,$(if $(filter-out C POSIX,$(_LANG_BASE)),$(subst _,-,$(_LANG_BASE)),en-US))
_LANG_SET  := $(filter command line,$(origin LANG))

# ── Automatic activation of the local JDK and SDK ────────────────────────────
#
# When tools/jdk/ and tools/android-sdk/ exist, they take precedence over the
# system ones. The exports apply to every recipe in this file.

ifneq ($(wildcard $(JDK_DIR)/bin/java),)
  export JAVA_HOME := $(abspath $(JDK_DIR))
  export PATH      := $(abspath $(JDK_DIR)/bin):$(PATH)
endif

ifneq ($(wildcard $(SDK_DIR)/platform-tools/adb),)
  export ANDROID_HOME := $(abspath $(SDK_DIR))
  ADB := $(abspath $(SDK_DIR)/platform-tools/adb)
else
  ADB := adb
endif

# ADB targeting the emulator only (avoids conflicts with a connected device)
ADBE := $(ADB) -e

GW := ./gradlew

.DEFAULT_GOAL := help

# ── ADB guards (internal prerequisites) ──────────────────────────────────────

.PHONY: _check-device
_check-device:
	@$(ADB) devices 2>&1 | grep -qE "[[:space:]]device$$" || { \
	  echo "✗ No device connected."; \
	  echo "  → Physical device: enable USB debugging and plug the cable"; \
	  echo "  → Emulator       : make avd-start"; \
	  exit 1; }

.PHONY: _check-emulator
_check-emulator:
	@$(ADB) devices 2>&1 | grep -q "^emulator" || { \
	  echo "✗ No emulator running."; \
	  echo "  → Run: make avd-start"; \
	  exit 1; }

# Gradle needs an Android SDK: tools/android-sdk (exported as ANDROID_HOME
# above), an ANDROID_HOME from the environment, or sdk.dir in local.properties
.PHONY: _check-sdk
_check-sdk:
	@if [ -z "$$ANDROID_HOME" ] && ! grep -qs '^sdk.dir=' local.properties; then \
	  echo "✗ Android SDK not found."; \
	  echo "  → Install it locally: make tools-install"; \
	  echo "  → Or symlink tools/jdk and tools/android-sdk to an existing install"; \
	  exit 1; \
	fi

# Waits for the emulator to appear in ADB, then for Android to finish booting
.PHONY: _wait-emulator
_wait-emulator:
	@echo "→ Waiting for the ADB connection…" && \
	serial="" && \
	for i in $$(seq 1 60); do \
	    serial=$$($(ADB) devices | awk '/^emulator.*\tdevice$$/{print $$1; exit}'); \
	    [ -n "$$serial" ] && break; \
	    sleep 2; \
	done && \
	[ -n "$$serial" ] || { echo "✗ Emulator not detected after 2 min"; exit 1; } && \
	echo "→ Waiting for Android to boot on $$serial (1-2 min)…" && \
	$(ADB) -s $$serial shell 'while [ "$$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' && \
	$(ADB) -s $$serial shell input keyevent 82 && \
	echo "\033[32m✓ Emulator ready\033[0m"

# ── Help ─────────────────────────────────────────────────────────────────────

.PHONY: help
help: ## Show this help
	@awk 'BEGIN{FS=":.*##"; printf "\nUsage: make \033[36m<target>\033[0m\n"} \
	  /^[a-zA-Z_-]+:.*##/{printf "  \033[36m%-22s\033[0m %s\n",$$1,$$2} \
	  /^## /{printf "\n\033[1m%s\033[0m\n",substr($$0,4)}' $(MAKEFILE_LIST)

# ── Local tools ──────────────────────────────────────────────────────────────

## Local tools (tools/)

.PHONY: tools-install
tools-install: tools-jdk tools-sdk tools-wrapper tools-fdroid ## First-time setup: JDK + Android SDK + gradlew + fdroidserver
	@echo ""
	@echo "\033[32m✓ Installation complete.\033[0m"
	@echo "  To add the emulator (~2 GB): make tools-install-emu"
	@echo "  To check: make tools-status"

.PHONY: tools-update
tools-update: tools-update-jdk tools-update-sdk tools-update-fdroid ## Update the JDK, SDK packages and fdroidserver
	@echo "\033[32m✓ Update complete.\033[0m"

.PHONY: tools-status
tools-status: ## Show the versions of the tools installed in tools/
	@echo "\033[1m=== Local tools ===\033[0m"
	@printf "  %-18s" "Java (local):"
	@$(JDK_DIR)/bin/java -version 2>&1 | head -1 || echo "not installed"
	@printf "  %-18s" "Java (system):"
	@java -version 2>&1 | head -1 || echo "missing"
	@printf "  %-18s" "SDK Manager:"
	@$(SDK_MANAGER) --version 2>/dev/null || echo "not installed"
	@printf "  %-18s" "ADB:"
	@$(ADB) version 2>/dev/null | head -1 || echo "not installed"
	@printf "  %-18s" "Android Studio:"
	@cat $(STUDIO_DIR)/product-info.json 2>/dev/null | grep -o '"version":"[^"]*"' | head -1 || echo "not installed"
	@printf "  %-18s" "gradlew:"
	@test -x gradlew && $(GW) --version 2>/dev/null | grep "^Gradle" || echo "missing (make tools-wrapper)"
	@printf "  %-18s" "Emulator:"
	@$(EMULATOR) -version 2>/dev/null | head -1 || echo "not installed (make tools-install-emu)"
	@printf "  %-18s" "AVD $(AVD_NAME):"
	@$(AVD_MANAGER) list avd 2>/dev/null | grep -q "$(AVD_NAME)" && echo "created" || echo "not created (make avd-create)"
	@printf "  %-18s" "fdroidserver:"
	@$(PIXI) list --json fdroidserver 2>/dev/null | grep -o '"version": *"[^"]*"' | head -1 | cut -d'"' -f4 | grep . || echo "not installed (make tools-fdroid)"
	@echo ""
	@echo "\033[1m=== Installed SDK packages ===\033[0m"
	@$(SDK_MANAGER) --list_installed 2>/dev/null | grep -E "build-tools|platforms;|platform-tools|emulator|system-images" || echo "  (SDK not installed)"

.PHONY: tools-clean
tools-clean: ## Remove all local tools (frees disk space)
	@read -p "Remove tools/jdk, tools/android-sdk, tools/android-studio and the pixi environment? [y/N] " ans; \
	[ "$$ans" = "y" ] && rm -rf $(JDK_DIR) $(SDK_DIR) $(STUDIO_DIR) && { $(PIXI) clean 2>/dev/null || true; } && echo "Removed." || echo "Cancelled."

# ── JDK ──────────────────────────────────────────────────────────────────────

## Temurin JDK

.PHONY: tools-jdk
tools-jdk: ## Install Temurin JDK $(JDK_VERSION) into tools/jdk/ (skipped if present)
	@if [ -x "$(JDK_DIR)/bin/java" ]; then \
	  echo "JDK already installed: $$($(JDK_DIR)/bin/java -version 2>&1 | head -1)"; \
	else \
	  $(MAKE) --no-print-directory _tools-jdk-download; \
	fi

.PHONY: tools-update-jdk
tools-update-jdk: ## Update the JDK (downloads the latest Temurin $(JDK_VERSION) again)
	rm -rf $(JDK_DIR)
	$(MAKE) --no-print-directory _tools-jdk-download

.PHONY: _tools-jdk-download
_tools-jdk-download:
	@echo "→ Downloading Temurin JDK $(JDK_VERSION)…"
	mkdir -p $(JDK_DIR)
	curl -fL --progress-bar -o /tmp/jdk.tar.gz "$(JDK_URL)"
	tar -xf /tmp/jdk.tar.gz --strip-components=1 -C $(JDK_DIR)
	rm /tmp/jdk.tar.gz
	@echo "✓ JDK installed: $$($(JDK_DIR)/bin/java -version 2>&1 | head -1)"

# ── fdroidserver ─────────────────────────────────────────────────────────────

## fdroidserver (F-Droid tooling, through pixi)

$(PIXI):
	@echo "→ pixi not found, installing it into ~/.pixi/bin…"
	curl -fsSL https://pixi.sh/install.sh | sh

.PHONY: tools-fdroid
tools-fdroid: $(PIXI) ## Create/sync the pixi environment that provides fdroidserver (pixi.toml)
	@echo "→ Syncing the pixi environment…"
	$(PIXI) install
	@echo "✓ fdroidserver: $$($(FDROID) --version 2>/dev/null | tail -1)"

.PHONY: tools-update-fdroid
tools-update-fdroid: $(PIXI) ## Update fdroidserver and the pixi lockfile
	@echo "→ Updating the pixi environment…"
	$(PIXI) update
	@echo "✓ fdroidserver: $$($(FDROID) --version 2>/dev/null | tail -1)"

# ── Android SDK ──────────────────────────────────────────────────────────────

## Android SDK

.PHONY: tools-sdk
tools-sdk: ## Install cmdline-tools, platform-tools and build-tools into tools/android-sdk/
	@if [ -x "$(SDK_MANAGER)" ]; then \
	  echo "cmdline-tools already present. Install packages with 'make tools-update-sdk'."; \
	else \
	  $(MAKE) --no-print-directory _tools-sdk-cmdline; \
	fi
	$(MAKE) --no-print-directory _tools-sdk-packages
	$(MAKE) --no-print-directory _tools-local-properties

.PHONY: tools-update-sdk
tools-update-sdk: ## Update all installed SDK packages
	@echo "→ Updating the Android SDK…"
	yes | $(SDK_MANAGER) --update
	$(MAKE) --no-print-directory _tools-sdk-packages

.PHONY: _tools-sdk-cmdline
_tools-sdk-cmdline:
	@echo "→ Downloading the Android command-line tools (build $(CMDLINE_TOOLS_BUILD))…"
	mkdir -p $(SDK_DIR)/cmdline-tools
	curl -fL --progress-bar -o /tmp/cmdtools.zip "$(CMDLINE_TOOLS_URL)"
	unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools-extract/
	mv /tmp/cmdtools-extract/cmdline-tools $(SDK_DIR)/cmdline-tools/latest
	rm -rf /tmp/cmdtools.zip /tmp/cmdtools-extract
	@echo "✓ cmdline-tools installed"

.PHONY: _tools-sdk-packages
_tools-sdk-packages:
	@echo "→ Accepting SDK licenses…"
	yes | $(SDK_MANAGER) --licenses > /dev/null 2>&1 || true
	@echo "→ Installing SDK packages: $(SDK_PACKAGES)"
	$(SDK_MANAGER) $(foreach p,$(SDK_PACKAGES),'$(p)')

.PHONY: _tools-local-properties
_tools-local-properties:
	@echo "sdk.dir=$(abspath $(SDK_DIR))" > local.properties
	@echo "✓ local.properties updated (sdk.dir=$(abspath $(SDK_DIR)))"

.PHONY: tools-install-emu
tools-install-emu: tools-sdk-emu avd-create ## Install the emulator + system image and create the AVD (~2 GB)
	@echo "\033[32m✓ Emulator ready. Run: make avd-start\033[0m"

.PHONY: tools-sdk-emu
tools-sdk-emu: ## Install the emulator packages through sdkmanager (emulator + system image)
	@echo "→ Accepting SDK licenses…"
	yes | $(SDK_MANAGER) --licenses > /dev/null 2>&1 || true
	@echo "→ Installing emulator packages (~2 GB): $(SDK_PACKAGES_EMU)"
	$(SDK_MANAGER) $(foreach p,$(SDK_PACKAGES_EMU),'$(p)')

# ── Android Studio ───────────────────────────────────────────────────────────

## Android Studio

.PHONY: tools-studio
tools-studio: ## Download and install Android Studio $(STUDIO_VERSION) into tools/android-studio/
	@if [ -d "$(STUDIO_DIR)" ]; then \
	  echo "Android Studio already installed. Use 'make tools-update-studio' to update."; \
	else \
	  $(MAKE) --no-print-directory _tools-studio-download; \
	fi

.PHONY: tools-update-studio
tools-update-studio: ## Update Android Studio to version $(STUDIO_VERSION)
	rm -rf $(STUDIO_DIR)
	$(MAKE) --no-print-directory _tools-studio-download

.PHONY: _tools-studio-download
_tools-studio-download:
	@echo "→ Downloading Android Studio $(STUDIO_VERSION)…"
	@echo "  (~1 GB file, please wait…)"
	mkdir -p $(TOOLS_DIR)
	curl -fL --progress-bar -o /tmp/studio.tar.gz "$(STUDIO_URL)"
	tar -xf /tmp/studio.tar.gz -C $(TOOLS_DIR)/
	mv $(TOOLS_DIR)/android-studio $(STUDIO_DIR) 2>/dev/null || true
	rm /tmp/studio.tar.gz
	@echo "✓ Android Studio installed in $(STUDIO_DIR)/"
	@echo "  Run: $(STUDIO_DIR)/bin/studio.sh"

.PHONY: studio
studio: ## Launch Android Studio (must be installed with make tools-studio)
	@test -f $(STUDIO_DIR)/bin/studio.sh || \
	  { echo "Android Studio not installed. Run: make tools-studio"; exit 1; }
	$(STUDIO_DIR)/bin/studio.sh &

# ── Gradle wrapper ───────────────────────────────────────────────────────────

## Gradle wrapper

.PHONY: tools-wrapper
tools-wrapper: ## Generate gradlew with the local JDK (requires tools-jdk)
	@test -x "$(JDK_DIR)/bin/java" || \
	  { echo "Local JDK missing. Run: make tools-jdk"; exit 1; }
	@echo "→ Generating the Gradle wrapper…"
	@# Downloads gradle-wrapper.jar + gradlew from the official distribution
	mkdir -p gradle/wrapper
	curl -fsSL -o gradle/wrapper/gradle-wrapper.jar \
	  "https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
	curl -fsSL -o gradlew \
	  "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradlew"
	chmod +x gradlew
	@echo "✓ gradlew ready (Gradle 8.9)"

.PHONY: deps
deps: _check-sdk $(GW) ## Download all Gradle dependencies without compiling
	$(GW) dependencies --configuration releaseRuntimeClasspath

# ── Android emulator (AVD) ───────────────────────────────────────────────────

## Android emulator (AVD)

.PHONY: avd-create
avd-create: ## Create the '$(AVD_NAME)' AVD (Pixel 5, Android 35, Google Play)
	@test -x "$(AVD_MANAGER)" || \
	  { echo "avdmanager missing. Run: make tools-sdk-emu"; exit 1; }
	@echo "→ Creating AVD $(AVD_NAME)…"
	echo "no" | $(AVD_MANAGER) create avd \
	    --name "$(AVD_NAME)" \
	    --package "$(AVD_PACKAGE)" \
	    --device "$(AVD_DEVICE)" \
	    --force
	@$(MAKE) --no-print-directory avd-keyboard
	@echo "✓ AVD $(AVD_NAME) created"

.PHONY: avd-keyboard
avd-keyboard: ## Enable the host physical keyboard in the AVD (takes effect on next start)
	@test -f "$(AVD_CONFIG)" || { echo "AVD $(AVD_NAME) not found. Run: make avd-create"; exit 1; }
	@if grep -q '^hw.keyboard *=' "$(AVD_CONFIG)"; then \
	  sed -i 's/^hw.keyboard *=.*/hw.keyboard = yes/' "$(AVD_CONFIG)"; \
	else \
	  echo 'hw.keyboard = yes' >> "$(AVD_CONFIG)"; \
	fi
	@echo "✓ Physical keyboard enabled for $(AVD_NAME) (restart the emulator: make avd-stop avd-start)"

.PHONY: avd-list
avd-list: ## List all existing AVDs
	$(AVD_MANAGER) list avd

.PHONY: avd-devices
avd-devices: ## List the system images available to create AVDs
	$(SDK_MANAGER) --list 2>/dev/null | grep "system-images;android"

.PHONY: avd-start
avd-start: ## Start the '$(AVD_NAME)' emulator (app language: LANG=xx_YY, default: user locale)
	@test -x "$(EMULATOR)" || \
	  { echo "Emulator missing. Run: make tools-install-emu"; exit 1; }
	@echo "→ Starting emulator $(AVD_NAME)…"
	@nohup $(EMULATOR) -avd $(AVD_NAME) $(AVD_FLAGS) \
	    > /tmp/avd-$(AVD_NAME).log 2>&1 &
	@echo "  logs → /tmp/avd-$(AVD_NAME).log"
	@$(MAKE) --no-print-directory _wait-emulator
	@$(MAKE) --no-print-directory avd-lang

.PHONY: avd-stop
avd-stop: _check-emulator ## Stop the emulator cleanly
	$(ADBE) emu kill 2>/dev/null || true
	@echo "✓ Emulator stopped"

.PHONY: avd-install-debug
avd-install-debug: _check-emulator debug ## Build and install the debug APK on the emulator
	$(ADBE) install -r $(APK_DBG)
	@$(MAKE) --no-print-directory avd-lang

.PHONY: avd-lang
avd-lang: _check-emulator ## Set the app language on the emulator (LANG=xx_YY, default: user locale)
	@if $(ADBE) shell pm path $(APP_ID) > /dev/null 2>&1; then \
	  $(ADBE) shell cmd locale set-app-locales $(APP_ID) --locales '$(APP_LOCALE)' && \
	  echo "✓ $(APP_ID) language: $(or $(APP_LOCALE),device language)"; \
	else \
	  echo "  $(APP_ID) not installed yet: its language will be set on install"; \
	fi

.PHONY: avd-run-debug
avd-run-debug: avd-install-debug ## Build, install and launch the app on the emulator
	$(ADBE) shell am start -n $(ACTIVITY)

.PHONY: avd-restart
avd-restart: _check-emulator ## Restart the app on the emulator
	$(ADBE) shell am force-stop $(APP_ID)
	$(ADBE) shell am start -n $(ACTIVITY)

# Needed after a change of the database schema: there are no migrations
# before the first release, so Room refuses to open the old database.
.PHONY: avd-clear-data
avd-clear-data: _check-emulator ## Delete the app data on the emulator (database, settings), then restart it
	@read -p "Delete all PBill data on the emulator? [y/N] " ans; \
	[ "$$ans" = "y" ] || { echo "Cancelled."; exit 1; }
	$(ADBE) shell pm clear $(APP_ID)
	$(ADBE) shell am start -n $(ACTIVITY)

.PHONY: avd-log
avd-log: _check-emulator ## Live app logs on the emulator (Ctrl-C to quit)
	$(ADBE) logcat --pid=$$($(ADBE) shell pidof -s $(APP_ID)) -v color

.PHONY: avd-screenshot
avd-screenshot: _check-emulator ## Capture the emulator screen into screenshot.png, or the store screenshot NAME (LOCALE=en-US)
	@out=$(if $(NAME),$(FASTLANE_DIR)/$(LOCALE)/images/phoneScreenshots/$(NAME).png,screenshot.png); \
	mkdir -p "$$(dirname "$$out")" && \
	$(ADBE) exec-out screencap -p > "$$out" && \
	echo "✓ $$out"

.PHONY: avd-wipe
avd-wipe: ## Wipe all emulator data and restart it (cold boot)
	@read -p "Wipe all data of emulator $(AVD_NAME)? [y/N] " ans; \
	[ "$$ans" = "y" ] || { echo "Cancelled."; exit 1; }
	$(ADBE) emu kill 2>/dev/null || true
	sleep 2
	@nohup $(EMULATOR) -avd $(AVD_NAME) $(AVD_FLAGS) -wipe-data \
	    > /tmp/avd-$(AVD_NAME).log 2>&1 &
	@echo "→ Restarting with wiped data…"
	@$(MAKE) --no-print-directory _wait-emulator

# ── Build ────────────────────────────────────────────────────────────────────

## Build

.PHONY: build
build: _check-sdk $(GW) ## Build the release APK (unsigned without a release key, as F-Droid builds it)
	$(GW) assembleRelease

.PHONY: release-apk
release-apk: _check-sdk $(GW) ## Build the signed release APK into dist/ and check its signing certificate
	@[ -f keystore.properties ] || [ -n "$$PBILL_KEYSTORE_FILE" ] || { \
	  echo "✗ No release key: create keystore.properties (see keystore.properties.example)"; exit 1; }
	$(GW) assembleRelease
	@apksigner=$$(ls -d "$$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1); \
	expected=$$(echo "$(RELEASE_CERT_SHA256)" | tr -d ':' | tr 'A-F' 'a-f'); \
	actual=$$("$$apksigner" verify --print-certs $(APK_REL) | sed -n 's/^Signer #1 certificate SHA-256 digest: //p'); \
	[ "$$actual" = "$$expected" ] || { \
	  echo "✗ $(APK_REL) is not signed with the release key (SHA-256: $${actual:-none})"; exit 1; }
	@version=$(APP_VERSION); \
	if ! git describe --exact-match --tags HEAD 2>/dev/null | grep -qx "v$$version" \
	   || ! git diff --quiet || ! git diff --cached --quiet; then version="$$version-dev"; fi; \
	mkdir -p $(DIST_DIR) && cp $(APK_REL) $(DIST_DIR)/pbill-$$version.apk && \
	echo "✓ $(DIST_DIR)/pbill-$$version.apk (signed with the release key)"

.PHONY: bundle
bundle: _check-sdk $(GW) ## Build the release Android App Bundle (.aab) for Google Play
	$(GW) bundleRelease
	@echo "✓ $(AAB_REL)"

.PHONY: debug
debug: _check-sdk $(GW) ## Build the debug APK
	$(GW) assembleDebug

.PHONY: clean
clean: _check-sdk $(GW) ## Remove build artifacts
	$(GW) clean

.PHONY: rebuild
rebuild: clean build ## Clean then build release

.PHONY: rebuild-debug
rebuild-debug: clean debug ## Clean then build debug

# ── Assets ───────────────────────────────────────────────────────────────────

## Assets

.PHONY: icon
icon: ## Regenerate the launcher icon layers from art/icon.svg
	python3 tools/make_icon.py

.PHONY: icon-preview
icon-preview: ## Render the launcher icon under common masks into build/icon/previews.png (Inkscape)
	python3 tools/make_icon.py --preview build/icon/preview
	@for f in full circle rounded square monochrome; do \
	  inkscape build/icon/preview/$$f.svg --export-type=png \
	    --export-filename=$(CURDIR)/build/icon/preview/$$f.png > /dev/null 2>&1; \
	done
	@cd build/icon/preview && convert full.png circle.png rounded.png square.png monochrome.png +append ../previews.png
	@echo "✓ build/icon/previews.png"

.PHONY: store-icon
store-icon: ## Render the 512 px store listing icon into fastlane/ (Inkscape)
	python3 tools/make_icon.py --preview build/icon/preview
	@mkdir -p $(FASTLANE_DIR)/en-US/images
	@inkscape build/icon/preview/store.svg --export-type=png \
	  --export-filename=$(CURDIR)/$(FASTLANE_DIR)/en-US/images/icon.png > /dev/null 2>&1
	@echo "✓ $(FASTLANE_DIR)/en-US/images/icon.png"

# ── Code quality ─────────────────────────────────────────────────────────────

## Code quality

.PHONY: lint
lint: _check-sdk $(GW) ## Run Android Lint
	$(GW) lint
	@echo "Report: app/build/reports/lint-results-debug.html"

.PHONY: test
test: _check-sdk $(GW) ## Run the unit tests
	$(GW) test
	@echo "Report: app/build/reports/tests/testDebugUnitTest/index.html"

.PHONY: hooks
hooks: ## Run all pre-commit hooks on every file
	pre-commit run --all-files

# ── Device management (ADB) ──────────────────────────────────────────────────

## Device management (ADB)

.PHONY: devices
devices: ## List connected ADB devices
	$(ADB) devices -l

.PHONY: install
install: _check-device release-apk ## Build and install the signed release APK on the device (app language: LANG=xx_YY)
	$(ADB) install -r $(APK_REL)
	$(if $(_LANG_SET),@$(MAKE) --no-print-directory lang)

.PHONY: install-debug
install-debug: _check-device debug ## Build and install the debug APK on the device (app language: LANG=xx_YY)
	$(ADB) install -r $(APK_DBG)
	$(if $(_LANG_SET),@$(MAKE) --no-print-directory lang)

.PHONY: lang
lang: _check-device ## Set the app language on the device (LANG=xx_YY; LANG=system: device language)
	$(ADB) shell cmd locale set-app-locales $(APP_ID) --locales '$(APP_LOCALE)'
	@echo "✓ $(APP_ID) language: $(or $(APP_LOCALE),device language)"

.PHONY: run
run: install ## Build, install and launch the app in release mode
	$(ADB) shell am start -n $(ACTIVITY)

.PHONY: run-debug
run-debug: install-debug ## Build, install and launch the app in debug mode
	$(ADB) shell am start -n $(ACTIVITY)

.PHONY: uninstall
uninstall: _check-device ## Uninstall the app from the device
	$(ADB) uninstall $(APP_ID)

.PHONY: stop
stop: _check-device ## Force-stop the app
	$(ADB) shell am force-stop $(APP_ID)

.PHONY: restart
restart: _check-device stop ## Restart the app
	$(ADB) shell am start -n $(ACTIVITY)

# ── Logs ─────────────────────────────────────────────────────────────────────

## Logs

.PHONY: log
log: _check-device ## Show live app logs (Ctrl-C to quit)
	$(ADB) logcat --pid=$$($(ADB) shell pidof -s $(APP_ID)) -v color

.PHONY: log-clear
log-clear: _check-device ## Clear the logcat buffer
	$(ADB) logcat -c

# ── App data ─────────────────────────────────────────────────────────────────

## App data

.PHONY: clear-data
clear-data: stop ## Erase all app data
	@read -p "Erase all app data? [y/N] " ans; \
	[ "$$ans" = "y" ] && $(ADB) shell pm clear $(APP_ID) && echo "Data erased." || echo "Cancelled."

.PHONY: permissions
permissions: _check-device ## Show the app permission state on the device
	$(ADB) shell dumpsys package $(APP_ID) | grep -A2 "permission\."

# ── Information ──────────────────────────────────────────────────────────────

## Information

.PHONY: info
info: _check-device ## Show information about the installed app (version, install dates)
	@$(ADB) shell dumpsys package $(APP_ID) 2>/dev/null \
	    | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime" \
	    || echo "  (not installed on the device)"

.PHONY: apk-size-debug
apk-size-debug: $(APK_DBG) ## Show the debug APK size
	@du -h $(APK_DBG) | cut -f1 | xargs -I{} echo "APK debug: {}"
