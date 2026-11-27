# Start here (VPS)

Everything runs on one VPS with Docker: Caddy (HTTPS) -> app -> PostgreSQL.
The database is created and configured by Docker; the tables are created by the app at start-up.

## 0. Test on your own computer first (optional)

Needs only Docker Desktop (Windows or Mac). No Java, Maven or PostgreSQL to install.

```bash
cd deploy
docker compose -f docker-compose.local.yml up -d --build    # first build takes a few minutes
docker compose -f docker-compose.local.yml logs -f app       # wait for "Started ArrumoApplication", then Ctrl+C
```

Open http://localhost:8080 and sign in with `admin` and the `APP_ADMIN_PASSWORD` from `deploy/.env`.
The local test ignores `DOMAIN` and uses plain HTTP.

- Stop: `docker compose -f docker-compose.local.yml down`
- Stop and wipe the test data: `docker compose -f docker-compose.local.yml down -v`

When it works, move on to the VPS. The local data does not go with it.

## 1. Before you start

- A VPS with Ubuntu 24.04, 2 vCPU, 4 GB RAM.
- A domain (or subdomain) with a DNS **A record** pointing at the VPS IP.
- Open `deploy/.env` and set `DOMAIN` to that domain. Everything else is already filled in.

## 2. Prepare the server (once, as root)

```bash
apt update && apt -y upgrade && apt -y install ufw fail2ban unattended-upgrades unzip
ufw default deny incoming && ufw allow OpenSSH && ufw allow 80,443/tcp && ufw allow 443/udp && ufw enable
curl -fsSL https://get.docker.com | sh
```

## 3. Copy the project and start it

From your PC:

```bash
scp arrumo.zip root@<VPS-IP>:/opt/
```

On the VPS:

```bash
cd /opt && unzip arrumo.zip && cd arrumo/deploy
chmod 600 .env
docker compose up -d --build        # first build takes a few minutes
docker compose logs -f app          # wait for "Initial admin 'admin' created", then Ctrl+C
```

## 4. First login

1. Open `https://<your-domain>`.
2. Sign in with `admin` and the `APP_ADMIN_PASSWORD` from `deploy/.env`.
3. Choose a new password and set up 2FA.
4. On the VPS, delete the `APP_ADMIN_PASSWORD` line from `deploy/.env`.
5. In "Embalamentos" > "Configurar envios", paste the two Power Automate URLs.
6. In "Lotes" > "Importar stock", load the ERP stock.

## Useful commands (in /opt/arrumo/deploy)

| What | Command |
|---|---|
| Status | `docker compose ps` |
| App log | `docker compose logs -f app` |
| Restart | `docker compose restart app` |
| Update after changing code | `docker compose up -d --build` |
| Stop | `docker compose down` (data is kept) |

## Automatic deploy (GitHub Actions)

Every push to `main` runs the tests and, if they pass, copies the code to `/opt/arrumo` on the server
and runs `docker compose up -d --build` (see `.github/workflows/ci.yml`). `deploy/.env` and
`deploy/.backup.env` stay on the server and are never overwritten.
Repository secrets needed: `LIGHTSAIL_HOST`, `LIGHTSAIL_USER`, `LIGHTSAIL_SSH_KEY`.

Backups, Railway and everything else: [docs/MANUAL.md](docs/MANUAL.md).

`deploy/.env` holds real secrets: it is in `.gitignore`, never commit it.
