type SearchParamValue = string | string[] | undefined;

export function pageFromUrl(value: string | null | undefined) {
  const page = Number(value);
  return Number.isInteger(page) && page > 0 ? page : 1;
}

export function searchUrl(pathname: string, requirement: string, page: number) {
  const normalizedRequirement = requirement.trim();
  if (!normalizedRequirement) {
    return pathname;
  }

  const params = new URLSearchParams({ q: normalizedRequirement });
  if (page > 1) {
    params.set("page", String(page));
  }
  return `${pathname}?${params.toString()}`;
}

export function detailUrl(id: string, requirement: string, page: number) {
  return searchUrl(`/mcp/${encodeURIComponent(id)}`, requirement, page);
}

export function searchReturnUrl(searchParams: Record<string, SearchParamValue>) {
  const requirement = firstValue(searchParams.q)?.trim() ?? "";
  if (!requirement) {
    return "/";
  }
  return searchUrl("/", requirement, pageFromUrl(firstValue(searchParams.page)));
}

function firstValue(value: SearchParamValue) {
  return Array.isArray(value) ? value[0] : value;
}
