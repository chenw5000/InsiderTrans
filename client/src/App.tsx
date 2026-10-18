import { useCallback, useEffect, useRef, useState } from "react";
import { deleteTransaction, listTransactions } from "./api";
import { AdminPage } from "./components/AdminPage";
import { FilterPanel } from "./components/FilterPanel";
import { RecordForm } from "./components/RecordForm";
import { TransactionTable } from "./components/TransactionTable";
import { formatMoney, formatShares, summarize } from "./format";
import {
  DEFAULT_PAGE_SIZE,
  emptyFilters,
  type Transaction,
  type TransactionFilters,
} from "./types";

function isAdminHash(): boolean {
  return window.location.hash.replace(/^#\/?/, "") === "admin";
}

export default function App() {
  const [admin, setAdmin] = useState(isAdminHash);

  useEffect(() => {
    const onHash = () => setAdmin(isAdminHash());
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  if (admin) {
    return <AdminPage />;
  }

  return <LedgerPage />;
}

function LedgerPage() {
  const [draft, setDraft] = useState<TransactionFilters>(emptyFilters);
  const [applied, setApplied] = useState<TransactionFilters>(emptyFilters);
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<Transaction[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [recording, setRecording] = useState(false);

  const requestId = useRef(0);

  const load = useCallback(async (filters: TransactionFilters, nextPage: number) => {
    const id = ++requestId.current;
    try {
      const result = await listTransactions(filters, nextPage, DEFAULT_PAGE_SIZE);
      if (id !== requestId.current) return;
      if (result.content.length === 0 && result.totalElements > 0 && nextPage > 1) {
        setPage(Math.max(result.totalPages, 1));
        return;
      }
      setRows(result.content);
      setTotalElements(result.totalElements);
      setTotalPages(result.totalPages);
      setPage(result.page);
      setStatus("ready");
      setMessage(null);
    } catch (error) {
      if (id !== requestId.current) return;
      setStatus("error");
      setMessage(error instanceof Error ? error.message : "The ledger could not be loaded.");
    }
  }, []);

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect -- results are stored after the request resolves
    void load(applied, page);
  }, [applied, page, load]);

  async function onDelete(row: Transaction) {
    const confirmed = window.confirm(
      `Remove the ${row.ticker} ${row.code} filing for ${row.insiderName} on ${row.transactionDate}?`,
    );
    if (!confirmed) return;
    setStatus("loading");
    setMessage(null);
    try {
      await deleteTransaction(row.id);
      await load(applied, page);
    } catch (error) {
      setStatus("error");
      setMessage(error instanceof Error ? error.message : "The filing could not be removed.");
    }
  }

  function applyFilters(next: TransactionFilters) {
    setRecording(false);
    setStatus("loading");
    setMessage(null);
    setPage(1);
    setApplied(next);
  }

  function filterByTicker(ticker: string) {
    const next = { ...draft, ticker };
    setDraft(next);
    applyFilters(next);
  }

  function filterByInsider(insiderName: string) {
    const next = { ...draft, insider: insiderName };
    setDraft(next);
    applyFilters(next);
  }

  const summary = summarize(rows);
  const from = totalElements === 0 ? 0 : (page - 1) * DEFAULT_PAGE_SIZE + 1;
  const to = Math.min(page * DEFAULT_PAGE_SIZE, totalElements);

  return (
    <div className="app">
      <aside className="sidebar">
        <header className="brand">
          <p className="eyebrow">Filing ledger</p>
          <h1>InsiderTrans</h1>
          <p>
            Trace purchases, sales, awards, and gifts by officers, directors, and major owners.
          </p>
        </header>
        <FilterPanel
          value={draft}
          onChange={setDraft}
          onApply={() => applyFilters(draft)}
          onClear={() => {
            setDraft(emptyFilters);
            applyFilters(emptyFilters);
          }}
          onRecord={() => setRecording(true)}
        />
        <a className="button button-ghost nav-link" href="#/admin">
          Admin
        </a>
        <p className="database">PostgreSQL · insidertrans</p>
      </aside>

      <main>
        {recording ? (
          <RecordForm
            onCancel={() => setRecording(false)}
            onCreated={() => {
              setRecording(false);
              setStatus("loading");
              setMessage(null);
              setPage(1);
              void load(applied, 1);
            }}
          />
        ) : (
          <>
            <header className="ledger-head">
              <div>
                <p className="eyebrow">Ledger</p>
                <h2>Insider transactions</h2>
              </div>
              <p className="status-text">
                {status === "loading"
                  ? "Loading filings…"
                  : totalElements === 0
                    ? "0 shown"
                    : `${from}–${to} of ${totalElements.toLocaleString("en-US")}`}
              </p>
            </header>

            {message ? (
              <div className="banner" role="alert">
                <p>{message}</p>
                <button
                  type="button"
                  className="button button-tiny"
                  onClick={() => {
                    setStatus("loading");
                    setMessage(null);
                    void load(applied, page);
                  }}
                >
                  Retry
                </button>
              </div>
            ) : null}

            <section className="stats" aria-label="Totals for this page">
              <article>
                <span>Shares acquired</span>
                <strong>{formatShares(summary.acquiredShares)}</strong>
              </article>
              <article>
                <span>Shares disposed</span>
                <strong>{formatShares(summary.disposedShares)}</strong>
              </article>
              <article>
                <span>Value acquired</span>
                <strong>{formatMoney(summary.acquiredValue)}</strong>
              </article>
              <article>
                <span>Net value</span>
                <strong className={summary.netValue < 0 ? "down" : summary.netValue > 0 ? "up" : undefined}>
                  {formatMoney(summary.netValue)}
                </strong>
              </article>
            </section>
            {summary.unpriced > 0 ? (
              <p className="note">
                {summary.unpriced} {summary.unpriced === 1 ? "row has" : "rows have"} no price and{" "}
                {summary.unpriced === 1 ? "is" : "are"} left out of the value totals.
              </p>
            ) : (
              <p className="note">Totals are for the current page ({DEFAULT_PAGE_SIZE} filings per page).</p>
            )}

            {status === "loading" && rows.length === 0 ? (
              <div className="empty">
                <h3>Loading the ledger</h3>
                <p>Fetching filings from the server.</p>
              </div>
            ) : status === "error" && rows.length === 0 ? null : (
              <TransactionTable
                rows={rows}
                page={page}
                totalPages={totalPages}
                totalElements={totalElements}
                size={DEFAULT_PAGE_SIZE}
                onPageChange={(next) => {
                  setStatus("loading");
                  setPage(next);
                }}
                onTickerClick={filterByTicker}
                onInsiderClick={filterByInsider}
                onDelete={(row) => void onDelete(row)}
              />
            )}
          </>
        )}
      </main>
    </div>
  );
}
