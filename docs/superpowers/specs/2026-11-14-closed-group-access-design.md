# Closed-environment group access — design (replaces code pairing)

Date: 2026-11-14 · Branch `adddevice` · **Supersedes** the pairing flow of `2026-11-13-pairing-design.md`: codes, requests and Allow/Deny. Its SOLID principles, the `DocumentStore` seam and the "host cannot remove a supervisor" rule still apply.

## Intent
LiveCast runs in a **closed environment**: a family or a known team, not the public. The user decided (2026-11-14) to drop per-supervisor code pairing. Clients instead **list the online hosts they are allowed to see**. Access control moves from "pair each device" to **membership in a group** (option B), so a stranger who installs the client still sees nothing.

## Decided by the user
- Remove code pairing; closed environment.
- Claude's calls 1–3 approved (2026-11-14): verified-email membership is the security hinge; the host phone uses its own `device` account; removing `device` membership does not unenroll (only the owner's "Remove from group" does).
- Option B: group allowlist, plus a one-time enrollment of each host phone, done on the phone.
- Earlier decisions still stand: child monitoring only; the host can never remove its supervision; web = React, iOS = `:client` with the Swift `DocumentStore` bridge.

## Goals / non-goals / anti-goals
- **Goals:**
  - A group owner manages members by email.
  - A host phone enrolls into its group once, visibly.
  - Supervisors see their group's devices with online/offline status, live.
  - The host shows "Supervised by …" with the group's supervisors.
  - Firestore rules enforce all of it.
- **Non-goals (later slices):**
  - Waking an offline host lands with slice 2, the host agent: a Firestore command channel while the host runs, plus FCM high-priority data messages sent from **the user's own server** (decided 2026-11-14: no Firebase Blaze / Cloud Functions).
  - Screen cast, and the persistent supervision indicator (slice 2).
  - Multiple groups in one picker UI. The data model allows several; the UI shows all devices across the user's groups.
- **Anti-goals:**
  - Any rule that lets a signed-in non-member read devices.
  - Unverified emails granting access.
  - The host phone removing itself or its supervisors.

## Design

### Identity and membership *(Claude's calls, flagged)*
- **Members are keyed by lowercase email:** `groups/{groupId}/members/{email}`. Rules match `request.auth.token.email` **and require `request.auth.token.email_verified == true`**. Google sign-in is verified automatically. Email/password accounts must verify through Firebase's verification link, so clients add a "Verify your email" step. *(This is the security hinge: without it, anyone could sign up with a member's address.)*
- **Roles:**
  - `owner`: manages members and devices.
  - `supervisor`: sees devices.
  - `device`: an account a host phone signs in with.
- The host phone uses **its own account** (listed as `device`), not a parent's. *(Flag: the alternative, signing the phone in with a parent account, puts parent credentials on the child's phone.)*
- Guests (anonymous accounts) have no email, so they never get access.

### Firestore data model
```
groups/{groupId}                     name, ownerUid, createdAt
groups/{groupId}/members/{email}     email, role: owner|supervisor|device, name?, addedAt
devices/{deviceId}                   groupId, hostUid, displayName, model, online, lastSeen, enrolledAt
```
`deviceId` = the host account's uid, as before. **Online** = `online == true && lastSeen` within the last 2 minutes, computed on the client, because a crash can leave `online=true` behind. The host keeps a heartbeat going while the app runs; the existing heartbeat moves from `users/{uid}` to `devices/{uid}`.

### Flows
1. **Create a group (client):**
   - A verified user with no group taps "Create group".
   - One batch writes `groups/{id}` (`ownerUid` = me) and `members/{myEmail}` (role owner).
   - The rules check the owner member doc with `getAfter`.
2. **Manage members (client, owner only):** add an email with a role, change a role, remove a member. The owner can't remove themselves while they are the only owner.
3. **Enroll a host (phone, once):**
   - After sign-in, the host looks up groups where its email is a `device` member, using a collection-group query on `members` filtered by email.
   - If there is exactly one, it shows *"This phone will be supervised by {group name}: {supervisor names}. Only a group owner can remove supervision."* with **[Continue]**, which writes `devices/{uid}` with `groupId` and `enrolledAt`.
   - If there is none, it shows *"Ask your group owner to add {email} as a device."*
   - If there are several, a picker comes first, then the same screen.
