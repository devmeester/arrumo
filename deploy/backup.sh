#!/bin/sh
# Daily encrypted copy of the database, stored off the server (restic).
# cron: 30 2 * * * /opt/arrumo/deploy/backup.sh >> /var/log/arrumo-backup.log 2>&1
set -eu
cd "$(dirname "$0")"
. ./.backup.env   # RESTIC_REPOSITORY, RESTIC_PASSWORD and the target's credentials (see docs)
mkdir -p /var/backups/arrumo
docker compose exec -T db pg_dump -U arrumo -Fc arrumo > /var/backups/arrumo/arrumo.dump
restic backup /var/backups/arrumo/arrumo.dump --tag arrumo
restic forget --tag arrumo --keep-daily 14 --keep-weekly 8 --keep-monthly 12 --prune
echo "$(date -Is) backup ok"
