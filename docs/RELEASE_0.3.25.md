# Veilark Windows 0.3.25

Status: candidate; not published. Code: 325.

## Changes

- Separate Fairway connection button, matching the Android engine choice.
- Veilark Core uses the shared Fairway dispatcher and the exact Android RU IP
  policy. The RU list is not installed as thousands of Windows routes.
- A managed subscription can issue an independent key for each installation.
  Windows keeps its private key locally under DPAPI; only its public key is sent.
  Existing Android credentials remain unchanged.
- Android compact subscription links are recognized by the Windows importer.
- Core-only subscription refresh preserves existing TrustTunnel and sing-box
  profiles. Pending enrollment can be retried without switching to a legacy import.

## Remaining publication steps

Deploy the matched server and enrollment runtime, verify a real managed claim and
effective daemon receipt, then set the immutable native pin and assemble the final
installer. Verify packaged resources, installer identity and signed OTA metadata.
Do not publish the earlier image built with Fairway disabled.

The owner will test full Windows TUN forwarding after publication; it has not been
tested on this workstation. Linux isolated TUN/driver checks are separate evidence.

Existing static Android Core links retain their old access. For simultaneous
Windows access, use the canonical managed subscription to obtain a distinct key;
copying a static private key across devices does not provide independent sessions.
