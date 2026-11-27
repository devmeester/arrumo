# Arrumo manual

Real-time map of the frozen-goods cold room, used by several people at once.
The backend is Spring Boot 4 (Java 21), the database is PostgreSQL and HTTPS is handled by Caddy, all with Docker on a VPS.

The user interface is in Portuguese (the language of the people who use it), so button and screen names below are quoted as they appear in the app.

```
backend/   Java code + web UI (src/main/resources/static)
deploy/    docker-compose, Caddyfile, .env.example, backup.sh
```

## What it does

- **Map** ("Mapa"): raw-material room, floor 1 (2 slots per position) and floor 2 (3 slots per position). Each position's colour shows the lot's age. **Orange** means the lot was scanned at the terminal but is not in the database yet.
- **Store**: tap a free position and scan the lot, or type the code and press Enter. Any other text (e.g. "Casa do Bacalhau") is kept as a note on the position.
- **Move, release, notes**: when a position is released, the user chooses where the lot goes: shipped ("Saiu da câmara"), corridor ("Corredor") or leftovers ("Sobras"). A finished-goods pallet only leaves its slot when it is shipped; to change its slot, use Move ("Mover").
- **Corridor and leftovers**: lists of lots without a position.
- **Real time**: what one person does shows up on everyone else's screen straight away. If two people change the same position at the same time, the second one gets a warning and the map is refreshed. Nothing is overwritten.
- **Management**: products, lots (create, complete, fix), stock import by pasting from Excel, users and roles.
- **Traceability**: every movement is recorded (who, when, from where, to where). The database refuses to change or delete these records.

Roles: **Consulta** (viewer) only looks. **Operador** (operator) stores, moves and releases. **Gestor** (manager) also manages products, lots and imports. **Administrador** also manages users and sees the access log. **Logística** (logistics) only works on the finished-goods map.

## Security

| Threat | Protection |
|---|---|
| Stolen password | 2FA (6-digit code from an authenticator app), required for admins and managers; can be extended to everyone with `APP_MFA_REQUIRED` |
| Password guessing | 15-minute lockout after 5 failures, per-IP rate limit, passwords of 12+ characters stored with BCrypt |
| Typing the code every time | "Não pedir o código neste computador durante 30 dias": a signed cookie that stops working if the password or 2FA changes |
| Session theft | `HttpOnly`, `Secure`, `SameSite=Strict` cookie; new session id at each login step; 12 h maximum |
| Forged requests (CSRF) | CSRF token on every request that changes data |
| Injected scripts (XSS) | Content-Security-Policy `'self'` only, no external scripts; text is always escaped |
| Disabled account keeps an open session | Every request checks the account: disabling someone or resetting their password ends their sessions at once |
| Stolen database copy | 2FA secrets and webhook URLs encrypted with AES-256-GCM; the key is only in `.env` |
| Intercepted traffic | HTTPS only (automatic certificate), HSTS |
| Direct database access | PostgreSQL has no open port and sits on an internal Docker network with no way out |
| Erasing tracks | `movement` and `audit_entry` only accept INSERT (triggers block UPDATE, DELETE and TRUNCATE) |
| Data loss | Daily encrypted backup off the server (restic) |

The strongest extra measure is **restricting access to the plant's IP**. If the plant has a fixed IP, uncomment the 2 lines in the `Caddyfile`.

## Run it on your PC (development)

You need Java 21, Maven and Docker.

```bash
docker run -d --name arrumo-db -e POSTGRES_DB=arrumo -e POSTGRES_USER=arrumo \
  -e POSTGRES_PASSWORD=arrumo -p 5432:5432 postgres:16-alpine
cd backend
mvn verify                                          # builds and runs the tests
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Then open http://localhost:8080 and sign in with `admin` / `change-me-now-2026`. You will be asked for a new password and to set up 2FA.
The `dev` profile loads anonymised demo data (`db/demo`).

## Configuration

All settings come from environment variables (the `.env` file on the server). See `deploy/.env.example`.

| Variable | What it is |
|---|---|
| `DOMAIN` | Domain pointing at the server (used by Caddy) |
| `DB_PASSWORD` | PostgreSQL password |
| `APP_ENCRYPTION_KEY` | Key that encrypts 2FA secrets and stored webhook URLs (`openssl rand -base64 32`). Keep a copy in a password manager |
| `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` | Admin created on the first start. Delete the password line afterwards |
| `APP_MFA_REQUIRED` | Roles that must use 2FA, e.g. `ADMIN,MANAGER` (roles: `ADMIN`, `MANAGER`, `OPERATOR`, `VIEWER`, `LOGISTICS`) |
| `APP_PACKING_WEBHOOK_DAILY`, `APP_PACKING_WEBHOOK_FINISHED` | Optional Power Automate URLs. Usually pasted in the app instead (see Packing board) |
| `APP_PACKING_CRON` | When the daily list is sent. Default `0 0 17 * * MON-FRI` (Lisbon time) |
| `APP_SECURE_COOKIE` | `true` by default; only set `false` for plain-HTTP local tests |
| `APP_TOTP_ISSUER` | Name shown in the authenticator app (default `Arrumo`) |

## Deploy to a VPS

**1. Server.** A VPS in the EU is enough, with Ubuntu 24.04, 2 vCPU and 4 GB of RAM. Point a domain (A record) at the server's IP.

**2. Harden the server** (as root, once):

```bash
adduser kaina && usermod -aG sudo kaina
mkdir -p /home/kaina/.ssh && cp ~/.ssh/authorized_keys /home/kaina/.ssh/ && chown -R kaina: /home/kaina/.ssh
# /etc/ssh/sshd_config: PasswordAuthentication no, PermitRootLogin no ; then: systemctl restart ssh
apt update && apt -y upgrade && apt -y install ufw fail2ban unattended-upgrades restic
ufw default deny incoming && ufw allow OpenSSH && ufw allow 80,443/tcp && ufw allow 443/udp && ufw enable
dpkg-reconfigure -plow unattended-upgrades
curl -fsSL https://get.docker.com | sh && usermod -aG docker kaina
```

**3. Install the app:**

```bash
sudo mkdir -p /opt/arrumo && sudo chown kaina: /opt/arrumo
git clone <your repository> /opt/arrumo && cd /opt/arrumo/deploy
cp .env.example .env && chmod 600 .env && nano .env   # fill everything in
docker compose up -d --build
docker compose logs -f app    # wait for "Initial admin 'admin' created"
```

Open `https://your-domain` and sign in as admin (change the password and turn on 2FA). Then **delete `APP_ADMIN_PASSWORD` from `.env`**.

