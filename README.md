# AbyssStats — Fabric 26.3

Mod **server-side**, separat de AbyssGuard. Nu are scoreboard lateral.

## Comenzi (OP nivel 2)

- `/as pos1` / `/as pos2` — selecteaza colturile arenei
- `/as create pvp` — creeaza arena
- `/as delete pvp` — sterge arena
- `/as list`
- `/as holo create pvp kills` — holograma TOP kills la pozitia jucatorului
- `/as holo create pvp deaths` — holograma TOP deaths la pozitia jucatorului
- `/as holo remove pvp kills` / `deaths`
- `/as stats ZixtyOFF pvp`
- `/as reload` — reincarca configuratia de pe disc

**Regula:** se numara decesele numai daca victima moare in cuboid. Un kill este acordat daca un jucator este sursa directa/atribuita a mortii. Nu se numara kill-uri pentru decese fara killer jucator. Fiecare arena are clasament separat.

## Configurare

Datele se salveaza in `config/abyssstats/data.json`. Fiecare holograma creeaza `config/abyssstats/holograms/scoreboard-pvp-kills.yml` sau `scoreboard-pvp-deaths.yml`.

YAML suportat: `title`, `limit`, `line`, `empty`. Placeholder-e `{rank}`, `{name}`, `{value}`. Culori cu `&`. Parserul este intentionat simplu: chei de forma `cheie: 'valoare'`, nu YAML complex.

## Compilare

GitHub Actions > Build AbyssStats > Run workflow; descarca artifactul JAR. Necesita Java 25. Acest pachet contine surse, **nu JAR compilat**.

## Limitari

- Hologramele sunt entitati vanilla text_display; sunt regenerate la actualizare, iar refresh-ul sterge toate entitatile `text_display` cu tag-ul `abyssstats_holo`.
- Statisticile sunt pastrate pe UUID. Hologramele sunt afisate doar cand chunk-urile lor sunt incarcate; reinnoirea poate produce flicker.
- Pentru simplitate, permisiunile de administrare folosesc nivel OP 2, nu LuckPerms explicit.
- Codul nu a fost compilat/validat intr-un server Minecraft 26.3 in acest mediu.
