import { API_ORIGIN } from '@/shared/api/http';
import type { MarketplaceErrorResponse } from '../model/marketplaceTypes';

const DEFAULT_ERROR_MESSAGE = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';
const NETWORK_ERROR_MESSAGE = '네트워크에 연결하지 못했어요. 연결을 확인해 주세요.';

export interface MarketplaceRequestOptions {
  signal?: AbortSignal;
}

interface MarketplaceRequestConfig extends MarketplaceRequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown | FormData;
}

export class MarketplaceApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fieldErrors?: Record<string, string>;

  constructor(status: number, body?: MarketplaceErrorResponse) {
    super(
      body?.message || (status === 0 ? NETWORK_ERROR_MESSAGE : DEFAULT_ERROR_MESSAGE),
    );
    this.name = 'MarketplaceApiError';
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = body?.fieldErrors;
  }
}

async function readErrorBody(
  response: Response,
): Promise<MarketplaceErrorResponse | undefined> {
  try {
    const body: unknown = await response.json();
    if (typeof body !== 'object' || body === null) {
      return undefined;
    }

    const candidate = body as Record<string, unknown>;
    return {
      code: typeof candidate.code === 'string' ? candidate.code : undefined,
      message: typeof candidate.message === 'string' ? candidate.message : undefined,
      fieldErrors:
        typeof candidate.fieldErrors === 'object' && candidate.fieldErrors !== null
          ? (candidate.fieldErrors as Record<string, string>)
          : undefined,
    };
  } catch {
    return undefined;
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

export async function marketplaceRequest<TResponse>(
  path: string,
  config: MarketplaceRequestConfig = {},
): Promise<TResponse> {
  const isFormData = config.body instanceof FormData;
  const hasBody = config.body !== undefined;
  const headers = new Headers({ Accept: 'application/json' });

  if (hasBody && !isFormData) {
    headers.set('Content-Type', 'application/json');
  }

  let response: Response;
  try {
    response = await fetch(`${API_ORIGIN}${path}`, {
      method: config.method ?? 'GET',
      credentials: 'include',
      headers,
      body: hasBody
        ? isFormData
          ? (config.body as FormData)
          : JSON.stringify(config.body)
        : undefined,
      signal: config.signal,
    });
  } catch (error) {
    if (isAbortError(error)) {
      throw error;
    }

    throw new MarketplaceApiError(0);
  }

  if (!response.ok) {
    throw new MarketplaceApiError(response.status, await readErrorBody(response));
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  return (await response.json()) as TResponse;
}
