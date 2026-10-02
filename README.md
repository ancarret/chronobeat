# Chronobeat 🎵

**A music chronology game: a mystery song plays and you place it on your timeline before the
title, artist and year are revealed.**

**[Try it live → chronobeat.pages.dev](https://chronobeat.pages.dev)**

<sub>Hosted on free tiers: after ~15 minutes idle the backend sleeps, so the first request can take
30–50 s.</sub>

![Java](https://img.shields.io/badge/Java-21-e76f00)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6db33f)
![Angular](https://img.shields.io/badge/Angular-21-dd0031)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ed)
![Tests](https://img.shields.io/badge/tests-90%20backend%20%C2%B7%2092%20frontend-brightgreen)

<img src="docs/media/demo.gif" width="100%" alt="Chronobeat demo: setting up a game, placing songs on the timeline, the reveal, and an online room's lobby">

## How it plays

- Listen to a short preview and guess where it fits: before, between or after the songs already
  on your timeline. Get it right and it stays; get it wrong and you lose a life.
- Play **solo**, pass **one phone** around, or open an **online room** that friends join from
  their own phones with a 4-letter code.
- Take turns, or race on the **same songs** to be the first to reach N cards.
- Filter by decade, genre and region, from a catalog of thousands of real songs.
- An anonymous profile keeps your records: no sign-up needed.

## Tech stack

- **Backend**: Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL, Flyway, Server-Sent Events
  for live rooms, tested with JUnit 5 and Testcontainers.
- **Frontend**: Angular 21 (standalone components, signals), TypeScript, SCSS, Vitest.
- **Infrastructure**: Docker Compose locally, GitHub Actions CI; deployed on Cloudflare Pages,
  Render and Neon.
- **Music**: song data and 30-second previews from Apple's public iTunes Search API.

## Run it locally

```bash
docker compose up -d --build
# frontend: http://localhost:4200 · backend: http://localhost:8080/swagger-ui.html
```

The catalog fills itself from iTunes on the first run.

## More

Architecture, data model, how answers stay secret until the reveal, testing and deployment are
covered in [docs/architecture.md](docs/architecture.md).

An original portfolio project, not affiliated with any commercial music game. Built by
**Andrés Carretero**.
