import { useCallback, useEffect, useState, type FormEvent } from "react";
import { getFetcherStatus, startBackfill, updateFetcher } from "../api";
import { FETCH_INTERVALS, type FetcherStatus } from "../types";

function formatInstant(value: string | null): string {
  if (!value) return "Never";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

export function AdminPage() {
  const [status, setStatus] = useState<FetcherStatus | null>(null);
  const [intervalMinutes, setIntervalMinutes] = useState(60);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    const next = await getFetcherStatus();
    setStatus(next);
    setIntervalMinutes(next.intervalMinutes);
  }, []);

  useEffect(() => {
    void refresh().catch((err: unknown) => {
      setError(err instanceof Error ? err.message : "Could not load admin status.");
    });
  }, [refresh]);

  useEffect(() => {
    if (!status?.jobRunning) return;
    const id = window.setInterval(() => {
      void refresh().catch(() => undefined);
    }, 3000);
    return () => window.clearInterval(id);
  }, [status?.jobRunning, refresh]);

  async function saveInterval() {
    setBusy(true);
    setError(null);
    try {
      const next = await updateFetcher({ intervalMinutes });
      setStatus(next);
      setMessage(`Fetch interval set to ${labelFor(next.intervalMinutes)}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save interval.");
    } finally {
      setBusy(false);
    }
  }

  async function toggleEnabled() {
    if (!status) return;
    setBusy(true);
    setError(null);
    try {
      const next = await updateFetcher({ enabled: !status.enabled, intervalMinutes });
      setStatus(next);
      setMessage(next.enabled ? "Automatic fetching started." : "Automatic fetching stopped.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update the fetcher.");
    } finally {
      setBusy(false);
    }
  }

  async function onBackfill(event: FormEvent) {
    event.preventDefault();
    if (!from || !to) {
      setError("Choose both from and to dates for backfill.");
      return;
    }
    if (to < from) {
      setError("To date must be on or after from date.");
      return;
    }
    const confirmed = window.confirm(
      `Backfill Form 4 filings from ${from} through ${to}? Already stored accessions will be skipped.`,
    );
    if (!confirmed) return;
    setBusy(true);
    setError(null);
    try {
      const next = await startBackfill(from, to);
      setStatus(next);
      setMessage(`Backfill started for ${from} to ${to}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start backfill.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <header className="brand">
          <p className="eyebrow">Admin</p>
          <h1>InsiderTrans</h1>
          <p>Control how often new Form 4 filings are pulled from EDGAR.</p>
        </header>
        <a className="button button-quiet nav-link" href="#/">
          Back to ledger
        </a>
        <p className="database">PostgreSQL · insidertrans</p>
      </aside>
      <main>
        <header className="ledger-head">
          <div>
            <p className="eyebrow">Fetcher</p>
            <h2>Automatic updates</h2>
          </div>
          <p className="status-text">
            {status?.jobRunning
              ? `Running ${status.jobKind}`
              : status?.enabled
                ? "Scheduled"
                : "Stopped"}
          </p>
        </header>

        {error ? (
          <div className="banner" role="alert">
            <p>{error}</p>
          </div>
        ) : null}
        {message ? <p className="note">{message}</p> : null}

        <section className="record admin-card">
          <h3>Schedule</h3>
          <p className="note">
            When started, the server reads the latest daily master index (today and yesterday), then
            imports new Form 4 packages. Duplicate accessions are skipped.
          </p>
          <label>
            <span>Fetch frequency</span>
            <select
              value={intervalMinutes}
              onChange={(event) => setIntervalMinutes(Number(event.target.value))}
              disabled={busy || !status}
            >
              {FETCH_INTERVALS.map((item) => (
                <option key={item.minutes} value={item.minutes}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <div className="record-actions">
            <button type="button" className="button button-quiet" onClick={() => void saveInterval()} disabled={busy}>
              Save frequency
            </button>
            <button
              type="button"
              className={status?.enabled ? "button button-tiny" : "button button-primary"}
              onClick={() => void toggleEnabled()}
              disabled={busy || !status}
            >
              {status?.enabled ? "Stop automatic fetching" : "Start automatic fetching"}
            </button>
          </div>
        </section>

        <section className="record admin-card">
          <h3>Backfill missing days</h3>
          <p className="note">
            Walk each calendar day in the range. Weekends and unpublished indexes are skipped.
            Filings already in the database are not imported again.
          </p>
          <form className="fields" onSubmit={(event) => void onBackfill(event)}>
            <label>
              <span>From</span>
              <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} required />
            </label>
            <label>
              <span>To</span>
              <input type="date" value={to} onChange={(event) => setTo(event.target.value)} required />
            </label>
            <div className="record-actions admin-backfill-actions">
              <button type="submit" className="button button-primary" disabled={busy || status?.jobRunning}>
                Start backfill
              </button>
            </div>
          </form>
        </section>

        <section className="record admin-card">
          <h3>Last run</h3>
          <dl className="admin-dl">
            <div>
              <dt>Last finished</dt>
              <dd>{formatInstant(status?.lastRunAt ?? null)}</dd>
            </div>
            <div>
              <dt>Summary</dt>
              <dd>{status?.lastSummary || "—"}</dd>
            </div>
            <div>
              <dt>Error</dt>
              <dd>{status?.lastError || "None"}</dd>
            </div>
          </dl>
        </section>
      </main>
    </div>
  );
}

function labelFor(minutes: number): string {
  return FETCH_INTERVALS.find((item) => item.minutes === minutes)?.label ?? `${minutes} minutes`;
}
