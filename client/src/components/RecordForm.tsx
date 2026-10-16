import { useEffect, useState, type FormEvent } from "react";
import { createTransaction, listCompanies, listInsiders } from "../api";
import type { Company, Insider, TransactionInput } from "../types";
import { TRANSACTION_CODES, emptyInput } from "../types";

type RecordFormProps = {
  onCancel: () => void;
  onCreated: () => void;
};

export function RecordForm({ onCancel, onCreated }: RecordFormProps) {
  const [form, setForm] = useState<TransactionInput>(emptyInput);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [insiders, setInsiders] = useState<Insider[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([listCompanies(), listInsiders()])
      .then(([nextCompanies, nextInsiders]) => {
        if (!cancelled) {
          setCompanies(nextCompanies);
          setInsiders(nextInsiders);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError("Existing issuers could not be loaded. You can still type a new filing.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function update<K extends keyof TransactionInput>(key: K, value: TransactionInput[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function onCodeChange(code: TransactionInput["code"]) {
    const match = TRANSACTION_CODES.find((item) => item.code === code);
    setForm((current) => ({
      ...current,
      code,
      acquiredOrDisposed: match?.disposition ?? "",
    }));
  }

  function matchCompany() {
    const match = companies.find(
      (company) => company.ticker.toLowerCase() === form.ticker.trim().toLowerCase(),
    );
    if (!match) return;
    setForm((current) => ({
      ...current,
      companyName: current.companyName.trim() === "" ? match.name : current.companyName,
      companyCik: current.companyCik.trim() === "" ? (match.cik ?? "") : current.companyCik,
    }));
  }

  function matchInsider() {
    const match = insiders.find(
      (insider) => insider.name.toLowerCase() === form.insiderName.trim().toLowerCase(),
    );
    if (!match) return;
    setForm((current) => ({
      ...current,
      insiderCik: current.insiderCik.trim() === "" ? (match.cik ?? "") : current.insiderCik,
    }));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (form.acquiredOrDisposed !== "A" && form.acquiredOrDisposed !== "D") {
      setError("Choose whether the shares were acquired or disposed.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await createTransaction(form);
      onCreated();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The filing could not be saved.");
      setSubmitting(false);
    }
  }

  return (
    <form className="record" onSubmit={onSubmit}>
      <div className="record-head">
        <div>
          <p className="eyebrow">New filing</p>
          <h2>Record a transaction</h2>
        </div>
        <button type="button" className="button button-ghost" onClick={onCancel}>
          Back to ledger
        </button>
      </div>

      {error ? (
        <p className="banner" role="alert">
          {error}
        </p>
      ) : null}

      <fieldset>
        <legend>Issuer</legend>
        <div className="fields">
          <label>
            <span>Ticker</span>
            <input
              required
              list="tickers"
              value={form.ticker}
              onChange={(event) => update("ticker", event.target.value)}
              onBlur={matchCompany}
              placeholder="AAPL"
            />
            <datalist id="tickers">
              {companies.map((company) => (
                <option key={company.id} value={company.ticker}>
                  {company.name}
                </option>
              ))}
            </datalist>
          </label>
          <label>
            <span>Company</span>
            <input
              required
              value={form.companyName}
              onChange={(event) => update("companyName", event.target.value)}
              placeholder="Issuer name"
            />
          </label>
          <label>
            <span>CIK</span>
            <input
              value={form.companyCik}
              onChange={(event) => update("companyCik", event.target.value)}
              placeholder="Optional"
            />
          </label>
        </div>
      </fieldset>

      <fieldset>
        <legend>Insider</legend>
        <div className="fields">
          <label>
            <span>Name</span>
            <input
              required
              list="insider-names"
              value={form.insiderName}
              onChange={(event) => update("insiderName", event.target.value)}
              onBlur={matchInsider}
              placeholder="Reporting owner"
            />
            <datalist id="insider-names">
              {insiders.map((insider) => (
                <option key={insider.id} value={insider.name} />
              ))}
            </datalist>
          </label>
          <label>
            <span>Role</span>
            <input
              list="roles"
              value={form.insiderRole}
              onChange={(event) => update("insiderRole", event.target.value)}
              placeholder="Officer, director, 10% owner"
            />
            <datalist id="roles">
              <option value="Officer" />
              <option value="Director" />
              <option value="10% owner" />
              <option value="Officer and director" />
            </datalist>
          </label>
          <label>
            <span>Insider CIK</span>
            <input
              value={form.insiderCik}
              onChange={(event) => update("insiderCik", event.target.value)}
              placeholder="Optional"
            />
          </label>
        </div>
      </fieldset>

      <fieldset>
        <legend>Trade</legend>
        <div className="fields">
          <label>
            <span>Transaction date</span>
            <input
              required
              type="date"
              value={form.transactionDate}
              onChange={(event) => update("transactionDate", event.target.value)}
            />
          </label>
          <label>
            <span>Filing date</span>
            <input
              type="date"
              value={form.filingDate}
              onChange={(event) => update("filingDate", event.target.value)}
            />
          </label>
          <label>
            <span>Code</span>
            <select
              value={form.code}
              onChange={(event) => onCodeChange(event.target.value as TransactionInput["code"])}
            >
              {TRANSACTION_CODES.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.code} · {item.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Direction</span>
            <select
              required
              value={form.acquiredOrDisposed}
              onChange={(event) =>
                update("acquiredOrDisposed", event.target.value as TransactionInput["acquiredOrDisposed"])
              }
            >
              <option value="" disabled>
                Select
              </option>
              <option value="A">Acquired</option>
              <option value="D">Disposed</option>
            </select>
          </label>
          <label>
            <span>Shares</span>
            <input
              required
              inputMode="decimal"
              value={form.shares}
              onChange={(event) => update("shares", event.target.value)}
              placeholder="0"
            />
          </label>
          <label>
            <span>Price</span>
            <input
              inputMode="decimal"
              value={form.pricePerShare}
              onChange={(event) => update("pricePerShare", event.target.value)}
              placeholder="Optional"
            />
          </label>
          <label>
            <span>Owned after</span>
            <input
              inputMode="decimal"
              value={form.sharesOwnedFollowing}
              onChange={(event) => update("sharesOwnedFollowing", event.target.value)}
              placeholder="Optional"
            />
          </label>
          <label>
            <span>Security</span>
            <input
              required
              value={form.securityTitle}
              onChange={(event) => update("securityTitle", event.target.value)}
            />
          </label>
        </div>
      </fieldset>

      <div className="record-actions">
        <button type="submit" className="button button-primary" disabled={submitting}>
          {submitting ? "Saving…" : "Save filing"}
        </button>
      </div>
    </form>
  );
}
