# Assignment 3: PostgreSQL Internals Assignment

In this assignment you will explore the internals of PostgreSQL — a production-grade,
open-source relational database system with ~1.4 million lines of C code. Rather than
treating the database as a black box, you will read the source code, trace how a query
moves through the system, and make a real change to the server itself. By the end you
should have a concrete mental model of how a DBMS actually works beneath the SQL surface.

---

## Getting Started

### Step 0 — Do the Hello World exercise first

Before anything else, start the container and read through the guided exercise:

```bash
cat /home/postgres/hello_world_exercise.md
```

It walks you through the edit → compile → restart → test loop so the rest of this
assignment makes sense. Do not skip it.

### Step 1 — Pull the Docker image

```bash
docker pull amolumd/cmsc424-postgres-dev
docker run -it --rm -p 5431:5432 amolumd/cmsc424-postgres-dev
```

This drops you into a bash shell inside the container as the `postgres` user.
PostgreSQL is already running and the `university` database is pre-loaded.

> **Port note:** the container's PostgreSQL listens on 5432 internally, mapped to
> **5431** on your host to avoid conflicts with any local PostgreSQL installation.

### Connecting

Inside the container:
```bash
psql -d university          # or just: psql  (alias defaults to -U postgres)
```

From your host machine (with the container running):
```bash
psql -h localhost -p 5431 -U postgres -d university
```

### Rebuild shortcut

After editing any source file, recompile and restart with a single line:

```bash
make -j$(nproc) -C src/backend && make -C src/backend install && pg_ctl restart -D $PGDATA
```

This recompiles only changed files (usually a few seconds) rather than rebuilding
the entire tree.

### Optional: build from source

If you want to modify the Dockerfile or start from scratch:

```bash
git clone <this-repo>
cd PostgreSQL-docker
docker compose build      # takes several minutes — compiles PG17 from source
docker compose run --rm postgres-dev
```

---

## The University Database

The pre-loaded `university` database is our running example from the textbook.

Schema: `~/postgres/../DDL.sql` (also at `/usr/local/pgsql/sql/DDL.sql`)

---

## Part 1 — Codebase Exploration

Answer the following questions on Gradescope. Each question requires you to open the
PostgreSQL source code (in `~/postgres/`) and read the relevant file. Short answers are
fine — 1–4 sentences each, plus the relevant file path and/or function name.

The source tree is a standard C project. Useful navigation tips:
```bash
# Find a function definition
grep -rn "^exec_simple_query" ~/postgres/src/

# Jump to a file in vim and search
vim ~/postgres/src/backend/tcop/postgres.c
# then: /pg_parse_query  to find the call
```

---

**Q1. Entry point**

When a client sends a SQL string to the server, what C function is the first to receive
and process it? Give the function name and the file it lives in.

*Hint: start in `src/backend/tcop/postgres.c`. Look for a function called when a
"simple query" message arrives from the client.*

---

**Q2. Parse tree representation**

After parsing, each SQL statement is represented as a node in an abstract syntax tree
(AST). What C struct wraps a single raw statement at this stage? In which header file
is it defined?

*Hint: look at what `pg_parse_query()` returns and what type is extracted from the list
with `lfirst_node(...)` inside the foreach loop.*

---

**Q3. Runtime type checking**

PostgreSQL's node system allows one function to handle many different node types.
What macro does PostgreSQL use to check whether a parse tree node is, say, a
`SelectStmt` versus an `InsertStmt`? Explain briefly how it works mechanically (i.e.
what does the macro actually inspect at runtime?).

*Hint: find the macro definition in `src/include/nodes/nodes.h`.*

---

**Q4. Semantic analysis**

Parsing produces a "raw" parse tree that knows nothing about the schema (it doesn't
know whether `student` is a table, a view, or a typo). What function transforms this
raw tree into an analyzed `Query *` struct that has resolved all names against the
catalog? Give the function name and file.

*Hint: `src/backend/parser/analyze.c`.*

---

**Q5. Query rewriting**

Between analysis and planning there is a rewriting step. What is the query rewriter's
primary practical use in PostgreSQL (think: what feature of SQL does it implement)?
What is the entry point function and where is it?

*Hint: `src/backend/rewrite/rewriteHandler.c`.*

---

