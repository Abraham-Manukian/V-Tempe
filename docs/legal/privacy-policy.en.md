# V-Tempe Personal Data Processing Policy

Revision of [DATE]. Document version: 2026-10-08.

> DRAFT FOR A LAWYER. Fields in square brackets are filled in by the owner. The Russian version
> (`privacy-policy.md`) prevails. Data flows match the code as of 2026-10-08
> (`docs/legal/DATA_INVENTORY.md`).

## 1. General

1.1. This Policy describes how personal data of users of the V-Tempe mobile app (the "App") is
processed and protected. It is issued under Art. 18.1(2) of Russian Federal Law No. 152-FZ
of 27.07.2006 "On Personal Data" (the "Law") and is published openly.

1.2. Data operator:

- name: [OPERATOR NAME];
- tax ID / registration number: [INN/OGRN];
- address: [ADDRESS];
- e-mail for personal data requests: [CONTACT E-MAIL];
- person responsible for personal data processing: [RESPONSIBLE PERSON].

1.3. Health data is a special category of personal data (Art. 10 of the Law).

1.4. The App is intended for persons aged 16 and over (section 11).

## 2. Data we process

| Category | Contents | Source |
|----------|----------|--------|
| Profile | age, sex, height, weight, goal, experience level, equipment, workout schedule, daily activity level, food budget, training focus and session length, chosen coach | you, during setup and in Settings |
| **Health data (special category)** | injuries and limitations, health notes and contraindications, allergies (free text); sleep and workout notes and chat messages if you mention health in them | you |
| Food preferences | dietary preferences and restrictions | you |
| Logs | completed sets, weights, reps, perceived effort (RPE), workout notes; sleep (dates, duration, notes); weight history | you |
| AI coach conversation | your messages and the coach's replies | you |
| Account | Firebase user ID (UID), e-mail, Google profile name and photo | Google sign-in |
| Subscription (once paid features launch) | subscription expiry, payment source, amount and currency, provider payment ID | Google Play, YooKassa |
| Technical data | IP address, User-Agent, request time and parameters | automatically, on each server request |
| App diagnostics and analytics | installation ID, device model, OS and App version, crash reports; with your analytics consent — usage events and grouped profile attributes (age, height and weight ranges, sex, goal, budget, focus, chosen coach), country by IP | Firebase SDK |
| Settings | language, units, AI model mode, your consent decisions | the App |

We do **not** collect location, contacts, photos or camera, advertising ID, or full card data.

## 3. Purposes and legal bases

| Purpose | Data | Legal basis |
|---------|------|-------------|
| Personal workout, nutrition and sleep plans and AI coach chat replies | profile, health data, food preferences, logs, conversation | separate consent to health data processing and cross-border transfer (Art. 9, 10(2)(1), 12 of the Law); for other data — performance of the Terms of Use (Art. 6(1)(5)) |
| Storing data on the device and offline features | everything you enter | performance of the Terms of Use |
| Account and server backup for restoring on another device (only if you sign in with Google) | account, profile, health data, logs | performance of the Terms of Use; for health data — consent |
| Paid features (once launched) | account, subscription data | contract performance; accounting and tax obligations (Art. 6(1)(2)) |
| Usage analytics to improve the App | analytics data | consent (Art. 6(1)(1)), separate and off by default |
| Crash diagnostics | crash reports, device data | the operator's legitimate interest in a working App (Art. 6(1)(7)) [LAWYER: confirm or move under consent] |
| Abuse protection | IP address, User-Agent | legitimate interest (Art. 6(1)(7)) |
| Answering user requests | e-mail and request contents | consent given by sending the request; obligations under Art. 14, 20 |

We make no decisions with legal effect based solely on automated processing.

## 4. Processing operations

Collection, recording, systematisation, accumulation, storage, updating, retrieval, use,
transfer (including cross-border), pseudonymisation, blocking, deletion, destruction — by
automated means.

## 5. Processors and recipients

| Party | Country | What it receives | Why |
|-------|---------|------------------|-----|
| Google LLC / Google Cloud (Cloud Run, Cloud Logging, database) | servers in `europe-west1` (Belgium, EU) | everything the App sends to the V-Tempe server: profile and health data in AI requests and in the backup, logs, UID, subscription data, IP and User-Agent | server and database hosting |
| OpenRouter, Inc. | USA | on every AI request: profile, health data, up to 6 recent workouts, up to 7 sleep entries with notes, up to 8 weight entries; in chat — up to 8 recent messages plus your new message, current plans | routing to the AI model |
| AI model developers OpenRouter routes to, including Anthropic PBC [CURRENT MODEL PROVIDER LIST] | USA [verify] | same as OpenRouter | generating the reply |
| Google LLC (Firebase Authentication) | USA | UID, e-mail, Google name and photo | sign-in |
| Google LLC (Firebase Crashlytics) | USA | crash reports, installation ID, device model, OS and App version | diagnostics |
| Google LLC (Firebase Analytics) — only with your consent | USA | usage events, grouped profile attributes, app instance ID, device data, country | analytics |
| Google LLC (Google Play Billing) — once paid features launch | USA | purchase data | payment |
| NCO YooMoney LLC (YooKassa) — once paid features launch | Russia | payment data; UID and subscription term in payment metadata | payment |

