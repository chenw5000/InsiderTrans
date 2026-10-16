export const TRANSACTION_CODES = [
  { code: "P", label: "Open-market purchase", disposition: "A" },
  { code: "S", label: "Open-market sale", disposition: "D" },
  { code: "A", label: "Grant or award", disposition: "A" },
  { code: "M", label: "Option exercise", disposition: "A" },
  { code: "F", label: "Tax withholding", disposition: "D" },
  { code: "G", label: "Gift", disposition: "" },
  { code: "D", label: "Sale to the issuer", disposition: "D" },
] as const;

export type TransactionCode = (typeof TRANSACTION_CODES)[number]["code"];
export type Disposition = "A" | "D";

export type Transaction = {
  id: number;
  ticker: string;
  companyName: string;
  insiderName: string;
  insiderRole: string | null;
  transactionDate: string;
  filingDate: string | null;
  code: TransactionCode;
  codeLabel: string;
  acquiredOrDisposed: Disposition;
  shares: number;
  pricePerShare: number | null;
  value: number | null;
  sharesOwnedFollowing: number | null;
  securityTitle: string;
};

export type Company = {
  id: number;
  ticker: string;
  name: string;
  cik: string | null;
};

export type Insider = {
  id: number;
  name: string;
  cik: string | null;
};

export type TransactionFilters = {
  ticker: string;
  insider: string;
  code: TransactionCode | "";
  from: string;
  to: string;
};

export const emptyFilters: TransactionFilters = {
  ticker: "",
  insider: "",
  code: "",
  from: "",
  to: "",
};

export type TransactionInput = {
  ticker: string;
  companyName: string;
  companyCik: string;
  insiderName: string;
  insiderCik: string;
  insiderRole: string;
  transactionDate: string;
  filingDate: string;
  code: TransactionCode;
  acquiredOrDisposed: Disposition | "";
  shares: string;
  pricePerShare: string;
  sharesOwnedFollowing: string;
  securityTitle: string;
};

export function codeLabel(code: TransactionCode): string {
  return TRANSACTION_CODES.find((item) => item.code === code)?.label ?? code;
}

export function todayIso(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

export function emptyInput(): TransactionInput {
  return {
    ticker: "",
    companyName: "",
    companyCik: "",
    insiderName: "",
    insiderCik: "",
    insiderRole: "Officer",
    transactionDate: todayIso(),
    filingDate: "",
    code: "P",
    acquiredOrDisposed: "A",
    shares: "",
    pricePerShare: "",
    sharesOwnedFollowing: "",
    securityTitle: "Common Stock",
  };
}
