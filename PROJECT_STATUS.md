# LT Hisab Project Status — v1.1.0

## Fixed Build & Runtime Issues (from v1.0.0 Audit)
1. **AAPT2 Logo Fix**: `sarotech_logo.png` contained JPEG bytes (`JFIF`) under a `.png` extension, which fails AAPT2 compilation. Renamed to `sarotech_logo.jpg` preserving exact original bytes and colors.
2. **AAPT2 Resource Directory Fix**: Removed invalid `res/values-vaghi/` qualifier directory that broke Android builds. Replaced with a runtime Compose localization engine (`AppStrings.kt`) supporting English, Hindi, and Kherwara-local Vagdi in Devanagari across the entire app and PDF statements.
3. **Compose Compiler Fix**: Removed illegal `@Composable` `LocalThemeToggle.current` invocation inside an `onClick` lambda in `MainActivity.kt`.
4. **Biometric Host Fix**: Switched `MainActivity` from `ComponentActivity` to `FragmentActivity` (`androidx.fragment:fragment-ktx`) required by `BiometricPrompt`.
5. **Layout & Precision Fixes**: Fixed bottom bar clipping (`Modifier.weight(1f)`), added horizontal scroll for period chips, fixed `weekStart` midnight boundary bug, replaced floating-point `(amount * 100).toLong()` with exact `BigDecimal` paise parsing, and added Room `ksp` schema location configuration.

## Implemented in v1.1.0
- Runtime language selector (English / Hindi / Vagdi) applied to UI & PDFs.
- Persistent DataStore settings (theme, language, profile name/address, opening balance, currency symbol, Indian number format, date/time format, first day of week, default period, running balance toggle, row time toggle, notes suggestions toggle, keep-screen-on).
- Edit transaction, custom date/time pickers, recent notes suggestions, search, sort, custom date range, confirmed delete.
- Opening balance & period carry-forward math.
- 4-digit PIN lock, Change PIN, security-question recovery, and Biometric unlock.
- Versioned JSON backup/restore (v2 + v1 compatibility) with strict validation and confirmation dialog.
- Multipage Devanagari-safe PDF statement with logos, repeated headers, page numbers, PrintManager, and Email chooser.
- Expanded JVM unit tests (`BalanceCalculatorTest`, `BackupManagerTest`, `LocalizationTest`).

## Verification Status
This container does not include Android SDK / Gradle; push to GitHub Actions (`.github/workflows/android.yml`) to execute `gradle testDebugUnitTest` and `gradle assembleDebug` and verify on an API 26+ Android device.