**4. Load the data.** In "Lotes", click "Importar stock" and choose the stock type (finished goods, raw material or leftovers). In Excel, select the Lote, Produto, Estoque and Observacoes columns and paste. If you add a `Posicao` column (e.g. `R522a`) or a `Local` column (`CORREDOR`/`SOBRAS`), the lots go straight to the right place.

**5. Backups:**

```bash
cat > /opt/arrumo/deploy/.backup.env <<'X'
export RESTIC_REPOSITORY=s3:https://<endpoint>/<bucket>     # e.g. Hetzner Object Storage or Backblaze B2
export RESTIC_PASSWORD=<openssl rand -hex 32>               # also keep it in a password manager
export AWS_ACCESS_KEY_ID=...  AWS_SECRET_ACCESS_KEY=...
X
chmod 600 /opt/arrumo/deploy/.backup.env
. /opt/arrumo/deploy/.backup.env && restic init
sudo crontab -e   # 30 2 * * * /opt/arrumo/deploy/backup.sh >> /var/log/arrumo-backup.log 2>&1
```

To restore (test this once, before you need it):

```bash
. ./.backup.env && restic restore latest --tag arrumo --target /tmp/rest
docker compose exec -T db pg_restore -U arrumo -d arrumo --clean --if-exists < /tmp/rest/var/backups/arrumo/arrumo.dump
```

**6. Update the app:** `git pull && docker compose up -d --build`. Database migrations run by themselves at start-up.

## Deploy to Railway (alternative to a VPS)

1. Code in a **private** GitHub repository.
2. Railway → New Project → Deploy from GitHub → pick the repository. In Settings, **Root Directory** = `backend` (it uses the `Dockerfile`).
3. In the same project: New → Database → **PostgreSQL**.
4. App service variables:
   ```
   DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   DB_USER=${{Postgres.PGUSER}}
   DB_PASSWORD=${{Postgres.PGPASSWORD}}
   APP_ENCRYPTION_KEY=<openssl rand -base64 32>
   APP_ADMIN_USERNAME=admin
   APP_ADMIN_PASSWORD=<strong temporary password>
   JAVA_TOOL_OPTIONS=-Xmx384m -XX:+UseSerialGC
   ```
5. Settings → Networking → **Generate Domain** (HTTPS included). Healthcheck path: `/actuator/health`.
6. Sign in as admin, change the password and set up 2FA.

## Finished goods (logistics)

Map of 9 racks, 2 levels and 4 slots per level. `R324` is rack 3, level 2, slot 4.

- **The unit is the pallet.** Scanning a pallet's GS1 label (GS1-128 or DataMatrix) on a free slot stores the SSCC (00), GTIN (01/02), batch (10), production date (11), expiry (15/17), boxes (37) and net weight (310n). The same lot can be on several pallets.
- **FEFO:** the colour shows how long until expiry. Pallets expiring within 30 days are red and leave first.
- **Product:** comes from the GTIN. Put each finished product's GTIN in "Produtos" and pallets with that GTIN complete themselves. A pallet with an unknown GTIN, or with a hand-typed lot, stays orange until someone picks the product in "Corrigir dados".
- **Notes:** any text that is neither a label nor a lot is kept as a note on the slot.
- **A pallet that had been shipped and is scanned again** comes back into stock. This is recorded in the history.
- **ERP stock:** in "Lotes", "Importar stock", choose "Stock de produto acabado". It takes the same ERP export as raw material (Lot, Product, Stock, Notes), one line per lot. Each import replaces the previous one. Pallets do not change: the app shows the ERP quantity and notes in the detail panel, gives a product to pallets without one, and lists ERP lots with no pallet in the app and map pallets that are no longer in the ERP.

