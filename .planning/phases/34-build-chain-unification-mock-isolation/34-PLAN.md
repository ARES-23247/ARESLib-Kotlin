# Phase 34: Build Chain Unification & Mock Isolation - Plan

## 1. Create `ftc-mocks` Module
- Create directory `ftc-mocks/src/main/kotlin`.
- Create `ftc-mocks/build.gradle.kts` using `kotlin("jvm")` and add it to `settings.gradle.kts`.

## 2. Relocate Mock Stubs
- Move the following packages from `core/src/main/kotlin` to `ftc-mocks/src/main/kotlin`:
  - `com.qualcomm.robotcore.hardware`
  - `com.qualcomm.robotcore.eventloop`
  - `com.qualcomm.hardware`

## 3. Configure Dependencies
- In `core/build.gradle.kts` and `ftc-hardware/build.gradle.kts`:
  - Add `compileOnly(project(":ftc-mocks"))` to compile against the stubs.
  - Add `testImplementation(project(":ftc-mocks"))` for unit tests.
- In `simulator/build.gradle.kts`:
  - Add `implementation(project(":ftc-mocks"))` so the simulator can run using the mock implementations.
- In `ftc-app/TeamCode/build.gradle`:
  - Ensure it only depends on `:core` and `:ftc-hardware` (which no longer leak the mock SDK).

## 4. Compile and Verify
- Run `./gradlew :core:test` to ensure pure logic tests pass.
- Run `./gradlew :TeamCode:installDebug` (or `assembleDebug`) to verify Android compilation succeeds without duplicate class errors.