**Q6. Query planning**

The planner takes an analyzed `Query *` and produces a `PlannedStmt *` — a physical
execution plan. What is the top-level planner entry point function?

*Hint: `src/backend/optimizer/plan/planner.c`.*

---

**Q7. Executor dispatch**

The executor works on a tree of plan nodes (SeqScan, HashJoin, Sort, etc.). What
single function is called to get the next tuple from *any* plan node type, regardless
of what that node actually is? How does it dispatch to the right implementation?

*Hint: `src/backend/executor/execProcnode.c`.*

---

**Q8. Sequential scan**

Find the implementation of the sequential (full table) scan executor node. What
function fetches the next tuple off the heap? Give the file and function name.

*Hint: `src/backend/executor/nodeSeqscan.c`.*

---

**Q9. Error handling**

If you call `ereport(ERROR, errmsg("something went wrong"))` from inside an executor
node, what happens to the currently running transaction? How does PostgreSQL unwind
the call stack — does it use C exceptions, `setjmp`/`longjmp`, or something else?

*Hint: search for `PG_TRY` and `PG_CATCH` in the source and read the comments in
`src/include/utils/elog.h`.*

---

**Q10. MVCC visibility (bonus)**

Every heap tuple has two hidden system columns: `xmin` and `xmax`. What do they
represent? What function ultimately decides whether a given tuple is visible to the
current transaction? Give the file and function name.

*Hint: `src/backend/access/heap/heapam_visibility.c`.*

---

## Part 2 — Programming Challenge

Pick **one** option below and implement it. All options are graded the same — choose
based on how much time and ambition you have.

### Submission format

1. Generate a patch of your changes:
   ```bash
   cd ~/postgres
   git diff > my_change.patch
   ```
2. Write a short report (roughly 1 page, PDF):
   - What you changed and which files
   - Why the change works (trace through the relevant code path)
   - A screenshot or copy-paste of psql output showing it in action
3. Submit both files on Gradescope.

---

### Tier 1 — Warmup (a few hours)

These are direct extensions of the Hello World exercise. Good if you're short on time
or less comfortable with C.

**Option A — Per-statement-type messages**

Extend the hello-world NOTICE so that INSERT, UPDATE, and DELETE each get their own
distinct (and ideally sarcastic) message. A SELECT should still get its own message.

- Where to look: the `foreach` loop in `exec_simple_query` in
  `src/backend/tcop/postgres.c`
- Key macros: `IsA(parsetree->stmt, InsertStmt)`, `IsA(parsetree->stmt, UpdateStmt)`,
  `IsA(parsetree->stmt, DeleteStmt)`

**Option B — Session query counter**

Add a per-session counter that tracks how many queries of each type
(SELECT / INSERT / UPDATE / DELETE) the current client has run, and include the
running totals in each NOTICE message.

- Where to look: same location as Option A
- Key concept: `static` local variables in C persist across calls within the same
  process (and PostgreSQL forks one backend process per client connection)

**Option C — Forbidden table guard**

Pick any table in the university schema (e.g. `instructor`). Make PostgreSQL refuse
any query that touches that table with a helpful `ereport(ERROR, ...)` message. The
check should fire after analysis, so the table name has been resolved.

- Where to look: after `pg_analyze_and_rewrite_fixedparams()` returns in
  `exec_simple_query`; inspect `query->rtable` (a list of `RangeTblEntry *`) and
  check each entry's `relid` against `RangeVarGetRelid()`
- Alternatively: add the check inside `src/backend/parser/analyze.c` after name
  resolution

---

### Tier 2 — Intermediate (about a week)

These require reading more of the codebase and understanding its data structures.

**Option D — Custom configuration parameter (GUC)**

Add a new boolean server parameter, e.g. `cmsc424.verbose_queries`, that can be set
in `postgresql.conf` or with `SET cmsc424.verbose_queries = on`. Use it to gate
whether the Tier 1 NOTICE messages appear, so the behavior is configurable without
recompiling.

- Where to look: `src/backend/utils/misc/guc.c` — search for `DefineCustomBoolVariable`
  and follow an existing example (e.g. `log_duration`)
- You will also need to declare the variable in the right header and call
  `DefineCustomBoolVariable` during server startup (look for `InitializeGUCOptions`)

