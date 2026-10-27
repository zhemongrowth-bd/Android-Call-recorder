# Winstone Pulse Android — device test checklist

Run this on **one test phone** before agents install a new build. Use a test lead and a
second phone you control. Never call a real customer during testing.

Tester: ________  Phone model / Android version: ________  App version: ________  Date: ________

## 0. Install and setup
- [ ] Install **Winstone Pulse TEST** (test build) — it sits beside the agent app, nothing is removed.
- [ ] Sign in with a staff test account; the 9:05 briefing acknowledgement appears and must be accepted before any work.
- [ ] Grant phone, call log, microphone and notification permissions when asked. Denying one shows a clear message, not a crash.
- [ ] Phone's own call recorder is switched ON (Settings → Phone → Call recording).

## 1. Outgoing call
- [ ] From a test lead, tap Call. The phone dialer opens with the right number.
- [ ] Talk ≥ 30 seconds, hang up.
- [ ] Post-call report opens automatically and cannot be skipped.
- [ ] CRM call log shows: direction **outgoing**, status **completed**, duration within ±5 s of the phone's call history.
- [ ] Call of 0 seconds (hang up before answer) shows **no answer**, not completed.

## 2. Incoming call
- [ ] Call the test phone from the second phone, answer, talk ≥ 30 s.
- [ ] CRM shows direction **incoming**, status **completed**, correct number and duration.
- [ ] An unknown number does **not** get assigned to anyone — it enters as an unassigned lead for LeadHub.

## 3. Missed / rejected calls
- [ ] Let an incoming call ring out → CRM shows **missed / no answer**, duration 0.
- [ ] Reject an incoming call → shows **no answer**, never completed.
- [ ] If available: send one to voicemail and block a number → neither shows as connected.

## 4. Recording upload
- [ ] After the outgoing call, Desk → **Phone recordings** lists the number with **Pending Sync → Syncing → Synced**.
- [ ] "Synced" appears only after the CRM has the audio; open the call in the CRM and play it.
- [ ] No recording on the phone for a call → nothing uploaded, no fake "Synced".

## 5. No internet
- [ ] Turn on airplane mode, make/receive a call (use Wi‑Fi calling off), end it.
- [ ] Status shows **Pending Sync**; nothing is lost.
- [ ] Restart the phone while still offline, then reconnect → upload completes and shows **Synced**.
- [ ] Same recording appears **once** in the CRM (no duplicate).

## 6. Failed upload and retry
- [ ] Force a failure (e.g. delete the recording file from the phone's recorder folder before reconnecting).
- [ ] Status shows **Failed** in red with the retry button.
- [ ] Tap **Retry failed uploads**: if the file is gone, the message says so; if present, it re-sends and becomes **Synced**.

## 7. Release sign-off
- [ ] Every box above ticked, no crash, no wrong lead owner.
- [ ] Install **Winstone Pulse** (release build) on one agent phone and repeat sections 1 and 4.
- [ ] Only then share the release build with all agents.

Problems found: ______________________________________________
