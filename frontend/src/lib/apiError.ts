import axios from 'axios';

interface ProblemResponse {
  message?: string;
  violations?: { field: string; message: string }[];
}

export class ApiClientError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = 'ApiClientError';
  }
}

export function toApiClientError(error: unknown): ApiClientError {
  if (!axios.isAxiosError<ProblemResponse>(error)) {
    return error instanceof ApiClientError
      ? error
      : new ApiClientError('The request could not be completed.');
  }

  const violation = error.response?.data?.violations?.[0];
  return new ApiClientError(
    violation?.message ??
      error.response?.data?.message ??
      (error.response ? 'The request could not be completed.' : 'Unable to reach WorkSphere.'),
    error.response?.status,
  );
}
