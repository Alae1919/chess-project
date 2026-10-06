# Deploying to AWS

This puts REXCHESS on a single EC2 server, running the same Docker Compose stack you can start on your own
machine, with HTTPS from a free Let's Encrypt certificate.

```
 browser ──https──► Caddy ──► nginx ──┬──► Spring Boot backend ──► Postgres
          (443)     (HTTPS)   (the app │   (REST + 2 WebSockets)    (a volume on
                              + proxy)  └─ /api, /ws                   the server's disk)
```

Only Caddy is reachable from the internet (ports 80 and 443). Everything else lives on Docker's internal network.

**Why one server and not several containers behind a load balancer (ECS, Fargate)?** The live games, the matchmaking
queue and the WebSocket connections are kept in the backend's memory. Two backends would each know about
different games, and two players matched on different ones could never meet. One server is the right size for a
portfolio demo; scaling out would first mean moving that state into Redis or the database.

## What you need

* An AWS account.
* A domain name you can add a DNS record to (Route 53 or anywhere else). No domain? See
  [Without a domain](#without-a-domain) below.
* About 30 minutes.

## 1. Launch the server

In the EC2 console, **Launch instance**:

| Setting | Value |
|---|---|
| Image | Ubuntu Server 24.04 LTS |
| Type | `t3.small` (2 vCPU, 2 GB). The AI is CPU-bound; if several people play the strongest level at once, go up to `t3.medium` |
| Key pair | create one and keep the `.pem` file safe: it is the only way in |
| Storage | 20 GiB gp3 |
| Security group | inbound **80** and **443** from anywhere (`0.0.0.0/0`), inbound **22** from **your IP only** |

Do not open 5432 (Postgres) or 8080 (the backend): nothing outside the server should reach them.

Then **Elastic IPs → Allocate**, and associate it with the instance. Without one, the server's address changes
every time it is stopped, and your DNS record would point at nothing. (AWS charges for public IPv4 addresses, a few
dollars a month.)

## 2. Point your domain at it

Create an **A record** for `chess.example.com` (your own name) with the Elastic IP as its value. Wait until
`nslookup chess.example.com` shows that address. Caddy can only get a certificate once this works.

### Without a domain

Use the address itself as the name: `sslip.io` turns any IP into a name that resolves to it. For the Elastic IP
`203.0.113.10`, the site is `203-0-113-10.sslip.io`. Use that as `SITE_ADDRESS` below. It is fine for a trial; for
the link on your CV, a domain of your own looks better and avoids sharing a certificate quota with strangers.

## 3. Prepare the server

Connect (`ssh -i your-key.pem ubuntu@<elastic-ip>`), then:

```bash
# Docker, with the compose plugin
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu          # log out and back in afterwards

# 2 GB of swap: building the Angular app on a 2 GB server needs it
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 4. Get the code and configure it

```bash
git clone https://github.com/Alae1919/chess-project.git
cd chess-project
cp .env.example .env
chmod 600 .env
```

Edit `.env` (`nano .env`):

| Variable | Value |
|---|---|
| `SITE_ADDRESS` | your domain, e.g. `chess.example.com` |
| `POSTGRES_PASSWORD` | `openssl rand -base64 24` |
| `JWT_SECRET` | `openssl rand -base64 48` |

`ALLOWED_ORIGINS` defaults to `https://` plus your `SITE_ADDRESS`, which is right, so you can leave it out. If you
change `SITE_ADDRESS` later, run the `up -d --build` command below again so the backend picks up the new origin.

Keep a copy of `.env` somewhere safe (a password manager). Changing `JWT_SECRET` logs everyone out; changing
`POSTGRES_PASSWORD` after the first start does **not** change the password inside the existing database.

## 5. Start it

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

The first build takes several minutes. Then:

```bash
docker compose -f docker-compose.prod.yml ps     # db, backend, frontend, caddy: all "healthy"/"running"
curl -I https://chess.example.com                # 200, with Strict-Transport-Security and Content-Security-Policy
```

Open the site, register, and play a game against the AI. To check the online side, open it in a second browser
(or a private window) with another account, and start an online game between the two.

The first request after a fresh start can take a few seconds while Caddy gets the certificate. If it does not
arrive, see [Troubleshooting](#troubleshooting).

## Day to day

```bash
cd ~/chess-project

# Update to the latest code
git pull
docker compose -f docker-compose.prod.yml up -d --build

# Logs (add a service name to see one: backend, frontend, caddy, db)
docker compose -f docker-compose.prod.yml logs -f --tail=100

# Restart one service
docker compose -f docker-compose.prod.yml restart backend
```

The containers restart by themselves after a crash or a reboot of the server. An update briefly interrupts games in
progress: players reconnect, and unfinished games are reloaded from the database.

**Roll back** a bad update: `git log` to find the last good commit, `git checkout <commit>`, and run the `up -d --build`
line again.

## Backups

The database lives in a Docker volume on the server's disk. If the instance is terminated, the games and accounts go
with it, so back it up.

**Nightly dump**, kept for two weeks. Run `crontab -e` and add (one line):

```
0 3 * * * mkdir -p $HOME/backups && docker exec chess_db pg_dump -U chess_user chess_db | gzip > $HOME/backups/chess_$(date +\%F).sql.gz && find $HOME/backups -name 'chess_*.sql.gz' -mtime +14 -delete
```

**Restore** a dump into the running stack (this replaces the current data, so stop the backend first):

```bash
docker compose -f docker-compose.prod.yml stop backend
docker exec chess_db psql -U chess_user -d postgres -c 'DROP DATABASE chess_db' -c 'CREATE DATABASE chess_db OWNER chess_user'
gunzip -c ~/backups/chess_2026-10-06.sql.gz | docker exec -i chess_db psql -U chess_user chess_db
docker compose -f docker-compose.prod.yml start backend
```

The dumps are on the same disk as the database, so they do not protect against losing the server. Two ways to get them
off it: take **EBS snapshots** (EC2 → Elastic Block Store → Lifecycle Manager can do it nightly for you), or copy
them to an S3 bucket by attaching an IAM role that may write to the bucket and adding an `aws s3 cp` to the cron line.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| The browser says the connection timed out | the security group does not allow 80 and 443, or the Elastic IP is not associated |
| The certificate is not issued (`docker compose logs caddy` shows ACME errors) | the DNS record does not point at the server yet, or port 80 is closed: Let's Encrypt validates over port 80 |
| Login says "forbidden" or the page loads but nothing works | `ALLOWED_ORIGINS` does not match the address in the browser. It must be exactly `https://` plus the site name |
| `502 Bad Gateway` | the backend is not healthy: `docker compose -f docker-compose.prod.yml logs backend` |
| The build is killed ("Killed", exit 137) | out of memory: check the swap file is on (`swapon --show`) or use a `t3.medium` |
| `required variable ... is missing a value` | a variable in `.env` is empty or misspelled |
| Games stop mid-move for everyone | the backend restarted (see its logs); `restart: unless-stopped` brings it back on its own |

## What this does not cover

* **A deploy pipeline.** Updating is `git pull` and one command on the server. Automating that from GitHub Actions
  (over SSH or AWS Systems Manager) is a reasonable next step once the manual way has worked once.
* **A managed database.** RDS for PostgreSQL would take backups off your hands, at the price of a database that costs more
  than the server. The backend only needs its `SPRING_DATASOURCE_*` settings changed.
* **Monitoring.** Beyond `docker compose ps`, consider an uptime check (Route 53 health checks, or any free
  uptime monitor) on `https://your-site/`.
