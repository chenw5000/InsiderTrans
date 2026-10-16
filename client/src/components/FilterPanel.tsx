import type { TransactionFilters } from "../types";
import { TRANSACTION_CODES } from "../types";

type FilterPanelProps = {
  value: TransactionFilters;
  onChange: (value: TransactionFilters) => void;
  onApply: () => void;
  onClear: () => void;
  onRecord: () => void;
};

export function FilterPanel({ value, onChange, onApply, onClear, onRecord }: FilterPanelProps) {
  function update<K extends keyof TransactionFilters>(key: K, next: TransactionFilters[K]) {
    onChange({ ...value, [key]: next });
  }

  return (
    <div className="panel">
      <form
        className="filters"
        onSubmit={(event) => {
          event.preventDefault();
          onApply();
        }}
      >
        <h2>Filters</h2>
        <label>
          <span>Ticker</span>
          <input
            value={value.ticker}
            onChange={(event) => update("ticker", event.target.value)}
            placeholder="AAPL"
            autoComplete="off"
          />
        </label>
        <label>
          <span>Insider</span>
          <input
            value={value.insider}
            onChange={(event) => update("insider", event.target.value)}
            placeholder="Name contains"
            autoComplete="off"
          />
        </label>
        <label>
          <span>Code</span>
          <select
            value={value.code}
            onChange={(event) => update("code", event.target.value as TransactionFilters["code"])}
          >
            <option value="">Any code</option>
            {TRANSACTION_CODES.map((item) => (
              <option key={item.code} value={item.code}>
                {item.code} · {item.label}
              </option>
            ))}
          </select>
        </label>
        <div className="date-pair">
          <label>
            <span>From</span>
            <input type="date" value={value.from} onChange={(event) => update("from", event.target.value)} />
          </label>
          <label>
            <span>To</span>
            <input type="date" value={value.to} onChange={(event) => update("to", event.target.value)} />
          </label>
        </div>
        <div className="filter-actions">
          <button type="submit" className="button button-quiet">
            Apply
          </button>
          <button type="button" className="button button-ghost" onClick={onClear}>
            Clear
          </button>
        </div>
      </form>
      <button type="button" className="button button-primary" onClick={onRecord}>
        Record a filing
      </button>
    </div>
  );
}
