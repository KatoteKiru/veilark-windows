# Veilark Windows 0.3.14

This maintenance release updates the packaged runtimes without changing the
routing model or subscription format:

- sing-box 1.13.19 to stable 1.13.21;
- TrustTunnel 1.1.5-rc.6 to stable 1.1.5.

The Gradle test suite, clean Windows distributable build and packaged-runtime
verification must pass before OTA publication. A real elevated TUN session and
upgrade-preservation check remain physical-device acceptance gates.