We never send your UID, e-mail, name, photo, IP, User-Agent, subscription or payment data to
the AI. E-mail addresses, phone numbers and @-handles are removed from free text automatically;
names written in free text are not detected — do not put unnecessary personal details in notes
or chat.

We do not sell personal data or use it for advertising.

## 6. Cross-border transfer and storage location

6.1. The V-Tempe server and its database are located outside the Russian Federation (Google
Cloud, Belgium). Everything the App sends to the server is stored and processed there, i.e.
transferred cross-border. [LAWYER AND OWNER: decision on Art. 18(5) of the Law — see README.]

6.2. Data needed by the AI coach is transferred to the USA (OpenRouter and model developers);
account, analytics and diagnostics data — to the USA (Google Firebase). The USA is not on the
list of countries ensuring adequate protection of data subjects' rights.

6.3. Health data is transferred abroad only with your separate consent (section 7). Without it
the AI coach is off and no health data is sent to the AI.

6.4. The operator notifies Roskomnadzor of cross-border transfer under Art. 12. [Notice date
and number: ___]

## 7. Consent to health data processing

7.1. Health data is processed only with your separate consent, a standalone document "Consent
to processing of special categories of personal data and their cross-border transfer"
[CONSENT URL]. The consent checkbox in setup is never pre-ticked and setup cannot be finished
without it. Users who started before the consent existed are asked once on the next launch.

7.2. Analytics consent is separate and optional.

7.3. Your decision (given or withdrawn), its date and the consent text version are stored on
your device.

## 8. Retention

| Where | What | Period |
|-------|------|--------|
| Your device | profile, plans, logs, settings; chat history — last 30 messages; sleep — last 14 entries; weight — last 30 entries | until you reset data, sign out (for account data) or uninstall |
| Server backup (only when signed in with Google) | latest profile, workout, sleep and weight snapshots | until account deletion |
| Server subscription and payment records (once paid features launch) | UID, expiry, source, amount, currency, payment ID | [PERIOD: at least 5 years under Art. 29 of Federal Law No. 402-FZ "On Accounting"; LAWYER to confirm] |
| Server in-memory plan cache | generated plans | 30 minutes, cleared on restart |
| Server abuse protection | IP address | about 1 minute |
| Server logs (Google Cloud Logging) | IP, User-Agent, request parameters, technical errors without UID or e-mail | [30 days — Google Cloud default; state actual setting] |
| OpenRouter and model developers | AI request contents | per their policies and the operator's account settings [STATE after checking: prompt logging off / period] |
| Firebase Analytics | analytics data | [2 or 14 months — project setting] |
| Firebase Crashlytics | crash reports | 90 days |

When the purpose is achieved, consent is withdrawn or you request it, data is destroyed within
the periods set by Art. 21 of the Law unless the law requires otherwise.

## 9. Your rights and how to use them

9.1. You may obtain information about processing (Art. 14), demand correction, blocking or
destruction of data, withdraw your health data and/or analytics consent, and complain to
Roskomnadzor or a court.

9.2. How:

| Action | Where |
|--------|-------|
| Edit profile data | Settings → "Edit profile" |
| Withdraw or give health data consent | Settings → "Legal and consents". After withdrawal the AI coach stops sending data immediately |
| Turn analytics on or off | Settings → "Share anonymous usage statistics" |
| Erase all data on the device | Settings → "Reset data". If you are signed in, the server backup of your progress is cleared too |
| Delete the account and server data | Settings → "Delete account". The account and the server backup are deleted; payment records are kept for the period required by accounting law. Instructions: [ACCOUNT DELETION PAGE URL] |
| Any other request | e-mail [CONTACT E-MAIL] from the address linked to the account or with details that identify you |

9.3. We reply within 10 working days; this may be extended by up to 5 working days with
notice of the reason (Art. 20).

9.4. On withdrawal of health data consent the operator stops processing and destroys the data
within 30 days unless the law provides otherwise. To remove server data immediately, delete
your account in the App. [OWNER: see README, "Consent withdrawal and server data".]

## 10. Security measures

- HTTPS (TLS) for all App–server traffic.
- Account data on the server is accessible only with a verified Firebase token; the server
  extracts only the UID and does not store the token.
- No user identifiers are sent to the AI; direct identifiers are removed from free text.
- Raw AI model responses are not recorded by default; response fragments are not logged.
- Crash reports exclude error message texts that could contain your data.
- Android backup is disabled, so App data does not go into device cloud backups.
- Analytics is off by default; the advertising ID is not collected.
- Organisational measures: [responsible person, internal policies, restricted access to Google
  Cloud and Firebase, admin 2FA, threat model, harm assessment — TO FILL IN].

## 11. Children

The App is not intended for persons under 16. We do not knowingly collect children's data. If
you learn that a child has given us data, write to [CONTACT E-MAIL] and we will delete it.

## 12. Changes

We may change this Policy. A new revision takes effect when published at [POLICY URL]. If the
terms of health data processing change, the App asks for consent again.

## 13. Contact

[OPERATOR NAME], [ADDRESS], [CONTACT E-MAIL].
