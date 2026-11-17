# Deploying

Vercel hosts static sites and short serverless functions. It cannot run the Spring Boot service
or Postgres, so there are two ways to publish this project.

| | What runs on Vercel | Backend | Use it for |
|---|---|---|---|
| **A. Demo** | The dashboard with three recorded runs | None | A link for a recruiter. Free, always fast, nothing to abuse. |
| **B. Full stack** | The dashboard | Spring Boot and Postgres on another host | Letting people start their own runs. |

Start with A. It takes about five minutes.

## A. Demo on Vercel (no backend)

The dashboard has a demo build (`VITE_DEMO=true`) that reads recorded runs from
`web/public/demo/` instead of calling the service. Three runs from the real Nasdaq data are
already committed there. Only aggregated results and per-second timelines are stored, never
market data messages.

1. Push the repository to GitHub.
2. At vercel.com, sign in with GitHub, then **Add New → Project** and import the repository.
3. Set **Root Directory** to `web`. Vercel should detect **Vite**.
4. Leave the build settings alone. `web/vercel.json` already sets the build command to
   `npm run build:demo` and the output directory to `dist`.
5. Click **Deploy**, then open the `*.vercel.app` link. It should open on the first recorded run,
   with a note in the sidebar saying it's a demo.

Command-line alternative, from `web/`: `npx vercel`, then `npx vercel --prod`.

To try the demo build locally: `cd web && npm run build:demo && npx vite preview`.

### Re-recording the demo runs

Needed if you change the simulator, the defaults or the scenarios.

```bash
FEEDLAB_PORT=8090 docker compose up -d --build     # with the real file in ./data
cd web
FEEDLAB_API=http://localhost:8090 npm run export-demo
git add public/demo && git commit -m "Re-record demo runs"
```

Scenarios live in `web/scripts/export-demo.mjs`.

## B. Full stack

### 1. Backend and database

Any host that runs a Docker image and offers Postgres works (Render, Railway, Fly.io). The exact
screens differ, so check the host's documentation. What you need:

1. Create a **Postgres** database on the host.
2. Create a **web service from the repository's root `Dockerfile`**.
3. Set these environment variables on the service:

   | Variable | Value |
   |---|---|
   | `FEEDLAB_DB_URL` | `jdbc:postgresql://HOST:5432/DBNAME` |
   | `FEEDLAB_DB_USER` | the database user |
   | `FEEDLAB_DB_PASSWORD` | the database password |

   Hosts usually give a `postgres://user:password@host/db` URL. Split it into the three values
   above; Spring needs the `jdbc:postgresql://` form.
4. Deploy. Check `https://YOUR-BACKEND/api/inputs`. It should return
   `["synthetic-10min.itch.gz"]`.

What is already handled in the repo:

- **Port.** The service listens on `PORT` when the host sets it, otherwise 8080.
- **Data.** The image contains ten minutes of synthetic traffic, so a hosted copy can run
  simulations without the Nasdaq file. Synthetic bursts are made up, so don't draw conclusions
  about real feeds from its numbers.
- **Memory.** Java sizes its heap to 70% of the container limit. A 512 MB limit is enough: a run
  peaked at about 240 MB in testing.
- **Schema.** Flyway creates the tables on first start.

### 2. Dashboard on Vercel

1. Copy `web/vercel.fullstack.example.json` over `web/vercel.json`, and replace
   `YOUR-BACKEND-HOST` with your backend's address. The rewrite sends the dashboard's `/api/...`
   calls to the backend, so no CORS setup is needed.
2. Follow steps 1–5 of section A.

### 3. Before you share the link

- **The API has no login.** Anyone with the link can start runs and use your server's CPU. Share
  the link only where you mean to, or prefer the demo link.
- **Free backends sleep** after a while and take around a minute to wake. Open the app a few
  minutes before an interview.
- **Real Nasdaq data.** Don't put the Nasdaq file in the repository or on a public host, and check
  Nasdaq's terms before publishing results derived from it.
- **Passwords.** Use real, private values on the host. `feedlab/feedlab` in `docker-compose.yml`
  is for local development only.