4. **My devices (client):** a live list of devices in my groups, with an online/offline badge and last-seen time. Tapping one opens the viewer placeholder. The owner also gets "Remove from group", which clears `groupId`, so the host returns to the not-enrolled state.
5. **Supervised by (host):** a live list of the group's `owner` and `supervisor` members, shown on `StageScreen` (replacing today's supervisors subcollection).

### Rules (sketch; the builder writes them in full with tests)
- `isVerified()`: `email_verified == true`.
- `memberRole(g)`: `get(/groups/g/members/<email>).data.role`.

| Path | Rules |
|---|---|
| `groups/{g}` | read: members; create: verified user who becomes owner (batch with the owner member doc via `getAfter`); update/delete: owner. |
| `groups/{g}/members/{email}` | read: members of `g`, plus the doc's own email (enables the collection-group lookup); write: owner of `g`. |
| collection group `/{path=**}/members/{email}` | read when `resource.data.email == token.email`. |
| `devices/{d}` | read: `owner`/`supervisor` members of `resource.data.groupId`, or the host itself (`auth.uid == d`); create/enroll: `auth.uid == d`, verified, and its email is a `device` member of the target `groupId`; host heartbeat updates: only `online`, `lastSeen`, `displayName`, `model`; owner may set `groupId` to null (remove); the host can never change or clear `groupId` after enrolling. |
| Legacy `pairingCodes`, `pairingRequests`, `devices/*/supervisors` | Removed. |

### Code impact
- **Removed:**
  - `:protocol` `PairingCode`/`PairingRequest`/`PairingStatus`/`PairingRules` and the request mappers.
  - Host: `PairingCodeIssuer`, `PairingRequestInbox`, `PairSupervisorScreen`, the code/approve parts of `HostPairingViewModel`, `PairingCodeGenerator`.
  - Client: `PairingRequestRepository`, `AddDeviceViewModel`/`AddDeviceScreen`, the request parts of `PairingRequestPolicy`.
  - The rules blocks and tests for codes and requests.
- **Reshaped (same SOLID seams):**
  - `SupervisorDirectory` → `GroupMembersDirectory` (host, "Supervised by").
  - `SupervisedDeviceRepository` → `GroupDeviceRepository` (client: observe devices + presence, owner removal).
  - `DeviceRegistry` gains `enroll(groupId)` and the heartbeat.
  - New `GroupRepository` (create group, observe my groups, manage members) with its view models and screens.
  - Android implementations use the Firebase SDK; desktop and iOS implementations use `DocumentStore`. `DocumentStore` needs a collection-group query with an equality filter (already supported) and batched writes (already supported).
- **Unchanged:** `:auth` (plus an email-verification call: `sendEmailVerification` and a reload of `email_verified`), `:firestore-grpc`, `DocumentStore`, Koin platform modules, `normalizeTimestamps`, the rules-test harness.
- **iOS + web design:** the parity target becomes My devices + Group management + Verify email, instead of Add device by code.

### Edge cases
- The owner removes a supervisor who has the device list open: the listener gets PERMISSION_DENIED, which shows as "You no longer have access" (no crash; R1).
- A host's account is removed from `device` membership after enrolling: the device stays enrolled until the owner removes it. Membership only gates enrollment *(Claude's call)*.
- Email changes on an account: access follows the verified email.
- A stale `online=true` after a crash: covered by the 2-minute last-seen window.

## Verification
- Rules emulator tests:
  - non-member reads → denied
  - unverified email → denied
  - supervisor lists group devices → allowed
  - host enrolls only with a `device` membership
  - host can't clear `groupId`
  - owner removes a device
  - non-owner member writes → denied
  - collection-group self-lookup
- Unit tests for the new view models (fakes).
- All existing gates.
- Manual: owner on the desktop client creates a group and adds the host's email as `device` and a second account as `supervisor`; the host on the emulator enrolls; both clients see it online; the owner removes it.

## Left to the builder
- Screen layouts.
- The heartbeat interval (≤ 60 s).
- Member-management UI details.
- Exact rule helper structure.
