import type { Transaction } from "./types";

const shareFormat = new Intl.NumberFormat("en-US", { maximumFractionDigits: 4 });
const moneyFormat = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  maximumFractionDigits: 2,
});
const dateFormat = new Intl.DateTimeFormat("en-US", {
  month: "short",
  day: "numeric",
  year: "numeric",
});

export function formatShares(value: number | null): string {
  if (value == null || Number.isNaN(value)) return "—";
  return shareFormat.format(value);
}

export function formatMoney(value: number | null): string {
  if (value == null || Number.isNaN(value)) return "—";
  return moneyFormat.format(value);
}

export function formatDate(value: string | null): string {
  if (!value) return "—";
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return value;
  return dateFormat.format(new Date(year, month - 1, day));
}

export type LedgerSummary = {
  count: number;
  acquiredShares: number;
  disposedShares: number;
  acquiredValue: number;
  disposedValue: number;
  netValue: number;
  unpriced: number;
};

export function summarize(rows: Transaction[]): LedgerSummary {
  const summary: LedgerSummary = {
    count: rows.length,
    acquiredShares: 0,
    disposedShares: 0,
    acquiredValue: 0,
    disposedValue: 0,
    netValue: 0,
    unpriced: 0,
  };

  for (const row of rows) {
    const acquired = row.acquiredOrDisposed === "A";
    if (acquired) summary.acquiredShares += row.shares;
    else summary.disposedShares += row.shares;
    if (row.value == null) {
      summary.unpriced += 1;
      continue;
    }
    if (acquired) summary.acquiredValue += row.value;
    else summary.disposedValue += row.value;
  }

  summary.netValue = summary.acquiredValue - summary.disposedValue;
  return summary;
}
