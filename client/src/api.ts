import type {
  Company,
  FetcherStatus,
  Insider,
  Transaction,
  TransactionFilters,
  TransactionInput,
  TransactionPage,
} from "./types";
import { DEFAULT_PAGE_SIZE } from "./types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        ...init?.headers,
      },
    });
  } catch (error) {
    if (error instanceof TypeError) {
      throw new Error(
        "The server is not reachable. Start it on port 6060 and confirm the insidertrans database is available.",
      );
    }
    throw error;
  }

  if (!response.ok) {
    throw new Error(await readError(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

async function readError(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { detail?: string; title?: string };
    return body.detail || body.title || response.statusText;
  } catch {
    return response.statusText || "Request failed";
  }
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

export function listTransactions(
  filters: TransactionFilters,
  page = 1,
  size = DEFAULT_PAGE_SIZE,
): Promise<TransactionPage> {
  const params = new URLSearchParams();
  if (filters.ticker.trim()) params.set("ticker", filters.ticker.trim());
  if (filters.insider.trim()) params.set("insider", filters.insider.trim());
  if (filters.code) params.set("code", filters.code);
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  params.set("page", String(page));
  params.set("size", String(size));
  return request<TransactionPage>(`/api/transactions?${params.toString()}`);
}

export function createTransaction(input: TransactionInput): Promise<Transaction> {
  return request<Transaction>("/api/transactions", {
    method: "POST",
    body: JSON.stringify({
      ticker: input.ticker.trim(),
      companyName: input.companyName.trim(),
      companyCik: emptyToNull(input.companyCik),
      insiderName: input.insiderName.trim(),
      insiderCik: emptyToNull(input.insiderCik),
      insiderRole: emptyToNull(input.insiderRole),
      transactionDate: input.transactionDate,
      filingDate: emptyToNull(input.filingDate),
      code: input.code,
      acquiredOrDisposed: input.acquiredOrDisposed || null,
      shares: input.shares.trim(),
      pricePerShare: emptyToNull(input.pricePerShare),
      sharesOwnedFollowing: emptyToNull(input.sharesOwnedFollowing),
      securityTitle: input.securityTitle.trim(),
    }),
  });
}

export function deleteTransaction(id: number): Promise<void> {
  return request<void>(`/api/transactions/${id}`, { method: "DELETE" });
}

export function listCompanies(): Promise<Company[]> {
  return request<Company[]>("/api/companies");
}

export function listInsiders(): Promise<Insider[]> {
  return request<Insider[]>("/api/insiders");
}

export function getFetcherStatus(): Promise<FetcherStatus> {
  return request<FetcherStatus>("/api/admin/fetcher");
}

export function updateFetcher(config: {
  enabled?: boolean;
  intervalMinutes?: number;
}): Promise<FetcherStatus> {
  return request<FetcherStatus>("/api/admin/fetcher", {
    method: "PUT",
    body: JSON.stringify(config),
  });
}

export function startBackfill(startDate: string, endDate: string): Promise<FetcherStatus> {
  return request<FetcherStatus>("/api/admin/backfill", {
    method: "POST",
    body: JSON.stringify({ startDate, endDate }),
  });
}