**Option E — New built-in SQL function**

Implement a new SQL-callable function in C and register it so you can call it from
psql. For example: `SELECT word_count('hello world foo')` → `3`, or
`SELECT str_reverse('hello')` → `'olleh'`.

- Where to look: `src/backend/utils/adt/varlena.c` for text functions; copy the
  pattern of a simple existing function like `text_length`
- Registration: add an entry to `src/include/catalog/pg_proc.dat` and run
  `make -C src/backend/catalog` to regenerate headers; or use `CREATE FUNCTION`
  with `LANGUAGE C` as a simpler alternative that doesn't require catalog changes

**Option F — Per-query execution statistics**

After each query finishes, print a NOTICE showing how many heap pages were read and
how many tuples were scanned. This gives students a taste of how `EXPLAIN ANALYZE`
works internally.

- Where to look: `pgBufferUsage` (a global struct updated by the buffer manager) in
  `src/include/executor/instrument.h`; snapshot it before and after `PortalRun()` in
  `exec_simple_query` and diff the fields

---

### Tier 3 — Advanced (two or more weeks)

These are open-ended and research-adjacent. Partial credit is available for serious
attempts that don't fully work.

**Option G — New executor node: random sampler**

Implement a new plan node type `SampleLimit` that works like `LIMIT N` but returns N
randomly selected rows from its child node rather than the first N. This requires:
adding a new `NodeTag`, implementing `ExecSampleLimit` (modeled on `nodeLimit.c`),
wiring it into `execProcnode.c`, and modifying the parser/planner to emit the new
node for a new syntax like `FETCH RANDOM 5 ROWS`.

- Starting point: `src/backend/executor/nodeLimit.c`,
  `src/backend/executor/execProcnode.c`, `src/include/nodes/plannodes.h`

**Option H — B-tree instrumentation**

Modify the B-tree index scan to record how many index pages and how many heap pages
it visits during a single scan, and report this at the end of the query via a NOTICE.
Compare the numbers between a query that uses an index and one that does a seq scan.

- Starting point: `src/backend/access/nbtree/nbtsearch.c` (the scan functions),
  `src/backend/access/nbtree/nbtutils.c`

**Option I — Custom table access method**

PostgreSQL 12+ supports pluggable storage engines via the Table Access Method API.
Implement a minimal access method that stores tuples in a simple flat file (no MVCC,
no WAL) — enough to support `INSERT` and `SELECT *`. This is how extensions like
`columnar` or `zedstore` hook into the engine.

- Starting point: `src/backend/access/heap/heapam_handler.c` (the reference
  implementation); `src/include/access/tableam.h` (the interface)

---

## Reference: Key Source Files

| What | File |
|------|------|
| Simple query entry point | `src/backend/tcop/postgres.c` |
| Parser | `src/backend/parser/` |
| Semantic analysis | `src/backend/parser/analyze.c` |
| Query rewriter | `src/backend/rewrite/rewriteHandler.c` |
| Planner top level | `src/backend/optimizer/plan/planner.c` |
| Executor dispatch | `src/backend/executor/execProcnode.c` |
| Sequential scan | `src/backend/executor/nodeSeqscan.c` |
| Index scan (B-tree) | `src/backend/access/nbtree/nbtsearch.c` |
| Heap tuple visibility | `src/backend/access/heap/heapam_visibility.c` |
| Error/notice reporting | `src/include/utils/elog.h` |
| Node types & `IsA` macro | `src/include/nodes/nodes.h` |
| Parse tree node structs | `src/include/nodes/parsenodes.h` |
| GUC parameters | `src/backend/utils/misc/guc.c` |
| Built-in text functions | `src/backend/utils/adt/varlena.c` |

---

## Tips

- **`grep -rn "function_name" ~/postgres/src/`** is your best friend for navigating
  the codebase.
- **`git diff`** shows exactly what you've changed at any point.
- **`git stash`** lets you temporarily revert to a clean state to verify your baseline.
- The PostgreSQL source has excellent inline comments — read them.
- If the server crashes after a change (assertion failure, segfault), check
  `$PGDATA/logfile` for the backtrace, then use `gdb` (already installed) to dig in.
- You can always reset to a clean state: `git checkout src/` and rebuild.