**Roles:**
- **Logistics** only sees this map: stores, moves, ships and fixes data.
- **Manager and admin** can also change finished goods.
- **Viewer and operator** see finished goods but cannot change them.

## Lots that have left

The ERP stock list (raw material or finished goods) is the complete list. After an import, the app shows everything that is on the map or in the lists but was missing from the ERP. With "Dar saída a todos", those lots and pallets are shipped in one go and the history records "não veio no stock do ERP". With "Agora não", they stay as they are. Leftovers come in a separate list: the raw-material list checks the map and the corridor, and the leftovers list checks only the leftovers. If a lot in the list was marked as shipped, it comes back into the room at the place given (for example, the leftovers).

## Terminal mode (handhelds)

In the menu, "Terminal" opens a screen for the operators' scanners, with large buttons and one field at a time. The scanner sends Enter after each scan. On a 1024×768 screen it uses two columns: the operation on the left, results or recent movements on the right.

At the top there are two tabs, "Matéria-prima" (raw material) and "Produto acabado" (finished goods). Store, move, remove and search only work on the chosen tab, and the app remembers the last tab used on that device. When searching, each tab shows how many results it has. Operators can only search finished goods. Logistics users see no tabs: they only have finished goods.

- **Arrumar (store):** scan the position, then the lot or label.
- **Mover (move):** scan the lot (or the position it leaves), then the destination position.
- **Tirar (remove):** scan the lot or position and choose where it goes (shipped, corridor, leftovers; for finished goods, shipped).
- **Pesquisar (search):** scan the lot or SSCC, or type an ERP code, a product, a position or text from notes. Shows where it is, the product and the notes.

Raw-material positions end with the slot: `R514a`, `R514b` on floor 1; `R521a`, `R521b`, `R521c` on floor 2. Finished goods go from `R111` to `R924`. Typing just `R522` also works: if several slots are free, the app asks which one. A lot without the leading `L` is also found. Direct address for the handheld: `https://<server>/#terminal`.

## Search

The map search also looks in position notes and in the notes of lots, pallets and the ERP stock. The "Lotes" search also looks in the notes.

## Packing board (admin only)

Kanban board that replaced Trello. Columns can be renamed, added or deleted, and each column can have:

- **"Lista das 17h"**: the column's cards go out at 17:00, Monday to Friday, by e-mail and Teams.
- **"Reporta no Teams"**: when a card enters the column, a message is sent to Teams straight away.

The app never sends e-mail itself. It posts the data to two **Power Automate** flows, and the flows send from your account. So no e-mail password is kept on the server.

The JSON body the app sends has:

| Field | Content |
|---|---|
| `type`, `attachments` | Adaptive card, read by the Teams template as-is |
| `subject` | E-mail subject |
| `text` | Plain-text version |
| `html` | HTML version for the e-mail body |

**Flow 1, the 17:00 list (Teams + e-mail):**

1. In Teams, open the chat with logistics, click `...` > **Workflows** and choose the template **"Post to a chat when a webhook request is received"**. Create it.
2. Open the flow in Power Automate. In the trigger "When a Teams webhook request is received", copy the **HTTP URL**.
3. Still in the flow, add the action **Office 365 Outlook > Send an email (V2)**:
   - To: the recipients' addresses
   - Subject: the expression `triggerBody()?['subject']`
   - Body: the expression `triggerBody()?['html']`
4. Save. In the app: "Embalamentos" → **"Configurar envios"** → paste the URL. It is stored encrypted in the database (and included in backups). The environment variable `APP_PACKING_WEBHOOK_DAILY` also works; the value in the app wins.

**Flow 2, finished packing job (Teams only):** repeat step 1 (it can be the same chat) and paste the URL in "Configurar envios" (or set `APP_PACKING_WEBHOOK_FINISHED`).

Test with the **"Enviar lista agora"** button (no restart needed when using "Configurar envios").

Notes:
- The URLs are secret: whoever has one can post messages to that chat. The app never shows them again (only the last 6 characters) and only accepts HTTPS Power Automate URLs.
- If the app runs only on the internal network, the server needs outbound HTTPS to `*.logic.azure.com` and `*.powerplatform.com`.
- Failed sends show on the card ("Não reportado"), and the card has a button to try again.
- To change the time, set `APP_PACKING_CRON`.

## Notes

- **Terminal scanners** must be in keyboard (HID) mode with an Enter suffix.
- **Changing the layout** (more racks, another room): add a new migration `V3__...sql` in `db/migration`. Never edit a migration that has already run.
- **Before production**, move Spring Boot to the latest 4.x (`mvn versions:display-parent-updates`).
- Real time uses Server-Sent Events, which is enough for one app instance (dozens of screens). To run several instances, connect them with PostgreSQL `LISTEN/NOTIFY`.
