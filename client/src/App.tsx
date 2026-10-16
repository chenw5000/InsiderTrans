import { useCallback, useEffect, useRef, useState } from "react";
import { deleteTransaction, listTransactions } from "./api";
import { FilterPanel } from "./components/FilterPanel";
import { RecordForm } from "./components/RecordForm";
import { TransactionTable } from "./components/TransactionTable";
import { formatMoney, formatShares, summarize } from "./format";
import { emptyFilters, type Transaction, type TransactionFilters } from "./types";

export default function App() {
  const [draft, setDraft] = useState<TransactionFilters>(emptyFilters);
  const [applied, setApplied] = useState<TransactionFilters>(emptyFilters);
  const [rows, setRows] = useState<Transaction[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [recording, setRecording] = useState(false);

  const requestId = useRef(0);

  const load = useCallback(async (filters: TransactionFilters) => {
    const id = ++requestId.current;
    try {
      const next = await listTransactions(filters);
      if (id !== requestId.current) return;
      setRows(next);
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
    void load(applied);
  }, [applied, load]);

  async function onDelete(row: Transaction) {
    const confirmed = window.confirm(
      `Remove the ${row.ticker} ${row.code} filing for ${row.insiderName} on ${row.transactionDate}?`,
    );
    if (!confirmed) return;
    setStatus("loading");
    setMessage(null);
    try {
      await deleteTransaction(row.id);
      await load(applied);
    } catch (error) {
      setStatus("error");
      setMessage(error instanceof Error ? error.message : "The filing could not be removed.");
    }
  }

  const summary = summarize(rows);

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
          onApply={() => {
            setRecording(false);
            if (draft !== applied) {
              setStatus("loading");
              setMessage(null);
            }
            setApplied(draft);
          }}
          onClear={() => {
            setDraft(emptyFilters);
            setRecording(false);
            if (applied !== emptyFilters) {
              setStatus("loading");
              setMessage(null);
            }
            setApplied(emptyFilters);
          }}
          onRecord={() => setRecording(true)}
        />
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
              void load(applied);
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
                {status === "loading" ? "Loading filings…" : `${summary.count} shown`}
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
                    void load(applied);
                  }}
                >
                  Retry
                </button>
              </div>
            ) : null}

            <section className="stats" aria-label="Totals for the current view">
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
            ) : null}

            {status === "loading" && rows.length === 0 ? (
              <div className="empty">
                <h3>Loading the ledger</h3>
                <p>Fetching filings from the server.</p>
              </div>
            ) : status === "error" && rows.length === 0 ? null : (
              <TransactionTable rows={rows} onDelete={(row) => void onDelete(row)} />
            )}
          </>
        )}
      </main>
    </div>
  );
}
