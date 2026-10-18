import { formatDate, formatMoney, formatShares } from "../format";
import type { Transaction } from "../types";

type TransactionTableProps = {
  rows: Transaction[];
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPageChange: (page: number) => void;
  onTickerClick: (ticker: string) => void;
  onInsiderClick: (insiderName: string) => void;
  onDelete: (row: Transaction) => void;
};

export function TransactionTable({
  rows,
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  onTickerClick,
  onInsiderClick,
  onDelete,
}: TransactionTableProps) {
  if (totalElements === 0) {
    return (
      <div className="empty">
        <h3>No filings in this view</h3>
        <p>Adjust the filters, or record a transaction to start the ledger.</p>
      </div>
    );
  }

  const from = (page - 1) * size + 1;
  const to = Math.min(page * size, totalElements);

  return (
    <>
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
                  <button
                    type="button"
                    className="issuer-link"
                    onClick={() => onTickerClick(row.ticker)}
                  >
                    <strong className="ticker">{row.ticker}</strong>
                    <span>{row.companyName}</span>
                  </button>
                </td>
                <td>
                  <button
                    type="button"
                    className="issuer-link"
                    onClick={() => onInsiderClick(row.insiderName)}
                  >
                    <strong>{row.insiderName}</strong>
                    <span>{row.insiderRole || "Role not recorded"}</span>
                  </button>
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
      <nav className="pager" aria-label="Pagination">
        <button
          type="button"
          className="button button-tiny"
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </button>
        <p>
          {from}–{to} of {totalElements.toLocaleString("en-US")} · Page {page} of {Math.max(totalPages, 1)}
        </p>
        <button
          type="button"
          className="button button-tiny"
          disabled={page >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </button>
      </nav>
    </>
  );
}
