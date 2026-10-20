# Moving Dateroem to a hosted server

Everything needed to run Dateroem on one rented Ubuntu 24.04 server with the company's own domain. The full guide, with where to host, the domain, Safaricom's Go Live and every value to change, is the Word document **Hosting and Deployment Guide** (documentation/02 Configuration and Production).

| File | What it is |
|---|---|
| `Caddyfile` | The public router: HTTPS for the domain, the website, `/mobile-api/` (phone app), `/admin-api` (desktop app), the payment callbacks |
| `systemd/*.service` | Starts the backend and the three small servers on boot and restarts them if they stop |
| `env/*.env.example` | The settings files; `setup-server.sh` turns them into `/etc/dateroem/*.env` |
| `scripts/make-release.sh` | **This computer**: builds and packs a release (`--with-media` the first time) |
| `scripts/export-database.sh` | **This computer**: exports the database once, for the move |
| `scripts/setup-server.sh` | **Server, once**: packages, firewall, account, database, settings files |
| `scripts/import-database.sh` | **Server, once**: loads the exported database |
| `scripts/install-release.sh` | **Server, every release**: installs, restarts, checks |
| `scripts/check-server.sh` | **Server, any time**: health, empty settings, certificate, backups, disk |
| `scripts/backup.sh` | **Server, daily at 02:30**: database and pictures into `/srv/dateroem/backups` |

## The move in short

1. Rent the server and buy the domain; point the domain (and `www`) at the server's IP address.
2. On this computer: `bash deploy/scripts/make-release.sh --with-media` and `bash deploy/scripts/export-database.sh`; copy both files from `deploy/out/` to the server.
3. On the server: unpack the release (`tar xzf dateroem-release-*.tar.gz`), then
   `sudo bash dateroem/deploy/scripts/setup-server.sh <domain> <email>`,
   `sudo bash dateroem/deploy/scripts/import-database.sh dateroem-db-*.sql.gz`.
4. Fill in the values only you can get: `sudo nano /etc/dateroem/backend.env` (M-Pesa, AI keys) and `/etc/dateroem/website-server.env` (Gmail App Password).
5. `sudo bash dateroem/deploy/scripts/install-release.sh dateroem-release-*.tar.gz`; it ends with the health check.
6. Point the apps at the new address: the desktop app's **Change** on the sign-in screen (`https://<domain>/admin-api`) and a new phone app build (`-PmobileBaseUrl=https://<domain>/mobile-api/`).

Updates later: step 2 without `--with-media` and without the database, then step 5.

`out/` (releases and database exports) is never committed: the exports hold customer data.
