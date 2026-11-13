# Write-up

## 1. What did you ask the AI to do, and what did you write or decide yourself?

I used Claude Code as a pair programmer for this whole build rather than writing
it by hand, but the decisions that actually shape the service were mine, made
before any code got written.

**Stack.** Spring Boot on Java, with Maven and PostgreSQL. I didn't pick this
because it's trendy — I picked it because I know it well enough to defend every
line of it in a follow-up session, and because the framework's defaults line up
well with what this exercise is actually testing. Spring Data JPA gets rid of
hand-written CRUD boilerplate so the code that's left is the code that matters
(the short-code algorithm, the dedup rule). Bean Validation gives me declarative
request validation instead of a pile of `if` statements at the top of a
controller. And `@Transactional` matters here in a way it wouldn't for a
toy script: the duplicate-URL check is a read-then-maybe-write, and without a
transaction boundary and a DB-level unique constraint backing it up, two
concurrent identical requests could both pass the "does this URL exist" check
and both try to insert — Spring's transaction management plus Postgres's
constraints turn that from a manual concurrency problem into infrastructure I
get for free.

**PostgreSQL specifically**, over something like a key-value store, because the
data model isn't actually "just a lookup table" — it has invariants that matter
(short codes must be unique, ids must be strictly monotonic for the encoding
scheme to hold) and a relational database enforces those at the storage layer
instead of trusting application code to get it right every time. I run Postgres
as a native local install rather than through Docker. For a single-developer,
single-machine exercise like this one, a container runtime is a moving part
whose main benefit — consistent environments across a team — isn't in play yet;
a native install starts faster and lets me poke at the database directly with
`psql` while iterating. (It also happens to be what was available on this
machine — Docker wasn't installed — but I'd have made the same call either way
for a project this size. I'd reach for `docker-compose` the moment a second
person needs to run this, which is presumably the live follow-up round, and
I've flagged that in section 4.)

Within that frame, I had the AI draft the design for each piece — the entity
and schema, the collision-free short-code generator, the validators, the
dedup/custom-alias logic, the controllers, the exception handling, and the
tests — and I reviewed each piece before moving to the next one, rather than
letting it generate the whole service in one pass and reviewing at the end.
For the two endpoints the exercise actually grades, I didn't stop at "it
compiles" — I ran the service against local Postgres and hit it with curl for
the round trip, the dedup case, a custom alias, a duplicate alias, and both
validation failure paths, before calling any of it done.

## 2. Where did you override, correct, or throw away the AI's output — and why?

The one that mattered most: the original plan for generating ids was to let JPA
assign the primary key from a DB sequence, read the assigned value back inside
the same method, derive the short code from it, and set it on the entity before
the row got flushed — one INSERT, fully populated, no wasted round trip. It
compiled cleanly and the unit tests for the encoder itself passed, because
those only test the pure math function, not what happens once persistence gets
involved. The first real `POST /shorten` against Postgres failed with a
`NOT NULL` violation on `short_code`. It turns out Hibernate snapshots an
entity's state for the queued insert action *at the moment `persist()` is
called*, not at flush time — so the field I set a line later never made it
into the actual INSERT. That's a genuinely subtle Hibernate behavior, and I
only found it because I insisted on running the endpoint instead of trusting a
green compile. The fix was to fetch `nextval('url_mapping_seq')` explicitly
*before* constructing the entity, so both the id and the derived short code
are already set going into a single `persist()` call — which also meant the
entity needed to implement `Persistable<Long>`, since Spring Data's default
"is this row new?" heuristic assumes a non-null `@Id` means an existing row,
which is exactly wrong once you're assigning ids yourself instead of letting
JPA do it.

Second: Lombok's `@Getter`/`@Setter` silently generated nothing on this
machine — the installed JDK (25) is newer than what the Lombok version Spring
Boot pulled in supports for annotation processing. Rather than chase down a
compatible Lombok version or pin an older JDK (fragile either way, and an
evaluator's machine could easily hit the same wall), I had it rewritten with
plain getters and setters. A screenful of boilerplate is a small price for one
less thing that can silently break in someone else's environment.

Third, and smaller: I pushed back on commit granularity partway through. At
one point several unrelated files for the `/shorten` feature were about to
land in a single commit. I had it split into separate commits for the DTOs,
the exception handling, the validators, the id-generation bugfix, the service,
and the controller, so the git history actually reads as a sequence of
decisions instead of one large drop — which is what the exercise explicitly
asks for.

## 3. The two or three biggest trade-offs, and the alternatives considered

**Deterministic short codes instead of random-plus-retry.** I derive each code
by multiplying a DB sequence id by a constant coprime to `62^6` and taking it
mod `62^6`, then base62-encoding the result to a fixed 6 characters.
Multiplying by a value coprime to the modulus is a bijection over that range,
so two different ids provably cannot produce the same code — not "extremely
unlikely," but structurally impossible as long as ids stay under ~56.8 billion.
The usual alternative — generate a random code, try to insert, retry on a
unique-constraint conflict — is easier to explain in one sentence but only
gives you a probabilistic guarantee and needs a retry loop in the write path.
The cost of my approach is a single global sequence as the id source, which
becomes a write bottleneck at very high scale; a real high-throughput version
would want a sharded or Snowflake-style id scheme instead.

**Duplicate URLs are deduplicated, but only against auto-generated codes.**
Submitting the same URL twice without a custom alias returns the existing
mapping (`200`, not `201`) rather than minting a second code for the same
link — this keeps the table from accumulating redundant rows and matches how
most public shorteners behave. A custom alias, though, always creates a new
row even if that URL already has a code, because asking for a specific alias
is a distinct, explicit request, not "just shorten this for me." I considered
the simpler rule of always creating a new row regardless, which avoids this
branching logic entirely, but it means the same link can end up with an
unbounded number of unrelated short codes over time, which felt like the wrong
default.

**Real Postgres for the app, in-memory H2 for tests.** I wanted the service
itself to run against the same kind of database it would use in production,
but I didn't want the test suite to depend on a local Postgres instance being
up — `mvn test` should work on a clean checkout with nothing else installed.
Both use the same `schema.sql`, so the id-generation path under test is the
same code path that runs against Postgres, not a mocked-out substitute.

## 4. What's missing, or what you'd do with another day

- No rate limiting or auth on `/shorten` — right now anyone can mint unlimited
  links, which is fine for an exercise but not for anything public-facing.
- No click analytics, despite the exercise's "Link Analytics" framing. I
  scoped to the two required endpoints and their edge cases first, given the
  time budget, and would add a hit counter, a last-accessed timestamp, and a
  `GET /{code}/stats` endpoint next.
- No `Dockerfile`/`docker-compose.yml` yet — as noted above, I'd add this
  specifically for the live follow-up session, where a second person needs to
  stand this up without installing Postgres by hand the way I did.
- No CI pipeline running `mvn test` on push.
- The short-code keyspace (`62^6`, ~56.8 billion) is bounded by design, which
  is more than enough here but wouldn't be for a "real" unbounded service —
  that would need either a longer code or the sharded id scheme mentioned in
  section 3.
