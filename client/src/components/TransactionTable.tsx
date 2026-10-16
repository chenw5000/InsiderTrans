import { formatDate, formatMoney, formatShares } from "../format";
import type { Transaction } from "../types";

type TransactionTableProps = {
  rows: Transaction[];
  onDelete: (row: Transaction) => void;
};

export function TransactionTable({ rows, onDelete }: TransactionTableProps) {
  if (rows.length === 0) {
    return (
      <div className="empty">
        <h3>No filings in this view</h3>
        <p>Adjust the filters, or record a transaction to start the ledger.</p>
      </div>
    );
  }

  return (
    <div className="table-wrap">
      <table>
        <caption>Insider transactions matching the current filters</caption>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Issuer</th>
            <th scope="col">Insider</th>
            <th scope="col">Code</th>
            <th scope="col">Shares</th>
            <th scope="col">Price</th>
            <th scope="col">Value</th>
            <th scope="col">Owned after</th>
            <th scope="col">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              <td>
                <div className="stack">
                  <strong>{formatDate(row.transactionDate)}</strong>
                  <span>Filed {formatDate(row.filingDate)}</span>
                </div>
              </td>
              <td>
                <div className="stack">
                  <strong className="ticker">{row.ticker}</strong>
                  <span>{row.companyName}</span>
                </div>
              </td>
              <td>
                <div className="stack">
                  <strong>{row.insiderName}</strong>
                  <span>{row.insiderRole || "Role not recorded"}</span>
                </div>
              </td>
              <td>
                <div className="stack">
                  <span className={`code code-${row.code.toLowerCase()}`}>{row.code}</span>
                  <span>{row.codeLabel}</span>
                  <span>
                    {row.acquiredOrDisposed === "A" ? "Acquired" : "Disposed"} · {row.securityTitle}
                  </span>
                </div>
              </td>
              <td className="num">{formatShares(row.shares)}</td>
              <td className="num">{formatMoney(row.pricePerShare)}</td>
              <td className="num">{formatMoney(row.value)}</td>
              <td className="num">{formatShares(row.sharesOwnedFollowing)}</td>
              <td>
                <button
                  type="button"
                  className="button button-tiny"
                  onClick={() => onDelete(row)}
                  aria-label={`Remove ${row.insiderName} ${row.ticker} transaction on ${row.transactionDate}`}
                >
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
