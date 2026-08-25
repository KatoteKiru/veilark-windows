# Veilark Windows 0.3.11 — PC acceptance

1. In **Settings → Updates**, install 0.3.11 and accept the UAC prompt.
2. Confirm subscriptions, selected endpoints, application routing, domain/CIDR
   rules, and language survived the update.
3. In sing-box mode connect one endpoint from each available location. Verify a
   foreign IP-check page, GitHub, YouTube, ChatGPT, Gemini, and UDP/QUIC traffic.
4. Switch Wi-Fi/Ethernet where available and verify recovery without a stale
   connected state.
5. Disconnect and confirm normal direct DNS and traffic are restored.
6. Smoke-test TrustTunnel separately; its bundled version remains 1.1.5-rc.6.

If another VPN owns Windows routes, disconnect it before judging tunnel traffic.
