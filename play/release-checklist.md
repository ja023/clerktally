# CrewTally — Release Checklist (remaining human steps)

The app is code-complete, signed, and builds a release bundle green. Everything below needs a
human (Jad) because it requires a Google account, a real device, or a business decision. The
agent cannot and must not do these.

## 1. Google Play Console account

- [ ] Create or use a Google Play Console developer account (one-time 25 USD registration fee).
- [ ] Create a new app: name "CrewTally", default language English, type App, free.
- [ ] Confirm the package name is `com.vague.crewtally`. This is LOCKED and can never change
      after the first upload.

## 2. The upload artifact is an AAB, not an APK

Google Play requires an Android App Bundle (.aab), not an APK, for new apps.

- [ ] Build the release bundle:
      `JAVA_HOME=/home/jad/android-dev/jdk17 ./gradlew bundleRelease`
- [ ] The signed bundle is written to `app/build/outputs/bundle/release/app-release.aab`.
- [ ] Upload that .aab on a Play Console release track (start with Internal testing, then
      promote to Production when happy).

Note: the release keystore that signs this is at `/home/jad/.crewtally-release/`. See that
folder's README.txt. Losing it means never being able to update the listing again, so back it
up before publishing. (Enrolling in Play App Signing at upload time is recommended; it lets
Google hold the app signing key while you keep the upload key.)

## 3. Store listing assets still needed (must be produced on a real device or by design)

The listing copy is drafted in `play/listing.md`. These visual assets still have to be made:

- [ ] **App icon 512x512 PNG** (32-bit, with alpha). Export from the adaptive icon design
      (deep blue background, white tally-of-five mark). This is the store icon; it is separate
      from the in-app adaptive icon, which is already built.
- [ ] **Feature graphic 1024x500 PNG or JPG**. Required for the listing.
- [ ] **Phone screenshots**, at least 2 (up to 8). Capture on a real device or emulator:
      suggested shots are the Home dashboard, a project's attendance day, a clerk balance
      screen, and the reports/backup area.
- [ ] (Optional) 7-inch and 10-inch tablet screenshots if targeting tablets.

## 4. Data safety form

- [ ] Transcribe the answers from `play/data-safety.md` into the Play Console Data safety
      section. Summary: no data collected, no data shared, no ephemeral processing, contacts
      permission is optional and on-device only.

## 5. Content rating questionnaire

- [ ] Complete the IARC content rating questionnaire. CrewTally has no user-generated public
      content, no ads, no violence, no data collection. It should rate for Everyone. Answer
      honestly; the questionnaire drives the rating automatically.

## 6. Privacy policy URL

- [ ] Host the text in `play/privacy-policy.md` at a public URL and paste that URL into the
      Play Console app content section. Add a real contact email and an effective date first.

## 7. Pricing and distribution

- [ ] Set the app to **Free**. (Locked decision: free; no in-app purchases.) Pricing is set per
      country; the LBP/USD question is moot for a free app, but confirm the app is available in
      Lebanon and any other target countries.
- [ ] Select target countries for distribution.
- [ ] Complete the remaining required Play declarations: target audience and content, ads
      declaration (declare NO ads), government-app and financial-features declarations (none
      apply), and the app access section (no login required, so "All functionality is available
      without special access").

## 8. Final pre-submit sanity

- [ ] Install the release build on Jad's phone and smoke-test the core loop once more.
- [ ] Confirm versionName 1.0.0 / versionCode 1 in the uploaded bundle.
- [ ] Submit for review.

## Build reference

- Debug APK: `JAVA_HOME=/home/jad/android-dev/jdk17 ./gradlew assembleDebug`
- Release APK (sideload/testing): `JAVA_HOME=/home/jad/android-dev/jdk17 ./gradlew assembleRelease`
- Release AAB (Play upload): `JAVA_HOME=/home/jad/android-dev/jdk17 ./gradlew bundleRelease`
- Unit tests: `JAVA_HOME=/home/jad/android-dev/jdk17 ./gradlew testDebugUnitTest`
