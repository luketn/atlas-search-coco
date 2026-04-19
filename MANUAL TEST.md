# Manual Test

This document lists the main use cases to verify manually for the Atlas Search + Vector Search application.

## Prerequisites

### Base environment

- MongoDB Atlas Local is running and reachable from the app.
- The app is running on `http://localhost:8222`.
- The browser cache is cleared or a fresh tab is used after restarting the app with different startup flags.

### For text-only startup

- Start the app without vector embedding generation:

```bash
mvn compile exec:java
```

Or:

```bash
docker compose up java-app
```

### For vector-enabled startup

- LM Studio is running locally with an embedding-capable model loaded.
- The model `text-embedding-nomic-embed-text-v1.5` is available through the LM Studio API.
- Start the app with vector embedding generation:

```bash
mvn compile exec:java -Dexec.args="--lmStudioVectorEmbeddings"
```

Or:

```bash
JAVA_APP_ARGS=--lmStudioVectorEmbeddings docker compose up java-app
```

## Test Cases

### 1. App loads successfully

- Open `http://localhost:8222`.
- Confirm the page renders without a blank screen.
- Confirm the category filters load.
- Confirm search results appear automatically for the default query.

Expected result:
- The page shows search inputs, filters, and result cards.

### 2. Text-only mode hides vector controls when no vector index exists

- Start the app against a database that does not have the vector index.
- Open `http://localhost:8222`.

Expected result:
- The `Search Type` section is not shown.
- There is no `Text` / `Vector` checkbox group.
- Standard text search still works.

### 3. Vector-enabled mode shows vector controls

- Start the app against a database that has the vector index.
- Open `http://localhost:8222`.

Expected result:
- The `Search Type` section is visible.
- Both `Text` and `Vector` options are present.
- `Text` is selected by default.

### 4. Text search returns relevant caption matches

- Enter `cat` in the caption search field.

Expected result:
- Results update.
- Returned captions visibly relate to cats.
- Pagination updates consistently.

### 5. Vector-only search works

- In vector-enabled mode, enter a semantic query such as `icy sculpture on a footpath`.
- Leave only `Vector` selected.

Expected result:
- Results load successfully.
- No server error is shown.
- Returned results are semantically related even if the exact words do not appear in the caption.

### 6. Hybrid search works

- In vector-enabled mode, enter `newspaper`.
- Select both `Text` and `Vector`.
- Add filters such as:
  - `animal=bird`
  - `hasPerson=true`

Expected result:
- Results load successfully.
- No server error is shown.
- The results reflect both semantic relevance and text relevance.

### 7. Search still works with only Text selected after toggling modes

- In vector-enabled mode, select both `Text` and `Vector`.
- Then deselect `Vector` so only `Text` remains.
- Run a search such as `cat`.

Expected result:
- Search still returns normal text-search results.
- The app does not get stuck in an empty state.

### 8. Result cards do not expose internal vector fields

- Run any search.
- Inspect the visible result cards.
- Optionally inspect the `/image/search` response in devtools or with curl.

Expected result:
- The results include user-facing fields such as caption, image URL, dimensions, and tags.
- The search result payload does not include:
  - `captionEmbedding`
  - `captionEmbeddingModel`
  - `licenseUrl`
  - `licenseName`

### 9. Clicking a result opens the detail viewer

- Click any result card.

Expected result:
- A detail dialog opens.
- The large image is displayed.
- The caption is shown underneath.
- Tags are shown underneath.
- The dimensions are shown correctly.

### 10. Detail viewer left/right navigation works

- Open the detail viewer from any result card.
- Click the right arrow several times.
- Click the left arrow several times.

Expected result:
- The viewer advances one result at a time.
- The content updates correctly for each result.
- The viewer does not close unexpectedly.

### 11. Detail viewer automatically pulls the next page

- Search for a term with more than one page of results, such as `cat`.
- Open the first result in the detail viewer.
- Keep clicking the right arrow until you move beyond the fifth result.

Expected result:
- The viewer continues into the next page automatically.
- The result counter continues increasing.
- The app does not require closing the viewer to continue browsing.

### 12. Detail viewer can navigate back across a page boundary

- After moving into a later page in the detail viewer, click the left arrow.

Expected result:
- The viewer can move back to earlier results.
- The content stays consistent.

### 13. Pagination controls work in the main results view

- Use `Next` and `Previous` buttons in the main results area.

Expected result:
- The page number updates correctly.
- Results change correctly.
- No duplicate or stale results are shown for the selected page.

### 14. Filters combine correctly with search

- Use a query such as `cat`.
- Add one or more filters from the left-hand panel.

Expected result:
- Results narrow correctly.
- Facet counts refresh.
- Removing a filter expands the results again.

### 15. Browser layout works on mobile-sized viewport

- Open the app in a narrow viewport such as approximately `390x844`.
- Run a search and open the detail viewer.

Expected result:
- The page remains usable without broken layout.
- Result cards stack vertically.
- The detail viewer remains readable and navigable.

### 16. Docker Compose startup without vector flag preserves previous behavior

- Start with:

```bash
docker compose up java-app
```

Expected result:
- The app starts normally.
- Text search works.
- Vector search controls are absent unless the collection already has the vector index.

### 17. Docker Compose startup with vector flag enables vector flow

- Start with:

```bash
JAVA_APP_ARGS=--lmStudioVectorEmbeddings docker compose up java-app
```

Expected result:
- The app starts successfully.
- Embeddings are generated during load.
- The vector index is available after startup.
- Vector and hybrid search work in the UI.

## Suggested Smoke Test Sequence

For a short end-to-end regression pass, run these in order:

1. Verify startup and initial results load.
2. Verify text-only search with `cat`.
3. Verify vector-only search with `icy sculpture on a footpath`.
4. Verify hybrid search with `newspaper` plus filters.
5. Open a result in the detail viewer.
6. Navigate past the page boundary in the detail viewer.
7. Re-run the app without a vector index and confirm the search type controls disappear.
